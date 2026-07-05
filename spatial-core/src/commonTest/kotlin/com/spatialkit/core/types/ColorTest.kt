package com.spatialkit.core.types

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorTest {
    private fun assertColorEquals(
        expected: Color,
        actual: Color,
        delta: Float = 0.0001f,
    ) {
        assertEquals(expected.r, actual.r, delta, "Red component mismatch")
        assertEquals(expected.g, actual.g, delta, "Green component mismatch")
        assertEquals(expected.b, actual.b, delta, "Blue component mismatch")
        assertEquals(expected.a, actual.a, delta, "Alpha component mismatch")
    }

    @Test
    fun testColorCreation() {
        val color = Color(0.5f, 0.6f, 0.7f, 0.8f)
        assertEquals(0.5f, color.r)
        assertEquals(0.6f, color.g)
        assertEquals(0.7f, color.b)
        assertEquals(0.8f, color.a)
    }

    @Test
    fun testColorConstants() {
        assertColorEquals(Color(1f, 0f, 0f, 1f), Color.Red)
        assertColorEquals(Color(0f, 1f, 0f, 1f), Color.Green)
        assertColorEquals(Color(0f, 0f, 1f, 1f), Color.Blue)
        assertColorEquals(Color(1f, 1f, 1f, 1f), Color.White)
        assertColorEquals(Color(0f, 0f, 0f, 1f), Color.Black)
        assertColorEquals(Color(0f, 0f, 0f, 0f), Color.Transparent)
    }

    @Test
    fun testColorLerp() {
        val color1 = Color.Black
        val color2 = Color.White

        val mid = color1.lerp(color2, 0.5f)
        assertColorEquals(Color(0.5f, 0.5f, 0.5f, 1f), mid)

        val start = color1.lerp(color2, 0f)
        assertColorEquals(color1, start)

        val end = color1.lerp(color2, 1f)
        assertColorEquals(color2, end)
    }

    @Test
    fun testColorWithAlpha() {
        val color = Color.Red.withAlpha(0.5f)
        assertColorEquals(Color(1f, 0f, 0f, 0.5f), color)
    }

    @Test
    fun testColorMultiplication() {
        val color = Color(0.5f, 0.6f, 0.7f, 1f)
        val scaled = color * 2f

        assertColorEquals(Color(1f, 1f, 1f, 1f), scaled)
    }

    @Test
    fun testColorAddition() {
        val color1 = Color(0.3f, 0.4f, 0.5f, 0.5f)
        val color2 = Color(0.2f, 0.3f, 0.4f, 0.3f)
        val sum = color1 + color2

        assertColorEquals(Color(0.5f, 0.7f, 0.9f, 0.8f), sum)
    }

    @Test
    fun testFromHex() {
        // #RRGGBB format
        val color1 = Color.fromHex("#FF0000")
        assertColorEquals(Color.Red, color1)

        // RRGGBB format (without #)
        val color2 = Color.fromHex("00FF00")
        assertColorEquals(Color.Green, color2)

        // #RGB format
        val color3 = Color.fromHex("#F00")
        assertColorEquals(Color.Red, color3)

        // #RRGGBBAA format
        val color4 = Color.fromHex("#FF000080")
        assertColorEquals(Color(1f, 0f, 0f, 0.5f), color4, 0.01f)
    }

    @Test
    fun testToHex() {
        assertEquals("#FF0000", Color.Red.toHex())
        assertEquals("#00FF00", Color.Green.toHex())
        assertEquals("#0000FF", Color.Blue.toHex())
        assertEquals("#FFFFFF", Color.White.toHex())
        assertEquals("#000000", Color.Black.toHex())
    }

    @Test
    fun testFromRGB() {
        val color = Color.fromRGB(255, 128, 0)
        assertColorEquals(Color(1f, 0.5f, 0f, 1f), color, 0.01f)
    }

    @Test
    fun testToArgb() {
        val red = Color.Red.toArgb()
        assertEquals(0xFFFF0000.toInt(), red)

        val semiTransparentRed = Color(1f, 0f, 0f, 0.5f).toArgb()
        assertEquals(0x7FFF0000, semiTransparentRed)
    }

    @Test
    fun testFromArgb() {
        val color = Color.fromArgb(0xFFFF0000.toInt())
        assertColorEquals(Color.Red, color, 0.01f)
    }

    @Test
    fun testHSLConversion() {
        // Test pure red
        val red = Color.Red
        val (h, s, l) = red.toHSL()
        assertEquals(0f, h, 1f)
        assertEquals(1f, s, 0.01f)
        assertEquals(0.5f, l, 0.01f)

        // Convert back
        val redFromHSL = Color.fromHSL(h, s, l)
        assertColorEquals(red, redFromHSL, 0.01f)
    }

    @Test
    fun testFromHSL() {
        // Red: hue=0, full saturation, 50% lightness
        val red = Color.fromHSL(0f, 1f, 0.5f)
        assertColorEquals(Color.Red, red, 0.01f)

        // Green: hue=120
        val green = Color.fromHSL(120f, 1f, 0.5f)
        assertColorEquals(Color.Green, green, 0.01f)

        // Blue: hue=240
        val blue = Color.fromHSL(240f, 1f, 0.5f)
        assertColorEquals(Color.Blue, blue, 0.01f)

        // Gray: saturation=0
        val gray = Color.fromHSL(0f, 0f, 0.5f)
        assertColorEquals(Color.Gray, gray, 0.01f)
    }
}
