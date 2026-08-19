package com.spatialkit.render.filament

import com.spatialkit.render.GeometryData
import com.spatialkit.render.GeometryType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * CPU-side triangle mesh in the shared rendering conventions: right-handed,
 * +Y up, CCW front faces, UV origin top-left. Indices are 32-bit so meshes
 * above 65k vertices work.
 */
internal class MeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
}

/**
 * Generators for [GeometryType] primitives, matching the v1 unit-size
 * contracts (cube 1x1x1, sphere radius 1, plane 1x1 facing +Y, cylinder
 * r=0.5 h=1, capsule r=0.5 total h=1 — all centered at the origin). The
 * scene layer bakes node size × scale into the transform, so these stay
 * unit-sized.
 */
internal object PrimitiveMeshes {
    fun build(geometry: GeometryData): MeshData =
        when (geometry.type) {
            GeometryType.CUBE -> cube()
            GeometryType.SPHERE -> sphere(radius = 1f)
            GeometryType.PLANE -> plane()
            GeometryType.CYLINDER -> cylinder(radius = 0.5f, height = 1f)
            GeometryType.CAPSULE -> capsule(radius = 0.5f, totalHeight = 1f)
            GeometryType.CUSTOM_MESH -> custom(geometry)
        }

    private fun custom(geometry: GeometryData): MeshData {
        val positions =
            requireNotNull(geometry.vertices) { "CUSTOM_MESH requires vertices" }
        val indices =
            requireNotNull(geometry.indices) { "CUSTOM_MESH requires indices" }
        val vertexCount = positions.size / 3
        val normals =
            geometry.normals ?: computeVertexNormals(positions, indices)
        val uvs = geometry.uvs ?: FloatArray(vertexCount * 2)
        require(normals.size == vertexCount * 3) {
            "normals length ${normals.size} != vertexCount*3 (${vertexCount * 3})"
        }
        require(uvs.size == vertexCount * 2) {
            "uvs length ${uvs.size} != vertexCount*2 (${vertexCount * 2})"
        }
        return MeshData(positions, normals, uvs, indices)
    }

    /** Area-weighted vertex normals for custom meshes that omit them. */
    private fun computeVertexNormals(
        positions: FloatArray,
        indices: IntArray,
    ): FloatArray {
        val normals = FloatArray(positions.size)
        var i = 0
        while (i < indices.size) {
            val a = indices[i] * 3
            val b = indices[i + 1] * 3
            val c = indices[i + 2] * 3
            val abx = positions[b] - positions[a]
            val aby = positions[b + 1] - positions[a + 1]
            val abz = positions[b + 2] - positions[a + 2]
            val acx = positions[c] - positions[a]
            val acy = positions[c + 1] - positions[a + 1]
            val acz = positions[c + 2] - positions[a + 2]
            // Cross product ab × ac: CCW front faces → outward normal.
            val nx = aby * acz - abz * acy
            val ny = abz * acx - abx * acz
            val nz = abx * acy - aby * acx
            for (v in intArrayOf(a, b, c)) {
                normals[v] += nx
                normals[v + 1] += ny
                normals[v + 2] += nz
            }
            i += 3
        }
        var v = 0
        while (v < normals.size) {
            val len =
                kotlin.math.sqrt(
                    normals[v] * normals[v] + normals[v + 1] * normals[v + 1] + normals[v + 2] * normals[v + 2],
                )
            if (len > 1e-8f) {
                normals[v] /= len
                normals[v + 1] /= len
                normals[v + 2] /= len
            } else {
                normals[v + 1] = 1f
            }
            v += 3
        }
        return normals
    }

