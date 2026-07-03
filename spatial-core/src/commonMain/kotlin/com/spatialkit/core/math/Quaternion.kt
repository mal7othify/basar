package com.spatialkit.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A quaternion representation for 3D rotations.
 *
 * Quaternions provide a robust way to represent rotations without gimbal lock.
 * They consist of four components: x, y, z (the vector part) and w (the scalar part).
 * A unit quaternion represents a valid rotation.
 *
 * @property x The x component of the quaternion
 * @property y The y component of the quaternion
 * @property z The z component of the quaternion
 * @property w The w (scalar) component of the quaternion
 */
public data class Quaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
) {
    /**
     * Multiplies this quaternion by another quaternion (quaternion composition).
     * The result represents the combined rotation of both quaternions.
     *
     * @param other The quaternion to multiply with
     * @return A new quaternion representing the combined rotation
     */
    public operator fun times(other: Quaternion): Quaternion =
        Quaternion(
            w * other.x + x * other.w + y * other.z - z * other.y,
            w * other.y - x * other.z + y * other.w + z * other.x,
            w * other.z + x * other.y - y * other.x + z * other.w,
            w * other.w - x * other.x - y * other.y - z * other.z,
        )

    /**
     * Rotates a 3D vector by this quaternion.
     *
     * @param vec The vector to rotate
     * @return The rotated vector
     */
    public operator fun times(vec: Vec3): Vec3 {
        // Convert to quaternion multiplication: q * v * q^-1
        val qVec = Quaternion(vec.x, vec.y, vec.z, 0f)
        val result = this * qVec * conjugate()
        return Vec3(result.x, result.y, result.z)
    }

    /**
     * Returns the conjugate of this quaternion.
     * For unit quaternions, the conjugate is equivalent to the inverse.
     *
     * @return The conjugate quaternion
     */
    public fun conjugate(): Quaternion = Quaternion(-x, -y, -z, w)

    /**
     * Calculates the length (magnitude) of this quaternion.
     *
     * @return The length of the quaternion
     */
    public fun length(): Float = sqrt(x * x + y * y + z * z + w * w)

    /**
     * Calculates the squared length of this quaternion.
     * This is more efficient than length() when only comparing lengths.
     *
     * @return The squared length of the quaternion
     */
    public fun lengthSquared(): Float = x * x + y * y + z * z + w * w

    /**
     * Returns a normalized (unit length) version of this quaternion.
     * Unit quaternions represent valid rotations.
     *
     * @return A new normalized quaternion
     */
    public fun normalized(): Quaternion {
        val len = length()
        return if (len > 0f) {
            Quaternion(x / len, y / len, z / len, w / len)
        } else {
            IDENTITY
        }
    }

    /**
     * Computes the dot product with another quaternion.
     *
     * @param other The other quaternion
     * @return The dot product
     */
    public fun dot(other: Quaternion): Float = x * other.x + y * other.y + z * other.z + w * other.w

    /**
     * Performs spherical linear interpolation (slerp) between this quaternion and another.
     * This provides smooth interpolation between rotations.
     *
     * @param other The target quaternion
     * @param t The interpolation factor (0.0 returns this, 1.0 returns other)
     * @return A new interpolated quaternion
     */
    public fun slerp(
        other: Quaternion,
        t: Float,
    ): Quaternion {
        var cosHalfTheta = dot(other)

        // If the dot product is negative, slerp won't take the shorter path
        // Fix by reversing one quaternion
        val target =
            if (cosHalfTheta < 0f) {
                cosHalfTheta = -cosHalfTheta
                Quaternion(-other.x, -other.y, -other.z, -other.w)
            } else {
                other
            }

        // If quaternions are very close, use linear interpolation
        if (cosHalfTheta >= 0.99f) {
            return Quaternion(
                x + (target.x - x) * t,
                y + (target.y - y) * t,
                z + (target.z - z) * t,
                w + (target.w - w) * t,
            ).normalized()
        }

        val halfTheta = acos(cosHalfTheta)
        val sinHalfTheta = sqrt(1f - cosHalfTheta * cosHalfTheta)

        // If theta is 180 degrees, the result is not fully defined
        if (abs(sinHalfTheta) < 0.001f) {
            return Quaternion(
                (x + target.x) * 0.5f,
                (y + target.y) * 0.5f,
                (z + target.z) * 0.5f,
                (w + target.w) * 0.5f,
            )
        }

        val ratioA = sin((1 - t) * halfTheta) / sinHalfTheta
        val ratioB = sin(t * halfTheta) / sinHalfTheta

        return Quaternion(
            x * ratioA + target.x * ratioB,
            y * ratioA + target.y * ratioB,
            z * ratioA + target.z * ratioB,
            w * ratioA + target.w * ratioB,
        )
    }

    /**
     * Returns the inverse of this quaternion.
     * For unit quaternions, this is the same as the conjugate.
     *
     * @return The inverse quaternion
     */
    public fun inverse(): Quaternion {
        val lenSq = lengthSquared()
        return if (lenSq > 0f) {
            conjugate() / lenSq
        } else {
            IDENTITY
        }
    }

    /**
     * Divides this quaternion by a scalar value.
     *
     * @param scalar The scalar to divide by
     * @return A new scaled quaternion
     */
    public operator fun div(scalar: Float): Quaternion = Quaternion(x / scalar, y / scalar, z / scalar, w / scalar)

    /**
     * Converts this quaternion to Euler angles (in radians).
     * Returns a Vec3 where x = pitch, y = yaw, z = roll.
     *
     * @return Euler angles in radians
     */
    public fun toEulerAngles(): Vec3 {
        // Roll (x-axis rotation)
        val sinRCosP = 2 * (w * x + y * z)
        val cosRCosP = 1 - 2 * (x * x + y * y)
        val roll = atan2(sinRCosP, cosRCosP)

        // Pitch (y-axis rotation)
        val sinP = 2 * (w * y - z * x)
        val pitch =
            if (abs(sinP) >= 1) {
                (PI.toFloat() / 2) * sign(sinP) // Use 90 degrees if out of range
            } else {
                asin(sinP)
            }

        // Yaw (z-axis rotation)
        val sinYCosP = 2 * (w * z + x * y)
        val cosYCosP = 1 - 2 * (y * y + z * z)
        val yaw = atan2(sinYCosP, cosYCosP)

        return Vec3(pitch, yaw, roll)
    }

    public companion object {
        /**
         * The identity quaternion representing no rotation (0, 0, 0, 1).
         */
        public val IDENTITY: Quaternion = Quaternion(0f, 0f, 0f, 1f)

        /**
         * Creates a quaternion from Euler angles (in radians).
         *
         * @param pitch Rotation around X-axis (in radians)
         * @param yaw Rotation around Y-axis (in radians)
         * @param roll Rotation around Z-axis (in radians)
         * @return A new quaternion representing the rotation
         */
        public fun fromEuler(
            pitch: Float,
            yaw: Float,
            roll: Float,
        ): Quaternion {
            val cy = cos(yaw * 0.5f)
            val sy = sin(yaw * 0.5f)
            val cp = cos(pitch * 0.5f)
            val sp = sin(pitch * 0.5f)
            val cr = cos(roll * 0.5f)
            val sr = sin(roll * 0.5f)

            return Quaternion(
                x = cy * sp * cr + sy * cp * sr,
                y = sy * cp * cr - cy * sp * sr,
                z = cy * cp * sr - sy * sp * cr,
                w = cy * cp * cr + sy * sp * sr,
            )
        }

        /**
         * Creates a quaternion from Euler angles provided as a Vec3 (in radians).
         *
         * @param euler A Vec3 where x = pitch, y = yaw, z = roll (in radians)
         * @return A new quaternion representing the rotation
         */
        public fun fromEuler(euler: Vec3): Quaternion = fromEuler(euler.x, euler.y, euler.z)

        /**
         * Creates a quaternion from an axis and angle.
         *
         * @param axis The axis of rotation (should be normalized)
         * @param angle The angle of rotation in radians
         * @return A new quaternion representing the rotation
         */
        public fun fromAxisAngle(
            axis: Vec3,
            angle: Float,
        ): Quaternion {
            val halfAngle = angle * 0.5f
            val s = sin(halfAngle)
            val normalizedAxis = axis.normalized()

            return Quaternion(
                x = normalizedAxis.x * s,
                y = normalizedAxis.y * s,
                z = normalizedAxis.z * s,
                w = cos(halfAngle),
            )
        }

        /**
         * Creates a quaternion that rotates from one direction to another.
         *
         * @param from The source direction (should be normalized)
         * @param to The target direction (should be normalized)
         * @return A new quaternion representing the rotation
         */
        public fun fromToRotation(
            from: Vec3,
            to: Vec3,
        ): Quaternion {
            val fromNorm = from.normalized()
            val toNorm = to.normalized()

            val dot = fromNorm.dot(toNorm)

            // Vectors are parallel
            if (dot >= 0.999999f) {
                return IDENTITY
            }

            // Vectors are opposite
            if (dot <= -0.999999f) {
                // Find an orthogonal axis
                var axis = Vec3.RIGHT.cross(fromNorm)
                if (axis.lengthSquared() < 0.000001f) {
                    axis = Vec3.UP.cross(fromNorm)
                }
                return fromAxisAngle(axis.normalized(), PI.toFloat())
            }

            val axis = fromNorm.cross(toNorm)
            val s = sqrt((1 + dot) * 2)
            val invS = 1f / s

            return Quaternion(
                x = axis.x * invS,
                y = axis.y * invS,
                z = axis.z * invS,
                w = s * 0.5f,
            ).normalized()
        }

        /**
         * Creates a quaternion that looks in a specific direction.
         * This rotation will make the object's local forward direction (0, 0, 1)
         * point in the specified world direction.
         *
         * @param forward The forward direction in world space
         * @param up The up direction (defaults to Vec3.UP)
         * @return A new quaternion representing the rotation
         */
        public fun lookRotation(
            forward: Vec3,
            up: Vec3 = Vec3.UP,
        ): Quaternion {
            // Use fromToRotation to create a rotation from default forward to target forward
            // Default forward in object space is (0, 0, 1)
            val defaultForward = Vec3(0f, 0f, 1f)
            return fromToRotation(defaultForward, forward.normalized())
        }
    }
}
