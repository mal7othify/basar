package com.spatialkit.scene

import com.spatialkit.engine.SceneData

/**
 * A mutable scene graph for spatial applications.
 *
 * SpatialScene manages:
 * - Root-level spatial nodes
 * - Spatial anchors
 * - Conversion to immutable engine SceneData
 *
 * Thread-safe operations for concurrent access from user code.
 *
 * Usage:
 * ```kotlin
 * val scene = SpatialScene()
 *
 * // Add nodes
 * val cube = CubeNode(size = 0.1f, color = Color.RED)
 * cube.position = Vec3(0f, 0f, -1f)
 * scene.add(cube)
 *
 * // Create hierarchy
 * val group = GroupNode()
 * group.addChild(SphereNode(color = Color.BLUE))
 * scene.add(group)
 *
 * // Create anchor
 * val anchor = scene.createAnchor(AnchorType.World(...))
 *
 * // Convert to engine format
 * val sceneData = scene.toSceneData()
 * ```
 */
public class SpatialScene {
    // Synchronized collections for thread safety
    private val rootNodes = mutableListOf<SpatialNode>()
    private val anchors = mutableMapOf<String, SpatialAnchor>()
    private val lock = SpatialLock() // Synchronization lock

    /**
     * All root-level nodes in the scene.
     * Nodes with parents are not included here.
     * Returns a copy to prevent concurrent modification.
     */
    public val nodes: List<SpatialNode> get() = lock.withLock { rootNodes.toList() }

    /**
     * All anchors in the scene.
     * Returns a copy to prevent concurrent modification.
     */
    public val allAnchors: List<SpatialAnchor> get() = lock.withLock { anchors.values.toList() }

    /**
     * Add a root-level node to the scene.
     *
     * @param node Node to add
     */
    public fun add(node: SpatialNode) {
        lock.withLock {
            if (node !in rootNodes) {
                rootNodes.add(node)
            }
        }
    }

    /**
     * Remove a node from the scene.
     *
     * This removes the node and all its descendants from the scene.
     * If the node is a child of another node, it's removed from parent first.
     *
     * @param node Node to remove
     * @return true if node was removed
     */
    public fun remove(node: SpatialNode): Boolean {
        node.removeFromParent()
        return lock.withLock {
            rootNodes.remove(node)
        }
    }

    /**
     * Remove a node by ID.
     *
     * Searches all nodes (root and children) for matching ID.
     *
     * @param id Node ID to remove
     * @return true if node was found and removed
     */
    public fun removeById(id: String): Boolean {
        lock.withLock {
            // Check root nodes
            rootNodes.find { it.id == id }?.let {
                return remove(it)
            }

            // Check descendants
            rootNodes.forEach { root ->
                root.findNodeById(id)?.let { node ->
                    node.removeFromParent()
                    return true
                }
            }

            return false
        }
    }

    /**
     * Clear all nodes from the scene.
     */
    public fun clear() {
        lock.withLock {
            rootNodes.clear()
        }
    }

    /**
     * Find a node by ID anywhere in the scene.
     *
     * @param id Node ID to find
     * @return Node with matching ID or null
     */
    public fun findNodeById(id: String): SpatialNode? {
        rootNodes.forEach { root ->
            root.findNodeById(id)?.let { return it }
        }
        return null
    }

    /**
     * Get all nodes in the scene (root + all descendants).
     */
    public fun getAllNodes(): List<SpatialNode> =
        buildList {
            rootNodes.forEach { root ->
                add(root)
                addAll(root.getAllDescendants())
            }
        }

    /**
     * Create a spatial anchor.
     *
     * The anchor will be tracked and updated each frame.
     *
     * @param type Type of anchor to create
     * @param id Optional custom ID (auto-generated if not provided)
     * @return Created anchor
     */
    public fun createAnchor(
        type: com.spatialkit.engine.AnchorType,
        id: String = SpatialAnchor.generateId(),
    ): SpatialAnchor {
        lock.withLock {
            val anchor = SpatialAnchor(id, type)
            anchors[id] = anchor
            return anchor
        }
    }

    /**
     * Get an anchor by ID.
     *
     * @param id Anchor ID
     * @return Anchor or null if not found
     */
    public fun getAnchor(id: String): SpatialAnchor? = lock.withLock { anchors[id] }

    /**
     * Remove an anchor from the scene.
     *
     * @param id Anchor ID to remove
     * @return true if anchor was removed
     */
    public fun removeAnchor(id: String): Boolean = lock.withLock { anchors.remove(id) != null }

    /**
     * Remove an anchor from the scene.
     *
     * @param anchor Anchor to remove
     * @return true if anchor was removed
     */
    public fun removeAnchor(anchor: SpatialAnchor): Boolean = removeAnchor(anchor.id)

    /**
     * Update anchor states from engine frame data.
     *
     * Called internally by SpatialSession.
     */
    internal fun updateAnchors(anchorStates: List<com.spatialkit.engine.AnchorState>) {
        lock.withLock {
            anchorStates.forEach { state ->
                anchors[state.id]?.update(state)
            }
        }
    }

    /**
     * Convert this scene to engine SceneData format.
     *
     * This creates an immutable snapshot of the current scene state.
     * Called internally by SpatialSession each frame.
     */
    internal fun toSceneData(): SceneData =
        lock.withLock {
            SceneData(
                nodes = rootNodes.map { it.toSceneNode() },
                anchors = anchors.values.map { it.toAnchorRequest() },
            )
        }
}
