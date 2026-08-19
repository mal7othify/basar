package com.spatialkit.engine

import com.spatialkit.render.SceneNode

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Transform
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneBoundsTest {
    private fun mesh(
        id: String,
        type: GeometryType = GeometryType.CUBE,
        transform: Transform = Transform(),
        geometry: GeometryData = GeometryData(type = type),
        visible: Boolean = true,
        castsShadow: Boolean = false,
    ): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform = transform,
            visible = visible,
            geometry = geometry,
            material = MaterialData(type = MaterialType.LIT, baseColor = Vec4(1f, 1f, 1f, 1f)),
            castsShadow = castsShadow,
        )

    // === localBounds ===

    @Test
    fun primitivesFitTheUnitCube() {
        listOf(
            GeometryType.CUBE,
            GeometryType.SPHERE,
            GeometryType.CYLINDER,
            GeometryType.CAPSULE,
        ).forEach { type ->
            val bounds = assertNotNull(SceneBounds.localBounds(GeometryData(type = type)))
            assertEquals(Vec3(-0.5f, -0.5f, -0.5f), bounds.min, "min of $type")
            assertEquals(Vec3(0.5f, 0.5f, 0.5f), bounds.max, "max of $type")
        }
    }

    @Test
    fun planeIsFlatInY() {
        val bounds = assertNotNull(SceneBounds.localBounds(GeometryData(type = GeometryType.PLANE)))
        assertEquals(0f, bounds.min.y)
        assertEquals(0f, bounds.max.y)
        assertEquals(-0.5f, bounds.min.x)
        assertEquals(0.5f, bounds.max.z)
    }

    @Test
    fun customMeshBoundsComeFromVertices() {
        val geometry =
            GeometryData(
                type = GeometryType.CUSTOM_MESH,
                vertices = floatArrayOf(-1f, 0f, 2f, 3f, -4f, 0f, 0f, 5f, -6f),
                indices = intArrayOf(0, 1, 2),
            )

        val bounds = assertNotNull(SceneBounds.localBounds(geometry))
        assertEquals(Vec3(-1f, -4f, -6f), bounds.min)
        assertEquals(Vec3(3f, 5f, 2f), bounds.max)
    }

    @Test
    fun customMeshWithoutVerticesHasNoBounds() {
        assertNull(SceneBounds.localBounds(GeometryData(type = GeometryType.CUSTOM_MESH)))
    }

    // === worldBounds ===

    @Test
    fun worldBoundsApplyTranslationAndScale() {
        val node =
            mesh(
                "cube",
                transform =
                    Transform(
                        position = Vec3(1f, 2f, 3f),
                        scale = Vec3(2f, 4f, 6f),
                    ),
            )

        val bounds = assertNotNull(SceneBounds.worldBounds(node))
        assertEquals(Vec3(0f, 0f, 0f), bounds.min)
        assertEquals(Vec3(2f, 4f, 6f), bounds.max)
        assertEquals(Vec3(1f, 2f, 3f), bounds.center)
    }

    @Test
    fun worldBoundsAccumulateGroupTransforms() {
        // Child cube offset +1 in X inside a group scaled ×2 and moved +10 in Y
        val group =
            SceneNode.Group(
                id = "group",
                transform =
                    Transform(
                        position = Vec3(0f, 10f, 0f),
                        scale = Vec3(2f, 2f, 2f),
                    ),
                children = listOf(mesh("cube", transform = Transform(position = Vec3(1f, 0f, 0f)))),
            )

        val bounds = assertNotNull(SceneBounds.worldBounds(group))
        assertEquals(Vec3(1f, 9f, -1f), bounds.min)
        assertEquals(Vec3(3f, 11f, 1f), bounds.max)
    }

    @Test
    fun rotatedCubeGrowsItsAabb() {
        // A unit cube rotated 45° about Y spans √2 in XZ
        val node =
            mesh(
                "cube",
                transform = Transform(rotation = Quaternion.fromAxisAngle(Vec3(0f, 1f, 0f), (PI / 4).toFloat())),
            )

        val bounds = assertNotNull(SceneBounds.worldBounds(node))
        val expected = (kotlin.math.sqrt(2.0) / 2).toFloat()
        assertEquals(expected, bounds.max.x, 1e-4f)
        assertEquals(expected, bounds.max.z, 1e-4f)
        assertEquals(0.5f, bounds.max.y, 1e-4f)
    }

    @Test
    fun invisibleNodesHaveNoBounds() {
        assertNull(SceneBounds.worldBounds(mesh("cube", visible = false)))
    }

    // === shadowCasters ===

    @Test
    fun findsTopmostCastersWithWorldBounds() {
        val flagged =
            SceneNode.Group(
                id = "model",
                transform = Transform(position = Vec3(0f, 1f, 0f)),
                castsShadow = true,
                // Nested flag must be ignored: one shadow per subtree
                children = listOf(mesh("inner", castsShadow = true)),
            )
        val unflagged = mesh("plain", transform = Transform(position = Vec3(5f, 0f, 0f)))

        val casters = SceneBounds.shadowCasters(listOf(flagged, unflagged))

        assertEquals(1, casters.size)
        val (node, bounds) = casters[0]
        assertEquals("model", node.id)
        assertEquals(0.5f, bounds.min.y, 1e-4f)
        assertEquals(1.5f, bounds.max.y, 1e-4f)
    }

    @Test
    fun castersNestedInGroupsGetWorldSpaceBounds() {
        // Caster sits inside an unflagged group translated +10 in X
        val root =
            SceneNode.Group(
                id = "root",
                transform = Transform(position = Vec3(10f, 0f, 0f)),
                children = listOf(mesh("caster", castsShadow = true)),
            )

        val casters = SceneBounds.shadowCasters(listOf(root))

        assertEquals(1, casters.size)
        assertEquals(9.5f, casters[0].second.min.x, 1e-4f)
        assertEquals(10.5f, casters[0].second.max.x, 1e-4f)
    }

    @Test
    fun invisibleCastersAreSkipped() {
        val hidden = mesh("hidden", visible = false, castsShadow = true)
        assertTrue(SceneBounds.shadowCasters(listOf(hidden)).isEmpty())
    }
}
