package com.spatialkit.dsl

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
 * Content added in this scope is positioned relative to the anchor's
 * tracked position. The anchor's position updates as tracking updates.
 */
@SpatialDsl
public class AnchorScope(
    private val anchor: SpatialAnchor,
    private val scene: SpatialScene,
) {
    private val anchoredNodes = mutableListOf<SpatialNode>()

    init {
        // When anchor starts tracking, attach nodes
        anchor.onTrack {
            anchoredNodes.forEach { node ->
                if (anchor.pose != null) {
                    // Position nodes at anchor location
                    node.position = anchor.pose!!.position + node.position
                    node.rotation = anchor.pose!!.rotation * node.rotation
                }
            }
        }

        // When anchor loses tracking, could hide nodes
        anchor.onLost {
            // Optionally hide or remove nodes
        }
    }

    /**
     * Creates a cube node anchored to this spatial anchor.
     */
    public fun cube(
        size: Float = 0.1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CubeNode.() -> Unit = {},
    ): CubeNode =
        CubeNode(size, color, id).apply(configure).also {
            anchoredNodes.add(it)
            scene.add(it)
        }

    /**
     * Creates a sphere node anchored to this spatial anchor.
     */
    public fun sphere(
        radius: Float = 0.05f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: SphereNode.() -> Unit = {},
    ): SphereNode =
        SphereNode(radius, color, id).apply(configure).also {
            anchoredNodes.add(it)
            scene.add(it)
        }

    /**
     * Creates a group node anchored to this spatial anchor.
     */
    public fun group(
        id: String = SpatialNode.generateId(),
        configure: GroupScope.() -> Unit,
    ): GroupNode {
        val group = GroupNode(id)
        GroupScope(group).configure()
        anchoredNodes.add(group)
        scene.add(group)
        return group
    }
}
