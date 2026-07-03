package com.spatialkit.scene

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.engine.SceneNode
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpatialNodeTest {
    private fun assertVec3Equals(
        expected: Vec3,
        actual: Vec3,
        delta: Float = 0.0001f,
    ) {
        assertEquals(expected.x, actual.x, delta, "x mismatch")
        assertEquals(expected.y, actual.y, delta, "y mismatch")
        assertEquals(expected.z, actual.z, delta, "z mismatch")
    }

    @Test
    fun worldPositionAppliesParentScale() {
        val group = GroupNode()
        group.scale = Vec3(2f, 2f, 2f)
        val child = CubeNode()
        child.position = Vec3(1f, 0f, 0f)
        group.addChild(child)

        // Local (1,0,0) under a 2x-scaled parent sits at world (2,0,0)
        assertVec3Equals(Vec3(2f, 0f, 0f), child.worldPosition)
    }

    @Test
    fun worldPositionAppliesParentRotationAndTranslation() {
        val group = GroupNode()
        group.position = Vec3(0f, 1f, 0f)
        group.rotation = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)
        val child = CubeNode()
        child.position = Vec3(1f, 0f, 0f)
        group.addChild(child)

        // +X rotated 90° around +Y (right-handed) points toward -Z
        assertVec3Equals(Vec3(0f, 1f, -1f), child.worldPosition, 0.001f)
    }

    @Test
    fun worldPositionThroughNestedScaledParents() {
        val outer = GroupNode().apply { scale = Vec3(2f, 2f, 2f) }
        val inner = GroupNode().apply { position = Vec3(1f, 0f, 0f) }
        val leaf = CubeNode().apply { position = Vec3(1f, 0f, 0f) }
        outer.addChild(inner)
        inner.addChild(leaf)

        // inner sits at 2 (scaled), leaf adds 1 * outer scale 2 = 2 more
        assertVec3Equals(Vec3(4f, 0f, 0f), leaf.worldPosition)
        assertVec3Equals(Vec3(2f, 2f, 2f), leaf.worldScale)
    }

    @Test
    fun addChildRejectsSelf() {
        val node = GroupNode()
        assertFailsWith<IllegalArgumentException> { node.addChild(node) }
    }

    @Test
    fun addChildRejectsAncestorCycle() {
        val grandparent = GroupNode()
        val parent = GroupNode()
        val child = GroupNode()
        grandparent.addChild(parent)
        parent.addChild(child)

        assertFailsWith<IllegalArgumentException> { child.addChild(grandparent) }
        assertFailsWith<IllegalArgumentException> { child.addChild(parent) }
    }

    @Test
    fun addChildReparents() {
        val a = GroupNode()
        val b = GroupNode()
        val child = CubeNode()

        a.addChild(child)
        assertSame(a, child.parent)

        b.addChild(child)
        assertSame(b, child.parent)
        assertTrue(a.children.isEmpty(), "old parent should no longer list the child")
        assertEquals(listOf<SpatialNode>(child), b.children)
    }

    @Test
    fun removeFromParentClearsParent() {
        val group = GroupNode()
        val child = CubeNode()
        group.addChild(child)

        child.removeFromParent()
        assertNull(child.parent)
        assertTrue(group.children.isEmpty())
    }

    @Test
    fun meshNodeWithoutChildrenConvertsToMesh() {
        val cube = CubeNode(size = 0.5f)
        cube.position = Vec3(1f, 2f, 3f)
        cube.scale = Vec3(2f, 2f, 2f)

        val node = cube.toSceneNode()
        val mesh = assertIs<SceneNode.Mesh>(node)
        assertEquals(cube.id, mesh.id)
        assertVec3Equals(Vec3(1f, 2f, 3f), mesh.transform.position)
        // geometry size (0.5) * node scale (2)
        assertVec3Equals(Vec3(1f, 1f, 1f), mesh.transform.scale)
    }

    @Test
    fun meshNodeWithChildrenConvertsToGroupContainingMeshAndChildren() {
        val cube = CubeNode(size = 0.5f)
        cube.position = Vec3(0f, 1f, 0f)
        val satellite = SphereNode(radius = 0.1f)
        satellite.position = Vec3(1f, 0f, 0f)
        cube.addChild(satellite)

        val node = cube.toSceneNode()
        val group = assertIs<SceneNode.Group>(node)
        assertEquals(cube.id, group.id)
        assertVec3Equals(Vec3(0f, 1f, 0f), group.transform.position)
        // Group scale carries the node scale only, not the cube's size
        assertVec3Equals(Vec3.ONE, group.transform.scale)
        assertEquals(2, group.children.size)

        val innerMesh = assertIs<SceneNode.Mesh>(group.children[0])
        assertVec3Equals(Vec3(0.5f, 0.5f, 0.5f), innerMesh.transform.scale)
        assertVec3Equals(Vec3.ZERO, innerMesh.transform.position)

        val childMesh = assertIs<SceneNode.Mesh>(group.children[1])
        assertEquals(satellite.id, childMesh.id)
        assertVec3Equals(Vec3(1f, 0f, 0f), childMesh.transform.position)
    }

    @Test
    fun planeNodeIsDoubleSidedAndPropagatesToMaterial() {
        val plane = PlaneNode()
        val mesh = assertIs<SceneNode.Mesh>(plane.toSceneNode())
        assertTrue(mesh.material.doubleSided)

        val cube = CubeNode()
        val cubeMesh = assertIs<SceneNode.Mesh>(cube.toSceneNode())
        assertTrue(!cubeMesh.material.doubleSided)
    }

    @Test
    fun findNodeByIdSearchesDescendants() {
        val root = GroupNode()
        val mid = GroupNode()
        val leaf = CubeNode(id = "leaf")
        root.addChild(mid)
        mid.addChild(leaf)

        assertSame(leaf, root.findNodeById("leaf"))
        assertNull(root.findNodeById("missing"))
    }

    @Test
    fun generateIdIsUnique() {
        val ids = (1..10_000).map { SpatialNode.generateId() }.toSet()
        assertEquals(10_000, ids.size)
    }
}
