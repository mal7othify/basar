package com.spatialkit.core.primitives

import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.BoundingBox
import com.spatialkit.core.types.Size3D

/**
 * Base interface for all geometric shapes in the spatial scene.
 *
 * Shapes define the visual and collision geometry of objects in 3D space.
 * Each shape can calculate its bounding box for efficient culling and
 * collision detection.
 */
sealed interface ShapeGeometry {
    /**
     * Calculates the axis-aligned bounding box for this shape.
     *
     * @return The bounding box encompassing this shape
     */
    fun boundingBox(): BoundingBox

    /**
     * Estimates the approximate surface area of this shape.
     *
     * @return The surface area
     */
    fun surfaceArea(): Float

    /**
     * Estimates the approximate volume of this shape.
     *
     * @return The volume
     */
    fun volume(): Float
}

/**
 * A cube (box) geometry with uniform or non-uniform dimensions.
 *
 * The cube is centered at the origin by default.
 *
 * @property size The size of the cube. If a single value, creates a uniform cube.
 *               If a Size3D, can have different width, height, and depth.
 */
data class CubeGeometry(
    val size: Size3D = Size3D.ONE,
) : ShapeGeometry {
    /**
     * Creates a uniform cube with the same dimension on all sides.
     *
     * @param size The size for all dimensions
     */
    constructor(size: Float) : this(Size3D.uniform(size))

    override fun boundingBox(): BoundingBox {
        val halfSize = size.toVec3() * 0.5f
        return BoundingBox(
            min = -halfSize,
            max = halfSize,
        )
    }

    override fun surfaceArea(): Float = size.surfaceArea()

    override fun volume(): Float = size.volume()

    /**
     * Returns the width (X dimension) of the cube.
     */
    val width: Float get() = size.width

    /**
     * Returns the height (Y dimension) of the cube.
     */
    val height: Float get() = size.height

    /**
     * Returns the depth (Z dimension) of the cube.
     */
    val depth: Float get() = size.depth

    companion object {
        /**
         * A unit cube with size 1x1x1.
         */
        val UNIT = CubeGeometry(1f)

        /**
         * Creates a cube from width, height, and depth.
         *
         * @param width The width (X dimension)
         * @param height The height (Y dimension)
         * @param depth The depth (Z dimension)
         * @return A new CubeGeometry
         */
        fun of(
            width: Float,
            height: Float,
            depth: Float,
        ): CubeGeometry = CubeGeometry(Size3D(width, height, depth))
    }
}

/**
 * A sphere geometry defined by its radius.
 *
 * The sphere is centered at the origin by default.
 *
 * @property radius The radius of the sphere
 * @property segments The number of segments used for tessellation (higher = smoother).
 *                    Minimum value is 3. Default is 32.
 */
data class SphereGeometry(
    val radius: Float,
    val segments: Int = 32,
) : ShapeGeometry {
    init {
        require(radius > 0f) { "Sphere radius must be positive, got $radius" }
        require(segments >= 3) { "Sphere segments must be at least 3, got $segments" }
    }

    override fun boundingBox(): BoundingBox {
        val extent = Vec3(radius, radius, radius)
        return BoundingBox(
            min = -extent,
            max = extent,
        )
    }

    override fun surfaceArea(): Float = 4f * kotlin.math.PI.toFloat() * radius * radius

    override fun volume(): Float = (4f / 3f) * kotlin.math.PI.toFloat() * radius * radius * radius

    /**
     * Returns the diameter of the sphere.
     */
    val diameter: Float get() = radius * 2f

    companion object {
        /**
         * A unit sphere with radius 1.
         */
        val UNIT = SphereGeometry(1f)

        /**
         * Creates a sphere from a diameter.
         *
         * @param diameter The diameter of the sphere
         * @param segments The number of segments
         * @return A new SphereGeometry
         */
        fun fromDiameter(
            diameter: Float,
            segments: Int = 32,
        ): SphereGeometry = SphereGeometry(diameter / 2f, segments)
    }
}

