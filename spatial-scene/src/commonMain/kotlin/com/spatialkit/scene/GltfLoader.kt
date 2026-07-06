package com.spatialkit.scene

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Transform
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.Color
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.TextureData
import com.spatialkit.engine.TextureFilter
import com.spatialkit.engine.TextureSampler
import com.spatialkit.engine.TextureWrap
import com.spatialkit.engine.generateVertexNormals
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.sqrt

/**
 * Thrown when a glTF/GLB asset cannot be parsed.
 *
 * The message describes what is wrong with the asset (or which glTF feature
 * is not supported) so failures are actionable.
 */
public class GltfParseException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Loads glTF 2.0 assets (`.gltf` JSON with embedded buffers, or binary `.glb`).
 *
 * Supported:
 * - GLB container (JSON + BIN chunk) and glTF JSON with base64 data-URI buffers
 * - Triangle meshes: POSITION, NORMAL, TEXCOORD_0, indices
 *   (ubyte/ushort/uint), respecting accessor `byteOffset` and bufferView
 *   `byteStride` (interleaved layouts)
 * - PBR metallic-roughness materials: `baseColorFactor`, `baseColorTexture`
 *   (PNG/JPEG from a bufferView or data URI), `metallicFactor`,
 *   `roughnessFactor`, `doubleSided`, sampler wrap/filter modes
 * - Node hierarchies with TRS or matrix transforms
 *
 * Not supported (throws [GltfParseException] where relevant): external file
 * URIs, non-triangle primitive modes, Draco/quantized attributes, animations,
 * skins, morph targets, and glTF extensions (unknown extensions are ignored).
 *
 * Usage:
 * ```kotlin
 * val model = GltfLoader.load(glbBytes)
 * scene.add(model.toNode())
 * ```
 */
public object GltfLoader {
    private val json = Json { ignoreUnknownKeys = true }

    private const val GLB_MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A // "JSON"
    private const val CHUNK_BIN = 0x004E4942 // "BIN\0"

    /**
     * Loads a model from GLB bytes or UTF-8 glTF JSON bytes (auto-detected).
     *
     * @throws GltfParseException if the asset is malformed or uses an
     *   unsupported feature
     */
    public fun load(bytes: ByteArray): GltfModel {
        if (bytes.size >= 4 && readUInt32LE(bytes, 0) == GLB_MAGIC) {
            return parseGlb(bytes)
        }
        return parse(bytes.decodeToString(), binChunk = null)
    }

    /**
     * Loads a model from glTF JSON. Buffers must be embedded as base64 data
     * URIs (external buffer files are not supported).
     *
     * @throws GltfParseException if the asset is malformed or uses an
     *   unsupported feature
     */
    public fun load(gltfJson: String): GltfModel = parse(gltfJson, binChunk = null)

    // ------------------------------------------------------------------
    // GLB container
    // ------------------------------------------------------------------

    private fun parseGlb(bytes: ByteArray): GltfModel {
        if (bytes.size <
            12
        ) {
            throw GltfParseException("GLB truncated: ${bytes.size} bytes is smaller than the 12-byte header")
        }

        val version = readUInt32LE(bytes, 4)
        if (version !=
            2
        ) {
            throw GltfParseException("Unsupported GLB container version $version (only version 2 is supported)")
        }

        val declaredLength = readUInt32LE(bytes, 8)
        if (declaredLength > bytes.size) {
            throw GltfParseException(
                "GLB truncated: header declares $declaredLength bytes but only ${bytes.size} are present",
            )
        }

        var offset = 12
        var jsonChunk: ByteArray? = null
        var binChunk: ByteArray? = null

        while (offset + 8 <= declaredLength) {
            val chunkLength = readUInt32LE(bytes, offset)
            val chunkType = readUInt32LE(bytes, offset + 4)
            val dataStart = offset + 8
            if (chunkLength < 0 || dataStart + chunkLength > declaredLength) {
                throw GltfParseException("GLB chunk at offset $offset overruns the file (chunk length $chunkLength)")
            }
            when (chunkType) {
                CHUNK_JSON -> jsonChunk = bytes.copyOfRange(dataStart, dataStart + chunkLength)
                CHUNK_BIN -> binChunk = bytes.copyOfRange(dataStart, dataStart + chunkLength)
                else -> Unit // unknown chunk types are skipped per spec
            }
            offset = dataStart + chunkLength
        }

        val jsonBytes = jsonChunk ?: throw GltfParseException("GLB has no JSON chunk")
        return parse(jsonBytes.decodeToString(), binChunk)
    }

