package com.spatialkit.dsl

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import kotlin.math.PI

/**
 * Extension functions for convenient DSL syntax.
 *
 * These provide Compose-like helpers for common operations.
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
// Unit Conversions
// ============================================================================

/**
 * Converts meters to meters (identity, for readability).
 *
 * Usage: `size = 1.5.meters`
 */
public val Float.meters: Float
    get() = this

/**
 * Converts meters to meters (identity, for readability).
 */
public val Int.meters: Float
    get() = this.toFloat()

/**
 * Converts centimeters to meters.
 *
 * Usage: `size = 10.cm`
 */
public val Float.cm: Float
    get() = this / 100f

/**
 * Converts centimeters to meters.
 */
public val Int.cm: Float
    get() = this.toFloat() / 100f

/**
 * Converts millimeters to meters.
 *
 * Usage: `size = 100.mm`
 */
public val Float.mm: Float
    get() = this / 1000f

/**
 * Converts millimeters to meters.
 */
public val Int.mm: Float
    get() = this.toFloat() / 1000f

// ============================================================================
// Angle Conversions
// ============================================================================

/**
 * Converts degrees to radians.
 *
 * Usage: `rotation = Quaternion.fromEuler(0f, 45.degrees, 0f)`
 */
public val Float.degrees: Float
    get() = this * PI.toFloat() / 180f

/**
 * Converts degrees to radians.
 */
public val Int.degrees: Float
    get() = this.toFloat() * PI.toFloat() / 180f

/**
 * Converts radians to radians (identity, for readability).
 *
 * Usage: `rotation = Quaternion.fromEuler(0f, 1.57.radians, 0f)`
 */
public val Float.radians: Float
    get() = this

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
public fun rgb(hex: Int): com.spatialkit.core.types.Color {
    val r = ((hex shr 16) and 0xFF) / 255f
    val g = ((hex shr 8) and 0xFF) / 255f
    val b = (hex and 0xFF) / 255f
    return com.spatialkit.core.types.Color(r, g, b, 1f)
}

/**
 * Creates an RGBA color from hex value.
 *
 * Usage: `color = rgba(0xFF0000FF)` for red with full opacity
 */
public fun rgba(hex: Int): com.spatialkit.core.types.Color {
    val r = ((hex shr 24) and 0xFF) / 255f
    val g = ((hex shr 16) and 0xFF) / 255f
    val b = ((hex shr 8) and 0xFF) / 255f
    val a = (hex and 0xFF) / 255f
    return com.spatialkit.core.types.Color(r, g, b, a)
}
