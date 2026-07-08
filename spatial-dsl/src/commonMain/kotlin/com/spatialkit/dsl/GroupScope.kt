package com.spatialkit.dsl

import com.spatialkit.core.types.Color
import com.spatialkit.engine.GeometryData
import com.spatialkit.scene.CapsuleNode
import com.spatialkit.scene.CubeNode
import com.spatialkit.scene.CustomMeshNode
import com.spatialkit.scene.CylinderNode
import com.spatialkit.scene.GltfModel
import com.spatialkit.scene.GroupNode
import com.spatialkit.scene.ModelNode
import com.spatialkit.scene.PlaneNode
import com.spatialkit.scene.SpatialNode
import com.spatialkit.scene.SphereNode

/**
 * DSL scope for building group node hierarchies.
 *
 * Nodes added in this scope become children of the parent group.
 * Child transforms are relative to the parent group's transform.
 */
@SpatialDsl
public class GroupScope(
    private val group: GroupNode,
) {
    /**
     * The group node being built. Use it to set the group's own transform or
     * interaction behavior:
     *
     * ```kotlin
     * group(id = "furniture") {
     *     node.position = vec3(0f, 0f, -1f)
     *     node.onTap = { event -> select(event.node) }
     *     node.draggable()
     *
     *     cube { ... }
     * }
     * ```
     */
    public val node: GroupNode get() = group

    /**
     * Creates a cube node and adds it as a child of this group.
     */
    public fun cube(
        size: Float = 0.1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CubeNode.() -> Unit = {},
    ): CubeNode = CubeNode(size, color, id).apply(configure).also { group.addChild(it) }

    /**
     * Creates a sphere node and adds it as a child of this group.
     */
    public fun sphere(
        radius: Float = 0.05f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: SphereNode.() -> Unit = {},
    ): SphereNode = SphereNode(radius, color, id).apply(configure).also { group.addChild(it) }

    /**
     * Creates a plane node and adds it as a child of this group.
     */
    public fun plane(
        width: Float = 1f,
        height: Float = 1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: PlaneNode.() -> Unit = {},
    ): PlaneNode = PlaneNode(width, height, color, id).apply(configure).also { group.addChild(it) }

    /**
     * Creates a cylinder node and adds it as a child of this group.
     */
    public fun cylinder(
        radius: Float = 0.05f,
        height: Float = 0.2f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CylinderNode.() -> Unit = {},
    ): CylinderNode = CylinderNode(radius, height, color, id).apply(configure).also { group.addChild(it) }

    /**
     * Creates a capsule node and adds it as a child of this group.
     */
    public fun capsule(
        radius: Float = 0.05f,
        height: Float = 0.2f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CapsuleNode.() -> Unit = {},
    ): CapsuleNode = CapsuleNode(radius, height, color, id).apply(configure).also { group.addChild(it) }

    /**
     * Creates a custom mesh node and adds it as a child of this group.
     */
    public fun customMesh(
        geometry: GeometryData,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CustomMeshNode.() -> Unit = {},
    ): CustomMeshNode = CustomMeshNode(geometry, color, id).apply(configure).also { group.addChild(it) }

    /**
     * Places a loaded glTF model as a child of this group.
     *
     * See [SpatialSceneScope.model] for loading and reuse semantics.
     */
    public fun model(
        model: GltfModel,
        id: String = SpatialNode.generateId(),
        configure: ModelNode.() -> Unit = {},
    ): ModelNode = ModelNode(model, id).apply(configure).also { group.addChild(it) }

    /**
     * Creates a nested group node.
     */
    public fun group(
        id: String = SpatialNode.generateId(),
        configure: GroupScope.() -> Unit,
    ): GroupNode {
        val nestedGroup = GroupNode(id)
        GroupScope(nestedGroup).configure()
        group.addChild(nestedGroup)
        return nestedGroup
    }
}