    // ------------------------------------------------------------------
    // Document parsing
    // ------------------------------------------------------------------

    private fun parse(
        gltfJson: String,
        binChunk: ByteArray?,
    ): GltfModel {
        val doc =
            try {
                json.decodeFromString<GltfDocument>(gltfJson)
            } catch (e: SerializationException) {
                throw GltfParseException("Not valid glTF JSON: ${e.message}", e)
            } catch (e: IllegalArgumentException) {
                throw GltfParseException("Not valid glTF JSON: ${e.message}", e)
            }

        val assetVersion = doc.asset?.version
        if (assetVersion == null || !assetVersion.startsWith("2.")) {
            throw GltfParseException("Unsupported glTF version '$assetVersion' (only 2.x is supported)")
        }

        val context = ParseContext(doc, resolveBuffers(doc, binChunk))
        val meshes = doc.meshes.map { mesh -> mesh.primitives.map { context.readPrimitive(it) } }

        val roots = buildHierarchy(doc, meshes)
        return GltfModel(roots)
    }

    private fun resolveBuffers(
        doc: GltfDocument,
        binChunk: ByteArray?,
    ): List<ByteArray> =
        doc.buffers.mapIndexed { index, buffer ->
            val uri = buffer.uri
            when {
                uri == null -> {
                    if (index == 0 && binChunk != null) {
                        binChunk
                    } else {
                        throw GltfParseException("Buffer $index has no URI and there is no GLB BIN chunk to satisfy it")
                    }
                }
                uri.startsWith("data:") -> decodeDataUri(uri, what = "buffer $index").bytes
                else -> throw GltfParseException(
                    "Buffer $index references an external file ('$uri'); " +
                        "external URIs are not supported — use GLB or embedded base64 buffers",
                )
            }
        }

    private class DecodedDataUri(
        val bytes: ByteArray,
        val mimeType: String?,
    )

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeDataUri(
        uri: String,
        what: String,
    ): DecodedDataUri {
        val comma = uri.indexOf(',')
        if (comma == -1) throw GltfParseException("Malformed data URI for $what")
        val header = uri.substring(5, comma) // strip "data:"
        if (!header.endsWith(";base64")) {
            throw GltfParseException("Data URI for $what is not base64-encoded")
        }
        val mimeType = header.removeSuffix(";base64").ifEmpty { null }
        val bytes =
            try {
                Base64.decode(uri, startIndex = comma + 1)
            } catch (e: IllegalArgumentException) {
                throw GltfParseException("Invalid base64 data for $what: ${e.message}", e)
            }
        return DecodedDataUri(bytes, mimeType)
    }

    // ------------------------------------------------------------------
    // Accessor / primitive reading
    // ------------------------------------------------------------------

