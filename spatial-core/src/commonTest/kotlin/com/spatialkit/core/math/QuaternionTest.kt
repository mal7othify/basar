package com.spatialkit.core.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuaternionTest {
    private fun assertQuaternionEquals(
        expected: Quaternion,
        actual: Quaternion,
        delta: Float = 0.0001f,
    ) {
        assertEquals(expected.x, actual.x, delta, "x component mismatch")
        assertEquals(expected.y, actual.y, delta, "y component mismatch")
        assertEquals(expected.z, actual.z, delta, "z component mismatch")
        assertEquals(expected.w, actual.w, delta, "w component mismatch")
    }

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
    fun testIdentity() {
        val q = Quaternion.IDENTITY
        assertEquals(0f, q.x, 0.0001f)
        assertEquals(0f, q.y, 0.0001f)
        assertEquals(0f, q.z, 0.0001f)
        assertEquals(1f, q.w, 0.0001f)
    }

    @Test
    fun testFromAxisAngle() {
        // 90 degree rotation around Y axis
        val q = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)

        // Rotate a vector pointing right, should point forward
        val result = q * Vec3.RIGHT
        assertVec3Equals(Vec3.FORWARD, result, 0.001f)
    }

    @Test
    fun testFromEuler() {
        // 90 degree rotation around Y axis (yaw)
        val q = Quaternion.fromEuler(0f, PI.toFloat() / 2f, 0f)

        // Rotate a vector pointing right, should point forward
        val result = q * Vec3.RIGHT
        assertVec3Equals(Vec3.FORWARD, result, 0.001f)
    }

    @Test
    fun testQuaternionMultiplication() {
        // Two 90-degree rotations around Y should equal 180 degrees
        val q1 = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)
        val q2 = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)
        val combined = q1 * q2

        // Rotate right vector by 180 degrees around Y should give left
        val result = combined * Vec3.RIGHT
        assertVec3Equals(Vec3.LEFT, result, 0.001f)
    }

    @Test
    fun testConjugate() {
        val q = Quaternion(1f, 2f, 3f, 4f)
        val conj = q.conjugate()

        assertEquals(-1f, conj.x, 0.0001f)
        assertEquals(-2f, conj.y, 0.0001f)
        assertEquals(-3f, conj.z, 0.0001f)
        assertEquals(4f, conj.w, 0.0001f)
    }

    @Test
    fun testNormalized() {
        val q = Quaternion(1f, 2f, 3f, 4f)
        val normalized = q.normalized()

        // Check that it's a unit quaternion
        assertEquals(1f, normalized.length(), 0.0001f)
    }

    @Test
    fun testInverse() {
        val q = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 4f)
        val inv = q.inverse()

        // q * q^-1 should equal identity
        val result = q * inv
        assertQuaternionEquals(Quaternion.IDENTITY, result, 0.001f)
    }

    @Test
    fun testVectorRotation() {
        // Rotate a vector 90 degrees around negative Z axis (forward)
        val q = Quaternion.fromAxisAngle(Vec3.FORWARD, PI.toFloat() / 2f)
        val v = Vec3.RIGHT
        val rotated = q * v

        // Right vector rotated 90 degrees around -Z should point down
        assertVec3Equals(Vec3.DOWN, rotated, 0.001f)
    }

    @Test
    fun testSlerp() {
        val q1 = Quaternion.IDENTITY
        val q2 = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)

        // Halfway between no rotation and 90 degrees should be 45 degrees
        val halfway = q1.slerp(q2, 0.5f)

        val v = Vec3.RIGHT
        val rotated = halfway * v

        // Should be rotated 45 degrees, which is about (0.707, 0, 0.707)
        assertEquals(0.707f, rotated.x, 0.01f)
        assertEquals(0f, rotated.y, 0.01f)
        assertEquals(0.707f, rotated.z, 0.01f)
    }

    @Test
    fun testFromToRotation() {
        val from = Vec3.RIGHT
        val to = Vec3.FORWARD

        val q = Quaternion.fromToRotation(from, to)
        val result = q * from

        assertVec3Equals(to, result, 0.001f)
    }

    @Test
    fun testLookRotation() {
        val forward = Vec3.FORWARD
        val q = Quaternion.lookRotation(forward)

        // lookRotation should rotate the default Z-forward (0,0,1) to point in the target direction
        val defaultForward = Vec3(0f, 0f, 1f)
        val rotatedForward = q * defaultForward
        assertVec3Equals(Vec3.FORWARD, rotatedForward, 0.001f)
    }

    @Test
    fun testToEulerAngles() {
        val euler = Vec3(0f, PI.toFloat() / 2f, 0f)
        val q = Quaternion.fromEuler(euler)
        val result = q.toEulerAngles()

        // Should get back approximately the same Euler angles
        assertEquals(euler.x, result.x, 0.01f)
        assertEquals(euler.y, result.y, 0.01f)
        assertEquals(euler.z, result.z, 0.01f)
    }
}
