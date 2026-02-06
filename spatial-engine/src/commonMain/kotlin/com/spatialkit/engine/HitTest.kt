package com.spatialkit.engine

/**
 * Result of a screen-space hit test (AR).
 *
 * Hit tests cast a ray from screen coordinates into the AR world,
 * testing against detected planes and estimated depth.
 *
 * @property position World-space position where the ray hit
 * @property normal Surface normal at the hit point
 * @property distance Distance from camera to hit point
 * @property planeId ID of the plane that was hit (null if hit estimation-based surface)
 */
data class HitTestResult(
    val position: Vec3,
    val normal: Vec3?,
    val distance: Float,
    val planeId: String?,
)

/**
 * Result of a world-space raycast.
 *
 * Raycasts test a ray defined by origin and direction against
 * detected geometry and anchored content.
 *
 * @property position World-space position where the ray hit
 * @property normal Surface normal at the hit point
 * @property distance Distance from ray origin to hit point
 * @property nodeId ID of the scene node that was hit (null if environment geometry)
 */
data class RaycastResult(
    val position: Vec3,
    val normal: Vec3,
    val distance: Float,
    val nodeId: String?,
)
