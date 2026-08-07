package com.spatialkit.tracking

/**
 * A reference image the provider should detect and track in the real world.
 *
 * Supply reference images through [TrackingConfig.referenceImages]; the
 * provider reports detections each frame via [TrackingFrame.trackedImages].
 * Only honored where [TrackingCapabilities.supportsImageTracking] is true.
 *
 * Image bytes are decoded platform-side, so use widely supported encodings
 * (PNG or JPEG). Detection quality follows the platform's guidelines: prefer
 * high-contrast, feature-rich images and avoid repetitive patterns.
 *
 * @property id Unique identifier, reported back as [TrackedImage.imageId]
 * @property imageBytes Encoded image data (PNG or JPEG)
 * @property physicalWidthMeters Real-world width of the printed/displayed
 *   image in meters. Required — ARKit refuses reference images without a
 *   physical size, and ARCore detects significantly faster with one
 */
public data class ReferenceImage(
    val id: String,
    val imageBytes: ByteArray,
    val physicalWidthMeters: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ReferenceImage

        if (id != other.id) return false
        if (!imageBytes.contentEquals(other.imageBytes)) return false
        if (physicalWidthMeters != other.physicalWidthMeters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + physicalWidthMeters.hashCode()
        return result
    }
}

/**
 * Per-frame state of a reference image the provider is tracking.
 *
 * Images appear here once first detected and stay in the list afterwards —
 * check [status] before using the pose for placement.
 *
 * @property imageId The [ReferenceImage.id] this state belongs to
 * @property status Current tracking quality for this image
 * @property pose Center pose of the image in world space (x/z span the image
 *   surface, +y is its normal). Null only when the image is [TrackedImageStatus.NOT_TRACKING]
 *   with no last-known pose
 * @property extentX Measured width of the image in meters
 * @property extentZ Measured height of the image in meters
 */
public data class TrackedImage(
    val imageId: String,
    val status: TrackedImageStatus,
    val pose: Pose?,
    val extentX: Float,
    val extentZ: Float,
)

/** Tracking quality of a [TrackedImage]. */
public enum class TrackedImageStatus {
    /** The image is visible and its pose is actively tracked. */
    TRACKING,

    /**
     * The image is not currently visible; the pose is the last known one
     * (ARCore's LAST_KNOWN_POSE, ARKit's `isTracked == false`). Fine for
     * static images, stale for moving ones.
     */
    LAST_KNOWN_POSE,

    /** The image is known but not tracked this frame; the pose is unreliable. */
    NOT_TRACKING,
}
