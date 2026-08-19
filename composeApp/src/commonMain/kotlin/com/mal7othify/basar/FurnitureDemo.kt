package com.mal7othify.basar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spatialkit.compose.SpatialView2
import com.spatialkit.core.math.Vec3
import com.spatialkit.dsl.rgb
import com.spatialkit.scene.CubeNode
import com.spatialkit.scene.CylinderNode
import com.spatialkit.scene.GroupNode
import com.spatialkit.scene.SpatialSession2

/**
 * Retail use case: preview furniture in the customer's own room.
 *
 * Tap a detected surface to place a chair, then arrange it with the M8
 * gestures (drag to move, pinch to resize, twist to turn). Tap a placed
 * chair to remove it — the full "try before you buy" loop in ~100 lines.
 */
@Composable
fun FurnitureDemo(onBack: () -> Unit) {
    var status by remember {
        mutableStateOf("Find a surface (scan the floor slowly), then tap it to place a chair")
    }
    var planeCount by remember { mutableStateOf(0) }
    var placedCount by remember { mutableStateOf(0) }

    DemoScaffold(screen = DemoScreen.FURNITURE, status = status, onBack = onBack) {
        SpatialView2(
            modifier = Modifier.fillMaxSize(),
            onSessionReady = { session ->
                // Showroom piece so there's something on screen before the
                // first placement (structural scene ops are synchronized)
                session.scene.add(
                    buildChair(id = "chair-showroom").apply {
                        position = Vec3(0f, 0f, -0.9f)
                    },
                )

                // Tap on a real-world surface -> place a chair there (M8
                // tap-to-place; runs on the render thread)
                session.interactions.onTapMissed = { x, y ->
                    val hit = session.performHitTest(x, y)
                    if (hit != null) {
                        placedCount += 1
                        session.scene.add(
                            buildChair(id = "chair-$placedCount", session = session) {
                                placedCount -= 1
                                status = "Removed — $placedCount placed"
                            }.apply { position = hit.position },
                        )
                        status =
                            "$placedCount placed — drag / pinch / twist to arrange, tap a chair to remove it"
                    } else {
                        status =
                            if (planeCount == 0) {
                                "No surface detected yet — move slowly over a textured floor"
                            } else {
                                "Missed the surface — aim at the detected area and tap"
                            }
                    }
                }
            },
            onFrame = { frame ->
                // Same-value writes don't recompose
                planeCount = frame.detectedPlanes.size
            },
            onError = { error -> status = "⚠ $error" },
        ) {
            // Scene content is placed imperatively in onSessionReady; the
            // build-once content block stays empty.
        }
    }
}

/**
 * Builds a chair from primitives (seat, backrest, four legs), arrangeable
 * with the standard gestures. When [session] is given, tapping the chair
 * removes it and invokes [onRemoved].
 */
private fun buildChair(
    id: String,
    session: SpatialSession2? = null,
    onRemoved: () -> Unit = {},
): GroupNode {
    val wood = rgb(0x9C6B30)
    val darkWood = rgb(0x7A4F1D)

    val chair =
        GroupNode(id).apply {
            // Seat: 32x4x32 cm at 26 cm height
            addChild(
                CubeNode(0.32f, wood, "$id-seat").apply {
                    position = Vec3(0f, 0.26f, 0f)
                    scale = Vec3(1f, 0.125f, 1f)
                },
            )
            // Backrest: 32x30x3 cm rising from the seat's back edge
            addChild(
                CubeNode(0.32f, wood, "$id-back").apply {
                    position = Vec3(0f, 0.42f, -0.145f)
                    scale = Vec3(1f, 0.94f, 0.09f)
                },
            )
            // Legs at the four corners
            listOf(
                Vec3(-0.13f, 0.12f, -0.13f),
                Vec3(0.13f, 0.12f, -0.13f),
                Vec3(-0.13f, 0.12f, 0.13f),
                Vec3(0.13f, 0.12f, 0.13f),
            ).forEachIndexed { i, legPosition ->
                addChild(
                    CylinderNode(0.016f, 0.24f, darkWood, "$id-leg-$i").apply {
                        position = legPosition
                    },
                )
            }

            castsShadow = true // M9.2 soft blob shadow grounds the chair
            draggable()
            scalable(minFactor = 0.5f, maxFactor = 2f)
            rotatable()
        }

    if (session != null) {
        chair.onTap = { _ ->
            session.scene.remove(chair)
            onRemoved()
        }
    }
    return chair
}
