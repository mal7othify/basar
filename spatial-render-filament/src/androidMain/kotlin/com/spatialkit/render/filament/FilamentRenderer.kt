package com.spatialkit.render.filament

import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.spatialkit.render.AnimationClip
import com.spatialkit.render.AnimationState
import com.spatialkit.render.ModelHandle
import com.spatialkit.render.RenderCapabilities
import com.spatialkit.render.SceneRenderer
import java.nio.ByteBuffer

/**
 * Android Filament implementation of [SceneRenderer].
 *
 * R0 scope: renders loaded glTF models with a fixed camera, a directional sun
 * and a solid-color skybox in a non-AR view. The AR camera feed, IBL,
 * occlusion and `SceneData` sync arrive in R1/R4/R5.
 *
 * All calls (including construction) must happen on the frame thread — for
 * Android that is the main thread, where [FilamentSurfaceHost]'s Choreographer
 * callback runs.
 */
public class FilamentRenderer : SceneRenderer {
    init {
        FilamentInit.ensure()
    }

    internal val engine: Engine = Engine.create()
    internal val renderer: Renderer = engine.createRenderer()
    internal val scene: Scene = engine.createScene()
    internal val view: View = engine.createView()
    internal val camera: Camera =
        engine.createCamera(engine.entityManager.create()).apply {
            // Physical exposure defaults (ModelViewer's): sunny-16.
            setExposure(16f, 1f / 125f, 100f)
            lookAt(0.0, 0.9, 2.6, 0.0, 0.4, 0.0, 0.0, 1.0, 0.0)
        }

