package com.spatialkit.engine

import kotlin.math.sqrt

/**
 * Generate smooth per-vertex normals for an indexed triangle mesh.
 *
 * Face normals are accumulated per vertex (weighted by triangle area, since
 * the cross-product magnitude is proportional to it) and normalized. Useful
 * when geometry sources (e.g. GLTF files) omit the NORMAL attribute.
 *
 * Degenerate input is handled defensively: triangles with out-of-range
 * indices are skipped, and vertices not referenced by any triangle get an
 * up-facing normal.
 *
 * @param vertices Vertex positions as packed xyz triples
 * @param indices Triangle indices (every 3 entries form one triangle)
 * @return Packed xyz normals, same size as [vertices]
 */
public fun generateVertexNormals(
    vertices: FloatArray,
    indices: IntArray,
): FloatArray {
    val normals = FloatArray(vertices.size)
    val vertexCount = vertices.size / 3

    var i = 0
    while (i + 2 < indices.size) {
        val a = indices[i]
        val b = indices[i + 1]
        val c = indices[i + 2]
        i += 3

        if (a >= vertexCount || b >= vertexCount || c >= vertexCount || a < 0 || b < 0 || c < 0) continue

        val ax = vertices[a * 3]
        val ay = vertices[a * 3 + 1]
        val az = vertices[a * 3 + 2]

        // Edge vectors ab and ac
        val abx = vertices[b * 3] - ax
        val aby = vertices[b * 3 + 1] - ay
        val abz = vertices[b * 3 + 2] - az
        val acx = vertices[c * 3] - ax
        val acy = vertices[c * 3 + 1] - ay
        val acz = vertices[c * 3 + 2] - az

        // Face normal = ab × ac (magnitude ∝ triangle area)
        val nx = aby * acz - abz * acy
        val ny = abz * acx - abx * acz
        val nz = abx * acy - aby * acx

        for (v in intArrayOf(a, b, c)) {
            normals[v * 3] += nx
            normals[v * 3 + 1] += ny
            normals[v * 3 + 2] += nz
        }
    }

    var v = 0
    while (v < normals.size) {
        val x = normals[v]
        val y = normals[v + 1]
        val z = normals[v + 2]
        val length = sqrt(x * x + y * y + z * z)
        if (length > 1e-6f) {
            normals[v] = x / length
            normals[v + 1] = y / length
            normals[v + 2] = z / length
        } else {
            normals[v] = 0f
            normals[v + 1] = 1f
            normals[v + 2] = 0f
        }
        v += 3
    }
    return normals
}
