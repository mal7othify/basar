package com.spatialkit.engine

/**
 * Data provided by the engine for each rendered frame.
 *
 * This is the complete state of the AR/VR world at a specific moment.
 * Applications receive this via the onFrame() callback to update their
 * scene based on tracking, detected environment, and input.
 *
 * @property timestamp Frame timestamp in milliseconds
 * @property deltaTime Time since last frame in seconds
 * @property cameraPose Current camera/head pose in world space
 * @property projectionMatrix 4x4 projection matrix (column-major)
 * @property viewMatrix 4x4 view matrix (column-major)
 * @property trackingState Current tracking quality
 * @property detectedPlanes All detected planes in the environment (AR)
 * @property lightEstimate Estimated environment lighting (AR)
 * @property inputSources All active input sources this frame
 * @property anchors Current state of all tracked anchors
 * @property trackedImages Reference images detected in the environment
 *   (M12.2; empty unless [EngineConfig.referenceImages] were supplied and
 *   the engine supports image tracking)
 */
public data class FrameData(
    val timestamp: Long,
    val deltaTime: Float,
    val cameraPose: Pose,
    val projectionMatrix: FloatArray,
    val viewMatrix: FloatArray,
    val trackingState: TrackingState,
    val detectedPlanes: List<DetectedPlane>,
    val lightEstimate: LightEstimate?,
    val inputSources: List<InputSource>,
    val anchors: List<AnchorState>,
    val trackedImages: List<TrackedImage> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FrameData

        if (timestamp != other.timestamp) return false
        if (deltaTime != other.deltaTime) return false
        if (cameraPose != other.cameraPose) return false
        if (!projectionMatrix.contentEquals(other.projectionMatrix)) return false
        if (!viewMatrix.contentEquals(other.viewMatrix)) return false
        if (trackingState != other.trackingState) return false
        if (detectedPlanes != other.detectedPlanes) return false
        if (lightEstimate != other.lightEstimate) return false
        if (inputSources != other.inputSources) return false
        if (anchors != other.anchors) return false
        if (trackedImages != other.trackedImages) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + deltaTime.hashCode()
        result = 31 * result + cameraPose.hashCode()
        result = 31 * result + projectionMatrix.contentHashCode()
        result = 31 * result + viewMatrix.contentHashCode()
        result = 31 * result + trackingState.hashCode()
        result = 31 * result + detectedPlanes.hashCode()
        result = 31 * result + (lightEstimate?.hashCode() ?: 0)
        result = 31 * result + inputSources.hashCode()
        result = 31 * result + anchors.hashCode()
        result = 31 * result + trackedImages.hashCode()
        return result
    }
}

/**
 * Pose (position + rotation) in 3D space.
 *
 * Used for camera, anchors, input devices, etc.
 *
 * @property position 3D position
 * @property rotation Rotation as quaternion
 */
public data class Pose(
    val position: Vec3,
    val rotation: Quaternion,
)

/**
 * Quality of positional tracking.
 */
public enum class TrackingState {
    /** Tracking is not available (no camera, etc.) */
    NOT_AVAILABLE,

    /** Tracking is available but limited (low confidence) */
    LIMITED,

    /** Tracking is working normally with good confidence */
    NORMAL,
}

/**
 * A detected plane in the environment (AR).
 *
 * Planes are surfaces detected by the AR system (floors, tables, walls).
 * They can be used to place content realistically in the world.
 *
 * @property id Unique identifier for this plane (stable across frames)
 * @property type Classification of the plane
 * @property centerPose Pose at the center of the plane
 * @property extentX Width of the plane (X axis)
 * @property extentZ Depth of the plane (Z axis)
 * @property vertices Boundary polygon vertices (null if not available)
 */
public data class DetectedPlane(
    val id: String,
    val type: PlaneType,
    val centerPose: Pose,
    val extentX: Float,
    val extentZ: Float,
    val vertices: List<Vec3>?,
)

/**
 * Classification of detected planes.
 */
public enum class PlaneType {
    /** Horizontal plane facing up (floor, table) */
    HORIZONTAL_UP,

    /** Horizontal plane facing down (ceiling) */
    HORIZONTAL_DOWN,

    /** Vertical plane (wall) */
    VERTICAL,

    /** Unknown orientation */
    UNKNOWN,
}

/**
 * Estimated lighting in the environment (AR).
 *
 * Used to match virtual object lighting to the real world. Engines feed this
 * estimate into their own renderer lighting automatically (M9.1) when
 * [EngineConfig.enableLightEstimation] is on; it is also surfaced here so
 * apps can react to it (e.g. dim UI content in a dark room).
 *
 * Intensity values are normalized so **1.0 ≈ a nominally lit indoor scene**
 * on every platform (ARCore's pixel intensity is divided by its 0.466
 * middle-grey reference, ARKit's lumens by 1000). Values above 1 mean a
 * brighter-than-nominal environment.
 *
 * @property ambientIntensity Overall ambient light intensity (1.0 = nominal)
 * @property ambientColor RGB color balance of ambient light (1,1,1 = neutral)
 * @property directionalIntensity Intensity of the primary directional light
 *   (0 when the platform provides no directional estimate)
 * @property directionalDirection Direction the primary light travels
 *   (world space, pointing away from the light source)
 */
public data class LightEstimate(
    val ambientIntensity: Float,
    val ambientColor: Vec3,
    val directionalIntensity: Float,
    val directionalDirection: Vec3,
)
