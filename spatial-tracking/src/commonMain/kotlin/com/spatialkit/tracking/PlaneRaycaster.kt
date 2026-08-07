package com.spatialkit.tracking

import kotlin.math.abs

/**
 * CPU raycaster against [DetectedPlane]s (ported unchanged from v1).
 *
 * Provides real-world hit testing for providers whose platform API cannot
 * hit-test an arbitrary ray: the ray is localized into each plane's space,
 * intersected with its y = 0 surface, and accepted when the intersection
 * lies inside the plane's boundary polygon — or inside its extent rectangle
 * when no polygon is available.
 *
 * All methods are pure functions of their input: safe to call from any thread
 * as long as the caller snapshots the plane list (DetectedPlane is immutable).
 */
public object PlaneRaycaster {
    /**
     * Casts a ray against [planes] and returns the closest hit, or null.
     *
     * @param origin Ray origin in world space
     * @param direction Ray direction in world space (need not be normalized;
     *   hit distances are measured in world units regardless)
     * @return Closest hit with the plane's id, or null if nothing is hit
     */
    public fun raycast(
        origin: Vec3,
        direction: Vec3,
        planes: List<DetectedPlane>,
    ): HitTestResult? {
        var best: HitTestResult? = null
        planes.forEach { plane ->
            val hit = raycastPlane(origin, direction, plane) ?: return@forEach
            if (best == null || hit.distance < best.distance) best = hit
        }
        return best
    }

    /**
     * Intersects the ray with a single plane's bounded surface.
     *
     * @return Hit on the plane's surface (normal faces the ray origin), or
     *   null when the ray misses, is parallel, or hits behind the origin
     */
    public fun raycastPlane(
        origin: Vec3,
        direction: Vec3,
        plane: DetectedPlane,
    ): HitTestResult? {
        val rotation = plane.centerPose.rotation
        val invRotation = rotation.conjugate()
        val localOrigin = invRotation * (origin - plane.centerPose.position)
        val localDirection = invRotation * direction

        // Plane surface is y = 0 in local space
        if (abs(localDirection.y) < EPSILON) return null
        val t = -localOrigin.y / localDirection.y
        if (t <= 0f) return null

        val localHit = localOrigin + localDirection * t
        val vertices = plane.vertices
        val inside =
            if (vertices != null && vertices.size >= 3) {
                containsPointXZ(vertices, localHit)
            } else {
                abs(localHit.x) <= plane.extentX * 0.5f && abs(localHit.z) <= plane.extentZ * 0.5f
            }
        if (!inside) return null

        // Face the normal toward the ray origin so placement math works from
        // either side of the surface
        val planeNormal = rotation * Vec3.UP
        val normal = if (planeNormal.dot(direction) > 0f) -planeNormal else planeNormal

        return HitTestResult(
            position = origin + direction * t,
            normal = normal,
            distance = t * direction.length(),
            planeId = plane.id,
        )
    }

    /**
     * Even-odd-rule point-in-polygon test in the plane's local X/Z space
     * (boundary vertices have y = 0 by the [DetectedPlane] contract).
     */
    private fun containsPointXZ(
        polygon: List<Vec3>,
        point: Vec3,
    ): Boolean {
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.z > point.z) != (b.z > point.z)) {
                val crossX = a.x + (point.z - a.z) / (b.z - a.z) * (b.x - a.x)
                if (point.x < crossX) inside = !inside
            }
            j = i
        }
        return inside
    }

    private const val EPSILON = 1e-6f
}
