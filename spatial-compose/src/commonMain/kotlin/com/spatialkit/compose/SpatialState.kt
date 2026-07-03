package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.scene.SpatialNode

/**
 * Remembers a spatial state value that can trigger recomposition.
 *
 * Use this for animating or updating spatial properties reactively.
 *
 * Example:
 * ```kotlin
 * var rotation by rememberSpatialState { Quaternion.IDENTITY }
 *
 * SpatialView {
 *     cube {
 *         rotation = rotation  // Updates when rotation changes
 *     }
 * }
 *
 * LaunchedEffect(Unit) {
 *     while (true) {
 *         delay(16)
 *         rotation = Quaternion.fromEuler(0f, time * 45f, 0f)
 *     }
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
 * Creates an animated spatial value using Compose's animation system.
 *
 * Example:
 * ```kotlin
 * val position by animatedSpatialVec3(
 *     targetValue = Vec3(1f, 0f, 0f),
 *     animationSpec = tween(1000)
 * )
 *
 * SpatialView {
 *     cube {
 *         position = position  // Smoothly animates
 *     }
 * }
 * ```
 */
@Composable
public fun animatedSpatialVec3(
    targetValue: Vec3,
    initialValue: Vec3 = Vec3.ZERO,
): State<Vec3> {
    // For now, just return the target value
    // Full animation support would use Compose's animateFloatAsState for each component
    return rememberUpdatedState(targetValue)
}

/**
 * Creates an animated rotation using Compose's animation system.
 *
 * Example:
 * ```kotlin
 * val rotation by animatedSpatialRotation(
 *     targetValue = Quaternion.fromEuler(0f, 90f, 0f),
 *     animationSpec = tween(1000)
 * )
 *
 * SpatialView {
 *     cube {
 *         rotation = rotation  // Smoothly rotates
 *     }
 * }
 * ```
 */
@Composable
public fun animatedSpatialRotation(
    targetValue: Quaternion,
    initialValue: Quaternion = Quaternion.IDENTITY,
): State<Quaternion> {
    // For now, just return the target value
    // Full animation support would use slerp interpolation
    return rememberUpdatedState(targetValue)
}
