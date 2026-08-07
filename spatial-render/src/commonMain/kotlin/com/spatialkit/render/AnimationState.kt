package com.spatialkit.render

/**
 * Declarative animation state for a model node. The renderer advances time
 * itself on its frame loop; callers only describe *what* should play.
 */
public data class AnimationState(
    /** Index into [ModelHandle.animations]. */
    val clip: Int,
    val playing: Boolean = true,
    val loop: Boolean = true,
    /** Playback speed multiplier (1.0 = authored speed). */
    val speed: Float = 1f,
    /** Start offset within the clip, in seconds. */
    val timeOffset: Float = 0f,
    /** Crossfade duration from the previously playing clip, in seconds. */
    val crossfadeSeconds: Float = 0f,
)
