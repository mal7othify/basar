package com.spatialkit.scene

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialAnimatorTest {
    @Test
    fun positionAnimationInterpolatesAndCompletes() {
        val animator = SpatialAnimator()
        val node = CubeNode()
        var completed = 0

        animator.animatePosition(
            node = node,
            from = Vec3.ZERO,
            to = Vec3(1f, 0f, 0f),
            duration = 1f,
            onComplete = { completed++ },
        )

        animator.update(0.5f)
        assertEquals(0.5f, node.position.x, 0.001f)
        assertEquals(1, animator.animationCount)

        animator.update(0.5f)
        assertEquals(1f, node.position.x, 0.001f)
        assertEquals(1, completed)
        assertEquals(0, animator.animationCount)

        // Further updates do not re-fire completion
        animator.update(1f)
        assertEquals(1, completed)
    }

    @Test
    fun rotationAnimationSlerps() {
        val animator = SpatialAnimator()
        val node = CubeNode()
        val target = Quaternion.fromAxisAngle(Vec3.UP, PI.toFloat() / 2f)

        animator.animateRotation(node, Quaternion.IDENTITY, target, duration = 1f)
        animator.update(1f)

        val rotated = node.rotation * Vec3.RIGHT
        assertEquals(0f, rotated.x, 0.001f)
        assertEquals(-1f, rotated.z, 0.001f)
    }

    @Test
    fun cancelStopsAnimation() {
        val animator = SpatialAnimator()
        val node = CubeNode()
        var completed = 0

        val animation =
            animator.animatePosition(
                node = node,
                from = Vec3.ZERO,
                to = Vec3(1f, 0f, 0f),
                duration = 1f,
                onComplete = { completed++ },
            )
        animator.cancel(animation)
        animator.update(2f)

        assertEquals(0, completed)
        assertEquals(0, animator.animationCount)
    }

    @Test
    fun easingFunctionsHitEndpoints() {
        Easing.entries.forEach { easing ->
            assertEquals(0f, easing.apply(0f), 0.01f, "$easing at t=0")
            assertEquals(1f, easing.apply(1f), 0.01f, "$easing at t=1")
        }
    }

    @Test
    fun easingOutputStaysReasonable() {
        Easing.entries.forEach { easing ->
            var t = 0f
            while (t <= 1f) {
                val v = easing.apply(t)
                assertTrue(v in -0.5f..1.5f, "$easing($t) = $v out of range")
                t += 0.05f
            }
        }
    }
}
