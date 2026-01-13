package com.spatialkit.core.types

import com.spatialkit.core.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundsTest {
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
    fun testBoundingBoxCreation() {
        val box =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        assertVec3Equals(Vec3(-1f, -1f, -1f), box.min)
        assertVec3Equals(Vec3(1f, 1f, 1f), box.max)
    }

    @Test
    fun testBoundingBoxCenter() {
        val box =
            BoundingBox(
                min = Vec3(-2f, -2f, -2f),
                max = Vec3(2f, 2f, 2f),
            )

        assertVec3Equals(Vec3.ZERO, box.center)
    }

    @Test
    fun testBoundingBoxSize() {
        val box =
            BoundingBox(
                min = Vec3(-1f, -2f, -3f),
                max = Vec3(1f, 2f, 3f),
            )

        val size = box.size
        assertEquals(2f, size.width, 0.0001f)
        assertEquals(4f, size.height, 0.0001f)
        assertEquals(6f, size.depth, 0.0001f)
    }

    @Test
    fun testBoundingBoxExtents() {
        val box =
            BoundingBox(
                min = Vec3(-2f, -4f, -6f),
                max = Vec3(2f, 4f, 6f),
            )

        assertVec3Equals(Vec3(2f, 4f, 6f), box.extents)
    }

    @Test
    fun testBoundingBoxCorners() {
        val box =
            BoundingBox(
                min = Vec3(0f, 0f, 0f),
                max = Vec3(1f, 1f, 1f),
            )

        val corners = box.corners
        assertEquals(8, corners.size)

        // Check a few corners
        assertVec3Equals(Vec3(0f, 0f, 0f), corners[0]) // min corner
        assertVec3Equals(Vec3(1f, 1f, 1f), corners[7]) // max corner
    }

    @Test
    fun testContainsPoint() {
        val box =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        // Inside
        assertTrue(box.contains(Vec3(0f, 0f, 0f)))
        assertTrue(box.contains(Vec3(0.5f, 0.5f, 0.5f)))

        // On boundary
        assertTrue(box.contains(Vec3(1f, 1f, 1f)))
        assertTrue(box.contains(Vec3(-1f, -1f, -1f)))

        // Outside
        assertFalse(box.contains(Vec3(2f, 0f, 0f)))
        assertFalse(box.contains(Vec3(0f, 2f, 0f)))
        assertFalse(box.contains(Vec3(0f, 0f, 2f)))
    }

    @Test
    fun testIntersects() {
        val box1 =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        val box2 =
            BoundingBox(
                min = Vec3(0f, 0f, 0f),
                max = Vec3(2f, 2f, 2f),
            )

        val box3 =
            BoundingBox(
                min = Vec3(2f, 2f, 2f),
                max = Vec3(3f, 3f, 3f),
            )

        assertTrue(box1.intersects(box2)) // Overlapping
        assertFalse(box1.intersects(box3)) // Separate
    }

    @Test
    fun testExpandWithPoint() {
        val box =
            BoundingBox(
                min = Vec3(0f, 0f, 0f),
                max = Vec3(1f, 1f, 1f),
            )

        val expanded = box.expand(Vec3(2f, 2f, 2f))

        assertVec3Equals(Vec3(0f, 0f, 0f), expanded.min)
        assertVec3Equals(Vec3(2f, 2f, 2f), expanded.max)
    }

    @Test
    fun testExpandWithAmount() {
        val box =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        val expanded = box.expand(1f)

        assertVec3Equals(Vec3(-2f, -2f, -2f), expanded.min)
        assertVec3Equals(Vec3(2f, 2f, 2f), expanded.max)
    }

    @Test
    fun testClosestPoint() {
        val box =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        // Point inside - returns the point itself
        assertVec3Equals(Vec3(0f, 0f, 0f), box.closestPoint(Vec3(0f, 0f, 0f)))

        // Point outside - returns closest point on box
        assertVec3Equals(Vec3(1f, 0f, 0f), box.closestPoint(Vec3(2f, 0f, 0f)))
        assertVec3Equals(Vec3(-1f, -1f, -1f), box.closestPoint(Vec3(-2f, -2f, -2f)))
    }

    @Test
    fun testDistance() {
        val box =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        // Point inside - distance is 0
        assertEquals(0f, box.distance(Vec3(0f, 0f, 0f)), 0.0001f)

        // Point outside
        val distance = box.distance(Vec3(3f, 0f, 0f))
        assertEquals(2f, distance, 0.0001f)
    }

    @Test
    fun testTranslate() {
        val box =
            BoundingBox(
                min = Vec3(0f, 0f, 0f),
                max = Vec3(1f, 1f, 1f),
            )

        val translated = box.translate(Vec3(5f, 5f, 5f))

        assertVec3Equals(Vec3(5f, 5f, 5f), translated.min)
        assertVec3Equals(Vec3(6f, 6f, 6f), translated.max)
    }

    @Test
    fun testScale() {
        val box =
            BoundingBox(
                min = Vec3(-1f, -1f, -1f),
                max = Vec3(1f, 1f, 1f),
            )

        val scaled = box.scale(2f)

        assertVec3Equals(Vec3(-2f, -2f, -2f), scaled.min)
        assertVec3Equals(Vec3(2f, 2f, 2f), scaled.max)
    }

    @Test
    fun testFromCenterAndExtents() {
        val box =
            BoundingBox.fromCenterAndExtents(
                center = Vec3(5f, 5f, 5f),
                extents = Vec3(1f, 2f, 3f),
            )

        assertVec3Equals(Vec3(4f, 3f, 2f), box.min)
        assertVec3Equals(Vec3(6f, 7f, 8f), box.max)
    }

    @Test
    fun testFromPoints() {
        val points =
            listOf(
                Vec3(-1f, -1f, -1f),
                Vec3(2f, 3f, 4f),
                Vec3(0f, 0f, 0f),
            )

        val box = BoundingBox.fromPoints(points)

        assertVec3Equals(Vec3(-1f, -1f, -1f), box.min)
        assertVec3Equals(Vec3(2f, 3f, 4f), box.max)
    }

    @Test
    fun testVolume() {
        val box =
            BoundingBox(
                min = Vec3(0f, 0f, 0f),
                max = Vec3(2f, 3f, 4f),
            )

        assertEquals(24f, box.volume(), 0.0001f)
    }
}
