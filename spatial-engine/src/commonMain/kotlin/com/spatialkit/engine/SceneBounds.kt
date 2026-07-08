package com.spatialkit.engine

/**
 * Axis-aligned bounding box.
 *
 * The space it lives in (mesh-local or world) is determined by the producing
 * call — see [SceneBounds].
 *
 * @property min Minimum corner
 * @property max Maximum corner
 */
public data class Aabb(
    val min: Vec3,
    val max: Vec3,
) {
    public val center: Vec3 get() = (min + max) * 0.5f
    public val size: Vec3 get() = max - min

    /** Smallest box containing both this box and [other]. */
    public fun union(other: Aabb): Aabb =
        Aabb(
            min =
                Vec3(
                    minOf(min.x, other.min.x),
                    minOf(min.y, other.min.y),
                    minOf(min.z, other.min.z),
                ),
            max =
                Vec3(
                    maxOf(max.x, other.max.x),
                    maxOf(max.y, other.max.y),
                    maxOf(max.z, other.max.z),
                ),
        )
}

/**
 * CPU bounds computation for engine [SceneData] content.
 *
 * Used by renderers to size and place grounding effects (M9.2 blob shadows)
 * under nodes flagged with [SceneNode.castsShadow]. Follows the same unit
 * primitive conventions as [SceneRaycaster]: every primitive fits the
 * ±0.5 unit cube, and custom meshes are bounded by their vertices.
 *
 * Pure functions of their input: safe from any thread as long as the caller
 * snapshots the node list (SceneData is immutable).
 */
public object SceneBounds {
    /**
     * Local-space bounds of a mesh geometry (before its node transform).
     *
     * Returns null for a custom mesh without vertices. Custom meshes are
     * scanned vertex-by-vertex — renderers that call this per frame should
     * cache the result per node (geometry is immutable per node revision).
     */
    public fun localBounds(geometry: GeometryData): Aabb? =
        when (geometry.type) {
            GeometryType.CUBE,
            GeometryType.SPHERE,
            GeometryType.CYLINDER,
            GeometryType.CAPSULE,
            -> UNIT_BOUNDS

            // Unit plane spans XZ at y = 0
            GeometryType.PLANE -> Aabb(Vec3(-0.5f, 0f, -0.5f), Vec3(0.5f, 0f, 0.5f))

            GeometryType.CUSTOM_MESH -> {
                val vertices = geometry.vertices
                if (vertices == null || vertices.size < 3) {
                    null
                } else {
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var minZ = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    var maxZ = -Float.MAX_VALUE
                    var i = 0
                    while (i + 2 < vertices.size) {
                        val x = vertices[i]
                        val y = vertices[i + 1]
                        val z = vertices[i + 2]
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (z < minZ) minZ = z
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                        if (z > maxZ) maxZ = z
                        i += 3
                    }
                    Aabb(Vec3(minX, minY, minZ), Vec3(maxX, maxY, maxZ))
                }
            }
        }

    /**
     * World-space AABB of a node subtree, or null if it contains no visible
     * mesh with bounds.
     *
     * Points are transformed level by level through the TRS chain (exact for
     * non-uniform scale under rotation, unlike composing TRS triples), so the
     * result matches what renderers display.
     *
     * @param node Subtree root, in world space (i.e. a root-level scene node)
     * @param localBoundsOf Local bounds provider for meshes; override to
     *   supply cached values for custom meshes (defaults to [localBounds])
     */
    public fun worldBounds(
        node: SceneNode,
        localBoundsOf: (SceneNode.Mesh) -> Aabb? = { localBounds(it.geometry) },
    ): Aabb? = accumulate(node, localBoundsOf) { it }

    /**
     * Finds the topmost [SceneNode.castsShadow] nodes in [nodes] and returns
     * each with the world-space AABB of its subtree (M9.2).
     *
     * Renderers draw one blob shadow per returned entry, at the base of the
     * AABB, sized to its XZ footprint. Flags nested inside an already flagged
     * subtree are ignored (one shadow per subtree), and invisible nodes are
     * skipped entirely.
     *
     * @param nodes Root-level scene content (world space)
     * @param localBoundsOf Local bounds provider for meshes (see [worldBounds])
     */
    public fun shadowCasters(
        nodes: List<SceneNode>,
        localBoundsOf: (SceneNode.Mesh) -> Aabb? = { localBounds(it.geometry) },
    ): List<Pair<SceneNode, Aabb>> {
        val result = mutableListOf<Pair<SceneNode, Aabb>>()

        fun walk(
            nodes: List<SceneNode>,
            toWorld: (Vec3) -> Vec3,
        ) {
            nodes.forEach { node ->
                if (!node.visible) return@forEach
                if (node.castsShadow) {
                    accumulate(node, localBoundsOf, toWorld)?.let { result.add(node to it) }
                    return@forEach
                }
                if (node is SceneNode.Group) {
                    val t = node.transform
                    walk(node.children) { p -> toWorld(t.rotation * (p * t.scale) + t.position) }
                }
            }
        }
        walk(nodes) { it }
        return result
    }

    private fun accumulate(
        node: SceneNode,
        localBoundsOf: (SceneNode.Mesh) -> Aabb?,
        toWorld: (Vec3) -> Vec3,
    ): Aabb? {
        if (!node.visible) return null
        val t = node.transform
        val toParentWorld: (Vec3) -> Vec3 = { p -> toWorld(t.rotation * (p * t.scale) + t.position) }

        return when (node) {
            is SceneNode.Mesh -> {
                val local = localBoundsOf(node) ?: return null
                var result: Aabb? = null
                for (corner in 0 until 8) {
                    val p =
                        toParentWorld(
                            Vec3(
                                if (corner and 1 == 0) local.min.x else local.max.x,
                                if (corner and 2 == 0) local.min.y else local.max.y,
                                if (corner and 4 == 0) local.min.z else local.max.z,
                            ),
                        )
                    val point = Aabb(p, p)
                    result = result?.union(point) ?: point
                }
                result
            }

            is SceneNode.Group -> {
                var result: Aabb? = null
                node.children.forEach { child ->
                    val childBounds = accumulate(child, localBoundsOf, toParentWorld) ?: return@forEach
                    result = result?.union(childBounds) ?: childBounds
                }
                result
            }
        }
    }

    private val UNIT_BOUNDS = Aabb(Vec3(-0.5f, -0.5f, -0.5f), Vec3(0.5f, 0.5f, 0.5f))
}
