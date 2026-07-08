package com.spatialkit.engine

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CPU raycaster against engine [SceneData] content.
 *
 * Provides node picking for renderers without a native scene-graph hit test
 * (the GLES ARCore renderer, the fallback engine, future preview renderers).
 * Handles every [GeometryType] — analytic intersections for the unit
 * primitives and Möller–Trumbore triangle tests for custom meshes — and
 * accumulates group transforms, so nested content (groups, glTF model
 * hierarchies) is picked at its rendered pose.
 *
 * Transform chains are applied by localizing the ray level by level with each
 * node's inverse TRS, which stays exact for non-uniform scale chains where
 * composing TRS triples directly would not be.
 *
 * All methods are pure functions of their input: safe to call from any thread
 * as long as the caller snapshots the node list (SceneData is immutable).
 */
public object SceneRaycaster {
    /**
     * Casts a ray against [nodes] and returns the closest hit, or null.
     *
     * @param nodes Scene content (hierarchical, as submitted to the engine)
     * @param origin Ray origin in world space
     * @param direction Ray direction in world space (need not be normalized)
     * @return Closest hit with the mesh node's id, or null if nothing is hit
     */
    public fun raycast(
        nodes: List<SceneNode>,
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        var best: RaycastResult? = null
        nodes.forEach { node ->
            val hit = raycastNode(node, origin, direction) ?: return@forEach
            if (best == null || hit.distance < best.distance) best = hit
        }
        return best
    }

    private fun raycastNode(
        node: SceneNode,
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        if (!node.visible) return null

        // Localize the ray by the node's inverse TRS. The direction is left
        // unnormalized so parametric distances stay comparable per level; hit
        // positions are transformed back to world space before measuring.
        val transform = node.transform
        val invRotation = transform.rotation.conjugate()
        val invScale = safeInverse(transform.scale)
        val localOrigin = (invRotation * (origin - transform.position)) * invScale
        val localDirection = (invRotation * direction) * invScale

        val localHit =
            when (node) {
                is SceneNode.Group -> {
                    var best: LocalHit? = null
                    node.children.forEach { child ->
                        // Recurse in this group's local space; child hits come
                        // back already lifted into it.
                        val hit = childHit(child, localOrigin, localDirection) ?: return@forEach
                        if (best == null || hit.t < best.t) best = hit
                    }
                    best
                }

                is SceneNode.Mesh -> intersectGeometry(node, localOrigin, localDirection)
            } ?: return null

        // Lift the hit back into the caller's space. Normals transform by the
        // inverse scale (inverse-transpose of TRS), not the forward scale.
        val position = (transform.rotation * (localHit.position * transform.scale)) + transform.position
        val normal = (transform.rotation * (localHit.normal * invScale)).normalized()
        return RaycastResult(
            position = position,
            normal = normal,
            distance = (position - origin).length(),
            nodeId = localHit.nodeId,
        )
    }

    private fun childHit(
        child: SceneNode,
        origin: Vec3,
        direction: Vec3,
    ): LocalHit? {
        val result = raycastNode(child, origin, direction) ?: return null
        // Reuse the world-space math one level down: positions/normals are in
        // the parent's local space here; t carries the closest-hit ordering.
        return LocalHit(
            t = result.distance,
            position = result.position,
            normal = result.normal,
            nodeId = result.nodeId ?: child.id,
        )
    }

    private fun intersectGeometry(
        mesh: SceneNode.Mesh,
        origin: Vec3,
        direction: Vec3,
    ): LocalHit? {
        val hit =
            when (mesh.geometry.type) {
                GeometryType.CUBE -> intersectBox(origin, direction)
                GeometryType.SPHERE -> intersectSphere(origin, direction)
                GeometryType.PLANE -> intersectPlane(origin, direction)
                GeometryType.CYLINDER -> intersectCylinder(origin, direction)
                // Approximated as the union of the unit cylinder and unit
                // sphere — close enough for picking
                GeometryType.CAPSULE ->
                    closest(
                        intersectCylinder(origin, direction),
                        intersectSphere(origin, direction),
                    )

                GeometryType.CUSTOM_MESH -> intersectTriangles(mesh.geometry, origin, direction)
            } ?: return null
        return LocalHit(hit.t, hit.position, hit.normal, mesh.id)
    }