    private class ParseContext(
        val doc: GltfDocument,
        val buffers: List<ByteArray>,
    ) {
        /** TextureData per glTF texture index, so shared textures are one instance. */
        private val textureCache = mutableMapOf<Int, TextureData>()

        fun readPrimitive(primitive: GltfPrimitive): MeshData {
            if (primitive.mode != 4) {
                throw GltfParseException(
                    "Unsupported primitive mode ${primitive.mode}; only TRIANGLES (4) is supported",
                )
            }

            val positionAccessor =
                primitive.attributes["POSITION"]
                    ?: throw GltfParseException("Mesh primitive has no POSITION attribute")

            val vertices = readFloatAccessor(positionAccessor, "VEC3", "POSITION")
            val normals = primitive.attributes["NORMAL"]?.let { readFloatAccessor(it, "VEC3", "NORMAL") }
            val uvs = primitive.attributes["TEXCOORD_0"]?.let { readFloatAccessor(it, "VEC2", "TEXCOORD_0") }
            val indices = primitive.indices?.let { readIndexAccessor(it) }

            val geometry =
                GeometryData(
                    type = GeometryType.CUSTOM_MESH,
                    vertices = vertices,
                    // Minimal exporters omit NORMAL; generate normals so the
                    // mesh is lit correctly on every renderer.
                    normals = normals ?: indices?.let { generateVertexNormals(vertices, it) },
                    indices = indices,
                    uvs = uvs,
                )

            return MeshData(geometry, readMaterial(primitive.material))
        }

        private fun accessor(index: Int): GltfAccessor =
            doc.accessors.getOrNull(index)
                ?: throw GltfParseException("Accessor $index does not exist (file has ${doc.accessors.size})")

        private class AccessorView(
            val bytes: ByteArray,
            val start: Int,
            val stride: Int,
        )

        /**
         * Resolves an accessor to its backing bytes, honoring accessor
         * byteOffset and bufferView byteStride, and bounds-checks the last
         * element.
         */
        private fun view(
            acc: GltfAccessor,
            elementSize: Int,
            label: String,
        ): AccessorView {
            val bufferViewIndex =
                acc.bufferView
                    ?: throw GltfParseException(
                        "Accessor for $label has no bufferView (sparse accessors are not supported)",
                    )
            val bufferView =
                doc.bufferViews.getOrNull(bufferViewIndex)
                    ?: throw GltfParseException(
                        "BufferView $bufferViewIndex does not exist (file has ${doc.bufferViews.size})",
                    )
            val buffer =
                buffers.getOrNull(bufferView.buffer)
                    ?: throw GltfParseException("Buffer ${bufferView.buffer} does not exist (file has ${buffers.size})")

            val stride = bufferView.byteStride ?: elementSize
            val start = bufferView.byteOffset + acc.byteOffset
            val end = start + (acc.count - 1) * stride + elementSize
            val viewEnd = bufferView.byteOffset + bufferView.byteLength
            if (acc.count > 0 && (end > viewEnd || end > buffer.size)) {
                throw GltfParseException(
                    "$label accessor overruns its buffer view " +
                        "(needs bytes $start..${end - 1}, view ends at ${viewEnd - 1}, buffer has ${buffer.size})",
                )
            }
            return AccessorView(buffer, start, stride)
        }

        fun readFloatAccessor(
            accessorIndex: Int,
            expectedType: String,
            label: String,
        ): FloatArray {
            val acc = accessor(accessorIndex)
            if (acc.type != expectedType) {
                throw GltfParseException("$label accessor has type ${acc.type}, expected $expectedType")
            }
            if (acc.componentType != COMPONENT_FLOAT) {
                throw GltfParseException(
                    "$label accessor has componentType ${acc.componentType}; " +
                        "only FLOAT (5126) attributes are supported",
                )
            }

            val components = if (expectedType == "VEC2") 2 else 3
            val v = view(acc, elementSize = components * 4, label = label)

            val out = FloatArray(acc.count * components)
            for (i in 0 until acc.count) {
                val base = v.start + i * v.stride
                for (c in 0 until components) {
                    out[i * components + c] = readFloatLE(v.bytes, base + c * 4)
                }
            }
            return out
        }

        fun readIndexAccessor(accessorIndex: Int): IntArray {
            val acc = accessor(accessorIndex)
            if (acc.type != "SCALAR") {
                throw GltfParseException("Index accessor has type ${acc.type}, expected SCALAR")
            }
            val elementSize =
                when (acc.componentType) {
                    COMPONENT_UBYTE -> 1
                    COMPONENT_USHORT -> 2
                    COMPONENT_UINT -> 4
                    else -> throw GltfParseException(
                        "Index accessor has componentType ${acc.componentType}; " +
                            "expected UNSIGNED_BYTE (5121), UNSIGNED_SHORT (5123) or UNSIGNED_INT (5125)",
                    )
                }
            val v = view(acc, elementSize, label = "indices")

            val out = IntArray(acc.count)
            for (i in 0 until acc.count) {
                val base = v.start + i * v.stride
                out[i] =
                    when (elementSize) {
                        1 -> v.bytes[base].toInt() and 0xFF
                        2 -> readUShortLE(v.bytes, base)
                        else -> readUInt32LE(v.bytes, base)
                    }
            }
            return out
        }

        // --------------------------------------------------------------
        // Materials & textures
        // --------------------------------------------------------------

        fun readMaterial(materialIndex: Int?): MaterialInfo {
            val material = materialIndex?.let { doc.materials.getOrNull(it) } ?: return MaterialInfo()
            val pbr = material.pbrMetallicRoughness ?: GltfPbr()

            val factor = pbr.baseColorFactor
            if (factor != null && factor.size != 4) {
                throw GltfParseException("baseColorFactor must have 4 components, got ${factor.size}")
            }

            return MaterialInfo(
                color = factor?.let { Color(it[0], it[1], it[2], it[3]) } ?: Color.White,
                metallic = pbr.metallicFactor,
                roughness = pbr.roughnessFactor,
                doubleSided = material.doubleSided,
                texture = pbr.baseColorTexture?.let { readTexture(it) },
            )
        }

        private fun readTexture(ref: GltfTextureRef): TextureData {
            if (ref.texCoord != 0) {
                throw GltfParseException("Only TEXCOORD_0 is supported (texture uses texCoord ${ref.texCoord})")
            }
            return textureCache.getOrPut(ref.index) {
                val texture =
                    doc.textures.getOrNull(ref.index)
                        ?: throw GltfParseException(
                            "Texture ${ref.index} does not exist (file has ${doc.textures.size})",
                        )
                val imageIndex =
                    texture.source
                        ?: throw GltfParseException("Texture ${ref.index} has no image source")
                val image =
                    doc.images.getOrNull(imageIndex)
                        ?: throw GltfParseException("Image $imageIndex does not exist (file has ${doc.images.size})")

                val (bytes, mimeType) = readImageBytes(image, imageIndex)
                TextureData(
                    id = "gltf-tex-$imageIndex-${contentHash(bytes)}",
                    data = bytes,
                    mimeType = mimeType,
                    sampler = texture.sampler?.let { samplerFor(it) } ?: TextureSampler(),
                )
            }
        }

        private fun readImageBytes(
            image: GltfImage,
            imageIndex: Int,
        ): Pair<ByteArray, String?> {
            val uri = image.uri
            return when {
                image.bufferView != null -> {
                    val bufferView =
                        doc.bufferViews.getOrNull(image.bufferView)
                            ?: throw GltfParseException(
                                "Image $imageIndex bufferView ${image.bufferView} does not exist",
                            )
                    val buffer =
                        buffers.getOrNull(bufferView.buffer)
                            ?: throw GltfParseException(
                                "Image $imageIndex references missing buffer ${bufferView.buffer}",
                            )
                    val start = bufferView.byteOffset
                    val end = start + bufferView.byteLength
                    if (end > buffer.size) {
                        throw GltfParseException("Image $imageIndex bufferView overruns its buffer")
                    }
                    buffer.copyOfRange(start, end) to image.mimeType
                }
                uri != null && uri.startsWith("data:") -> {
                    val decoded = decodeDataUri(uri, what = "image $imageIndex")
                    decoded.bytes to (image.mimeType ?: decoded.mimeType)
                }
                uri != null -> throw GltfParseException(
                    "Image $imageIndex references an external file ('$uri'); " +
                        "external URIs are not supported — use GLB or embedded base64 images",
                )
                else -> throw GltfParseException("Image $imageIndex has neither a bufferView nor a URI")
            }
        }

        private fun samplerFor(samplerIndex: Int): TextureSampler {
            val sampler =
                doc.samplers.getOrNull(samplerIndex)
                    ?: throw GltfParseException("Sampler $samplerIndex does not exist (file has ${doc.samplers.size})")
            return TextureSampler(
                wrapU = wrapMode(sampler.wrapS),
                wrapV = wrapMode(sampler.wrapT),
                minFilter = filterMode(sampler.minFilter),
                magFilter = filterMode(sampler.magFilter),
            )
        }

        private fun wrapMode(gl: Int): TextureWrap =
            when (gl) {
                33071 -> TextureWrap.CLAMP_TO_EDGE
                33648 -> TextureWrap.MIRRORED_REPEAT
                else -> TextureWrap.REPEAT // 10497 and default
            }

        private fun filterMode(gl: Int?): TextureFilter =
            when (gl) {
                // NEAREST and the *_MIPMAP_* variants whose base filter is NEAREST
                9728, 9984, 9986 -> TextureFilter.NEAREST
                else -> TextureFilter.LINEAR
            }
    }

