package com.spatialkit.tracking.arcore

import android.content.Context
import android.graphics.BitmapFactory
import android.view.Surface
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.spatialkit.core.log.SpatialLog
import com.spatialkit.tracking.AnchorState
import com.spatialkit.tracking.AnchorStatus
import com.spatialkit.tracking.CameraImage
import com.spatialkit.tracking.DepthImage
import com.spatialkit.tracking.DetectedPlane
import com.spatialkit.tracking.HitTestResult
import com.spatialkit.tracking.LightEstimate
import com.spatialkit.tracking.PlaneType
import com.spatialkit.tracking.Pose
import com.spatialkit.tracking.Quaternion
import com.spatialkit.tracking.ReferenceImage
import com.spatialkit.tracking.TrackedImage
import com.spatialkit.tracking.TrackedImageStatus
import com.spatialkit.tracking.TrackingCapabilities
import com.spatialkit.tracking.TrackingConfig
import com.spatialkit.tracking.TrackingFrame
import com.spatialkit.tracking.TrackingProvider
import com.spatialkit.tracking.TrackingSessionState
import com.spatialkit.tracking.TrackingState
import com.spatialkit.tracking.Vec3
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.EnumSet
import com.google.ar.core.LightEstimate as ArLightEstimate
import com.google.ar.core.Pose as ArPose
import com.google.ar.core.TrackingState as ArTrackingState

/**
 * ARCore [TrackingProvider]: the v2 tracking-only adapter (ported from v1's
 * ARCoreEngine minus all rendering — `docs/V2_REALISM_REWRITE.md` §4).
 *
 * ## Wiring with the renderer (Android specifics)
 *
 * ARCore's camera image arrives through GL external-OES textures: the
 * runtime must create texture id(s) on an EGL context *shared with the
 * renderer* and pass them via [setCameraTextureIds] before frames are
 * produced, and that EGL context must be current on the thread calling
 * [update] (ARCore binds the camera image during `Session.update()`).
 * [TrackingFrame.cameraImage] then carries the texture id plus the display
 * UV transform.
 *
 * The surface host reports size/rotation through [setDisplayGeometry].
 */
