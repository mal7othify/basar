package com.mal7othify.basar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spatialkit.compose.SpatialView
import com.spatialkit.core.math.Vec3
import com.spatialkit.dsl.rgb
import com.spatialkit.dsl.vec3
import com.spatialkit.engine.SessionMode
import com.spatialkit.scene.CylinderNode
import com.spatialkit.scene.SpatialNode
import kotlin.math.cos
import kotlin.math.sin

/**
 * Education use case: an animated, tappable solar system.
 *
 * The scene is built once (declarative DSL); all motion happens by mutating
 * node positions from `onFrame` on the render thread — the library's update
 * model. Tap a planet to see its fact card in the header.
 */
@Composable
fun SolarSystemDemo(onBack: () -> Unit) {
    var status by remember { mutableStateOf("Tap a planet to learn about it") }

    // Orbit centre: table height in AR, nicely framed in the browser preview
    val center = Vec3(0f, 0.25f, -0.7f)

    // name, fact, radius (m), orbit radius (m), angular speed (rad/s), color
    val planets =
        remember {
            listOf(
                Planet("Mercury", "Mercury orbits the Sun in just 88 days", 0.008f, 0.09f, 1.6f, 0x9E9E9E),
                Planet("Venus", "Venus spins backwards compared to Earth", 0.012f, 0.13f, 1.2f, 0xE6C87A),
                Planet("Earth", "Earth is the only known planet with life", 0.013f, 0.17f, 1.0f, 0x3D85C6),
                Planet("Mars", "Mars hosts the tallest volcano in the solar system", 0.010f, 0.21f, 0.8f, 0xCC4125),
                Planet("Jupiter", "Jupiter is heavier than all other planets combined", 0.026f, 0.28f, 0.5f, 0xD9A268),
                Planet("Saturn", "Saturn's rings are mostly water ice", 0.022f, 0.35f, 0.35f, 0xE8D9A0),
            )
        }
    val planetNodes = remember { arrayOfNulls<SpatialNode>(planets.size) }
    val angles = remember { FloatArray(planets.size) { it * 1.1f } }
    var moonNode: SpatialNode? = null
    var moonAngle = 0f

    DemoScaffold(screen = DemoScreen.SOLAR_SYSTEM, status = status, onBack = onBack) {
        SpatialView(
            modifier = Modifier.fillMaxSize(),
            mode = SessionMode.AR,
            onFrame = { frame ->
                // Update model: nodes are mutated imperatively per frame
                for (i in planets.indices) {
                    angles[i] += planets[i].speed * frame.deltaTime
                    planetNodes[i]?.position = center + orbitOffset(angles[i], planets[i].orbitRadius)
                }
                moonAngle += 3f * frame.deltaTime
                val earth = planetNodes[2]
                if (earth != null) {
                    moonNode?.position = earth.position + orbitOffset(moonAngle, 0.028f)
                }
            },
            onError = { error -> status = "⚠ $error" },
        ) {
            // Sun
            sphere(radius = 0.05f, color = rgb(0xFFC107), id = "sun") {
                position = vec3(center.x, center.y, center.z)
                onTap = { _ -> status = "Sun — 99.8% of the solar system's mass" }
            }

            planets.forEachIndexed { i, planet ->
                planetNodes[i] =
                    sphere(radius = planet.radius, color = rgb(planet.color), id = "planet-${planet.name}") {
                        position = vec3(center.x + planet.orbitRadius, center.y, center.z)
                        onTap = { _ -> status = "${planet.name} — ${planet.fact}" }
                        if (planet.name == "Saturn") {
                            // Ring: a flat cylinder child, inheriting Saturn's transform
                            addChild(
                                CylinderNode(0.038f, 0.0015f, rgb(0xF0E6C8), "saturn-ring"),
                            )
                        }
                    }
            }

            moonNode =
                sphere(radius = 0.0045f, color = rgb(0xBDBDBD), id = "moon") {
                    onTap = { _ -> status = "Moon — always shows Earth the same face" }
                }
        }
    }
}

private class Planet(
    val name: String,
    val fact: String,
    val radius: Float,
    val orbitRadius: Float,
    val speed: Float,
    val color: Int,
)

private fun orbitOffset(
    angle: Float,
    radius: Float,
): Vec3 = Vec3(cos(angle) * radius, 0f, sin(angle) * radius)
