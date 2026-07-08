package com.spatialkit.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.spatialkit.scene.GestureEvent
import com.spatialkit.scene.InteractionManager
import kotlin.math.PI
import kotlin.math.abs

/**
 * Turns Compose pointer input into spatial [GestureEvent]s (M8.2).
 *
 * Recognized gestures, all forwarded to [interactions] with normalized
 * (0..1, top-left origin) coordinates:
 * - **Tap** — down + up without crossing touch slop
 * - **Transform** — one-finger drag, two-finger pinch and twist, reported as
 *   one gesture stream with per-event zoom/twist deltas
 *
 * Events are queued thread-safely and applied to the scene on the render
 * thread by the session (see [InteractionManager]); nothing runs on the UI
 * thread beyond recognition.
 */
internal fun Modifier.spatialGestures(interactions: InteractionManager): Modifier =
    this
        .pointerInput(interactions) {
            detectTapGestures { offset ->
                interactions.submit(
                    GestureEvent.Tap(
                        screenX = offset.x / size.width,
                        screenY = offset.y / size.height,
                    ),
                )
            }
        }.pointerInput(interactions) {
            detectTransformGesturesWithLifecycle(
                onGestureStart = { centroid ->
                    interactions.submit(
                        GestureEvent.TransformStart(
                            screenX = centroid.x / size.width,
                            screenY = centroid.y / size.height,
                        ),
                    )
                },
                onGesture = { centroid, zoom, rotationDegrees ->
                    interactions.submit(
                        GestureEvent.Transform(
                            screenX = centroid.x / size.width,
                            screenY = centroid.y / size.height,
                            zoom = zoom,
                            twist = rotationDegrees * DEGREES_TO_RADIANS,
                        ),
                    )
                },
                onGestureEnd = { interactions.submit(GestureEvent.TransformEnd) },
            )
        }

/**
 * Like `detectTransformGestures` from compose-foundation (same slop logic and
 * event math), but with explicit start/end callbacks — the interaction layer
 * picks the target node when the gesture starts and releases it at the end.
 */
private suspend fun PointerInputScope.detectTransformGesturesWithLifecycle(
    onGestureStart: (centroid: Offset) -> Unit,
    onGesture: (centroid: Offset, zoom: Float, rotationDegrees: Float) -> Unit,
    onGestureEnd: () -> Unit,
) {
    awaitEachGesture {
        val touchSlop = viewConfiguration.touchSlop
        var accumulatedZoom = 1f
        var accumulatedRotation = 0f
        var accumulatedPan = Offset.Zero
        var started = false

        awaitFirstDown(requireUnconsumed = false)
        var canceled: Boolean
        do {
            val event = awaitPointerEvent()
            canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!started) {
                    accumulatedZoom *= zoomChange
                    accumulatedRotation += rotationChange
                    accumulatedPan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - accumulatedZoom) * centroidSize
                    val rotationMotion =
                        abs(accumulatedRotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = accumulatedPan.getDistance()

                    if (zoomMotion > touchSlop || rotationMotion > touchSlop || panMotion > touchSlop) {
                        started = true
                        onGestureStart(event.calculateCentroid(useCurrent = false))
                    }
                }

                if (started) {
                    if (zoomChange != 1f || rotationChange != 0f || panChange != Offset.Zero) {
                        onGesture(event.calculateCentroid(useCurrent = true), zoomChange, rotationChange)
                    }
                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })

        if (started) onGestureEnd()
    }
}

private const val DEGREES_TO_RADIANS = PI.toFloat() / 180f
