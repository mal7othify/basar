package com.spatialkit.scene

import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Animation system for spatial nodes (P2 bug #8 fix).
 *
 * Provides a simple but functional animation API for animating node transforms.
 * Animations are updated each frame by calling update(deltaTime).
 *
 * Usage:
 * ```kotlin
 * val animator = SpatialAnimator()
 * val cube = CubeNode()
 *
 * // Animate position
 * animator.animatePosition(
 *     node = cube,
 *     from = Vec3(0f, 0f, -1f),
 *     to = Vec3(0f, 1f, -1f),
 *     duration = 1.0f,
 *     easing = Easing.EaseInOut
 * )
 *
 * // Update in your frame callback
 * onFrame { frameData ->
 *     animator.update(frameData.deltaTime)
 * }
 * ```
 */
class SpatialAnimator {
    private val activeAnimations = mutableListOf<Animation>()

    /**
     * Animate a node's position.
     *
     * @param node The node to animate
     * @param from Starting position
     * @param to Ending position
     * @param duration Animation duration in seconds
     * @param easing Easing function to use
     * @param onComplete Optional callback when animation finishes
     * @return The created animation
     */
    fun animatePosition(
        node: SpatialNode,
        from: Vec3,
        to: Vec3,
        duration: Float,
        easing: Easing = Easing.Linear,
        onComplete: (() -> Unit)? = null,
    ): Animation {
        node.position = from
        val animation =
            PositionAnimation(
                node = node,
                from = from,
                to = to,
                duration = duration,
                easing = easing,
                onComplete = onComplete,
            )
        activeAnimations.add(animation)
        return animation
    }

    /**
     * Animate a node's rotation.
     *
     * @param node The node to animate
     * @param from Starting rotation
     * @param to Ending rotation
     * @param duration Animation duration in seconds
     * @param easing Easing function to use
     * @param onComplete Optional callback when animation finishes
     * @return The created animation
     */
    fun animateRotation(
        node: SpatialNode,
        from: Quaternion,
        to: Quaternion,
        duration: Float,
        easing: Easing = Easing.Linear,
        onComplete: (() -> Unit)? = null,
    ): Animation {
        node.rotation = from
        val animation =
            RotationAnimation(
                node = node,
                from = from,
                to = to,
                duration = duration,
                easing = easing,
                onComplete = onComplete,
            )
        activeAnimations.add(animation)
        return animation
    }

    /**
     * Animate a node's scale.
     *
     * @param node The node to animate
     * @param from Starting scale
     * @param to Ending scale
     * @param duration Animation duration in seconds
     * @param easing Easing function to use
     * @param onComplete Optional callback when animation finishes
     * @return The created animation
     */
    fun animateScale(
        node: SpatialNode,
        from: Vec3,
        to: Vec3,
        duration: Float,
        easing: Easing = Easing.Linear,
        onComplete: (() -> Unit)? = null,
    ): Animation {
        node.scale = from
        val animation =
            ScaleAnimation(
                node = node,
                from = from,
                to = to,
                duration = duration,
                easing = easing,
                onComplete = onComplete,
            )
        activeAnimations.add(animation)
        return animation
    }

    /**
     * Update all active animations.
     * Call this each frame with the frame's deltaTime.
     *
     * @param deltaTime Time elapsed since last frame in seconds
     */
    fun update(deltaTime: Float) {
        val iterator = activeAnimations.iterator()
        while (iterator.hasNext()) {
            val animation = iterator.next()
            animation.update(deltaTime)

            if (animation.isComplete) {
                animation.onComplete?.invoke()
                iterator.remove()
            }
        }
    }

    /**
     * Stop and remove an animation.
     *
     * @param animation The animation to cancel
     */
    fun cancel(animation: Animation) {
        activeAnimations.remove(animation)
    }

    /**
     * Stop all animations.
     */
    fun cancelAll() {
        activeAnimations.clear()
    }

    /**
     * Get the number of active animations.
     */
    val animationCount: Int
        get() = activeAnimations.size
}

/**
 * Base class for animations.
 */
sealed class Animation {
    abstract val duration: Float
    abstract val easing: Easing
    abstract val onComplete: (() -> Unit)?

    internal var elapsedTime: Float = 0f

    val isComplete: Boolean
        get() = elapsedTime >= duration

    val progress: Float
        get() = (elapsedTime / duration).coerceIn(0f, 1f)

    internal abstract fun update(deltaTime: Float)
}

/**
 * Position animation.
 */
internal class PositionAnimation(
    val node: SpatialNode,
    val from: Vec3,
    val to: Vec3,
    override val duration: Float,
    override val easing: Easing,
    override val onComplete: (() -> Unit)?,
) : Animation() {
    override fun update(deltaTime: Float) {
        elapsedTime += deltaTime
        val t = easing.apply(progress)
        node.position = from.lerp(to, t)
    }
}

/**
 * Rotation animation using spherical linear interpolation (slerp).
 */
internal class RotationAnimation(
    val node: SpatialNode,
    val from: Quaternion,
    val to: Quaternion,
    override val duration: Float,
    override val easing: Easing,
    override val onComplete: (() -> Unit)?,
) : Animation() {
    override fun update(deltaTime: Float) {
        elapsedTime += deltaTime
        val t = easing.apply(progress)
        node.rotation = from.slerp(to, t)
    }
}

/**
 * Scale animation.
 */
internal class ScaleAnimation(
    val node: SpatialNode,
    val from: Vec3,
    val to: Vec3,
    override val duration: Float,
    override val easing: Easing,
    override val onComplete: (() -> Unit)?,
) : Animation() {
    override fun update(deltaTime: Float) {
        elapsedTime += deltaTime
        val t = easing.apply(progress)
        node.scale = from.lerp(to, t)
    }
}

/**
 * Easing functions for animations.
 *
 * Transform the linear progress (0-1) into eased progress.
 */
enum class Easing {
    /** No easing - constant speed */
    Linear {
        override fun apply(t: Float): Float = t
    },

    /** Slow start, accelerates */
    EaseIn {
        override fun apply(t: Float): Float = t * t
    },

    /** Fast start, decelerates */
    EaseOut {
        override fun apply(t: Float): Float = t * (2f - t)
    },

    /** Slow start and end, fast middle */
    EaseInOut {
        override fun apply(t: Float): Float =
            if (t < 0.5f) {
                2f * t * t
            } else {
                -1f + (4f - 2f * t) * t
            }
    },

    /** Cubic easing in */
    EaseInCubic {
        override fun apply(t: Float): Float = t * t * t
    },

    /** Cubic easing out */
    EaseOutCubic {
        override fun apply(t: Float): Float = 1f + (t - 1f).pow(3)
    },

    /** Bounce at the end */
    EaseOutBounce {
        override fun apply(t: Float): Float =
            when {
                t < 1f / 2.75f -> {
                    7.5625f * t * t
                }

                t < 2f / 2.75f -> {
                    val t2 = t - 1.5f / 2.75f
                    7.5625f * t2 * t2 + 0.75f
                }

                t < 2.5f / 2.75f -> {
                    val t2 = t - 2.25f / 2.75f
                    7.5625f * t2 * t2 + 0.9375f
                }

                else -> {
                    val t2 = t - 2.625f / 2.75f
                    7.5625f * t2 * t2 + 0.984375f
                }
            }
    },

    /** Elastic spring effect */
    EaseOutElastic {
        override fun apply(t: Float): Float {
            if (t == 0f) return 0f
            if (t == 1f) return 1f
            val p = 0.3f
            return (2f.pow(-10f * t) * sin((t - p / 4f) * (2f * PI.toFloat()) / p) + 1f).toFloat()
        }
    },
    ;

    abstract fun apply(t: Float): Float
}
