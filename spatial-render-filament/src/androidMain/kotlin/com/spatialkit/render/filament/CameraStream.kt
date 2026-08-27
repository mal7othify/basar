package com.spatialkit.render.filament

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.spatialkit.tracking.CameraImage
import com.spatialkit.tracking.DepthImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Draws the AR camera image as a fullscreen clip-space quad behind the
 * scene. Two material variants (R5):
 *
 * - `camera_flat` — image only, no depth write.
 * - `camera_depth` — additionally writes real-world depth from the frame's
 *   [DepthImage] (DEPTH16 packed into an RG8 texture), so virtual geometry
 *   z-tests against the real scene — per-pixel occlusion with no changes
 *   to any other material.
 *
 * The variant is picked per frame by whether a depth image is present; the
 * external-OES texture id comes from the tracking provider's [CameraImage];
 * the per-frame display UV transform is uploaded into the quad's uv0 buffer
 * when it changes (device rotation).
 */
internal class CameraStream(
    private val engine: Engine,
    private val scene: Scene,
    materialBytes: ByteArray,
    depthMaterialBytes: ByteArray? = null,
) {
    private val material: Material = buildMaterial(engine, materialBytes)
    private val materialInstance: MaterialInstance = material.createInstance()

    private val depthMaterial: Material? = depthMaterialBytes?.let { buildMaterial(engine, it) }
    private val depthMaterialInstance: MaterialInstance? = depthMaterial?.createInstance()
    private var depthTexture: Texture? = null
    private var usingDepthVariant = false
    private val depthSampler =
        TextureSampler(
            TextureSampler.MinFilter.NEAREST,
            TextureSampler.MagFilter.NEAREST,
            TextureSampler.WrapMode.CLAMP_TO_EDGE,
        )

    private val vertexBuffer: VertexBuffer
    private val indexBuffer: IndexBuffer
    private val entity: Int = EntityManager.get().create()

    /** Imported Filament textures keyed by GL texture name — ARCore rotates
     * through the registered names, so each frame may use a different one. */
    private val importedTextures = HashMap<Int, Texture>()
    private val cameraSampler =
        TextureSampler(
            TextureSampler.MinFilter.LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.CLAMP_TO_EDGE,
        )
    private var appliedUvs: FloatArray? = null
    private var addedToScene = false

    private val uvData =
        ByteBuffer
            .allocateDirect(4 * 2 * 4)
            .order(ByteOrder.nativeOrder())

    init {
        // Clip-space quad (triangle strip order as two triangles), z at the
        // far plane; positions never change, UVs are streamed.
        val positions =
            ByteBuffer
                .allocateDirect(4 * 2 * 4)
                .order(ByteOrder.nativeOrder())
        positions.asFloatBuffer().put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))

        vertexBuffer =
            VertexBuffer
                .Builder()
                .vertexCount(4)
                .bufferCount(2)
                .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT2, 0, 8)
                .attribute(VertexBuffer.VertexAttribute.UV0, 1, VertexBuffer.AttributeType.FLOAT2, 0, 8)
                .build(engine)
        vertexBuffer.setBufferAt(engine, 0, positions)
        setUvs(floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f))

        val indices =
            ByteBuffer
                .allocateDirect(6 * 2)
                .order(ByteOrder.nativeOrder())
        indices.asShortBuffer().put(shortArrayOf(0, 1, 2, 2, 1, 3))
        indexBuffer =
            IndexBuffer
                .Builder()
                .indexCount(6)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine)
        indexBuffer.setBuffer(engine, indices)

        RenderableManager
            .Builder(1)
            .castShadows(false)
            .receiveShadows(false)
            .culling(false)
            // Render before scene content so early-z can skip covered pixels
            .priority(0)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, materialInstance)
            .build(engine, entity)
    }

    /** Per-frame update; adds the quad to the scene on the first image. */
    fun update(
        image: CameraImage,
        depth: DepthImage? = null,
    ) {
        val texture =
            importedTextures.getOrPut(image.textureId) {
                Texture
                    .Builder()
                    .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
                    .format(Texture.InternalFormat.RGB8)
                    .importTexture(image.textureId.toLong())
                    .build(engine)
            }

        val wantDepth = depth != null && depthMaterialInstance != null
        if (wantDepth != usingDepthVariant) {
            val rm = engine.renderableManager
            rm.setMaterialInstanceAt(
                rm.getInstance(entity),
                0,
                if (wantDepth) depthMaterialInstance!! else materialInstance,
            )
            usingDepthVariant = wantDepth
        }
        val activeInstance = if (usingDepthVariant) depthMaterialInstance!! else materialInstance

        // Set every frame, not just on change: ARCore updates the texture on
        // the tracking (shared) context, and GL's shared-object rules only
        // guarantee the render context sees the new image after it re-binds
        // the texture. A stale binding samples uninitialized memory (noise).
        activeInstance.setParameter("cameraTexture", texture, cameraSampler)
        if (wantDepth) uploadDepth(depth!!, activeInstance)

        if (!image.uvTransform.contentEquals(appliedUvs)) {
            setUvs(image.uvTransform)
        }

        if (!addedToScene) {
            scene.addEntity(entity)
            addedToScene = true
        }
    }

    /**
     * Uploads DEPTH16 millimeters as an RG8 texture (r = low byte, g = high
     * byte — recombined in the material) and binds it. The texture is
     * recreated only when the depth image size changes.
     */
    private fun uploadDepth(
        depth: DepthImage,
        instance: MaterialInstance,
    ) {
        var texture = depthTexture
        if (texture == null || texture.getWidth(0) != depth.width || texture.getHeight(0) != depth.height) {
            texture?.let(engine::destroyTexture)
            texture =
                Texture
                    .Builder()
                    .width(depth.width)
                    .height(depth.height)
                    .levels(1)
                    .format(Texture.InternalFormat.RG8)
                    .build(engine)
            depthTexture = texture
        }
        texture.setImage(
            engine,
            0,
            Texture.PixelBufferDescriptor(
                depth.buffer.duplicate() as ByteBuffer,
                Texture.Format.RG,
                Texture.Type.UBYTE,
            ),
        )
        instance.setParameter("depthTexture", texture, depthSampler)
    }

    private fun setUvs(uvs: FloatArray) {
        uvData.rewind()
        uvData.asFloatBuffer().put(uvs)
        vertexBuffer.setBufferAt(engine, 1, uvData)
        appliedUvs = uvs.copyOf()
    }

    fun destroy() {
        if (addedToScene) scene.removeEntity(entity)
        engine.destroyEntity(entity)
        EntityManager.get().destroy(entity)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
        importedTextures.values.forEach(engine::destroyTexture)
        importedTextures.clear()
        depthTexture?.let(engine::destroyTexture)
        depthTexture = null
        engine.destroyMaterialInstance(materialInstance)
        engine.destroyMaterial(material)
        depthMaterialInstance?.let(engine::destroyMaterialInstance)
        depthMaterial?.let(engine::destroyMaterial)
    }
}

private fun buildMaterial(
    engine: Engine,
    bytes: ByteArray,
): Material =
    Material
        .Builder()
        .payload(
            ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            },
            bytes.size,
        ).build(engine)
