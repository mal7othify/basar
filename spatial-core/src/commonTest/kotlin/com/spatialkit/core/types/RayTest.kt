package com.spatialkit.core.types

import com.spatialkit.core.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RayTest {
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
    fun testRayCreation() {
        val ray =
            Ray(
                origin = Vec3.ZERO,
                direction = Vec3(1f, 0f, 0f),
            )

        assertVec3Equals(Vec3.ZERO, ray.origin)
        assertVec3Equals(Vec3(1f, 0f, 0f), ray.direction)
    }

    @Test
    fun testPointAt() {
        val ray =
            Ray(
                origin = Vec3.ZERO,
                direction = Vec3(1f, 0f, 0f),
            )

        val point = ray.pointAt(5f)
        assertVec3Equals(Vec3(5f, 0f, 0f), point)
    }

    @Test
    fun testRayBoxIntersection() {
        val ray =
            Ray(
                origin = Vec3(-5f, 0f, 0f),
                direction = Vec3(1f, 0f, 0f),
            )

        val box =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        val distance = ray.intersects(box)
        assertNotNull(distance)
        assertEquals(4f, distance, 0.001f)

        // Ray pointing away from box
        val rayAway =
            Ray(
                origin = Vec3(-5f, 0f, 0f),
                direction = Vec3(-1f, 0f, 0f),
            )
        assertNull(rayAway.intersects(box))
    }

    @Test
    fun testRaySphereIntersection() {
        val ray =
            Ray(
                origin = Vec3(-5f, 0f, 0f),
                direction = Vec3(1f, 0f, 0f),
            )

        val distance = ray.intersectsSphere(center = Vec3.ZERO, radius = 1f)
        assertNotNull(distance)
        assertEquals(4f, distance, 0.001f)

        // Ray missing sphere
        val rayMiss =
            Ray(
                origin = Vec3(-5f, 5f, 0f),
                direction = Vec3(1f, 0f, 0f),
            )
        assertNull(rayMiss.intersectsSphere(center = Vec3.ZERO, radius = 1f))
    }

    @Test
    fun testRayPlaneIntersection() {
        val ray =
            Ray(
                origin = Vec3(0f, 5f, 0f),
                direction = Vec3(0f, -1f, 0f),
            )

        val distance =
            ray.intersectsPlane(
                planePoint = Vec3.ZERO,
                planeNormal = Vec3(0f, 1f, 0f),
            )

        assertNotNull(distance)
        assertEquals(5f, distance, 0.001f)

        // Ray parallel to plane
        val rayParallel =
            Ray(
                origin = Vec3(0f, 5f, 0f),
                direction = Vec3(1f, 0f, 0f),
            )
        assertNull(
            rayParallel.intersectsPlane(
                planePoint = Vec3.ZERO,
                planeNormal = Vec3(0f, 1f, 0f),
            ),
        )
    }

    @Test
    fun testClosestPointTo() {
        val ray =
            Ray(
                origin = Vec3.ZERO,
                direction = Vec3(1f, 0f, 0f),
            )

        val point = Vec3(5f, 3f, 0f)
        val closest = ray.closestPointTo(point)

        assertVec3Equals(Vec3(5f, 0f, 0f), closest)
    }

    @Test
    fun testDistanceTo() {
        val ray =
            Ray(
                origin = Vec3.ZERO,
                direction = Vec3(1f, 0f, 0f),
            )

        val point = Vec3(5f, 3f, 0f)
        val distance = ray.distanceTo(point)

        assertEquals(3f, distance, 0.0001f)
    }

    @Test
    fun testFromTo() {
        val from = Vec3(0f, 0f, 0f)
        val to = Vec3(10f, 0f, 0f)

        val ray = Ray.fromTo(from, to)

        assertVec3Equals(from, ray.origin)
        assertVec3Equals(Vec3(1f, 0f, 0f), ray.normalizedDirection)
    }

    @Test
    fun testRayHit() {
        val hit =
            RayHit.hit(
                distance = 5f,
                point = Vec3(5f, 0f, 0f),
                normal = Vec3(1f, 0f, 0f),
            )

        assertEquals(true, hit.hit)
        assertEquals(5f, hit.distance)
        assertNotNull(hit.point)
        assertNotNull(hit.normal)

        val miss = RayHit.MISS
        assertEquals(false, miss.hit)
        assertNull(miss.distance)
        assertNull(miss.point)
        assertNull(miss.normal)
    }
}