    // ------------------------------------------------------------------
    // Hierarchy
    // ------------------------------------------------------------------

    private fun buildHierarchy(
        doc: GltfDocument,
        meshes: List<List<MeshData>>,
    ): List<NodeData> {
        fun buildNode(
            index: Int,
            ancestry: Set<Int>,
        ): NodeData {
            if (index in ancestry) throw GltfParseException("Node $index is part of a cycle in the node hierarchy")
            val node =
                doc.nodes.getOrNull(index)
                    ?: throw GltfParseException("Node $index does not exist (file has ${doc.nodes.size})")
            val nodeMeshes =
                node.mesh?.let {
                    meshes.getOrNull(it)
                        ?: throw GltfParseException("Node $index references missing mesh ${node.mesh}")
                } ?: emptyList()
            return NodeData(
                name = node.name,
                transform = node.toTransform(),
                meshes = nodeMeshes,
                children = node.children.map { buildNode(it, ancestry + index) },
            )
        }

        val rootIndices =
            when {
                doc.scenes.isNotEmpty() -> doc.scenes.getOrNull(doc.scene ?: 0)?.nodes ?: emptyList()
                doc.nodes.isNotEmpty() -> {
                    // No scenes declared: treat nodes that are nobody's child as roots
                    val childIndices = doc.nodes.flatMap { it.children }.toSet()
                    doc.nodes.indices.filter { it !in childIndices }
                }
                else -> emptyList()
            }

        if (rootIndices.isNotEmpty()) {
            return rootIndices.map { buildNode(it, emptySet()) }
        }

        // Files with meshes but no node graph (common in minimal assets):
        // expose each mesh as a root.
        return meshes.map { NodeData(name = null, transform = Transform(), meshes = it, children = emptyList()) }
    }

