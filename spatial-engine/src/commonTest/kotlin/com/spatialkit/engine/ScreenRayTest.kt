package com.spatialkit.engine

import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenRayTest {
    /** Symmetric perspective projection with the given tan(halfFov) per axis. */
    private fun projection(
        tanHalfX: Float = 1f,
        tanHalfY: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): FloatArray {
        val m = FloatArray(16)
        m[0] = 1f / tanHalfX
        m[5] = 1f / tanHalfY
        m[8] = offsetX
        m[9] = offsetY
        m[10] = -1.001f
        m[11] = -1f
        m[14] = -0.2f
        return m
    }

    private fun frame(
        pose: Pose = Pose(Vec3.ZERO, Quaternion.IDENTITY),
        proj: FloatArray = projection(),
    ): FrameData =
        FrameData(
            timestamp = 0L,
            deltaTime = 0f,
            cameraPose = pose,
            projectionMatrix = proj,
            viewMatrix = FloatArray(16),
            trackingState = TrackingState.NORMAL,
            detectedPlanes = emptyList(),
            lightEstimate = null,
            inputSources = emptyList(),
            anchors = emptyList(),
        )

    private fun assertVec3(
        expected: Vec3,
        actual: Vec3,
        tolerance: Float = 1e-5f,
    ) {
        assertEquals(expected.x, actual.x, tolerance, "x")
        assertEquals(expected.y, actual.y, tolerance, "y")
        assertEquals(expected.z, actual.z, tolerance, "z")
    }

    @Test
    fun screenCenterLooksAlongCameraForward() {
        val ray = frame().screenPointToRay(0.5f, 0.5f)

        assertVec3(Vec3.ZERO, ray.origin)
        assertVec3(Vec3(0f, 0f, -1f), ray.direction)
    }

    @Test
    fun rayOriginatesAtCameraPosition() {
        val pose = Pose(Vec3(1f, 2f, 3f), Quaternion.IDENTITY)
        val ray = frame(pose).screenPointToRay(0.5f, 0.5f)

        assertVec3(Vec3(1f, 2f, 3f), ray.origin)
    }

    @Test
    fun screenEdgesMapToFieldOfView() {
        // tanHalf = 1 → 90° horizontal/vertical FOV: the right edge of the
        // screen is 45° off forward, i.e. direction (1, 0, -1) normalized.
        val diag = 1f / sqrt(2f)

        val right = frame().screenPointToRay(1f, 0.5f)
        assertVec3(Vec3(diag, 0f, -diag), right.direction)

        // Screen Y grows downward, world Y up: the top edge points up
        val top = frame().screenPointToRay(0.5f, 0f)
        assertVec3(Vec3(0f, diag, -diag), top.direction)
    }

    @Test
    fun narrowFovScalesDirection() {
        // tanHalfX = 0.5 → right edge at atan(0.5) off forward
        val ray = frame(proj = projection(tanHalfX = 0.5f)).screenPointToRay(1f, 0.5f)

        val len = sqrt(0.5f * 0.5f + 1f)
        assertVec3(Vec3(0.5f / len, 0f, -1f / len), ray.direction)
    }

    @Test
    fun cameraRotationRotatesRay() {
        // Camera yawed 90° left: forward (-Z) becomes -X
        val pose = Pose(Vec3.ZERO, Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f))
        val ray = frame(pose).screenPointToRay(0.5f, 0.5f)

        assertVec3(Vec3(-1f, 0f, 0f), ray.direction, tolerance = 1e-4f)
    }

    @Test
    fun offCenterProjectionShiftsRay() {
        // P[8] = 0.2 shifts the projection center: the screen center no
        // longer maps to camera forward but to x = (0 + 0.2) / 1 = 0.2
        val ray = frame(proj = projection(offsetX = 0.2f)).screenPointToRay(0.5f, 0.5f)

        val len = sqrt(0.2f * 0.2f + 1f)
        assertVec3(Vec3(0.2f / len, 0f, -1f / len), ray.direction)
    }

    @Test
    fun directionIsNormalized() {
        val ray = frame().screenPointToRay(0.9f, 0.1f)

        assertEquals(1f, ray.direction.length(), 1e-5f)
    }
}
