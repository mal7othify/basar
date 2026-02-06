package com.spatialkit.dsl

import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.Color
import com.spatialkit.scene.*

/**
 * DSL scope for building group node hierarchies.
 *
 * Nodes added in this scope become children of the parent group.
 * Child transforms are relative to the parent group's transform.
 */
@SpatialDsl
class GroupScope(
    private val group: GroupNode,
) {
    /**
     * Creates a cube node and adds it as a child of this group.
     */
    fun cube(
        size: Float = 0.1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CubeNode.() -> Unit = {},
    ): CubeNode {
        return CubeNode(size, color, id).apply(configure).also { group.addChild(it) }
    }

    /**
     * Creates a sphere node and adds it as a child of this group.
     */
    fun sphere(
        radius: Float = 0.05f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: SphereNode.() -> Unit = {},
    ): SphereNode {
        return SphereNode(radius, color, id).apply(configure).also { group.addChild(it) }
    }

    /**
     * Creates a plane node and adds it as a child of this group.
     */
    fun plane(
        width: Float = 1f,
        height: Float = 1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: PlaneNode.() -> Unit = {},
    ): PlaneNode {
        return PlaneNode(width, height, color, id).apply(configure).also { group.addChild(it) }
    }

    /**
     * Creates a cylinder node and adds it as a child of this group.
     */
    fun cylinder(
        radius: Float = 0.05f,
        height: Float = 0.2f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CylinderNode.() -> Unit = {},
    ): CylinderNode {
        return CylinderNode(radius, height, color, id).apply(configure).also { group.addChild(it) }
    }

    /**
     * Creates a capsule node and adds it as a child of this group.
     */
    fun capsule(
        radius: Float = 0.05f,
        height: Float = 0.2f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CapsuleNode.() -> Unit = {},
    ): CapsuleNode {
        return CapsuleNode(radius, height, color, id).apply(configure).also { group.addChild(it) }
    }

    /**
     * Creates a nested group node.
     */
    fun group(
        id: String = SpatialNode.generateId(),
        configure: GroupScope.() -> Unit,
    ): GroupNode {
        val nestedGroup = GroupNode(id)
        GroupScope(nestedGroup).configure()
        group.addChild(nestedGroup)
        return nestedGroup
    }
}
