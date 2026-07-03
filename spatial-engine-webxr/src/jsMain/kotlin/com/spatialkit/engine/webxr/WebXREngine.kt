package com.spatialkit.engine.webxr

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
import org.w3c.dom.HTMLCanvasElement
import com.spatialkit.engine.Hand as ControllerHand

/**
 * WebXR implementation of SpatialEngine.
 *
 * Uses Three.js for rendering and WebXR Device API for AR/VR capabilities.
 * Delegates all XR rendering to Three.js's built-in WebXR manager, which
 * handles framebuffer binding, camera matrix updates, and viewport management.
 */
class WebXREngine : SpatialEngine {
    // Three.js components
    private var scene: Scene? = null
    private var camera: PerspectiveCamera? = null
    private var renderer: WebGLRenderer? = null

    // WebXR components
    private var xrSession: XRSession? = null
    private var xrReferenceSpace: XRReferenceSpace? = null
    private var hitTestSource: XRHitTestSource? = null

    // State management
    private var _state: EngineState = EngineState.Uninitialized
    private var _capabilities: EngineCapabilities? = null
    private var currentConfig: EngineConfig? = null
    private var frameCallback: ((FrameData) -> Unit)? = null

    // Scene management
    private val nodeMap = mutableMapOf<String, Object3D>()
    private var lastFrameTime = 0.0
    private var frameCount = 0

    // Reference to Compose canvas so we can hide/show it
    private var composeCanvas: HTMLCanvasElement? = null

    override val state: EngineState
        get() = _state

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
        if (_state != EngineState.Uninitialized) {
            throw IllegalStateException("Engine already initialized")
        }

        _state = EngineState.Initializing
        currentConfig = config

