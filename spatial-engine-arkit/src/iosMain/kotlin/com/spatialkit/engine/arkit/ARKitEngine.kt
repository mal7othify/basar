package com.spatialkit.engine.arkit

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.engine.DetectedPlane
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.MaterialData
import com.spatialkit.engine.MaterialType
import com.spatialkit.engine.PlaneType
import com.spatialkit.engine.Pose
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.TrackingState
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.Vector128
import kotlinx.cinterop.Vector128Var
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.ARKit.ARAnchor
import platform.ARKit.ARCamera
import platform.ARKit.ARConfiguration
import platform.ARKit.ARFaceTrackingConfiguration
import platform.ARKit.ARFrame
import platform.ARKit.ARFrameSemanticPersonSegmentationWithDepth
import platform.ARKit.ARHitTestResult
import platform.ARKit.ARHitTestResultTypeExistingPlaneUsingExtent
import platform.ARKit.ARImageTrackingConfiguration
import platform.ARKit.ARPlaneAnchor
import platform.ARKit.ARPlaneAnchorAlignment
import platform.ARKit.ARPlaneDetectionHorizontal
import platform.ARKit.ARPlaneDetectionVertical
import platform.ARKit.ARRaycastQuery
import platform.ARKit.ARRaycastResult
import platform.ARKit.ARRaycastTarget
import platform.ARKit.ARRaycastTargetAlignment
import platform.ARKit.ARSCNView
import platform.ARKit.ARSession
import platform.ARKit.ARTrackingState
import platform.ARKit.ARWorldAlignment
import platform.ARKit.ARWorldTrackingConfiguration
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNumber
import platform.Foundation.NSTimer
import platform.Foundation.numberWithFloat
import platform.QuartzCore.CACurrentMediaTime
import platform.SceneKit.SCNBox
import platform.SceneKit.SCNCapsule
import platform.SceneKit.SCNCylinder
import platform.SceneKit.SCNLightingModelPhysicallyBased
import platform.SceneKit.SCNMaterial
import platform.SceneKit.SCNNode
import platform.SceneKit.SCNPlane
import platform.SceneKit.SCNScene
import platform.SceneKit.SCNSphere
import platform.SceneKit.SCNVector3Make
import platform.SceneKit.SCNVector4Make
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.darwin.simd_float4x4
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
class ARKitEngine : SpatialEngine {
    private var arSession: ARSession? = null
    private var arView: ARSCNView? = null
    private var currentConfig: EngineConfig? = null
    private var sessionMode: SessionMode? = null

    private var frameCallback: ((FrameData) -> Unit)? = null
    private var lastFrameTime: Long = 0L

    private var viewportSize: CValue<CGSize>? = null
    private var currentOrientation: UIInterfaceOrientation = UIInterfaceOrientationPortrait

    // SceneKit node management
    private val contentRoot = SCNNode()
    private val sceneNodeMap = mutableMapOf<String, SCNNode>()
    private var currentScene: SceneData? = null

    override var state: EngineState = EngineState.Uninitialized
        private set

    override val capabilities: EngineCapabilities
        get() =
            EngineCapabilities(
                supportsAR = ARWorldTrackingConfiguration.isSupported,
                supportsVR = false, // iOS doesn't support native VR
                supportsPlaneDetection = ARWorldTrackingConfiguration.isSupported,
                supportsImageTracking = ARImageTrackingConfiguration.isSupported,
                supportsFaceTracking = ARFaceTrackingConfiguration.isSupported,
                supportsHandTracking = false, // ARKit doesn't provide generic hand tracking
                // depth occlusion (person-segmentation-with-depth) check
                supportsDepthOcclusion =
                    ARWorldTrackingConfiguration.supportsFrameSemantics(
                        ARFrameSemanticPersonSegmentationWithDepth,
                    ),
                // anchors exist; persistent / cloud anchors are separate concern
                supportsSpatialAnchors = true,
                maxAnchors = 100, // heuristic only; ARKit does not publish a hard limit
            )

