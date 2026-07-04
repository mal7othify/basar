package com.spatialkit.compose

import androidx.compose.animation.core.AnimationVector4D
import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialStateTest {
    @Test
    fun vec3ConverterRoundTrips() {
        val original = Vec3(1.5f, -2f, 0.25f)
        val vector = Vec3.VectorConverter.convertToVector(original)
        val back = Vec3.VectorConverter.convertFromVector(vector)
        assertEquals(original, back)
    }

    @Test
    fun vec3ConverterMapsComponentsInOrder() {
        val vector = Vec3.VectorConverter.convertToVector(Vec3(1f, 2f, 3f))
        assertEquals(1f, vector.v1)
        assertEquals(2f, vector.v2)
        assertEquals(3f, vector.v3)
    }

    @Test
    fun quaternionConverterRoundTripsUnitQuaternions() {
        val original = Quaternion.fromEuler(30f, 45f, 60f)
        val vector = Quaternion.VectorConverter.convertToVector(original)
        val back = Quaternion.VectorConverter.convertFromVector(vector)
        assertQuaternionEquals(original, back)
    }

    @Test
    fun quaternionConverterRenormalizesInterpolatedValues() {
        // Mid-interpolation values are not unit length; the converter must
        // renormalize so nodes never receive a denormalized rotation.
        val aVector = Quaternion.VectorConverter.convertToVector(Quaternion.IDENTITY)
        val bVector = Quaternion.VectorConverter.convertToVector(Quaternion.fromEuler(0f, 90f, 0f))
        val midVector =
            AnimationVector4D(
                (aVector.v1 + bVector.v1) / 2f,
                (aVector.v2 + bVector.v2) / 2f,
                (aVector.v3 + bVector.v3) / 2f,
                (aVector.v4 + bVector.v4) / 2f,
            )

        val mid = Quaternion.VectorConverter.convertFromVector(midVector)
        assertTrue(abs(mid.length() - 1f) < 1e-5f, "expected unit quaternion, length=${mid.length()}")

        // And the renormalized midpoint is the halfway rotation (45° yaw).
        assertQuaternionEquals(Quaternion.fromEuler(0f, 45f, 0f), mid)
    }

    private fun assertQuaternionEquals(
        expected: Quaternion,
        actual: Quaternion,
        tolerance: Float = 1e-5f,
    ) {
        // q and -q are the same rotation; compare via |dot| ≈ 1.
        val dot = expected.dot(actual)
        assertTrue(
            abs(abs(dot) - 1f) < tolerance,
            "expected $expected, got $actual (|dot|=${abs(dot)})",
        )
    }
}