/**
 * A flat plane geometry defined by its width and height.
 *
 * The plane lies in the XY plane (Z = 0) and is centered at the origin by default.
 *
 * @property width The width (X dimension) of the plane
 * @property height The height (Y dimension) of the plane
 */
data class PlaneGeometry(
    val width: Float,
    val height: Float,
) : ShapeGeometry {
    init {
        require(width > 0f) { "Plane width must be positive, got $width" }
        require(height > 0f) { "Plane height must be positive, got $height" }
    }

    override fun boundingBox(): BoundingBox {
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        return BoundingBox(
            min = Vec3(-halfWidth, -halfHeight, 0f),
            max = Vec3(halfWidth, halfHeight, 0f),
        )
    }

    override fun surfaceArea(): Float = width * height * 2f // Both sides

    override fun volume(): Float = 0f // Planes have no volume

    /**
     * Returns the size of the plane as a Size3D with depth = 0.
     */
    val size: Size3D get() = Size3D(width, height, 0f)

    companion object {
        /**
         * A unit plane with size 1x1.
         */
        val UNIT = PlaneGeometry(1f, 1f)

        /**
         * Creates a square plane with uniform dimensions.
         *
         * @param size The size for both width and height
         * @return A new PlaneGeometry
         */
        fun square(size: Float): PlaneGeometry = PlaneGeometry(size, size)
    }
}

/**
 * A cylinder geometry defined by its radius and height.
 *
 * The cylinder's axis is aligned with the Y-axis, centered at the origin.
 *
 * @property radius The radius of the cylinder
 * @property height The height of the cylinder (along Y-axis)
 * @property segments The number of segments around the circumference (higher = smoother).
 *                    Minimum value is 3. Default is 32.
 */
data class CylinderGeometry(
    val radius: Float,
    val height: Float,
    val segments: Int = 32,
) : ShapeGeometry {
    init {
        require(radius > 0f) { "Cylinder radius must be positive, got $radius" }
        require(height > 0f) { "Cylinder height must be positive, got $height" }
        require(segments >= 3) { "Cylinder segments must be at least 3, got $segments" }
    }

    override fun boundingBox(): BoundingBox {
        val halfHeight = height / 2f
        return BoundingBox(
            min = Vec3(-radius, -halfHeight, -radius),
            max = Vec3(radius, halfHeight, radius),
        )
    }

    override fun surfaceArea(): Float {
        val lateral = 2f * kotlin.math.PI.toFloat() * radius * height
        val caps = 2f * kotlin.math.PI.toFloat() * radius * radius
        return lateral + caps
    }

    override fun volume(): Float = kotlin.math.PI.toFloat() * radius * radius * height

    /**
     * Returns the diameter of the cylinder.
     */
    val diameter: Float get() = radius * 2f

    companion object {
        /**
         * A unit cylinder with radius 0.5 and height 1.
         */
        val UNIT = CylinderGeometry(0.5f, 1f)

        /**
         * Creates a cylinder from diameter and height.
         *
         * @param diameter The diameter of the cylinder
         * @param height The height of the cylinder
         * @param segments The number of segments
         * @return A new CylinderGeometry
         */
        fun fromDiameter(
            diameter: Float,
            height: Float,
            segments: Int = 32,
        ): CylinderGeometry = CylinderGeometry(diameter / 2f, height, segments)
    }
}

/**
 * A capsule geometry (cylinder with hemispherical ends).
 *
 * The capsule's axis is aligned with the Y-axis, centered at the origin.
 * The total height includes both the cylindrical part and the two hemispheres.
 *
 * @property radius The radius of the capsule
 * @property height The height of the cylindrical part (not including the end caps)
 */
