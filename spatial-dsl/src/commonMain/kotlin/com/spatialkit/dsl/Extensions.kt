package com.spatialkit.dsl

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.math.degrees
import com.spatialkit.core.types.Color
import com.spatialkit.render.AnimationState
import com.spatialkit.scene.AnimatedModelNode

/*
 * Extension functions for convenient DSL syntax.
 *
 * Unit conversions (`10.cm`, `1.5.meters`, `45.degrees`, ...) live in
 * `com.spatialkit.core.math` (see `Units.kt`) and work on any [Number];
 * import them from there. This file only adds DSL-specific constructors.
 */

// ============================================================================
// Vector Construction Helpers
// ============================================================================

/**
 * Creates a Vec3 with convenient syntax.
 *
 * Usage: `position = vec3(0f, 1.5f, -1f)`
 */
public fun vec3(
    x: Float,
    y: Float,
    z: Float,
): Vec3 = Vec3(x, y, z)

/**
 * Alias for vec3, more semantic for positions.
 *
 * Usage: `position = position(0f, 1.5f, -1f)`
 */
public fun position(
    x: Float,
    y: Float,
    z: Float,
): Vec3 = Vec3(x, y, z)

// ============================================================================
// Rotation Helpers
// ============================================================================

/**
 * Creates a rotation from Euler angles in degrees.
 *
 * Usage: `rotation = eulerDegrees(0f, 45f, 0f)`
 */
public fun eulerDegrees(
    pitch: Float,
    yaw: Float,
    roll: Float,
): Quaternion = Quaternion.fromEuler(pitch.degrees, yaw.degrees, roll.degrees)

/**
 * Creates a rotation from Euler angles in degrees (with Int support).
 */
public fun eulerDegrees(
    pitch: Int,
    yaw: Int,
    roll: Int,
): Quaternion = Quaternion.fromEuler(pitch.degrees, yaw.degrees, roll.degrees)

/**
 * Creates a rotation from Euler angles in radians.
 *
 * Usage: `rotation = eulerRadians(0f, 1.57f, 0f)`
 */
public fun eulerRadians(
    pitch: Float,
    yaw: Float,
    roll: Float,
): Quaternion = Quaternion.fromEuler(pitch, yaw, roll)

// ============================================================================
// Color Helpers
// ============================================================================

/**
 * Creates an RGB color from hex value.
 *
 * Usage: `color = rgb(0xFF0000)` for red
 */
public fun rgb(hex: Int): Color {
    val r = ((hex shr 16) and 0xFF) / 255f
    val g = ((hex shr 8) and 0xFF) / 255f
    val b = (hex and 0xFF) / 255f
    return Color(r, g, b, 1f)
}

/**
 * Creates an RGBA color from hex value.
 *
 * Usage: `color = rgba(0xFF0000FF)` for red with full opacity
 */
public fun rgba(hex: Int): Color {
    val r = ((hex shr 24) and 0xFF) / 255f
    val g = ((hex shr 16) and 0xFF) / 255f
    val b = ((hex shr 8) and 0xFF) / 255f
    val a = (hex and 0xFF) / 255f
    return Color(r, g, b, a)
}

/**
 * Declarative animation control for a renderer-loaded glTF node (v2 R4).
 *
 * Usage inside `gltfModel(...) { animation(clip = 0, loop = true) }`.
 * The renderer advances the clip on its own clock; call again with new
 * values to switch clips, or set [AnimatedModelNode.animation] to null to stop.
 *
 * @param clip Animation index in the asset (see the model's clip list)
 * @param playing False to pose at [timeOffset] without advancing
 * @param loop Repeat when the clip ends (else hold the last frame)
 * @param speed Playback rate multiplier
 * @param timeOffset Start position within the clip, seconds
 */
public fun AnimatedModelNode.animation(
    clip: Int = 0,
    playing: Boolean = true,
    loop: Boolean = true,
    speed: Float = 1f,
    timeOffset: Float = 0f,
) {
    animation =
        AnimationState(
            clip = clip,
            playing = playing,
            loop = loop,
            speed = speed,
            timeOffset = timeOffset,
        )
}
