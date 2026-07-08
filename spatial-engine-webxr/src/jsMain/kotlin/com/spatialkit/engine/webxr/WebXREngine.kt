package com.spatialkit.engine.webxr

import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.AnchorRequest
import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.engine.AnchorType
import com.spatialkit.engine.Axis
import com.spatialkit.engine.Button
import com.spatialkit.engine.ButtonState
import com.spatialkit.engine.DetectedPlane
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.InputSource
import com.spatialkit.engine.PlaneRaycaster
import com.spatialkit.engine.PlaneType
import com.spatialkit.engine.Pose
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.TrackingState
import com.spatialkit.engine.Vec3
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.HTMLCanvasElement
import com.spatialkit.engine.Hand as ControllerHand

/**
 * WebXR implementation of SpatialEngine.
 *
 * Uses Three.js for rendering and WebXR Device API for AR/VR capabilities.
 * Delegates all XR rendering to Three.js's built-in WebXR manager, which
 * handles framebuffer binding, camera matrix updates, and viewport management.
 * Scene-graph synchronization lives in [ThreeSceneRenderer], shared with the
 * non-XR preview engine.
 */
public class WebXREngine : SpatialEngine {
    // Three.js components
    private var sceneRenderer: ThreeSceneRenderer? = null
    private var camera: PerspectiveCamera? = null
    private var renderer: WebGLRenderer? = null

    // WebXR components
    private var xrSession: XRSession? = null
    private var xrReferenceSpace: XRReferenceSpace? = null
    private var hitTestSource: XRHitTestSource? = null

    // State management
    private val _state = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    private var _capabilities: EngineCapabilities? = null
    private var currentConfig: EngineConfig? = null
    private var frameCallback: ((FrameData) -> Unit)? = null
    private var lastFrameTime = 0.0

    // M9.1 light estimation: scene lights driven by the XR light probe
    private var xrLightProbe: dynamic = null
    private var lastLightEstimate: com.spatialkit.engine.LightEstimate? = null

    // M12.1 anchors: XRAnchor-backed where the WebXR anchors module exists,
    // with a fixed-pose software fallback elsewhere so AnchorType.World /
    // HitTest / Plane keep working (they just won't be tracking-refined).
    // An id lives in exactly one of these maps/sets at a time.
    private val xrAnchors = LinkedHashMap<String, XRAnchor>()
    private val softwareAnchors = LinkedHashMap<String, Pose>()
    private val pendingAnchorRequests = LinkedHashMap<String, AnchorRequest>()
    private val creatingAnchorIds = mutableSetOf<String>()
    private val failedAnchorIds = mutableSetOf<String>()

    // M12.1 plane detection: converted once per frame from the session's
    // XRPlaneSet; ids are kept stable across frames via a WeakMap keyed by
    // XRPlane identity
    private var planeIdMap: dynamic = null
    private var nextPlaneId = 0
    private var currentPlanes: List<DetectedPlane> = emptyList()
    private var currentXRPlanes: Map<String, XRPlane> = emptyMap()

    /**
     * Invoked when an immersive XR session starts. The hosting UI should hide
     * any overlay content (e.g. its 2D canvas) so the camera feed is visible.
     * The engine itself never mutates host-page styling.
     */
    public var onEnterXR: (() -> Unit)? = null

    /**
     * Invoked when the immersive XR session ends and the hosting UI should be
     * restored.
     */
    public var onExitXR: (() -> Unit)? = null

    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override val capabilities: EngineCapabilities
        get() =
            _capabilities ?: EngineCapabilities(
                supportsAR = false,
                supportsVR = false,
                supportsPlaneDetection = false,
                supportsImageTracking = false,
                supportsFaceTracking = false,
                supportsHandTracking = false,
                supportsDepthOcclusion = false,
                supportsSpatialAnchors = false,
                maxAnchors = 0,
            )

