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
abstract class SpatialNode(
    val id: String = generateId(),
) {
    /**
     * Local position relative to parent.
     * If no parent, this is world position.
     */
    var position: Vec3 = Vec3.ZERO

    /**
     * Local rotation relative to parent.
     * If no parent, this is world rotation.
     */
    var rotation: Quaternion = Quaternion.IDENTITY

    /**
     * Local scale relative to parent.
     * If no parent, this is world scale.
     */
    var scale: Vec3 = Vec3.ONE

    /**
     * Whether this node is visible.
     * Invisible nodes are not rendered but still track.
     */
    var visible: Boolean = true

    /**
     * Parent node in the scene graph.
     * null if this is a root node.
     */
    var parent: SpatialNode? = null
        private set

    /**
     * Child nodes in the scene graph.
     * Children inherit this node's transform.
     */
    private val _children = mutableListOf<SpatialNode>()
    val children: List<SpatialNode> get() = _children

    /**
     * World-space position (accumulated from parent chain).
     */
    val worldPosition: Vec3
        get() {
            val p = parent
            return if (p != null) {
                // Transform local position by parent's world transform
                (p.worldRotation * position) + p.worldPosition
            } else {
                position
            }
        }

    /**
     * World-space rotation (accumulated from parent chain).
     */
    val worldRotation: Quaternion
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
    val worldScale: Vec3
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
     */
    fun addChild(child: SpatialNode) {
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
    fun removeChild(child: SpatialNode): Boolean {
        if (_children.remove(child)) {
            child.parent = null
            return true
        }
        return false
    }

    /**
     * Remove this node from its parent.
     */
    fun removeFromParent() {
        parent?.removeChild(this)
    }

    /**
     * Get all descendant nodes (recursive).
     */
    fun getAllDescendants(): List<SpatialNode> =
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
    fun findNodeById(id: String): SpatialNode? {
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

    companion object {
        private var nodeCounter = 0

        /**
         * Generate a unique node ID.
         */
        fun generateId(): String = "node-${nodeCounter++}-${Random.nextInt(1000)}"
    }
}

/**
 * Extension to rotate a Vec3 by a Quaternion.
 */
fun Quaternion.rotate(v: Vec3): Vec3 {
    // Quaternion rotation: v' = q * v * q^-1
    // Optimized version without creating intermediate quaternions

    val qx = this.x
    val qy = this.y
    val qz = this.z
    val qw = this.w

    // First: q * v
    val ix = qw * v.x + qy * v.z - qz * v.y
    val iy = qw * v.y + qz * v.x - qx * v.z
    val iz = qw * v.z + qx * v.y - qy * v.x
    val iw = -qx * v.x - qy * v.y - qz * v.z

    // Second: result * q^-1 (conjugate for unit quaternion)
    return Vec3(
        x = ix * qw + iw * -qx + iy * -qz - iz * -qy,
        y = iy * qw + iw * -qy + iz * -qx - ix * -qz,
        z = iz * qw + iw * -qz + ix * -qy - iy * -qx,
    )
}