data class CapsuleGeometry(
    val radius: Float,
    val height: Float,
) : ShapeGeometry {
    init {
        require(radius > 0f) { "Capsule radius must be positive, got $radius" }
        require(height >= 0f) { "Capsule height must be non-negative, got $height" }
    }

    override fun boundingBox(): BoundingBox {
        val halfHeight = height / 2f
        val totalHalfHeight = halfHeight + radius
        return BoundingBox(
            min = Vec3(-radius, -totalHalfHeight, -radius),
            max = Vec3(radius, totalHalfHeight, radius),
        )
    }

    override fun surfaceArea(): Float {
        val cylinderLateral = 2f * kotlin.math.PI.toFloat() * radius * height
        val sphereSurface = 4f * kotlin.math.PI.toFloat() * radius * radius
        return cylinderLateral + sphereSurface
    }

    override fun volume(): Float {
        val cylinderVolume = kotlin.math.PI.toFloat() * radius * radius * height
        val sphereVolume = (4f / 3f) * kotlin.math.PI.toFloat() * radius * radius * radius
        return cylinderVolume + sphereVolume
    }

    /**
     * Returns the total height of the capsule including the end caps.
     */
    val totalHeight: Float get() = height + radius * 2f

    /**
     * Returns the diameter of the capsule.
     */
    val diameter: Float get() = radius * 2f

    companion object {
        /**
         * A unit capsule with radius 0.5 and cylindrical height 1.
         */
        val UNIT = CapsuleGeometry(0.5f, 1f)

        /**
         * Creates a capsule from diameter and height.
         *
         * @param diameter The diameter of the capsule
         * @param height The height of the cylindrical part
         * @return A new CapsuleGeometry
         */
        fun fromDiameter(
            diameter: Float,
            height: Float,
        ): CapsuleGeometry = CapsuleGeometry(diameter / 2f, height)

        /**
         * Creates a capsule from total height (including end caps) and radius.
         *
         * @param radius The radius of the capsule
         * @param totalHeight The total height including the hemispheres
         * @return A new CapsuleGeometry
         */
        fun fromTotalHeight(
            radius: Float,
            totalHeight: Float,
        ): CapsuleGeometry = CapsuleGeometry(radius, (totalHeight - radius * 2f).coerceAtLeast(0f))
    }
}

/**
 * A cone geometry defined by its base radius and height.
 *
 * The cone's axis is aligned with the Y-axis, with the base at y = -height/2
 * and the tip at y = height/2.
 *
 * @property radius The radius of the base
 * @property height The height of the cone
 * @property segments The number of segments around the base (higher = smoother).
 *                    Minimum value is 3. Default is 32.
 */
data class ConeGeometry(
    val radius: Float,
    val height: Float,
    val segments: Int = 32,
) : ShapeGeometry {
    init {
        require(radius > 0f) { "Cone radius must be positive, got $radius" }
        require(height > 0f) { "Cone height must be positive, got $height" }
        require(segments >= 3) { "Cone segments must be at least 3, got $segments" }
    }

    override fun boundingBox(): BoundingBox {
        val halfHeight = height / 2f
        return BoundingBox(
            min = Vec3(-radius, -halfHeight, -radius),
            max = Vec3(radius, halfHeight, radius),
        )
    }

    override fun surfaceArea(): Float {
        val slantHeight = kotlin.math.sqrt(radius * radius + height * height)
        val lateral = kotlin.math.PI.toFloat() * radius * slantHeight
        val base = kotlin.math.PI.toFloat() * radius * radius
        return lateral + base
    }

    override fun volume(): Float = (1f / 3f) * kotlin.math.PI.toFloat() * radius * radius * height

    /**
     * Returns the diameter of the cone's base.
     */
    val diameter: Float get() = radius * 2f

    /**
     * Returns the slant height of the cone.
     */
    val slantHeight: Float get() = kotlin.math.sqrt(radius * radius + height * height)

    companion object {
        /**
         * A unit cone with radius 0.5 and height 1.
         */
        val UNIT = ConeGeometry(0.5f, 1f)

        /**
         * Creates a cone from diameter and height.
         *
         * @param diameter The diameter of the base
         * @param height The height of the cone
         * @param segments The number of segments
         * @return A new ConeGeometry
         */
        fun fromDiameter(
            diameter: Float,
            height: Float,
            segments: Int = 32,
        ): ConeGeometry = ConeGeometry(diameter / 2f, height, segments)
    }
}
