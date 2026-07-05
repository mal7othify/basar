package com.spatialkit.core.math

/**
 * Represents a 3D transformation with position, rotation, and scale.
 *
 * This is a convenient way to represent object transforms in 3D space
 * without needing to work directly with matrices. The Transform can be
 * converted to a transformation matrix when needed for rendering.
 *
 * @property position The position in 3D space
 * @property rotation The rotation as a quaternion
 * @property scale The scale in each axis
 */
public data class Transform(
    val position: Vec3 = Vec3.ZERO,
    val rotation: Quaternion = Quaternion.IDENTITY,
    val scale: Vec3 = Vec3.ONE,
) {
    /**
     * Converts this transform to a 4x4 transformation matrix.
     * The matrix represents the transformation in the order: Scale -> Rotate -> Translate.
     *
     * @return A new transformation matrix
     */
    public fun toMatrix(): Matrix4 = Matrix4.trs(position, rotation, scale)

    /**
     * Transforms a point from local space to world space.
     * This applies scale, then rotation, then translation.
     *
     * @param point The point in local space
     * @return The point in world space
     */
    public fun localToWorld(point: Vec3): Vec3 {
        // Scale
        val scaled = Vec3(point.x * scale.x, point.y * scale.y, point.z * scale.z)
        // Rotate
        val rotated = rotation * scaled
        // Translate
        return rotated + position
    }

    /**
     * Transforms a point from world space to local space.
     * This is the inverse of localToWorld.
     *
     * @param point The point in world space
     * @return The point in local space
     */
    public fun worldToLocal(point: Vec3): Vec3 {
        // Inverse translate
        val translated = point - position
        // Inverse rotate
        val rotated = rotation.inverse() * translated
        // Inverse scale
        return Vec3(
            if (scale.x != 0f) rotated.x / scale.x else rotated.x,
            if (scale.y != 0f) rotated.y / scale.y else rotated.y,
            if (scale.z != 0f) rotated.z / scale.z else rotated.z,
        )
    }

    /**
     * Transforms a direction from local space to world space.
     * This applies rotation only (no translation or scale).
     *
     * @param direction The direction in local space
     * @return The direction in world space
     */
    public fun localToWorldDirection(direction: Vec3): Vec3 = rotation * direction

    /**
     * Transforms a direction from world space to local space.
     * This is the inverse of localToWorldDirection.
     *
     * @param direction The direction in world space
     * @return The direction in local space
     */
    public fun worldToLocalDirection(direction: Vec3): Vec3 = rotation.inverse() * direction

    /**
     * Returns the forward direction of this transform in world space.
     *
     * Forward is local [Vec3.FORWARD] (0, 0, -1) transformed by this rotation —
     * the library-wide right-handed convention (OpenGL/WebXR/ARKit style).
     * A transform created with [lookingAt] therefore satisfies
     * `forward() == direction to target`.
     *
     * @return The forward direction
     */
    public fun forward(): Vec3 = rotation * Vec3.FORWARD

    /**
     * Returns the right direction of this transform in world space.
     * Right is the local positive X-axis transformed by this rotation.
     *
     * @return The right direction
     */
    public fun right(): Vec3 = rotation * Vec3(1f, 0f, 0f)

    /**
     * Returns the up direction of this transform in world space.
     * Up is the local positive Y-axis transformed by this rotation.
     *
     * @return The up direction
     */
    public fun up(): Vec3 = rotation * Vec3(0f, 1f, 0f)

    /**
     * Combines this transform with another transform.
     * This is useful for parent-child transform hierarchies.
     *
     * @param other The child transform to combine
     * @return A new combined transform
     */
    public fun combine(other: Transform): Transform {
        val newPosition = localToWorld(other.position)
        val newRotation = rotation * other.rotation
        val newScale =
            Vec3(
                scale.x * other.scale.x,
                scale.y * other.scale.y,
                scale.z * other.scale.z,
            )
        return Transform(newPosition, newRotation, newScale)
    }

    /**
     * Returns the inverse of this transform.
     *
     * @return A new inverted transform
     */
    public fun inverse(): Transform {
        val invRotation = rotation.inverse()
        val invScale =
            Vec3(
                if (scale.x != 0f) 1f / scale.x else 0f,
                if (scale.y != 0f) 1f / scale.y else 0f,
                if (scale.z != 0f) 1f / scale.z else 0f,
            )
        val invPosition = invRotation * (position * -1f)

        return Transform(
            position =
                Vec3(
                    invPosition.x * invScale.x,
                    invPosition.y * invScale.y,
                    invPosition.z * invScale.z,
                ),
            rotation = invRotation,
            scale = invScale,
        )
    }

    /**
     * Creates a copy of this transform with a new position.
     *
     * @param position The new position
     * @return A new transform
     */
    public fun withPosition(position: Vec3): Transform = copy(position = position)

    /**
     * Creates a copy of this transform with a new rotation.
     *
     * @param rotation The new rotation
     * @return A new transform
     */
    public fun withRotation(rotation: Quaternion): Transform = copy(rotation = rotation)

    /**
     * Creates a copy of this transform with a new scale.
     *
     * @param scale The new scale
     * @return A new transform
     */
    public fun withScale(scale: Vec3): Transform = copy(scale = scale)

    /**
     * Creates a copy of this transform with a uniform scale.
     *
     * @param scale The uniform scale value
     * @return A new transform
     */
    public fun withScale(scale: Float): Transform = copy(scale = Vec3(scale, scale, scale))

    /**
     * Translates this transform by a given offset.
     *
     * @param offset The translation offset
     * @return A new translated transform
     */
    public fun translate(offset: Vec3): Transform = copy(position = position + offset)

    /**
     * Rotates this transform by a quaternion.
     *
     * @param quaternion The rotation to apply
     * @return A new rotated transform
     */
    public fun rotate(quaternion: Quaternion): Transform = copy(rotation = rotation * quaternion)

    /**
     * Rotates this transform around an axis by an angle.
     *
     * @param axis The axis to rotate around (should be normalized)
     * @param angle The angle in radians
     * @return A new rotated transform
     */
    public fun rotateAround(
        axis: Vec3,
        angle: Float,
    ): Transform {
        val rot = Quaternion.fromAxisAngle(axis, angle)
        return copy(rotation = rotation * rot)
    }

    /**
     * Scales this transform by a scale factor.
     *
     * @param scaleFactor The scale factor
     * @return A new scaled transform
     */
    public fun scaleBy(scaleFactor: Vec3): Transform =
        copy(
            scale =
                Vec3(
                    scale.x * scaleFactor.x,
                    scale.y * scaleFactor.y,
                    scale.z * scaleFactor.z,
                ),
        )

    /**
     * Uniformly scales this transform by a scale factor.
     *
     * @param scaleFactor The uniform scale factor
     * @return A new scaled transform
     */
    public fun scaleBy(scaleFactor: Float): Transform = scaleBy(Vec3(scaleFactor, scaleFactor, scaleFactor))

    /**
     * Makes this transform look at a target position.
     *
     * @param target The target position to look at
     * @param up The up direction (defaults to Vec3.UP)
     * @return A new transform looking at the target
     */
    public fun lookAt(
        target: Vec3,
        up: Vec3 = Vec3.UP,
    ): Transform {
        val direction = (target - position).normalized()
        if (direction.lengthSquared() < 0.000001f) {
            return this
        }
        val newRotation = Quaternion.lookRotation(direction, up)
        return copy(rotation = newRotation)
    }

    /**
     * Performs linear interpolation between this transform and another.
     *
     * @param other The target transform
     * @param t The interpolation factor (0.0 returns this, 1.0 returns other)
     * @return A new interpolated transform
     */
    public fun lerp(
        other: Transform,
        t: Float,
    ): Transform =
        Transform(
            position = position.lerp(other.position, t),
            rotation = rotation.slerp(other.rotation, t),
            scale = scale.lerp(other.scale, t),
        )

    public companion object {
        /**
         * The identity transform (position at origin, no rotation, scale of 1).
         */
        public val IDENTITY: Transform = Transform()

        /**
         * Creates a transform from a position only.
         *
         * @param position The position
         * @return A new transform
         */
        public fun fromPosition(position: Vec3): Transform = Transform(position = position)

        /**
         * Creates a transform from a rotation only.
         *
         * @param rotation The rotation
         * @return A new transform
         */
        public fun fromRotation(rotation: Quaternion): Transform = Transform(rotation = rotation)

        /**
         * Creates a transform from Euler angles.
         *
         * @param euler The Euler angles (pitch, yaw, roll) in radians
         * @return A new transform
         */
        public fun fromEuler(euler: Vec3): Transform = Transform(rotation = Quaternion.fromEuler(euler))

        /**
         * Creates a transform from Euler angles.
         *
         * @param pitch Rotation around X-axis in radians
         * @param yaw Rotation around Y-axis in radians
         * @param roll Rotation around Z-axis in radians
         * @return A new transform
         */
        public fun fromEuler(
            pitch: Float,
            yaw: Float,
            roll: Float,
        ): Transform = Transform(rotation = Quaternion.fromEuler(pitch, yaw, roll))

        /**
         * Creates a transform looking at a target from a position.
         *
         * @param position The position
         * @param target The target to look at
         * @param up The up direction (defaults to Vec3.UP)
         * @return A new transform
         */
        public fun lookingAt(
            position: Vec3,
            target: Vec3,
            up: Vec3 = Vec3.UP,
        ): Transform {
            val direction = (target - position).normalized()
            if (direction.lengthSquared() < 0.000001f) {
                return Transform(position = position)
            }
            val rotation = Quaternion.lookRotation(direction, up)
            return Transform(position = position, rotation = rotation)
        }
    }
}
