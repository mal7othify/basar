package com.spatialkit.tracking

/**
 * Result of a real-world hit test (against detected planes and depth).
 *
 * @property position World-space position where the ray hit
 * @property normal Surface normal at the hit point (null when unknown)
 * @property distance Distance from the ray origin to the hit point
 * @property planeId ID of the plane that was hit (null for depth-based hits)
 */
public data class HitTestResult(
    val position: Vec3,
    val normal: Vec3?,
    val distance: Float,
    val planeId: String?,
)