    private fun GltfNode.toTransform(): Transform {
        matrix?.let { m ->
            if (m.size != 16) throw GltfParseException("Node matrix must have 16 elements, got ${m.size}")
            return decomposeMatrix(m)
        }
        val t = translation
        val r = rotation
        val s = scale
        if (t != null && t.size != 3) throw GltfParseException("Node translation must have 3 elements, got ${t.size}")
        if (r != null && r.size != 4) throw GltfParseException("Node rotation must have 4 elements, got ${r.size}")
        if (s != null && s.size != 3) throw GltfParseException("Node scale must have 3 elements, got ${s.size}")
        return Transform(
            position = t?.let { Vec3(it[0], it[1], it[2]) } ?: Vec3.ZERO,
            rotation = r?.let { Quaternion(it[0], it[1], it[2], it[3]).normalized() } ?: Quaternion.IDENTITY,
            scale = s?.let { Vec3(it[0], it[1], it[2]) } ?: Vec3.ONE,
        )
    }

    /** Decomposes a column-major 4x4 TRS matrix into a [Transform]. */
    private fun decomposeMatrix(m: List<Float>): Transform {
        val position = Vec3(m[12], m[13], m[14])

        val sx = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
        val sy = sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6])
        val sz = sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10])
        if (sx == 0f || sy == 0f || sz == 0f) {
            throw GltfParseException("Node matrix has a zero scale component and cannot be decomposed")
        }

        // Rotation matrix columns with scale removed
        val r00 = m[0] / sx
        val r01 = m[1] / sx
        val r02 = m[2] / sx
        val r10 = m[4] / sy
        val r11 = m[5] / sy
        val r12 = m[6] / sy
        val r20 = m[8] / sz
        val r21 = m[9] / sz
        val r22 = m[10] / sz

        // Standard rotation-matrix → quaternion conversion. Note the r{c}{r}
        // naming above is column-row (glTF matrices are column-major).
        val trace = r00 + r11 + r22
        val q =
            if (trace > 0f) {
                val s = sqrt(trace + 1f) * 2f
                Quaternion(
                    x = (r12 - r21) / s,
                    y = (r20 - r02) / s,
                    z = (r01 - r10) / s,
                    w = s / 4f,
                )
            } else if (r00 > r11 && r00 > r22) {
                val s = sqrt(1f + r00 - r11 - r22) * 2f
                Quaternion(
                    x = s / 4f,
                    y = (r10 + r01) / s,
                    z = (r20 + r02) / s,
                    w = (r12 - r21) / s,
                )
            } else if (r11 > r22) {
                val s = sqrt(1f + r11 - r00 - r22) * 2f
                Quaternion(
                    x = (r10 + r01) / s,
                    y = s / 4f,
                    z = (r21 + r12) / s,
                    w = (r20 - r02) / s,
                )
            } else {
                val s = sqrt(1f + r22 - r00 - r11) * 2f
                Quaternion(
                    x = (r20 + r02) / s,
                    y = (r21 + r12) / s,
                    z = s / 4f,
                    w = (r01 - r10) / s,
                )
            }

        return Transform(position, q.normalized(), Vec3(sx, sy, sz))
    }

    // ------------------------------------------------------------------
    // Byte helpers (little-endian, as required by glTF)
    // ------------------------------------------------------------------

    private fun readUInt32LE(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun readUShortLE(
        bytes: ByteArray,
        offset: Int,
    ): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readFloatLE(
        bytes: ByteArray,
        offset: Int,
    ): Float = Float.fromBits(readUInt32LE(bytes, offset))

    /** FNV-1a over the bytes; cheap stable content hash for texture cache ids. */
    private fun contentHash(bytes: ByteArray): String {
        var hash = -0x340d631b7bdddcdbL // FNV offset basis
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xFF)
            hash *= 0x100000001b3L // FNV prime
        }
        return hash.toULong().toString(16)
    }

    private const val COMPONENT_UBYTE = 5121
    private const val COMPONENT_USHORT = 5123
    private const val COMPONENT_FLOAT = 5126
    private const val COMPONENT_UINT = 5125
}

