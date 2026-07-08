package com.spatialkit.engine.preview

import com.spatialkit.engine.Pose
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.Vec3
import kotlin.math.PI

/**
 * Camera state for the non-XR preview renderer (M10.1): a camera orbiting a
 * target point, controlled by yaw/pitch angles and a distance.
 *
 * Pure math, no renderer types — platform preview engines apply [position]
 * and [rotation] to their native camera each frame, and feed pointer input
 * through [orbit], [zoomBy] and [pan].
 *
 * Conventions (right-handed, Y up, matching the engine contract):
 * - `yaw = 0, pitch = 0` places the camera on the target's +Z side looking
 *   toward -Z, like an AR session's starting view.
 * - Positive [pitch] raises the camera above the target (looking down).
 * - Positive yaw orbits counterclockwise when viewed from above.
 *
 * Not thread-safe; mutate and read from the render thread only.
 *
 * @param target Point the camera orbits and looks at
 * @param yaw Initial yaw angle in radians
 * @param pitch Initial pitch angle in radians (clamped near ±90°)
 * @param distance Initial distance from the target in meters
 * @param minDistance Closest allowed zoom, meters
 * @param maxDistance Farthest allowed zoom, meters
 */
public class OrbitCameraModel(
    public var target: Vec3 = Vec3(0f, 0f, -1f),
    yaw: Float = 0f,
    pitch: Float = DEFAULT_PITCH,
    distance: Float = 2f,
    private val minDistance: Float = 0.2f,
    private val maxDistance: Float = 50f,
) {
    /** Yaw angle in radians (unbounded; wraps naturally). */
    public var yaw: Float = yaw

    /** Pitch angle in radians, clamped just short of the poles. */
    public var pitch: Float = pitch.coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        set(value) {
            field = value.coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
        }

    /** Distance from the target in meters, clamped to [minDistance]..[maxDistance]. */
    public var distance: Float = distance.coerceIn(minDistance, maxDistance)
        set(value) {
            field = value.coerceIn(minDistance, maxDistance)
        }

    /**
     * Camera orientation: looks at [target] from [position], no roll.
     */
    public val rotation: Quaternion
        get() =
            Quaternion.fromAxisAngle(Vec3(0f, 1f, 0f), yaw) *
                Quaternion.fromAxisAngle(Vec3(1f, 0f, 0f), -pitch)

    /**
     * Camera position in world space: [target] plus the orbit offset.
     */
    public val position: Vec3
        get() = target + rotation * Vec3(0f, 0f, distance)

    /** Camera pose ([position] + [rotation]) for FrameData. */
    public fun pose(): Pose = Pose(position = position, rotation = rotation)

    /**
     * Rotates the camera around the target.
     *
     * @param deltaYaw Yaw change in radians
     * @param deltaPitch Pitch change in radians (clamped near the poles)
     */
    public fun orbit(
        deltaYaw: Float,
        deltaPitch: Float,
    ) {
        yaw += deltaYaw
        pitch += deltaPitch
    }

    /**
     * Scales the orbit distance: factors > 1 move away from the target,
     * factors in (0, 1) move closer. Clamped to the distance limits.
     */
    public fun zoomBy(factor: Float) {
        if (factor > 0f) {
            distance *= factor
        }
    }

    /**
     * Moves the target (and camera with it) in the camera's screen plane.
     *
     * @param rightUnits World-space meters along the camera's right axis
     * @param upUnits World-space meters along the camera's up axis
     */
    public fun pan(
        rightUnits: Float,
        upUnits: Float,
    ) {
        val r = rotation
        target = target + r * Vec3(1f, 0f, 0f) * rightUnits + r * Vec3(0f, 1f, 0f) * upUnits
    }

    public companion object {
        /** Default elevation (~20° above the target). */
        public const val DEFAULT_PITCH: Float = 0.35f

        // Just short of straight up/down so the orientation stays unambiguous
        private const val PITCH_LIMIT: Float = (PI / 2.0 - 0.01).toFloat()
    }
}
