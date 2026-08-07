package com.spatialkit.tracking

/**
 * The complete tracked state of the real world for one frame, produced by
 * [TrackingProvider.update] and consumed by the renderer and the scene layer.
 *
 * Matrices are column-major, in the shared conventions (right-handed, +Y up,
 * -Z forward, meters). Frames are transient per-tick values: hold no
 * references across frames — platform image handles ([cameraImage],
 * [depthImage]) are only valid during the tick they were produced in.
 */
public class TrackingFrame(
    /** Frame timestamp in nanoseconds (platform frame clock). */
    public val timestamp: Long,
    /** Seconds since the previous frame. */
    public val deltaTime: Float,
    public val cameraPose: Pose,
    /** 4x4 column-major view matrix (world → camera). */
    public val viewMatrix: FloatArray,
    /** 4x4 column-major projection matrix (may be off-center in AR). */
    public val projectionMatrix: FloatArray,
    public val trackingState: TrackingState,
    public val planes: List<DetectedPlane> = emptyList(),
    public val lightEstimate: LightEstimate? = null,
    /**
     * The camera image for background compositing, as an opaque platform
     * handle (GL external texture on Android, retained CVPixelBuffer on
     * iOS). Null before the first camera frame.
     */
    public val cameraImage: CameraImage? = null,
    /** Per-pixel scene depth when enabled and supported; null otherwise. */
    public val depthImage: DepthImage? = null,
    public val anchors: List<AnchorState> = emptyList(),
    public val trackedImages: List<TrackedImage> = emptyList(),
)

/** Pose (position + rotation) in world space. */
public data class Pose(
    val position: Vec3,
    val rotation: Quaternion,
)

/** Quality of positional tracking. */
public enum class TrackingState {
    /** Tracking is not available (no camera, session not running). */
    NOT_AVAILABLE,

    /** Tracking is available but limited (low confidence). */
    LIMITED,

    /** Tracking is working normally with good confidence. */
    NORMAL,
}

/**
 * A surface detected in the environment.
 *
 * @property id Stable identifier across frames
 * @property centerPose Pose at the plane center (+y is the plane normal)
 * @property extentX Width along local X, meters
 * @property extentZ Depth along local Z, meters
 * @property vertices Boundary polygon in plane-local space (y = 0), or null
 */
public data class DetectedPlane(
    val id: String,
    val type: PlaneType,
    val centerPose: Pose,
    val extentX: Float,
    val extentZ: Float,
    val vertices: List<Vec3>?,
)

/** Classification of detected planes. */
public enum class PlaneType {
    /** Horizontal plane facing up (floor, table). */
    HORIZONTAL_UP,

    /** Horizontal plane facing down (ceiling). */
    HORIZONTAL_DOWN,

    /** Vertical plane (wall). */
    VERTICAL,

    /** Unknown orientation. */
    UNKNOWN,
}

/**
 * Estimated real-world lighting, in renderer-ready physical units.
 *
 * v2 upgrades v1's single ambient scalar to the full HDR estimate the
 * renderer needs for image-based lighting: main directional light in lux,
 * ambient spherical harmonics, and (when the platform provides one) an HDR
 * environment cubemap. Providers fill what their platform supports and
 * report the rest as null/defaults — see
 * [TrackingCapabilities.supportsHdrLightEstimation].
 */
public class LightEstimate(
    /**
     * Normalized ambient intensity, 1.0 ≈ nominally lit indoor scene (the
     * v1 convention — kept for app-level reactions like dimming UI).
     */
    public val ambientIntensity: Float,
    /** RGB balance of ambient light (1,1,1 = neutral). */
    public val ambientColor: Vec3,
    /** Illuminance of the main directional light in lux (0 = no estimate). */
    public val mainLightIntensityLux: Float,
    /** Relative RGB color of the main light (1,1,1 = white). */
    public val mainLightColor: Vec3,
    /** Direction the main light travels (world space, away from source). */
    public val mainLightDirection: Vec3,
    /**
     * Ambient spherical harmonics: 27 floats, 9 RGB coefficients in ARCore's
     * output order (compatible with Filament's `IndirectLight.irradiance(3,
     * sh)`). Null when the platform only provides scalar ambient.
     */
    public val sphericalHarmonics: FloatArray? = null,
    /** HDR environment cubemap handle for reflections, when available. */
    public val environmentCubemap: EnvironmentCubemap? = null,
)
