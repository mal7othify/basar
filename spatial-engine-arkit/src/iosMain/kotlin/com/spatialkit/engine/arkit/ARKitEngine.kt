package com.spatialkit.engine.arkit

import com.spatialkit.core.log.SpatialLog
import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.engine.Aabb
import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.engine.DetectedPlane
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.MaterialData
import com.spatialkit.engine.MaterialType
import com.spatialkit.engine.PlaneType
import com.spatialkit.engine.Pose
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneBounds
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.TextureData
import com.spatialkit.engine.TextureFilter
import com.spatialkit.engine.TextureSampler
import com.spatialkit.engine.TextureWrap
import com.spatialkit.engine.TrackingState
import com.spatialkit.engine.generateVertexNormals
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.Vector128
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.ARKit.ARAnchor
import platform.ARKit.ARCamera
import platform.ARKit.ARConfiguration
import platform.ARKit.ARFaceTrackingConfiguration
import platform.ARKit.ARFrame
import platform.ARKit.ARHitTestResult
import platform.ARKit.ARHitTestResultTypeExistingPlaneUsingExtent
import platform.ARKit.ARImageTrackingConfiguration
import platform.ARKit.ARPlaneAnchor
import platform.ARKit.ARPlaneAnchorAlignment
import platform.ARKit.ARPlaneDetectionHorizontal
import platform.ARKit.ARPlaneDetectionVertical
import platform.ARKit.ARSCNView
import platform.ARKit.ARSCNViewDelegateProtocol
import platform.ARKit.ARSession
import platform.ARKit.ARTrackingState
import platform.ARKit.ARWorldAlignment
import platform.ARKit.ARWorldTrackingConfiguration
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawRadialGradient
import platform.CoreGraphics.CGFloatVar
import platform.CoreGraphics.CGGradientCreateWithColorComponents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.dataWithBytes
import platform.Foundation.numberWithFloat
import platform.QuartzCore.CACurrentMediaTime
import platform.SceneKit.SCNBox
import platform.SceneKit.SCNCapsule
import platform.SceneKit.SCNCylinder
import platform.SceneKit.SCNFilterModeLinear
import platform.SceneKit.SCNFilterModeNearest
import platform.SceneKit.SCNFilterModeNone
import platform.SceneKit.SCNGeometry
import platform.SceneKit.SCNGeometryElement
import platform.SceneKit.SCNGeometryPrimitiveTypeTriangles
import platform.SceneKit.SCNGeometrySource
import platform.SceneKit.SCNGeometrySourceSemanticNormal
import platform.SceneKit.SCNGeometrySourceSemanticTexcoord
import platform.SceneKit.SCNGeometrySourceSemanticVertex
import platform.SceneKit.SCNHitTestResult
import platform.SceneKit.SCNLight
import platform.SceneKit.SCNLightTypeAmbient
import platform.SceneKit.SCNLightTypeDirectional
import platform.SceneKit.SCNLightingModelConstant
import platform.SceneKit.SCNLightingModelPhysicallyBased
import platform.SceneKit.SCNMaterial
import platform.SceneKit.SCNMaterialProperty
import platform.SceneKit.SCNNode
import platform.SceneKit.SCNPlane
import platform.SceneKit.SCNScene
import platform.SceneKit.SCNSceneRendererProtocol
import platform.SceneKit.SCNSphere
import platform.SceneKit.SCNVector3Make
import platform.SceneKit.SCNVector4Make
import platform.SceneKit.SCNWrapMode
import platform.SceneKit.SCNWrapModeClamp
import platform.SceneKit.SCNWrapModeMirror
import platform.SceneKit.SCNWrapModeRepeat
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.darwin.NSObject
import platform.darwin.simd_float4x4
import kotlin.math.sqrt

/**
 * ARSCNView delegate driving the engine's frame loop.
 *
 * Uses `renderer(_:updateAtTime:)` (inherited from SCNSceneRendererDelegate),
 * which SceneKit calls once per rendered frame. Note that
 * `renderer(_:didAddNode:forAnchor:)` is NOT a frame callback — it only fires
 * when ARKit attaches a node for a newly detected anchor.
 */