    // === Primitive intersections (unit primitives in local space) ===
    // Conventions match the renderers: cube 1×1×1, sphere ⌀1, plane 1×1 in
    // XZ facing +Y, cylinder r=0.5 h=1 — all centered at the origin.

    private class Hit(
        val t: Float,
        val position: Vec3,
        val normal: Vec3,
    )

    private class LocalHit(
        val t: Float,
        val position: Vec3,
        val normal: Vec3,
        val nodeId: String,
    )

    private fun closest(
        a: Hit?,
        b: Hit?,
    ): Hit? =
        when {
            a == null -> b
            b == null -> a
            a.t <= b.t -> a
            else -> b
        }

    private fun intersectBox(
        origin: Vec3,
        direction: Vec3,
    ): Hit? {
        val half = 0.5f
        var tMin = -Float.MAX_VALUE
        var tMax = Float.MAX_VALUE
        var entryAxis = -1
        var entrySign = 0f

        for (axis in 0..2) {
            val o = origin.component(axis)
            val d = direction.component(axis)
            if (abs(d) < EPSILON) {
                if (o < -half || o > half) return null
                continue
            }
            val t1 = (-half - o) / d
            val t2 = (half - o) / d
            val near = minOf(t1, t2)
            val far = maxOf(t1, t2)
            if (near > tMin) {
                tMin = near
                entryAxis = axis
                entrySign = if (d < 0f) 1f else -1f
            }
            if (far < tMax) tMax = far
            if (tMin > tMax) return null
        }

        if (tMax < 0f) return null
        // Inside the box: report the exit face so dragging something you are
        // inside of still resolves
        val t = if (tMin >= 0f) tMin else tMax
        val position = origin + direction * t
        val normal =
            when {
                entryAxis == 0 -> Vec3(entrySign, 0f, 0f)
                entryAxis == 1 -> Vec3(0f, entrySign, 0f)
                else -> Vec3(0f, 0f, entrySign)
            }
        return Hit(t, position, if (tMin >= 0f) normal else -normal)
    }

    private fun intersectSphere(
        origin: Vec3,
        direction: Vec3,
    ): Hit? {
        val radius = 0.5f
        val a = direction.dot(direction)
        if (a < EPSILON * EPSILON) return null
        val b = 2f * origin.dot(direction)
        val c = origin.dot(origin) - radius * radius
        val discriminant = b * b - 4f * a * c
        if (discriminant < 0f) return null

        val sqrtD = sqrt(discriminant)
        val t1 = (-b - sqrtD) / (2f * a)
        val t2 = (-b + sqrtD) / (2f * a)
        val t =
            when {
                t1 >= 0f -> t1
                t2 >= 0f -> t2
                else -> return null
            }

        val position = origin + direction * t
        return Hit(t, position, position.normalized())
    }

    private fun intersectPlane(
        origin: Vec3,
        direction: Vec3,
    ): Hit? {
        if (abs(direction.y) < EPSILON) return null
        val t = -origin.y / direction.y
        if (t < 0f) return null

        val position = origin + direction * t
        if (abs(position.x) > 0.5f || abs(position.z) > 0.5f) return null
        // Planes render double-sided; face the ray
        val normal = if (origin.y >= 0f) Vec3(0f, 1f, 0f) else Vec3(0f, -1f, 0f)
        return Hit(t, position, normal)
    }

