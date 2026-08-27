package com.spatialkit.render.filament

import android.content.Context
import android.opengl.Matrix
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
import com.spatialkit.tracking.TrackingFrame
import com.spatialkit.tracking.TrackingState
import java.nio.ByteBuffer

/**
 * Android Filament implementation of [SceneRenderer].
 *
 * Two modes, decided per frame by [render]'s argument:
 * - **Non-AR** (null frame — the R0 spike, previews): fixed camera,
 *   directional sun, solid-color skybox.
 * - **AR** (a [TrackingFrame]): camera driven by the tracked pose, the
 *   camera image composited behind the scene via [CameraStream]. Requires
 *   the constructor's [context] (to load the camera material) and
 *   [sharedEglContext] (the [EglContextHelper] context the camera OES
 *   texture lives on).
 *
 * All calls (including construction) must happen on the frame thread — the
 * main thread, where the Choreographer-driven hosts run.
 */
public class FilamentRenderer(
    private val context: Context? = null,
    sharedEglContext: Any? = null,
) : SceneRenderer {
    init {
        FilamentInit.ensure()
    }

    internal val engine: Engine =
        if (sharedEglContext != null) {
            // Backend must be OpenGL explicitly: the AR camera path shares GL
            // texture names with the tracking context, and Filament's default
            // Android backend is no longer guaranteed to be GL (Vulkan default
            // since ~1.6x). Engine.create(sharedContext)'s docs claim OpenGL,
            // but the implementation only sets the shared context.
            Engine
                .Builder()
                .backend(Engine.Backend.OPENGL)
                .sharedContext(sharedEglContext)
                .build()
        } else {
            Engine.create()
        }
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

    private var cameraStream: CameraStream? = null
    private var planeCatchers: PlaneShadowCatchers? = null
    private val iblManager: IblManager by lazy { IblManager(engine, scene, sunEntity) }
    private var iblStarted = false
    private var arMode = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val cameraToWorld = FloatArray(16)

    override val capabilities: RenderCapabilities =
        RenderCapabilities(
            supportsIbl = true, // R4: HDR estimate feeds IndirectLight + sun
            supportsShadows = true, // R5: shadow maps + plane shadow catchers
            // R5: the depth-writing camera background occludes per pixel —
            // effective only when the tracking session produces depth images
            // (TrackingConfig.depth on a Depth-API device).
            supportsOcclusion = true,
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
            .shadowOptions(
                LightManager.ShadowOptions().apply {
                    mapSize = 2048
                    // Short-range detail where the shadow map runs out (R5).
                    screenSpaceContactShadows = true
                },
            ).build(engine, sunEntity)
        scene.addEntity(sunEntity)
    }

    private val sceneSync: SceneGraphSync by lazy {
        SceneGraphSync(
            engine = engine,
            scene = scene,
            context =
                requireNotNull(context) {
                    "FilamentRenderer needs a Context to load the standard scene materials"
                },
            assetLoader = assetLoader,
            resourceLoader = resourceLoader,
        )
    }
    private var sceneSyncStarted = false

    override fun submitScene(scene: com.spatialkit.render.SceneData) {
        sceneSyncStarted = true
        sceneSync.submit(scene)
    }

    override fun render(
        frame: TrackingFrame?,
        frameTimeNanos: Long,
    ) {
        advanceAnimations(frameTimeNanos)
        if (sceneSyncStarted) sceneSync.advanceAnimations(frameTimeNanos)

        if (frame != null) {
            if (!arMode) {
                // First AR frame: the camera feed replaces the skybox.
                arMode = true
                scene.skybox = null
            }
            frame.cameraImage?.let { image ->
                val stream =
                    cameraStream ?: CameraStream(
                        engine,
                        scene,
                        loadRenderAsset("materials/camera_flat.filamat"),
                        loadRenderAsset("materials/camera_depth.filamat"),
                    ).also { cameraStream = it }
                stream.update(image, frame.depthImage)
            }
            if (frame.planes.isNotEmpty() || planeCatchers != null) {
                val catchers =
                    planeCatchers ?: PlaneShadowCatchers(
                        engine,
                        scene,
                        loadRenderAsset("materials/shadow_catcher.filamat"),
                    ).also { planeCatchers = it }
                catchers.update(frame.planes)
            }
            frame.lightEstimate?.let { estimate ->
                iblStarted = true
                iblManager.update(estimate)
            }
            if (frame.trackingState == TrackingState.NORMAL) {
                camera.setCustomProjection(
                    DoubleArray(16) { i -> frame.projectionMatrix[i].toDouble() },
                    NEAR_PLANE,
                    FAR_PLANE,
                )
                Matrix.invertM(cameraToWorld, 0, frame.viewMatrix, 0)
                camera.setModelMatrix(cameraToWorld)
            }
        }

        val sc = swapChain ?: return
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    override suspend fun loadModel(glb: ByteArray): ModelHandle {
        val buffer =
            ByteBuffer.allocateDirect(glb.size).apply {
                put(glb)
                rewind()
            }
        val asset =
            requireNotNull(assetLoader.createAsset(buffer)) { "gltfio could not parse the GLB asset" }
        // Synchronous resource load is fine at R0/R1 asset sizes; async
        // streaming (asyncBeginLoad + per-frame asyncUpdateLoad) lands in R3.
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)

        val id = nextModelId++
        models[id] = LoadedModel(asset, animation = null)
        if (!arMode) fitToUnitishCube(asset)

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

    internal fun onResized(
        width: Int,
        height: Int,
    ) {
        surfaceWidth = width
        surfaceHeight = height
        view.viewport = Viewport(0, 0, width, height)
        if (!arMode) {
            // AR projection comes from the TrackingFrame; only the non-AR
            // camera needs an aspect-derived projection.
            val aspect = if (height == 0) 1.0 else width.toDouble() / height.toDouble()
            camera.setProjection(45.0, aspect, NEAR_PLANE, FAR_PLANE, Camera.Fov.VERTICAL)
        }
    }

    override fun dispose() {
        models.values.forEach { model ->
            scene.removeEntities(model.asset.entities)
            assetLoader.destroyAsset(model.asset)
        }
        models.clear()
        if (sceneSyncStarted) sceneSync.dispose()
        if (iblStarted) iblManager.dispose()
        planeCatchers?.destroy()
        planeCatchers = null
        cameraStream?.destroy()
        cameraStream = null
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

    private fun loadRenderAsset(assetPath: String): ByteArray {
        val ctx =
            requireNotNull(context) {
                "FilamentRenderer needs a Context to load '$assetPath'"
            }
        return ctx.assets.open(assetPath).use { it.readBytes() }
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
     * Non-AR convenience: scales/centers the asset so any model shows up in
     * the fixed spike camera (Fox.glb is ~100 units tall). Overwritten by
     * any later [setModelTransform] call; skipped in AR mode where real
     * scale is the point.
     */
    private fun fitToUnitishCube(asset: FilamentAsset) {
        val box = asset.boundingBox
        val halfExtent = box.halfExtent
        val center = box.center
        val maxExtent = 2f * maxOf(halfExtent[0], halfExtent[1], halfExtent[2])
        if (maxExtent <= 0f) return
        val scale = 1.5f / maxExtent
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

    private companion object {
        const val NEAR_PLANE = 0.1
        const val FAR_PLANE = 100.0
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
            // filament-utils has its own JNI layer (IBLPrefilterContext, KTX
            // loading) — without this, first use throws UnsatisfiedLinkError.
            com.google.android.filament.utils.Utils
                .init()
            initialized = true
        }
    }
}