        try {
            // Find and store reference to the existing Compose canvas
            // so we can hide it when entering XR (it would otherwise cover the camera feed)
            composeCanvas = document.querySelector("canvas:not(#webxr-canvas)") as? HTMLCanvasElement

            // Create Three.js scene with lighting
            scene =
                Scene().apply {
                    add(AmbientLight(0xffffff, 0.6))
                    add(
                        DirectionalLight(0xffffff, 0.8).apply {
                            position.set(1.0, 1.0, 1.0)
                        },
                    )
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
                console.log("WebXR: Session ended (via Three.js XR manager).")
                handleSessionEnded()
            }

            // Check WebXR capabilities
            _capabilities = checkCapabilities()

            _state = EngineState.Ready
        } catch (e: Exception) {
            _state =
                EngineState.Error(
                    EngineError.InitializationFailed("Failed to initialize WebXR: ${e.message}"),
                )
            throw e
        }
    }

    override fun startSession(mode: SessionMode) {
        // Allow retry from Error state if the error was due to missing user activation
        if (_state != EngineState.Ready) {
            val currentState = _state
            if (currentState is EngineState.Error) {
                val errorMessage = currentState.error.toString()
                if (errorMessage.contains("user activation", ignoreCase = true)) {
                    _state = EngineState.Ready
                    console.log("WebXR: Retrying session start with user gesture")
                } else {
                    throw IllegalStateException("Engine not ready. Current state: $_state")
                }
            } else {
                throw IllegalStateException("Engine not ready. Current state: $_state")
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
                _state = EngineState.Error(EngineError.NotSupported)
                return
            }

        if (mode is SessionMode.AR) {
            console.log("WebXR: Requesting camera permission...")
            val mediaDevices = navigator.asDynamic().mediaDevices
            if (mediaDevices != null) {
                val constraints = js("{video: true}")
                mediaDevices
                    .getUserMedia(constraints)
                    .then { stream ->
                        console.log("WebXR: Camera permission granted!")
                        stream.getTracks().forEach { track ->
                            track.stop()
                        }
                        requestXRSession(xr, xrMode, mode)
                    }.catch { error ->
                        console.error("WebXR: Camera permission denied: $error")
                        _state =
                            EngineState.Error(
                                EngineError.SessionFailed("Camera permission denied: $error"),
                            )
                    }
            } else {
                console.warn("WebXR: MediaDevices API not available, trying XR session anyway...")
                requestXRSession(xr, xrMode, mode)
            }
        } else {
            requestXRSession(xr, xrMode, mode)
        }
    }

    private fun requestXRSession(
        xr: dynamic,
        xrMode: String,
        mode: SessionMode,
    ) {
        console.log("WebXR: Requesting $xrMode session...")

        // Build required/optional features based on mode
        val requiredFeatures =
            if (mode is SessionMode.AR) {
                arrayOf("local")
            } else {
                arrayOf("local-floor")
            }

        val optionalFeatures =
            if (mode is SessionMode.AR) {
                arrayOf("camera-access", "hit-test", "hand-tracking")
            } else {
                arrayOf("hand-tracking", "local")
            }

        val sessionOptions = js("{}")
        sessionOptions.requiredFeatures = requiredFeatures
        sessionOptions.optionalFeatures = optionalFeatures

        xr
            .requestSession(
                xrMode,
                sessionOptions,
            ).then { session ->
                console.log("WebXR: Session granted! Configuring...")
                handleSessionStarted(session, mode)
            }.catch { error ->
                console.error("WebXR: Session request failed: $error")
                _state =
                    EngineState.Error(
                        EngineError.SessionFailed("Failed to start session: $error"),
                    )
            }
    }

    private fun handleSessionStarted(
        session: XRSession,
        mode: SessionMode,
    ) {
        console.log("WebXR: handleSessionStarted() entered")
        xrSession = session

        // Configure Three.js XR reference space type BEFORE setting session
        val referenceSpaceType = if (mode is SessionMode.AR) "local" else "local-floor"
        renderer?.xr?.setReferenceSpaceType(referenceSpaceType)

        // Also request our own reference space for building FrameData
        session
            .requestReferenceSpace(referenceSpaceType)
            .then { referenceSpace ->
                console.log("WebXR: Reference space acquired: $referenceSpaceType")
                xrReferenceSpace = referenceSpace

                // Set up hit test source for AR
                if (mode is SessionMode.AR) {
                    val hitTestOptions = js("{}")
                    hitTestOptions.space = referenceSpace
                    session
                        .requestHitTestSource(
                            hitTestOptions,
                        ).then { source ->
                            hitTestSource = source
                        }.catch { }
                }
            }.catch { error ->
                console.error("WebXR: Failed to get reference space: $error")
            }

        // === Let Three.js manage the XR session ===
        // This tells Three.js to handle framebuffer binding, camera matrices,
        // viewport setup, and all the WebGL/XR plumbing internally.
        renderer?.xr?.setSession(session)

        // Hide the Compose canvas so the camera feed is visible behind Three.js
        composeCanvas?.style?.display = "none"
        document.body?.style?.background = "transparent"

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

        _state = EngineState.Running(mode)
        console.log("WebXR: Session running via Three.js XR manager")
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
        frameCount++
        if (frameCount <= 3) {
            console.log("WebXR: onFrame #$frameCount")
        }

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
            val viewerPose = frame.getViewerPose(xrReferenceSpace!!)

            if (viewerPose != null) {
                val frameData = convertToFrameData(time, deltaTime, viewerPose, frame, frame.session)
                frameCallback?.invoke(frameData)
            }
        }

        // Render — Three.js handles all XR-specific GL state
        threeRenderer.render(threeScene, threeCamera)

        if (frameCount % 120 == 0) {
            console.log("WebXR: Frame $frameCount, scene objects: ${threeScene.children.size}")
        }
    }

    private fun handleSessionEnded() {
        xrSession = null
        xrReferenceSpace = null
        hitTestSource = null
        frameCount = 0
        lastFrameTime = 0.0

        // Stop the animation loop
        renderer?.setAnimationLoop(null)

        // Show the Compose canvas again
        composeCanvas?.style?.display = ""
        document.body?.style?.background = "linear-gradient(135deg, #667eea 0%, #764ba2 100%)"

        _state = EngineState.Ready
    }

    override fun stopSession() {
        xrSession?.end()
    }

    override fun dispose() {
        stopSession()

        scene = null
        camera = null
        renderer?.domElement?.remove()
        renderer = null

        nodeMap.clear()
        _state = EngineState.Disposed
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        frameCallback = callback
    }

    override fun submitScene(scene: SceneData) {
        val threeScene = this.scene ?: return

        // Remove old nodes that aren't in new scene
        val newNodeIds = collectNodeIds(scene.nodes)
        val toRemove = nodeMap.keys.filter { it !in newNodeIds }
        toRemove.forEach { id ->
            nodeMap[id]?.let { obj ->
                threeScene.remove(obj)
            }
            nodeMap.remove(id)
        }

        // Add or update nodes
        scene.nodes.forEach { node ->
            processSceneNodeIntoScene(node, threeScene)
        }
    }

    private fun processSceneNodeIntoScene(
        node: SceneNode,
        scene: Scene,
    ) {
        when (node) {
            is SceneNode.Mesh -> {
                val mesh =
                    nodeMap.getOrPut(node.id) {
                        createMesh(node)
                    }
                updateTransform(mesh, node.transform)
                mesh.visible = node.visible

                if (mesh.asDynamic().parent == null) {
                    scene.add(mesh)
                }
            }

            is SceneNode.Group -> {
                val group =
                    nodeMap.getOrPut(node.id) {
                        Group().also { it.name = node.id }
                    }
                updateTransform(group, node.transform)
                group.visible = node.visible

                if (group.asDynamic().parent == null) {
                    scene.add(group)
                }

                node.children.forEach { child ->
                    processSceneNode(child, group)
                }
            }
        }
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? = null

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
                normal = Vec3(0f, 1f, 0f),
                distance = hit.distance.toFloat(),
                nodeId = hit.`object`.name.takeIf { it.isNotEmpty() },
            )
        }
    }

    // === Private Implementation ===

    private fun processSceneNode(
        node: SceneNode,
        parent: Object3D,
    ) {
        when (node) {
            is SceneNode.Mesh -> {
                val mesh =
                    nodeMap.getOrPut(node.id) {
                        createMesh(node)
                    }
                updateTransform(mesh, node.transform)
                mesh.visible = node.visible

                if (mesh.asDynamic().parent == null) {
                    parent.add(mesh)
                }
            }

            is SceneNode.Group -> {
                val group =
                    nodeMap.getOrPut(node.id) {
                        Group().also { it.name = node.id }
                    }
                updateTransform(group, node.transform)
                group.visible = node.visible

                if (group.asDynamic().parent == null) {
                    parent.add(group)
                }

                node.children.forEach { child ->
                    processSceneNode(child, group)
                }
            }
        }
    }

    private fun createMesh(node: SceneNode.Mesh): Mesh {
        val geometry = createGeometry(node.geometry)
        val material = createMaterial(node.material)
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
                data.indices?.let { indices ->
                    val indicesArray = js("new Uint16Array(indices)")
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
     * to a single packed integer.
     */
    private fun createMaterial(data: MaterialData): Material {
        val r = (data.baseColor.x * 255).toInt().coerceIn(0, 255)
        val g = (data.baseColor.y * 255).toInt().coerceIn(0, 255)
        val b = (data.baseColor.z * 255).toInt().coerceIn(0, 255)
        val colorHex = (r shl 16) or (g shl 8) or b

        return when (data.type) {
            MaterialType.UNLIT -> {
                val params = js("{}")
                params.color = colorHex
                MeshBasicMaterial(params)
            }

            MaterialType.LIT, MaterialType.PBR -> {
                val params = js("{}")
                params.color = colorHex
                params.metalness = data.metallic.toDouble()
                params.roughness = data.roughness.toDouble()
                MeshStandardMaterial(params)
            }
        }
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
            lightEstimate = null,
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
                maxAnchors = 0,
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
}

// Extension to convert JS Float32Array to Kotlin FloatArray.
// The external `get` operator doesn't map to bracket indexing at runtime in IR,
// so we go through asDynamic() to access the native typed array elements.
private fun Float32Array.toFloatArray(): FloatArray {
    val jsArray = this.asDynamic()
    return FloatArray(length) { (jsArray[it] as Number).toFloat() }
}
