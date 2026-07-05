package com.spatialkit.scene

import com.spatialkit.core.annotations.ExperimentalSpatialApi
import com.spatialkit.engine.GeometryType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalSpatialApi::class, ExperimentalEncodingApi::class)
class GltfLoaderTest {
    @Test
    fun parsesTriangleGeometry() {
        val geometry = GltfLoader(triangleGltf()).parseGeometry()

        assertNotNull(geometry)
        assertEquals(GeometryType.CUSTOM_MESH, geometry.type)
        assertContentEquals(
            floatArrayOf(
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
            geometry.vertices,
        )
        assertContentEquals(
            floatArrayOf(
                0f,
                0f,
                1f,
                0f,
                0f,
                1f,
                0f,
                0f,
                1f,
            ),
            geometry.normals,
        )
        assertContentEquals(intArrayOf(0, 1, 2), geometry.indices)
    }

    @Test
    fun loadFirstMeshWrapsGeometryInNode() {
        val node = GltfLoader(triangleGltf()).loadFirstMesh()

        assertNotNull(node)
        val vertices = assertNotNull(node.geometry.vertices)
        assertEquals(9, vertices.size)
    }

    @Test
    fun returnsNullWhenNoMeshes() {
        assertNull(GltfLoader("""{ "asset": { "version": "2.0" } }""").parseGeometry())
    }

    @Test
    fun returnsNullForInvalidJson() {
        assertNull(GltfLoader("not gltf at all").parseGeometry())
    }

    /**
     * A minimal valid glTF 2.0 asset: one triangle with positions, normals and
     * UNSIGNED_SHORT indices in a single embedded base64 buffer.
     *
     * Buffer layout (little-endian): positions 3×VEC3 float at 0..35,
     * normals 3×VEC3 float at 36..71, indices 3×ushort at 72..77.
     */
    private fun triangleGltf(): String {
        val buffer = ByteArray(78)
        val positions = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f)
        val normals = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        positions.forEachIndexed { i, value -> buffer.putFloatLE(i * 4, value) }
        normals.forEachIndexed { i, value -> buffer.putFloatLE(36 + i * 4, value) }
        shortArrayOf(0, 1, 2).forEachIndexed { i, value -> buffer.putUShortLE(72 + i * 2, value.toInt()) }

        val base64 = Base64.encode(buffer)
        return """
            {
              "asset": { "version": "2.0" },
              "meshes": [
                { "primitives": [ { "attributes": { "POSITION": 0, "NORMAL": 1 }, "indices": 2 } ] }
              ],
              "accessors": [
                { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 1, "componentType": 5126, "count": 3, "type": "VEC3" },
                { "bufferView": 2, "componentType": 5123, "count": 3, "type": "SCALAR" }
              ],
              "bufferViews": [
                { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 36, "byteLength": 36 },
                { "buffer": 0, "byteOffset": 72, "byteLength": 6 }
              ],
              "buffers": [
                { "uri": "data:application/octet-stream;base64,$base64", "byteLength": 78 }
              ]
            }
            """.trimIndent()
    }

    private fun ByteArray.putFloatLE(
        offset: Int,
        value: Float,
    ) {
        val bits = value.toRawBits()
        this[offset] = (bits and 0xFF).toByte()
        this[offset + 1] = ((bits shr 8) and 0xFF).toByte()
        this[offset + 2] = ((bits shr 16) and 0xFF).toByte()
        this[offset + 3] = ((bits shr 24) and 0xFF).toByte()
    }

    private fun ByteArray.putUShortLE(
        offset: Int,
        value: Int,
    ) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
