package com.spatialkit.render

/**
 * THE renderer contract of the v2 architecture: one physically-based renderer
 * (Filament) shared by every platform, fed by a thin per-platform tracking
 * adapter (see `docs/V2_REALISM_REWRITE.md` §4).
 *
 * R0 added model loading + animation control; R1 added AR frame consumption
 * (`render(TrackingFrame)`); R3 added retained-mode scene submission
 * ([submitScene]), which is how the scene graph drives the renderer.
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
     * Renders one frame.
     *
     * With a [frame], the renderer drives its camera from the tracked pose
     * (view/projection matrices) and composites the camera image behind the
     * scene. With null (non-AR: the R0 spike, previews) it renders with its
     * fixed default camera and skybox.
     *
     * Called by the runtime's frame ticker on the frame thread — except on
     * platforms whose surface drives itself (the iOS bridge view's display
     * link at R0/R1); there the platform host documents who calls this.
     *
     * @param frameTimeNanos The vsync timestamp for this frame (drives
     *   animation clocks)
     */
    public fun render(
        frame: com.spatialkit.tracking.TrackingFrame?,
        frameTimeNanos: Long,
    )

    /**
     * Submits the scene snapshot to render (R3).
     *
     * The renderer retains per-node GPU state keyed by [SceneNode.id] and
     * diffs consecutive snapshots: transform/visibility changes update in
     * place (no entity churn), geometry/material changes rebuild only the
     * affected node, and nodes absent from the new snapshot are destroyed.
     * Submitting an unchanged scene is cheap, so callers may submit every
     * frame.
     *
     * [SceneData.anchors] is ignored here — anchor lifecycles belong to the
     * tracking layer; the session routes them to the `TrackingProvider`.
     *
     * Frame-thread only, like every other method.
     */
    public fun submitScene(scene: SceneData)

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
