package com.spatialkit.core.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Matrix4Test {
    private fun assertVec3Equals(
        expected: Vec3,
        actual: Vec3,
        delta: Float = 0.0001f,
    ) {
        assertEquals(expected.x, actual.x, delta, "x component mismatch")
        assertEquals(expected.y, actual.y, delta, "y component mismatch")
        assertEquals(expected.z, actual.z, delta, "z component mismatch")
    }

    private fun assertMatrixEquals(
        expected: Matrix4,
        actual: Matrix4,
        delta: Float = 0.0001f,
    ) {
        for (i in 0..15) {
            assertEquals(expected.m[i], actual.m[i], delta, "Matrix element [$i] mismatch")
        }
    }

    @Test
    fun testIdentity() {
        val identity = Matrix4.identity()

        // Identity matrix should not change a vector
        val v = Vec3(1f, 2f, 3f)
        val result = identity * v
        assertVec3Equals(v, result)
    }

    @Test
    fun testTranslation() {
        val translation = Matrix4.translation(5f, 10f, 15f)
        val v = Vec3(0f, 0f, 0f)
        val result = translation * v

        assertVec3Equals(Vec3(5f, 10f, 15f), result)
    }

    @Test
    fun testScale() {
        val scale = Matrix4.scale(2f, 3f, 4f)
        val v = Vec3(1f, 1f, 1f)
        val result = scale * v

        assertVec3Equals(Vec3(2f, 3f, 4f), result)
    }

    @Test
    fun testRotation() {
        // 90 degree rotation around Y axis
        val q = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)
        val rotation = Matrix4.rotation(q)

        val v = Vec3.RIGHT
        val result = rotation * v

        // Right vector rotated 90 degrees around Y should point forward
        assertVec3Equals(Vec3.FORWARD, result, 0.001f)
    }

    @Test
    fun testMatrixMultiplication() {
        val translation = Matrix4.translation(5f, 0f, 0f)
        val scale = Matrix4.scale(2f, 2f, 2f)

        // Apply scale then translation
        val combined = translation * scale

        val v = Vec3(1f, 0f, 0f)
        val result = combined * v

        // Scale by 2, then translate by 5
        assertVec3Equals(Vec3(7f, 0f, 0f), result)
    }

    @Test
    fun testInverse() {
        val translation = Matrix4.translation(5f, 10f, 15f)
        val inverse = translation.inverse()

        val combined = translation * inverse
        assertMatrixEquals(Matrix4.identity(), combined, 0.001f)
    }

    @Test
    fun testTranspose() {
        val m =
            Matrix4(
                floatArrayOf(
                    1f,
                    2f,
                    3f,
                    4f,
                    5f,
                    6f,
                    7f,
                    8f,
                    9f,
                    10f,
                    11f,
                    12f,
                    13f,
                    14f,
                    15f,
                    16f,
                ),
            )

        val transposed = m.transpose()

        // Check a few key elements
        assertEquals(1f, transposed.m[0], 0.0001f)
        assertEquals(5f, transposed.m[1], 0.0001f)
        assertEquals(9f, transposed.m[2], 0.0001f)
        assertEquals(13f, transposed.m[3], 0.0001f)
    }

    @Test
    fun testTRS() {
        val position = Vec3(1f, 2f, 3f)
        val rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 4f)
        val scale = Vec3(2f, 2f, 2f)

        val trs = Matrix4.trs(position, rotation, scale)

        // Test that a point at origin is transformed correctly
        val v = Vec3.ZERO
        val result = trs * v

        // Only translation should apply to origin point
        assertVec3Equals(position, result, 0.001f)
    }

    @Test
    fun testGetTranslation() {
        val translation = Vec3(5f, 10f, 15f)
        val matrix = Matrix4.translation(translation)

        val result = matrix.getTranslation()
        assertVec3Equals(translation, result)
    }

    @Test
    fun testGetScale() {
        val scale = Vec3(2f, 3f, 4f)
        val matrix = Matrix4.scale(scale)

        val result = matrix.getScale()
        assertVec3Equals(scale, result)
    }

    @Test
    fun testPerspective() {
        val fov = PI.toFloat() / 4f // 45 degrees
        val aspectRatio = 16f / 9f
        val near = 0.1f
        val far = 100f

        val perspective = Matrix4.perspective(fov, aspectRatio, near, far)

        // Just verify it's created without errors and is not identity
        assertTrue(perspective.m[0] != 1f || perspective.m[5] != 1f)
    }

    @Test
    fun testOrthographic() {
        val ortho = Matrix4.orthographic(-10f, 10f, -10f, 10f, 0.1f, 100f)

        // Just verify it's created without errors
        assertTrue(ortho.m[0] != 0f)
    }

    @Test
    fun testLookAt() {
        val eye = Vec3(0f, 0f, 5f)
        val target = Vec3(0f, 0f, 0f)
        val up = Vec3.UP

        val lookAt = Matrix4.lookAt(eye, target, up)

        // A point at the origin should be transformed to be in front of the camera
        val point = Vec3(0f, 0f, 0f)
        val result = lookAt * point

        // The point should now be along the negative Z axis (camera looks down -Z)
        assertTrue(result.z < 0f, "Point should be in front of camera")
    }

    @Test
    fun testVec4Transformation() {
        val translation = Matrix4.translation(5f, 10f, 15f)
        val v = Vec4(1f, 2f, 3f, 1f)
        val result = translation * v

        assertEquals(6f, result.x, 0.0001f)
        assertEquals(12f, result.y, 0.0001f)
        assertEquals(18f, result.z, 0.0001f)
        assertEquals(1f, result.w, 0.0001f)
    }

    @Test
    fun testConstructorCopiesInputArray() {
        val values =
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            )
        val matrix = Matrix4(values)

        // Mutating the source array must not affect the matrix
        values[12] = 99f
        assertEquals(0f, matrix[12], 0.0001f)

        // And the array returned by toFloatArray is a copy too
        val out = matrix.toFloatArray()
        out[0] = 42f
        assertEquals(1f, matrix[0], 0.0001f)
    }

    @Test
    fun testIndexedAccess() {
        val translation = Matrix4.translation(5f, 10f, 15f)
        assertEquals(5f, translation[12], 0.0001f)
        assertEquals(5f, translation[0, 3], 0.0001f)
        assertEquals(1f, translation[0, 0], 0.0001f)
    }
}
