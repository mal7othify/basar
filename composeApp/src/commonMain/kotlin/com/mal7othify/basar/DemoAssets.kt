package com.mal7othify.basar

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

// Self-contained demo assets: embedded glTF/GLB builders shared by the demo
// screens. In production you'd ship real GLB files and load their bytes from
// Compose resources or the network; embedding keeps the demos dependency-free.

/**
 * Creates a glTF JSON string for a 10cm crate with a checkerboard base-color
 * texture (embedded as a PNG data URI) — the M7 textured-model demo asset.
 */
internal fun createCrateGltf(): String {
    val half = 0.05f
    val faceNormals =
        arrayOf(
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0f, 0f, -1f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, -1f, 0f),
        )

    val positions = FloatArray(24 * 3)
    val normals = FloatArray(24 * 3)
    val uvs = FloatArray(24 * 2)
    val indices = IntArray(36)

    faceNormals.forEachIndexed { face, n ->
        // Face basis chosen so u × v = n: corners wind counter-clockwise
        // seen from outside (the engine contract's front-face convention)
        val u = if (n[1] != 0f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(n[2], 0f, -n[0])
        val v =
            floatArrayOf(
                n[1] * u[2] - n[2] * u[1],
                n[2] * u[0] - n[0] * u[2],
                n[0] * u[1] - n[1] * u[0],
            )

        for (corner in 0 until 4) {
            val su = if (corner == 1 || corner == 2) 1f else -1f
            val sv = if (corner >= 2) 1f else -1f
            val vi = face * 4 + corner
            for (axis in 0 until 3) {
                positions[vi * 3 + axis] = (n[axis] + u[axis] * su + v[axis] * sv) * half
                normals[vi * 3 + axis] = n[axis]
            }
            uvs[vi * 2] = (su + 1f) / 2f
            uvs[vi * 2 + 1] = (1f - sv) / 2f
        }

        val base = face * 4
        val ii = face * 6
        indices[ii] = base
        indices[ii + 1] = base + 1
        indices[ii + 2] = base + 2
        indices[ii + 3] = base
        indices[ii + 4] = base + 2
        indices[ii + 5] = base + 3
    }

    val positionBytes = floatsToBytes(positions)
    val normalBytes = floatsToBytes(normals)
    val uvBytes = floatsToBytes(uvs)
    val indexBytes = intsToUShortBytes(indices)
    val buffer = positionBytes + normalBytes + uvBytes + indexBytes
    val base64 = bytesToBase64(buffer)

    val normalOffset = positionBytes.size
    val uvOffset = normalOffset + normalBytes.size
    val indexOffset = uvOffset + uvBytes.size

    return """
{
  "asset": { "version": "2.0" },
  "scene": 0,
  "scenes": [{ "nodes": [0] }],
  "nodes": [{ "mesh": 0 }],
  "meshes": [{
    "primitives": [{
      "attributes": { "POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2 },
      "indices": 3,
      "material": 0
    }]
  }],
  "materials": [{
    "pbrMetallicRoughness": {
      "baseColorTexture": { "index": 0 },
      "metallicFactor": 0.0,
      "roughnessFactor": 0.8
    }
  }],
  "textures": [{ "source": 0, "sampler": 0 }],
  "samplers": [{ "magFilter": 9728, "minFilter": 9728, "wrapS": 10497, "wrapT": 10497 }],
  "images": [{ "uri": "data:image/png;base64,$CRATE_TEXTURE_PNG" }],
  "accessors": [
    { "bufferView": 0, "componentType": 5126, "count": 24, "type": "VEC3" },
    { "bufferView": 1, "componentType": 5126, "count": 24, "type": "VEC3" },
    { "bufferView": 2, "componentType": 5126, "count": 24, "type": "VEC2" },
    { "bufferView": 3, "componentType": 5123, "count": 36, "type": "SCALAR" }
  ],
  "bufferViews": [
    { "buffer": 0, "byteOffset": 0, "byteLength": ${positionBytes.size} },
    { "buffer": 0, "byteOffset": $normalOffset, "byteLength": ${normalBytes.size} },
    { "buffer": 0, "byteOffset": $uvOffset, "byteLength": ${uvBytes.size} },
    { "buffer": 0, "byteOffset": $indexOffset, "byteLength": ${indexBytes.size} }
  ],
  "buffers": [{ "byteLength": ${buffer.size}, "uri": "data:application/octet-stream;base64,$base64" }]
}
        """.trimIndent()
}