    private val assetLoader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)

    private val skybox: Skybox =
        Skybox
            .Builder()
            .color(0.035f, 0.037f, 0.045f, 1.0f)
            .build(engine)
    private val sunEntity: Int = engine.entityManager.create()

    private class LoadedModel(
        val asset: FilamentAsset,
        var animation: AnimationState?,
        /** Frame-clock time at which the current animation state started. */
        var animationStartNanos: Long = -1L,
    )

    private val models = LinkedHashMap<Long, LoadedModel>()
    private var nextModelId = 1L
    internal var swapChain: SwapChain? = null

    override val capabilities: RenderCapabilities =
        RenderCapabilities(
            supportsIbl = true, // wired in R4; the engine itself is capable
            supportsShadows = true,
            supportsOcclusion = false, // depth-write background lands in R5
            supportsAnimation = true,
        )

    init {
        view.scene = scene
        view.camera = camera
        scene.skybox = skybox

        LightManager
            .Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.98f, 0.94f)
            .intensity(110_000.0f)
            .direction(-0.28f, -0.78f, -0.56f)
            .castShadows(true)
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)
    }

    override suspend fun loadModel(glb: ByteArray): ModelHandle {
        val buffer =
            ByteBuffer.allocateDirect(glb.size).apply {
                put(glb)
                rewind()
            }
        val asset =
            requireNotNull(assetLoader.createAsset(buffer)) { "gltfio could not parse the GLB asset" }
        // Synchronous resource load is fine at R0 asset sizes; async streaming
        // (asyncBeginLoad + per-frame asyncUpdateLoad) replaces this in R3.
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)

        val id = nextModelId++
        models[id] = LoadedModel(asset, animation = null)
        fitToUnitishCube(asset)

        val animator = asset.instance.animator
        val clips =
            (0 until animator.animationCount).map { i ->
                AnimationClip(
                    index = i,
                    name = animator.getAnimationName(i),
                    durationSeconds = animator.getAnimationDuration(i),
                )
            }
        return ModelHandle.create(id, clips)
    }

    override fun setModelTransform(
        handle: ModelHandle,
        matrix: FloatArray,
    ) {
        require(matrix.size == 16) { "Expected a 4x4 column-major matrix (16 floats), got ${matrix.size}" }
        val model = models[handle.id] ?: return
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(model.asset.root), matrix)
    }

    override fun setAnimation(
        handle: ModelHandle,
        state: AnimationState?,
    ) {
        val model = models[handle.id] ?: return
        model.animation = state
        model.animationStartNanos = -1L
    }

    override fun destroyModel(handle: ModelHandle) {
        val model = models.remove(handle.id) ?: return
        scene.removeEntities(model.asset.entities)
        assetLoader.destroyAsset(model.asset)
    }

    /**
     * Renders one frame. Driven by [FilamentSurfaceHost]'s Choreographer
     * callback; not part of the common contract at R0 (R1 replaces this with
     * `render(TrackingFrame)`).
     */
    public fun renderFrame(frameTimeNanos: Long) {
        advanceAnimations(frameTimeNanos)
        val sc = swapChain ?: return
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    internal fun onResized(
        width: Int,
        height: Int,
    ) {
        view.viewport = Viewport(0, 0, width, height)
        val aspect = if (height == 0) 1.0 else width.toDouble() / height.toDouble()
        camera.setProjection(45.0, aspect, 0.05, 100.0, Camera.Fov.VERTICAL)
    }

    override fun dispose() {
        models.values.forEach { model ->
            scene.removeEntities(model.asset.entities)
            assetLoader.destroyAsset(model.asset)
        }
        models.clear()
        resourceLoader.destroy()
        assetLoader.destroy()

        swapChain?.let(engine::destroySwapChain)
        swapChain = null
        engine.destroyEntity(sunEntity)
        engine.destroySkybox(skybox)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        val cameraEntity = camera.entity
        engine.destroyCameraComponent(cameraEntity)
        engine.entityManager.destroy(cameraEntity)
        engine.destroy()
    }

    private fun advanceAnimations(frameTimeNanos: Long) {
        for (model in models.values) {
            val state = model.animation ?: continue
            if (!state.playing) continue
            val animator = model.asset.instance.animator
            if (state.clip !in 0 until animator.animationCount) continue

            if (model.animationStartNanos < 0) model.animationStartNanos = frameTimeNanos
            val elapsed = (frameTimeNanos - model.animationStartNanos) * 1e-9f * state.speed + state.timeOffset
            val duration = animator.getAnimationDuration(state.clip)
            val time =
                when {
                    duration <= 0f -> 0f
                    state.loop -> elapsed.mod(duration)
                    else -> elapsed.coerceAtMost(duration)
                }
            animator.applyAnimation(state.clip, time)
            animator.updateBoneMatrices()
        }
    }

    /**
     * R0 convenience: scales/centers the asset so any model shows up nicely in
     * the fixed spike camera (Fox.glb is ~100 units tall). Overwritten by any
     * later [setModelTransform] call.
     */
    private fun fitToUnitishCube(asset: FilamentAsset) {
        val box = asset.boundingBox
        val halfExtent = box.halfExtent
        val center = box.center
        val maxExtent = 2f * maxOf(halfExtent[0], halfExtent[1], halfExtent[2])
        if (maxExtent <= 0f) return
        val scale = 1.5f / maxExtent
        // Column-major TRS: uniform scale, then translate so the box center
        // sits slightly above the origin (model roughly fills the camera).
        val tx = -center[0] * scale
        val ty = -center[1] * scale + 0.4f
        val tz = -center[2] * scale
        val matrix =
            floatArrayOf(
                scale,
                0f,
                0f,
                0f,
                0f,
                scale,
                0f,
                0f,
                0f,
                0f,
                scale,
                0f,
                tx,
                ty,
                tz,
                1f,
            )
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(asset.root), matrix)
    }
}

/** One-time process-wide Filament native library initialization. */
internal object FilamentInit {
    private var initialized = false

    fun ensure() {
        if (!initialized) {
            com.google.android.filament.Filament
                .init()
            com.google.android.filament.gltfio.Gltfio
                .init()
            initialized = true
        }
    }
}
