package com.spatialkit.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeometryUtilsTest {
    @Test
    fun singleTriangleGetsFaceNormal() {
        // Triangle in the XY plane, counter-clockwise seen from +Z
        val vertices =
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
        val indices = intArrayOf(0, 1, 2)

        val normals = generateVertexNormals(vertices, indices)

        assertEquals(vertices.size, normals.size)
        for (v in 0 until 3) {
            assertEquals(0f, normals[v * 3], 1e-5f, "x of vertex $v")
            assertEquals(0f, normals[v * 3 + 1], 1e-5f, "y of vertex $v")
            assertEquals(1f, normals[v * 3 + 2], 1e-5f, "z of vertex $v")
        }
    }

    @Test
    fun normalsAreUnitLength() {
        // Two triangles sharing an edge, tilted against each other
        val vertices =
            floatArrayOf(
                0f,
                0f,
                0f,
                1f,
                0f,
                0f,
                0.5f,
                1f,
                0f,
                0.5f,
                -1f,
                0.5f,
            )
        val indices = intArrayOf(0, 1, 2, 1, 0, 3)

        val normals = generateVertexNormals(vertices, indices)

        for (v in 0 until vertices.size / 3) {
            val x = normals[v * 3]
            val y = normals[v * 3 + 1]
            val z = normals[v * 3 + 2]
            val length = kotlin.math.sqrt(x * x + y * y + z * z)
            assertEquals(1f, length, 1e-4f, "length of normal $v")
        }
    }

    @Test
    fun unreferencedVertexGetsUpNormal() {
        val vertices =
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
                // never referenced by a triangle
                5f,
                5f,
                5f,
            )
        val indices = intArrayOf(0, 1, 2)

        val normals = generateVertexNormals(vertices, indices)

        assertEquals(0f, normals[9])
        assertEquals(1f, normals[10])
        assertEquals(0f, normals[11])
    }

    @Test
    fun outOfRangeIndicesAreSkipped() {
        val vertices =
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
        // Second triangle references a vertex that doesn't exist
        val indices = intArrayOf(0, 1, 2, 0, 1, 99)

        val normals = generateVertexNormals(vertices, indices)

        assertEquals(vertices.size, normals.size)
        assertTrue(normals.all { it.isFinite() })
    }
}
