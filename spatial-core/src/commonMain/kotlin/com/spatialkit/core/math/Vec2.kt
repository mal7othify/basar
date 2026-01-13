package com.spatialkit.core.math

import kotlin.math.sqrt

/**
 * A 2D vector representation with x and y components.
 *
 * This immutable data class provides various vector operations including
 * arithmetic operations, dot product, length calculations, normalization,
 * and linear interpolation.
 *
 * @property x The x component of the vector
 * @property y The y component of the vector
 */
data class Vec2(
    val x: Float,
    val y: Float,
) {
    /**
     * Adds this vector to another vector.
     *
     * @param other The vector to add
     * @return A new vector representing the sum
     */
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    /**
     * Subtracts another vector from this vector.
     *
     * @param other The vector to subtract
     * @return A new vector representing the difference
     */
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    /**
     * Multiplies this vector by a scalar value.
     *
     * @param scalar The scalar value to multiply by
     * @return A new scaled vector
     */
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)

    /**
     * Divides this vector by a scalar value.
     *
     * @param scalar The scalar value to divide by
     * @return A new scaled vector
     * @throws ArithmeticException if scalar is zero
     */
    operator fun div(scalar: Float): Vec2 = Vec2(x / scalar, y / scalar)

    /**
     * Computes the dot product of this vector and another vector.
     *
     * @param other The other vector
     * @return The dot product (scalar value)
     */
    fun dot(other: Vec2): Float = x * other.x + y * other.y

    /**
     * Calculates the length (magnitude) of this vector.
     *
     * @return The length of the vector
     */
    fun length(): Float = sqrt(x * x + y * y)

    /**
     * Calculates the squared length of this vector.
     * This is more efficient than length() when only comparing lengths.
     *
     * @return The squared length of the vector
     */
    fun lengthSquared(): Float = x * x + y * y

    /**
     * Returns a normalized (unit length) version of this vector.
     * If the vector has zero length, returns ZERO.
     *
     * @return A new normalized vector
     */
    fun normalized(): Vec2 {
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
        other: Vec2,
        t: Float,
    ): Vec2 =
        Vec2(
            x + (other.x - x) * t,
            y + (other.y - y) * t,
        )

    /**
     * Calculates the distance between this vector and another vector.
     *
     * @param other The other vector
     * @return The distance between the two vectors
     */
    fun distance(other: Vec2): Float = (this - other).length()

    /**
     * Calculates the squared distance between this vector and another vector.
     * This is more efficient than distance() when only comparing distances.
     *
     * @param other The other vector
     * @return The squared distance between the two vectors
     */
    fun distanceSquared(other: Vec2): Float = (this - other).lengthSquared()

    /**
     * Negates this vector.
     *
     * @return A new vector with negated components
     */
    operator fun unaryMinus(): Vec2 = Vec2(-x, -y)

    companion object {
        /**
         * A zero vector (0, 0).
         */
        val ZERO = Vec2(0f, 0f)

        /**
         * A unit vector (1, 1).
         */
        val ONE = Vec2(1f, 1f)

        /**
         * A unit vector pointing right (1, 0).
         */
        val RIGHT = Vec2(1f, 0f)

        /**
         * A unit vector pointing left (-1, 0).
         */
        val LEFT = Vec2(-1f, 0f)

        /**
         * A unit vector pointing up (0, 1).
         */
        val UP = Vec2(0f, 1f)

        /**
         * A unit vector pointing down (0, -1).
         */
        val DOWN = Vec2(0f, -1f)

        /**
         * Creates a vector from integer components.
         *
         * @param x The x component
         * @param y The y component
         * @return A new Vec2
         */
        fun of(
            x: Int,
            y: Int,
        ): Vec2 = Vec2(x.toFloat(), y.toFloat())

        /**
         * Creates a vector from double components.
         *
         * @param x The x component
         * @param y The y component
         * @return A new Vec2
         */
        fun of(
            x: Double,
            y: Double,
        ): Vec2 = Vec2(x.toFloat(), y.toFloat())
    }
}

/**
 * Extension to allow scalar * vector multiplication.
 */
operator fun Float.times(vec: Vec2): Vec2 = vec * this

/**
 * Extension to allow scalar * vector multiplication with Int.
 */
operator fun Int.times(vec: Vec2): Vec2 = vec * this.toFloat()
