package com.spatialkit.engine.arcore

import android.content.Context
import android.view.Surface
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.spatialkit.engine.AnchorRequest
import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.engine.AnchorType
import com.spatialkit.engine.DetectedPlane
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.LightEstimate
import com.spatialkit.engine.PlaneType
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import java.util.EnumSet
import kotlin.jvm.java
import com.google.ar.core.LightEstimate as ArLightEstimate
import com.spatialkit.engine.Pose as EnginePose

/**
 * ARCore implementation of SpatialEngine.
 *
 * Uses Google ARCore for AR capabilities and Filament for rendering.
 */
class ARCoreEngine(
    private val context: Context,
) : SpatialEngine {
    // ARCore components
    private var session: Session? = null
    private var config: Config? = null

    // OpenGL renderer
    private var renderer: SimpleRenderer? = null

    // State management
    private var _state: EngineState = EngineState.Uninitialized
    private var _capabilities: EngineCapabilities? = null
    private var currentConfig: EngineConfig? = null
    private var frameCallback: ((FrameData) -> Unit)? = null

    // Scene management
    private val anchorMap = mutableMapOf<String, Anchor>()
    private var currentScene: SceneData? = null

    // Frame timing
    private var lastFrameTime = 0L

    override val state: EngineState
        get() = _state

    override val capabilities: EngineCapabilities
        get() =
            _capabilities ?: EngineCapabilities(
                supportsAR = false,
                supportsVR = false, // ARCore doesn't support VR
                supportsPlaneDetection = false,
                supportsImageTracking = false,
                supportsFaceTracking = false,
                supportsHandTracking = false,
                supportsDepthOcclusion = false,
                supportsSpatialAnchors = false,
                maxAnchors = 0,
            )

    override suspend fun initialize(config: EngineConfig) =
        withContext(Dispatchers.Main) {
            if (_state != EngineState.Uninitialized) {
                throw IllegalStateException("Engine already initialized")
            }

            _state = EngineState.Initializing
            currentConfig = config

            try {
                // Check ARCore availability
                val availability = ArCoreApk.getInstance().checkAvailability(context)
                if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                    throw EngineError.NotSupported.toException()
                }

                // Create ARCore session
                val arSession =
                    Session(context, EnumSet.noneOf(Session.Feature::class.java)).also {
                        session = it
                    }

                // Configure session
                val arConfig =
                    Config(arSession).apply {
                        // Set light estimation mode
                        if (config.enableLightEstimation) {
                            lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                        }

                        // Set plane detection
                        planeFindingMode =
                            if (config.enablePlaneDetection) {
                                Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                            } else {
                                Config.PlaneFindingMode.DISABLED
                            }

                        // Set depth mode if supported
                        if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            depthMode = Config.DepthMode.AUTOMATIC
                        }

                        // Set focus mode
                        focusMode = Config.FocusMode.AUTO
                    }

                arSession.configure(arConfig)
                this@ARCoreEngine.config = arConfig

                // Initialize OpenGL renderer
                renderer = SimpleRenderer(context)

                // Check capabilities
                _capabilities = checkCapabilities(arSession)

                _state = EngineState.Ready
            } catch (e: UnavailableArcoreNotInstalledException) {
                _state =
                    EngineState.Error(
                        EngineError.InitializationFailed("ARCore not installed"),
                    )
                throw e
            } catch (e: UnavailableUserDeclinedInstallationException) {
                _state =
                    EngineState.Error(
                        EngineError.InitializationFailed("User declined ARCore installation"),
                    )
                throw e
            } catch (e: UnavailableApkTooOldException) {
                _state =
                    EngineState.Error(
                        EngineError.InitializationFailed("ARCore APK too old"),
                    )
                throw e
            } catch (e: UnavailableSdkTooOldException) {
                _state =
                    EngineState.Error(
                        EngineError.InitializationFailed("SDK too old for ARCore"),
                    )
                throw e
            } catch (e: UnavailableDeviceNotCompatibleException) {
                _state =
                    EngineState.Error(
                        EngineError.InitializationFailed("Device not compatible with ARCore"),
                    )
                throw e
            } catch (e: Exception) {
                _state =
                    EngineState.Error(
                        EngineError.InitializationFailed("Failed to initialize ARCore: ${e.message}"),
                    )
                throw e
            }
        }

    override fun startSession(mode: SessionMode) {
        if (_state != EngineState.Ready) {
            throw IllegalStateException("Engine not ready. Current state: $_state")
        }

        val arSession = session ?: throw IllegalStateException("ARCore session not initialized")

        // ARCore only supports AR mode
        if (mode !is SessionMode.AR) {
            _state =
                EngineState.Error(
                    EngineError.SessionFailed("ARCore only supports AR mode"),
                )
            return
        }

        try {
            // Resume ARCore session
            arSession.resume()
            _state = EngineState.Running(mode)
        } catch (e: CameraNotAvailableException) {
            _state =
                EngineState.Error(
                    EngineError.SessionFailed("Camera not available: ${e.message}"),
                )
        }
    }

    override fun stopSession() {
        val arSession = session ?: return

        try {
            arSession.pause()
            _state = EngineState.Ready
        } catch (e: Exception) {
            // Ignore errors on pause
        }
    }

    override fun dispose() {
        stopSession()

        // Clean up anchors
        anchorMap.values.forEach { it.detach() }
        anchorMap.clear()

        // Clean up renderer
        renderer?.destroy()
        renderer = null

        // Clean up ARCore
        session?.close()
        session = null
        config = null

        _state = EngineState.Disposed
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        frameCallback = callback
    }

    override fun submitScene(scene: SceneData) {
        currentScene = scene

        // Process anchor requests
        scene.anchors.forEach { anchorRequest ->
            createAnchor(anchorRequest)
        }

        // Update Filament scene
        renderer?.updateScene(scene)
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        val arSession: Session = session ?: return null
        val frame: Frame = arSession.update()

        // Perform ARCore hit test
        val hits: List<HitResult> = frame.hitTest(screenX, screenY)
        val hit: HitResult = hits.firstOrNull() ?: return null

        val pose: Pose = hit.hitPose
        val plane = hit.trackable as? Plane

        // Extract normal from plane's pose if available
        val normal =
            plane?.centerPose?.let { centerPose ->
                val yAxis = centerPose.yAxis // No parameters in newer API
                Vec3(yAxis[0], yAxis[1], yAxis[2])
            } ?: Vec3(0f, 1f, 0f)

        return HitTestResult(
            position = Vec3(pose.tx(), pose.ty(), pose.tz()),
            normal = normal,
            distance = hit.distance,
            planeId = plane?.let { "plane-${it.hashCode()}" },
        )
    }

    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        val arSession = session ?: return null
        val frame: Frame = arSession.update()

        // TODO: ARCore doesn't have direct world-space raycasting
        // Would need to implement using the renderer's scene raycasting
        // For now, return null
        return renderer?.performRaycast(origin, direction)
    }

    /**
     * Call this from the rendering surface (GLSurfaceView or SurfaceView)
     * to update the AR frame and invoke callbacks.
     */
    fun onDrawFrame(surface: Surface? = null) {
        val arSession = session ?: return
        val arRenderer = renderer ?: return

        if (_state !is EngineState.Running) return

        try {
            // Update ARCore session
            arSession.setCameraTextureName(arRenderer.getCameraTextureId())
            val frame = arSession.update()
            val camera = frame.camera

            // Skip if not tracking
            if (camera.trackingState != TrackingState.TRACKING) {
                return
            }

            // Calculate delta time
            val currentTime = System.currentTimeMillis()
            val deltaTime =
                if (lastFrameTime > 0) {
                    (currentTime - lastFrameTime) / 1000f
                } else {
                    0f
                }
            lastFrameTime = currentTime

            // Convert to FrameData
            val frameData = convertToFrameData(frame, deltaTime)

            // Invoke callback
            frameCallback?.invoke(frameData)

            // Render scene
            arRenderer.render(frame, camera)
        } catch (e: CameraNotAvailableException) {
            _state =
                EngineState.Error(
                    EngineError.SessionFailed("Camera not available during frame: ${e.message}"),
                )
        }
    }

    /**
     * Call this when the surface is created or changed.
     */
    fun onSurfaceChanged(
        width: Int,
        height: Int,
    ) {
        renderer?.resize(width, height)
        session?.setDisplayGeometry(0, width, height)
    }

    // === Private Implementation ===

    private fun convertToFrameData(
        frame: Frame,
        deltaTime: Float,
    ): FrameData {
        val camera = frame.camera
        val pose = camera.pose

        return FrameData(
            timestamp = frame.timestamp / 1_000_000, // Convert nanoseconds to milliseconds
            deltaTime = deltaTime,
            cameraPose =
                EnginePose(
                    position =
                        Vec3(
                            pose.tx(),
                            pose.ty(),
                            pose.tz(),
                        ),
                    rotation =
                        Quaternion(
                            pose.qx(),
                            pose.qy(),
                            pose.qz(),
                            pose.qw(),
                        ),
                ),
            projectionMatrix = getProjectionMatrix(camera),
            viewMatrix = getViewMatrix(camera),
            trackingState = camera.trackingState.toEngineTrackingState(),
            detectedPlanes = getDetectedPlanes(frame),
            lightEstimate = getLightEstimate(frame),
            inputSources = emptyList(), // ARCore doesn't have controller input
            anchors = getAnchorStates(frame),
        )
    }

    private fun getProjectionMatrix(camera: Camera): FloatArray {
        val matrix = FloatArray(16)
        camera.getProjectionMatrix(matrix, 0, 0.1f, 100f)
        return matrix
    }

    private fun getViewMatrix(camera: Camera): FloatArray {
        val matrix = FloatArray(16)
        camera.getViewMatrix(matrix, 0)
        return matrix
    }

    private fun getDetectedPlanes(frame: Frame): List<DetectedPlane> {
        val arSession = session ?: return emptyList()

        return arSession
            .getAllTrackables(Plane::class.java)
            .filter { it.trackingState == com.google.ar.core.TrackingState.TRACKING }
            .map { plane ->
                val pose = plane.centerPose
                val polygon: FloatBuffer = plane.polygon
                val polygonArray = FloatArray(polygon.remaining())
                polygon.get(polygonArray)
                polygon.rewind()

                val vertices =
                    polygonArray
                        .toList()
                        .chunked(2)
                        .map { (x, z) -> Vec3(x, 0f, z) }

                DetectedPlane(
                    id = "plane-${plane.hashCode()}",
                    type =
                        when (plane.type) {
                            Plane.Type.HORIZONTAL_DOWNWARD_FACING -> PlaneType.HORIZONTAL_DOWN
                            Plane.Type.HORIZONTAL_UPWARD_FACING -> PlaneType.HORIZONTAL_UP
                            Plane.Type.VERTICAL -> PlaneType.VERTICAL
                        },
                    centerPose =
                        EnginePose(
                            position = Vec3(pose.tx(), pose.ty(), pose.tz()),
                            rotation = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()),
                        ),
                    extentX = plane.extentX,
                    extentZ = plane.extentZ,
                    vertices = vertices,
                )
            }
    }

    private fun getLightEstimate(frame: Frame): LightEstimate? {
        val estimate: ArLightEstimate = frame.lightEstimate
        if (estimate.state != ArLightEstimate.State.VALID) {
            return null
        }

        // Get color correction (ARCore requires passing in an array)
        val colorCorrection = FloatArray(4)
        estimate.getColorCorrection(colorCorrection, 0)

        return LightEstimate(
            ambientIntensity = estimate.pixelIntensity,
            ambientColor =
                Vec3(
                    colorCorrection[0],
                    colorCorrection[1],
                    colorCorrection[2],
                ),
            directionalIntensity = 0f, // Not available in basic mode
            directionalDirection = Vec3(0f, -1f, 0f), // Default down
        )
    }

    private fun getAnchorStates(frame: Frame): List<AnchorState> =
        anchorMap.map { (id, anchor) ->
            val pose = anchor.pose
            AnchorState(
                id = id,
                status =
                    when (anchor.trackingState) {
                        TrackingState.TRACKING -> AnchorStatus.TRACKING
                        TrackingState.PAUSED -> AnchorStatus.PAUSED
                        TrackingState.STOPPED -> AnchorStatus.LOST
                    },
                pose =
                    EnginePose(
                        position =
                            Vec3(
                                pose.tx(),
                                pose.ty(),
                                pose.tz(),
                            ),
                        rotation =
                            Quaternion(
                                pose.qx(),
                                pose.qy(),
                                pose.qz(),
                                pose.qw(),
                            ),
                    ),
            )
        }

    private fun createAnchor(request: AnchorRequest) {
        // Skip if anchor already exists
        if (anchorMap.containsKey(request.id)) return

        val arSession = session ?: return
        val frame = arSession.update()

        val anchor =
            when (val type = request.type) {
                is AnchorType.Plane -> {
                    // Find the plane
                    val plane =
                        arSession
                            .getAllTrackables(Plane::class.java)
                            .firstOrNull { "plane-${it.hashCode()}" == type.planeId } ?: return

                    // Create anchor at plane center with offset
                    val pose = plane.centerPose
                    val offsetPose =
                        pose.compose(
                            com.google.ar.core.Pose.makeTranslation(
                                type.offsetFromCenter.x,
                                type.offsetFromCenter.y,
                                type.offsetFromCenter.z,
                            ),
                        )
                    plane.createAnchor(offsetPose)
                }

                is AnchorType.HitTest -> {
                    // Create anchor at hit position
                    val pose =
                        com.google.ar.core.Pose.makeTranslation(
                            type.hitPoint.x,
                            type.hitPoint.y,
                            type.hitPoint.z,
                        )
                    arSession.createAnchor(pose)
                }

                is AnchorType.World -> {
                    // Create anchor at world position
                    val pose =
                        com.google.ar.core.Pose(
                            floatArrayOf(
                                type.position.x,
                                type.position.y,
                                type.position.z,
                            ),
                            floatArrayOf(
                                type.rotation.x,
                                type.rotation.y,
                                type.rotation.z,
                                type.rotation.w,
                            ),
                        )
                    arSession.createAnchor(pose)
                }

                is AnchorType.Persistent -> {
                    // Cloud anchors not implemented in this version
                    null
                }
            }

        anchor?.let {
            anchorMap[request.id] = it
        }
    }

    private fun checkCapabilities(session: Session): EngineCapabilities {
        // Check augmented images support
        val supportsAugmentedImages =
            try {
                val imageConfig =
                    Config(session).apply {
                        augmentedImageDatabase = AugmentedImageDatabase(session)
                    }
                session.isSupported(imageConfig)
            } catch (e: Exception) {
                false
            }

        // Check face tracking support (requires front-facing camera config)
        val supportsFaceTracking =
            try {
                val faceConfig =
                    Config(session).apply {
                        augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                    }
                session.isSupported(faceConfig)
            } catch (e: Exception) {
                false
            }

        // Check depth support
        val supportsDepth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)

        return EngineCapabilities(
            supportsAR = true,
            supportsVR = false,
            supportsPlaneDetection = true,
            supportsImageTracking = supportsAugmentedImages,
            supportsFaceTracking = supportsFaceTracking,
            supportsHandTracking = false,
            supportsDepthOcclusion = supportsDepth,
            supportsSpatialAnchors = true,
            maxAnchors = 0,
        )
    }

    // Extension functions for ARCore -> Engine type conversion
    private fun TrackingState.toEngineTrackingState(): com.spatialkit.engine.TrackingState =
        when (this) {
            TrackingState.TRACKING -> com.spatialkit.engine.TrackingState.NORMAL
            TrackingState.PAUSED -> com.spatialkit.engine.TrackingState.LIMITED
            TrackingState.STOPPED -> com.spatialkit.engine.TrackingState.NOT_AVAILABLE
        }

    private fun EngineError.toException(): Exception =
        when (this) {
            is EngineError.NotSupported -> IllegalStateException("ARCore not supported")
            is EngineError.PermissionDenied -> SecurityException("Permission denied")
            is EngineError.InitializationFailed -> IllegalStateException(this.reason)
            is EngineError.SessionFailed -> IllegalStateException(this.reason)
            else -> Exception("ARCore error: $this")
        }
}