    private fun intersectCylinder(
        origin: Vec3,
        direction: Vec3,
    ): Hit? {
        val radius = 0.5f
        val halfHeight = 0.5f
        var best: Hit? = null

        // Side surface
        val a = direction.x * direction.x + direction.z * direction.z
        if (a > EPSILON * EPSILON) {
            val b = 2f * (origin.x * direction.x + origin.z * direction.z)
            val c = origin.x * origin.x + origin.z * origin.z - radius * radius
            val discriminant = b * b - 4f * a * c
            if (discriminant >= 0f) {
                val sqrtD = sqrt(discriminant)
                for (t in floatArrayOf((-b - sqrtD) / (2f * a), (-b + sqrtD) / (2f * a))) {
                    if (t < 0f) continue
                    val p = origin + direction * t
                    if (abs(p.y) <= halfHeight) {
                        best = closest(best, Hit(t, p, Vec3(p.x, 0f, p.z).normalized()))
                        break
                    }
                }
            }
        }

        // End caps
        if (abs(direction.y) > EPSILON) {
            for (capY in floatArrayOf(halfHeight, -halfHeight)) {
                val t = (capY - origin.y) / direction.y
                if (t < 0f) continue
                val p = origin + direction * t
                if (p.x * p.x + p.z * p.z <= radius * radius) {
                    best = closest(best, Hit(t, p, Vec3(0f, if (capY > 0f) 1f else -1f, 0f)))
                }
            }
        }
        return best
    }

    private fun intersectTriangles(
        geometry: GeometryData,
        origin: Vec3,
        direction: Vec3,
    ): Hit? {
        val vertices = geometry.vertices ?: return null
        val vertexCount = vertices.size / 3
        val indices = geometry.indices

        var bestT = Float.MAX_VALUE
        var bestNormal = Vec3.ZERO
        var found = false

        val triangleCount = (indices?.size ?: vertexCount) / 3
        for (tri in 0 until triangleCount) {
            val ia = indices?.get(tri * 3) ?: (tri * 3)
            val ib = indices?.get(tri * 3 + 1) ?: (tri * 3 + 1)
            val ic = indices?.get(tri * 3 + 2) ?: (tri * 3 + 2)
            if (ia >= vertexCount || ib >= vertexCount || ic >= vertexCount || ia < 0 || ib < 0 || ic < 0) continue

            // Möller–Trumbore, allocation-free
            val ax = vertices[ia * 3]
            val ay = vertices[ia * 3 + 1]
            val az = vertices[ia * 3 + 2]
            val e1x = vertices[ib * 3] - ax
            val e1y = vertices[ib * 3 + 1] - ay
            val e1z = vertices[ib * 3 + 2] - az
            val e2x = vertices[ic * 3] - ax
            val e2y = vertices[ic * 3 + 1] - ay
            val e2z = vertices[ic * 3 + 2] - az

            // p = direction × e2
            val px = direction.y * e2z - direction.z * e2y
            val py = direction.z * e2x - direction.x * e2z
            val pz = direction.x * e2y - direction.y * e2x

            val det = e1x * px + e1y * py + e1z * pz
            // Two-sided test: meshes render without reliable culling guarantees
            if (abs(det) < EPSILON) continue
            val invDet = 1f / det

            val tx = origin.x - ax
            val ty = origin.y - ay
            val tz = origin.z - az

            val u = (tx * px + ty * py + tz * pz) * invDet
            if (u < 0f || u > 1f) continue

            // q = t × e1
            val qx = ty * e1z - tz * e1y
            val qy = tz * e1x - tx * e1z
            val qz = tx * e1y - ty * e1x

            val v = (direction.x * qx + direction.y * qy + direction.z * qz) * invDet
            if (v < 0f || u + v > 1f) continue

            val t = (e2x * qx + e2y * qy + e2z * qz) * invDet
            if (t < 0f || t >= bestT) continue

            bestT = t
            found = true
            // Face normal = e1 × e2, flipped toward the ray origin
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x
            val facing = nx * direction.x + ny * direction.y + nz * direction.z
            bestNormal = if (facing > 0f) Vec3(-nx, -ny, -nz) else Vec3(nx, ny, nz)
        }

        if (!found) return null
        val position = origin + direction * bestT
        return Hit(bestT, position, bestNormal.normalized())
    }

    private fun safeInverse(scale: Vec3): Vec3 =
        Vec3(
            if (scale.x != 0f) 1f / scale.x else 1f,
            if (scale.y != 0f) 1f / scale.y else 1f,
            if (scale.z != 0f) 1f / scale.z else 1f,
        )

    private fun Vec3.component(index: Int): Float =
        when (index) {
            0 -> x
            1 -> y
            else -> z
        }

    private const val EPSILON = 1e-6f
}