    override suspend fun initialize(config: EngineConfig) {
        if (_state.value != EngineState.Uninitialized && _state.value != EngineState.Disposed) {
            throw IllegalStateException("Engine already initialized")
        }

        _state.value = EngineState.Initializing
        currentConfig = config

        try {
            // Scene + lights live in the shared renderer; the XR light
            // estimate drives its lights each frame (M9.1).
            sceneRenderer = ThreeSceneRenderer()
            camera = PerspectiveCamera(75.0, 1.0, 0.1, 1000.0)

            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.id = "webxr-canvas"
            canvas.style.apply {
                position = "fixed"
                top = "0"
                left = "0"
                width = "100%"
                height = "100%"
                zIndex = "999"
            }
            document.body?.appendChild(canvas)

            val rendererParams = js("{}")
            rendererParams.canvas = canvas
            rendererParams.antialias = true
            rendererParams.alpha = true

            renderer =
                WebGLRenderer(rendererParams).apply {
                    setSize(window.innerWidth, window.innerHeight)
                    xr.enabled = true
                }

            // Listen for session end via Three.js's XR manager (registered once).
            // This fires AFTER Three.js's own cleanup, avoiding the race condition
            // where session.onend + setSession(null) would null out Three.js's
            // internal reference before its removeEventListener call.
            renderer?.xr.asDynamic().addEventListener("sessionend") {
                SpatialLog.debug(TAG) { "session ended (via Three.js XR manager)" }
                handleSessionEnded()
            }

            // Check WebXR capabilities
            _capabilities = checkCapabilities()

            _state.value = EngineState.Ready
        } catch (e: Exception) {
            _state.value =
                EngineState.Error(
                    EngineError.InitializationFailed("Failed to initialize WebXR: ${e.message}"),
                )
            throw e
        }
    }

    override suspend fun startSession(mode: SessionMode) {
        // Allow retry from Error state if the error was due to missing user activation
        if (_state.value != EngineState.Ready) {
            val currentState = _state.value
            if (currentState is EngineState.Error) {
                val errorMessage = currentState.error.toString()
                if (errorMessage.contains("user activation", ignoreCase = true)) {
                    _state.value = EngineState.Ready
                    SpatialLog.debug(TAG) { "retrying session start with user gesture" }
                } else {
                    throw IllegalStateException("Engine not ready. Current state: ${_state.value}")
                }
            } else {
                throw IllegalStateException("Engine not ready. Current state: ${_state.value}")
            }
        }

        val xrMode =
            when (mode) {
                is SessionMode.AR -> "immersive-ar"
                is SessionMode.VR -> "immersive-vr"
                is SessionMode.Passthrough -> "immersive-vr"
            }

        val navigator = window.asDynamic().navigator as Navigator
        val xr =
            navigator.xr ?: run {
                _state.value = EngineState.Error(EngineError.NotSupported)
                return
            }

        if (mode is SessionMode.AR && !requestCameraPermission(navigator)) {
            return
        }

        SpatialLog.debug(TAG) { "requesting $xrMode session" }

        // Build required/optional features based on mode
        val requiredFeatures =
            if (mode is SessionMode.AR) {
                arrayOf("local")
            } else {
                arrayOf("local-floor")
            }

        val optionalFeatures =
            if (mode is SessionMode.AR) {
                buildList {
                    add("camera-access")
                    add("hit-test")
                    add("hand-tracking")
                    add("anchors") // M12.1: XRAnchor-backed anchors
                    if (currentConfig?.enableLightEstimation != false) add("light-estimation")
                    if (currentConfig?.enablePlaneDetection != false) add("plane-detection") // M12.1
                }.toTypedArray()
            } else {
                arrayOf("hand-tracking", "local")
            }

        val sessionOptions = js("{}")
        sessionOptions.requiredFeatures = requiredFeatures
        sessionOptions.optionalFeatures = optionalFeatures

        val session =
            try {
                xr.requestSession(xrMode, sessionOptions).await()
            } catch (e: Throwable) {
                SpatialLog.error(TAG, e) { "session request failed" }
                _state.value =
                    EngineState.Error(
                        EngineError.SessionFailed("Failed to start session: ${e.message}"),
                    )
                return
            }

        SpatialLog.debug(TAG) { "session granted, configuring" }
        handleSessionStarted(session, mode)
    }

    /**
     * Requests (and immediately releases) camera access so the XR session
     * request runs with the permission already granted.
     *
     * @return false if permission was denied (state is set to Error)
     */
    private suspend fun requestCameraPermission(navigator: Navigator): Boolean {
        val mediaDevices = navigator.asDynamic().mediaDevices
        if (mediaDevices == null) {
            SpatialLog.warn(TAG) { "MediaDevices API not available, trying XR session anyway" }
            return true
        }
        SpatialLog.debug(TAG) { "requesting camera permission" }
        return try {
            val stream = mediaDevices.getUserMedia(js("{video: true}")).unsafeCast<kotlin.js.Promise<dynamic>>().await()
            SpatialLog.debug(TAG) { "camera permission granted" }
            stream.getTracks().unsafeCast<Array<dynamic>>().forEach { track ->
                track.stop()
            }
            true
        } catch (e: Throwable) {
            SpatialLog.error(TAG, e) { "camera permission denied" }
            _state.value = EngineState.Error(EngineError.PermissionDenied)
            false
        }
    }

