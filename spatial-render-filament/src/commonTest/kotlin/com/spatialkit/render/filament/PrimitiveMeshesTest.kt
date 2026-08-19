package com.spatialkit.render.filament

import com.spatialkit.render.GeometryData
import com.spatialkit.render.GeometryType
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrimitiveMeshesTest {
    private fun mesh(type: GeometryType) = PrimitiveMeshes.build(GeometryData(type))

    @Test
    fun allPrimitivesHaveConsistentArraySizes() {
        for (type in listOf(
            GeometryType.CUBE,
            GeometryType.SPHERE,
            GeometryType.PLANE,
            GeometryType.CYLINDER,
            GeometryType.CAPSULE,
        )) {
            val m = mesh(type)
            assertEquals(m.vertexCount * 3, m.positions.size, "$type positions")
            assertEquals(m.vertexCount * 3, m.normals.size, "$type normals")
            assertEquals(m.vertexCount * 2, m.uvs.size, "$type uvs")
            assertEquals(0, m.indices.size % 3, "$type triangle indices")
            assertTrue(m.indices.all { it in 0 until m.vertexCount }, "$type indices in range")
        }
    }

    @Test
    fun normalsAreUnitLength() {
        for (type in listOf(GeometryType.CUBE, GeometryType.SPHERE, GeometryType.CYLINDER, GeometryType.CAPSULE)) {
            val m = mesh(type)
            for (v in 0 until m.vertexCount) {
                val x = m.normals[v * 3]
                val y = m.normals[v * 3 + 1]
                val z = m.normals[v * 3 + 2]
                assertTrue(abs(sqrt(x * x + y * y + z * z) - 1f) < 1e-4f, "$type vertex $v normal length")
            }
        }
    }

    @Test
    fun unitSizeContracts() {
        fun bounds(m: MeshData): Pair<FloatArray, FloatArray> {
            val min = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
            val max = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
            for (v in 0 until m.vertexCount) {
                for (axis in 0..2) {
                    val p = m.positions[v * 3 + axis]
                    if (p < min[axis]) min[axis] = p
                    if (p > max[axis]) max[axis] = p
                }
            }
            return min to max
        }

        val (cubeMin, cubeMax) = bounds(mesh(GeometryType.CUBE))
        for (axis in 0..2) {
            assertEquals(-0.5f, cubeMin[axis], 1e-5f)
            assertEquals(0.5f, cubeMax[axis], 1e-5f)
        }

        val (sphereMin, sphereMax) = bounds(mesh(GeometryType.SPHERE))
        assertEquals(-1f, sphereMin[1], 1e-5f)
        assertEquals(1f, sphereMax[1], 1e-5f)

        val (cylMin, cylMax) = bounds(mesh(GeometryType.CYLINDER))
        assertEquals(-0.5f, cylMin[1], 1e-5f)
        assertEquals(0.5f, cylMax[1], 1e-5f)

        val (capMin, capMax) = bounds(mesh(GeometryType.CAPSULE))
        assertEquals(-0.5f, capMin[1], 1e-4f)
        assertEquals(0.5f, capMax[1], 1e-4f)
    }

    @Test
    fun sphereWindingIsCounterClockwiseSeenFromOutside() {
        // For each triangle, the geometric normal should point away from the
        // origin (dot with the centroid > 0) — CCW front faces outward.
        val m = mesh(GeometryType.SPHERE)
        var i = 0
        while (i < m.indices.size) {
            val a = m.indices[i] * 3
            val b = m.indices[i + 1] * 3
            val c = m.indices[i + 2] * 3
            val abx = m.positions[b] - m.positions[a]
            val aby = m.positions[b + 1] - m.positions[a + 1]
            val abz = m.positions[b + 2] - m.positions[a + 2]
            val acx = m.positions[c] - m.positions[a]
            val acy = m.positions[c + 1] - m.positions[a + 1]
            val acz = m.positions[c + 2] - m.positions[a + 2]
            val nx = aby * acz - abz * acy
            val ny = abz * acx - abx * acz
            val nz = abx * acy - aby * acx
            val cx = (m.positions[a] + m.positions[b] + m.positions[c]) / 3f
            val cy = (m.positions[a + 1] + m.positions[b + 1] + m.positions[c + 1]) / 3f
            val cz = (m.positions[a + 2] + m.positions[b + 2] + m.positions[c + 2]) / 3f
            val dot = nx * cx + ny * cy + nz * cz
            // Degenerate pole triangles have ~zero area; skip them.
            val area2 = nx * nx + ny * ny + nz * nz
            if (area2 > 1e-10f) {
                assertTrue(dot > 0f, "triangle at index $i winds clockwise")
            }
            i += 3
        }
    }

    @Test
    fun customMeshComputesNormalsWhenAbsent() {
        // A single CCW triangle in the XZ plane should get a +Y normal.
        val m =
            PrimitiveMeshes.build(
                GeometryData(
                    type = GeometryType.CUSTOM_MESH,
                    vertices = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, -1f),
                    indices = intArrayOf(0, 1, 2),
                ),
            )
        for (v in 0 until 3) {
            assertEquals(0f, m.normals[v * 3], 1e-5f)
            assertEquals(1f, m.normals[v * 3 + 1], 1e-5f)
            assertEquals(0f, m.normals[v * 3 + 2], 1e-5f)
        }
    }

    @Test
    fun customMeshRequiresVerticesAndIndices() {
        assertFailsWith<IllegalArgumentException> {
            PrimitiveMeshes.build(GeometryData(GeometryType.CUSTOM_MESH))
        }
    }

    @Test
    fun tangentFramesAreUnitQuaternionsWithPositiveW() {
        val m = mesh(GeometryType.SPHERE)
        val tangents = TangentFrames.fromNormals(m.normals)
        assertEquals(m.vertexCount * 4, tangents.size)
        for (v in 0 until m.vertexCount) {
            val x = tangents[v * 4]
            val y = tangents[v * 4 + 1]
            val z = tangents[v * 4 + 2]
            val w = tangents[v * 4 + 3]
            assertTrue(abs(sqrt(x * x + y * y + z * z + w * w) - 1f) < 1e-3f, "vertex $v quaternion length")
            assertTrue(w >= 0f, "vertex $v handedness sign")
        }
    }

    @Test
    fun tangentFrameRotatesZAxisOntoNormal() {
        val normals = floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f)
        val q = TangentFrames.fromNormals(normals)
        for (v in 0 until 3) {
            val x = q[v * 4]
            val y = q[v * 4 + 1]
            val z = q[v * 4 + 2]
            val w = q[v * 4 + 3]
            // Rotate (0,0,1) by the quaternion; expect the source normal.
            val rx = 2f * (x * z + w * y)
            val ry = 2f * (y * z - w * x)
            val rz = 1f - 2f * (x * x + y * y)
            assertEquals(normals[v * 3], rx, 1e-4f, "vertex $v nx")
            assertEquals(normals[v * 3 + 1], ry, 1e-4f, "vertex $v ny")
            assertEquals(normals[v * 3 + 2], rz, 1e-4f, "vertex $v nz")
        }
    }
}
