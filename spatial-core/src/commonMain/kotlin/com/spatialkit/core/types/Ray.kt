package com.spatialkit.core.types

import com.spatialkit.core.math.Vec3
import kotlin.math.max
import kotlin.math.min

/**
 * Represents a ray in 3D space, defined by an origin point and a direction.
 *
 * Rays are commonly used for:
 * - Raycasting (detecting what the user is pointing at)
 * - Line-of-sight checks
 * - Physics simulations
 * - Rendering (ray tracing)
 *
 * @property origin The starting point of the ray
 * @property direction The direction vector of the ray (should be normalized)
 */
data class Ray(
    val origin: Vec3,
    val direction: Vec3,
) {
    init {
        require(direction.lengthSquared() > 0f) { "Ray direction cannot be zero" }
    }

    /**
     * The normalized direction of the ray.
     */
    val normalizedDirection: Vec3 by lazy { direction.normalized() }

    /**
     * Calculates a point along the ray at a given distance.
     *
     * @param distance The distance along the ray from the origin
     * @return The point at that distance
     */
    fun pointAt(distance: Float): Vec3 = origin + normalizedDirection * distance

    /**
     * Tests for intersection between this ray and a bounding box.
     *
     * Uses the slab method for efficient axis-aligned bounding box intersection.
     *
     * @param box The bounding box to test against
     * @return The distance to the intersection point, or null if no intersection
     */
    fun intersects(box: BoundingBox): Float? {
        val dir = normalizedDirection
        val invDir =
            Vec3(
                if (dir.x != 0f) 1f / dir.x else Float.POSITIVE_INFINITY,
                if (dir.y != 0f) 1f / dir.y else Float.POSITIVE_INFINITY,
                if (dir.z != 0f) 1f / dir.z else Float.POSITIVE_INFINITY,
            )

        val t1 = (box.min.x - origin.x) * invDir.x
        val t2 = (box.max.x - origin.x) * invDir.x
        val t3 = (box.min.y - origin.y) * invDir.y
        val t4 = (box.max.y - origin.y) * invDir.y
        val t5 = (box.min.z - origin.z) * invDir.z
        val t6 = (box.max.z - origin.z) * invDir.z

        val tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6))
        val tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6))

        // Ray is intersecting AABB, but whole AABB is behind us
        if (tmax < 0f) {
            return null
        }

        // Ray doesn't intersect AABB
        if (tmin > tmax) {
            return null
        }

        // If tmin is negative, we're inside the box, so return tmax
        return if (tmin < 0f) tmax else tmin
    }

    /**
     * Tests for intersection between this ray and a sphere.
     *
     * @param center The center of the sphere
     * @param radius The radius of the sphere
     * @return The distance to the nearest intersection point, or null if no intersection
     */
    fun intersectsSphere(
        center: Vec3,
        radius: Float,
    ): Float? {
        val dir = normalizedDirection
        val oc = origin - center
        val a = dir.dot(dir)
        val b = 2f * oc.dot(dir)
        val c = oc.dot(oc) - radius * radius
        val discriminant = b * b - 4f * a * c

        if (discriminant < 0f) {
            return null
        }

        val sqrtDiscriminant = kotlin.math.sqrt(discriminant)
        val t1 = (-b - sqrtDiscriminant) / (2f * a)
        val t2 = (-b + sqrtDiscriminant) / (2f * a)

        // Return the nearest positive intersection
        return when {
            t1 > 0f -> t1
            t2 > 0f -> t2
            else -> null
        }
    }

    /**
     * Tests for intersection between this ray and a plane.
     *
     * @param planePoint A point on the plane
     * @param planeNormal The normal vector of the plane (should be normalized)
     * @return The distance to the intersection point, or null if no intersection
     */
    fun intersectsPlane(
        planePoint: Vec3,
        planeNormal: Vec3,
    ): Float? {
        val dir = normalizedDirection
        val denom = planeNormal.dot(dir)

        // Ray is parallel to the plane
        if (kotlin.math.abs(denom) < 0.0001f) {
            return null
        }

        val t = (planePoint - origin).dot(planeNormal) / denom

        // Intersection is behind the ray origin
        if (t < 0f) {
            return null
        }

        return t
    }

    /**
     * Calculates the closest point on this ray to a given point.
     *
     * @param point The reference point
     * @return The closest point on the ray
     */
    fun closestPointTo(point: Vec3): Vec3 {
        val dir = normalizedDirection
        val toPoint = point - origin
        val distance = toPoint.dot(dir).coerceAtLeast(0f)
        return origin + dir * distance
    }

    /**
     * Calculates the distance from a point to this ray.
     *
     * @param point The reference point
     * @return The shortest distance from the point to the ray
     */
    fun distanceTo(point: Vec3): Float {
        val closest = closestPointTo(point)
        return (point - closest).length()
    }

    /**
     * Calculates the squared distance from a point to this ray.
     * More efficient than distanceTo() when only comparing distances.
     *
     * @param point The reference point
     * @return The squared shortest distance from the point to the ray
     */
    fun distanceSquaredTo(point: Vec3): Float {
        val closest = closestPointTo(point)
        return (point - closest).lengthSquared()
    }

    /**
     * Creates a new ray with the direction normalized.
     *
     * @return A new ray with normalized direction
     */
    fun normalized(): Ray = Ray(origin, direction.normalized())

    /**
     * Translates this ray by an offset.
     *
     * @param offset The translation offset
     * @return A new translated ray
     */
    fun translate(offset: Vec3): Ray = Ray(origin + offset, direction)

    /**
     * Transforms this ray by moving the origin and rotating the direction.
     *
     * @param newOrigin The new origin point
     * @param newDirection The new direction vector
     * @return A new transformed ray
     */
    fun transform(
        newOrigin: Vec3,
        newDirection: Vec3,
    ): Ray = Ray(newOrigin, newDirection)

    companion object {
        /**
         * Creates a ray from an origin point towards a target point.
         *
         * @param from The origin point
         * @param to The target point
         * @return A new ray pointing from 'from' to 'to'
         */
        fun fromTo(
            from: Vec3,
            to: Vec3,
        ): Ray =
            Ray(
                origin = from,
                direction = (to - from).normalized(),
            )

        /**
         * Creates a ray from two points, with the first point as the origin.
         *
         * @param point1 The origin point
         * @param point2 A point along the ray direction
         * @return A new ray
         */
        fun throughPoints(
            point1: Vec3,
            point2: Vec3,
        ): Ray = fromTo(point1, point2)
    }
}

/**
 * Represents the result of a ray intersection test.
 *
 * @property hit Whether an intersection occurred
 * @property distance The distance along the ray to the intersection point (null if no hit)
 * @property point The intersection point in world space (null if no hit)
 * @property normal The surface normal at the intersection point (null if no hit)
 */
data class RayHit(
    val hit: Boolean,
    val distance: Float? = null,
    val point: Vec3? = null,
    val normal: Vec3? = null,
) {
    companion object {
        /**
         * A ray hit result indicating no intersection.
         */
        val MISS = RayHit(hit = false)

        /**
         * Creates a ray hit result for a successful intersection.
         *
         * @param distance The distance to the hit point
         * @param point The intersection point
         * @param normal The surface normal at the intersection
         * @return A new RayHit indicating a hit
         */
        fun hit(
            distance: Float,
            point: Vec3,
            normal: Vec3,
        ): RayHit =
            RayHit(
                hit = true,
                distance = distance,
                point = point,
                normal = normal,
            )
    }
}