/** 8x8 two-tone checkerboard PNG (nearest-filtered for crisp squares). */
private const val CRATE_TEXTURE_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAGUlEQVR42mPYU+V0b1UeJsmAVRRIMgxKHQD/fm3BYWe/ygAAAABJRU5ErkJggg=="

/**
 * Creates a GLTF JSON string for a simple 5-pointed star.
 */
internal fun createStarGltf(): String {
    // Star vertices: 5 outer points + 5 inner points + center
    val outerRadius = 0.1f
    val innerRadius = 0.04f
    val vertices = mutableListOf<Float>()
    val indices = mutableListOf<Int>()

    // Center vertex
    vertices.addAll(listOf(0f, 0f, 0f))

    // Generate star points (alternating outer/inner)
    for (i in 0 until 10) {
        val angle = (i * 36f - 90f) * (PI.toFloat() / 180f)
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        vertices.add(cos(angle) * radius)
        vertices.add(sin(angle) * radius)
        vertices.add(0f)
    }

    // Generate triangles (center to each edge)
    for (i in 0 until 10) {
        indices.addAll(listOf(0, i + 1, ((i + 1) % 10) + 1))
    }

    // Encode vertices and indices as base64
    val vertexBytes = floatsToBytes(vertices.toFloatArray())
    val indexBytes = intsToUShortBytes(indices.toIntArray())

    val vertexBase64 = bytesToBase64(vertexBytes)
    val indexBase64 = bytesToBase64(indexBytes)

    // Build GLTF JSON
    return """
{
  "asset": { "version": "2.0" },
  "buffers": [
    { "uri": "data:application/octet-stream;base64,$vertexBase64", "byteLength": ${vertexBytes.size} },
    { "uri": "data:application/octet-stream;base64,$indexBase64", "byteLength": ${indexBytes.size} }
  ],
  "bufferViews": [
    { "buffer": 0, "byteOffset": 0, "byteLength": ${vertexBytes.size} },
    { "buffer": 1, "byteOffset": 0, "byteLength": ${indexBytes.size} }
  ],
  "accessors": [
    { "bufferView": 0, "componentType": 5126, "count": ${vertices.size / 3}, "type": "VEC3" },
    { "bufferView": 1, "componentType": 5123, "count": ${indices.size}, "type": "SCALAR" }
  ],
  "meshes": [{
    "primitives": [{
      "attributes": { "POSITION": 0 },
      "indices": 1
    }]
  }]
}
        """.trimIndent()
}

private fun floatsToBytes(floats: FloatArray): ByteArray {
    val bytes = ByteArray(floats.size * 4)
    for (i in floats.indices) {
        val bits = floats[i].toBits()
        bytes[i * 4] = (bits and 0xFF).toByte()
        bytes[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
        bytes[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
        bytes[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
    }
    return bytes
}

private fun intsToUShortBytes(ints: IntArray): ByteArray {
    val bytes = ByteArray(ints.size * 2)
    for (i in ints.indices) {
        bytes[i * 2] = (ints[i] and 0xFF).toByte()
        bytes[i * 2 + 1] = ((ints[i] shr 8) and 0xFF).toByte()
    }
    return bytes
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun bytesToBase64(bytes: ByteArray): String =
    kotlin.io.encoding.Base64
        .encode(bytes)

internal fun Float.round2() = round(this * 100) / 100