public class ARCoreTrackingProvider(
    private val context: Context,
) : TrackingProvider {
    private var session: Session? = null
    private var arConfig: Config? = null
    private var trackingConfig: TrackingConfig? = null

    private val _state = MutableStateFlow<TrackingSessionState>(TrackingSessionState.Uninitialized)
    override val state: StateFlow<TrackingSessionState> = _state.asStateFlow()

    private var _capabilities: TrackingCapabilities? = null
    override val capabilities: TrackingCapabilities
        get() =
            _capabilities ?: TrackingCapabilities(
                supportsPlaneDetection = false,
                supportsImageTracking = false,
                supportsDepth = false,
                supportsHdrLightEstimation = false,
                supportsAnchors = false,
                supportsAnchorPersistence = false,
            )

    private var cameraTextureIds: IntArray? = null
    private var textureNamesApplied = false

    private val anchorMap = LinkedHashMap<String, Anchor>()
    private var nextAnchorId = 1L

    @Volatile
    private var lastFrame: Frame? = null

    @Volatile
    private var surfaceWidth = 0

    @Volatile
    private var surfaceHeight = 0

    @Volatile
    private var displayRotation = Surface.ROTATION_0

    @Volatile
    private var displayGeometryDirty = true

    private var lastFrameTimeNanos = 0L
    private var lastTimestamp = 0L

    // Scratch buffers for the per-frame UV transform (NDC quad corners →
    // camera texture UVs); recomputed only when display geometry changes.
    // Corner order matches the renderer's fullscreen quad (BL, BR, TL, TR);
    // x is mirrored because the Filament device-domain quad rasterizes with
    // screen-x opposite to ARCore's GL NDC convention (validated on device —
    // without the mirror the camera pass-through is left/right flipped).
    private val ndcCorners =
        floatArrayOf(1f, -1f, -1f, -1f, 1f, 1f, -1f, 1f)
    private var uvTransform: FloatArray? = null

    override suspend fun initialize(config: TrackingConfig) {
        withContext(Dispatchers.Main) {
            check(
                _state.value is TrackingSessionState.Uninitialized ||
                    _state.value is TrackingSessionState.Disposed,
            ) { "Provider already initialized (state=${_state.value})" }

            _state.value = TrackingSessionState.Initializing
            trackingConfig = config

            try {
                val availability = ArCoreApk.getInstance().checkAvailability(context)
                if (availability != ArCoreApk.Availability.SUPPORTED_INSTALLED) {
                    error("ARCore not available on this device: $availability")
                }

                val arSession = Session(context, EnumSet.noneOf(Session.Feature::class.java))
                session = arSession

                val supportsHdr = arSession.supportsLightMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR)
                val supportsDepth = arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)

                val sessionConfig =
                    Config(arSession).apply {
                        lightEstimationMode =
                            when {
                                !config.lightEstimation -> Config.LightEstimationMode.DISABLED
                                supportsHdr -> Config.LightEstimationMode.ENVIRONMENTAL_HDR
                                else -> Config.LightEstimationMode.AMBIENT_INTENSITY
                            }
                        planeFindingMode =
                            if (config.planeDetection) {
                                Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                            } else {
                                Config.PlaneFindingMode.DISABLED
                            }
                        // Depth is opt-in: ARCore computes depth every frame once
                        // enabled, which costs battery/GPU even if never sampled.
                        if (config.depth && supportsDepth) {
                            depthMode = Config.DepthMode.AUTOMATIC
                        }
                        buildImageDatabase(arSession, config.referenceImages)?.let {
                            augmentedImageDatabase = it
                        }
                        focusMode = Config.FocusMode.AUTO
                    }
                arSession.configure(sessionConfig)
                arConfig = sessionConfig

                _capabilities =
                    TrackingCapabilities(
                        supportsPlaneDetection = true,
                        supportsImageTracking = supportsAugmentedImages(arSession),
                        supportsDepth = supportsDepth,
                        supportsHdrLightEstimation = supportsHdr,
                        supportsAnchors = true,
                        // ARCore Cloud Anchors need a Google Cloud project;
                        // deferred (honest false) until the geospatial layer.
                        supportsAnchorPersistence = false,
                        supportsGeospatial = false,
                        maxAnchors = null,
                    )

                _state.value = TrackingSessionState.Ready
            } catch (e: Exception) {
                _state.value = TrackingSessionState.Failed("ARCore initialization failed: ${e.message}", e)
                throw e
            }
        }
    }

    override suspend fun start() {
        withContext(Dispatchers.Main) {
            check(_state.value is TrackingSessionState.Ready) {
                "Provider not ready (state=${_state.value})"
            }
            val arSession = checkNotNull(session)
            try {
                arSession.resume()
                _state.value = TrackingSessionState.Running
            } catch (e: CameraNotAvailableException) {
                _state.value = TrackingSessionState.Failed("Camera not available: ${e.message}", e)
                throw e
            }
        }
    }

    override fun stop() {
        val arSession = session ?: return
        lastFrame = null
        try {
            arSession.pause()
            _state.value = TrackingSessionState.Ready
        } catch (e: Exception) {
            SpatialLog.warn(TAG, e) { "error pausing ARCore session" }
        }
    }

    override fun dispose() {
        stop()
        lastFrame = null
        anchorMap.values.forEach { it.detach() }
        anchorMap.clear()
        session?.close()
        session = null
        arConfig = null
        _state.value = TrackingSessionState.Disposed
    }

    /**
     * Supplies the GL external-OES texture id(s) the camera image is bound
     * to. Must be created on an EGL context shared with the renderer, and
     * set before frames are produced. Runtime wiring, not app API.
     */
    public fun setCameraTextureIds(ids: IntArray) {
        require(ids.isNotEmpty()) { "At least one camera texture id is required" }
        cameraTextureIds = ids.copyOf()
        textureNamesApplied = false
    }

    /**
     * Reports the rendering surface's size and display rotation
     * ([Surface.ROTATION_0]..[Surface.ROTATION_270]); ARCore uses it to
     * rotate the camera pass-through and hit-test coordinates.
     */
    public fun setDisplayGeometry(
        rotation: Int,
        widthPx: Int,
        heightPx: Int,
    ) {
        displayRotation = rotation
        surfaceWidth = widthPx
        surfaceHeight = heightPx
        displayGeometryDirty = true
    }

    override fun update(frameTimeNanos: Long): TrackingFrame? {
        val arSession = session ?: return null
        if (_state.value !is TrackingSessionState.Running) return null
        val textureIds = cameraTextureIds ?: return null

        if (!textureNamesApplied) {
            arSession.setCameraTextureNames(textureIds)
            textureNamesApplied = true
        }
        if (displayGeometryDirty && surfaceWidth > 0 && surfaceHeight > 0) {
            arSession.setDisplayGeometry(displayRotation, surfaceWidth, surfaceHeight)
            displayGeometryDirty = false
        }

        val frame =
            try {
                arSession.update()
            } catch (e: CameraNotAvailableException) {
                _state.value = TrackingSessionState.Failed("Camera lost: ${e.message}", e)
                return null
            }
        // No camera image yet (session warming up) — the external texture is
        // undefined, so there is nothing to composite.
        if (frame.timestamp == 0L) return null
        lastFrame = frame

        if (frame.hasDisplayGeometryChanged() || uvTransform == null) {
            val uvs = FloatArray(8)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                ndcCorners,
                Coordinates2d.TEXTURE_NORMALIZED,
                uvs,
            )
            uvTransform = uvs
        }

        val deltaTime =
            if (lastFrameTimeNanos > 0) (frameTimeNanos - lastFrameTimeNanos) * 1e-9f else 0f
        lastFrameTimeNanos = frameTimeNanos

        val camera = frame.camera
        val isTracking = camera.trackingState == ArTrackingState.TRACKING
        // displayOrientedPose, not pose: consistent with the display-rotated
        // view/projection matrices and with what ARKit reports, so
        // screen-space math works (the v1 lesson).
        val pose = camera.displayOrientedPose

        val projection = FloatArray(16)
        camera.getProjectionMatrix(
            projection,
            0,
            trackingConfig?.nearPlane ?: 0.1f,
            trackingConfig?.farPlane ?: 100f,
        )
        val view = FloatArray(16)
        camera.getViewMatrix(view, 0)

        return TrackingFrame(
            timestamp = frame.timestamp,
            deltaTime = deltaTime,
            cameraPose =
                Pose(
                    position = Vec3(pose.tx(), pose.ty(), pose.tz()),
                    rotation = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()),
                ),
            viewMatrix = view,
            projectionMatrix = projection,
            trackingState = camera.trackingState.toTrackingState(),
            planes = if (isTracking) detectedPlanes(arSession) else emptyList(),
            lightEstimate = if (isTracking) lightEstimate(frame) else null,
            // The texture ARCore actually wrote this frame — with multiple
            // registered names ARCore rotates through them (double-buffering),
            // and the renderer must bind the per-frame one.
            cameraImage = CameraImage(frame.cameraTextureName, uvTransform ?: FALLBACK_UVS),
            depthImage = if (isTracking) acquireDepth(frame) else null,
            anchors = if (isTracking) anchorStates() else emptyList(),
            trackedImages = if (isTracking) trackedImages(arSession) else emptyList(),
        )
    }

    override fun hitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        // Contract: normalized coordinates (0,0 = top-left); ARCore expects
        // pixels, so scale by the surface size.
        if (surfaceWidth == 0 || surfaceHeight == 0) return null
        val frame = lastFrame ?: return null

        // The cached frame is superseded once update() runs again; ARCore may
        // reject hit tests against it, so guard the call.
        val hits: List<HitResult> =
            try {
                frame.hitTest(screenX * surfaceWidth, screenY * surfaceHeight)
            } catch (e: Exception) {
                SpatialLog.debug(TAG) { "hitTest on superseded frame rejected: ${e.message}" }
                return null
            }
        val hit = hits.firstOrNull() ?: return null

        val pose = hit.hitPose
        val plane = hit.trackable as? Plane
        val normal =
            plane?.centerPose?.let { centerPose ->
                val yAxis = centerPose.yAxis
                Vec3(yAxis[0], yAxis[1], yAxis[2])
            } ?: Vec3(0f, 1f, 0f)

        return HitTestResult(
            position = Vec3(pose.tx(), pose.ty(), pose.tz()),
            normal = normal,
            distance = hit.distance,
            planeId = plane?.let { "plane-${it.hashCode()}" },
        )
    }

    override fun createAnchor(pose: Pose): String? = createAnchor(pose, planeId = null)

    /**
     * Creates an anchor at [pose], attached to the detected plane [planeId]
     * when given (plane-attached anchors track more stably than free-space
     * ones). Returns the anchor id, or null when ARCore refused.
     */
    public fun createAnchor(
        pose: Pose,
        planeId: String?,
    ): String? {
        val arSession = session ?: return null
        val arPose =
            ArPose(
                floatArrayOf(pose.position.x, pose.position.y, pose.position.z),
                floatArrayOf(pose.rotation.x, pose.rotation.y, pose.rotation.z, pose.rotation.w),
            )
        val anchor =
            try {
                val plane =
                    planeId?.let { id ->
                        arSession
                            .getAllTrackables(Plane::class.java)
                            .firstOrNull { "plane-${it.hashCode()}" == id }
                    }
                plane?.createAnchor(arPose) ?: arSession.createAnchor(arPose)
            } catch (e: Exception) {
                SpatialLog.warn(TAG, e) { "createAnchor failed" }
                return null
            }
        val id = "anchor-${nextAnchorId++}"
        anchorMap[id] = anchor
        return id
    }

    override fun removeAnchor(anchorId: String) {
        anchorMap.remove(anchorId)?.detach()
    }

    // === Frame mapping internals ===

    private fun detectedPlanes(arSession: Session): List<DetectedPlane> =
        arSession
            .getAllTrackables(Plane::class.java)
            .filter { it.trackingState == ArTrackingState.TRACKING }
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
                        Pose(
                            position = Vec3(pose.tx(), pose.ty(), pose.tz()),
                            rotation = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()),
                        ),
                    extentX = plane.extentX,
                    extentZ = plane.extentZ,
                    vertices = vertices,
                )
            }

    private fun lightEstimate(frame: Frame): LightEstimate? {
        val estimate: ArLightEstimate = frame.lightEstimate
        if (estimate.state != ArLightEstimate.State.VALID) return null

        return when (arConfig?.lightEstimationMode) {
            Config.LightEstimationMode.ENVIRONMENTAL_HDR -> {
                val sh = estimate.environmentalHdrAmbientSphericalHarmonics
                val towardLight = estimate.environmentalHdrMainLightDirection
                val intensityRgb = estimate.environmentalHdrMainLightIntensity
                val maxComponent =
                    maxOf(intensityRgb[0], maxOf(intensityRgb[1], intensityRgb[2])).coerceAtLeast(1e-6f)

                LightEstimate(
                    // Relative luminance of the SH DC term, normalized with the
                    // same middle-grey reference as v1. Provisional scale —
                    // calibrated against real scenes in R4.
                    ambientIntensity =
                        (0.2126f * sh[0] + 0.7152f * sh[1] + 0.0722f * sh[2]) /
                            MIDDLE_GREY_PIXEL_INTENSITY,
                    ambientColor = Vec3(1f, 1f, 1f),
                    // Relative radiance → lux, scaled so 1.0 ≈ direct sun
                    // (SceneView's mapping). Calibrated in R4.
                    mainLightIntensityLux = maxComponent * MAIN_LIGHT_LUX_AT_UNIT_INTENSITY,
                    mainLightColor =
                        Vec3(
                            intensityRgb[0] / maxComponent,
                            intensityRgb[1] / maxComponent,
                            intensityRgb[2] / maxComponent,
                        ),
                    // ARCore reports the direction TOWARD the light; the
                    // contract (and Filament) wants the travel direction.
                    mainLightDirection = Vec3(-towardLight[0], -towardLight[1], -towardLight[2]),
                    sphericalHarmonics = sh,
                    environmentCubemap = null, // acquired on demand from R4
                )
            }

            Config.LightEstimationMode.AMBIENT_INTENSITY -> {
                val colorCorrection = FloatArray(4)
                estimate.getColorCorrection(colorCorrection, 0)
                LightEstimate(
                    ambientIntensity = estimate.pixelIntensity / MIDDLE_GREY_PIXEL_INTENSITY,
                    ambientColor = Vec3(colorCorrection[0], colorCorrection[1], colorCorrection[2]),
                    mainLightIntensityLux = 0f,
                    mainLightColor = Vec3(1f, 1f, 1f),
                    mainLightDirection = Vec3(0f, -1f, 0f),
                )
            }

            else -> null
        }
    }

    private fun acquireDepth(frame: Frame): DepthImage? {
        if (arConfig?.depthMode != Config.DepthMode.AUTOMATIC) return null
        val image =
            try {
                frame.acquireDepthImage16Bits()
            } catch (e: Exception) {
                return null // NotYetAvailableException while depth warms up
            }
        return image.use { depth ->
            val plane = depth.planes[0]
            val width = depth.width
            val height = depth.height
            val src = plane.buffer
            val rowStride = plane.rowStride
            val out = ByteBuffer.allocateDirect(width * height * 2).order(ByteOrder.nativeOrder())
            if (rowStride == width * 2) {
                out.put(src)
            } else {
                val row = ByteArray(width * 2)
                for (y in 0 until height) {
                    src.position(y * rowStride)
                    src.get(row, 0, row.size)
                    out.put(row)
                }
            }
            out.rewind()
            DepthImage(out, width, height)
        }
    }

    private fun anchorStates(): List<AnchorState> =
        anchorMap.map { (id, anchor) ->
            val pose = anchor.pose
            AnchorState(
                id = id,
                status =
                    when (anchor.trackingState) {
                        ArTrackingState.TRACKING -> AnchorStatus.TRACKING
                        ArTrackingState.PAUSED -> AnchorStatus.PAUSED
                        ArTrackingState.STOPPED -> AnchorStatus.LOST
                    },
                pose =
                    Pose(
                        position = Vec3(pose.tx(), pose.ty(), pose.tz()),
                        rotation = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()),
                    ),
            )
        }

    private fun trackedImages(arSession: Session): List<TrackedImage> {
        if (trackingConfig?.referenceImages?.isEmpty() != false) return emptyList()

        return arSession
            .getAllTrackables(AugmentedImage::class.java)
            .map { image ->
                val status =
                    when {
                        image.trackingState != ArTrackingState.TRACKING -> TrackedImageStatus.NOT_TRACKING
                        image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING ->
                            TrackedImageStatus.TRACKING
                        image.trackingMethod == AugmentedImage.TrackingMethod.LAST_KNOWN_POSE ->
                            TrackedImageStatus.LAST_KNOWN_POSE
                        else -> TrackedImageStatus.NOT_TRACKING
                    }
                val pose = image.centerPose
                TrackedImage(
                    imageId = image.name,
                    status = status,
                    pose =
                        if (status == TrackedImageStatus.NOT_TRACKING) {
                            null
                        } else {
                            Pose(
                                position = Vec3(pose.tx(), pose.ty(), pose.tz()),
                                rotation = Quaternion(pose.qx(), pose.qy(), pose.qz(), pose.qw()),
                            )
                        },
                    extentX = image.extentX,
                    extentZ = image.extentZ,
                )
            }
    }

    private fun buildImageDatabase(
        arSession: Session,
        referenceImages: List<ReferenceImage>,
    ): AugmentedImageDatabase? {
        if (referenceImages.isEmpty()) return null

        val database = AugmentedImageDatabase(arSession)
        var added = 0
        referenceImages.forEach { reference ->
            val bitmap =
                BitmapFactory.decodeByteArray(reference.imageBytes, 0, reference.imageBytes.size)
            if (bitmap == null) {
                SpatialLog.warn(TAG) { "reference image '${reference.id}' could not be decoded, skipping" }
                return@forEach
            }
            try {
                database.addImage(reference.id, bitmap, reference.physicalWidthMeters)
                added++
            } catch (e: Exception) {
                // ARCore rejects images with too few features
                SpatialLog.warn(TAG, e) { "reference image '${reference.id}' rejected by ARCore, skipping" }
            }
        }
        return if (added > 0) database else null
    }

    private fun supportsAugmentedImages(arSession: Session): Boolean =
        try {
            val imageConfig =
                Config(arSession).apply {
                    augmentedImageDatabase = AugmentedImageDatabase(arSession)
                }
            arSession.isSupported(imageConfig)
        } catch (e: Exception) {
            false
        }

    private fun Session.supportsLightMode(mode: Config.LightEstimationMode): Boolean =
        try {
            isSupported(Config(this).apply { lightEstimationMode = mode })
        } catch (e: Exception) {
            false
        }

    private fun ArTrackingState.toTrackingState(): TrackingState =
        when (this) {
            ArTrackingState.TRACKING -> TrackingState.NORMAL
            ArTrackingState.PAUSED -> TrackingState.LIMITED
            ArTrackingState.STOPPED -> TrackingState.NOT_AVAILABLE
        }

    private companion object {
        const val TAG = "ARCoreTracking"

        // ARCore's documented middle-grey reference for pixelIntensity;
        // dividing by it normalizes so 1.0 = nominal lighting (v1 contract).
        const val MIDDLE_GREY_PIXEL_INTENSITY = 0.466f

        // ENVIRONMENTAL_HDR main-light intensity is relative radiance where
        // ~1.0 corresponds to direct sunlight; scale into lux for the
        // renderer (provisional — calibrated in R4).
        const val MAIN_LIGHT_LUX_AT_UNIT_INTENSITY = 100_000f

        val FALLBACK_UVS = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
    }
}
