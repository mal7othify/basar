package com.spatialkit.dsl

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.Color
import com.spatialkit.scene.CubeNode
import com.spatialkit.scene.GroupNode
import com.spatialkit.scene.SpatialAnchor
import com.spatialkit.scene.SpatialNode
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SphereNode

/**
 * DSL scope for building content attached to spatial anchors.
 *
 * Content created in this scope is positioned relative to the anchor: the
 * position/rotation set at creation time are treated as offsets from the
 * anchor's pose. Whenever tracking (re)starts, nodes are repositioned from
 * those stored offsets — losing and regaining tracking does not accumulate.
 */
@SpatialDsl
public class AnchorScope(
    private val anchor: SpatialAnchor,
    private val scene: SpatialScene,
) {
    /**
     * Local offset of a node relative to the anchor, captured when the node
     * is registered.
     */
    private class AnchorOffset(
        val node: SpatialNode,
        val position: Vec3,
        val rotation: Quaternion,
    )

    private val anchoredNodes = mutableListOf<AnchorOffset>()

    init {
        // Whenever the anchor (re)starts tracking, place nodes at the anchor
        // pose plus their captured local offset. Recomputing from the stored
        // offset keeps repeated LOST -> TRACKING transitions from drifting.
        anchor.onTrack {
            val pose = anchor.pose ?: return@onTrack
            anchoredNodes.forEach { entry ->
                entry.node.position = pose.position + (pose.rotation * entry.position)
                entry.node.rotation = pose.rotation * entry.rotation
            }
        }
    }

    private fun <T : SpatialNode> register(node: T): T {
        anchoredNodes.add(AnchorOffset(node, node.position, node.rotation))
        scene.add(node)
        return node
    }

    /**
     * Creates a cube node anchored to this spatial anchor.
     */
    public fun cube(
        size: Float = 0.1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CubeNode.() -> Unit = {},
    ): CubeNode = register(CubeNode(size, color, id).apply(configure))

    /**
     * Creates a sphere node anchored to this spatial anchor.
     */
    public fun sphere(
        radius: Float = 0.05f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: SphereNode.() -> Unit = {},
    ): SphereNode = register(SphereNode(radius, color, id).apply(configure))

    /**
     * Creates a group node anchored to this spatial anchor.
     */
    public fun group(
        id: String = SpatialNode.generateId(),
        configure: GroupScope.() -> Unit,
    ): GroupNode {
        val group = GroupNode(id)
        GroupScope(group).configure()
        return register(group)
    }
}