    private suspend fun handleSessionStarted(
        session: XRSession,
        mode: SessionMode,
    ) {
        SpatialLog.debug(TAG) { "handleSessionStarted()" }
        xrSession = session

        // Configure Three.js XR reference space type BEFORE setting session
        val referenceSpaceType = if (mode is SessionMode.AR) "local" else "local-floor"
        renderer?.xr?.setReferenceSpaceType(referenceSpaceType)

        // Also request our own reference space for building FrameData
        try {
            xrReferenceSpace = session.requestReferenceSpace(referenceSpaceType).await()
            SpatialLog.debug(TAG) { "reference space acquired: $referenceSpaceType" }
        } catch (e: Throwable) {
            SpatialLog.error(TAG, e) { "failed to get reference space" }
            _state.value =
                EngineState.Error(
                    EngineError.SessionFailed("Failed to get reference space: ${e.message}"),
                )
            session.end()
            return
        }

        // Set up hit test source for AR (fire-and-forget; optional feature)
        if (mode is SessionMode.AR) {
            val hitTestOptions = js("{}")
            hitTestOptions.space = xrReferenceSpace
            session
                .requestHitTestSource(hitTestOptions)
                .then { source -> hitTestSource = source }
                .catch { null }
        }

        // Light probe for light estimation (M9.1); optional feature, only
        // granted where the browser supports it (fire-and-forget)
        if (mode is SessionMode.AR && currentConfig?.enableLightEstimation != false) {
            val sessionDyn = session.asDynamic()
            if (sessionDyn.requestLightProbe != undefined) {
                sessionDyn
                    .requestLightProbe()
                    .then { probe -> xrLightProbe = probe }
                    .catch { e ->
                        SpatialLog.debug(TAG) { "light probe unavailable: $e" }
                        null
                    }
            }
        }

        // === Let Three.js manage the XR session ===
        // This tells Three.js to handle framebuffer binding, camera matrices,
        // viewport setup, and all the WebGL/XR plumbing internally.
        renderer?.xr?.setSession(session)

        // Let the hosting UI hide overlay content (see [onEnterXR])
        onEnterXR?.invoke()

        // Start the Three.js animation loop.
        // When an XR session is active, Three.js automatically:
        //   - Calls session.requestAnimationFrame (not window.rAF)
        //   - Binds the XR framebuffer
        //   - Updates the camera projection/view from XRView
        //   - Sets the viewport from XRWebGLLayer
        // The callback receives (timestamp, xrFrame) — xrFrame is the dynamic param.
        renderer?.setAnimationLoop { time: Double, xrFrame: dynamic ->
            onFrame(time, xrFrame)
        }

        _state.value = EngineState.Running(mode)
        SpatialLog.debug(TAG) { "session running via Three.js XR manager" }
    }

    /**
     * Called every frame by Three.js's animation loop.
     *
     * When in XR mode, Three.js has already:
     *   - bound the correct framebuffer
     *   - updated camera matrices from the XR viewer pose
     *   - set the viewport
     *
     * We only need to:
     *   1. Build FrameData from the XRFrame for the user callback
     *   2. Invoke the user's frame callback (which may call submitScene)
     *   3. Call renderer.render() so Three.js draws the scene
     */
    private fun onFrame(
        time: Double,
        xrFrame: dynamic,
    ) {
        val threeScene = sceneRenderer?.scene ?: return
        val threeCamera = camera ?: return
        val threeRenderer = renderer ?: return

        // Calculate delta time
        val deltaTime =
            if (lastFrameTime > 0) {
                ((time - lastFrameTime) / 1000.0).toFloat()
            } else {
                0.0f
            }
        lastFrameTime = time

        // Build FrameData if we have an XR frame and reference space
        if (xrFrame != null && xrFrame != undefined && xrReferenceSpace != null) {
            val frame = xrFrame.unsafeCast<XRFrame>()
            updateLightEstimate(xrFrame)
            val viewerPose = frame.getViewerPose(xrReferenceSpace!!)

            if (viewerPose != null) {
                // M12.1: refresh detected planes first — pending plane
                // anchors resolve against this frame's plane set
                updateDetectedPlanes(frame)
                processAnchorRequests(frame)
                val frameData = convertToFrameData(time, deltaTime, viewerPose, frame, frame.session)
                frameCallback?.invoke(frameData)
            }
        }

        // Render — Three.js handles all XR-specific GL state
        threeRenderer.render(threeScene, threeCamera)
    }

