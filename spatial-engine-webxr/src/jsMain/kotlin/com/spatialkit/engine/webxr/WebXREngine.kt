package com.spatialkit.engine.webxr

import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.Axis
import com.spatialkit.engine.Button
import com.spatialkit.engine.ButtonState
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.InputSource
import com.spatialkit.engine.MaterialData
import com.spatialkit.engine.MaterialType
import com.spatialkit.engine.Pose
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.TrackingState
import com.spatialkit.engine.Transform
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
 */
public class WebXREngine : SpatialEngine {
    // Three.js components
    private var scene: Scene? = null
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

    // Scene management
    private val nodeMap = mutableMapOf<String, Object3D>()
    private val appliedMaterials = mutableMapOf<String, MaterialData>()
    private val appliedGeometry = mutableMapOf<String, GeometryData>()
    private val textureCache = ThreeTextureCache()
    private var lastFrameTime = 0.0

    // M9.1 light estimation: scene lights driven by the XR light probe
    private var ambientLight: AmbientLight? = null
    private var directionalLight: DirectionalLight? = null
    private var xrLightProbe: dynamic = null
    private var lastLightEstimate: com.spatialkit.engine.LightEstimate? = null

    // M9.2 blob shadows: one radial-gradient plane per shadow-casting subtree
    private val shadowMeshMap = mutableMapOf<String, Mesh>()
    private var blobShadowTexture: Texture? = null

    // Cached local AABBs for custom meshes (submitScene runs per frame)
    private val customMeshLocalBounds = mutableMapOf<String, com.spatialkit.engine.Aabb?>()

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
            // Create Three.js scene with lighting. The lights are kept as
            // fields so the XR light estimate can drive them (M9.1).
            val ambient = AmbientLight(0xffffff, 0.6)
            val directional =
                DirectionalLight(0xffffff, 0.8).apply {
                    position.set(1.0, 1.0, 1.0)
                }
            ambientLight = ambient
            directionalLight = directional
            scene =
                Scene().apply {
                    add(ambient)
                    add(directional)
                }
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
                if (currentConfig?.enableLightEstimation != false) {
                    arrayOf("camera-access", "hit-test", "hand-tracking", "light-estimation")
                } else {
                    arrayOf("camera-access", "hit-test", "hand-tracking")
                }
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
        val threeScene = scene ?: return
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
        ambientLight?.asDynamic()?.intensity = 0.6 * ambientScale
        directionalLight?.let { light ->
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

        // Release GPU resources held by cached meshes
        nodeMap.values.forEach { disposeObject(it) }
        nodeMap.clear()
        appliedMaterials.clear()
        appliedGeometry.clear()
        textureCache.disposeAll()
        shadowMeshMap.values.forEach { disposeObject(it) }
        shadowMeshMap.clear()
        blobShadowTexture?.dispose()
        blobShadowTexture = null
        customMeshLocalBounds.clear()
        ambientLight = null
        directionalLight = null

        scene = null
        camera = null
        renderer?.domElement?.remove()
        renderer = null

        _state.value = EngineState.Disposed
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        frameCallback = callback
    }

    override fun submitScene(scene: SceneData) {
        val threeScene = this.scene ?: return

        // Remove old nodes that aren't in new scene and free their GPU resources
        val newNodeIds = collectNodeIds(scene.nodes)
        val toRemove = nodeMap.keys.filter { it !in newNodeIds }
        toRemove.forEach { id ->
            nodeMap[id]?.let { obj ->
                obj.asDynamic().parent?.remove(obj)
                threeScene.remove(obj)
                disposeObject(obj)
            }
            nodeMap.remove(id)
            appliedMaterials.remove(id)?.baseColorTexture?.let { textureCache.release(it.id) }
            appliedGeometry.remove(id)
            customMeshLocalBounds.remove(id)
        }

        // Add or update nodes
        scene.nodes.forEach { node ->
            processSceneNodeIntoScene(node, threeScene)
        }

        updateBlobShadows(scene, threeScene)
    }

    /**
     * Maintains one blob-shadow plane (M9.2) under each shadow-casting
     * subtree: a radial-gradient disc at the base of the subtree's world
     * AABB, sized slightly beyond its footprint. Runs per submitScene (once
     * per frame), so shadows follow dragged/scaled content.
     */
    private fun updateBlobShadows(
        sceneData: SceneData,
        threeScene: Scene,
    ) {
        val casters =
            com.spatialkit.engine.SceneBounds.shadowCasters(sceneData.nodes) { mesh ->
                if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
                    customMeshLocalBounds.getOrPut(mesh.id) {
                        com.spatialkit.engine.SceneBounds
                            .localBounds(mesh.geometry)
                    }
                } else {
                    com.spatialkit.engine.SceneBounds
                        .localBounds(mesh.geometry)
                }
            }

