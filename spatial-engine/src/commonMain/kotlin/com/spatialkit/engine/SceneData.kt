package com.spatialkit.engine

import com.spatialkit.core.math.Transform

/**
 * Complete scene description submitted to the engine for rendering.
 *
 * This is the primary contract for telling the engine what to render.
 * Applications build a SceneData each frame (or when scene changes) and
 * submit it via SpatialEngine.submitScene().
 *
 * @property nodes All nodes to render (flat list, hierarchy via Group nodes)
 * @property anchors Anchor requests for this frame
 */
public data class SceneData(
    val nodes: List<SceneNode>,
    val anchors: List<AnchorRequest>,
)

/**
 * A node in the scene that can be rendered.
 *
 * Nodes are the fundamental unit of content. Each node has a transform
 * and visibility flag. The sealed interface ensures type safety.
 */
public sealed interface SceneNode {
    public val id: String
    public val transform: Transform
    public val visible: Boolean

    /**
     * A renderable mesh with geometry and material.
     */
    public data class Mesh(
        override val id: String,
        override val transform: Transform,
        override val visible: Boolean = true,
        val geometry: GeometryData,
        val material: MaterialData,
    ) : SceneNode

    /**
     * A group node that can contain children.
     *
     * Transform is applied to all children. Useful for hierarchies.
     */
    public data class Group(
        override val id: String,
        override val transform: Transform,
        override val visible: Boolean = true,
        val children: List<SceneNode>,
    ) : SceneNode
}

/**
 * Geometry definition as raw data (engine-agnostic).
 *
 * For primitive types, vertices/indices can be null - engine provides defaults.
 * For CUSTOM_MESH, all arrays must be provided.
 *
 * @property type The type of geometry (primitive or custom)
 * @property vertices Vertex positions (x,y,z triplets), null for primitives
 * @property indices Triangle indices, null for primitives
 * @property normals Normal vectors (x,y,z triplets), null for primitives
 * @property uvs Texture coordinates (u,v pairs), null if no texture
 */
public data class GeometryData(
    val type: GeometryType,
    val vertices: FloatArray? = null,
    val indices: IntArray? = null,
    val normals: FloatArray? = null,
    val uvs: FloatArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as GeometryData

        if (type != other.type) return false
        if (vertices != null) {
            if (other.vertices == null) return false
            if (!vertices.contentEquals(other.vertices)) return false
        } else if (other.vertices != null) {
            return false
        }
        if (indices != null) {
            if (other.indices == null) return false
            if (!indices.contentEquals(other.indices)) return false
        } else if (other.indices != null) {
            return false
        }
        if (normals != null) {
            if (other.normals == null) return false
            if (!normals.contentEquals(other.normals)) return false
        } else if (other.normals != null) {
            return false
        }
        if (uvs != null) {
            if (other.uvs == null) return false
            if (!uvs.contentEquals(other.uvs)) return false
        } else if (other.uvs != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (vertices?.contentHashCode() ?: 0)
        result = 31 * result + (indices?.contentHashCode() ?: 0)
        result = 31 * result + (normals?.contentHashCode() ?: 0)
        result = 31 * result + (uvs?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Supported geometry types.
 */
public enum class GeometryType {
    /** Unit cube (1x1x1), centered at origin */
    CUBE,

    /** Unit sphere (radius 1), centered at origin */
    SPHERE,

    /** Plane (1x1), facing up, centered at origin */
    PLANE,

    /** Cylinder (radius 0.5, height 1), centered at origin */
    CYLINDER,

    /** Capsule (radius 0.5, height 1), centered at origin */
    CAPSULE,

    /** Custom mesh with provided vertices/indices */
    CUSTOM_MESH,
}

/**
 * Material data for rendering.
 *
 * Simple material model that maps to most rendering backends.
 * Platform implementations convert to native materials.
 *
 * @property type Shading model to use
 * @property baseColor RGBA color (0-1 range)
 * @property metallic Metallic factor for PBR (0=dielectric, 1=metal)
 * @property roughness Roughness factor for PBR (0=smooth, 1=rough)
 * @property textureId Optional texture identifier (platform-specific)
 */
public data class MaterialData(
    val type: MaterialType,
    val baseColor: Vec4,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val textureId: String? = null,
)

/**
 * Material shading types.
 */
public enum class MaterialType {
    /** Unlit/emissive material (no lighting calculations) */
    UNLIT,

    /** Basic lit material (simple diffuse/specular) */
    LIT,

    /** Physically-Based Rendering (full PBR with metallic/roughness) */
    PBR,
}

// Transform is imported from com.spatialkit.core.math.Transform