    /**
     * Reads the XR light estimate for this frame (M9.1) and feeds it into the
     * scene's ambient + directional lights; also cached for [FrameData].
     *
     * The ambient term comes from the L0 spherical-harmonics band (Y00 =
     * 0.282095), the directional term from the probe's primary light. Both
     * are clamped so a pathological estimate can't black out or blow out the
     * scene.
     */
    private fun updateLightEstimate(xrFrame: dynamic) {
        val probe = xrLightProbe ?: return
        val estimate = xrFrame.getLightEstimate(probe) ?: return

        val sh = estimate.sphericalHarmonicsCoefficients ?: return
        val y00 = 0.282095f
        val r = ((sh[0] as Number).toFloat() * y00).coerceAtLeast(0f)
        val g = ((sh[1] as Number).toFloat() * y00).coerceAtLeast(0f)
        val b = ((sh[2] as Number).toFloat() * y00).coerceAtLeast(0f)
        val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b

        val primaryIntensity = estimate.primaryLightIntensity
        val directionalIntensity =
            maxOf(
                (primaryIntensity.x as Number).toFloat(),
                (primaryIntensity.y as Number).toFloat(),
                (primaryIntensity.z as Number).toFloat(),
            ).coerceIn(0f, 2f)

        val direction = estimate.primaryLightDirection
        val dirX = (direction.x as Number).toFloat()
        val dirY = (direction.y as Number).toFloat()
        val dirZ = (direction.z as Number).toFloat()

        // Scale the baseline lights (0.6 ambient / 0.8 directional at a
        // nominally lit scene) by the estimate
        val ambientScale = luminance.coerceIn(0f, 2f)
        sceneRenderer?.ambientLight?.asDynamic()?.intensity = 0.6 * ambientScale
        sceneRenderer?.directionalLight?.let { light ->
            light.asDynamic().intensity = 0.8 * directionalIntensity
            // primaryLightDirection points toward the light source; a Three
            // directional light shines from its position toward the origin
            light.position.set(dirX.toDouble(), dirY.toDouble(), dirZ.toDouble())
        }

        lastLightEstimate =
            com.spatialkit.engine.LightEstimate(
                ambientIntensity = ambientScale,
                ambientColor =
                    if (luminance > 1e-4f) {
                        Vec3(r / luminance, g / luminance, b / luminance)
                    } else {
                        Vec3(1f, 1f, 1f)
                    },
                directionalIntensity = directionalIntensity,
                // Contract: the direction light travels (away from source)
                directionalDirection = Vec3(-dirX, -dirY, -dirZ),
            )
    }

    private fun handleSessionEnded() {
        xrSession = null
        xrReferenceSpace = null
        hitTestSource = null
        xrLightProbe = null
        lastLightEstimate = null
        lastFrameTime = 0.0

        // XRAnchors die with the session — just drop the bookkeeping
        xrAnchors.clear()
        softwareAnchors.clear()
        pendingAnchorRequests.clear()
        creatingAnchorIds.clear()
        failedAnchorIds.clear()
        currentPlanes = emptyList()
        currentXRPlanes = emptyMap()

        // Stop the animation loop
        renderer?.setAnimationLoop(null)

        // Let the hosting UI restore itself (see [onExitXR])
        onExitXR?.invoke()

        _state.value = EngineState.Ready
    }

    override fun stopSession() {
        xrSession?.end()
    }

    override fun dispose() {
        stopSession()

        // Release GPU resources held by the scene renderer
        sceneRenderer?.dispose()
        sceneRenderer = null

        camera = null
        renderer?.domElement?.remove()
        renderer = null

        _state.value = EngineState.Disposed
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        frameCallback = callback
    }

    override fun submitScene(scene: SceneData) {
        sceneRenderer?.submitScene(scene)
        syncAnchorRequests(scene.anchors)
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        // WebXR does not expose synchronous real-world hit testing from
        // arbitrary screen points (that requires transient-input hit-test
        // sources). This casts a camera ray through the normalized screen
        // point against scene content and — since M12.1 — analytically
        // against the planes the plane-detection module reported this frame;
        // the nearest hit wins.
        val threeCamera = camera ?: return null
        val contentHit = sceneRenderer?.performHitTest(screenX, screenY, threeCamera)
        val planeHit = hitTestDetectedPlanes(screenX, screenY, threeCamera)
        return listOfNotNull(contentHit, planeHit).minByOrNull { it.distance }
    }

    private fun hitTestDetectedPlanes(
        screenX: Float,
        screenY: Float,
        threeCamera: Camera,
    ): HitTestResult? {
        val planes = currentPlanes
        if (planes.isEmpty()) return null

        // Normalized (0..1, top-left origin) -> NDC (-1..1, bottom-left origin)
        val ndc = Vector2((screenX * 2f - 1f).toDouble(), (1f - screenY * 2f).toDouble())
        val raycaster = Raycaster()
        raycaster.setFromCamera(ndc, threeCamera)
        val ray = raycaster.ray

        return PlaneRaycaster.raycast(
            origin = Vec3(ray.origin.x.toFloat(), ray.origin.y.toFloat(), ray.origin.z.toFloat()),
            direction = Vec3(ray.direction.x.toFloat(), ray.direction.y.toFloat(), ray.direction.z.toFloat()),
            planes = planes,
        )
    }

    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = sceneRenderer?.performRaycast(origin, direction)

