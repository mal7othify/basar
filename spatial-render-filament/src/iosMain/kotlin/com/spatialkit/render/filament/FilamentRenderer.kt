package com.spatialkit.render.filament

import cnames.structs.FbrEngine
import com.spatialkit.render.AnimationClip
import com.spatialkit.render.AnimationState
import com.spatialkit.render.ModelHandle
import com.spatialkit.render.RenderCapabilities
import com.spatialkit.render.SceneData
import com.spatialkit.render.SceneRenderer
import com.spatialkit.render.filament.bridge.fbr_animation_count
import com.spatialkit.render.filament.bridge.fbr_animation_duration
import com.spatialkit.render.filament.bridge.fbr_animation_name
import com.spatialkit.render.filament.bridge.fbr_clear_animation
import com.spatialkit.render.filament.bridge.fbr_create
import com.spatialkit.render.filament.bridge.fbr_destroy
import com.spatialkit.render.filament.bridge.fbr_destroy_asset
import com.spatialkit.render.filament.bridge.fbr_load_glb
import com.spatialkit.render.filament.bridge.fbr_make_view
import com.spatialkit.render.filament.bridge.fbr_set_animation
import com.spatialkit.render.filament.bridge.fbr_set_transform
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.UIKit.UIView

/**
 * iOS Filament implementation of [SceneRenderer], backed by the
 * FilamentBridge C API (native/filament-bridge) over Filament's Metal
 * backend. Rendering is driven by the bridge view's internal display link;
 * all calls must happen on the main thread.
 *
 * R0 scope mirrors the Android renderer: fixed camera, directional sun,
 * solid-color skybox, glTF models with declarative animation state.
 */
@OptIn(ExperimentalForeignApi::class)
public class FilamentRenderer : SceneRenderer {
    private val engine: CPointer<FbrEngine> =
        requireNotNull(fbr_create()) { "Filament could not start its Metal backend" }
    private var disposed = false

    override val capabilities: RenderCapabilities =
        RenderCapabilities(
            supportsIbl = true, // wired in R4; the engine itself is capable
            supportsShadows = true,
            supportsOcclusion = false, // LiDAR depth-write background lands in R5
            supportsAnimation = true,
        )

    /**
     * Returns the CAMetalLayer-backed view rendering this engine's scene.
     * Host it as a NON-interactive interop view (`interactionMode = null`) so
     * Compose keeps receiving touches — the 547ae24 lesson.
     */
    public fun makeView(): UIView = fbr_make_view(engine)

    override fun render(
        frame: com.spatialkit.tracking.TrackingFrame?,
        frameTimeNanos: Long,
    ) {
        // R0/R1: the bridge view's internal display link drives rendering and
        // animation on iOS; AR frame consumption (camera matrices + captured
        // image) is wired through the bridge in R2.
    }

    override fun submitScene(scene: SceneData) {
        // R3 scene submission lands on iOS together with the R2 ARKit
        // adapter (needs fbr_* bridge surface for primitive meshes and
        // materials). Until then iOS renders models via loadModel only.
    }

    override suspend fun loadModel(glb: ByteArray): ModelHandle {
        check(!disposed) { "Renderer is disposed" }
        require(glb.isNotEmpty()) { "Empty GLB buffer" }
        val id =
            glb.usePinned { pinned ->
                fbr_load_glb(engine, pinned.addressOf(0).reinterpret(), glb.size.convert())
            }
        require(id != 0L) { "gltfio could not parse the GLB asset" }

        val clips =
            (0 until fbr_animation_count(engine, id)).map { i ->
                AnimationClip(
                    index = i,
                    name = fbr_animation_name(engine, id, i)?.toKString().orEmpty(),
                    durationSeconds = fbr_animation_duration(engine, id, i),
                )
            }
        return ModelHandle.create(id, clips)
    }

    override fun setModelTransform(
        handle: ModelHandle,
        matrix: FloatArray,
    ) {
        require(matrix.size == 16) { "Expected a 4x4 column-major matrix (16 floats), got ${matrix.size}" }
        if (disposed) return
        matrix.usePinned { pinned ->
            fbr_set_transform(engine, handle.id, pinned.addressOf(0))
        }
    }

    override fun setAnimation(
        handle: ModelHandle,
        state: AnimationState?,
    ) {
        if (disposed) return
        if (state == null) {
            fbr_clear_animation(engine, handle.id)
        } else {
            fbr_set_animation(
                engine,
                handle.id,
                state.clip,
                state.playing,
                state.loop,
                state.speed,
                state.timeOffset,
            )
        }
    }

    override fun destroyModel(handle: ModelHandle) {
        if (disposed) return
        fbr_destroy_asset(engine, handle.id)
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        fbr_destroy(engine)
    }
}
