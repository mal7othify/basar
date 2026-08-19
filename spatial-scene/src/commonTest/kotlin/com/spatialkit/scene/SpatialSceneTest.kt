package com.spatialkit.scene

import com.spatialkit.render.AnchorType
import com.spatialkit.engine.Quaternion
import com.spatialkit.render.SceneNode
import com.spatialkit.engine.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpatialSceneTest {
    @Test
    fun addIsIdempotent() {
        val scene = SpatialScene()
        val cube = CubeNode()
        scene.add(cube)
        scene.add(cube)
        assertEquals(1, scene.nodes.size)
    }

    @Test
    fun removeDetachesNode() {
        val scene = SpatialScene()
        val cube = CubeNode()
        scene.add(cube)
        assertTrue(scene.remove(cube))
        assertTrue(scene.nodes.isEmpty())
        assertFalse(scene.remove(cube))
    }

    @Test
    fun removeByIdFindsNestedNodes() {
        val scene = SpatialScene()
        val group = GroupNode()
        val child = CubeNode(id = "child")
        group.addChild(child)
        scene.add(group)

        assertTrue(scene.removeById("child"))
        assertNull(child.parent)
        assertTrue(group.children.isEmpty())
        assertFalse(scene.removeById("child"))
    }

    @Test
    fun getAllNodesIncludesDescendants() {
        val scene = SpatialScene()
        val group = GroupNode()
        group.addChild(CubeNode())
        group.addChild(SphereNode())
        scene.add(group)
        scene.add(CubeNode())

        assertEquals(4, scene.getAllNodes().size)
    }

    @Test
    fun toSceneDataSnapshotsNodesAndAnchors() {
        val scene = SpatialScene()
        val cube = CubeNode(size = 1f, id = "cube")
        cube.position = Vec3(0f, 0f, -1f)
        scene.add(cube)
        scene.createAnchor(AnchorType.World(Vec3.ZERO, Quaternion.IDENTITY), id = "anchor")

        val data = scene.toSceneData()
        assertEquals(1, data.nodes.size)
        assertEquals(1, data.anchors.size)
        assertEquals("anchor", data.anchors[0].id)

        val mesh = assertIs<SceneNode.Mesh>(data.nodes[0])
        assertEquals("cube", mesh.id)
        assertEquals(-1f, mesh.transform.position.z)
    }

    @Test
    fun anchorLifecycle() {
        val scene = SpatialScene()
        val anchor = scene.createAnchor(AnchorType.World(Vec3.ZERO, Quaternion.IDENTITY))

        assertNotNull(scene.getAnchor(anchor.id))
        assertEquals(1, scene.allAnchors.size)
        assertTrue(scene.removeAnchor(anchor))
        assertNull(scene.getAnchor(anchor.id))
    }

    @Test
    fun clearRemovesRootNodes() {
        val scene = SpatialScene()
        scene.add(CubeNode())
        scene.add(SphereNode())
        scene.clear()
        assertTrue(scene.nodes.isEmpty())
    }
}