    /**
     * Builds [FrameData] from the viewer pose.
     *
     * Note: the projection matrix is taken from the first XR view. For
     * stereo (immersive VR) rendering Three.js manages per-eye cameras
     * internally; per-view data is not yet exposed through [FrameData].
     */
    private fun convertToFrameData(
        time: Double,
        deltaTime: Float,
        viewerPose: XRViewerPose,
        frame: XRFrame,
        session: XRSession,
    ): FrameData {
        val transform = viewerPose.transform

        return FrameData(
            timestamp = time.toLong(),
            deltaTime = deltaTime,
            cameraPose = transform.toPose(),
            projectionMatrix =
                viewerPose.views[0]
                    .projectionMatrix
                    .toFloatArray(),
            viewMatrix = transform.matrix.toFloatArray(),
            trackingState = TrackingState.NORMAL,
            detectedPlanes = currentPlanes,
            lightEstimate = lastLightEstimate,
            inputSources = convertInputSources(session.inputSources, frame),
            anchors = buildAnchorStates(frame),
        )
    }

    // === M12.1: plane detection ===

    /**
     * Converts this frame's XRPlaneSet (plane-detection module) into
     * [DetectedPlane]s. Ids stay stable across frames via a WeakMap keyed by
     * XRPlane identity — the set contains the same plane objects each frame
     * while a plane is tracked. No-op (empty list) on browsers without the
     * module or when the feature wasn't granted.
     */
    private fun updateDetectedPlanes(frame: XRFrame) {
        val referenceSpace = xrReferenceSpace ?: return
        val planeSet = frame.asDynamic().detectedPlanes
        if (planeSet == null || planeSet == undefined || currentConfig?.enablePlaneDetection == false) {
            currentPlanes = emptyList()
            currentXRPlanes = emptyMap()
            return
        }

        if (planeIdMap == null) planeIdMap = js("new WeakMap()")
        val idMap = planeIdMap
        val planes = ArrayList<DetectedPlane>()
        val byId = HashMap<String, XRPlane>()

        planeSet.forEach { planeDyn: dynamic ->
            val plane = planeDyn.unsafeCast<XRPlane>()
            val xrPose = frame.getPose(plane.planeSpace, referenceSpace)
            if (xrPose != null) {
                var id = idMap.get(plane) as? String
                if (id == null) {
                    id = "webxr-plane-${nextPlaneId++}"
                    idMap.set(plane, id)
                }

                val pose = xrPose.transform.toPose()
                var minX = 0f
                var maxX = 0f
                var minZ = 0f
                var maxZ = 0f
                val vertices =
                    plane.polygon.mapIndexed { index, point ->
                        val x = point.x.toFloat()
                        val z = point.z.toFloat()
                        if (index == 0) {
                            minX = x
                            maxX = x
                            minZ = z
                            maxZ = z
                        } else {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (z < minZ) minZ = z
                            if (z > maxZ) maxZ = z
                        }
                        Vec3(x, 0f, z)
                    }

                planes +=
                    DetectedPlane(
                        id = id,
                        type =
                            when (plane.orientation?.lowercase()) {
                                // The module only says "horizontal"; up vs down
                                // comes from where the plane normal (+y) points
                                "horizontal" ->
                                    if ((pose.rotation * Vec3.UP).y >= 0f) {
                                        PlaneType.HORIZONTAL_UP
                                    } else {
                                        PlaneType.HORIZONTAL_DOWN
                                    }
                                "vertical" -> PlaneType.VERTICAL
                                else -> PlaneType.UNKNOWN
                            },
                        centerPose = pose,
                        extentX = maxX - minX,
                        extentZ = maxZ - minZ,
                        vertices = vertices.takeIf { it.size >= 3 },
                    )
                byId[id] = plane
            }
        }

        currentPlanes = planes
        currentXRPlanes = byId
    }

    // === M12.1: anchors ===

