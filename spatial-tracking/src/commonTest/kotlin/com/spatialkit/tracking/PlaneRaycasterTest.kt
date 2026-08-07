package com.spatialkit.tracking

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaneRaycasterTest {
    private fun floorPlane(
        id: String = "floor",
        center: Vec3 = Vec3.ZERO,
        extentX: Float = 2f,
        extentZ: Float = 2f,
        vertices: List<Vec3>? = null,
    ) = DetectedPlane(
        id = id,
        type = PlaneType.HORIZONTAL_UP,
        centerPose = Pose(center, Quaternion.IDENTITY),
        extentX = extentX,
        extentZ = extentZ,
        vertices = vertices,
    )

    @Test
    fun hitsExtentRectangleWhenNoPolygon() {
        val hit =
            PlaneRaycaster.raycastPlane(
                origin = Vec3(0f, 1f, 0f),
                direction = Vec3(0f, -1f, 0f),
                plane = floorPlane(),
            )

        assertNotNull(hit)
        assertEquals("floor", hit.planeId)
        assertEquals(0f, hit.position.y, 1e-5f)
        assertEquals(1f, hit.distance, 1e-5f)
        // Normal faces the ray origin (up, against the downward ray)
        assertEquals(1f, assertNotNull(hit.normal).y, 1e-5f)
    }

    @Test
    fun missesOutsideExtents() {
        val hit =
            PlaneRaycaster.raycastPlane(
                origin = Vec3(5f, 1f, 0f),
                direction = Vec3(0f, -1f, 0f),
                plane = floorPlane(extentX = 2f, extentZ = 2f),
            )

        assertNull(hit)
    }

    @Test
    fun polygonBoundaryWinsOverExtents() {
        // L-shaped polygon that excludes the +x/+z quadrant even though the
        // extent rectangle covers it
        val polygon =
            listOf(
                Vec3(-1f, 0f, -1f),
                Vec3(1f, 0f, -1f),
                Vec3(1f, 0f, 0f),
                Vec3(0f, 0f, 0f),
                Vec3(0f, 0f, 1f),
                Vec3(-1f, 0f, 1f),
            )
        val plane = floorPlane(vertices = polygon)

        val insideL =
            PlaneRaycaster.raycastPlane(
                origin = Vec3(-0.5f, 1f, 0.5f),
                direction = Vec3(0f, -1f, 0f),
                plane = plane,
            )
        val inCutout =
            PlaneRaycaster.raycastPlane(
                origin = Vec3(0.5f, 1f, 0.5f),
                direction = Vec3(0f, -1f, 0f),
                plane = plane,
            )

        assertNotNull(insideL)
        assertNull(inCutout)
    }

    @Test
    fun missesParallelRayAndHitsBehindOrigin() {
        val parallel =
            PlaneRaycaster.raycastPlane(
                origin = Vec3(0f, 1f, 0f),
                direction = Vec3(1f, 0f, 0f),
                plane = floorPlane(),
            )
        val behind =
            PlaneRaycaster.raycastPlane(
                origin = Vec3(0f, 1f, 0f),
                direction = Vec3(0f, 1f, 0f),
                plane = floorPlane(),
            )

        assertNull(parallel)
        assertNull(behind)
    }

    @Test
    fun hitsRotatedWallPlane() {
        // Wall at x = 2: local +y (plane normal) rotated to world -x
        val wall =
            DetectedPlane(
                id = "wall",
                type = PlaneType.VERTICAL,
                centerPose =
                    Pose(
                        position = Vec3(2f, 0f, 0f),
                        rotation = Quaternion.fromAxisAngle(Vec3(0f, 0f, 1f), kotlin.math.PI.toFloat() / 2f),
                    ),
                extentX = 2f,
                extentZ = 2f,
                vertices = null,
            )

        val hit =
            PlaneRaycaster.raycastPlane(
                origin = Vec3.ZERO,
                direction = Vec3(1f, 0f, 0f),
                plane = wall,
            )

        assertNotNull(hit)
        assertEquals(2f, hit.position.x, 1e-4f)
        assertEquals(2f, hit.distance, 1e-4f)
        // Normal faces back toward the ray origin
        assertTrue(assertNotNull(hit.normal).x < -0.99f)
    }

    @Test
    fun distanceIsWorldUnitsForUnnormalizedDirection() {
        val hit =
            PlaneRaycaster.raycastPlane(
                origin = Vec3(0f, 2f, 0f),
                direction = Vec3(0f, -2f, -2f),
                plane = floorPlane(extentX = 10f, extentZ = 10f),
            )

        assertNotNull(hit)
        // Ray drops 2m and travels 2m in -z: world distance is 2*sqrt(2)
        assertEquals(2f * sqrt(2f), hit.distance, 1e-4f)
        assertEquals(-2f, hit.position.z, 1e-4f)
    }

    @Test
    fun raycastReturnsClosestPlane() {
        val near = floorPlane(id = "near", center = Vec3(0f, 1f, 0f), extentX = 10f, extentZ = 10f)
        val far = floorPlane(id = "far", center = Vec3(0f, 0f, 0f), extentX = 10f, extentZ = 10f)

        val hit =
            PlaneRaycaster.raycast(
                origin = Vec3(0f, 3f, 0f),
                direction = Vec3(0f, -1f, 0f),
                planes = listOf(far, near),
            )

        assertEquals("near", assertNotNull(hit).planeId)
    }
}