    override suspend fun initialize(config: EngineConfig) {
        try {
            currentConfig = config
            // Only create a new session if setARView hasn't been called yet
            // (arView's session takes precedence)
            if (arSession == null) {
                arSession = ARSession()
                println("ARKitEngine: Created new ARSession")
            } else {
                println("ARKitEngine: Using existing ARSession from ARSCNView")
            }
            state = EngineState.Ready
            println("ARKitEngine initialized successfully")
        } catch (e: Exception) {
            state =
                EngineState.Error(
                    EngineError.InitializationFailed("ARKit initialization failed: ${e.message}"),
                )
            throw e
        }
    }

    /**
     * Set the ARSCNView for hit testing and rendering.
     * Call this after creating your ARSCNView in the UI layer.
     *
     * Example usage in iOS UI code (Swift/UIKit integration):
     * ```
     * class MyARViewController {
     *     val engine = ARKitEngine()
     *
     *     override fun viewDidLoad() {
     *         val arView = ARSCNView(frame = view.bounds)
     *         engine.setARView(arView)  // <-- User must call this
     *     }
     *
     *     override fun viewDidLayoutSubviews() {
     *         // Called when device rotates
     *         engine.updateViewportSize(view.bounds.width, view.bounds.height)
     *     }
     * }
     * ```
     */
    fun setARView(view: ARSCNView) {
        arView = view
        arSession = view.session
        viewportSize =
            view.bounds.useContents {
                CGSizeMake(size.width, size.height)
            }

        // Get initial interface orientation
        currentOrientation = getCurrentInterfaceOrientation()

        // Ensure the view has a scene, create one if needed
        if (view.scene == null) {
            view.scene = SCNScene()
        }

        // Attach content root to the AR scene
        view.scene?.rootNode?.addChildNode(contentRoot)

        // Enable default lighting for better visuals
        view.autoenablesDefaultLighting = true

        println("ARKitEngine: ARSCNView connected, session=${arSession != null}")
    }

    fun updateViewportSize(
        width: Double,
        height: Double,
    ) {
        viewportSize = CGSizeMake(width, height)
        // Update orientation based on current interface orientation
        currentOrientation = getCurrentInterfaceOrientation()
    }

    /**
     * Get the current interface orientation from the application.
     * This ensures we use the correct orientation for camera matrix calculations.
     */
    private fun getCurrentInterfaceOrientation(): UIInterfaceOrientation {
        // Try to get orientation from the ARView's window scene (iOS 13+)
        return try {
            val view = arView
            if (view != null) {
                // Get orientation from the view's window scene (most reliable)
                val windowScene = view.window?.windowScene
                val orientation = windowScene?.interfaceOrientation
                if (orientation != null) {
                    return orientation
                }
            }

            // Fallback: try from shared application (deprecated but still works)
            @Suppress("DEPRECATION")
            UIApplication.sharedApplication.statusBarOrientation
        } catch (e: Exception) {
            // Default to portrait if we can't determine orientation
            UIInterfaceOrientationPortrait
        }
    }

    override fun startSession(mode: SessionMode) {
        val session = arSession ?: throw IllegalStateException("Engine not initialized")
        val config = currentConfig ?: throw IllegalStateException("Engine not initialized")

        try {
            sessionMode = mode

            val arConfig: ARConfiguration? =
                when (mode) {
                    SessionMode.AR -> createARConfiguration(config)
                    else -> null
                }

            if (arConfig == null) return

            println("ARKitEngine: Starting session with config, arView=${arView != null}")
            session.runWithConfiguration(arConfig)
            state = EngineState.Running(mode)
            println("ARKit session started: $mode")

            startFrameLoop()
        } catch (e: Exception) {
            state =
                EngineState.Error(
                    EngineError.InitializationFailed("Failed to start session: ${e.message}"),
                )
            println("ARKitEngine: Failed to start session: ${e.message}")
        }
    }

    private fun createARConfiguration(config: EngineConfig): ARConfiguration {
        val arConfig = ARWorldTrackingConfiguration()

        // Plane detection
        if (config.enablePlaneDetection) {
            arConfig.planeDetection = ARPlaneDetectionHorizontal or ARPlaneDetectionVertical
        }

        // Light estimation
        if (config.enableLightEstimation) {
            arConfig.lightEstimationEnabled = true
        }

        // World alignment
        arConfig.worldAlignment = ARWorldAlignment.ARWorldAlignmentGravity

        return arConfig
    }