    /**
     * Diffs the scene's declarative anchor requests against engine state:
     * unknown ids are queued for creation on the next XR frame, and anchors
     * whose request disappeared are released.
     */
    private fun syncAnchorRequests(requests: List<AnchorRequest>) {
        val requestedIds = HashSet<String>(requests.size)
        requests.forEach { request ->
            requestedIds += request.id
            val known =
                request.id in xrAnchors ||
                    request.id in softwareAnchors ||
                    request.id in creatingAnchorIds ||
                    request.id in failedAnchorIds ||
                    request.id in pendingAnchorRequests
            if (!known) pendingAnchorRequests[request.id] = request
        }

        // Anchors dropped from the scene stop being tracked; XRAnchor.delete
        // frees the UA-side tracking slot. In-flight creations resolve into
        // xrAnchors and get cleaned up by a later sync.
        val staleIds = xrAnchors.keys.filter { it !in requestedIds }
        staleIds.forEach { id ->
            val anchor = xrAnchors.remove(id) ?: return@forEach
            try {
                anchor.delete()
            } catch (e: Throwable) {
                SpatialLog.debug(TAG) { "XRAnchor.delete failed for $id: ${e.message}" }
            }
        }
        softwareAnchors.keys.retainAll(requestedIds)
        pendingAnchorRequests.keys.retainAll(requestedIds)
        failedAnchorIds.retainAll(requestedIds)
    }

    /**
     * Resolves queued anchor requests against this XR frame. Uses
     * `XRFrame.createAnchor` (anchors module) when the browser has it —
     * plane anchors are created in the plane's own [XRPlane.planeSpace] so
     * they follow plane refinements — and falls back to fixed-pose software
     * anchors elsewhere. Plane requests whose plane hasn't been detected yet
     * stay queued (mirroring ARCore, where they retry until the plane shows
     * up), unless plane detection is unavailable altogether.
     */
    private fun processAnchorRequests(frame: XRFrame) {
        if (pendingAnchorRequests.isEmpty()) return
        val frameDyn = frame.asDynamic()
        val nativeAnchors = frameDyn.createAnchor != undefined

        pendingAnchorRequests.values.toList().forEach { request ->
            when (val type = request.type) {
                is AnchorType.World ->
                    dispatchAnchor(request.id, frame, nativeAnchors, Pose(type.position, type.rotation))

                is AnchorType.HitTest ->
                    dispatchAnchor(request.id, frame, nativeAnchors, Pose(type.hitPoint, Quaternion.IDENTITY))

                is AnchorType.Plane -> {
                    val plane = currentPlanes.firstOrNull { it.id == type.planeId }
                    val xrPlane = currentXRPlanes[type.planeId]
                    when {
                        plane == null -> {
                            if (frameDyn.detectedPlanes == undefined) {
                                // No plane detection on this browser: the plane
                                // can never appear, so fail instead of pending
                                failAnchor(request.id, "plane detection unsupported")
                            }
                        }

                        nativeAnchors && xrPlane != null ->
                            createNativeAnchor(
                                request.id,
                                frame,
                                rigidTransform(type.offsetFromCenter, Quaternion.IDENTITY),
                                xrPlane.planeSpace,
                            )

                        else -> {
                            val pose = plane.centerPose
                            dispatchAnchor(
                                request.id,
                                frame,
                                nativeAnchors,
                                Pose(pose.position + pose.rotation * type.offsetFromCenter, pose.rotation),
                            )
                        }
                    }
                }

                is AnchorType.Persistent -> restorePersistedAnchor(request.id, type.handle)
            }
        }
    }

    private fun dispatchAnchor(
        id: String,
        frame: XRFrame,
        nativeAnchors: Boolean,
        pose: Pose,
    ) {
        if (nativeAnchors) {
            createNativeAnchor(id, frame, rigidTransform(pose.position, pose.rotation), xrReferenceSpace ?: return)
        } else {
            pendingAnchorRequests.remove(id)
            softwareAnchors[id] = pose
        }
    }

    private fun createNativeAnchor(
        id: String,
        frame: XRFrame,
        transform: dynamic,
        space: XRSpace,
    ) {
        pendingAnchorRequests.remove(id)
        creatingAnchorIds += id
        frame
            .asDynamic()
            .createAnchor(transform, space)
            .then { anchor: dynamic ->
                creatingAnchorIds.remove(id)
                xrAnchors[id] = anchor.unsafeCast<XRAnchor>()
                null
            }.catch { e: dynamic ->
                creatingAnchorIds.remove(id)
                failedAnchorIds += id
                SpatialLog.warn(TAG) { "createAnchor failed for $id: $e" }
                null
            }
    }

    private fun failAnchor(
        id: String,
        reason: String,
    ) {
        pendingAnchorRequests.remove(id)
        failedAnchorIds += id
        SpatialLog.warn(TAG) { "anchor $id failed: $reason" }
    }

    // === M12.3: anchor persistence (WebXR persistent-anchor handles) ===

