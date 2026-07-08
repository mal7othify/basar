package com.mal7othify.basar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spatialkit.compose.SpatialView
import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.math.cm
import com.spatialkit.core.math.degrees
import com.spatialkit.dsl.rgb
import com.spatialkit.dsl.vec3
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.SessionMode
import com.spatialkit.scene.Easing
import com.spatialkit.scene.GltfLoader
import com.spatialkit.scene.GltfModel
import com.spatialkit.scene.SpatialAnimator
import com.spatialkit.scene.SpatialNode
import com.spatialkit.scene.SpatialSession
import com.spatialkit.scene.loadGltf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * API playground showcasing four ways to create content in Spatial-KMP:
 *
 * 1. PRIMITIVES: Build complex shapes from built-in primitives (flower)
 * 2. CUSTOM MESH: Define vertices manually (leaf shape)
 * 3. GLTF LOADER: Load 3D models from GLTF format (star shape)
 * 4. TEXTURED MODEL: A textured GLB crate; tap a detected surface to place
 *    more copies (tap → hit test → anchor → model)
 *
 * Interaction (M8) uses the node-level gesture API: `onTap` bounces content,
 * taps that miss place a crate (`interactions.onTapMissed`), and placed
 * crates are `draggable()`, `scalable()` and `rotatable()`.
 */
