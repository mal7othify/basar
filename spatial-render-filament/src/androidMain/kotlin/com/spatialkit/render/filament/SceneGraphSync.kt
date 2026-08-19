package com.spatialkit.render.filament

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.Matrix
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler as FilamentSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.spatialkit.render.AnimationState
import com.spatialkit.render.GeometryData
import com.spatialkit.render.MaterialData
import com.spatialkit.render.MaterialType
import com.spatialkit.render.SceneData
import com.spatialkit.render.SceneNode
import com.spatialkit.render.TextureData
import com.spatialkit.render.TextureFilter
import com.spatialkit.render.TextureWrap
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Retained-mode sync of [SceneData] snapshots into Filament (R3).
 *
 * State is keyed by [SceneNode.id]. Between consecutive [submit] calls:
 * transform changes update the transform manager in place, visibility
 * toggles add/remove the entity from the scene, material changes update the
 * per-node material instance, geometry changes rebuild only that node's
 * buffers, and nodes that disappeared are destroyed. Submitting an
 * unchanged snapshot does no GPU work.
 *
 * Frame-thread only, like the renderer that owns it.
 */
internal class SceneGraphSync(
    private val engine: Engine,
    private val scene: Scene,
    private val context: Context,
    private val assetLoader: AssetLoader,
    private val resourceLoader: ResourceLoader,
) {
    private val litMaterial: Material by lazy { loadMaterial("materials/standard_lit.filamat") }
    private val unlitMaterial: Material by lazy { loadMaterial("materials/standard_unlit.filamat") }

    /** 1x1 white so the shader can multiply factor × texture unconditionally. */
    private val whiteTexture: Texture by lazy {
        Texture
            .Builder()
            .width(1)
            .height(1)
            .levels(1)
            .format(Texture.InternalFormat.RGBA8)
            .build(engine)
            .also { tex ->
                val data = ByteBuffer.allocateDirect(4).put(byteArrayOf(-1, -1, -1, -1)).apply { rewind() }
                tex.setImage(engine, 0, Texture.PixelBufferDescriptor(data, Texture.Format.RGBA, Texture.Type.UBYTE))
            }
    }

    private val textures = HashMap<String, Texture>()

    private class MeshState(
        val entity: Int,
        var vertexBuffer: VertexBuffer,
        var indexBuffer: IndexBuffer,
        var materialInstance: MaterialInstance,
        var isLit: Boolean,
        var geometry: GeometryData,
        var material: MaterialData,
        var worldMatrix: FloatArray,
        var inScene: Boolean,
    )

    private class ModelState(
        val asset: FilamentAsset,
        var contentKey: String,
        var animation: AnimationState?,
        var animationStartNanos: Long,
        var worldMatrix: FloatArray,
        var inScene: Boolean,
    )

    private val meshes = LinkedHashMap<String, MeshState>()
    private val models = LinkedHashMap<String, ModelState>()

    // Scratch collections reused across submits to avoid per-frame garbage.
    private val liveIds = HashSet<String>()
    private val liveTextureIds = HashSet<String>()
    private val scratchMatrix = FloatArray(16)

    fun submit(sceneData: SceneData) {
        liveIds.clear()
        liveTextureIds.clear()

        val identity = IDENTITY
        for (node in sceneData.nodes) {
            syncNode(node, identity)
        }

        // Destroy state for nodes gone from the snapshot.
        val meshIterator = meshes.entries.iterator()
        while (meshIterator.hasNext()) {
            val (id, state) = meshIterator.next()
            if (id !in liveIds) {
                destroyMesh(state)
                meshIterator.remove()
            }
        }
        val modelIterator = models.entries.iterator()
        while (modelIterator.hasNext()) {
            val (id, state) = modelIterator.next()
            if (id !in liveIds) {
                destroyModel(state)
                modelIterator.remove()
            }
        }
        val textureIterator = textures.entries.iterator()
        while (textureIterator.hasNext()) {
            val (id, texture) = textureIterator.next()
            if (id !in liveTextureIds) {
                engine.destroyTexture(texture)
                textureIterator.remove()
            }
        }
    }

    /** Advances glTF animation clocks for scene models; call once per frame. */
    fun advanceAnimations(frameTimeNanos: Long) {
        for (state in models.values) {
            val animation = state.animation ?: continue
            if (!animation.playing) continue
            val animator = state.asset.instance.animator
            if (animation.clip !in 0 until animator.animationCount) continue

            if (state.animationStartNanos < 0) state.animationStartNanos = frameTimeNanos
            val elapsed =
                (frameTimeNanos - state.animationStartNanos) * 1e-9f * animation.speed + animation.timeOffset
            val duration = animator.getAnimationDuration(animation.clip)
            val time =
                when {
                    duration <= 0f -> 0f
                    animation.loop -> elapsed.mod(duration)
                    else -> elapsed.coerceAtMost(duration)
                }
            animator.applyAnimation(animation.clip, time)
            animator.updateBoneMatrices()
        }
    }

    fun dispose() {
        meshes.values.forEach(::destroyMesh)
        meshes.clear()
        models.values.forEach(::destroyModel)
        models.clear()
        textures.values.forEach(engine::destroyTexture)
        textures.clear()
        engine.destroyTexture(whiteTexture)
        engine.destroyMaterial(litMaterial)
        engine.destroyMaterial(unlitMaterial)
    }

    private fun syncNode(
        node: SceneNode,
        parentMatrix: FloatArray,
    ) {
        when (node) {
            is SceneNode.Group -> {
                // v1 semantics: an invisible group hides its whole subtree.
                if (!node.visible) return
                val composed = FloatArray(16)
                Matrix.multiplyMM(composed, 0, parentMatrix, 0, node.transform.toMatrix().toFloatArray(), 0)
                node.children.forEach { syncNode(it, composed) }
            }

            is SceneNode.Mesh -> {
                liveIds.add(node.id)
                node.material.baseColorTexture?.let { liveTextureIds.add(it.id) }
                Matrix.multiplyMM(scratchMatrix, 0, parentMatrix, 0, node.transform.toMatrix().toFloatArray(), 0)
                syncMesh(node, scratchMatrix)
            }

            is SceneNode.Model -> {
                liveIds.add(node.id)
                Matrix.multiplyMM(scratchMatrix, 0, parentMatrix, 0, node.transform.toMatrix().toFloatArray(), 0)
                syncModel(node, scratchMatrix)
            }
        }
    }

    private fun syncMesh(
        node: SceneNode.Mesh,
        worldMatrix: FloatArray,
    ) {
        var state = meshes[node.id]

        if (state != null && state.geometry !== node.geometry && state.geometry != node.geometry) {
            // Geometry replaced: rebuild the node from scratch (rare).
            destroyMesh(state)
            meshes.remove(node.id)
            state = null
        }

        if (state == null) {
            state = createMesh(node, worldMatrix)
            meshes[node.id] = state
        } else {
            if (state.material !== node.material && state.material != node.material) {
                applyMaterial(state, node.material)
            }
            if (!state.worldMatrix.contentEquals(worldMatrix)) {
                val tm = engine.transformManager
                tm.setTransform(tm.getInstance(state.entity), worldMatrix)
                worldMatrix.copyInto(state.worldMatrix)
            }
        }

        if (node.visible && !state.inScene) {
            scene.addEntity(state.entity)
            state.inScene = true
        } else if (!node.visible && state.inScene) {
            scene.removeEntity(state.entity)
            state.inScene = false
        }
    }

    private fun createMesh(
        node: SceneNode.Mesh,
        worldMatrix: FloatArray,
    ): MeshState {
        val mesh = PrimitiveMeshes.build(node.geometry)
        val tangents = TangentFrames.fromNormals(mesh.normals)

        val vertexBuffer =
            VertexBuffer
                .Builder()
                .vertexCount(mesh.vertexCount)
                .bufferCount(3)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
                .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
                .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
                .build(engine)
        vertexBuffer.setBufferAt(engine, 0, mesh.positions.toDirectBuffer())
        vertexBuffer.setBufferAt(engine, 1, tangents.toDirectBuffer())
        vertexBuffer.setBufferAt(engine, 2, mesh.uvs.toDirectBuffer())

        val indexBuffer =
            IndexBuffer
                .Builder()
                .indexCount(mesh.indices.size)
                .bufferType(IndexBuffer.Builder.IndexType.UINT)
                .build(engine)
        val indexData =
            ByteBuffer
                .allocateDirect(mesh.indices.size * 4)
                .order(ByteOrder.nativeOrder())
                .apply { asIntBuffer().put(mesh.indices) }
        indexBuffer.setBuffer(engine, indexData)

        val isLit = node.material.type != MaterialType.UNLIT
        val instance = (if (isLit) litMaterial else unlitMaterial).createInstance()

        val entity = EntityManager.get().create()
        RenderableManager
            .Builder(1)
            .boundingBox(boundsOf(mesh.positions))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, instance)
            // Real shadow casting/receiving is capability-gated R5 work.
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, entity)

        engine.transformManager.create(entity)

        val state =
            MeshState(
                entity = entity,
                vertexBuffer = vertexBuffer,
                indexBuffer = indexBuffer,
                materialInstance = instance,
                isLit = isLit,
                geometry = node.geometry,
                material = node.material,
                worldMatrix = worldMatrix.copyOf(),
                inScene = false,
            )
        applyMaterial(state, node.material)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), worldMatrix)
        return state
    }

    private fun applyMaterial(
        state: MeshState,
        material: MaterialData,
    ) {
        val needsLit = material.type != MaterialType.UNLIT
        if (needsLit != state.isLit) {
            // Shading model changed: swap the instance for one of the other
            // material, keeping the entity and buffers.
            engine.destroyMaterialInstance(state.materialInstance)
            state.materialInstance = (if (needsLit) litMaterial else unlitMaterial).createInstance()
            state.isLit = needsLit
            val rm = engine.renderableManager
            rm.setMaterialInstanceAt(rm.getInstance(state.entity), 0, state.materialInstance)
        }
        val instance = state.materialInstance
        val c = material.baseColor
        instance.setParameter("baseColorFactor", c.x, c.y, c.z, c.w)
        if (state.isLit) {
            instance.setParameter("metallic", material.metallic)
            instance.setParameter("roughness", material.roughness)
        }
        instance.setDoubleSided(material.doubleSided)
        val textureData = material.baseColorTexture
        val texture = if (textureData != null) textureFor(textureData) else whiteTexture
        instance.setParameter("baseColorMap", texture, samplerFor(textureData))
        state.material = material
    }

    private fun textureFor(data: TextureData): Texture =
        textures.getOrPut(data.id) {
            val bitmap =
                requireNotNull(BitmapFactory.decodeByteArray(data.data, 0, data.data.size)) {
                    "Could not decode texture '${data.id}' (${data.data.size} bytes, ${data.mimeType})"
                }
            val texture =
                Texture
                    .Builder()
                    .width(bitmap.width)
                    .height(bitmap.height)
                    .levels(0xff) // full mip chain
                    // Base-color images are sRGB-encoded per the SceneData contract.
                    .format(Texture.InternalFormat.SRGB8_A8)
                    .build(engine)
            TextureHelper.setBitmap(engine, texture, 0, bitmap)
            bitmap.recycle()
            texture.generateMipmaps(engine)
            texture
        }

    private fun samplerFor(data: TextureData?): FilamentSampler {
        val sampler = data?.sampler
        val minFilter =
            when (sampler?.minFilter ?: TextureFilter.LINEAR) {
                TextureFilter.NEAREST -> FilamentSampler.MinFilter.NEAREST
                TextureFilter.LINEAR -> FilamentSampler.MinFilter.LINEAR_MIPMAP_LINEAR
            }
        val magFilter =
            when (sampler?.magFilter ?: TextureFilter.LINEAR) {
                TextureFilter.NEAREST -> FilamentSampler.MagFilter.NEAREST
                TextureFilter.LINEAR -> FilamentSampler.MagFilter.LINEAR
            }
        return FilamentSampler(minFilter, magFilter, wrapMode(sampler?.wrapU), wrapMode(sampler?.wrapV), FilamentSampler.WrapMode.CLAMP_TO_EDGE)
    }

    private fun wrapMode(wrap: TextureWrap?): FilamentSampler.WrapMode =
        when (wrap ?: TextureWrap.REPEAT) {
            TextureWrap.REPEAT -> FilamentSampler.WrapMode.REPEAT
            TextureWrap.MIRRORED_REPEAT -> FilamentSampler.WrapMode.MIRRORED_REPEAT
            TextureWrap.CLAMP_TO_EDGE -> FilamentSampler.WrapMode.CLAMP_TO_EDGE
        }

    private fun syncModel(
        node: SceneNode.Model,
        worldMatrix: FloatArray,
    ) {
        var state = models[node.id]

        if (state != null && state.contentKey != node.contentKey) {
            destroyModel(state)
            models.remove(node.id)
            state = null
        }

        if (state == null) {
            val buffer =
                ByteBuffer.allocateDirect(node.glb.size).apply {
                    put(node.glb)
                    rewind()
                }
            val asset =
                requireNotNull(assetLoader.createAsset(buffer)) {
                    "gltfio could not parse the GLB asset for scene node '${node.id}'"
                }
            resourceLoader.loadResources(asset)
            asset.releaseSourceData()
            state =
                ModelState(
                    asset = asset,
                    contentKey = node.contentKey,
                    animation = node.animation,
                    animationStartNanos = -1L,
                    worldMatrix = worldMatrix.copyOf(),
                    inScene = false,
                )
            models[node.id] = state
            val tm = engine.transformManager
            tm.setTransform(tm.getInstance(asset.root), worldMatrix)
        } else {
            if (state.animation != node.animation) {
                state.animation = node.animation
                state.animationStartNanos = -1L
            }
            if (!state.worldMatrix.contentEquals(worldMatrix)) {
                val tm = engine.transformManager
                tm.setTransform(tm.getInstance(state.asset.root), worldMatrix)
                worldMatrix.copyInto(state.worldMatrix)
            }
        }

        if (node.visible && !state.inScene) {
            scene.addEntities(state.asset.entities)
            state.inScene = true
        } else if (!node.visible && state.inScene) {
            scene.removeEntities(state.asset.entities)
            state.inScene = false
        }
    }

    private fun destroyMesh(state: MeshState) {
        if (state.inScene) scene.removeEntity(state.entity)
        engine.destroyEntity(state.entity)
        EntityManager.get().destroy(state.entity)
        engine.destroyVertexBuffer(state.vertexBuffer)
        engine.destroyIndexBuffer(state.indexBuffer)
        engine.destroyMaterialInstance(state.materialInstance)
    }

    private fun destroyModel(state: ModelState) {
        if (state.inScene) scene.removeEntities(state.asset.entities)
        assetLoader.destroyAsset(state.asset)
    }

    private fun boundsOf(positions: FloatArray): Box {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            val x = positions[i]
            val y = positions[i + 1]
            val z = positions[i + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
            i += 3
        }
        return Box(
            (minX + maxX) / 2f,
            (minY + maxY) / 2f,
            (minZ + maxZ) / 2f,
            (maxX - minX) / 2f,
            (maxY - minY) / 2f,
            (maxZ - minZ) / 2f,
        )
    }

    private fun loadMaterial(assetPath: String): Material {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        return Material
            .Builder()
            .payload(
                ByteBuffer.allocateDirect(bytes.size).apply {
                    put(bytes)
                    rewind()
                },
                bytes.size,
            ).build(engine)
    }

    private companion object {
        val IDENTITY = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    }
}

private fun FloatArray.toDirectBuffer(): ByteBuffer =
    ByteBuffer
        .allocateDirect(size * 4)
        .order(ByteOrder.nativeOrder())
        .apply { asFloatBuffer().put(this@toDirectBuffer) }
