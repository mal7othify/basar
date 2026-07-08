package com.spatialkit.engine.preview

import com.spatialkit.engine.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OrbitCameraModelTest {
    private val epsilon = 1e-4f

    private fun assertVec3Equals(
        expected: Vec3,
        actual: Vec3,
        message: String? = null,
    ) {
        assertTrue(
            abs(expected.x - actual.x) < epsilon &&
                abs(expected.y - actual.y) < epsilon &&
                abs(expected.z - actual.z) < epsilon,
            "${message ?: "vectors differ"}: expected $expected, got $actual",
        )
    }

    @Test
    fun defaultPoseSitsOnPositiveZSideOfTarget() {
        val model = OrbitCameraModel(target = Vec3(0f, 0f, -1f), pitch = 0f, distance = 2f)
        assertVec3Equals(Vec3(0f, 0f, 1f), model.position)
    }

    @Test
    fun cameraAlwaysLooksAtTarget() {
        // For several orbit states, the camera's forward axis (-Z rotated by
        // the camera rotation) must point from the camera to the target.
        val states =
            listOf(
                Triple(0f, 0f, 2f),
                Triple(1.2f, 0.4f, 3f),
                Triple(-2.5f, -0.8f, 1f),
                Triple(3.9f, 1.1f, 10f),
            )
        states.forEach { (yaw, pitch, distance) ->
            val model =
                OrbitCameraModel(
                    target = Vec3(0.5f, 1f, -2f),
                    yaw = yaw,
                    pitch = pitch,
                    distance = distance,
                )
            val forward = model.rotation * Vec3(0f, 0f, -1f)
            val toTarget = (model.target - model.position).normalized()
            assertVec3Equals(toTarget, forward, "yaw=$yaw pitch=$pitch")
        }
    }

    @Test
    fun positivePitchRaisesCameraAboveTarget() {
        val model = OrbitCameraModel(target = Vec3.ZERO, pitch = 0.5f, distance = 2f)
        assertTrue(model.position.y > 0f)
    }

    @Test
    fun yawOrbitsAroundTarget() {
        val model = OrbitCameraModel(target = Vec3.ZERO, pitch = 0f, distance = 2f)
        model.orbit(deltaYaw = (PI / 2).toFloat(), deltaPitch = 0f)
        // yaw = 90°: camera moves to the +X side, still 2m away
        assertVec3Equals(Vec3(2f, 0f, 0f), model.position)
    }

    @Test
    fun pitchIsClampedShortOfThePoles() {
        val model = OrbitCameraModel(target = Vec3.ZERO)
        model.orbit(deltaYaw = 0f, deltaPitch = 10f)
        assertTrue(model.pitch < (PI / 2).toFloat())
        model.orbit(deltaYaw = 0f, deltaPitch = -20f)
        assertTrue(model.pitch > (-PI / 2).toFloat())
    }

    @Test
    fun zoomIsClampedToDistanceLimits() {
        val model =
            OrbitCameraModel(target = Vec3.ZERO, distance = 2f)
        model.zoomBy(1000f)
        assertTrue(model.distance <= 50f)
        model.zoomBy(0.000001f)
        assertTrue(model.distance >= 0.2f)
    }

    @Test
    fun zoomIgnoresNonPositiveFactors() {
        val model = OrbitCameraModel(target = Vec3.ZERO, distance = 2f)
        model.zoomBy(0f)
        model.zoomBy(-1f)
        assertTrue(abs(model.distance - 2f) < epsilon)
    }

    @Test
    fun panMovesTargetInCameraPlane() {
        // Default orbit (yaw=0, pitch=0): camera right is world +X, camera up
        // is world +Y.
        val model = OrbitCameraModel(target = Vec3.ZERO, pitch = 0f, distance = 2f)
        model.pan(rightUnits = 1f, upUnits = 0.5f)
        assertVec3Equals(Vec3(1f, 0.5f, 0f), model.target)
        // Camera follows the target: same offset applies to the position
        assertVec3Equals(Vec3(1f, 0.5f, 2f), model.position)
    }

    @Test
    fun panFollowsYawedCameraAxes() {
        // yaw = 90°: camera sits at +X looking toward -X, so camera-right is
        // world -Z.
        val model =
            OrbitCameraModel(target = Vec3.ZERO, yaw = (PI / 2).toFloat(), pitch = 0f, distance = 2f)
        model.pan(rightUnits = 1f, upUnits = 0f)
        assertVec3Equals(Vec3(0f, 0f, -1f), model.target)
    }
}
