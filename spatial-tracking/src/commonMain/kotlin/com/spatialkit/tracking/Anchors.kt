package com.spatialkit.tracking

/**
 * Current state of a spatial anchor, reported each frame via
 * [TrackingFrame.anchors].
 *
 * @property id The anchor's unique identifier
 * @property status Current tracking status
 * @property pose Current pose (null if not yet tracking)
 */
public data class AnchorState(
    val id: String,
    val status: AnchorStatus,
    val pose: Pose?,
)

/** Anchor tracking status. */
public enum class AnchorStatus {
    /** Anchor is being created/resolved but not yet tracking. */
    PENDING,

    /** Anchor is actively tracked with good confidence. */
    TRACKING,

    /** Tracking is temporarily paused (e.g. camera covered). */
    PAUSED,

    /** Tracking was lost (the anchor may never recover). */
    LOST,

    /** Anchor creation or tracking failed. */
    ERROR,
}