    /**
     * Restores an anchor persisted with [persistAnchor] in an earlier
     * session via `XRSession.restorePersistentAnchor`.
     */
    private fun restorePersistedAnchor(
        id: String,
        handle: String,
    ) {
        val sessionDyn = xrSession?.asDynamic()
        if (sessionDyn == null || sessionDyn.restorePersistentAnchor == undefined) {
            failAnchor(id, "persistent anchors not supported")
            return
        }
        pendingAnchorRequests.remove(id)
        creatingAnchorIds += id
        sessionDyn
            .restorePersistentAnchor(handle)
            .then { anchor: dynamic ->
                creatingAnchorIds.remove(id)
                xrAnchors[id] = anchor.unsafeCast<XRAnchor>()
                null
            }.catch { e: dynamic ->
                creatingAnchorIds.remove(id)
                failedAnchorIds += id
                SpatialLog.warn(TAG) { "restorePersistentAnchor failed for $id: $e" }
                null
            }
    }

    override suspend fun persistAnchor(anchorId: String): String? {
        val anchor = xrAnchors[anchorId] ?: return null
        val anchorDyn = anchor.asDynamic()
        if (anchorDyn.requestPersistentHandle == undefined) return null
        return try {
            anchorDyn
                .requestPersistentHandle()
                .unsafeCast<kotlin.js.Promise<String>>()
                .await()
        } catch (e: Throwable) {
            SpatialLog.warn(TAG) { "requestPersistentHandle failed for $anchorId: ${e.message}" }
            null
        }
    }

    override suspend fun deletePersistedAnchor(handle: String) {
        val sessionDyn = xrSession?.asDynamic() ?: return
        if (sessionDyn.deletePersistentAnchor == undefined) return
        try {
            sessionDyn
                .deletePersistentAnchor(handle)
                .unsafeCast<kotlin.js.Promise<Unit>>()
                .await()
        } catch (e: Throwable) {
            SpatialLog.warn(TAG) { "deletePersistentAnchor failed for $handle: ${e.message}" }
        }
    }

    /**
     * Reports every known anchor's state for this frame. Native anchors are
     * checked against `XRFrame.trackedAnchors` and posed via their
     * [XRAnchor.anchorSpace]; software anchors always report their fixed pose
     * as TRACKING.
     */
    private fun buildAnchorStates(frame: XRFrame): List<AnchorState> {
        val nothingTracked =
            xrAnchors.isEmpty() &&
                softwareAnchors.isEmpty() &&
                creatingAnchorIds.isEmpty() &&
                pendingAnchorRequests.isEmpty() &&
                failedAnchorIds.isEmpty()
        if (nothingTracked) return emptyList()
        val referenceSpace = xrReferenceSpace ?: return emptyList()
        val trackedSet = frame.asDynamic().trackedAnchors

        val states = ArrayList<AnchorState>()
        xrAnchors.forEach { (id, anchor) ->
            val tracked = trackedSet == undefined || trackedSet.has(anchor).unsafeCast<Boolean>()
            states +=
                if (!tracked) {
                    AnchorState(id, AnchorStatus.LOST, null)
                } else {
                    val xrPose = frame.getPose(anchor.anchorSpace, referenceSpace)
                    if (xrPose != null) {
                        AnchorState(id, AnchorStatus.TRACKING, xrPose.transform.toPose())
                    } else {
                        AnchorState(id, AnchorStatus.PAUSED, null)
                    }
                }
        }
        softwareAnchors.forEach { (id, pose) -> states += AnchorState(id, AnchorStatus.TRACKING, pose) }
        creatingAnchorIds.forEach { states += AnchorState(it, AnchorStatus.PENDING, null) }
        pendingAnchorRequests.keys.forEach { states += AnchorState(it, AnchorStatus.PENDING, null) }
        failedAnchorIds.forEach { states += AnchorState(it, AnchorStatus.ERROR, null) }
        return states
    }

    /** Builds a native `XRRigidTransform` (constructed reflectively — it is a global class, not a module export). */
    private fun rigidTransform(
        position: Vec3,
        rotation: Quaternion,
    ): dynamic {
        val ctor = window.asDynamic().XRRigidTransform
        val positionInit = domPointInit(position.x, position.y, position.z, 1f)
        val orientationInit = domPointInit(rotation.x, rotation.y, rotation.z, rotation.w)
        return js("Reflect").construct(ctor, arrayOf(positionInit, orientationInit))
    }

    private fun domPointInit(
        x: Float,
        y: Float,
        z: Float,
        w: Float,
    ): dynamic {
        val point = js("({})")
        point.x = x
        point.y = y
        point.z = z
        point.w = w
        return point
    }

