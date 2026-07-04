package com.spatialkit.compose

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.scene.SpatialNode

/**
 * Remembers a spatial state value that can trigger recomposition.
 *
 * Note that the scene DSL is build-once (see [SpatialView]): state read inside
 * the `content` block does not update the scene. Apply changing values to a
 * node reference imperatively, typically in `onFrame`:
 *
 * ```kotlin
 * var angle by rememberSpatialState { 0f }
 * var cube: CubeNode? = null
 *
 * SpatialView(
 *     onFrame = { frame ->
 *         angle += frame.deltaTime * 45f
 *         cube?.rotation = Quaternion.fromEuler(0f, angle, 0f)
 *     },
 * ) {
 *     cube = cube(size = 0.1f)
 * }
 * ```
 */
@Composable
public fun <T> rememberSpatialState(initial: () -> T): MutableState<T> = remember { mutableStateOf(initial()) }

/**
 * Remembers a spatial node by ID.
 *
 * Useful for accessing and modifying specific nodes from outside the DSL.
 *
 * Example:
 * ```kotlin
 * val cube = rememberSpatialNode { session.scene.findNodeById("player") }
 *
 * LaunchedEffect(playerPosition) {
 *     cube?.position = playerPosition
 * }
 * ```
 */
@Composable
public fun rememberSpatialNode(
    key: Any? = Unit,
    finder: () -> SpatialNode?,
): SpatialNode? = remember(key) { finder() }

/**
 * A [TwoWayConverter] that animates a [Vec3] component-wise.
 *
 * Use with any Compose animation API (`animateValueAsState`, `Animatable`,
 * transitions); [animatedSpatialVec3] is a convenience wrapper around it.
 */
public val Vec3.Companion.VectorConverter: TwoWayConverter<Vec3, AnimationVector3D>
    get() = Vec3ToVector

private val Vec3ToVector: TwoWayConverter<Vec3, AnimationVector3D> =
    TwoWayConverter(
        convertToVector = { AnimationVector3D(it.x, it.y, it.z) },
        convertFromVector = { Vec3(it.v1, it.v2, it.v3) },
    )

/**
 * A [TwoWayConverter] that animates a [Quaternion] by interpolating its
 * components and renormalizing (nlerp).
 *
 * Nlerp follows the same arc as slerp (with slightly non-constant angular
 * velocity), which is what a spring/tween-driven rotation needs. Combine with
 * the sign alignment done by [animatedSpatialRotation] so interpolation takes
 * the short way around.
 */
public val Quaternion.Companion.VectorConverter: TwoWayConverter<Quaternion, AnimationVector4D>
    get() = QuaternionToVector

private val QuaternionToVector: TwoWayConverter<Quaternion, AnimationVector4D> =
    TwoWayConverter(
        convertToVector = { AnimationVector4D(it.x, it.y, it.z, it.w) },
        // Component-wise interpolation denormalizes; renormalize on the way out.
        convertFromVector = { Quaternion(it.v1, it.v2, it.v3, it.v4).normalized() },
    )

/**
 * Animates a [Vec3] toward [targetValue], like `animateFloatAsState` for
 * positions/scales.
 *
 * The scene DSL is build-once (see [SpatialView]), so apply the animated
 * value to a node imperatively — e.g. from `onFrame`, which reads the current
 * snapshot value on the render thread:
 *
 * ```kotlin
 * var lifted by remember { mutableStateOf(false) }
 * val position by animatedSpatialVec3(
 *     targetValue = if (lifted) Vec3(0f, 2f, -1f) else Vec3(0f, 1.5f, -1f),
 *     animationSpec = tween(1000),
 * )
 * var cube: CubeNode? = null
 *
 * SpatialView(
 *     onFrame = { cube?.position = position },
 * ) {
 *     cube = cube(size = 0.1f, color = Color.Red)
 * }
 * ```
 */
@Composable
public fun animatedSpatialVec3(
    targetValue: Vec3,
    animationSpec: AnimationSpec<Vec3> = spring(),
    label: String = "SpatialVec3Animation",
    finishedListener: ((Vec3) -> Unit)? = null,
): State<Vec3> =
    animateValueAsState(
        targetValue = targetValue,
        typeConverter = Vec3.VectorConverter,
        animationSpec = animationSpec,
        label = label,
        finishedListener = finishedListener,
    )

/**
 * Animates a [Quaternion] rotation toward [targetValue].
 *
 * Interpolation is normalized-lerp along the short arc: targets are
 * sign-aligned against the previous target (q and -q encode the same
 * rotation), so a 350° request rotates -10°, not +350°.
 *
 * Apply the value imperatively as with [animatedSpatialVec3]:
 *
 * ```kotlin
 * val rotation by animatedSpatialRotation(
 *     targetValue = if (open) Quaternion.fromEuler(0f, 90f, 0f) else Quaternion.IDENTITY,
 *     animationSpec = tween(500),
 * )
 * var door: CubeNode? = null
 *
 * SpatialView(
 *     onFrame = { door?.rotation = rotation },
 * ) {
 *     door = cube(size = 0.1f)
 * }
 * ```
 */
@Composable
public fun animatedSpatialRotation(
    targetValue: Quaternion,
    animationSpec: AnimationSpec<Quaternion> = spring(),
    label: String = "SpatialRotationAnimation",
    finishedListener: ((Quaternion) -> Unit)? = null,
): State<Quaternion> {
    // Quaternions double-cover rotations: q and -q are the same orientation.
    // Flip the incoming target when it points away from the previous one so
    // component interpolation takes the short arc.
    val lastTarget = remember { QuaternionTargetHolder(targetValue) }
    val aligned =
        if (lastTarget.value.dot(targetValue) < 0f) {
            Quaternion(-targetValue.x, -targetValue.y, -targetValue.z, -targetValue.w)
        } else {
            targetValue
        }
    lastTarget.value = aligned
    return animateValueAsState(
        targetValue = aligned,
        typeConverter = Quaternion.VectorConverter,
        animationSpec = animationSpec,
        label = label,
        finishedListener = finishedListener,
    )
}

/** Plain holder (not snapshot state): sign alignment must not recompose. */
private class QuaternionTargetHolder(
    var value: Quaternion,
)
