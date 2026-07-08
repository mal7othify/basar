package com.spatialkit.engine

import com.spatialkit.core.math.Transform
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneRaycasterTest {
    private fun mesh(
        id: String,
        type: GeometryType,
        transform: Transform = Transform(),
        geometry: GeometryData = GeometryData(type = type),
        visible: Boolean = true,
    ): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform = transform,
            visible = visible,
            geometry = geometry,
            material = MaterialData(type = MaterialType.LIT, baseColor = Vec4(1f, 1f, 1f, 1f)),
        )

    private val forward = Vec3(0f, 0f, -1f)

    // === Primitives ===

    @Test
    fun hitsCubeFrontFace() {
        val cube = mesh("cube", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -2f)))

        val hit = SceneRaycaster.raycast(listOf(cube), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals("cube", hit.nodeId)
        assertEquals(1.5f, hit.distance, 1e-4f)
        assertEquals(1f, hit.normal.z, 1e-4f)
    }

    @Test
    fun missesCubeBesideRay() {
        val cube = mesh("cube", GeometryType.CUBE, Transform(position = Vec3(2f, 0f, -2f)))

        assertNull(SceneRaycaster.raycast(listOf(cube), Vec3.ZERO, forward))
    }

    @Test
    fun hitsSphereAtScaledRadius() {
        // Unit sphere has diameter 1; scaled ×0.2 → radius 0.1
        val sphere =
            mesh(
                "sphere",
                GeometryType.SPHERE,
                Transform(position = Vec3(0f, 0f, -1f), scale = Vec3(0.2f, 0.2f, 0.2f)),
            )

        val hit = SceneRaycaster.raycast(listOf(sphere), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals(0.9f, hit.distance, 1e-4f)
    }

    @Test
    fun hitsPlaneFromAbove() {
        val plane = mesh("plane", GeometryType.PLANE, Transform(position = Vec3(0f, -1f, 0f)))

        val hit = SceneRaycaster.raycast(listOf(plane), Vec3(0.2f, 0f, 0.2f), Vec3(0f, -1f, 0f))

        assertNotNull(hit)
        assertEquals(1f, hit.distance, 1e-4f)
        assertEquals(1f, hit.normal.y, 1e-4f)
    }

    @Test
    fun missesPlaneOutsideExtent() {
        val plane = mesh("plane", GeometryType.PLANE)

        assertNull(SceneRaycaster.raycast(listOf(plane), Vec3(0.6f, 1f, 0f), Vec3(0f, -1f, 0f)))
    }

    @Test
    fun hitsCylinderEndCap() {
        // Straight down onto the top cap — the old Android raycast missed this
        val cylinder = mesh("cyl", GeometryType.CYLINDER, Transform(position = Vec3(0f, -1f, 0f)))

        val hit = SceneRaycaster.raycast(listOf(cylinder), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f))

        assertNotNull(hit)
        assertEquals(1.5f, hit.distance, 1e-4f)
        assertEquals(1f, hit.normal.y, 1e-4f)
    }

    @Test
    fun hitsCylinderSide() {
        val cylinder = mesh("cyl", GeometryType.CYLINDER, Transform(position = Vec3(0f, 0f, -2f)))

        val hit = SceneRaycaster.raycast(listOf(cylinder), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals(1.5f, hit.distance, 1e-4f)
    }

    @Test
    fun hitsCustomMeshTriangle() {
        // Single triangle facing +Z at z = -1
        val geometry =
            GeometryData(
                type = GeometryType.CUSTOM_MESH,
                vertices = floatArrayOf(-1f, -1f, -1f, 1f, -1f, -1f, 0f, 1f, -1f),
                indices = intArrayOf(0, 1, 2),
            )
        val tri = mesh("tri", GeometryType.CUSTOM_MESH, geometry = geometry)

        val hit = SceneRaycaster.raycast(listOf(tri), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals("tri", hit.nodeId)
        assertEquals(1f, hit.distance, 1e-4f)
        // Normal faces the ray origin regardless of winding
        assertEquals(1f, hit.normal.z, 1e-4f)
    }

    @Test
    fun customMeshWithoutIndicesUsesSequentialTriangles() {
        val geometry =
            GeometryData(
                type = GeometryType.CUSTOM_MESH,
                vertices = floatArrayOf(-1f, -1f, -2f, 1f, -1f, -2f, 0f, 1f, -2f),
            )
        val tri = mesh("tri", GeometryType.CUSTOM_MESH, geometry = geometry)

        val hit = SceneRaycaster.raycast(listOf(tri), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals(2f, hit.distance, 1e-4f)
    }

    // === Hierarchy ===

    @Test
    fun groupTransformOffsetsChildren() {
        val group =
            SceneNode.Group(
                id = "group",
                transform = Transform(position = Vec3(0f, 0f, -2f)),
                children = listOf(mesh("child", GeometryType.CUBE)),
            )

        val hit = SceneRaycaster.raycast(listOf(group), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals("child", hit.nodeId)
        assertEquals(1.5f, hit.distance, 1e-4f)
    }

    @Test
    fun nestedGroupScaleAndRotationCompose() {
        // Group scaled ×2 containing a cube at local (0, 0, -1) → world (0, 0, -2),
        // cube rendered at 2× size (faces at z = -1 and z = -3)
        val group =
            SceneNode.Group(
                id = "group",
                transform = Transform(scale = Vec3(2f, 2f, 2f)),
                children =
                    listOf(
                        mesh("child", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -1f))),
                    ),
            )

        val hit = SceneRaycaster.raycast(listOf(group), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals(1f, hit.distance, 1e-4f)
    }

    @Test
    fun rotatedGroupRotatesChildren() {
        // Group yawed 90°: child at local (0, 0, -1) lands at world (-1, 0, 0)
        val group =
            SceneNode.Group(
                id = "group",
                transform = Transform(rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)),
                children =
                    listOf(
                        mesh("child", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -1f))),
                    ),
            )

        val hit = SceneRaycaster.raycast(listOf(group), Vec3.ZERO, Vec3(-1f, 0f, 0f))

        assertNotNull(hit)
        assertEquals("child", hit.nodeId)
        assertEquals(0.5f, hit.distance, 1e-4f)
    }

    @Test
    fun closestNodeWinsAcrossHierarchyLevels() {
        val near = mesh("near", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -1f)))
        val farGroup =
            SceneNode.Group(
                id = "group",
                transform = Transform(position = Vec3(0f, 0f, -3f)),
                children = listOf(mesh("far", GeometryType.CUBE)),
            )

        val hit = SceneRaycaster.raycast(listOf(farGroup, near), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals("near", hit.nodeId)
    }

    @Test
    fun invisibleNodesAreSkipped() {
        val hidden =
            mesh("hidden", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -1f)), visible = false)
        val behind = mesh("behind", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -3f)))

        val hit = SceneRaycaster.raycast(listOf(hidden, behind), Vec3.ZERO, forward)

        assertNotNull(hit)
        assertEquals("behind", hit.nodeId)
    }

    @Test
    fun invisibleGroupHidesChildren() {
        val group =
            SceneNode.Group(
                id = "group",
                transform = Transform(),
                visible = false,
                children = listOf(mesh("child", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -1f)))),
            )

        assertNull(SceneRaycaster.raycast(listOf(group), Vec3.ZERO, forward))
    }

    @Test
    fun unnormalizedDirectionReturnsWorldDistance() {
        val cube = mesh("cube", GeometryType.CUBE, Transform(position = Vec3(0f, 0f, -2f)))

        val hit = SceneRaycaster.raycast(listOf(cube), Vec3.ZERO, Vec3(0f, 0f, -10f))

        assertNotNull(hit)
        assertEquals(1.5f, hit.distance, 1e-4f)
    }

    @Test
    fun nonUniformScaleNormalIsCorrect() {
        // Sphere stretched ×4 along X, hit from a 45° diagonal on top: the
        // world normal must bend toward Y (inverse-transpose), not toward X
        val sphere =
            mesh(
                "sphere",
                GeometryType.SPHERE,
                Transform(position = Vec3(0f, 0f, -2f), scale = Vec3(4f, 1f, 1f)),
            )

        val hit = SceneRaycaster.raycast(listOf(sphere), Vec3(0f, 5f, -2f), Vec3(0f, -1f, 0f))

        assertNotNull(hit)
        assertTrue(hit.normal.y > 0.99f, "expected up-facing normal, got ${hit.normal}")
    }
}
