package com.spatialkit.scene

import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class ModelNodeTest {
    @Test
    fun toNodeReturnsModelNodeBackedByTheModel() {
        val model = GltfLoader.load(TRIANGLE_GLTF)
        val node = model.toNode("duck")

        assertEquals("duck", node.id)
        assertSame(model, node.model)
        assertTrue(node.children.isNotEmpty(), "model hierarchy should be instantiated as children")
    }

    @Test
    fun instancesShareTheModelButNotNodes() {
        val model = GltfLoader.load(TRIANGLE_GLTF)
        val a = ModelNode(model)
        val b = ModelNode(model)

        assertSame(a.model, b.model)
        assertNotSame(a.children.first(), b.children.first())
    }

    @Test
    fun loadGltfCachesByContent() =
        runTest {
            val bytes = TRIANGLE_GLTF.encodeToByteArray()
            val first = loadGltf(bytes)
            // A distinct array with equal content must hit the cache
            val second = loadGltf(bytes.copyOf())

            assertSame(first, second)
        }

    @Test
    fun loadGltfPropagatesParseFailures() =
        runTest {
            assertFailsWith<GltfParseException> {
                loadGltf(byteArrayOf(0x7B, 0x20)) // "{ " — truncated JSON
            }
        }

    private companion object {
        /**
         * Minimal valid glTF: one indexed triangle in the XY plane.
         * Buffer layout: 3×vec3 float positions (36 bytes) + 3 ushort indices.
         */
        val TRIANGLE_GLTF: String =
            run {
                val buffer = ByteArray(44)
                val positions =
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
                    )
                positions.forEachIndexed { i, v ->
                    val bits = v.toRawBits()
                    for (b in 0 until 4) {
                        buffer[i * 4 + b] = (bits shr (8 * b)).toByte()
                    }
                }
                shortArrayOf(0, 1, 2).forEachIndexed { i, v ->
                    buffer[36 + i * 2] = (v.toInt() and 0xFF).toByte()
                    buffer[36 + i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
                }
                val base64 = Base64.encode(buffer)

                """
                {
                  "asset": { "version": "2.0" },
                  "scene": 0,
                  "scenes": [{ "nodes": [0] }],
                  "nodes": [{ "mesh": 0 }],
                  "meshes": [{ "primitives": [{ "attributes": { "POSITION": 0 }, "indices": 1 }] }],
                  "accessors": [
                    { "bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3" },
                    { "bufferView": 1, "componentType": 5123, "count": 3, "type": "SCALAR" }
                  ],
                  "bufferViews": [
                    { "buffer": 0, "byteOffset": 0, "byteLength": 36 },
                    { "buffer": 0, "byteOffset": 36, "byteLength": 6 }
                  ],
                  "buffers": [{ "byteLength": 44, "uri": "data:application/octet-stream;base64,$base64" }]
                }
                """.trimIndent()
            }
    }
}