@OptIn(ExperimentalForeignApi::class)
private class ARViewDelegate(
    private val engine: ARKitEngine,
) : NSObject(),
    ARSCNViewDelegateProtocol {
    override fun renderer(
        renderer: SCNSceneRendererProtocol,
        updateAtTime: Double,
    ) {
        engine.onRenderTick()
    }
}

@OptIn(ExperimentalForeignApi::class)
public class ARKitEngine : SpatialEngine {
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

    // M9.1 explicit light rig (replaces autoenablesDefaultLighting so the
    // ARKit light estimate can drive intensities)
    private val ambientLight = SCNLight()
    private val directionalLight = SCNLight()
    private val ambientLightNode = SCNNode()
    private val directionalLightNode = SCNNode()

    // M9.2 blob shadow nodes under contentRoot, keyed by caster node id
    private val shadowNodeMap = mutableMapOf<String, SCNNode>()
    private var blobShadowImage: UIImage? = null

    // Cached local AABBs for custom meshes (vertex scans are O(n) and
    // submitScene runs per frame)
    private val customMeshLocalBounds = mutableMapOf<String, Aabb?>()

    // Last applied material per node id, to skip redundant per-frame
    // SCNMaterial updates (each one invalidates SceneKit's render caches)
    private val materialCache = mutableMapOf<String, MaterialData>()

    // Decoded UIImages keyed by TextureData.id, so meshes sharing a texture
    // decode it once (SceneKit dedupes the GPU upload for identical contents)
    private val imageCache = mutableMapOf<String, UIImage>()

    // Last applied transform scale per primitive-mesh node id (primitives
    // bake scale into geometry, so scale changes require a rebuild)
    private val appliedScales = mutableMapOf<String, Vec3>()

    // Texture ids that failed to decode; skipped instead of retried
    private val failedTextures = mutableSetOf<String>()
    private var currentScene: SceneData? = null

    // Delegate for frame updates
    private val viewDelegate = ARViewDelegate(this)

