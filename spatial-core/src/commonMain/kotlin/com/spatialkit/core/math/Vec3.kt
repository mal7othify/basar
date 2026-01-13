package com.spatialkit.core.math

import kotlin.math.sqrt

/**
 * A 3D vector representation with x, y, and z components.
 *
 * This immutable data class provides various vector operations including
 * arithmetic operations, dot product, cross product, length calculations,
 * normalization, and linear interpolation.
 *
 * @property x The x component of the vector
 * @property y The y component of the vector
 * @property z The z component of the vector
 */
data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float,
) {
    /**
     * Adds this vector to another vector.
     *
     * @param other The vector to add
     * @return A new vector representing the sum
     */
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)

    /**
     * Subtracts another vector from this vector.
     *
     * @param other The vector to subtract
     * @return A new vector representing the difference
     */
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)

    /**
     * Multiplies this vector by a scalar value.
     *
     * @param scalar The scalar value to multiply by
     * @return A new scaled vector
     */
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)

    /**
     * Divides this vector by a scalar value.
     *
     * @param scalar The scalar value to divide by
     * @return A new scaled vector
     * @throws ArithmeticException if scalar is zero
     */
    operator fun div(scalar: Float): Vec3 = Vec3(x / scalar, y / scalar, z / scalar)

    /**
     * Computes the dot product of this vector and another vector.
     *
     * @param other The other vector
     * @return The dot product (scalar value)
     */
    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    /**
     * Computes the cross product of this vector and another vector.
     * The cross product produces a vector perpendicular to both input vectors.
     *
     * @param other The other vector
     * @return A new vector representing the cross product
     */
    fun cross(other: Vec3): Vec3 =
        Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x,
        )

    /**
     * Calculates the length (magnitude) of this vector.
     *
     * @return The length of the vector
     */
    fun length(): Float = sqrt(x * x + y * y + z * z)

    /**
     * Calculates the squared length of this vector.
     * This is more efficient than length() when only comparing lengths.
     *
     * @return The squared length of the vector
     */
    fun lengthSquared(): Float = x * x + y * y + z * z

    /**
     * Returns a normalized (unit length) version of this vector.
     * If the vector has zero length, returns ZERO.
     *
     * @return A new normalized vector
     */
    fun normalized(): Vec3 {
        val len = length()
        return if (len > 0f) this / len else ZERO
    }

    /**
     * Performs linear interpolation between this vector and another vector.
     *
     * @param other The target vector
     * @param t The interpolation factor (0.0 returns this, 1.0 returns other)
     * @return A new interpolated vector
     */
    fun lerp(
        other: Vec3,
        t: Float,
    ): Vec3 =
        Vec3(
            x + (other.x - x) * t,
            y + (other.y - y) * t,
            z + (other.z - z) * t,
        )

    /**
     * Calculates the distance between this vector and another vector.
     *
     * @param other The other vector
     * @return The distance between the two vectors
     */
    fun distance(other: Vec3): Float = (this - other).length()

    /**
     * Calculates the squared distance between this vector and another vector.
     * This is more efficient than distance() when only comparing distances.
     *
     * @param other The other vector
     * @return The squared distance between the two vectors
     */
    fun distanceSquared(other: Vec3): Float = (this - other).lengthSquared()

    /**
     * Negates this vector.
     *
     * @return A new vector with negated components
     */
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    /**
     * Returns the XY components as a Vec2.
     *
     * @return A new Vec2 with x and y components
     */
    fun toVec2(): Vec2 = Vec2(x, y)

    companion object {
        /**
         * A zero vector (0, 0, 0).
         */
        val ZERO = Vec3(0f, 0f, 0f)

        /**
         * A unit vector (1, 1, 1).
         */
        val ONE = Vec3(1f, 1f, 1f)

        /**
         * A unit vector pointing up (0, 1, 0).
         * In most coordinate systems, this represents the Y-axis.
         */
        val UP = Vec3(0f, 1f, 0f)

        /**
         * A unit vector pointing down (0, -1, 0).
         */
        val DOWN = Vec3(0f, -1f, 0f)

        /**
         * A unit vector pointing forward (0, 0, -1).
         * In right-handed coordinate systems (OpenGL, most graphics APIs),
         * forward is the negative Z-axis.
         */
        val FORWARD = Vec3(0f, 0f, -1f)

        /**
         * A unit vector pointing back (0, 0, 1).
         */
        val BACK = Vec3(0f, 0f, 1f)

        /**
         * A unit vector pointing left (-1, 0, 0).
         */
        val LEFT = Vec3(-1f, 0f, 0f)

        /**
         * A unit vector pointing right (1, 0, 0).
         * In most coordinate systems, this represents the positive X-axis.
         */
        val RIGHT = Vec3(1f, 0f, 0f)

        /**
         * Creates a vector from integer components.
         *
         * @param x The x component
         * @param y The y component
         * @param z The z component
         * @return A new Vec3
         */
        fun of(
            x: Int,
            y: Int,
            z: Int,
        ): Vec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

        /**
         * Creates a vector from double components.
         *
         * @param x The x component
         * @param y The y component
         * @param z The z component
         * @return A new Vec3
         */
        fun of(
            x: Double,
            y: Double,
            z: Double,
        ): Vec3 = Vec3(x.toFloat(), y.toFloat(), z.toFloat())

        /**
         * Creates a Vec3 from a Vec2, using 0 for the z component.
         *
         * @param vec2 The Vec2 to convert
         * @param z The z component (defaults to 0)
         * @return A new Vec3
         */
        fun from(
            vec2: Vec2,
            z: Float = 0f,
        ): Vec3 = Vec3(vec2.x, vec2.y, z)
    }
}

/**
 * Extension to allow scalar * vector multiplication.
 */
operator fun Float.times(vec: Vec3): Vec3 = vec * this

/**
 * Extension to allow scalar * vector multiplication with Int.
 */
operator fun Int.times(vec: Vec3): Vec3 = vec * this.toFloat()