@Composable
fun ShowcaseDemo(onBack: () -> Unit) {
    var sessionReady by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var lastTapInfo by remember { mutableStateOf("Tap objects to bounce them") }

    // Surface-detection feedback: without it there is no way to tell when
    // tap-to-place can work (detected planes are not rendered)
    var planeCount by remember { mutableStateOf(0) }
    var trackingState by remember { mutableStateOf("...") }

    // Textured model, parsed once off the main thread (M7). Capture the value
    // (not the state) below so the scene rebuilds when loading completes.
    var crateModel by remember { mutableStateOf<GltfModel?>(null) }
    LaunchedEffect(Unit) {
        crateModel = loadGltf(createCrateGltf().encodeToByteArray())
    }
    val crate = crateModel

    val animator = remember { SpatialAnimator() }

    // True resting scale per node, captured when a bounce starts (and reused
    // while one is in flight — re-reading node.scale mid-bounce would ratchet
    // the size up on rapid taps). Cleared per node when its bounce settles so
    // pinch-to-scale changes become the new resting size.
    val baseScales = remember { mutableMapOf<String, Vec3>() }
    val bounce: (SpatialNode) -> Unit =
        remember {
            { node ->
                val base = baseScales.getOrPut(node.id) { node.scale }
                animator.animateScale(
                    node = node,
                    from = node.scale,
                    to = base * 1.3f,
                    duration = 0.15f,
                    easing = Easing.EaseOut,
                    onComplete = {
                        animator.animateScale(
                            node = node,
                            from = node.scale,
                            to = base,
                            duration = 0.15f,
                            easing = Easing.EaseOut,
                            onComplete = { baseScales.remove(node.id) },
                        )
                    },
                )
            }
        }

    val status =
        buildString {
            append(if (sessionReady) "✓ Ready" else "⌛ Starting...")
            append(" | Tracking: $trackingState | Surfaces: $planeCount")
            append(
                if (planeCount == 0) " (scan the floor slowly)" else " — tap to place a crate",
            )
            lastError?.let { append("\n⚠ $it") }
            append("\n$lastTapInfo")
        }

    DemoScaffold(screen = DemoScreen.SHOWCASE, status = status, onBack = onBack) {
        SpatialView(
            modifier = Modifier.fillMaxSize(),
            mode = SessionMode.AR,
            // M9: light estimation is on by default; depth occlusion is
            // opt-in (real-world geometry hides virtual content where
            // supportsDepthOcclusion — currently ARCore Depth devices)
            config = EngineConfig(preferredMode = SessionMode.AR, enableDepthOcclusion = true),
            onSessionReady = { spatialSession ->
                sessionReady = true

                // M8: taps that hit no interactive node place a crate on
                // the tapped real-world surface. Runs on the render
                // thread; structural scene ops are synchronized.
                spatialSession.interactions.onTapMissed = { x, y ->
                    val model = crateModel
                    val hit = spatialSession.performHitTest(x, y)
                    when {
                        model != null && hit != null -> {
                            placeCrate(spatialSession, model, hit.position, bounce)
                            lastTapInfo =
                                "Placed crate at (${hit.position.x.round2()}, " +
                                "${hit.position.y.round2()}, ${hit.position.z.round2()}) — " +
                                "drag / pinch / twist it"
                        }
                        hit == null ->
                            lastTapInfo =
                                if (planeCount == 0) {
                                    "No surface yet — move the phone slowly over a textured floor/table"
                                } else {
                                    "Missed the detected surface — aim at it and tap again"
                                }
                        else -> lastTapInfo = "Model still loading..."
                    }
                }
            },
            onFrame = { frameData ->
                // Same-value writes don't recompose; this only fires
                // when detection/tracking actually changes
                planeCount = frameData.detectedPlanes.size
                trackingState = frameData.trackingState.name
                animator.update(frameData.deltaTime)
            },
            onError = { error ->
                lastError = error.toString()
            },
        ) {
            // ================================================================
            // DEMO 1: FLOWER FROM PRIMITIVES
            // Shows how to compose complex shapes from built-in primitives
            // Tapping any part bounces the whole flower: the tap resolves
            // to the nearest ancestor with a handler (M8).
            // ================================================================
            group(id = "flower") {
                node.onTap = { event ->
                    lastTapInfo = "Hit: ${event.node.id.take(12)} at ${event.distance.round2()}m"
                    bounce(event.node)
                }
                // M9.2: one blob shadow grounds the whole flower
                node.castsShadow = true
                // Stem - green cylinder
                cylinder(
                    radius = 0.5f.cm,
                    height = 12.cm,
                    color = rgb(0x228B22),
                ) {
                    position = vec3(-0.3f, 0.06f, -0.6f)
                }

                // Flower center - yellow sphere
                sphere(
                    radius = 1.5f.cm,
                    color = rgb(0xFFD700),
                ) {
                    position = vec3(-0.3f, 0.13f, -0.6f)
                }

                // Petals - 6 pink spheres arranged in a circle
                val petalCount = 6
                val petalRadius = 1.2f.cm
                val petalDistance = 2.5f.cm

                for (i in 0 until petalCount) {
                    val angle = (i * 360f / petalCount) * (PI.toFloat() / 180f)
                    val px = cos(angle) * petalDistance
                    val pz = sin(angle) * petalDistance

                    sphere(
                        radius = petalRadius,
                        color = rgb(0xFF69B4),
                        id = "petal_$i",
                    ) {
                        position = vec3(-0.3f + px, 0.13f, -0.6f + pz)
                    }
                }
            }

            // ================================================================
            // DEMO 2: CUSTOM MESH - LEAF SHAPE
            // Shows how to create arbitrary shapes with manual vertex data
            // ================================================================

            // Leaf vertices - a simple leaf shape
            val leafVertices =
                floatArrayOf(
                    // Base of leaf (stem attachment)
                    0f,
                    0f,
                    0f,
                    // Left edge points
                    -0.02f,
                    0.03f,
                    0f,
                    -0.025f,
                    0.06f,
                    0f,
                    -0.02f,
                    0.09f,
                    0f,
                    // Tip of leaf
                    0f,
                    0.12f,
                    0f,
                    // Right edge points
                    0.02f,
                    0.09f,
                    0f,
                    0.025f,
                    0.06f,
                    0f,
                    0.02f,
                    0.03f,
                    0f,
                )

            // Triangles forming the leaf (indices into vertices)
            val leafIndices =
                intArrayOf(
                    0,
                    1,
                    7, // Bottom triangle
                    1,
                    2,
                    7,
                    2,
                    6,
                    7,
                    2,
                    3,
                    6,
                    3,
                    5,
                    6,
                    3,
                    4,
                    5, // Top triangle
                )

            // Normals pointing forward (Z+)
            val leafNormals =
                floatArrayOf(
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    1f,
                )

            customMesh(
                geometry =
                    GeometryData(
                        type = GeometryType.CUSTOM_MESH,
                        vertices = leafVertices,
                        indices = leafIndices,
                        normals = leafNormals,
                    ),
                color = rgb(0x32CD32),
                id = "custom_leaf",
            ) {
                position = vec3(-0.25f, 0.02f, -0.6f)
                rotation = Quaternion.fromEuler(0f, 45f.degrees, 15f.degrees)
                onTap = { event ->
                    lastTapInfo = "Hit: ${event.node.id.take(12)} at ${event.distance.round2()}m"
                    bounce(event.node)
                }
            }

            // ================================================================
            // DEMO 3: GLTF LOADER - STAR SHAPE
            // Shows how to load 3D models from GLTF format
            // ================================================================

            // Embedded GLTF: A simple 5-pointed star
            // This GLTF was generated with vertices for a star shape
            val starGltf = createStarGltf()
            val starGeometry = GltfLoader.load(starGltf).firstGeometry()

            if (starGeometry != null) {
                customMesh(
                    geometry = starGeometry,
                    color = rgb(0xFFD700), // Gold
                    id = "gltf_star",
                ) {
                    position = vec3(0.3f, 0.1f, -0.6f)
                    scale = Vec3(0.5f, 0.5f, 0.5f)
                    onTap = { event ->
                        lastTapInfo = "Hit: ${event.node.id.take(12)} at ${event.distance.round2()}m"
                        bounce(event.node)
                    }
                }
            }

            // ================================================================
            // DEMO 4: TEXTURED GLB MODEL (M7 + M8)
            // A crate with a checkerboard base-color texture. Loaded
            // with loadGltf() outside this block (scene content builds
            // synchronously, once); placed via the model() builder.
            // Tap a detected surface to place more copies, then arrange
            // them: drag to move, pinch to resize, twist to turn.
            // ================================================================
            if (crate != null) {
                model(crate, id = "crate_preview") {
                    position = vec3(0f, 0.05f, -0.6f)
                    onTap = { event ->
                        lastTapInfo = "Hit: crate_preview at ${event.distance.round2()}m"
                        bounce(event.node)
                    }
                    draggable()
                    scalable(minFactor = 0.3f, maxFactor = 3f)
                    rotatable()
                    castsShadow = true // M9.2: soft blob shadow underneath
                }
            }

            // ================================================================
            // LABELS - Small cubes to mark each demo
            // ================================================================

            // Label marker for flower
            cube(size = 1.cm, color = rgb(0xFF69B4)) {
                position = vec3(-0.3f, 0.2f, -0.6f)
            }

            // Label marker for custom mesh
            cube(size = 1.cm, color = rgb(0x32CD32)) {
                position = vec3(0f, 0.2f, -0.6f)
            }

            // Label marker for GLTF
            cube(size = 1.cm, color = rgb(0xFFD700)) {
                position = vec3(0.3f, 0.2f, -0.6f)
            }
        }
    }
}

/**
 * Places a user-arrangeable [model] instance at [position] (M8 "place and
 * arrange furniture" flow): tap to bounce, drag along surfaces, pinch to
 * resize, twist to turn. Runs from the tap handler (render thread) —
 * structural scene ops like [com.spatialkit.scene.SpatialScene.add] are
 * synchronized.
 *
 * Unlike the M7 version, placed crates are not pinned to a world anchor: an
 * anchor's tracked pose would overwrite the position every frame and fight
 * the user's dragging. (A production app would re-anchor when a drag ends.)
 */
private fun placeCrate(
    session: SpatialSession,
    model: GltfModel,
    position: Vec3,
    bounce: (SpatialNode) -> Unit,
) {
    val node = model.toNode()
    node.position = position
    node.onTap = { event -> bounce(event.node) }
    node.draggable()
    node.scalable(minFactor = 0.3f, maxFactor = 3f)
    node.rotatable()
    node.castsShadow = true // M9.2: grounds the crate on its surface
    session.scene.add(node)
}
