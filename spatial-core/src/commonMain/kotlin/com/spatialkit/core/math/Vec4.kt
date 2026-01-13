package com.spatialkit.core.math

import kotlin.math.sqrt

/**
 * A 4D vector representation with x, y, z, and w components.
 *
 * This is commonly used for homogeneous coordinates in 3D graphics,
 * where the w component enables representation of points at infinity
 * and perspective transformations.
 *
 * @property x The x component of the vector
 * @property y The y component of the vector
 * @property z The z component of the vector
 * @property w The w component of the vector (typically 1.0 for points, 0.0 for directions)
 */
data class Vec4(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
) {
    /**
     * Adds this vector to another vector.
     *
     * @param other The vector to add
     * @return A new vector representing the sum
     */
    operator fun plus(other: Vec4): Vec4 = Vec4(x + other.x, y + other.y, z + other.z, w + other.w)

    /**
     * Subtracts another vector from this vector.
     *
     * @param other The vector to subtract
     * @return A new vector representing the difference
     */
    operator fun minus(other: Vec4): Vec4 = Vec4(x - other.x, y - other.y, z - other.z, w - other.w)

    /**
     * Multiplies this vector by a scalar value.
     *
     * @param scalar The scalar value to multiply by
     * @return A new scaled vector
     */
    operator fun times(scalar: Float): Vec4 = Vec4(x * scalar, y * scalar, z * scalar, w * scalar)

    /**
     * Divides this vector by a scalar value.
     *
     * @param scalar The scalar value to divide by
     * @return A new scaled vector
     * @throws ArithmeticException if scalar is zero
     */
    operator fun div(scalar: Float): Vec4 = Vec4(x / scalar, y / scalar, z / scalar, w / scalar)

    /**
     * Computes the dot product of this vector and another vector.
     *
     * @param other The other vector
     * @return The dot product (scalar value)
     */
    fun dot(other: Vec4): Float = x * other.x + y * other.y + z * other.z + w * other.w

    /**
     * Calculates the length (magnitude) of this vector.
     *
     * @return The length of the vector
     */
    fun length(): Float = sqrt(x * x + y * y + z * z + w * w)

    /**
     * Calculates the squared length of this vector.
     * This is more efficient than length() when only comparing lengths.
     *
     * @return The squared length of the vector
     */
    fun lengthSquared(): Float = x * x + y * y + z * z + w * w

    /**
     * Returns a normalized (unit length) version of this vector.
     * If the vector has zero length, returns ZERO.
     *
     * @return A new normalized vector
     */
    fun normalized(): Vec4 {
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
        other: Vec4,
        t: Float,
    ): Vec4 =
        Vec4(
            x + (other.x - x) * t,
            y + (other.y - y) * t,
            z + (other.z - z) * t,
            w + (other.w - w) * t,
        )

    /**
     * Negates this vector.
     *
     * @return A new vector with negated components
     */
    operator fun unaryMinus(): Vec4 = Vec4(-x, -y, -z, -w)

    /**
     * Returns the XYZ components as a Vec3, discarding the w component.
     *
     * @return A new Vec3 with x, y, and z components
     */
    fun toVec3(): Vec3 = Vec3(x, y, z)

    /**
     * Performs perspective division (divides x, y, z by w).
     * This converts homogeneous coordinates to 3D Cartesian coordinates.
     *
     * @return A new Vec3 with perspective division applied
     * @throws ArithmeticException if w is zero
     */
    fun perspectiveDivide(): Vec3 =
        if (w != 0f) {
            Vec3(x / w, y / w, z / w)
        } else {
            Vec3.ZERO
        }

    companion object {
        /**
         * A zero vector (0, 0, 0, 0).
         */
        val ZERO = Vec4(0f, 0f, 0f, 0f)

        /**
         * A unit vector (1, 1, 1, 1).
         */
        val ONE = Vec4(1f, 1f, 1f, 1f)

        /**
         * A point at the origin in homogeneous coordinates (0, 0, 0, 1).
         */
        val ORIGIN_POINT = Vec4(0f, 0f, 0f, 1f)

        /**
         * Creates a vector from integer components.
         *
         * @param x The x component
         * @param y The y component
         * @param z The z component
         * @param w The w component
         * @return A new Vec4
         */
        fun of(
            x: Int,
            y: Int,
            z: Int,
            w: Int,
        ): Vec4 = Vec4(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())

        /**
         * Creates a vector from double components.
         *
         * @param x The x component
         * @param y The y component
         * @param z The z component
         * @param w The w component
         * @return A new Vec4
         */
        fun of(
            x: Double,
            y: Double,
            z: Double,
            w: Double,
        ): Vec4 = Vec4(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())

        /**
         * Creates a Vec4 from a Vec3, using the specified w component.
         *
         * @param vec3 The Vec3 to convert
         * @param w The w component (defaults to 1.0 for points)
         * @return A new Vec4
         */
        fun from(
            vec3: Vec3,
            w: Float = 1f,
        ): Vec4 = Vec4(vec3.x, vec3.y, vec3.z, w)

        /**
         * Creates a point in homogeneous coordinates (w = 1.0).
         *
         * @param vec3 The position
         * @return A new Vec4 representing a point
         */
        fun point(vec3: Vec3): Vec4 = Vec4(vec3.x, vec3.y, vec3.z, 1f)

        /**
         * Creates a direction vector in homogeneous coordinates (w = 0.0).
         *
         * @param vec3 The direction
         * @return A new Vec4 representing a direction
         */
        fun direction(vec3: Vec3): Vec4 = Vec4(vec3.x, vec3.y, vec3.z, 0f)
    }
}

/**
 * Extension to allow scalar * vector multiplication.
 */
operator fun Float.times(vec: Vec4): Vec4 = vec * this

/**
 * Extension to allow scalar * vector multiplication with Int.
 */
operator fun Int.times(vec: Vec4): Vec4 = vec * this.toFloat()
