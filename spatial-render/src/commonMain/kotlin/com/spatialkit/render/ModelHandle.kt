package com.spatialkit.render

/**
 * Handle to a loaded glTF model instance owned by a [SceneRenderer].
 *
 * Handles are renderer-scoped: a handle from one renderer must never be passed
 * to another. The renderer that produced it is the only thing that can render,
 * animate, or destroy it.
 */
public class ModelHandle internal constructor(
    /** Renderer-internal instance id. */
    public val id: Long,
    /** Animation clips baked into the asset, in gltf animation-index order. */
    public val animations: List<AnimationClip>,
) {
    override fun toString(): String = "ModelHandle(id=$id, animations=${animations.size})"

    public companion object {
        /**
         * Renderer-implementation entry point. Internal-with-escape-hatch so
         * renderer modules (spatial-render-filament) can mint handles without
         * this module needing to know them.
         */
        public fun create(
            id: Long,
            animations: List<AnimationClip>,
        ): ModelHandle = ModelHandle(id, animations)
    }
}

/** One animation baked into a glTF asset. */
public data class AnimationClip(
    val index: Int,
    val name: String,
    val durationSeconds: Float,
)