        val liveIds = mutableSetOf<String>()
        casters.forEach { (node, bounds) ->
            val size = bounds.size
            val diameter = maxOf(size.x, size.z) * SHADOW_FOOTPRINT_SCALE
            if (diameter <= 0f) return@forEach
            liveIds.add(node.id)

            val shadow =
                shadowMeshMap.getOrPut(node.id) {
                    createBlobShadowMesh().also { threeScene.add(it) }
                }
            shadow.position.set(
                bounds.center.x.toDouble(),
                // Slightly above the base to avoid z-fighting the surface
                (bounds.min.y + 0.002f).toDouble(),
                bounds.center.z.toDouble(),
            )
            // Plane local XY spans world XZ after the flat rotation
            shadow.scale.set(diameter.toDouble(), diameter.toDouble(), 1.0)
        }

        (shadowMeshMap.keys - liveIds).forEach { id ->
            shadowMeshMap.remove(id)?.let { mesh ->
                threeScene.remove(mesh)
                mesh.geometry.dispose()
                mesh.material.dispose()
            }
        }
    }

    /**
     * A 1×1 plane rotated flat with a radial-gradient "blob" texture:
     * transparent, non-depth-writing, and excluded from raycasts so it never
     * intercepts node picking.
     */
    private fun createBlobShadowMesh(): Mesh {
        val texture =
            blobShadowTexture ?: createBlobShadowTexture().also { blobShadowTexture = it }

        val params = js("{}")
        params.map = texture
        params.transparent = true
        params.depthWrite = false
        val material = MeshBasicMaterial(params)

        val mesh = Mesh(PlaneGeometry(1.0, 1.0), material)
        // Three.js planes face +Z; rotate to face +Y (flat on the ground)
        mesh.rotation.x = -kotlin.math.PI / 2
        // Opt out of raycasting (performRaycast/performHitTest walk the scene)
        mesh.asDynamic().raycast = js("function() {}")
        return mesh
    }

    /** Radial gradient matching the other renderers' blob shadows. */
    private fun createBlobShadowTexture(): Texture {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 128
        canvas.height = 128
        val ctx = canvas.asDynamic().getContext("2d")
        val gradient = ctx.createRadialGradient(64.0, 64.0, 0.0, 64.0, 64.0, 64.0)
        gradient.addColorStop(0.0, "rgba(0,0,0,$SHADOW_OPACITY)")
        gradient.addColorStop(0.55, "rgba(0,0,0,${SHADOW_OPACITY * 0.75})")
        gradient.addColorStop(1.0, "rgba(0,0,0,0)")
        ctx.fillStyle = gradient
        ctx.fillRect(0, 0, 128, 128)

        return Texture(canvas).apply { needsUpdate = true }
    }

    private fun processSceneNodeIntoScene(
        node: SceneNode,
        scene: Scene,
    ) {
        processSceneNode(node) { obj ->
            if (obj.asDynamic().parent == null) {
                scene.add(obj)
            }
        }
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        // WebXR does not expose synchronous real-world hit testing from
        // arbitrary screen points (that requires transient-input hit-test
        // sources). This casts a camera ray through the normalized screen
        // point against scene content instead.
        val threeScene = scene ?: return null
        val threeCamera = camera ?: return null

        // Normalized (0..1, top-left origin) -> NDC (-1..1, bottom-left origin)
        val ndc = Vector2((screenX * 2f - 1f).toDouble(), (1f - screenY * 2f).toDouble())
        val raycaster = Raycaster()
        raycaster.setFromCamera(ndc, threeCamera)

        val hit = raycaster.intersectObjects(threeScene.children, recursive = true).firstOrNull() ?: return null
        return HitTestResult(
            position = Vec3(hit.point.x.toFloat(), hit.point.y.toFloat(), hit.point.z.toFloat()),
            normal = hit.worldNormal(),
            distance = hit.distance.toFloat(),
            planeId = null,
        )
    }

    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        val threeScene = scene ?: return null

        val raycaster =
            Raycaster(
                Vector3(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble()),
                Vector3(direction.x.toDouble(), direction.y.toDouble(), direction.z.toDouble()),
            )

        val intersections = raycaster.intersectObjects(threeScene.children, recursive = true)

        return intersections.firstOrNull()?.let { hit ->
            RaycastResult(
                position = Vec3(hit.point.x.toFloat(), hit.point.y.toFloat(), hit.point.z.toFloat()),
                normal = hit.worldNormal(),
                distance = hit.distance.toFloat(),
                nodeId = hit.`object`.name.takeIf { it.isNotEmpty() },
            )
        }
    }

    /**
     * Extracts the intersected face normal in world space, falling back to +Y.
     */
    private fun Intersection.worldNormal(): Vec3 {
        val face = this.face ?: return Vec3(0f, 1f, 0f)
        val normal =
            Vector3(
                (face.normal.x as Number).toDouble(),
                (face.normal.y as Number).toDouble(),
                (face.normal.z as Number).toDouble(),
            )
        // Face normals are in object space; rotate into world space
        val worldQuaternion = Quaternion()
        this.`object`.asDynamic().getWorldQuaternion(worldQuaternion)
        normal.asDynamic().applyQuaternion(worldQuaternion)
        return Vec3(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    // === Private Implementation ===

    private fun processSceneNode(
        node: SceneNode,
        attach: (Object3D) -> Unit,
    ) {
        when (node) {
            is SceneNode.Mesh -> {
                val mesh =
                    nodeMap.getOrPut(node.id) {
                        createMesh(node)
                    } as Mesh
                updateMeshData(node, mesh)
                updateTransform(mesh, node.transform)
                mesh.visible = node.visible
                attach(mesh)
            }

            is SceneNode.Group -> {
                val group =
                    nodeMap.getOrPut(node.id) {
                        Group().also { it.name = node.id }
                    }
                updateTransform(group, node.transform)
                group.visible = node.visible
                attach(group)

                node.children.forEach { child ->
                    processSceneNode(child) { obj ->
                        if (obj.asDynamic().parent == null) {
                            group.add(obj)
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies material/geometry changes to an already-created mesh, disposing
     * the replaced GPU resources.
     */
    private fun updateMeshData(
        node: SceneNode.Mesh,
        mesh: Mesh,
    ) {
        val previousMaterial = appliedMaterials[node.id]
        if (previousMaterial != node.material) {
            if (previousMaterial != null) {
                mesh.material.dispose()
                previousMaterial.baseColorTexture?.let { textureCache.release(it.id) }
            }
            mesh.material = createMaterial(node.material)
            appliedMaterials[node.id] = node.material
        }

        val previousGeometry = appliedGeometry[node.id]
        if (previousGeometry != node.geometry) {
            if (previousGeometry != null) {
                mesh.geometry.dispose()
            }
            mesh.geometry = createGeometry(node.geometry)
            appliedGeometry[node.id] = node.geometry
        }
    }

    /**
     * Releases GPU resources of a cached object (geometry + material of meshes).
     */
    private fun disposeObject(obj: Object3D) {
        if (obj is Mesh) {
            obj.geometry.dispose()
            obj.material.dispose()
        }
    }

    private fun createMesh(node: SceneNode.Mesh): Mesh {
        val geometry = createGeometry(node.geometry)
        val material = createMaterial(node.material)
        appliedMaterials[node.id] = node.material
        appliedGeometry[node.id] = node.geometry
        return Mesh(geometry, material).also { it.name = node.id }
    }

    private fun createGeometry(data: GeometryData): BufferGeometry =
        when (data.type) {
            GeometryType.CUBE -> {
                BoxGeometry(1.0, 1.0, 1.0)
            }

            GeometryType.SPHERE -> {
                SphereGeometry(0.5, 32, 16)
            }

            GeometryType.PLANE -> {
                PlaneGeometry(1.0, 1.0)
            }

            GeometryType.CYLINDER -> {
                CylinderGeometry(0.5, 0.5, 1.0, 32)
            }

            GeometryType.CAPSULE -> {
                CapsuleGeometry(0.5, 1.0, 4, 8)
            }

            GeometryType.CUSTOM_MESH -> {
                val geometry = BufferGeometry()
                data.vertices?.let { vertices ->
                    val positions = Float32Array(vertices.size)
                    vertices.forEachIndexed { i, v -> positions[i] = v }
                    geometry.setAttribute("position", BufferAttribute(positions, 3))
                }
                data.normals?.let { normals ->
                    val normalsArray = Float32Array(normals.size)
                    normals.forEachIndexed { i, v -> normalsArray[i] = v }
                    geometry.setAttribute("normal", BufferAttribute(normalsArray, 3))
                }
                data.uvs?.let { uvs ->
                    val uvArray = Float32Array(uvs.size)
                    uvs.forEachIndexed { i, v -> uvArray[i] = v }
                    geometry.setAttribute("uv", BufferAttribute(uvArray, 2))
                }
                data.indices?.let { indices ->
                    // Uint16 only addresses 65535 vertices; switch to Uint32 beyond that
                    val needsWideIndices = indices.any { it > 0xFFFF }
                    val indicesArray =
                        if (needsWideIndices) {
                            js("new Uint32Array(indices)")
                        } else {
                            js("new Uint16Array(indices)")
                        }
                    geometry.setIndex(BufferAttribute(indicesArray, 1))
                }
                geometry
            }
        }

    /**
     * Creates a Three.js material from MaterialData.
     *
     * Three.js expects color as a hex integer (e.g. 0xFF0000 for red),
     * not as an {r, g, b} object. We convert the float RGB components (0.0-1.0)
     * to a single packed integer. Three multiplies `map` by `color`, matching
     * glTF's baseColorFactor × baseColorTexture semantics.
     *
     * Texture decode is asynchronous: the material starts with its base color
     * and gets `map` assigned when the cache delivers the texture. The matching
     * release happens where the applied material is replaced or removed.
     */
    private fun createMaterial(data: MaterialData): Material {
        val r = (data.baseColor.x * 255).toInt().coerceIn(0, 255)
        val g = (data.baseColor.y * 255).toInt().coerceIn(0, 255)
        val b = (data.baseColor.z * 255).toInt().coerceIn(0, 255)
        val colorHex = (r shl 16) or (g shl 8) or b

        val material =
            when (data.type) {
                MaterialType.UNLIT -> {
                    val params = js("{}")
                    params.color = colorHex
                    if (data.doubleSided) params.side = 2 // THREE.DoubleSide
                    MeshBasicMaterial(params)
                }

                MaterialType.LIT, MaterialType.PBR -> {
                    val params = js("{}")
                    params.color = colorHex
                    params.metalness = data.metallic.toDouble()
                    params.roughness = data.roughness.toDouble()
                    if (data.doubleSided) params.side = 2 // THREE.DoubleSide
                    MeshStandardMaterial(params)
                }
            }

        data.baseColorTexture?.let { textureData ->
            textureCache.acquire(textureData) { texture ->
                material.asDynamic().map = texture
                material.asDynamic().needsUpdate = true
            }
        }

        return material
    }

    private fun updateTransform(
        obj: Object3D,
        transform: Transform,
    ) {
        obj.position.set(
            transform.position.x.toDouble(),
            transform.position.y.toDouble(),
            transform.position.z.toDouble(),
        )
        obj.quaternion.set(
            transform.rotation.x.toDouble(),
            transform.rotation.y.toDouble(),
            transform.rotation.z.toDouble(),
            transform.rotation.w.toDouble(),
        )
        obj.scale.set(
            transform.scale.x.toDouble(),
            transform.scale.y.toDouble(),
            transform.scale.z.toDouble(),
        )
    }

    private fun collectNodeIds(nodes: List<SceneNode>): Set<String> =
        buildSet {
            nodes.forEach { node ->
                add(node.id)
                if (node is SceneNode.Group) {
                    addAll(collectNodeIds(node.children))
                }
            }
        }

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
            cameraPose =
                Pose(
                    position =
                        Vec3(
                            transform.position.x.toFloat(),
                            transform.position.y.toFloat(),
                            transform.position.z.toFloat(),
                        ),
                    rotation =
                        Quaternion(
                            transform.orientation.x.toFloat(),
                            transform.orientation.y.toFloat(),
                            transform.orientation.z.toFloat(),
                            transform.orientation.w.toFloat(),
                        ),
                ),
            projectionMatrix =
                viewerPose.views[0]
                    .projectionMatrix
                    .toFloatArray(),
            viewMatrix = transform.matrix.toFloatArray(),
            trackingState = TrackingState.NORMAL,
            detectedPlanes = emptyList(),
            lightEstimate = lastLightEstimate,
            inputSources = convertInputSources(session.inputSources, frame),
            anchors = emptyList(),
        )
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

        // Blob shadows (M9.2): quad size relative to the caster's footprint,
        // and peak darkness at the blob center — matches the other renderers
        const val SHADOW_FOOTPRINT_SCALE = 1.25f
        const val SHADOW_OPACITY = 0.35
    }
}

// Extension to convert JS Float32Array to Kotlin FloatArray.
// The external `get` operator doesn't map to bracket indexing at runtime in IR,
// so we go through asDynamic() to access the native typed array elements.
private fun Float32Array.toFloatArray(): FloatArray {
    val jsArray = this.asDynamic()
    return FloatArray(length) { (jsArray[it] as Number).toFloat() }
}
