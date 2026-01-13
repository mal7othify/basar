package com.spatialkit.core.types

/**
 * Represents a color with red, green, blue, and alpha (opacity) components.
 *
 * All components are in the range [0.0, 1.0] where:
 * - 0.0 represents no intensity
 * - 1.0 represents full intensity
 *
 * @property r The red component (0.0 to 1.0)
 * @property g The green component (0.0 to 1.0)
 * @property b The blue component (0.0 to 1.0)
 * @property a The alpha (opacity) component (0.0 to 1.0), defaults to 1.0 (fully opaque)
 */
data class Color(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1f,
) {
    init {
        require(r in 0f..1f) { "Red component must be in range [0.0, 1.0], got $r" }
        require(g in 0f..1f) { "Green component must be in range [0.0, 1.0], got $g" }
        require(b in 0f..1f) { "Blue component must be in range [0.0, 1.0], got $b" }
        require(a in 0f..1f) { "Alpha component must be in range [0.0, 1.0], got $a" }
    }

    /**
     * Performs linear interpolation between this color and another color.
     *
     * @param other The target color
     * @param fraction The interpolation factor (0.0 returns this, 1.0 returns other)
     * @return A new interpolated color
     */
    fun lerp(
        other: Color,
        fraction: Float,
    ): Color {
        val t = fraction.coerceIn(0f, 1f)
        return Color(
            r = r + (other.r - r) * t,
            g = g + (other.g - g) * t,
            b = b + (other.b - b) * t,
            a = a + (other.a - a) * t,
        )
    }

    /**
     * Returns a new color with the specified alpha value.
     *
     * @param alpha The new alpha value (0.0 to 1.0)
     * @return A new color with the updated alpha
     */
    fun withAlpha(alpha: Float): Color = copy(a = alpha.coerceIn(0f, 1f))

    /**
     * Returns a new color with the specified red value.
     *
     * @param red The new red value (0.0 to 1.0)
     * @return A new color with the updated red component
     */
    fun withRed(red: Float): Color = copy(r = red.coerceIn(0f, 1f))

    /**
     * Returns a new color with the specified green value.
     *
     * @param green The new green value (0.0 to 1.0)
     * @return A new color with the updated green component
     */
    fun withGreen(green: Float): Color = copy(g = green.coerceIn(0f, 1f))

    /**
     * Returns a new color with the specified blue value.
     *
     * @param blue The new blue value (0.0 to 1.0)
     * @return A new color with the updated blue component
     */
    fun withBlue(blue: Float): Color = copy(b = blue.coerceIn(0f, 1f))

    /**
     * Multiplies this color by a scalar value.
     * Useful for darkening/brightening colors.
     *
     * @param scalar The scalar multiplier
     * @return A new color with scaled RGB components (alpha unchanged)
     */
    operator fun times(scalar: Float): Color =
        Color(
            r = (r * scalar).coerceIn(0f, 1f),
            g = (g * scalar).coerceIn(0f, 1f),
            b = (b * scalar).coerceIn(0f, 1f),
            a = a,
        )

    /**
     * Adds this color to another color (component-wise addition).
     *
     * @param other The color to add
     * @return A new color with summed components
     */
    operator fun plus(other: Color): Color =
        Color(
            r = (r + other.r).coerceIn(0f, 1f),
            g = (g + other.g).coerceIn(0f, 1f),
            b = (b + other.b).coerceIn(0f, 1f),
            a = (a + other.a).coerceIn(0f, 1f),
        )

    /**
     * Converts this color to a hexadecimal string representation.
     *
     * @param includeAlpha Whether to include the alpha channel in the output
     * @return A hex string in format "#RRGGBB" or "#RRGGBBAA"
     */
    private fun Int.toHex2(): String =
        this
            .coerceIn(0, 255)
            .toString(16)
            .padStart(2, '0')
            .uppercase()

    fun toHex(includeAlpha: Boolean = false): String {
        val rInt = (r * 255).toInt()
        val gInt = (g * 255).toInt()
        val bInt = (b * 255).toInt()
        val aInt = (a * 255).toInt()

        return if (includeAlpha) {
            "#${rInt.toHex2()}${gInt.toHex2()}${bInt.toHex2()}${aInt.toHex2()}"
        } else {
            "#${rInt.toHex2()}${gInt.toHex2()}${bInt.toHex2()}"
        }
    }

    /**
     * Converts this color to HSL (Hue, Saturation, Lightness) representation.
     *
     * @return A Triple of (hue: 0-360, saturation: 0-1, lightness: 0-1)
     */
    fun toHSL(): Triple<Float, Float, Float> {
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        // Lightness
        val l = (max + min) / 2f

        if (delta == 0f) {
            // Achromatic (gray)
            return Triple(0f, 0f, l)
        }

        // Saturation
        val s =
            if (l < 0.5f) {
                delta / (max + min)
            } else {
                delta / (2f - max - min)
            }

        // Hue
        val h =
            when (max) {
                r -> ((g - b) / delta + (if (g < b) 6f else 0f)) * 60f
                g -> ((b - r) / delta + 2f) * 60f
                b -> ((r - g) / delta + 4f) * 60f
                else -> 0f
            }

        return Triple(h, s, l)
    }

    /**
     * Converts this color to an integer ARGB representation.
     *
     * @return An integer in ARGB format (0xAARRGGBB)
     */
    fun toArgb(): Int {
        val aInt = (a * 255).toInt().coerceIn(0, 255)
        val rInt = (r * 255).toInt().coerceIn(0, 255)
        val gInt = (g * 255).toInt().coerceIn(0, 255)
        val bInt = (b * 255).toInt().coerceIn(0, 255)
        return (aInt shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
    }

    companion object {
        /**
         * Pure red color (1.0, 0.0, 0.0, 1.0)
         */
        val Red = Color(1f, 0f, 0f, 1f)

        /**
         * Pure green color (0.0, 1.0, 0.0, 1.0)
         */
        val Green = Color(0f, 1f, 0f, 1f)

        /**
         * Pure blue color (0.0, 0.0, 1.0, 1.0)
         */
        val Blue = Color(0f, 0f, 1f, 1f)

        /**
         * White color (1.0, 1.0, 1.0, 1.0)
         */
        val White = Color(1f, 1f, 1f, 1f)

        /**
         * Black color (0.0, 0.0, 0.0, 1.0)
         */
        val Black = Color(0f, 0f, 0f, 1f)

        /**
         * Transparent color (0.0, 0.0, 0.0, 0.0)
         */
        val Transparent = Color(0f, 0f, 0f, 0f)

        /**
         * Yellow color (1.0, 1.0, 0.0, 1.0)
         */
        val Yellow = Color(1f, 1f, 0f, 1f)

        /**
         * Cyan color (0.0, 1.0, 1.0, 1.0)
         */
        val Cyan = Color(0f, 1f, 1f, 1f)

        /**
         * Magenta color (1.0, 0.0, 1.0, 1.0)
         */
        val Magenta = Color(1f, 0f, 1f, 1f)

        /**
         * Gray color (0.5, 0.5, 0.5, 1.0)
         */
        val Gray = Color(0.5f, 0.5f, 0.5f, 1f)

        /**
         * Dark gray color (0.25, 0.25, 0.25, 1.0)
         */
        val DarkGray = Color(0.25f, 0.25f, 0.25f, 1f)

        /**
         * Light gray color (0.75, 0.75, 0.75, 1.0)
         */
        val LightGray = Color(0.75f, 0.75f, 0.75f, 1f)

        /**
         * Creates a color from a hexadecimal string.
         *
         * Supported formats:
         * - "#RGB" (e.g., "#F00")
         * - "#RRGGBB" (e.g., "#FF0000")
         * - "#RRGGBBAA" (e.g., "#FF0000FF")
         * - "RGB", "RRGGBB", "RRGGBBAA" (without #)
         *
         * @param hex The hexadecimal color string
         * @return A new Color instance
         * @throws IllegalArgumentException if the hex string is invalid
         */
        fun fromHex(hex: String): Color {
            val cleanHex = hex.trim().removePrefix("#")

            return when (cleanHex.length) {
                3 -> {
                    // #RGB format
                    val r = cleanHex.substring(0, 1).repeat(2).toInt(16) / 255f
                    val g = cleanHex.substring(1, 2).repeat(2).toInt(16) / 255f
                    val b = cleanHex.substring(2, 3).repeat(2).toInt(16) / 255f
                    Color(r, g, b)
                }

                6 -> {
                    // #RRGGBB format
                    val r = cleanHex.substring(0, 2).toInt(16) / 255f
                    val g = cleanHex.substring(2, 4).toInt(16) / 255f
                    val b = cleanHex.substring(4, 6).toInt(16) / 255f
                    Color(r, g, b)
                }

                8 -> {
                    // #RRGGBBAA format
                    val r = cleanHex.substring(0, 2).toInt(16) / 255f
                    val g = cleanHex.substring(2, 4).toInt(16) / 255f
                    val b = cleanHex.substring(4, 6).toInt(16) / 255f
                    val a = cleanHex.substring(6, 8).toInt(16) / 255f
                    Color(r, g, b, a)
                }

                else -> {
                    throw IllegalArgumentException("Invalid hex color format: $hex")
                }
            }
        }

        /**
         * Creates a color from HSL (Hue, Saturation, Lightness) values.
         *
         * @param hue The hue value (0.0 to 360.0 degrees)
         * @param saturation The saturation value (0.0 to 1.0)
         * @param lightness The lightness value (0.0 to 1.0)
         * @param alpha The alpha value (0.0 to 1.0), defaults to 1.0
         * @return A new Color instance
         */
        fun fromHSL(
            hue: Float,
            saturation: Float,
            lightness: Float,
            alpha: Float = 1f,
        ): Color {
            val h = (hue % 360f + 360f) % 360f / 360f
            val s = saturation.coerceIn(0f, 1f)
            val l = lightness.coerceIn(0f, 1f)

            if (s == 0f) {
                // Achromatic (gray)
                return Color(l, l, l, alpha)
            }

            val q =
                if (l < 0.5f) {
                    l * (1f + s)
                } else {
                    l + s - l * s
                }
            val p = 2f * l - q

            fun hueToRgb(
                p: Float,
                q: Float,
                t: Float,
            ): Float {
                var tt = t
                if (tt < 0f) tt += 1f
                if (tt > 1f) tt -= 1f
                if (tt < 1f / 6f) return p + (q - p) * 6f * tt
                if (tt < 1f / 2f) return q
                if (tt < 2f / 3f) return p + (q - p) * (2f / 3f - tt) * 6f
                return p
            }

            val r = hueToRgb(p, q, h + 1f / 3f)
            val g = hueToRgb(p, q, h)
            val b = hueToRgb(p, q, h - 1f / 3f)

            return Color(r, g, b, alpha)
        }

        /**
         * Creates a color from integer RGB values (0-255).
         *
         * @param r The red component (0 to 255)
         * @param g The green component (0 to 255)
         * @param b The blue component (0 to 255)
         * @param a The alpha component (0 to 255), defaults to 255
         * @return A new Color instance
         */
        fun fromRGB(
            r: Int,
            g: Int,
            b: Int,
            a: Int = 255,
        ): Color =
            Color(
                r = r.coerceIn(0, 255) / 255f,
                g = g.coerceIn(0, 255) / 255f,
                b = b.coerceIn(0, 255) / 255f,
                a = a.coerceIn(0, 255) / 255f,
            )

        /**
         * Creates a color from an ARGB integer value.
         *
         * @param argb The ARGB value (0xAARRGGBB)
         * @return A new Color instance
         */
        fun fromArgb(argb: Int): Color =
            Color(
                r = ((argb shr 16) and 0xFF) / 255f,
                g = ((argb shr 8) and 0xFF) / 255f,
                b = (argb and 0xFF) / 255f,
                a = ((argb shr 24) and 0xFF) / 255f,
            )
    }
}
