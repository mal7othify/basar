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
            // Create Three.js components
            scene = Scene()
            camera = PerspectiveCamera(75.0, 1.0, 0.1, 1000.0)

            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.id = "webxr-canvas"
            document.body?.appendChild(canvas)

            renderer =
                WebGLRenderer(
                    object {
                        val canvas = canvas
                        val antialias = true
                        val alpha = true
                    },
                ).apply {
                    setSize(window.innerWidth, window.innerHeight)
                    xr.enabled = true
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
        if (_state != EngineState.Ready) {
            throw IllegalStateException("Engine not ready. Current state: $_state")
        }

        val xrMode =
            when (mode) {
                is SessionMode.AR -> "immersive-ar"
                is SessionMode.VR -> "immersive-vr"
                is SessionMode.Passthrough -> "immersive-vr" // WebXR doesn't have explicit passthrough
            }

        val navigator = window.asDynamic().navigator as Navigator
        val xr =
            navigator.xr ?: run {
                _state = EngineState.Error(EngineError.NotSupported)
                return
            }

        // Request XR session
        xr
            .requestSession(
                xrMode,
                object {
                    val optionalFeatures = arrayOf("hit-test", "dom-overlay", "hand-tracking")
                },
            ).then { session ->
                handleSessionStarted(session, mode)
            }.catch { error ->
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
        xrSession = session

        // Set up session end handler
        session.onend = {
            handleSessionEnded()
        }

        // Request reference space
        session
            .requestReferenceSpace("local-floor")
            .then { referenceSpace ->
                xrReferenceSpace = referenceSpace

                // Set up hit test source for AR
                if (mode is SessionMode.AR) {
                    session
                        .requestHitTestSource(
                            object {
                                val space = referenceSpace
                            },
                        ).then { source ->
                            hitTestSource = source
                        }.catch { }
                }

                // Configure WebGL layer
                val gl =
                    renderer?.domElement?.getContext(
                        "webgl2",
                        object {
                            val xrCompatible = true
                        },
                    )

                session.updateRenderState(
                    object {
                        val baseLayer = XRWebGLLayer(session, gl)
                    },
                )

                // Start animation loop
                renderer?.setAnimationLoop { time, frame ->
                    if (frame != null) {
                        onXRFrame(time, frame.unsafeCast<XRFrame>())
                    }
                }

                _state = EngineState.Running(mode)
            }.catch { error ->
                _state =
                    EngineState.Error(
                        EngineError.SessionFailed("Failed to get reference space: $error"),
                    )
            }
    }

    private fun handleSessionEnded() {
        xrSession = null
        xrReferenceSpace = null
        hitTestSource = null
        _state = EngineState.Ready
    }

    override fun stopSession() {
        xrSession?.end()?.then {
            handleSessionEnded()
        }
        renderer?.setAnimationLoop(null)
    }

    override fun dispose() {
        stopSession()

        // Clean up Three.js resources
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

                // Process children
                node.children.forEach { child ->
                    processSceneNode(child, group)
                }
            }
        }
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        // WebXR hit testing is done per-frame via hitTestSource
        // Screen-space hit testing would need to be done via raycasting
        // For now, return null - this would be implemented based on frame hit test results
        return null
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
                normal = Vec3(0f, 1f, 0f), // Would need to calculate from face
                distance = hit.distance.toFloat(),
                nodeId = hit.`object`.name.takeIf { it.isNotEmpty() },
            )
        }
    }

    // === Private Implementation ===

    private fun onXRFrame(
        time: Double,
        frame: XRFrame,
    ) {
        val referenceSpace = xrReferenceSpace ?: return
        val session = frame.session
        val threeScene = scene ?: return
        val threeCamera = camera ?: return
        val threeRenderer = renderer ?: return

        // Get viewer pose
        val pose = frame.getViewerPose(referenceSpace) ?: return

        // Calculate delta time
        val deltaTime =
            if (lastFrameTime > 0) {
                ((time - lastFrameTime) / 1000.0).toFloat()
            } else {
                0.0f
            }
        lastFrameTime = time

        // Convert to FrameData
        val frameData = convertToFrameData(time, deltaTime, pose, frame, session)

        // Call user callback
        frameCallback?.invoke(frameData)

        // Render
        threeRenderer.render(threeScene, threeCamera)
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
                viewerPose.views
                    .first()
                    .projectionMatrix
                    .toFloatArray(),
            viewMatrix = transform.matrix.toFloatArray(),
            trackingState = TrackingState.NORMAL,
            detectedPlanes = emptyList(), // WebXR doesn't expose plane detection directly
            lightEstimate = null,
            inputSources = convertInputSources(session.inputSources, frame),
            anchors = emptyList(), // Would track anchors separately
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
                        touches = emptyList(), // Would need to track touch events separately
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

                // Process children
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

    private fun createMaterial(data: MaterialData): Material =
        when (data.type) {
            MaterialType.UNLIT -> {
                MeshBasicMaterial(
                    object {
                        val color =
                            object {
                                val r = data.baseColor.x.toDouble()
                                val g = data.baseColor.y.toDouble()
                                val b = data.baseColor.z.toDouble()
                            }
                    },
                )
            }

            MaterialType.LIT, MaterialType.PBR -> {
                MeshStandardMaterial(
                    object {
                        val color =
                            object {
                                val r = data.baseColor.x.toDouble()
                                val g = data.baseColor.y.toDouble()
                                val b = data.baseColor.z.toDouble()
                            }
                        val metalness = data.metallic.toDouble()
                        val roughness = data.roughness.toDouble()
                    },
                )
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
                supportsPlaneDetection = supportsAR, // AR implies some plane detection
                supportsImageTracking = false,
                supportsFaceTracking = false, // WebXR doesn't standardize face tracking
                supportsHandTracking = true, // WebXR supports hand tracking
                supportsDepthOcclusion = false,
                supportsSpatialAnchors = supportsAR,
                maxAnchors = 0, // Unlimited in WebXR
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

// Extension to convert Float32Array to FloatArray
private fun Float32Array.toFloatArray(): FloatArray = FloatArray(length) { get(it) }