    private fun cube(): MeshData {
        val h = 0.5f
        // 6 faces × 4 vertices, per-face normals/UVs. UV origin top-left.
        val faces =
            arrayOf(
                // +X
                Face(floatArrayOf(1f, 0f, 0f), floatArrayOf(h, -h, h, h, -h, -h, h, h, -h, h, h, h)),
                // -X
                Face(floatArrayOf(-1f, 0f, 0f), floatArrayOf(-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h)),
                // +Y
                Face(floatArrayOf(0f, 1f, 0f), floatArrayOf(-h, h, h, h, h, h, h, h, -h, -h, h, -h)),
                // -Y
                Face(floatArrayOf(0f, -1f, 0f), floatArrayOf(-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h)),
                // +Z
                Face(floatArrayOf(0f, 0f, 1f), floatArrayOf(-h, -h, h, h, -h, h, h, h, h, -h, h, h)),
                // -Z
                Face(floatArrayOf(0f, 0f, -1f), floatArrayOf(h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h)),
            )
        val positions = FloatArray(6 * 4 * 3)
        val normals = FloatArray(6 * 4 * 3)
        val uvs = FloatArray(6 * 4 * 2)
        val indices = IntArray(6 * 6)
        faces.forEachIndexed { f, face ->
            face.corners.copyInto(positions, f * 12)
            for (v in 0 until 4) {
                face.normal.copyInto(normals, f * 12 + v * 3)
            }
            // Bottom-left, bottom-right, top-right, top-left of each face in
            // texture space (v grows downward).
            floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f).copyInto(uvs, f * 8)
            val base = f * 4
            intArrayOf(base, base + 1, base + 2, base, base + 2, base + 3).copyInto(indices, f * 6)
        }
        return MeshData(positions, normals, uvs, indices)
    }

    private class Face(
        val normal: FloatArray,
        val corners: FloatArray,
    )

    private fun plane(): MeshData {
        val h = 0.5f
        return MeshData(
            positions = floatArrayOf(-h, 0f, h, h, 0f, h, h, 0f, -h, -h, 0f, -h),
            normals = floatArrayOf(0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f),
            uvs = floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
            indices = intArrayOf(0, 1, 2, 0, 2, 3),
        )
    }

    private fun sphere(
        radius: Float,
        stacks: Int = 24,
        slices: Int = 32,
    ): MeshData {
        val vertexCount = (stacks + 1) * (slices + 1)
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        var v = 0
        for (stack in 0..stacks) {
            val phi = PI * stack / stacks // 0 = north pole
            val y = cos(phi).toFloat()
            val ringRadius = sin(phi).toFloat()
            for (slice in 0..slices) {
                val theta = 2.0 * PI * slice / slices
                val x = (ringRadius * sin(theta)).toFloat()
                val z = (ringRadius * cos(theta)).toFloat()
                positions[v * 3] = x * radius
                positions[v * 3 + 1] = y * radius
                positions[v * 3 + 2] = z * radius
                normals[v * 3] = x
                normals[v * 3 + 1] = y
                normals[v * 3 + 2] = z
                uvs[v * 2] = slice.toFloat() / slices
                uvs[v * 2 + 1] = stack.toFloat() / stacks
                v++
            }
        }
        val indices = IntArray(stacks * slices * 6)
        var i = 0
        for (stack in 0 until stacks) {
            for (slice in 0 until slices) {
                val a = stack * (slices + 1) + slice
                val b = a + slices + 1
                indices[i++] = a
                indices[i++] = b
                indices[i++] = a + 1
                indices[i++] = a + 1
                indices[i++] = b
                indices[i++] = b + 1
            }
        }
        return MeshData(positions, normals, uvs, indices)
    }

    private fun cylinder(
        radius: Float,
        height: Float,
        slices: Int = 32,
    ): MeshData = tube(radius, -height / 2f, height / 2f, slices, caps = true)

    /**
     * Capsule with [totalHeight] including both hemispherical caps; the
     * cylindrical mid-section is `totalHeight - 2*radius` (clamped at 0, in
     * which case the result is a sphere of [radius]).
     */
    private fun capsule(
        radius: Float,
        totalHeight: Float,
        stacks: Int = 12,
        slices: Int = 32,
    ): MeshData {
        val cylHalf = max(0f, totalHeight / 2f - radius)
        // Build as one lat-long sweep whose equator is stretched apart by
        // the cylinder half-height: stacks for the top hemisphere, one
        // stretched band, stacks for the bottom hemisphere.
        val rings = stacks * 2 + 1
        val vertexCount = (rings + 1) * (slices + 1)
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)
        var v = 0
        for (ring in 0..rings) {
            // Map rings 0..stacks to the top hemisphere (phi 0..π/2) and
            // stacks+1..rings to the bottom one (π/2..π).
            val topHalf = ring <= stacks
            val phi =
                if (topHalf) {
                    PI / 2 * ring / stacks
                } else {
                    PI / 2 + PI / 2 * (ring - stacks - 1 + 1).toDouble() / stacks
                }
            val ny = cos(phi).toFloat()
            val ringRadius = sin(phi).toFloat()
            val yOffset = if (topHalf) cylHalf else -cylHalf
            for (slice in 0..slices) {
                val theta = 2.0 * PI * slice / slices
                val nx = (ringRadius * sin(theta)).toFloat()
                val nz = (ringRadius * cos(theta)).toFloat()
                positions[v * 3] = nx * radius
                positions[v * 3 + 1] = ny * radius + yOffset
                positions[v * 3 + 2] = nz * radius
                normals[v * 3] = nx
                normals[v * 3 + 1] = ny
                normals[v * 3 + 2] = nz
                uvs[v * 2] = slice.toFloat() / slices
                uvs[v * 2 + 1] = ring.toFloat() / rings
                v++
            }
        }
        val indices = IntArray(rings * slices * 6)
        var i = 0
        for (ring in 0 until rings) {
            for (slice in 0 until slices) {
                val a = ring * (slices + 1) + slice
                val b = a + slices + 1
                indices[i++] = a
                indices[i++] = b
                indices[i++] = a + 1
                indices[i++] = a + 1
                indices[i++] = b
                indices[i++] = b + 1
            }
        }
        return MeshData(positions, normals, uvs, indices)
    }

    private fun tube(
        radius: Float,
        yBottom: Float,
        yTop: Float,
        slices: Int,
        caps: Boolean,
    ): MeshData {
        // Side: (slices+1) × 2 rings with radial normals; caps: their own
        // vertices with axial normals.
        val sideVerts = (slices + 1) * 2
        val capVerts = if (caps) (slices + 2) * 2 else 0
        val vertexCount = sideVerts + capVerts
        val positions = FloatArray(vertexCount * 3)
        val normals = FloatArray(vertexCount * 3)
        val uvs = FloatArray(vertexCount * 2)

        var v = 0
        for (slice in 0..slices) {
            val theta = 2.0 * PI * slice / slices
            val x = (sin(theta) * radius).toFloat()
            val z = (cos(theta) * radius).toFloat()
            val nx = (sin(theta)).toFloat()
            val nz = (cos(theta)).toFloat()
            // top ring then bottom ring interleaved as v, v+1
            positions[v * 3] = x
            positions[v * 3 + 1] = yTop
            positions[v * 3 + 2] = z
            normals[v * 3] = nx
            normals[v * 3 + 2] = nz
            uvs[v * 2] = slice.toFloat() / slices
            uvs[v * 2 + 1] = 0f
            v++
            positions[v * 3] = x
            positions[v * 3 + 1] = yBottom
            positions[v * 3 + 2] = z
            normals[v * 3] = nx
            normals[v * 3 + 2] = nz
            uvs[v * 2] = slice.toFloat() / slices
            uvs[v * 2 + 1] = 1f
            v++
        }
        val sideIndexCount = slices * 6
        val capIndexCount = if (caps) slices * 3 * 2 else 0
        val indices = IntArray(sideIndexCount + capIndexCount)
        var i = 0
        for (slice in 0 until slices) {
            val a = slice * 2 // top
            val b = a + 1 // bottom
            val c = a + 2 // next top
            val d = a + 3 // next bottom
            indices[i++] = a
            indices[i++] = b
            indices[i++] = c
            indices[i++] = c
            indices[i++] = b
            indices[i++] = d
        }
        if (caps) {
            // Top cap: center + ring, normal +Y, CCW seen from above.
            val topCenter = v
            positions[v * 3 + 1] = yTop
            normals[v * 3 + 1] = 1f
            uvs[v * 2] = 0.5f
            uvs[v * 2 + 1] = 0.5f
            v++
            val topRingStart = v
            for (slice in 0..slices) {
                val theta = 2.0 * PI * slice / slices
                positions[v * 3] = (sin(theta) * radius).toFloat()
                positions[v * 3 + 1] = yTop
                positions[v * 3 + 2] = (cos(theta) * radius).toFloat()
                normals[v * 3 + 1] = 1f
                uvs[v * 2] = 0.5f + sin(theta).toFloat() / 2f
                uvs[v * 2 + 1] = 0.5f - cos(theta).toFloat() / 2f
                v++
            }
            for (slice in 0 until slices) {
                indices[i++] = topCenter
                indices[i++] = topRingStart + slice + 1
                indices[i++] = topRingStart + slice
            }
            // Bottom cap: normal -Y, CCW seen from below.
            val bottomCenter = v
            positions[v * 3 + 1] = yBottom
            normals[v * 3 + 1] = -1f
            uvs[v * 2] = 0.5f
            uvs[v * 2 + 1] = 0.5f
            v++
            val bottomRingStart = v
            for (slice in 0..slices) {
                val theta = 2.0 * PI * slice / slices
                positions[v * 3] = (sin(theta) * radius).toFloat()
                positions[v * 3 + 1] = yBottom
                positions[v * 3 + 2] = (cos(theta) * radius).toFloat()
                normals[v * 3 + 1] = -1f
                uvs[v * 2] = 0.5f + sin(theta).toFloat() / 2f
                uvs[v * 2 + 1] = 0.5f + cos(theta).toFloat() / 2f
                v++
            }
            for (slice in 0 until slices) {
                indices[i++] = bottomCenter
                indices[i++] = bottomRingStart + slice
                indices[i++] = bottomRingStart + slice + 1
            }
        }
        return MeshData(positions, normals, uvs, indices)
    }
}
