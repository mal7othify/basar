package com.spatialkit.engine

import com.spatialkit.core.math.Transform

/**
 * Complete scene description submitted to the engine for rendering.
 *
 * This is the primary contract for telling the engine what to render.
 * Applications build a SceneData each frame (or when scene changes) and
 * submit it via SpatialEngine.submitScene().
 *
 * ## Rendering conventions (all platform renderers MUST follow these)
 *
 * These match glTF 2.0, so loader output renders identically everywhere:
 *
 * - **Coordinate system**: right-handed, +Y up, +X right, -Z forward
 *   (into the screen). Units are meters.
 * - **Winding order**: counter-clockwise triangles are front-facing.
 * - **UV origin**: (0,0) is the **top-left** of the texture image, V grows
 *   downward. Renderers that upload with a bottom-left GL convention must
 *   compensate (e.g. Three.js `flipY = false`) instead of rewriting UVs.
 * - **Color spaces**: [MaterialData.baseColor] and PBR factors are linear;
 *   [TextureData] base-color images are sRGB-encoded. Color-managed
 *   renderers (SceneKit, Three.js with `colorSpace = "srgb"`) decode
 *   accordingly; the minimal GLES renderer samples sRGB bytes directly,
 *   which is consistent with its non-color-managed output.
 * - **Base color × texture**: when both are present they multiply
 *   (glTF `baseColorFactor` semantics).
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
 * @property baseColor RGBA color (0-1 range); multiplied with [baseColorTexture] if present
 * @property metallic Metallic factor for PBR (0=dielectric, 1=metal)
 * @property roughness Roughness factor for PBR (0=smooth, 1=rough)
 * @property baseColorTexture Optional base-color texture, sampled with the mesh UVs
 * @property doubleSided Render both faces (disables back-face culling)
 */
public data class MaterialData(
    val type: MaterialType,
    val baseColor: Vec4,
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val baseColorTexture: TextureData? = null,
    val doubleSided: Boolean = false,
)

/**
 * A texture as encoded image bytes plus sampling settings.
 *
 * The image is kept in its encoded container format (PNG/JPEG) and decoded by
 * each platform renderer with its native image APIs. Renderers cache uploaded
 * textures by [id]: two [TextureData] instances with the same id are treated
 * as the same texture, so reuse one instance (or at least one id) when many
 * meshes share a texture.
 *
 * @property id Stable identifier used as the renderer-side cache key
 * @property data Encoded image bytes (PNG or JPEG)
 * @property mimeType MIME type of [data] ("image/png", "image/jpeg"), if known
 * @property sampler How the texture is sampled
 */
public class TextureData(
    public val id: String,
    public val data: ByteArray,
    public val mimeType: String? = null,
    public val sampler: TextureSampler = TextureSampler(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TextureData

        return id == other.id &&
            mimeType == other.mimeType &&
            sampler == other.sampler &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + sampler.hashCode()
        result = 31 * result + data.size
        return result
    }

    override fun toString(): String = "TextureData(id=$id, ${data.size} bytes, mimeType=$mimeType, sampler=$sampler)"
}

/**
 * Texture sampling settings.
 *
 * @property wrapU Wrapping along the U axis
 * @property wrapV Wrapping along the V axis
 * @property minFilter Filter used when the texture is minified
 * @property magFilter Filter used when the texture is magnified
 */
public data class TextureSampler(
    val wrapU: TextureWrap = TextureWrap.REPEAT,
    val wrapV: TextureWrap = TextureWrap.REPEAT,
    val minFilter: TextureFilter = TextureFilter.LINEAR,
    val magFilter: TextureFilter = TextureFilter.LINEAR,
)

/**
 * Texture coordinate wrapping modes.
 */
public enum class TextureWrap {
    /** Repeat the texture (tile) */
    REPEAT,

    /** Repeat, mirroring on each wrap */
    MIRRORED_REPEAT,

    /** Clamp to the edge pixel */
    CLAMP_TO_EDGE,
}

/**
 * Texture filtering modes.
 */
public enum class TextureFilter {
    /** Nearest-neighbor (blocky) */
    NEAREST,

    /** Linear interpolation (smooth); renderers may use mipmapped variants */
    LINEAR,
}

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