    private val _state = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override val capabilities: EngineCapabilities
        get() =
            EngineCapabilities(
                supportsAR = ARWorldTrackingConfiguration.isSupported,
                supportsVR = false, // iOS doesn't support native VR
                supportsPlaneDetection = ARWorldTrackingConfiguration.isSupported,
                supportsImageTracking = ARImageTrackingConfiguration.isSupported,
                supportsFaceTracking = ARFaceTrackingConfiguration.isSupported,
                supportsHandTracking = false, // ARKit doesn't provide generic hand tracking
                // The SceneKit backend cannot composite ARKit scene depth
                // over rendered content (that needs a custom Metal pipeline
                // or RealityKit), so environment occlusion is honestly
                // unsupported here. Person occlusion (a different feature)
                // is out of scope.
                supportsDepthOcclusion = false,
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
            }
            _state.value = EngineState.Ready
        } catch (e: Exception) {
            _state.value =
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
    public fun setARView(view: ARSCNView) {
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

        // M9.1: explicit ambient + directional rig instead of SceneKit's
        // automatic lights, so the ARKit light estimate can drive it (see
        // applyLightEstimate). Disable both automatics to avoid double
        // lighting.
        view.autoenablesDefaultLighting = false
        view.automaticallyUpdatesLighting = false
        setupLightRig(view)

        // Set the delegate for efficient frame updates
        view.delegate = viewDelegate
    }

    private fun setupLightRig(view: ARSCNView) {
        val root = view.scene?.rootNode ?: return

        ambientLight.type = SCNLightTypeAmbient
        ambientLight.intensity = NOMINAL_LUMENS
        ambientLightNode.light = ambientLight
        if (ambientLightNode.parentNode == null) {
            root.addChildNode(ambientLightNode)
        }

        directionalLight.type = SCNLightTypeDirectional
        directionalLight.intensity = NOMINAL_LUMENS
        directionalLightNode.light = directionalLight
        // Shine down and slightly forward (SceneKit directional lights emit
        // along the node's -Z axis)
        directionalLightNode.eulerAngles = SCNVector3Make((-kotlin.math.PI / 3).toFloat(), 0f, 0f)
        if (directionalLightNode.parentNode == null) {
            root.addChildNode(directionalLightNode)
        }
    }

    /**
     * Feeds the sampled ARKit light estimate into the rig (M9.1). ARKit's
     * ambientIntensity is in lumens (1000 = nominal); the engine's
     * [com.spatialkit.engine.LightEstimate] is normalized to 1.0 = nominal,
     * so scale back up here. The color temperature shifts both lights.
     */
    private fun applyLightEstimate(
        normalizedIntensity: Float,
        colorTemperature: Float,
    ) {
        val lumens = (normalizedIntensity * NOMINAL_LUMENS).coerceIn(0.0, 2 * NOMINAL_LUMENS)
        ambientLight.intensity = lumens
        directionalLight.intensity = lumens
        ambientLight.temperature = colorTemperature.toDouble()
        directionalLight.temperature = colorTemperature.toDouble()
    }

    public fun updateViewportSize(
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

    override suspend fun startSession(mode: SessionMode) {
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

            session.runWithConfiguration(arConfig)
            _state.value = EngineState.Running(mode)
        } catch (e: Exception) {
            _state.value =
                EngineState.Error(
                    EngineError.InitializationFailed("Failed to start session: ${e.message}"),
                )
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

    /**
     * Per-render-tick frame data that does not require touching an ARFrame.
     */
    private class SampledFrameInfo(
        val trackingState: TrackingState,
        val detectedPlanes: List<DetectedPlane>,
        val anchors: List<AnchorState>,
        val lightEstimate: com.spatialkit.engine.LightEstimate?,
        val projectionMatrix: FloatArray,
        val viewMatrix: FloatArray,
    )

    private var tickCount = 0L
    private var sampledFrameInfo: SampledFrameInfo? = null

    /**
     * Drive one frame of the engine. Called by the ARViewDelegate on each
     * SceneKit render tick (~60 fps).
     *
     * IMPORTANT: an ARFrame accessed from Kotlin stays retained until the
     * Kotlin/Native GC collects its wrapper. Touching `session.currentFrame`
     * every tick therefore starves ARKit's fixed camera-buffer pool, which
     * shows up as "No video frame received. Dropping frame!" logs and a
     * collapsed frame rate. So the ARFrame is sampled only occasionally (for
     * anchors/planes/light, which change slowly) inside an autorelease pool,
     * while the per-tick camera pose comes from SceneKit's pointOfView node —
     * no ARFrame needed.
     */
    @OptIn(kotlinx.cinterop.BetaInteropApi::class)
    internal fun onRenderTick() {
        // Only process frames when session is running
        if (_state.value !is EngineState.Running) return

        val currentTime = (CACurrentMediaTime() * 1000).toLong()
        val deltaTime =
            if (lastFrameTime > 0) {
                (currentTime - lastFrameTime) / 1000.0f
            } else {
                1.0f / 60.0f
            }
        lastFrameTime = currentTime

        if (sampledFrameInfo == null || tickCount % FRAME_SAMPLE_INTERVAL == 0L) {
            autoreleasepool {
                arSession?.currentFrame?.let { frame ->
                    sampledFrameInfo = sampleFrameInfo(frame)
                }
            }
        }
        tickCount++

        val info = sampledFrameInfo ?: return
        val cameraPose = currentCameraPose() ?: return

        frameCallback?.invoke(
            FrameData(
                timestamp = currentTime,
                deltaTime = deltaTime,
                cameraPose = cameraPose,
                projectionMatrix = info.projectionMatrix,
                viewMatrix = info.viewMatrix,
                trackingState = info.trackingState,
                detectedPlanes = info.detectedPlanes,
                lightEstimate = info.lightEstimate,
                inputSources = emptyList(),
                anchors = info.anchors,
            ),
        )
    }

    /**
     * Camera pose from SceneKit's point-of-view node (kept in sync with the
     * ARKit camera by ARSCNView). Reading it does not retain an ARFrame.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun currentCameraPose(): Pose? {
        val pointOfView = arView?.pointOfView ?: return null
        return pointOfView.worldTransform.useContents {
            // SCNMatrix4: basis vectors are rows (m11,m12,m13 = X axis),
            // translation is (m41,m42,m43) — same memory layout as
            // simd_float4x4 columns.
            val position = Vec3(m41, m42, m43)
            val rotation =
                quaternionFromRotationElements(
                    m00 = m11,
                    m01 = m21,
                    m02 = m31,
                    m10 = m12,
                    m11 = m22,
                    m12 = m32,
                    m20 = m13,
                    m21 = m23,
                    m22 = m33,
                )
            Pose(position, rotation)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun sampleFrameInfo(frame: ARFrame): SampledFrameInfo {
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

        // Light estimate: normalize lumens so 1.0 = nominal (the cross-
        // platform LightEstimate contract), and feed it into the SceneKit
        // light rig (M9.1)
        val lightEstimate =
            frame.lightEstimate?.let { light ->
                applyLightEstimate(
                    normalizedIntensity = (light.ambientIntensity / NOMINAL_LUMENS).toFloat(),
                    colorTemperature = light.ambientColorTemperature.toFloat(),
                )
                com.spatialkit.engine.LightEstimate(
                    ambientIntensity = (light.ambientIntensity / NOMINAL_LUMENS).toFloat(),
                    ambientColor = Vec3(1f, 1f, 1f), // ARKit reports temperature, not RGB
                    directionalIntensity = 0f,
                    directionalDirection = Vec3.DOWN,
                )
            }

        return SampledFrameInfo(
            trackingState = trackingState,
            detectedPlanes = detectedPlanes,
            anchors = anchorsList,
            lightEstimate = lightEstimate,
            projectionMatrix = projectionMatrix,
            viewMatrix = viewMatrix,
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
        return quaternionFromRotationElements(
            m00 = col0[0],
            m01 = col1[0],
            m02 = col2[0],
            m10 = col0[1],
            m11 = col1[1],
            m12 = col2[1],
            m20 = col0[2],
            m21 = col1[2],
            m22 = col2[2],
        )
    }

    @Suppress("LongParameterList")
    private fun quaternionFromRotationElements(
        m00: Float,
        m01: Float,
        m02: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m20: Float,
        m21: Float,
        m22: Float,
    ): Quaternion {
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
            materialCache.remove(id)
            appliedScales.remove(id)
            customMeshLocalBounds.remove(id)
        }

        // Drop cached images whose textures left the scene
        if (toRemove.isNotEmpty() && imageCache.isNotEmpty()) {
            val liveTextureIds = mutableSetOf<String>()
            collectTextureIds(scene.nodes, liveTextureIds)
            imageCache.keys.retainAll(liveTextureIds)
            failedTextures.retainAll(liveTextureIds)
        }

        // Create or update nodes
        scene.nodes.forEach { node ->
            processSceneNode(node, contentRoot)
        }

        updateBlobShadows(scene)
    }

    /**
     * Maintains one blob-shadow plane (M9.2) under each shadow-casting
     * subtree: a radial-gradient disc at the base of the subtree's world
     * AABB, sized slightly beyond its footprint. Runs per submitScene (once
     * per frame), so shadows follow dragged/scaled content.
     */
    private fun updateBlobShadows(scene: SceneData) {
        val casters =
            SceneBounds.shadowCasters(scene.nodes) { mesh ->
                if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
                    customMeshLocalBounds.getOrPut(mesh.id) { SceneBounds.localBounds(mesh.geometry) }
                } else {
                    SceneBounds.localBounds(mesh.geometry)
                }
            }

        val liveIds = mutableSetOf<String>()
        casters.forEach { (node, bounds) ->
            val size = bounds.size
            val diameter = maxOf(size.x, size.z) * SHADOW_FOOTPRINT_SCALE
            if (diameter <= 0f) return@forEach
            liveIds.add(node.id)

            val shadowNode = shadowNodeMap.getOrPut(node.id) { createBlobShadowNode() }
            shadowNode.position =
                SCNVector3Make(
                    bounds.center.x,
                    // Slightly above the base to avoid z-fighting the surface
                    bounds.min.y + 0.002f,
                    bounds.center.z,
                )
            // Unit plane scaled in its local XY (rotated flat, so XY spans XZ)
            shadowNode.scale = SCNVector3Make(diameter, diameter, 1f)
        }

        (shadowNodeMap.keys - liveIds).forEach { id ->
            shadowNodeMap.remove(id)?.removeFromParentNode()
        }
    }

    /**
     * A 1×1 SCNPlane rotated flat with a radial-gradient "blob" texture:
     * unlit, non-depth-writing, so it darkens the camera image under content
     * without ever occluding anything.
     */
    private fun createBlobShadowNode(): SCNNode {
        val plane = SCNPlane.planeWithWidth(1.0, height = 1.0)
        val material = SCNMaterial()
        material.diffuse.contents = blobShadowImage ?: createBlobShadowImage().also { blobShadowImage = it }
        material.lightingModelName = SCNLightingModelConstant
        material.writesToDepthBuffer = false
        material.doubleSided = true
        plane.firstMaterial = material

        val node = SCNNode.nodeWithGeometry(plane)
        // SCNPlane faces +Z; rotate to face +Y (flat on the ground)
        node.eulerAngles = SCNVector3Make((-kotlin.math.PI / 2).toFloat(), 0f, 0f)
        // Never intercept content raycasts (performRaycast walks contentRoot)
        node.categoryBitMask = 0uL
        contentRoot.addChildNode(node)
        return node
    }

    /**
     * Renders the blob-shadow gradient once with CoreGraphics: black fading
     * from [SHADOW_OPACITY] alpha at the center to transparent at the rim.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun createBlobShadowImage(): UIImage? {
        val sizePx = 128.0
        UIGraphicsBeginImageContextWithOptions(CGSizeMake(sizePx, sizePx), false, 1.0)
        val context = UIGraphicsGetCurrentContext()
        if (context == null) {
            UIGraphicsEndImageContext()
            return null
        }

        memScoped {
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            // RGBA stops: opaque-ish black core, soft mid falloff, clear rim
            val components =
                allocArray<CGFloatVar>(12).also {
                    val values =
                        doubleArrayOf(
                            0.0,
                            0.0,
                            0.0,
                            SHADOW_OPACITY,
                            0.0,
                            0.0,
                            0.0,
                            SHADOW_OPACITY * 0.75,
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                        )
                    values.forEachIndexed { i, v -> it[i] = v }
                }
            val locations =
                allocArray<CGFloatVar>(3).also {
                    it[0] = 0.0
                    it[1] = 0.55
                    it[2] = 1.0
                }
            val gradient = CGGradientCreateWithColorComponents(colorSpace, components, locations, 3uL)
            val center = CGPointMake(sizePx / 2, sizePx / 2)
            CGContextDrawRadialGradient(
                context,
                gradient,
                startCenter = center,
                startRadius = 0.0,
                endCenter = center,
                endRadius = sizePx / 2,
                options = 0u,
            )
        }

        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return image
    }

    private fun collectTextureIds(
        nodes: List<SceneNode>,
        ids: MutableSet<String>,
    ) {
        nodes.forEach { node ->
            when (node) {
                is SceneNode.Mesh -> node.material.baseColorTexture?.let { ids.add(it.id) }
                is SceneNode.Group -> collectTextureIds(node.children, ids)
            }
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

        // Custom meshes keep their vertices in meters, so node scale applies
        // directly (primitives instead bake scale into geometry dimensions
        // at creation time). This also makes scale animations work for them.
        if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
            scnNode.scale =
                SCNVector3Make(
                    mesh.transform.scale.x,
                    mesh.transform.scale.y,
                    mesh.transform.scale.z,
                )
        } else {
            // Primitives bake scale into the geometry, so a scale change
            // (e.g. a bounce animation) needs the geometry rebuilt — the
            // node transform alone won't resize them.
            val previousScale = appliedScales.put(mesh.id, mesh.transform.scale)
            if (previousScale != null && previousScale != mesh.transform.scale) {
                scnNode.geometry = createGeometry(mesh.geometry, mesh.transform.scale)
                updateNodeMaterial(scnNode, mesh.material, isCustomMesh = false)
            }
        }

        // Update visibility
        scnNode.hidden = !mesh.visible

        // Update material only when it actually changed
        if (materialCache[mesh.id] != mesh.material) {
            updateNodeMaterial(scnNode, mesh.material, mesh.geometry.type == GeometryType.CUSTOM_MESH)
            materialCache[mesh.id] = mesh.material
        }
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
        val geometry = createGeometry(mesh.geometry, mesh.transform.scale)
        val node = SCNNode.nodeWithGeometry(geometry)

        // Apply material
        val material = createMaterial(mesh.material)
        if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
            // The GL-based renderers don't cull back faces, so render custom
            // meshes double-sided here too for cross-platform parity (flat
            // meshes like leaves would otherwise vanish from behind).
            material.doubleSided = true
        }
        geometry?.firstMaterial = material

        return node
    }

    private fun createGeometry(
        data: GeometryData,
        scale: Vec3,
    ): platform.SceneKit.SCNGeometry? =
        when (data.type) {
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

            GeometryType.CUSTOM_MESH -> createCustomGeometry(data)
        }

    /**
     * Build an SCNGeometry from raw vertex/index buffers.
     *
     * Vertices are in meters (model space); unlike primitives, node scale is
     * NOT baked in here — it is applied as `node.scale` in [processMeshNode].
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun createCustomGeometry(data: GeometryData): platform.SceneKit.SCNGeometry? {
        val vertices = data.vertices ?: return null
        val indices = data.indices ?: return null
        if (vertices.isEmpty() || indices.isEmpty()) return null

        val vertexCount = vertices.size / 3
        val normals =
            data.normals?.takeIf { it.size == vertices.size }
                ?: generateVertexNormals(vertices, indices)

        // NSData copies the bytes, so pinning only needs to outlive the call
        fun FloatArray.toNSData(): NSData =
            usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), (size * Float.SIZE_BYTES).toULong())
            }

        val vertexSource =
            SCNGeometrySource.geometrySourceWithData(
                data = vertices.toNSData(),
                semantic = SCNGeometrySourceSemanticVertex,
                vectorCount = vertexCount.toLong(),
                floatComponents = true,
                componentsPerVector = 3,
                bytesPerComponent = Float.SIZE_BYTES.toLong(),
                dataOffset = 0,
                dataStride = (3 * Float.SIZE_BYTES).toLong(),
            )

        val normalSource =
            SCNGeometrySource.geometrySourceWithData(
                data = normals.toNSData(),
                semantic = SCNGeometrySourceSemanticNormal,
                vectorCount = vertexCount.toLong(),
                floatComponents = true,
                componentsPerVector = 3,
                bytesPerComponent = Float.SIZE_BYTES.toLong(),
                dataOffset = 0,
                dataStride = (3 * Float.SIZE_BYTES).toLong(),
            )

        // Texture coordinates (two components per vertex). SceneKit's texcoord
        // origin is top-left, same as glTF, so UVs pass through unchanged.
        val texcoordSource =
            data.uvs?.takeIf { it.size / 2 == vertexCount }?.let { uvs ->
                SCNGeometrySource.geometrySourceWithData(
                    data = uvs.toNSData(),
                    semantic = SCNGeometrySourceSemanticTexcoord,
                    vectorCount = vertexCount.toLong(),
                    floatComponents = true,
                    componentsPerVector = 2,
                    bytesPerComponent = Float.SIZE_BYTES.toLong(),
                    dataOffset = 0,
                    dataStride = (2 * Float.SIZE_BYTES).toLong(),
                )
            }

        val indexData =
            indices.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), (indices.size * Int.SIZE_BYTES).toULong())
            }
        val element =
            SCNGeometryElement.geometryElementWithData(
                data = indexData,
                primitiveType = SCNGeometryPrimitiveTypeTriangles,
                primitiveCount = (indices.size / 3).toLong(),
                bytesPerIndex = Int.SIZE_BYTES.toLong(),
            )

        return SCNGeometry.geometryWithSources(
            sources = listOfNotNull(vertexSource, normalSource, texcoordSource),
            elements = listOf(element),
        )
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
        val image = materialData.baseColorTexture?.let { getOrCreateImage(it) }
        val diffuseContents: Any = image ?: color
        material.diffuse.contents = diffuseContents
        materialData.baseColorTexture?.sampler?.let { applySampler(material.diffuse, it) }
        material.doubleSided = materialData.doubleSided

        // glTF multiplies baseColorFactor with the texture; SCNMaterial's
        // multiply slot provides exactly that.
        if (image != null && !materialData.baseColor.isWhite()) {
            material.multiply.contents = color
        }

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
                material.emission.contents = diffuseContents
                material.diffuse.contents = UIColor.blackColor
            }
        }

        return material
    }

    private fun com.spatialkit.engine.Vec4.isWhite(): Boolean = x == 1f && y == 1f && z == 1f && w == 1f

    /**
     * Decodes [data]'s encoded PNG/JPEG bytes into a UIImage, cached by id.
     * Returns null (and records the failure) if the bytes can't be decoded.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getOrCreateImage(data: TextureData): UIImage? {
        imageCache[data.id]?.let { return it }
        if (data.id in failedTextures || data.data.isEmpty()) return null

        val nsData =
            data.data.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), data.data.size.toULong())
            }
        val image = UIImage.imageWithData(nsData)
        if (image == null) {
            SpatialLog.warn(TAG) { "texture decode failed for '${data.id}' (${data.data.size} bytes)" }
            failedTextures.add(data.id)
            return null
        }
        imageCache[data.id] = image
        return image
    }

    private fun applySampler(
        property: SCNMaterialProperty,
        sampler: TextureSampler,
    ) {
        property.wrapS = sampler.wrapU.toSCN()
        property.wrapT = sampler.wrapV.toSCN()
        property.magnificationFilter =
            when (sampler.magFilter) {
                TextureFilter.NEAREST -> SCNFilterModeNearest
                TextureFilter.LINEAR -> SCNFilterModeLinear
            }
        when (sampler.minFilter) {
            TextureFilter.NEAREST -> {
                property.minificationFilter = SCNFilterModeNearest
                property.mipFilter = SCNFilterModeNone
            }
            TextureFilter.LINEAR -> {
                // Trilinear for quality at glancing angles
                property.minificationFilter = SCNFilterModeLinear
                property.mipFilter = SCNFilterModeLinear
            }
        }
    }

    private fun TextureWrap.toSCN(): SCNWrapMode =
        when (this) {
            TextureWrap.REPEAT -> SCNWrapModeRepeat
            TextureWrap.MIRRORED_REPEAT -> SCNWrapModeMirror
            TextureWrap.CLAMP_TO_EDGE -> SCNWrapModeClamp
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

    /**
     * Rebuilds the node's material from [materialData]. A full rebuild keeps
     * texture, sampler, and PBR state consistent in one place; it only runs
     * when the material actually changed (see [materialCache]).
     */
    private fun updateNodeMaterial(
        node: SCNNode,
        materialData: MaterialData,
        isCustomMesh: Boolean,
    ) {
        node.geometry?.let { geometry ->
            val material = createMaterial(materialData)
            if (isCustomMesh) {
                // Match the GL-based renderers, which don't cull back faces
                material.doubleSided = true
            }
            geometry.firstMaterial = material
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

    /**
     * Raycasts against **scene content** (the nodes submitted via
     * [submitScene]), matching the ARCore and WebXR engines. Real-world
     * surfaces are [performHitTest]'s job.
     *
     * Implemented with SceneKit's segment hit test under [contentRoot], so it
     * works for any geometry (primitives and custom meshes alike). Does not
     * touch `session.currentFrame` — a retained ARFrame starves ARKit's
     * camera buffer pool (see [onRenderTick]) and stalls rendering.
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        val start = SCNVector3Make(origin.x, origin.y, origin.z)
        val end =
            SCNVector3Make(
                origin.x + direction.x * RAYCAST_MAX_DISTANCE,
                origin.y + direction.y * RAYCAST_MAX_DISTANCE,
                origin.z + direction.z * RAYCAST_MAX_DISTANCE,
            )

        // contentRoot sits at identity under the scene root, so world-space
        // segment endpoints are valid in its local space, and the search is
        // limited to engine-submitted content (not ARKit's internal nodes).
        val hits = contentRoot.hitTestWithSegmentFromPoint(start, end, null)

        for (rawHit in hits) {
            val hit = rawHit as? SCNHitTestResult ?: continue
            val nodeId = nodeIdFor(hit.node) ?: continue
            val position = hit.worldCoordinates.useContents { Vec3(x, y, z) }
            val normal = hit.worldNormal.useContents { Vec3(x, y, z) }
            return RaycastResult(
                position = position,
                normal = normal,
                distance = origin.distance(position),
                nodeId = nodeId,
            )
        }
        return null
    }

    /**
     * Resolves an SCNNode (or its nearest tracked ancestor) back to the
     * scene-node id it was created for.
     */
    private fun nodeIdFor(scnNode: SCNNode): String? {
        var node: SCNNode? = scnNode
        while (node != null) {
            val current = node
            sceneNodeMap.entries.firstOrNull { it.value === current }?.let { return it.key }
            node = current.parentNode
        }
        return null
    }

    override fun stopSession() {
        arSession?.pause()
        lastFrameTime = 0L
        sampledFrameInfo = null
        _state.value = EngineState.Ready
    }

    override fun dispose() {
        arSession?.pause()
        arSession = null
        arView = null
        frameCallback = null
        currentConfig = null
        currentScene = null
        sampledFrameInfo = null
        lastFrameTime = 0L

        // Clean up SceneKit nodes
        contentRoot.removeFromParentNode()
        sceneNodeMap.values.forEach { it.removeFromParentNode() }
        sceneNodeMap.clear()
        shadowNodeMap.values.forEach { it.removeFromParentNode() }
        shadowNodeMap.clear()
        ambientLightNode.removeFromParentNode()
        directionalLightNode.removeFromParentNode()
        materialCache.clear()
        imageCache.clear()
        failedTextures.clear()
        appliedScales.clear()
        customMeshLocalBounds.clear()

        _state.value = EngineState.Uninitialized
    }

    private companion object {
        const val TAG = "ARKitEngine"

        // Render ticks between ARFrame samples (~1 s at 60 fps). Anchors,
        // planes and light estimates change slowly; the camera pose is
        // refreshed every tick from pointOfView instead.
        const val FRAME_SAMPLE_INTERVAL = 60L

        // Segment length for scene-content raycasts (meters)
        const val RAYCAST_MAX_DISTANCE = 100f

        // ARKit's nominal ambient intensity in lumens; the engine-level
        // LightEstimate is normalized so this maps to 1.0
        const val NOMINAL_LUMENS = 1000.0

        // Blob shadows (M9.2): quad size relative to the caster's footprint,
        // and peak darkness at the blob center — matches the other renderers
        const val SHADOW_FOOTPRINT_SCALE = 1.25f
        const val SHADOW_OPACITY = 0.35
    }
}

public fun createARKitEngine(): SpatialEngine = ARKitEngine()

public fun isARKitAvailable(): Boolean = ARWorldTrackingConfiguration.isSupported
