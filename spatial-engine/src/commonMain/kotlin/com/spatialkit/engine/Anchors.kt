package com.spatialkit.engine

/**
 * Request to create a spatial anchor.
 *
 * Anchors are the key abstraction for AR - they pin content to the real world.
 * The engine tracks anchor poses and updates them as tracking improves.
 *
 * @property id Unique identifier for this anchor
 * @property type The type of anchor to create
 */
data class AnchorRequest(
    val id: String,
    val type: AnchorType,
)

/**
 * Types of spatial anchors that can be created.
 */
sealed interface AnchorType {
    /**
     * Attach to a detected plane.
     *
     * Most stable for AR - anchors to a specific detected surface.
     *
     * @property planeId ID of the detected plane from FrameData
     * @property offsetFromCenter Offset from the plane's center point
     */
    data class Plane(
        val planeId: String,
        val offsetFromCenter: Vec3 = Vec3.ZERO,
    ) : AnchorType

    /**
     * Attach to a hit test result.
     *
     * Anchors to a point in space found by raycasting against detected geometry.
     *
     * @property hitPoint The 3D position where the ray hit
     * @property hitNormal The surface normal at the hit point
     */
    data class HitTest(
        val hitPoint: Vec3,
        val hitNormal: Vec3,
    ) : AnchorType

    /**
     * World position anchor (may drift over time).
     *
     * Less stable than plane or hit test anchors - use when no surfaces are detected.
     *
     * @property position World-space position
     * @property rotation World-space rotation
     */
    data class World(
        val position: Vec3,
        val rotation: Quaternion,
    ) : AnchorType

    /**
     * Persistent cloud anchor (shared across sessions/devices).
     *
     * Requires platform support and network connectivity.
     *
     * @property cloudId Cloud anchor identifier (platform-specific)
     */
    data class Persistent(
        val cloudId: String,
    ) : AnchorType
}

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
data class AnchorState(
    val id: String,
    val status: AnchorStatus,
    val pose: Pose?,
)

/**
 * Anchor tracking status.
 */
enum class AnchorStatus {
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
