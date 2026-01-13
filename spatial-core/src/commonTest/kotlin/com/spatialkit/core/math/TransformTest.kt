package com.spatialkit.core.math

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class TransformTest {
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
        val transform = Transform.IDENTITY

        assertVec3Equals(Vec3.ZERO, transform.position)
        assertEquals(Quaternion.IDENTITY, transform.rotation)
        assertVec3Equals(Vec3.ONE, transform.scale)
    }

    @Test
    fun testLocalToWorld() {
        val transform =
            Transform(
                position = Vec3(10f, 0f, 0f),
                rotation = Quaternion.IDENTITY,
                scale = Vec3(2f, 2f, 2f),
            )

        val localPoint = Vec3(1f, 0f, 0f)
        val worldPoint = transform.localToWorld(localPoint)

        // Scale by 2, then translate by 10
        assertVec3Equals(Vec3(12f, 0f, 0f), worldPoint)
    }

    @Test
    fun testWorldToLocal() {
        val transform =
            Transform(
                position = Vec3(10f, 0f, 0f),
                rotation = Quaternion.IDENTITY,
                scale = Vec3(2f, 2f, 2f),
            )

        val worldPoint = Vec3(12f, 0f, 0f)
        val localPoint = transform.worldToLocal(worldPoint)

        // Inverse: translate by -10, then scale by 0.5
        assertVec3Equals(Vec3(1f, 0f, 0f), localPoint, 0.001f)
    }

    @Test
    fun testLocalToWorldWithRotation() {
        val transform =
            Transform(
                position = Vec3.ZERO,
                rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f),
                scale = Vec3.ONE,
            )

        val localPoint = Vec3.RIGHT
        val worldPoint = transform.localToWorld(localPoint)

        // Rotate right vector 90 degrees around Y should point forward
        assertVec3Equals(Vec3.FORWARD, worldPoint, 0.001f)
    }

    @Test
    fun testForwardRightUp() {
        val transform =
            Transform(
                rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f),
            )

        // After 90 degree rotation around Y:
        // - Forward should point left
        // - Right should point forward
        // - Up should remain up
        assertVec3Equals(Vec3.LEFT, transform.forward(), 0.001f)
        assertVec3Equals(Vec3.FORWARD, transform.right(), 0.001f)
        assertVec3Equals(Vec3.UP, transform.up(), 0.001f)
    }

    @Test
    fun testCombine() {
        val parent =
            Transform(
                position = Vec3(5f, 0f, 0f),
                rotation = Quaternion.IDENTITY,
                scale = Vec3(2f, 2f, 2f),
            )

        val child =
            Transform(
                position = Vec3(1f, 0f, 0f),
                rotation = Quaternion.IDENTITY,
                scale = Vec3(1f, 1f, 1f),
            )

        val combined = parent.combine(child)

        // Child position (1, 0, 0) scaled by parent (2) then translated by parent (5)
        assertVec3Equals(Vec3(7f, 0f, 0f), combined.position)
        assertVec3Equals(Vec3(2f, 2f, 2f), combined.scale)
    }

    @Test
    fun testTranslate() {
        val transform = Transform(position = Vec3(1f, 2f, 3f))
        val translated = transform.translate(Vec3(4f, 5f, 6f))

        assertVec3Equals(Vec3(5f, 7f, 9f), translated.position)
    }

    @Test
    fun testRotate() {
        val transform = Transform.IDENTITY
        val rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)
        val rotated = transform.rotate(rotation)

        val forward = rotated.forward()
        assertVec3Equals(Vec3.LEFT, forward, 0.001f)
    }

    @Test
    fun testScaleBy() {
        val transform = Transform(scale = Vec3(2f, 2f, 2f))
        val scaled = transform.scaleBy(Vec3(3f, 3f, 3f))

        assertVec3Equals(Vec3(6f, 6f, 6f), scaled.scale)
    }

    @Test
    fun testLookAt() {
        val transform = Transform(position = Vec3(0f, 0f, 5f))
        val target = Vec3(0f, 0f, 0f)

        val looking = transform.lookAt(target)

        // From (0,0,5) looking at (0,0,0) should face negative Z (FORWARD)
        val forward = looking.forward()
        assertVec3Equals(Vec3.FORWARD, forward, 0.001f)
    }

    @Test
    fun testLerp() {
        val t1 = Transform(position = Vec3(0f, 0f, 0f))
        val t2 = Transform(position = Vec3(10f, 10f, 10f))

        val halfway = t1.lerp(t2, 0.5f)
        assertVec3Equals(Vec3(5f, 5f, 5f), halfway.position)
    }

    @Test
    fun testInverse() {
        val transform =
            Transform(
                position = Vec3(5f, 10f, 15f),
                rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 4f),
                scale = Vec3(2f, 2f, 2f),
            )

        val inverse = transform.inverse()
        val combined = transform.combine(inverse)

        // Combined should be approximately identity (some floating point error expected)
        assertVec3Equals(Vec3.ZERO, combined.position, 0.01f)
    }

    @Test
    fun testToMatrix() {
        val transform =
            Transform(
                position = Vec3(5f, 10f, 15f),
                rotation = Quaternion.IDENTITY,
                scale = Vec3(2f, 2f, 2f),
            )

        val matrix = transform.toMatrix()
        val point = Vec3.ZERO
        val result = matrix * point

        assertVec3Equals(transform.position, result)
    }

    @Test
    fun testFromPosition() {
        val position = Vec3(1f, 2f, 3f)
        val transform = Transform.fromPosition(position)

        assertVec3Equals(position, transform.position)
        assertEquals(Quaternion.IDENTITY, transform.rotation)
        assertVec3Equals(Vec3.ONE, transform.scale)
    }

    @Test
    fun testFromEuler() {
        val euler = Vec3(0f, PI.toFloat() / 2f, 0f)
        val transform = Transform.fromEuler(euler)

        val forward = transform.forward()
        assertVec3Equals(Vec3.LEFT, forward, 0.001f)
    }

    @Test
    fun testLookingAt() {
        val position = Vec3(0f, 0f, 5f)
        val target = Vec3(0f, 0f, 0f)

        val transform = Transform.lookingAt(position, target)

        assertVec3Equals(position, transform.position)

        // From (0,0,5) looking at (0,0,0) should face negative Z (FORWARD)
        val forward = transform.forward()
        assertVec3Equals(Vec3.FORWARD, forward, 0.001f)
    }

    @Test
    fun testLocalToWorldDirection() {
        val transform =
            Transform(
                rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f),
            )

        val localDir = Vec3.RIGHT
        val worldDir = transform.localToWorldDirection(localDir)

        // Direction should not be affected by position or scale, only rotation
        assertVec3Equals(Vec3.FORWARD, worldDir, 0.001f)
    }

    @Test
    fun testWorldToLocalDirection() {
        val transform =
            Transform(
                rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f),
            )

        val worldDir = Vec3.FORWARD
        val localDir = transform.worldToLocalDirection(worldDir)

        // Inverse of rotating RIGHT to FORWARD should take FORWARD back to RIGHT
        assertVec3Equals(Vec3.RIGHT, localDir, 0.001f)
    }
}
