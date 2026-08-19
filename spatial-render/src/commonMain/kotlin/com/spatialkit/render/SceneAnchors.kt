package com.spatialkit.render

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3

/**
 * Request to create a spatial anchor.
 *
 * Anchors are the key abstraction for AR - they pin content to the real
 * world. Requests travel with [SceneData] as part of the scene snapshot;
 * they are consumed by the session / tracking layer (which owns anchor
 * lifecycles), not by renderers.
 *
 * @property id Unique identifier for this anchor
 * @property type The type of anchor to create
 */
public data class AnchorRequest(
    val id: String,
    val type: AnchorType,
)

/**
 * Types of spatial anchors that can be created.
 */
public sealed interface AnchorType {
    /**
     * Attach to a detected plane.
     *
     * Most stable for AR - anchors to a specific detected surface.
     *
     * @property planeId ID of the detected plane
     * @property offsetFromCenter Offset from the plane's center point
     */
    public data class Plane(
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
    public data class HitTest(
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
    public data class World(
        val position: Vec3,
        val rotation: Quaternion,
    ) : AnchorType

    /**
     * Restores an anchor persisted in an earlier session.
     *
     * Only honored where the tracking backend reports anchor-persistence
     * support; the anchor reports an error status elsewhere. Handles are
     * created by the persist-anchor API of the session/provider in a
     * previous session and stored app-side.
     *
     * @property handle Opaque persistence handle from a previous session
     */
    public data class Persistent(
        val handle: String,
    ) : AnchorType
}
