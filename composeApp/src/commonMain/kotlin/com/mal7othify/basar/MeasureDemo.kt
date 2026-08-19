package com.mal7othify.basar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spatialkit.compose.SpatialView2
import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.dsl.rgb
import com.spatialkit.scene.CylinderNode
import com.spatialkit.scene.SpatialNode
import com.spatialkit.scene.SpatialSession2
import com.spatialkit.scene.SphereNode

/**
 * Construction use case: a tape measure on real-world surfaces.
 *
 * Each tap on a detected surface drops a marker; consecutive markers are
 * connected with a measured segment (hit test -> world position -> plain
 * vector math). Tap any marker to clear and start over.
 */
@Composable
fun MeasureDemo(onBack: () -> Unit) {
    var status by remember {
        mutableStateOf("Find a surface, then tap two points on it to measure the distance")
    }
    var planeCount by remember { mutableStateOf(0) }

    // Render-thread state: taps, markers and segments all live on the frame
    // callback side (M8 gesture handlers run on the render thread)
    val points = remember { mutableListOf<Vec3>() }
    val measureNodes = remember { mutableListOf<SpatialNode>() }
    var totalLength by remember { mutableStateOf(0f) }

    DemoScaffold(screen = DemoScreen.MEASURE, status = status, onBack = onBack) {
        SpatialView2(
            modifier = Modifier.fillMaxSize(),
            onSessionReady = { session ->
                val clearAll = {
                    measureNodes.forEach { session.scene.remove(it) }
                    measureNodes.clear()
                    points.clear()
                    totalLength = 0f
                    status = "Cleared — tap two points on a surface to measure"
                }

                session.interactions.onTapMissed = { x, y ->
                    val hit = session.performHitTest(x, y)
                    if (hit == null) {
                        status =
                            if (planeCount == 0) {
                                "No surface yet — scan slowly over a floor or table"
                            } else {
                                "Missed the surface — aim at the detected area and tap"
                            }
                    } else {
                        addMeasurePoint(session, hit.position, points, measureNodes, clearAll)
                        if (points.size >= 2) {
                            val segment = points[points.size - 1].distance(points[points.size - 2])
                            totalLength += segment
                            status =
                                "Segment: ${(segment * 100).round2()} cm — " +
                                "total: ${(totalLength * 100).round2()} cm (tap a marker to clear)"
                        } else {
                            status = "First point set — tap the end point"
                        }
                    }
                }
            },
            onFrame = { frame -> planeCount = frame.detectedPlanes.size },
            onError = { error -> status = "⚠ $error" },
        ) {
            // All content is created per tap in onSessionReady.
        }
    }
}

/**
 * Drops a marker at [position]; from the second marker on, connects it to
 * the previous one with a thin measured segment.
 */
private fun addMeasurePoint(
    session: SpatialSession2,
    position: Vec3,
    points: MutableList<Vec3>,
    measureNodes: MutableList<SpatialNode>,
    clearAll: () -> Unit,
) {
    val index = points.size
    points += position

    // Marker sits just above the surface so it doesn't z-fight
    val marker =
        SphereNode(0.008f, rgb(0xFF8C00), "measure-point-$index").apply {
            this.position = position + Vec3(0f, 0.008f, 0f)
            onTap = { _ -> clearAll() }
        }
    session.scene.add(marker)
    measureNodes += marker

    if (index >= 1) {
        val from = points[index - 1]
        val to = position
        val delta = to - from
        val length = delta.length()
        if (length > 1e-4f) {
            // A cylinder's axis is +Y: place it at the midpoint and rotate
            // +Y onto the segment direction
            val segment =
                CylinderNode(0.003f, length, rgb(0xFFD700), "measure-line-$index").apply {
                    this.position = (from + to) * 0.5f + Vec3(0f, 0.008f, 0f)
                    rotation = Quaternion.fromToRotation(Vec3.UP, delta)
                }
            session.scene.add(segment)
            measureNodes += segment
        }
    }
}
