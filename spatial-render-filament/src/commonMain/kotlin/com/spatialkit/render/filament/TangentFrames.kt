package com.spatialkit.render.filament

import kotlin.math.sqrt

/**
 * Packs per-vertex normals into the quaternion tangent frames Filament's
 * lit materials require (the TANGENTS vertex attribute, FLOAT4).
 *
 * Our material model has no normal maps, so any consistent tangent works;
 * the basis is derived from the normal alone (Frisvad's method). The
 * quaternion rotates +Z onto the normal with the tangent as +X; Filament
 * only requires w != 0 (sign encodes handedness, positive here).
 */
internal object TangentFrames {
    fun fromNormals(normals: FloatArray): FloatArray {
        val vertexCount = normals.size / 3
        val out = FloatArray(vertexCount * 4)
        for (v in 0 until vertexCount) {
            val nx = normals[v * 3]
            val ny = normals[v * 3 + 1]
            val nz = normals[v * 3 + 2]

            // Orthonormal basis from the normal (Frisvad / Pixar variant,
            // stable for nz near -1).
            val tx: Float
            val ty: Float
            val tz: Float
            if (nz < -0.999999f) {
                tx = 0f
                ty = -1f
                tz = 0f
            } else {
                val a = 1f / (1f + nz)
                val b = -nx * ny * a
                tx = 1f - nx * nx * a
                ty = b
                tz = -nx
            }
            // Bitangent = n × t
            val bx = ny * tz - nz * ty
            val by = nz * tx - nx * tz
            val bz = nx * ty - ny * tx

            // Rotation matrix columns (t, b, n) → quaternion.
            val trace = tx + by + nz
            var qx: Float
            var qy: Float
            var qz: Float
            var qw: Float
            if (trace > 0f) {
                val s = 0.5f / sqrt(trace + 1f)
                qw = 0.25f / s
                qx = (bz - ny) * s
                qy = (nx - tz) * s
                qz = (ty - bx) * s
            } else if (tx > by && tx > nz) {
                val s = 2f * sqrt(1f + tx - by - nz)
                qw = (bz - ny) / s
                qx = 0.25f * s
                qy = (bx + ty) / s
                qz = (nx + tz) / s
            } else if (by > nz) {
                val s = 2f * sqrt(1f + by - tx - nz)
                qw = (nx - tz) / s
                qx = (bx + ty) / s
                qy = 0.25f * s
                qz = (ny + bz) / s
            } else {
                val s = 2f * sqrt(1f + nz - tx - by)
                qw = (ty - bx) / s
                qx = (nx + tz) / s
                qy = (ny + bz) / s
                qz = 0.25f * s
            }
            // Filament requires w > 0 (sign carries handedness; ours is +1).
            if (qw < 0f) {
                qx = -qx
                qy = -qy
                qz = -qz
                qw = -qw
            }
            out[v * 4] = qx
            out[v * 4 + 1] = qy
            out[v * 4 + 2] = qz
            out[v * 4 + 3] = qw
        }
        return out
    }
}