// ----------------------------------------------------------------------
// Loaded model
// ----------------------------------------------------------------------

/**
 * A loaded glTF model: an immutable description that can be instantiated into
 * scene nodes any number of times.
 */
public class GltfModel internal constructor(
    private val roots: List<NodeData>,
) {
    /** Total number of mesh primitives in the model. */
    public val meshCount: Int = countMeshes(roots)

    /**
     * Instantiates the model as a scene-graph node.
     *
     * Each call creates fresh nodes, so the same model can be placed in a
     * scene multiple times. Mesh geometry and textures are shared between
     * instances (they are immutable); node transforms are per-instance.
     *
     * @param id Id for the root node
     */
    public fun toNode(id: String = SpatialNode.generateId()): ModelNode = ModelNode(this, id)

    /** Fresh scene-graph nodes for the model's root hierarchy. */
    internal fun instantiateChildren(): List<SpatialNode> = roots.map { it.instantiate() }

    /**
     * The geometry of the first mesh primitive, or null if the model has no
     * meshes. Convenience for single-mesh assets.
     */
    public fun firstGeometry(): GeometryData? = firstMesh(roots)?.geometry

    private fun countMeshes(nodes: List<NodeData>): Int = nodes.sumOf { it.meshes.size + countMeshes(it.children) }

    private fun firstMesh(nodes: List<NodeData>): MeshData? {
        for (node in nodes) {
            node.meshes.firstOrNull()?.let { return it }
            firstMesh(node.children)?.let { return it }
        }
        return null
    }
}

/** One mesh primitive: geometry plus its resolved material. */
internal class MeshData(
    val geometry: GeometryData,
    val material: MaterialInfo,
)

/** Resolved glTF PBR material values, in engine terms. */
internal class MaterialInfo(
    val color: Color = Color.White,
    val metallic: Float = 1f,
    val roughness: Float = 1f,
    val doubleSided: Boolean = false,
    val texture: TextureData? = null,
)

