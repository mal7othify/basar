package com.spatialkit.scene

import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.TextureFilter
import com.spatialkit.engine.TextureWrap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class GltfLoaderTest {
    // ------------------------------------------------------------------
    // Geometry: glTF JSON with embedded buffer
    // ------------------------------------------------------------------

    @Test
    fun parsesTriangleGeometryFromJson() {
        val model = GltfLoader.load(triangleGltfJson())
        val geometry = assertNotNull(model.firstGeometry())

        assertEquals(GeometryType.CUSTOM_MESH, geometry.type)
        assertContentEquals(TRIANGLE_POSITIONS, geometry.vertices)
        assertContentEquals(TRIANGLE_NORMALS, geometry.normals)
        assertContentEquals(intArrayOf(0, 1, 2), geometry.indices)
    }

    @Test
    fun generatesNormalsWhenMissing() {
        val model = GltfLoader.load(triangleGltfJson(includeNormals = false))
        val geometry = assertNotNull(model.firstGeometry())

        val normals = assertNotNull(geometry.normals)
        assertEquals(geometry.vertices!!.size, normals.size)
        // Triangle in the XY plane -> normals point along +Z
        assertTrue(abs(normals[2]) > 0.99f)
    }

    @Test
    fun honorsAccessorByteOffset() {
        // Positions and normals share one bufferView; the normals accessor
        // addresses them via accessor.byteOffset instead of its own view.
        val model = GltfLoader.load(triangleGltfJson(sharedBufferView = true))
        val geometry = assertNotNull(model.firstGeometry())

        assertContentEquals(TRIANGLE_POSITIONS, geometry.vertices)
        assertContentEquals(TRIANGLE_NORMALS, geometry.normals)
    }

    @Test
    fun honorsByteStrideForInterleavedAttributes() {
        // Interleaved layout: [pos.xyz uv.xy] per vertex, stride 20 bytes
        val vertexCount = 3
        val stride = 20
        val buffer = ByteArray(vertexCount * stride)
        for (v in 0 until vertexCount) {
            for (c in 0 until 3) buffer.putFloatLE(v * stride + c * 4, TRIANGLE_POSITIONS[v * 3 + c])
            for (c in 0 until 2) buffer.putFloatLE(v * stride + 12 + c * 4, TRIANGLE_UVS[v * 2 + c])
        }

        val json =
            """
            {
              "asset": { "version": "2.0" },
              "meshes": [ { "primitives": [ {
                "attributes": { "POSITION": 0, "TEXCOORD_0": 1 }
              } ] } ],
              "accessors": [
                { "bufferView": 0, "byteOffset": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 0, "byteOffset": 12, "componentType": 5126, "count": 3, "type": "VEC2" }
              ],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": ${buffer.size}, "byteStride": $stride }
              ],
              "buffers": [ { "uri": "data:application/octet-stream;base64,${Base64.encode(
                buffer,
            )}", "byteLength": ${buffer.size} } ]
            }
            """.trimIndent()

        val geometry = assertNotNull(GltfLoader.load(json).firstGeometry())
        assertContentEquals(TRIANGLE_POSITIONS, geometry.vertices)
        assertContentEquals(TRIANGLE_UVS, geometry.uvs)
    }

    // ------------------------------------------------------------------
    // GLB container
    // ------------------------------------------------------------------

    @Test
    fun parsesGlbWithBinChunk() {
        val model = GltfLoader.load(buildTriangleGlb())
        val geometry = assertNotNull(model.firstGeometry())

        assertContentEquals(TRIANGLE_POSITIONS, geometry.vertices)
        assertContentEquals(TRIANGLE_UVS, geometry.uvs)
        assertContentEquals(intArrayOf(0, 1, 2), geometry.indices)
    }

    @Test
    fun parsesTexturedGlbMaterial() {
        val model = GltfLoader.load(buildTriangleGlb(textured = true))
        val root = model.toNode("model")

        val mesh = assertIs<CustomMeshNode>(firstMeshNode(root))
        val texture = assertNotNull(mesh.baseColorTexture)
        assertContentEquals(FAKE_PNG, texture.data)
        assertEquals("image/png", texture.mimeType)
        assertEquals(TextureWrap.CLAMP_TO_EDGE, texture.sampler.wrapU)
        assertEquals(TextureWrap.REPEAT, texture.sampler.wrapV)
        assertEquals(TextureFilter.NEAREST, texture.sampler.magFilter)
        assertEquals(TextureFilter.LINEAR, texture.sampler.minFilter)

        // baseColorFactor + metallic/roughness flow into the node
        assertEquals(1f, mesh.color.r)
        assertEquals(0f, mesh.color.b, absoluteTolerance = 1e-6f)
        assertEquals(0.25f, mesh.metallic)
        assertEquals(0.75f, mesh.roughness)
        assertTrue(mesh.doubleSided)
    }

    @Test
    fun sharedTextureIsOneInstance() {
        // Two primitives referencing the same glTF texture must share one
        // TextureData instance (one renderer-side upload).
        val model = GltfLoader.load(buildTriangleGlb(textured = true, duplicatePrimitive = true))
        val root = model.toNode()

        val meshNodes = collectMeshNodes(root)
        assertEquals(2, meshNodes.size)
        assertSame(meshNodes[0].baseColorTexture, meshNodes[1].baseColorTexture)
    }

    @Test
    fun rejectsTruncatedGlb() {
        val glb = buildTriangleGlb()
        val error = assertFailsWith<GltfParseException> { GltfLoader.load(glb.copyOfRange(0, glb.size / 2)) }
        assertTrue("truncated" in error.message!!, "message was: ${error.message}")
    }

    // ------------------------------------------------------------------
    // Hierarchy and transforms
    // ------------------------------------------------------------------

    @Test
    fun buildsNodeHierarchyWithTransforms() {
        val json =
            triangleGltfJson(
                nodesJson = """
              "scenes": [ { "nodes": [0] } ],
              "scene": 0,
              "nodes": [
                { "children": [1], "translation": [1.0, 2.0, 3.0], "scale": [2.0, 2.0, 2.0] },
                { "mesh": 0, "translation": [0.5, 0.0, 0.0] }
              ],
            """,
            )

        val root = GltfLoader.load(json).toNode("root")
        assertEquals("root", root.id)

        val outer = assertIs<GroupNode>(root.children.single())
        assertEquals(1f, outer.position.x)
        assertEquals(2f, outer.position.y)
        assertEquals(3f, outer.position.z)
        assertEquals(2f, outer.scale.x)

        val mesh = assertIs<CustomMeshNode>(outer.children.single())
        assertEquals(0.5f, mesh.position.x)
    }

    @Test
    fun decomposesMatrixTransform() {
        // Column-major: translate (1,2,3), rotate 90° about Y, uniform scale 2
        val json =
            triangleGltfJson(
                nodesJson = """
              "nodes": [
                { "mesh": 0, "matrix": [
                    0.0, 0.0, -2.0, 0.0,
                    0.0, 2.0,  0.0, 0.0,
                    2.0, 0.0,  0.0, 0.0,
                    1.0, 2.0,  3.0, 1.0
                ] }
              ],
            """,
            )

        val node =
            GltfLoader
                .load(json)
                .toNode()
                .children
                .single()
        assertEquals(1f, node.position.x)
        assertEquals(2f, node.position.y)
        assertEquals(3f, node.position.z)
        assertEquals(2f, node.scale.x, absoluteTolerance = 1e-5f)
        assertEquals(2f, node.scale.y, absoluteTolerance = 1e-5f)
        assertEquals(2f, node.scale.z, absoluteTolerance = 1e-5f)
        // 90° about Y: q = (0, sin45, 0, cos45)
        assertEquals(0.7071f, abs(node.rotation.y), absoluteTolerance = 1e-3f)
        assertEquals(0.7071f, abs(node.rotation.w), absoluteTolerance = 1e-3f)
    }

    @Test
    fun instantiatesFreshNodesPerCall() {
        val model = GltfLoader.load(triangleGltfJson())
        val a = model.toNode()
        val b = model.toNode()

        assertTrue(a !== b)
        // Geometry is shared (immutable), node objects are not
        assertSame(
            collectMeshNodes(a).single().geometry,
            collectMeshNodes(b).single().geometry,
        )
    }

    @Test
    fun modelWithoutMeshesIsEmptyNotAnError() {
        val model = GltfLoader.load("""{ "asset": { "version": "2.0" } }""")
        assertEquals(0, model.meshCount)
        assertNull(model.firstGeometry())
        assertTrue(model.toNode().children.isEmpty())
    }

    // ------------------------------------------------------------------
    // Errors
    // ------------------------------------------------------------------

    @Test
    fun rejectsInvalidJson() {
        val error = assertFailsWith<GltfParseException> { GltfLoader.load("not gltf at all") }
        assertTrue("Not valid glTF JSON" in error.message!!)
    }

    @Test
    fun rejectsMissingVersion() {
        assertFailsWith<GltfParseException> { GltfLoader.load("""{ "meshes": [] }""") }
    }

    @Test
    fun rejectsExternalBufferUri() {
        val json =
            """
            {
              "asset": { "version": "2.0" },
              "buffers": [ { "uri": "model.bin", "byteLength": 100 } ]
            }
            """.trimIndent()
        val error = assertFailsWith<GltfParseException> { GltfLoader.load(json) }
        assertTrue("external" in error.message!!)
    }

    @Test
    fun rejectsAccessorOverrunningItsView() {
        // Accessor claims 100 vertices but the buffer view only holds 3
        val json =
            triangleGltfJson().replace(
                """"componentType": 5126, "count": 3, "type": "VEC3" }""",
                """"componentType": 5126, "count": 100, "type": "VEC3" }""",
            )
        val error = assertFailsWith<GltfParseException> { GltfLoader.load(json) }
        assertTrue("overruns" in error.message!!, "message was: ${error.message}")
    }

    @Test
    fun rejectsNonTrianglePrimitives() {
        val json = triangleGltfJson().replace(""""indices": 2""", """"indices": 2, "mode": 1""")
        val error = assertFailsWith<GltfParseException> { GltfLoader.load(json) }
        assertTrue("TRIANGLES" in error.message!!)
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    /**
     * Buffer layout (little-endian): positions 3×VEC3 float at 0..35,
     * normals 3×VEC3 float at 36..71, UVs 3×VEC2 float at 72..95,
     * indices 3×ushort at 96..101.
     */
    private fun buildTriangleBuffer(): ByteArray {
        val buffer = ByteArray(102)
        TRIANGLE_POSITIONS.forEachIndexed { i, v -> buffer.putFloatLE(i * 4, v) }
        TRIANGLE_NORMALS.forEachIndexed { i, v -> buffer.putFloatLE(36 + i * 4, v) }
        TRIANGLE_UVS.forEachIndexed { i, v -> buffer.putFloatLE(72 + i * 4, v) }
        shortArrayOf(0, 1, 2).forEachIndexed { i, v -> buffer.putUShortLE(96 + i * 2, v.toInt()) }
        return buffer
    }

    private fun triangleGltfJson(
        includeNormals: Boolean = true,
        sharedBufferView: Boolean = false,
        nodesJson: String = "",
    ): String {
        val buffer = buildTriangleBuffer()
        val base64 = Base64.encode(buffer)
        val attributes =
            if (includeNormals) {
                """{ "POSITION": 0, "NORMAL": 1 }"""
            } else {
                """{ "POSITION": 0 }"""
            }
        val accessors =
            if (sharedBufferView) {
                // Both accessors use bufferView 0; NORMAL addresses its data
                // through accessor.byteOffset = 36.
                """
                { "bufferView": 0, "byteOffset": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 0, "byteOffset": 36, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 2, "componentType": 5123, "count": 3, "type": "SCALAR" }
                """
            } else {
                """
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 2, "componentType": 5123, "count": 3, "type": "SCALAR" }
                """
            }
        return """
            {
              "asset": { "version": "2.0" },
              $nodesJson
              "meshes": [
                { "primitives": [ { "attributes": $attributes, "indices": 2 } ] }
              ],
              "accessors": [ $accessors ],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": 72 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 96, "byteLength": 6 }
              ],
              "buffers": [
                { "uri": "data:application/octet-stream;base64,$base64", "byteLength": ${buffer.size} }
              ]
            }
            """.trimIndent()
    }

    /**
     * Builds a GLB in-memory: triangle geometry in the BIN chunk, optionally
     * with a PNG texture image (also in the BIN chunk) and a PBR material.
     */
    private fun buildTriangleGlb(
        textured: Boolean = false,
        duplicatePrimitive: Boolean = false,
    ): ByteArray {
        val geometry = buildTriangleBuffer()
        val imageOffset = (geometry.size + 3) and (3.inv()) // 4-align
        val bin =
            if (textured) {
                ByteArray(imageOffset + FAKE_PNG.size).also {
                    geometry.copyInto(it)
                    FAKE_PNG.copyInto(it, imageOffset)
                }
            } else {
                geometry
            }

        val materialRef = if (textured) """, "material": 0""" else ""
        val primitive =
            """{ "attributes": { "POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 3 }, "indices": 2$materialRef }"""
        val primitives = if (duplicatePrimitive) "$primitive, $primitive" else primitive

        val materialJson =
            if (textured) {
                """
                ,"materials": [ {
                    "doubleSided": true,
                    "pbrMetallicRoughness": {
                      "baseColorFactor": [1.0, 0.5, 0.0, 1.0],
                      "baseColorTexture": { "index": 0 },
                      "metallicFactor": 0.25,
                      "roughnessFactor": 0.75
                    }
                } ],
                "textures": [ { "source": 0, "sampler": 0 } ],
                "images": [ { "bufferView": 4, "mimeType": "image/png" } ],
                "samplers": [ { "wrapS": 33071, "wrapT": 10497, "magFilter": 9728, "minFilter": 9729 } ]
                """
            } else {
                ""
            }

        val imageBufferView =
            if (textured) {
                """,{ "buffer": 0, "byteOffset": $imageOffset, "byteLength": ${FAKE_PNG.size} }"""
            } else {
                ""
            }

        val json =
            """
            {
              "asset": { "version": "2.0" },
              "meshes": [ { "primitives": [ $primitives ] } ],
              "accessors": [
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 2, "componentType": 5123, "count": 3, "type": "SCALAR" },
                { "bufferView": 3, "componentType": 5126, "count": 3, "type": "VEC2" }
              ],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 96, "byteLength": 6 },
                { "buffer": 0, "byteOffset": 72, "byteLength": 24 }
                $imageBufferView
              ],
              "buffers": [ { "byteLength": ${bin.size} } ]
              $materialJson
            }
            """.trimIndent()

        return buildGlb(json.encodeToByteArray(), bin)
    }

    private fun buildGlb(
        jsonBytes: ByteArray,
        bin: ByteArray,
    ): ByteArray {
        fun pad4(size: Int) = (size + 3) and (3.inv())

        val jsonPadded =
            ByteArray(pad4(jsonBytes.size)) { i ->
                if (i <
                    jsonBytes.size
                ) {
                    jsonBytes[i]
                } else {
                    ' '.code.toByte()
                }
            }
        val binPadded = ByteArray(pad4(bin.size)).also { bin.copyInto(it) }

        val total = 12 + 8 + jsonPadded.size + 8 + binPadded.size
        val out = ByteArray(total)
        out.putUIntLE(0, 0x46546C67) // magic "glTF"
        out.putUIntLE(4, 2) // version
        out.putUIntLE(8, total)
        out.putUIntLE(12, jsonPadded.size)
        out.putUIntLE(16, 0x4E4F534A) // "JSON"
        jsonPadded.copyInto(out, 20)
        val binHeader = 20 + jsonPadded.size
        out.putUIntLE(binHeader, binPadded.size)
        out.putUIntLE(binHeader + 4, 0x004E4942) // "BIN\0"
        binPadded.copyInto(out, binHeader + 8)
        return out
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun firstMeshNode(node: SpatialNode): SpatialNode? = collectMeshNodes(node).firstOrNull()

    private fun collectMeshNodes(node: SpatialNode): List<CustomMeshNode> =
        buildList {
            if (node is CustomMeshNode) add(node)
            node.children.forEach { addAll(collectMeshNodes(it)) }
        }

    private fun ByteArray.putFloatLE(
        offset: Int,
        value: Float,
    ) {
        putUIntLE(offset, value.toRawBits())
    }

    private fun ByteArray.putUIntLE(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
        this[offset + 2] = ((value shr 16) and 0xFF).toByte()
        this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun ByteArray.putUShortLE(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private companion object {
        val TRIANGLE_POSITIONS = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val TRIANGLE_NORMALS = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val TRIANGLE_UVS = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f)

        /** Not a decodable PNG — just recognizable bytes with the PNG signature. */
        val FAKE_PNG =
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                1,
                2,
                3,
                4,
                5,
                6,
                7,
                8,
            )
    }
}
