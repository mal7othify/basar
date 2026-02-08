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
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.PlaneType
import com.spatialkit.engine.Pose
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
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
import platform.Foundation.NSTimer
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIInterfaceOrientationPortrait
import platform.darwin.simd_float3
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
            arSession = ARSession()
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
    }

    fun updateViewportSize(
        width: Double,
        height: Double,
    ) {
        viewportSize = CGSizeMake(width, height)
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

            session.runWithConfiguration(arConfig)
            state = EngineState.Running(mode)
            println("ARKit session started: $mode")

            startFrameLoop()
        } catch (e: Exception) {
            state =
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
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getProjectionMatrix(camera: ARCamera): FloatArray {
        val viewport = viewportSize ?: return FloatArray(16) { if (it % 5 == 0) 1f else 0f } // Identity fallback

        // Get projection matrix for the viewport
        // Using reasonable near/far planes for AR
        val nearZ = 0.01 // 1cm
        val farZ = 1000.0 // 1km

        // Use useContents to extract the CGSize value for the interop call
        val projectionMatrix = viewport.useContents {
            camera.projectionMatrixForOrientation(
                orientation = UIInterfaceOrientationPortrait,
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
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getViewMatrix(camera: ARCamera): FloatArray {
        val viewport = viewportSize ?: return FloatArray(16) { if (it % 5 == 0) 1f else 0f }

        val viewMatrix =
            camera.viewMatrixForOrientation(
                orientation = UIInterfaceOrientationPortrait,
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
        // TODO: integrate with SceneKit/Metal
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
        state = EngineState.Uninitialized
        println("ARKitEngine disposed")
    }
}

fun createARKitEngine(): SpatialEngine = ARKitEngine()

fun isARKitAvailable(): Boolean = ARWorldTrackingConfiguration.isSupported