    private fun startFrameLoop() {
        // Poll currentFrame at a timer. Better: use ARSessionDelegate callbacks.
        NSTimer.scheduledTimerWithTimeInterval(
            interval = 1.0 / 60.0,
            repeats = true,
        ) { timer ->
            if (state !is EngineState.Running) {
                timer?.invalidate()
                return@scheduledTimerWithTimeInterval
            }

            val session = arSession ?: return@scheduledTimerWithTimeInterval
            val frame = session.currentFrame ?: return@scheduledTimerWithTimeInterval

            processFrame(frame)
        }
    }

    private fun processFrame(frame: ARFrame) {
        val currentTime = (CACurrentMediaTime() * 1000).toLong()
        val deltaTime =
            if (lastFrameTime > 0) {
                (currentTime - lastFrameTime) / 1000.0f
            } else {
                1.0f / 60.0f
            }
        lastFrameTime = currentTime

        val frameData = convertARFrameToFrameData(frame, currentTime, deltaTime)
        frameCallback?.invoke(frameData)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun convertARFrameToFrameData(
        frame: ARFrame,
        timestamp: Long,
        deltaTime: Float,
    ): FrameData {
        // Camera pose
        val transform: CValue<simd_float4x4> = frame.camera.transform
        val cameraPose = convertTransform(transform)

        // Tracking state
        val trackingState =
            when (frame.camera.trackingState) {
                ARTrackingState.ARTrackingStateNormal -> TrackingState.NORMAL
                ARTrackingState.ARTrackingStateLimited -> TrackingState.LIMITED
                ARTrackingState.ARTrackingStateNotAvailable -> TrackingState.NOT_AVAILABLE
                else -> TrackingState.NOT_AVAILABLE
            }

        // Prepare containers
        val detectedPlanes = mutableListOf<DetectedPlane>()
        val anchorsList = mutableListOf<AnchorState>()

        // frame.anchors is an NSArray - iterate safely
        val anchorsArray = frame.anchors
        for (i in 0 until anchorsArray.size) {
            val raw = anchorsArray[i] as? ARAnchor ?: continue
            if (raw is ARPlaneAnchor) {
                detectedPlanes.add(convertPlaneAnchor(raw))
                anchorsList.add(
                    AnchorState(
                        id = raw.identifier.UUIDString,
                        pose = convertTransform(raw.transform),
                        status = AnchorStatus.TRACKING,
                    ),
                )
            } else {
                // generic anchor
                anchorsList.add(
                    AnchorState(
                        id = raw.identifier.UUIDString,
                        pose = convertTransform(raw.transform),
                        status = AnchorStatus.TRACKING,
                    ),
                )
            }
        }

        // projection/view matrices: provide placeholders (16 floats). You can fill these from camera later.
        val projectionMatrix = getProjectionMatrix(frame.camera)
        val viewMatrix = getViewMatrix(frame.camera)

        // Get light estimate if available
        val lightEstimate =
            frame.lightEstimate?.let { light ->
                com.spatialkit.engine.LightEstimate(
                    ambientIntensity = (light.ambientIntensity / 1000.0).toFloat(), // Normalize to ~0-1
                    ambientColor = Vec3(1f, 1f, 1f), // ARKit doesn't provide color directly in basic estimate
                    directionalIntensity = 0f,
                    directionalDirection = Vec3.DOWN,
                )
            }

        return FrameData(
            timestamp = timestamp,
            deltaTime = deltaTime,
            cameraPose = cameraPose,
            projectionMatrix = projectionMatrix,
            viewMatrix = viewMatrix,
            trackingState = trackingState,
            detectedPlanes = detectedPlanes,
            lightEstimate = lightEstimate,
            inputSources = emptyList(),
            anchors = anchorsList,
        )
    }

    /**
     * Extract projection matrix from ARCamera.
     * Uses current interface orientation to ensure correct camera perspective.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getProjectionMatrix(camera: ARCamera): FloatArray {
        val viewport = viewportSize ?: return FloatArray(16) { if (it % 5 == 0) 1f else 0f } // Identity fallback

        // Get projection matrix for the viewport
        // Using reasonable near/far planes for AR
        val nearZ = 0.01 // 1cm
        val farZ = 1000.0 // 1km

        // Use useContents to extract the CGSize value for the interop call
        // Use current orientation instead of hardcoded portrait
        val projectionMatrix =
            viewport.useContents {
                camera.projectionMatrixForOrientation(
                    orientation = currentOrientation,
                    viewportSize = CGSizeMake(width, height),
                    zNear = nearZ,
                    zFar = farZ,
                )
            }

        return convertMatrixToFloatArray(projectionMatrix)
    }

    /**
     * Extract view matrix from ARCamera.
     * The view matrix is the inverse of the camera transform.
     * Uses current interface orientation to ensure correct camera perspective.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getViewMatrix(camera: ARCamera): FloatArray {
        val viewport = viewportSize ?: return FloatArray(16) { if (it % 5 == 0) 1f else 0f }

        // Use current orientation instead of hardcoded portrait
        val viewMatrix =
            camera.viewMatrixForOrientation(
                orientation = currentOrientation,
            )

        return convertMatrixToFloatArray(viewMatrix)
    }

    /**
     * Convert simd_float4x4 to column-major FloatArray.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun convertMatrixToFloatArray(matrix: CValue<simd_float4x4>): FloatArray {
        val result = FloatArray(16)
        matrix.useContents {
            for (col in 0 until 4) {
                val colPtr = columns[col].ptr.reinterpret<FloatVar>()
                for (row in 0 until 4) {
                    result[col * 4 + row] = colPtr[row]
                }
            }
        }
        return result
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun convertTransform(matrix: CValue<simd_float4x4>): Pose =
        matrix.useContents {
            // columns[3] contains translation (4th column of the matrix)
            // Reinterpret the Vector128 pointer as a float pointer to access components
            val col3Ptr = columns[3].ptr.reinterpret<FloatVar>()

            val position =
                Vec3(
                    x = col3Ptr[0],
                    y = col3Ptr[1],
                    z = col3Ptr[2],
                )

            val rotation = matrixToQuaternion(this)
            Pose(position, rotation)
        }

    @OptIn(ExperimentalForeignApi::class)
    private fun matrixToQuaternion(m: simd_float4x4): Quaternion {
        // Reinterpret each column as float pointers
        val col0 = m.columns[0].ptr.reinterpret<FloatVar>()
        val col1 = m.columns[1].ptr.reinterpret<FloatVar>()
        val col2 = m.columns[2].ptr.reinterpret<FloatVar>()

        // col[0] = x, col[1] = y, col[2] = z, col[3] = w
        // Matrix diagonal: col0[0], col1[1], col2[2]
        val m00 = col0[0]
        val m01 = col1[0]
        val m02 = col2[0]
        val m10 = col0[1]
        val m11 = col1[1]
        val m12 = col2[1]
        val m20 = col0[2]
        val m21 = col1[2]
        val m22 = col2[2]

        val trace = m00 + m11 + m22

        return when {
            trace > 0f -> {
                val s = sqrt((trace + 1.0f).toDouble()).toFloat() * 2f
                Quaternion(
                    w = 0.25f * s,
                    x = (m21 - m12) / s,
                    y = (m02 - m20) / s,
                    z = (m10 - m01) / s,
                )
            }

            (m00 > m11) && (m00 > m22) -> {
                val s = sqrt((1.0f + m00 - m11 - m22).toDouble()).toFloat() * 2f
                Quaternion(
                    w = (m21 - m12) / s,
                    x = 0.25f * s,
                    y = (m01 + m10) / s,
                    z = (m02 + m20) / s,
                )
            }

            m11 > m22 -> {
                val s = sqrt((1.0f + m11 - m00 - m22).toDouble()).toFloat() * 2f
                Quaternion(
                    w = (m02 - m20) / s,
                    x = (m01 + m10) / s,
                    y = 0.25f * s,
                    z = (m12 + m21) / s,
                )
            }

            else -> {
                val s = sqrt((1.0f + m22 - m00 - m11).toDouble()).toFloat() * 2f
                Quaternion(
                    w = (m10 - m01) / s,
                    x = (m02 + m20) / s,
                    y = (m12 + m21) / s,
                    z = 0.25f * s,
                )
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun convertPlaneAnchor(planeAnchor: ARPlaneAnchor): DetectedPlane {
        val type =
            when (planeAnchor.alignment) {
                ARPlaneAnchorAlignment.ARPlaneAnchorAlignmentHorizontal -> PlaneType.HORIZONTAL_UP
                ARPlaneAnchorAlignment.ARPlaneAnchorAlignmentVertical -> PlaneType.VERTICAL
                else -> PlaneType.UNKNOWN
            }

        val center = convertTransform(planeAnchor.transform)
        val extent: Vector128 = planeAnchor.extent
        val extentX = extent.getFloatAt(0)
        val extentZ = extent.getFloatAt(2)
        val halfX = extentX / 2f
        val halfZ = extentZ / 2f
        val vertices =
            listOf(
                Vec3(x = -halfX, y = 0f, z = -halfZ), // Bottom-left
                Vec3(x = halfX, y = 0f, z = -halfZ), // Bottom-right
                Vec3(x = halfX, y = 0f, z = halfZ), // Top-right
                Vec3(x = -halfX, y = 0f, z = halfZ), // Top-left
            )

        return DetectedPlane(
            id = planeAnchor.identifier.UUIDString,
            type = type,
            centerPose = center,
            extentX = extentX,
            extentZ = extentZ,
            vertices = vertices,
        )
    }

    override fun submitScene(scene: SceneData) {
        currentScene = scene

        // Collect all node IDs in the new scene
        val newNodeIds = mutableSetOf<String>()
        collectNodeIds(scene.nodes, newNodeIds)

        // Remove nodes that are no longer in the scene
        val toRemove = sceneNodeMap.keys - newNodeIds
        toRemove.forEach { id ->
            sceneNodeMap[id]?.removeFromParentNode()
            sceneNodeMap.remove(id)
        }

        // Create or update nodes
        scene.nodes.forEach { node ->
            processSceneNode(node, contentRoot)
        }
    }

    private fun collectNodeIds(
        nodes: List<SceneNode>,
        ids: MutableSet<String>,
    ) {
        nodes.forEach { node ->
            ids.add(node.id)
            if (node is SceneNode.Group) {
                collectNodeIds(node.children, ids)
            }
        }
    }

    private fun processSceneNode(
        node: SceneNode,
        parent: SCNNode,
    ) {
        when (node) {
            is SceneNode.Mesh -> processMeshNode(node, parent)
            is SceneNode.Group -> processGroupNode(node, parent)
        }
    }

    private fun processMeshNode(
        mesh: SceneNode.Mesh,
        parent: SCNNode,
    ) {
        val scnNode =
            sceneNodeMap[mesh.id] ?: run {
                val newNode = createSCNNodeForMesh(mesh)
                sceneNodeMap[mesh.id] = newNode
                parent.addChildNode(newNode)
                newNode
            }

        // Update transform
        updateNodeTransform(scnNode, mesh.transform)

        // Update visibility
        scnNode.hidden = !mesh.visible

        // Update material if needed
        updateNodeMaterial(scnNode, mesh.material)
    }

    private fun processGroupNode(
        group: SceneNode.Group,
        parent: SCNNode,
    ) {
        val scnNode =
            sceneNodeMap[group.id] ?: run {
                val newNode = SCNNode()
                sceneNodeMap[group.id] = newNode
                parent.addChildNode(newNode)
                newNode
            }

        // Update transform
        updateNodeTransform(scnNode, group.transform)

        // Update visibility
        scnNode.hidden = !group.visible

        // Process children
        group.children.forEach { child ->
            processSceneNode(child, scnNode)
        }
    }

    private fun createSCNNodeForMesh(mesh: SceneNode.Mesh): SCNNode {
        val geometry = createGeometry(mesh.geometry.type, mesh.transform.scale)
        val node = SCNNode.nodeWithGeometry(geometry)

        // Apply material
        geometry?.firstMaterial = createMaterial(mesh.material)

        return node
    }

    private fun createGeometry(
        type: GeometryType,
        scale: Vec3,
    ): platform.SceneKit.SCNGeometry? =
        when (type) {
            GeometryType.CUBE -> {
                // Unit cube scaled by transform
                SCNBox.boxWithWidth(
                    width = scale.x.toDouble(),
                    height = scale.y.toDouble(),
                    length = scale.z.toDouble(),
                    chamferRadius = 0.0,
                )
            }

            GeometryType.SPHERE -> {
                // Sphere uses average of scale for radius
                val radius = (scale.x + scale.y + scale.z) / 3f
                SCNSphere.sphereWithRadius(radius = (radius / 2f).toDouble())
            }

            GeometryType.CYLINDER -> {
                // Cylinder: x/z for radius, y for height
                val radius = (scale.x + scale.z) / 4f // diameter to radius
                SCNCylinder.cylinderWithRadius(
                    radius = radius.toDouble(),
                    height = scale.y.toDouble(),
                )
            }

            GeometryType.PLANE -> {
                // Plane: x for width, z for height (y is typically 1)
                SCNPlane.planeWithWidth(
                    width = scale.x.toDouble(),
                    height = scale.z.toDouble(),
                )
            }

            GeometryType.CAPSULE -> {
                // Capsule: similar to cylinder
                val radius = (scale.x + scale.z) / 4f
                SCNCapsule.capsuleWithCapRadius(
                    capRadius = radius.toDouble(),
                    height = scale.y.toDouble(),
                )
            }

            GeometryType.CUSTOM_MESH -> {
                // TODO: Implement custom mesh support
                null
            }
        }

    private fun createMaterial(materialData: MaterialData): SCNMaterial {
        val material = SCNMaterial()

        // Set base color
        val color =
            UIColor(
                red = materialData.baseColor.x.toDouble(),
                green = materialData.baseColor.y.toDouble(),
                blue = materialData.baseColor.z.toDouble(),
                alpha = materialData.baseColor.w.toDouble(),
            )
        material.diffuse.contents = color

        // Set PBR properties
        when (materialData.type) {
            MaterialType.PBR -> {
                material.lightingModelName = SCNLightingModelPhysicallyBased
                material.metalness.contents = NSNumber.numberWithFloat(materialData.metallic)
                material.roughness.contents = NSNumber.numberWithFloat(materialData.roughness)
            }

            MaterialType.LIT -> {
                // Default lighting model is fine for LIT
            }

            MaterialType.UNLIT -> {
                // Use emission for unlit appearance
                material.emission.contents = color
                material.diffuse.contents = UIColor.clearColor
            }
        }

        return material
    }

    private fun updateNodeTransform(
        node: SCNNode,
        transform: com.spatialkit.core.math.Transform,
    ) {
        // Set position
        node.position =
            SCNVector3Make(
                transform.position.x,
                transform.position.y,
                transform.position.z,
            )

        // Set rotation (quaternion to SCNVector4 - axis-angle representation)
        // SCNVector4 for rotation is (x, y, z, angle) where (x,y,z) is axis
        // We need to convert quaternion to axis-angle
        val q = transform.rotation
        val angle = 2f * kotlin.math.acos(q.w.coerceIn(-1f, 1f))
        val sinHalfAngle = sqrt(1f - q.w * q.w)

        if (sinHalfAngle > 0.001f) {
            node.rotation =
                SCNVector4Make(
                    q.x / sinHalfAngle,
                    q.y / sinHalfAngle,
                    q.z / sinHalfAngle,
                    angle,
                )
        } else {
            // No rotation or very small rotation
            node.rotation = SCNVector4Make(0f, 1f, 0f, 0f)
        }

        // Note: Scale is already baked into geometry creation for primitives
        // For more complex scenarios, you might want to use node.scale
    }

    private fun updateNodeMaterial(
        node: SCNNode,
        materialData: MaterialData,
    ) {
        node.geometry?.firstMaterial?.let { material ->
            val color =
                UIColor(
                    red = materialData.baseColor.x.toDouble(),
                    green = materialData.baseColor.y.toDouble(),
                    blue = materialData.baseColor.z.toDouble(),
                    alpha = materialData.baseColor.w.toDouble(),
                )
            material.diffuse.contents = color
        }
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        frameCallback = callback
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        val view = arView ?: return null
        val bounds: CValue<CGRect> = view.bounds
        return bounds.useContents {
            val px = (screenX * this.size.width)
            val py = (screenY * this.size.height)
            val hits =
                view.hitTest(
                    CGPointMake(px, py),
                    types = ARHitTestResultTypeExistingPlaneUsingExtent,
                )

            if (hits.isEmpty()) return@useContents null

            val hit = hits[0] as ARHitTestResult

            val position =
                hit.worldTransform.useContents {
                    val col3Ptr = columns[3].ptr.reinterpret<FloatVar>()
                    Vec3(col3Ptr[0], col3Ptr[1], col3Ptr[2])
                }

            // Get planeId if hit is on a plane anchor
            val planeId = (hit.anchor as? ARPlaneAnchor)?.identifier?.UUIDString

            // Estimate normal based on plane type (ARKit doesn't provide normal directly)
            val normal =
                when ((hit.anchor as? ARPlaneAnchor)?.alignment) {
                    ARPlaneAnchorAlignment.ARPlaneAnchorAlignmentHorizontal -> {
                        Vec3.UP
                    }

                    ARPlaneAnchorAlignment.ARPlaneAnchorAlignmentVertical -> {
                        // For vertical planes, extract normal from plane transform
                        hit.anchor?.let { anchor ->
                            anchor.transform.useContents {
                                val col2Ptr = columns[2].ptr.reinterpret<FloatVar>()
                                Vec3(col2Ptr[0], col2Ptr[1], col2Ptr[2])
                            }
                        }
                    }

                    else -> {
                        null
                    }
                }

            HitTestResult(
                position = position,
                distance = hit.distance.toFloat(),
                normal = normal,
                planeId = planeId,
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        val session = arSession ?: return null
        val frame = session.currentFrame ?: return null // TODO: Test this
        val query =
            memScoped {
                val originPtr = allocArray<FloatVar>(4)
                originPtr[0] = origin.x
                originPtr[1] = origin.y
                originPtr[2] = origin.z
                originPtr[3] = 0f

                val dirPtr = allocArray<FloatVar>(4)
                dirPtr[0] = direction.x
                dirPtr[1] = direction.y
                dirPtr[2] = direction.z
                dirPtr[3] = 0f

                ARRaycastQuery(
                    origin = originPtr.reinterpret<Vector128Var>().pointed.value,
                    direction = dirPtr.reinterpret<Vector128Var>().pointed.value,
                    allowingTarget = ARRaycastTarget.ARRaycastTargetExistingPlaneGeometry,
                    alignment = ARRaycastTargetAlignment.ARRaycastTargetAlignmentAny,
                )
            }

        val results = session.raycast(query)

        if (results.isEmpty()) return null

        val hit = results[0] as ARRaycastResult

        val position =
            hit.worldTransform.useContents {
                val col3Ptr = columns[3].ptr.reinterpret<FloatVar>()
                Vec3(col3Ptr[0], col3Ptr[1], col3Ptr[2])
            }

        val normal =
            hit.worldTransform.useContents {
                val col2Ptr = columns[2].ptr.reinterpret<FloatVar>()
                Vec3(col2Ptr[0], col2Ptr[1], col2Ptr[2])
            }

        val distance = origin.distance(position)
        val nodeId = hit.anchor?.identifier?.UUIDString

        return RaycastResult(
            position = position,
            normal = normal,
            distance = distance,
            nodeId = nodeId,
        )
    }

    override fun stopSession() {
        arSession?.pause()
        state = EngineState.Ready
        println("ARKit session stopped")
    }

    override fun dispose() {
        arSession?.pause()
        arSession = null
        arView = null
        frameCallback = null
        currentConfig = null
        currentScene = null

        // Clean up SceneKit nodes
        contentRoot.removeFromParentNode()
        sceneNodeMap.values.forEach { it.removeFromParentNode() }
        sceneNodeMap.clear()

        state = EngineState.Uninitialized
        println("ARKitEngine disposed")
    }
}

fun createARKitEngine(): SpatialEngine = ARKitEngine()

fun isARKitAvailable(): Boolean = ARWorldTrackingConfiguration.isSupported
