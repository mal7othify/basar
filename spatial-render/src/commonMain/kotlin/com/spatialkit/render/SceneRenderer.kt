package com.spatialkit.render

/**
 * THE renderer contract of the v2 architecture: one physically-based renderer
 * (Filament) shared by every platform, fed by a thin per-platform tracking
 * adapter (see `docs/V2_REALISM_REWRITE.md` §4).
 *
 * R0 scope: model loading + animation control. Scene submission
 * (`submitScene(SceneData)`) and AR frame consumption (`render(TrackingFrame)`)
 * join the contract in R1 when `spatial-tracking` exists.
 *
 * Threading: all methods must be called from the platform frame thread (the
 * thread that drives rendering — Choreographer on Android, the display link
 * on iOS), matching the v1 rule that scene mutation happens on the frame
 * thread. Implementations are not required to be thread-safe.
 */
public interface SceneRenderer {
    /** Honest feature reporting, v1-style: never fake an unsupported feature. */
    public val capabilities: RenderCapabilities

    /**
     * Loads a binary glTF (.glb) asset and returns a handle to the live model
     * instance. The model is added to the scene immediately; position it with
     * [setModelTransform] and animate it with [setAnimation].
     *
     * Suspends until the model's geometry and textures are fully loaded.
     */
    public suspend fun loadModel(glb: ByteArray): ModelHandle

    /**
     * Sets the model's root transform as a column-major 4x4 matrix
     * (right-handed, +Y up, -Z forward, meters — the shared conventions).
     */
    public fun setModelTransform(
        handle: ModelHandle,
        matrix: FloatArray,
    )

    /** Applies an animation state; pass null to stop all animation. */
    public fun setAnimation(
        handle: ModelHandle,
        state: AnimationState?,
    )

    /** Removes the model from the scene and frees its GPU resources. */
    public fun destroyModel(handle: ModelHandle)

    /** Frees all renderer resources. The renderer is unusable afterwards. */
    public fun dispose()
}

/** What this renderer can actually do on this device. */
public data class RenderCapabilities(
    val supportsIbl: Boolean,
    val supportsShadows: Boolean,
    val supportsOcclusion: Boolean,
    val supportsAnimation: Boolean,
)
