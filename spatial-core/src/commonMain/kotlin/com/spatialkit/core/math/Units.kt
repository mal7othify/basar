package com.spatialkit.core.math

import kotlin.math.PI

/**
 * Converts this number to meters.
 * Since the internal unit system uses meters, this is a no-op conversion.
 *
 * @return This number as a Float representing meters
 */
val Number.meters: Float
    get() = this.toFloat()

/**
 * Alias for meters.
 */
val Number.m: Float
    get() = meters

/**
 * Converts this number from centimeters to meters.
 *
 * @return This number converted to meters
 */
val Number.centimeters: Float
    get() = this.toFloat() / 100f

/**
 * Alias for centimeters.
 */
val Number.cm: Float
    get() = centimeters

/**
 * Converts this number from millimeters to meters.
 *
 * @return This number converted to meters
 */
val Number.millimeters: Float
    get() = this.toFloat() / 1000f

/**
 * Alias for millimeters.
 */
val Number.mm: Float
    get() = millimeters

/**
 * Converts this number from kilometers to meters.
 *
 * @return This number converted to meters
 */
val Number.kilometers: Float
    get() = this.toFloat() * 1000f

/**
 * Alias for kilometers.
 */
val Number.km: Float
    get() = kilometers

/**
 * Converts this number from inches to meters.
 *
 * @return This number converted to meters
 */
val Number.inches: Float
    get() = this.toFloat() * 0.0254f

/**
 * Alias for inches.
 */
val Number.inch: Float
    get() = inches

/**
 * Converts this number from feet to meters.
 *
 * @return This number converted to meters
 */
val Number.feet: Float
    get() = this.toFloat() * 0.3048f

/**
 * Alias for feet.
 */
val Number.ft: Float
    get() = feet

// ===== Angle Units =====

/**
 * Converts this number from degrees to radians.
 * Most math functions in the library expect angles in radians.
 *
 * @return This number converted to radians
 */
val Number.degrees: Float
    get() = this.toFloat() * (PI.toFloat() / 180f)

/**
 * Alias for degrees.
 */
val Number.deg: Float
    get() = degrees

/**
 * Represents this number as radians (no conversion).
 * This is primarily for code readability.
 *
 * @return This number as a Float representing radians
 */
val Number.radians: Float
    get() = this.toFloat()

/**
 * Alias for radians.
 */
val Number.rad: Float
    get() = radians

// ===== Time Units =====

/**
 * Converts this number to seconds.
 * Since the internal unit system uses seconds, this is a no-op conversion.
 *
 * @return This number as a Float representing seconds
 */
val Number.seconds: Float
    get() = this.toFloat()

/**
 * Alias for seconds.
 */
val Number.s: Float
    get() = seconds

/**
 * Converts this number from milliseconds to seconds.
 *
 * @return This number converted to seconds
 */
val Number.milliseconds: Float
    get() = this.toFloat() / 1000f

/**
 * Alias for milliseconds.
 */
val Number.ms: Float
    get() = milliseconds

/**
 * Converts this number from minutes to seconds.
 *
 * @return This number converted to seconds
 */
val Number.minutes: Float
    get() = this.toFloat() * 60f

/**
 * Alias for minutes.
 */
val Number.min: Float
    get() = minutes

// ===== Utility Functions =====

/**
 * Converts radians to degrees.
 *
 * @param radians The angle in radians
 * @return The angle in degrees
 */
fun radiansToDegrees(radians: Float): Float = radians * (180f / PI.toFloat())

/**
 * Converts degrees to radians.
 *
 * @param degrees The angle in degrees
 * @return The angle in radians
 */
fun degreesToRadians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)

/**
 * Converts meters to centimeters.
 *
 * @param meters The distance in meters
 * @return The distance in centimeters
 */
fun metersToCentimeters(meters: Float): Float = meters * 100f

/**
 * Converts centimeters to meters.
 *
 * @param centimeters The distance in centimeters
 * @return The distance in meters
 */
fun centimetersToMeters(centimeters: Float): Float = centimeters / 100f

/**
 * Converts meters to feet.
 *
 * @param meters The distance in meters
 * @return The distance in feet
 */
fun metersToFeet(meters: Float): Float = meters / 0.3048f

/**
 * Converts feet to meters.
 *
 * @param feet The distance in feet
 * @return The distance in meters
 */
fun feetToMeters(feet: Float): Float = feet * 0.3048f

/**
 * Clamps a value between a minimum and maximum.
 *
 * @param value The value to clamp
 * @param min The minimum value
 * @param max The maximum value
 * @return The clamped value
 */
fun clamp(
    value: Float,
    min: Float,
    max: Float,
): Float =
    when {
        value < min -> min
        value > max -> max
        else -> value
    }

/**
 * Linearly interpolates between two values.
 *
 * @param a The start value
 * @param b The end value
 * @param t The interpolation factor (0.0 returns a, 1.0 returns b)
 * @return The interpolated value
 */
fun lerp(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t

/**
 * Maps a value from one range to another.
 *
 * @param value The value to map
 * @param inMin The minimum of the input range
 * @param inMax The maximum of the input range
 * @param outMin The minimum of the output range
 * @param outMax The maximum of the output range
 * @return The mapped value
 */
fun mapRange(
    value: Float,
    inMin: Float,
    inMax: Float,
    outMin: Float,
    outMax: Float,
): Float {
    val t = (value - inMin) / (inMax - inMin)
    return outMin + (outMax - outMin) * t
}