/** One node in the loaded hierarchy. */
internal class NodeData(
    val name: String?,
    val transform: Transform,
    val meshes: List<MeshData>,
    val children: List<NodeData>,
) {
    fun instantiate(): SpatialNode {
        // A node with exactly one mesh and no children collapses to a single
        // CustomMeshNode; anything else becomes a group.
        val single = meshes.size == 1 && children.isEmpty()
        if (single) {
            return meshNode(meshes[0]).also(::applyTransform)
        }

        val group = GroupNode()
        meshes.forEach { group.addChild(meshNode(it)) }
        children.forEach { group.addChild(it.instantiate()) }
        applyTransform(group)
        return group
    }

    private fun applyTransform(node: SpatialNode) {
        node.position = transform.position
        node.rotation = transform.rotation
        node.scale = transform.scale
    }

    private fun meshNode(mesh: MeshData): CustomMeshNode =
        CustomMeshNode(mesh.geometry).apply {
            color = mesh.material.color
            metallic = mesh.material.metallic
            roughness = mesh.material.roughness
            doubleSided = mesh.material.doubleSided
            baseColorTexture = mesh.material.texture
        }
}

// ----------------------------------------------------------------------
// glTF 2.0 JSON schema (the subset we read)
// ----------------------------------------------------------------------

@Serializable
internal class GltfDocument(
    val asset: GltfAsset? = null,
    val scene: Int? = null,
    val scenes: List<GltfScene> = emptyList(),
    val nodes: List<GltfNode> = emptyList(),
    val meshes: List<GltfMesh> = emptyList(),
    val accessors: List<GltfAccessor> = emptyList(),
    val bufferViews: List<GltfBufferView> = emptyList(),
    val buffers: List<GltfBuffer> = emptyList(),
    val materials: List<GltfMaterial> = emptyList(),
    val textures: List<GltfTexture> = emptyList(),
    val images: List<GltfImage> = emptyList(),
    val samplers: List<GltfSampler> = emptyList(),
)

@Serializable
internal class GltfAsset(
    val version: String? = null,
)

@Serializable
internal class GltfScene(
    val nodes: List<Int> = emptyList(),
)

@Serializable
internal class GltfNode(
    val name: String? = null,
    val mesh: Int? = null,
    val children: List<Int> = emptyList(),
    val translation: List<Float>? = null,
    val rotation: List<Float>? = null,
    val scale: List<Float>? = null,
    val matrix: List<Float>? = null,
)

@Serializable
internal class GltfMesh(
    val primitives: List<GltfPrimitive> = emptyList(),
)

@Serializable
internal class GltfPrimitive(
    val attributes: Map<String, Int> = emptyMap(),
    val indices: Int? = null,
    val material: Int? = null,
    val mode: Int = 4,
)

@Serializable
internal class GltfAccessor(
    val bufferView: Int? = null,
    val byteOffset: Int = 0,
    val componentType: Int,
    val count: Int,
    val type: String,
)

@Serializable
internal class GltfBufferView(
    val buffer: Int,
    val byteOffset: Int = 0,
    val byteLength: Int,
    val byteStride: Int? = null,
)

@Serializable
internal class GltfBuffer(
    val uri: String? = null,
    val byteLength: Int = 0,
)

@Serializable
internal class GltfMaterial(
    val pbrMetallicRoughness: GltfPbr? = null,
    val doubleSided: Boolean = false,
)

@Serializable
internal class GltfPbr(
    val baseColorFactor: List<Float>? = null,
    val baseColorTexture: GltfTextureRef? = null,
    val metallicFactor: Float = 1f,
    val roughnessFactor: Float = 1f,
)

@Serializable
internal class GltfTextureRef(
    val index: Int,
    val texCoord: Int = 0,
)

@Serializable
internal class GltfTexture(
    val sampler: Int? = null,
    val source: Int? = null,
)

@Serializable
internal class GltfImage(
    val uri: String? = null,
    val mimeType: String? = null,
    val bufferView: Int? = null,
)

@Serializable
internal class GltfSampler(
    val magFilter: Int? = null,
    val minFilter: Int? = null,
    val wrapS: Int = 10497,
    val wrapT: Int = 10497,
)
