package com.spatialkit.scene

import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.Vec3
import kotlin.random.Random

/**
 * Base class for all nodes in the spatial scene graph.
 *
 * SpatialNode provides:
 * - Mutable transform properties (position, rotation, scale)
 * - Parent-child hierarchy management
 * - Automatic world-space transform computation
 * - Conversion to engine SceneNode format
 *
 * Usage:
 * ```kotlin
 * val cube = CubeNode(size = 0.1f, color = Color.RED)
 * cube.position = Vec3(0f, 0f, -1f)
 * cube.rotation = Quaternion.fromEuler(0f, 45f, 0f)
 *
 * val group = GroupNode()
 * group.addChild(cube)
 * ```
 */
public abstract class SpatialNode(
    public val id: String = generateId(),
) {
    /**
     * Local position relative to parent.
     * If no parent, this is world position.
     */
    public var position: Vec3 = Vec3.ZERO

    /**
     * Local rotation relative to parent.
     * If no parent, this is world rotation.
     */
    public var rotation: Quaternion = Quaternion.IDENTITY

    /**
     * Local scale relative to parent.
     * If no parent, this is world scale.
     */
    public var scale: Vec3 = Vec3.ONE

    /**
     * Whether this node is visible.
     * Invisible nodes are not rendered but still track.
     */
    public var visible: Boolean = true

    /**
     * Parent node in the scene graph.
     * null if this is a root node.
     */
    public var parent: SpatialNode? = null
        private set

    /**
     * Child nodes in the scene graph.
     * Children inherit this node's transform.
     */
    private val _children = mutableListOf<SpatialNode>()
    public val children: List<SpatialNode> get() = _children

    /**
     * World-space position (accumulated from parent chain).
     *
     * Applies the full parent transform: the local position is scaled by the
     * parent's world scale, rotated by the parent's world rotation, then offset
     * by the parent's world position — matching what renderers display.
     */
    public val worldPosition: Vec3
        get() {
            val p = parent
            return if (p != null) {
                val ps = p.worldScale
                val scaled = Vec3(position.x * ps.x, position.y * ps.y, position.z * ps.z)
                (p.worldRotation * scaled) + p.worldPosition
            } else {
                position
            }
        }

    /**
     * World-space rotation (accumulated from parent chain).
     */
    public val worldRotation: Quaternion
        get() {
            val p = parent
            return if (p != null) {
                p.worldRotation * rotation
            } else {
                rotation
            }
        }

    /**
     * World-space scale (accumulated from parent chain).
     */
    public val worldScale: Vec3
        get() {
            val p = parent
            return if (p != null) {
                Vec3(
                    p.worldScale.x * scale.x,
                    p.worldScale.y * scale.y,
                    p.worldScale.z * scale.z,
                )
            } else {
                scale
            }
        }

    /**
     * Add a child node to this node.
     * The child will inherit this node's transform.
     *
     * @param child Node to add as child
     * @throws IllegalArgumentException if [child] is this node or an ancestor
     *   of it (which would create a cycle in the scene graph)
     */
    public fun addChild(child: SpatialNode) {
        require(child !== this) { "Cannot add a node as a child of itself (id=$id)" }
        var ancestor = parent
        while (ancestor != null) {
            require(ancestor !== child) {
                "Cannot add ancestor node '${child.id}' as a child of '$id' (cycle)"
            }
            ancestor = ancestor.parent
        }

        // Remove from previous parent if any
        child.parent?.removeChild(child)

        child.parent = this
        _children.add(child)
    }

    /**
     * Remove a child node from this node.
     *
     * @param child Node to remove
     * @return true if child was removed
     */
    public fun removeChild(child: SpatialNode): Boolean {
        if (_children.remove(child)) {
            child.parent = null
            return true
        }
        return false
    }

    /**
     * Remove this node from its parent.
     */
    public fun removeFromParent() {
        parent?.removeChild(this)
    }

    /**
     * Get all descendant nodes (recursive).
     */
    public fun getAllDescendants(): List<SpatialNode> =
        buildList {
            _children.forEach { child ->
                add(child)
                addAll(child.getAllDescendants())
            }
        }

    /**
     * Find a descendant node by ID.
     *
     * @param id Node ID to find
     * @return Node with matching ID or null
     */
    public fun findNodeById(id: String): SpatialNode? {
        if (this.id == id) return this
        _children.forEach { child ->
            child.findNodeById(id)?.let { return it }
        }
        return null
    }

    /**
     * Convert this node to engine SceneNode format.
     *
     * Subclasses must implement this to provide their specific
     * geometry and material configuration.
     */
    internal abstract fun toSceneNode(): SceneNode

    public companion object {
        /**
         * Generate a unique node ID.
         *
         * Uses 64 bits of randomness: safe to call from any thread and the
         * collision probability is negligible.
         */
        public fun generateId(): String = "node-${Random.nextLong().toULong().toString(16)}"
    }
}
