package com.spatialkit.core.types

import com.spatialkit.core.math.Vec3

/**
 * Represents the dimensions of a 3D object.
 *
 * @property width The width (X dimension)
 * @property height The height (Y dimension)
 * @property depth The depth (Z dimension)
 */
public data class Size3D(
    val width: Float,
    val height: Float,
    val depth: Float,
) {
    init {
        require(width >= 0f) { "Width must be non-negative, got $width" }
        require(height >= 0f) { "Height must be non-negative, got $height" }
        require(depth >= 0f) { "Depth must be non-negative, got $depth" }
    }

    /**
     * Calculates the volume of this size.
     *
     * @return The volume (width × height × depth)
     */
    public fun volume(): Float = width * height * depth

    /**
     * Calculates the surface area of a box with this size.
     *
     * @return The surface area
     */
    public fun surfaceArea(): Float = 2f * (width * height + width * depth + height * depth)

    /**
     * Returns the longest dimension of this size.
     *
     * @return The maximum of width, height, and depth
     */
    public fun maxDimension(): Float = maxOf(width, height, depth)

    /**
     * Returns the shortest dimension of this size.
     *
     * @return The minimum of width, height, and depth
     */
    public fun minDimension(): Float = minOf(width, height, depth)

    /**
     * Converts this size to a Vec3.
     *
     * @return A new Vec3 with (width, height, depth)
     */
    public fun toVec3(): Vec3 = Vec3(width, height, depth)

    /**
     * Multiplies this size by a scalar value.
     *
     * @param scalar The scalar multiplier
     * @return A new scaled size
     */
    public operator fun times(scalar: Float): Size3D =
        Size3D(
            width = width * scalar,
            height = height * scalar,
            depth = depth * scalar,
        )

    /**
     * Divides this size by a scalar value.
     *
     * @param scalar The scalar divisor
     * @return A new scaled size
     */
    public operator fun div(scalar: Float): Size3D =
        Size3D(
            width = width / scalar,
            height = height / scalar,
            depth = depth / scalar,
        )

    /**
     * Adds two sizes component-wise.
     *
     * @param other The size to add
     * @return A new size with summed components
     */
    public operator fun plus(other: Size3D): Size3D =
        Size3D(
            width = width + other.width,
            height = height + other.height,
            depth = depth + other.depth,
        )

    /**
     * Subtracts two sizes component-wise.
     *
     * @param other The size to subtract
     * @return A new size with subtracted components
     */
    public operator fun minus(other: Size3D): Size3D =
        Size3D(
            width = (width - other.width).coerceAtLeast(0f),
            height = (height - other.height).coerceAtLeast(0f),
            depth = (depth - other.depth).coerceAtLeast(0f),
        )

    /**
     * Performs linear interpolation between this size and another.
     *
     * @param other The target size
     * @param fraction The interpolation factor (0.0 returns this, 1.0 returns other)
     * @return A new interpolated size
     */
    public fun lerp(
        other: Size3D,
        fraction: Float,
    ): Size3D {
        val t = fraction.coerceIn(0f, 1f)
        return Size3D(
            width = width + (other.width - width) * t,
            height = height + (other.height - height) * t,
            depth = depth + (other.depth - depth) * t,
        )
    }

    /**
     * Checks if this size is effectively zero (all dimensions near zero).
     *
     * @param epsilon The tolerance for comparison
     * @return True if all dimensions are less than epsilon
     */
    public fun isZero(epsilon: Float = 0.0001f): Boolean = width < epsilon && height < epsilon && depth < epsilon

    /**
     * Returns a new size with the specified width.
     *
     * @param width The new width
     * @return A new size with updated width
     */
    public fun withWidth(width: Float): Size3D = copy(width = width.coerceAtLeast(0f))

    /**
     * Returns a new size with the specified height.
     *
     * @param height The new height
     * @return A new size with updated height
     */
    public fun withHeight(height: Float): Size3D = copy(height = height.coerceAtLeast(0f))

    /**
     * Returns a new size with the specified depth.
     *
     * @param depth The new depth
     * @return A new size with updated depth
     */
    public fun withDepth(depth: Float): Size3D = copy(depth = depth.coerceAtLeast(0f))

    public companion object {
        /**
         * A size with all dimensions set to zero.
         */
        public val ZERO: Size3D = Size3D(0f, 0f, 0f)

        /**
         * A size with all dimensions set to one (unit cube).
         */
        public val ONE: Size3D = Size3D(1f, 1f, 1f)

        /**
         * Creates a uniform size where all dimensions are equal.
         *
         * @param size The size value for all dimensions
         * @return A new Size3D with all dimensions set to size
         */
        public fun uniform(size: Float): Size3D = Size3D(size, size, size)

        /**
         * Creates a size from a Vec3.
         *
         * @param vec The vector containing size dimensions
         * @return A new Size3D
         */
        public fun from(vec: Vec3): Size3D =
            Size3D(
                width = vec.x.coerceAtLeast(0f),
                height = vec.y.coerceAtLeast(0f),
                depth = vec.z.coerceAtLeast(0f),
            )

        /**
         * Creates a size from integer dimensions.
         *
         * @param width The width
         * @param height The height
         * @param depth The depth
         * @return A new Size3D
         */
        public fun of(
            width: Int,
            height: Int,
            depth: Int,
        ): Size3D =
            Size3D(
                width = width.toFloat(),
                height = height.toFloat(),
                depth = depth.toFloat(),
            )
    }
}

/**
 * Extension to create a uniform Size3D from a number.
 *
 * Example: `5f.size3D` creates Size3D(5, 5, 5)
 */
public val Number.size3D: Size3D
    get() = Size3D.uniform(this.toFloat())

/**
 * Extension to create a Size3D from a pair of numbers (width and height, with depth = 0).
 *
 * Example: `(10f to 20f).size3D` creates Size3D(10, 20, 0)
 */
public val Pair<Number, Number>.size3D: Size3D
    get() = Size3D(first.toFloat(), second.toFloat(), 0f)

/**
 * Extension to create a Size3D from a triple of numbers.
 *
 * Example: `Triple(10f, 20f, 30f).size3D` creates Size3D(10, 20, 30)
 */
public val Triple<Number, Number, Number>.size3D: Size3D
    get() = Size3D(first.toFloat(), second.toFloat(), third.toFloat())
