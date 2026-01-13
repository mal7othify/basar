package com.spatialkit.core.types

import com.spatialkit.core.math.Vec3
import kotlin.math.max
import kotlin.math.min

/**
 * Represents an axis-aligned bounding box (AABB) in 3D space.
 *
 * A bounding box is defined by its minimum and maximum corners,
 * and is commonly used for collision detection, spatial queries,
 * and rendering optimizations.
 *
 * @property min The minimum corner (smallest x, y, z values)
 * @property max The maximum corner (largest x, y, z values)
 */
data class BoundingBox(
    val min: Vec3,
    val max: Vec3,
) {
    init {
        require(min.x <= max.x) { "min.x must be <= max.x (got ${min.x} > ${max.x})" }
        require(min.y <= max.y) { "min.y must be <= max.y (got ${min.y} > ${max.y})" }
        require(min.z <= max.z) { "min.z must be <= max.z (got ${min.z} > ${max.z})" }
    }

    /**
     * The center point of the bounding box.
     */
    val center: Vec3
        get() = (min + max) * 0.5f

    /**
     * The size (dimensions) of the bounding box.
     */
    val size: Size3D
        get() =
            Size3D(
                width = max.x - min.x,
                height = max.y - min.y,
                depth = max.z - min.z,
            )

    /**
     * The extents (half-size) of the bounding box.
     */
    val extents: Vec3
        get() = (max - min) * 0.5f

    /**
     * Returns all 8 corners of the bounding box.
     *
     * The corners are returned in the following order:
     * 0: (min.x, min.y, min.z) - back-bottom-left
     * 1: (max.x, min.y, min.z) - back-bottom-right
     * 2: (min.x, max.y, min.z) - back-top-left
     * 3: (max.x, max.y, min.z) - back-top-right
     * 4: (min.x, min.y, max.z) - front-bottom-left
     * 5: (max.x, min.y, max.z) - front-bottom-right
     * 6: (min.x, max.y, max.z) - front-top-left
     * 7: (max.x, max.y, max.z) - front-top-right
     *
     * @return An array of 8 corner positions
     */
    val corners: Array<Vec3>
        get() =
            arrayOf(
                Vec3(min.x, min.y, min.z),
                Vec3(max.x, min.y, min.z),
                Vec3(min.x, max.y, min.z),
                Vec3(max.x, max.y, min.z),
                Vec3(min.x, min.y, max.z),
                Vec3(max.x, min.y, max.z),
                Vec3(min.x, max.y, max.z),
                Vec3(max.x, max.y, max.z),
            )

    /**
     * Calculates the volume of the bounding box.
     *
     * @return The volume
     */
    fun volume(): Float = size.volume()

    /**
     * Calculates the surface area of the bounding box.
     *
     * @return The surface area
     */
    fun surfaceArea(): Float = size.surfaceArea()

    /**
     * Checks if a point is contained within this bounding box.
     *
     * @param point The point to test
     * @return True if the point is inside or on the boundary
     */
    fun contains(point: Vec3): Boolean =
        point.x >= min.x && point.x <= max.x &&
            point.y >= min.y && point.y <= max.y &&
            point.z >= min.z && point.z <= max.z

    /**
     * Checks if another bounding box intersects with this one.
     *
     * @param other The other bounding box
     * @return True if the boxes overlap
     */
    fun intersects(other: BoundingBox): Boolean =
        min.x <= other.max.x && max.x >= other.min.x &&
            min.y <= other.max.y && max.y >= other.min.y &&
            min.z <= other.max.z && max.z >= other.min.z

    /**
     * Checks if this bounding box completely contains another bounding box.
     *
     * @param other The other bounding box
     * @return True if other is completely inside this box
     */
    fun contains(other: BoundingBox): Boolean =
        other.min.x >= min.x && other.max.x <= max.x &&
            other.min.y >= min.y && other.max.y <= max.y &&
            other.min.z >= min.z && other.max.z <= max.z

    /**
     * Expands this bounding box to include a point.
     *
     * @param point The point to include
     * @return A new bounding box that includes the point
     */
    fun expand(point: Vec3): BoundingBox =
        BoundingBox(
            min =
                Vec3(
                    min(min.x, point.x),
                    min(min.y, point.y),
                    min(min.z, point.z),
                ),
            max =
                Vec3(
                    max(max.x, point.x),
                    max(max.y, point.y),
                    max(max.z, point.z),
                ),
        )

    /**
     * Expands this bounding box to include another bounding box.
     *
     * @param other The bounding box to include
     * @return A new bounding box that includes both boxes
     */
    fun expand(other: BoundingBox): BoundingBox =
        BoundingBox(
            min =
                Vec3(
                    min(min.x, other.min.x),
                    min(min.y, other.min.y),
                    min(min.z, other.min.z),
                ),
            max =
                Vec3(
                    max(max.x, other.max.x),
                    max(max.y, other.max.y),
                    max(max.z, other.max.z),
                ),
        )

    /**
     * Expands this bounding box by a uniform amount in all directions.
     *
     * @param amount The amount to expand (can be negative to shrink)
     * @return A new expanded bounding box
     */
    fun expand(amount: Float): BoundingBox =
        BoundingBox(
            min = min - Vec3(amount, amount, amount),
            max = max + Vec3(amount, amount, amount),
        )

    /**
     * Expands this bounding box by different amounts per axis.
     *
     * @param x The amount to expand along the X axis
     * @param y The amount to expand along the Y axis
     * @param z The amount to expand along the Z axis
     * @return A new expanded bounding box
     */
    fun expand(
        x: Float,
        y: Float,
        z: Float,
    ): BoundingBox =
        BoundingBox(
            min = min - Vec3(x, y, z),
            max = max + Vec3(x, y, z),
        )

    /**
     * Calculates the closest point in this bounding box to a given point.
     *
     * @param point The reference point
     * @return The closest point on or inside the bounding box
     */
    fun closestPoint(point: Vec3): Vec3 =
        Vec3(
            point.x.coerceIn(min.x, max.x),
            point.y.coerceIn(min.y, max.y),
            point.z.coerceIn(min.z, max.z),
        )

    /**
     * Calculates the squared distance from a point to this bounding box.
     * If the point is inside, returns 0.
     *
     * @param point The reference point
     * @return The squared distance
     */
    fun distanceSquared(point: Vec3): Float {
        val closest = closestPoint(point)
        return (point - closest).lengthSquared()
    }

    /**
     * Calculates the distance from a point to this bounding box.
     * If the point is inside, returns 0.
     *
     * @param point The reference point
     * @return The distance
     */
    fun distance(point: Vec3): Float {
        val closest = closestPoint(point)
        return (point - closest).length()
    }

    /**
     * Calculates the intersection of this bounding box with another.
     *
     * @param other The other bounding box
     * @return The intersection bounding box, or null if they don't intersect
     */
    fun intersection(other: BoundingBox): BoundingBox? {
        if (!intersects(other)) return null

        return BoundingBox(
            min =
                Vec3(
                    max(min.x, other.min.x),
                    max(min.y, other.min.y),
                    max(min.z, other.min.z),
                ),
            max =
                Vec3(
                    min(max.x, other.max.x),
                    min(max.y, other.max.y),
                    min(max.z, other.max.z),
                ),
        )
    }

    /**
     * Translates this bounding box by an offset.
     *
     * @param offset The translation offset
     * @return A new translated bounding box
     */
    fun translate(offset: Vec3): BoundingBox =
        BoundingBox(
            min = min + offset,
            max = max + offset,
        )

    /**
     * Scales this bounding box from its center.
     *
     * @param scale The scale factor
     * @return A new scaled bounding box
     */
    fun scale(scale: Float): BoundingBox {
        val center = center
        val halfSize = extents * scale
        return BoundingBox(
            min = center - halfSize,
            max = center + halfSize,
        )
    }

    /**
     * Scales this bounding box with per-axis scaling from its center.
     *
     * @param scaleX The X-axis scale factor
     * @param scaleY The Y-axis scale factor
     * @param scaleZ The Z-axis scale factor
     * @return A new scaled bounding box
     */
    fun scale(
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float,
    ): BoundingBox {
        val center = center
        val ext = extents
        val halfSize = Vec3(ext.x * scaleX, ext.y * scaleY, ext.z * scaleZ)
        return BoundingBox(
            min = center - halfSize,
            max = center + halfSize,
        )
    }

    companion object {
        /**
         * An empty bounding box at the origin with zero size.
         */
        val ZERO = BoundingBox(Vec3.ZERO, Vec3.ZERO)

        /**
         * A unit bounding box centered at the origin with size 1x1x1.
         */
        val UNIT = BoundingBox(Vec3(-0.5f, -0.5f, -0.5f), Vec3(0.5f, 0.5f, 0.5f))

        /**
         * Creates a bounding box from a center point and extents (half-size).
         *
         * @param center The center point
         * @param extents The half-size in each axis
         * @return A new bounding box
         */
        fun fromCenterAndExtents(
            center: Vec3,
            extents: Vec3,
        ): BoundingBox =
            BoundingBox(
                min = center - extents,
                max = center + extents,
            )

        /**
         * Creates a bounding box from a center point and size.
         *
         * @param center The center point
         * @param size The size in each axis
         * @return A new bounding box
         */
        fun fromCenterAndSize(
            center: Vec3,
            size: Size3D,
        ): BoundingBox {
            val halfSize = size.toVec3() * 0.5f
            return BoundingBox(
                min = center - halfSize,
                max = center + halfSize,
            )
        }

        /**
         * Creates a bounding box that encompasses all given points.
         *
         * @param points The points to encompass
         * @return A new bounding box, or ZERO if no points are provided
         */
        fun fromPoints(points: List<Vec3>): BoundingBox {
            if (points.isEmpty()) return ZERO

            var minX = points[0].x
            var minY = points[0].y
            var minZ = points[0].z
            var maxX = points[0].x
            var maxY = points[0].y
            var maxZ = points[0].z

            for (point in points) {
                minX = min(minX, point.x)
                minY = min(minY, point.y)
                minZ = min(minZ, point.z)
                maxX = max(maxX, point.x)
                maxY = max(maxY, point.y)
                maxZ = max(maxZ, point.z)
            }

            return BoundingBox(
                min = Vec3(minX, minY, minZ),
                max = Vec3(maxX, maxY, maxZ),
            )
        }

        /**
         * Creates a bounding box that encompasses all given points.
         *
         * @param points The points to encompass (vararg)
         * @return A new bounding box
         */
        fun fromPoints(vararg points: Vec3): BoundingBox = fromPoints(points.toList())
    }
}