    private fun convertInputSources(
        xrInputSources: Array<XRInputSource>,
        frame: XRFrame,
    ): List<InputSource> {
        val referenceSpace = xrReferenceSpace ?: return emptyList()

        return xrInputSources.mapNotNull { source ->
            when (source.targetRayMode) {
                "tracked-pointer" -> {
                    val pose = frame.getPose(source.targetRaySpace, referenceSpace)
                    pose?.let {
                        InputSource.Controller(
                            id = source.handedness,
                            hand = if (source.handedness == "left") ControllerHand.LEFT else ControllerHand.RIGHT,
                            pose =
                                Pose(
                                    position =
                                        Vec3(
                                            it.transform.position.x
                                                .toFloat(),
                                            it.transform.position.y
                                                .toFloat(),
                                            it.transform.position.z
                                                .toFloat(),
                                        ),
                                    rotation =
                                        Quaternion(
                                            it.transform.orientation.x
                                                .toFloat(),
                                            it.transform.orientation.y
                                                .toFloat(),
                                            it.transform.orientation.z
                                                .toFloat(),
                                            it.transform.orientation.w
                                                .toFloat(),
                                        ),
                                ),
                            buttons = convertGamepadButtons(source.gamepad),
                            axes = convertGamepadAxes(source.gamepad),
                        )
                    }
                }

                "screen" -> {
                    InputSource.Screen(
                        id = "screen",
                        touches = emptyList(),
                    )
                }

                else -> {
                    null
                }
            }
        }
    }

    private fun convertGamepadButtons(gamepad: Gamepad?): Map<Button, ButtonState> {
        gamepad ?: return emptyMap()

        return buildMap {
            if (gamepad.buttons.size > 0) {
                put(Button.TRIGGER, if (gamepad.buttons[0].pressed) ButtonState.PRESSED else ButtonState.RELEASED)
            }
            if (gamepad.buttons.size > 1) {
                put(Button.GRIP, if (gamepad.buttons[1].pressed) ButtonState.PRESSED else ButtonState.RELEASED)
            }
        }
    }

    private fun convertGamepadAxes(gamepad: Gamepad?): Map<Axis, Float> {
        gamepad ?: return emptyMap()

        return buildMap {
            if (gamepad.axes.size > 0) {
                put(Axis.THUMBSTICK_X, gamepad.axes[0].toFloat())
            }
            if (gamepad.axes.size > 1) {
                put(Axis.THUMBSTICK_Y, gamepad.axes[1].toFloat())
            }
        }
    }

    private suspend fun checkCapabilities(): EngineCapabilities {
        val navigator = window.asDynamic().navigator as Navigator
        val xr = navigator.xr

        return if (xr != null) {
            val supportsAR =
                try {
                    xr.isSessionSupported("immersive-ar").await()
                } catch (e: Exception) {
                    false
                }

            val supportsVR =
                try {
                    xr.isSessionSupported("immersive-vr").await()
                } catch (e: Exception) {
                    false
                }

            EngineCapabilities(
                supportsAR = supportsAR,
                supportsVR = supportsVR,
                supportsPlaneDetection = supportsAR,
                supportsImageTracking = false,
                supportsFaceTracking = false,
                supportsHandTracking = true,
                supportsDepthOcclusion = false,
                supportsSpatialAnchors = supportsAR,
                maxAnchors = null, // WebXR imposes no fixed anchor limit
                // M12.3: persistent-anchor handles (Chrome's anchors module)
                supportsAnchorPersistence = supportsAR && hasPersistentAnchorApi(),
            )
        } else {
            EngineCapabilities(
                supportsAR = false,
                supportsVR = false,
                supportsPlaneDetection = false,
                supportsImageTracking = false,
                supportsFaceTracking = false,
                supportsHandTracking = false,
                supportsDepthOcclusion = false,
                supportsSpatialAnchors = false,
                maxAnchors = 0,
            )
        }
    }

    private companion object {
        const val TAG = "WebXREngine"

        /** Whether the browser exposes the persistent-anchor API (M12.3). */
        fun hasPersistentAnchorApi(): Boolean =
            js("typeof XRSession !== 'undefined' && 'restorePersistentAnchor' in XRSession.prototype") as Boolean
    }
}

// Converts an XRRigidTransform to the engine Pose type.
private fun XRRigidTransform.toPose(): Pose =
    Pose(
        position =
            Vec3(
                position.x.toFloat(),
                position.y.toFloat(),
                position.z.toFloat(),
            ),
        rotation =
            Quaternion(
                orientation.x.toFloat(),
                orientation.y.toFloat(),
                orientation.z.toFloat(),
                orientation.w.toFloat(),
            ),
    )

// Extension to convert JS Float32Array to Kotlin FloatArray.
// The external `get` operator doesn't map to bracket indexing at runtime in IR,
// so we go through asDynamic() to access the native typed array elements.
private fun Float32Array.toFloatArray(): FloatArray {
    val jsArray = this.asDynamic()
    return FloatArray(length) { (jsArray[it] as Number).toFloat() }
}
