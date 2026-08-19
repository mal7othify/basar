package com.spatialkit.engine

/**
 * Anchor request/type moved to `:spatial-render` in R3 (they travel with
 * [SceneData]); typealiased here for v1 source compatibility. New code
 * should import `com.spatialkit.render.*` directly. [AnchorState] and
 * [AnchorStatus] stay engine-local (reported via FrameData).
 */
public typealias AnchorRequest = com.spatialkit.render.AnchorRequest

public typealias AnchorType = com.spatialkit.render.AnchorType

/**
 * Current state of a spatial anchor.
 *
 * The engine reports anchor states each frame so applications can
 * respond to tracking changes.
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

/**
 * Anchor tracking status.
 */
public enum class AnchorStatus {
    /** Anchor is being created but not yet tracking */
    PENDING,

    /** Anchor is actively tracked with good confidence */
    TRACKING,

    /** Tracking is temporarily paused (e.g., camera covered) */
    PAUSED,

    /** Tracking was lost (anchor may never recover) */
    LOST,

    /** Anchor creation or tracking failed */
    ERROR,
}
