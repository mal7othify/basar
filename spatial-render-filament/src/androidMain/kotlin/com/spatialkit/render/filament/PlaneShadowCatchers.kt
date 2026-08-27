package com.spatialkit.render.filament

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.spatialkit.core.math.Transform
import com.spatialkit.core.math.Vec3
import com.spatialkit.tracking.DetectedPlane
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Invisible shadow-receiving quads on detected real-world planes (R5):
 * virtual content casts real directional shadow-map shadows onto them via
 * the `shadow_catcher` material (unlit + shadowMultiplier + transparent) —
 * unshadowed texels render nothing.
 *
 * One retained quad per plane id, sized to the plane's extents and lifted
 * ~1cm along the plane normal so it never z-fights the depth-writing
 * camera background (R5 occlusion). Stale planes are destroyed.
 *
 * Frame-thread only, owned by [FilamentRenderer].
 */
internal class PlaneShadowCatchers(
    private val engine: Engine,
    private val scene: Scene,
    materialBytes: ByteArray,
) {
    private val material: Material =
        Material
            .Builder()
            .payload(
                ByteBuffer.allocateDirect(materialBytes.size).apply {
                    put(materialBytes)
                    rewind()
                },
                materialBytes.size,
            ).build(engine)
    private val materialInstance: MaterialInstance =
        material.createInstance().apply { setParameter("strength", SHADOW_STRENGTH) }

    // One shared unit quad in the XZ plane (+Y normal), position-only —
    // the unlit catcher material needs no other attributes.
    private val vertexBuffer: VertexBuffer =
        VertexBuffer
            .Builder()
            .vertexCount(4)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .build(engine)
            .also { vb ->
                val positions =
                    ByteBuffer
                        .allocateDirect(4 * 3 * 4)
                        .order(ByteOrder.nativeOrder())
                positions.asFloatBuffer().put(
                    floatArrayOf(
                        -0.5f, 0f, 0.5f,
                        0.5f, 0f, 0.5f,
                        0.5f, 0f, -0.5f,
                        -0.5f, 0f, -0.5f,
                    ),
                )
                vb.setBufferAt(engine, 0, positions)
            }
    private val indexBuffer: IndexBuffer =
        IndexBuffer
            .Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
            .also { ib ->
                val indices =
                    ByteBuffer
                        .allocateDirect(6 * 2)
                        .order(ByteOrder.nativeOrder())
                indices.asShortBuffer().put(shortArrayOf(0, 1, 2, 0, 2, 3))
                ib.setBuffer(engine, indices)
            }

    private class CatcherState(
        val entity: Int,
        var worldMatrix: FloatArray,
    )

    private val catchers = LinkedHashMap<String, CatcherState>()
    private val liveIds = HashSet<String>()

    fun update(planes: List<DetectedPlane>) {
        liveIds.clear()
        for (plane in planes) {
            if (plane.extentX <= 0f || plane.extentZ <= 0f) continue
            liveIds.add(plane.id)

            // Lift along the plane normal so the catcher wins the depth
            // test against the depth-writing camera background.
            val pose = plane.centerPose
            val lifted = pose.position + (pose.rotation * Vec3(0f, LIFT_METERS, 0f))
            val matrix =
                Transform(
                    position = lifted,
                    rotation = pose.rotation,
                    scale = Vec3(plane.extentX, 1f, plane.extentZ),
                ).toMatrix().toFloatArray()

            val state = catchers[plane.id]
            if (state == null) {
                val entity = EntityManager.get().create()
                RenderableManager
                    .Builder(1)
                    .boundingBox(Box(0f, 0f, 0f, 0.5f, 0.01f, 0.5f))
                    .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
                    .material(0, materialInstance)
                    .castShadows(false)
                    .receiveShadows(true)
                    .build(engine, entity)
                engine.transformManager.create(entity)
                engine.transformManager.setTransform(engine.transformManager.getInstance(entity), matrix)
                scene.addEntity(entity)
                catchers[plane.id] = CatcherState(entity, matrix)
            } else if (!state.worldMatrix.contentEquals(matrix)) {
                engine.transformManager.setTransform(engine.transformManager.getInstance(state.entity), matrix)
                state.worldMatrix = matrix
            }
        }

        val iterator = catchers.entries.iterator()
        while (iterator.hasNext()) {
            val (id, state) = iterator.next()
            if (id !in liveIds) {
                destroyCatcher(state)
                iterator.remove()
            }
        }
    }

    fun destroy() {
        catchers.values.forEach(::destroyCatcher)
        catchers.clear()
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
    }

    private fun destroyCatcher(state: CatcherState) {
        scene.removeEntity(state.entity)
        engine.destroyEntity(state.entity)
        EntityManager.get().destroy(state.entity)
    }

    private companion object {
        const val SHADOW_STRENGTH = 0.6f
        const val LIFT_METERS = 0.01f
    }
}
