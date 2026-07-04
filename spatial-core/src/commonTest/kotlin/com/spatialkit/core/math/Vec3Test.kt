package com.spatialkit.core.math

import kotlin.test.Test
import kotlin.test.assertEquals

class Vec3Test {
    private fun assertVec3Equals(
        expected: Vec3,
        actual: Vec3,
        delta: Float = 0.0001f,
    ) {
        assertEquals(expected.x, actual.x, delta, "x component mismatch")
        assertEquals(expected.y, actual.y, delta, "y component mismatch")
        assertEquals(expected.z, actual.z, delta, "z component mismatch")
    }

    @Test
    fun testVectorAddition() {
        val v1 = Vec3(1f, 2f, 3f)
        val v2 = Vec3(4f, 5f, 6f)
        val result = v1 + v2
        assertVec3Equals(Vec3(5f, 7f, 9f), result)
    }

    @Test
    fun testVectorSubtraction() {
        val v1 = Vec3(4f, 5f, 6f)
        val v2 = Vec3(1f, 2f, 3f)
        val result = v1 - v2
        assertVec3Equals(Vec3(3f, 3f, 3f), result)
    }

    @Test
    fun testScalarMultiplication() {
        val v = Vec3(1f, 2f, 3f)
        val result = v * 2f
        assertVec3Equals(Vec3(2f, 4f, 6f), result)
    }

    @Test
    fun testScalarDivision() {
        val v = Vec3(2f, 4f, 6f)
        val result = v / 2f
        assertVec3Equals(Vec3(1f, 2f, 3f), result)
    }

    @Test
    fun testDotProduct() {
        val v1 = Vec3(1f, 0f, 0f)
        val v2 = Vec3(0f, 1f, 0f)
        assertEquals(0f, v1.dot(v2), 0.0001f, "Perpendicular vectors should have dot product of 0")

        val v3 = Vec3(1f, 2f, 3f)
        val v4 = Vec3(4f, 5f, 6f)
        assertEquals(32f, v3.dot(v4), 0.0001f)
    }

    @Test
    fun testCrossProduct() {
        val v1 = Vec3(1f, 0f, 0f)
        val v2 = Vec3(0f, 1f, 0f)
        val result = v1.cross(v2)
        assertVec3Equals(Vec3(0f, 0f, 1f), result)

        // Cross product is anti-commutative
        val result2 = v2.cross(v1)
        assertVec3Equals(Vec3(0f, 0f, -1f), result2)
    }

    @Test
    fun testLength() {
        val v = Vec3(3f, 4f, 0f)
        assertEquals(5f, v.length(), 0.0001f)

        val v2 = Vec3(1f, 1f, 1f)
        assertEquals(1.732f, v2.length(), 0.001f)
    }

    @Test
    fun testNormalized() {
        val v = Vec3(3f, 4f, 0f)
        val normalized = v.normalized()
        assertEquals(1f, normalized.length(), 0.0001f, "Normalized vector should have length 1")
        assertVec3Equals(Vec3(0.6f, 0.8f, 0f), normalized)
    }

    @Test
    fun testLerp() {
        val v1 = Vec3(0f, 0f, 0f)
        val v2 = Vec3(10f, 10f, 10f)

        val half = v1.lerp(v2, 0.5f)
        assertVec3Equals(Vec3(5f, 5f, 5f), half)

        val start = v1.lerp(v2, 0f)
        assertVec3Equals(v1, start)

        val end = v1.lerp(v2, 1f)
        assertVec3Equals(v2, end)
    }

    @Test
    fun testDistance() {
        val v1 = Vec3(0f, 0f, 0f)
        val v2 = Vec3(3f, 4f, 0f)
        assertEquals(5f, v1.distance(v2), 0.0001f)
    }

    @Test
    fun testConstants() {
        assertVec3Equals(Vec3(0f, 0f, 0f), Vec3.ZERO)
        assertVec3Equals(Vec3(1f, 1f, 1f), Vec3.ONE)
        assertVec3Equals(Vec3(0f, 1f, 0f), Vec3.UP)
        assertVec3Equals(Vec3(0f, -1f, 0f), Vec3.DOWN)
        assertVec3Equals(Vec3(0f, 0f, -1f), Vec3.FORWARD)
        assertVec3Equals(Vec3(0f, 0f, 1f), Vec3.BACK)
        assertVec3Equals(Vec3(1f, 0f, 0f), Vec3.RIGHT)
        assertVec3Equals(Vec3(-1f, 0f, 0f), Vec3.LEFT)
    }

    @Test
    fun testUnaryMinus() {
        val v = Vec3(1f, -2f, 3f)
        val negated = -v
        assertVec3Equals(Vec3(-1f, 2f, -3f), negated)
    }

    @Test
    fun testExtensionOperators() {
        val v = Vec3(1f, 2f, 3f)

        // Float * Vec3
        val result1 = 2f * v
        assertVec3Equals(Vec3(2f, 4f, 6f), result1)

        // Int * Vec3
        val result2 = 2 * v
        assertVec3Equals(Vec3(2f, 4f, 6f), result2)
    }
}
