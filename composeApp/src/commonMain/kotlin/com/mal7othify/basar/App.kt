package com.mal7othify.basar

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.spatialkit.compose.SpatialView
import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.math.cm
import com.spatialkit.core.math.degrees
import com.spatialkit.dsl.rgb
import com.spatialkit.dsl.vec3
import com.spatialkit.engine.AnchorType
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.SessionMode
import com.spatialkit.scene.Easing
import com.spatialkit.scene.GltfLoader
import com.spatialkit.scene.GltfModel
import com.spatialkit.scene.SpatialAnimator
import com.spatialkit.scene.SpatialSession
import com.spatialkit.scene.loadGltf
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * Comprehensive demo showcasing four ways to create content in Spatial-KMP:
 *
 * 1. PRIMITIVES: Build complex shapes from built-in primitives (flower)
 * 2. CUSTOM MESH: Define vertices manually (leaf shape)
 * 3. GLTF LOADER: Load 3D models from GLTF format (star shape)
 * 4. TEXTURED MODEL: A textured GLB crate; tap a detected surface to place
 *    more copies (tap → hit test → anchor → model)
 */
@Composable
fun App() {
    var sessionReady by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }
    var lastTapInfo by remember { mutableStateOf("Tap objects to test raycasting") }
    var session: SpatialSession? by remember { mutableStateOf(null) }
    var lastFrameData by remember { mutableStateOf<FrameData?>(null) }

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

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Spatial-KMP Demo:",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("Session: ${if (sessionReady) "✓ Ready" else "⌛ Starting..."}")
                    Text(
                        "Tracking: $trackingState | Surfaces: $planeCount" +
                            if (planeCount == 0) " (scan the floor slowly)" else " — tap one to place a crate",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    lastError?.let { error ->
                        Text(
                            "⚠ $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        lastTapInfo,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val currentSession = session
                                val frameData = lastFrameData

                                if (currentSession != null && frameData != null) {
                                    val screenWidth = size.width.toFloat()
                                    val screenHeight = size.height.toFloat()
                                    val cameraPose = frameData.cameraPose

                                    val ndcX = (offset.x / screenWidth) * 2f - 1f
                                    val ndcY = -((offset.y / screenHeight) * 2f - 1f)

                                    // Unproject through the camera's actual projection
                                    // matrix (column-major): P[0]=1/(tanHalfX), P[5]=1/tanHalfY.
                                    // A guessed FOV makes rays miss small objects.
                                    val proj = frameData.projectionMatrix
                                    val tanHalfX = if (proj[0] != 0f) 1f / proj[0] else 1f
                                    val tanHalfY = if (proj[5] != 0f) 1f / proj[5] else 1f

                                    val forward = Vec3(0f, 0f, -1f)
                                    val right = Vec3(1f, 0f, 0f)
                                    val up = Vec3(0f, 1f, 0f)

                                    val rayDirCamera =
                                        (
                                            forward +
                                                right * (ndcX * tanHalfX) +
                                                up * (ndcY * tanHalfY)
                                        ).normalized()

                                    val rayDirection = cameraPose.rotation * rayDirCamera

                                    val result =
                                        currentSession.performRaycast(
                                            origin = cameraPose.position,
                                            direction = rayDirection,
                                        )

                                    if (result != null) {
                                        lastTapInfo = "Hit: ${result.nodeId?.take(12)} at ${result.distance.round2()}m"

                                        val hitNode = currentSession.scene.findNodeById(result.nodeId ?: "")
                                        if (hitNode != null) {
                                            val originalScale = hitNode.scale
                                            animator.animateScale(
                                                node = hitNode,
                                                from = originalScale,
                                                to = originalScale * 1.3f,
                                                duration = 0.15f,
                                                easing = Easing.EaseOut,
                                                onComplete = {
                                                    animator.animateScale(
                                                        node = hitNode,
                                                        from = hitNode.scale,
                                                        to = originalScale,
                                                        duration = 0.15f,
                                                        easing = Easing.EaseOut,
                                                    )
                                                },
                                            )
                                        }
                                    } else {
                                        // M7: tap on a detected surface →
                                        // hit test → anchor → textured model
                                        val model = crateModel
                                        val hit =
                                            currentSession.performHitTest(
                                                offset.x / screenWidth,
                                                offset.y / screenHeight,
                                            )
                                        when {
                                            model != null && hit != null -> {
                                                placeCrate(currentSession, model, hit.position)
                                                lastTapInfo =
                                                    "Placed crate at (${hit.position.x.round2()}, " +
                                                    "${hit.position.y.round2()}, ${hit.position.z.round2()})"
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
                                }
                            }
                        },
            ) {
                SpatialView(
                    modifier = Modifier.fillMaxSize(),
                    mode = SessionMode.AR,
                    onSessionReady = { spatialSession ->
                        sessionReady = true
                        session = spatialSession
                    },
                    onFrame = { frameData ->
                        lastFrameData = frameData
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
                    // ================================================================
                    group(id = "flower") {
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
                        }
                    }

                    // ================================================================
                    // DEMO 4: TEXTURED GLB MODEL (M7)
                    // A crate with a checkerboard base-color texture. Loaded
                    // with loadGltf() outside this block (scene content builds
                    // synchronously, once); placed via the model() builder.
                    // Tap a detected surface to place more copies.
                    // ================================================================
                    if (crate != null) {
                        model(crate, id = "crate_preview") {
                            position = vec3(0f, 0.05f, -0.6f)
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
    }
}

/**
 * Places a [model] instance at [position], pinned to a world anchor so the
 * pose stays put as tracking refines. Runs from the tap handler — structural
 * scene ops like [com.spatialkit.scene.SpatialScene.add] are synchronized.
 */
private fun placeCrate(
    session: SpatialSession,
    model: GltfModel,
    position: Vec3,
) {
    val node = model.toNode()
    node.position = position

    val anchor = session.scene.createAnchor(AnchorType.World(position, Quaternion.IDENTITY))
    anchor.onTrack {
        it.pose?.let { pose ->
            node.position = pose.position
            node.rotation = pose.rotation
        }
    }
    session.scene.add(node)
}

/**
 * Creates a glTF JSON string for a 10cm crate with a checkerboard base-color
 * texture (embedded as a PNG data URI) — the M7 textured-model demo asset.
 *
 * In production you'd ship a real GLB and load its bytes from Compose
 * resources or the network; embedding here keeps the demo self-contained.
 */
private fun createCrateGltf(): String {
    val half = 0.05f
    val faceNormals =
        arrayOf(
            floatArrayOf(0f, 0f, 1f),
            floatArrayOf(0f, 0f, -1f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, -1f, 0f),
        )

    val positions = FloatArray(24 * 3)
    val normals = FloatArray(24 * 3)
    val uvs = FloatArray(24 * 2)
    val indices = IntArray(36)

    faceNormals.forEachIndexed { face, n ->
        // Face basis chosen so u × v = n: corners wind counter-clockwise
        // seen from outside (the engine contract's front-face convention)
        val u = if (n[1] != 0f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(n[2], 0f, -n[0])
        val v =
            floatArrayOf(
                n[1] * u[2] - n[2] * u[1],
                n[2] * u[0] - n[0] * u[2],
                n[0] * u[1] - n[1] * u[0],
            )

        for (corner in 0 until 4) {
            val su = if (corner == 1 || corner == 2) 1f else -1f
            val sv = if (corner >= 2) 1f else -1f
            val vi = face * 4 + corner
            for (axis in 0 until 3) {
                positions[vi * 3 + axis] = (n[axis] + u[axis] * su + v[axis] * sv) * half
                normals[vi * 3 + axis] = n[axis]
            }
            uvs[vi * 2] = (su + 1f) / 2f
            uvs[vi * 2 + 1] = (1f - sv) / 2f
        }

        val base = face * 4
        val ii = face * 6
        indices[ii] = base
        indices[ii + 1] = base + 1
        indices[ii + 2] = base + 2
        indices[ii + 3] = base
        indices[ii + 4] = base + 2
        indices[ii + 5] = base + 3
    }

    val positionBytes = floatsToBytes(positions)
    val normalBytes = floatsToBytes(normals)
    val uvBytes = floatsToBytes(uvs)
    val indexBytes = intsToUShortBytes(indices)
    val buffer = positionBytes + normalBytes + uvBytes + indexBytes
    val base64 = bytesToBase64(buffer)

    val normalOffset = positionBytes.size
    val uvOffset = normalOffset + normalBytes.size
    val indexOffset = uvOffset + uvBytes.size

    return """
{
  "asset": { "version": "2.0" },
  "scene": 0,
  "scenes": [{ "nodes": [0] }],
  "nodes": [{ "mesh": 0 }],
  "meshes": [{
    "primitives": [{
      "attributes": { "POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2 },
      "indices": 3,
      "material": 0
    }]
  }],
  "materials": [{
    "pbrMetallicRoughness": {
      "baseColorTexture": { "index": 0 },
      "metallicFactor": 0.0,
      "roughnessFactor": 0.8
    }
  }],
  "textures": [{ "source": 0, "sampler": 0 }],
  "samplers": [{ "magFilter": 9728, "minFilter": 9728, "wrapS": 10497, "wrapT": 10497 }],
  "images": [{ "uri": "data:image/png;base64,$CRATE_TEXTURE_PNG" }],
  "accessors": [
    { "bufferView": 0, "componentType": 5126, "count": 24, "type": "VEC3" },
    { "bufferView": 1, "componentType": 5126, "count": 24, "type": "VEC3" },
    { "bufferView": 2, "componentType": 5126, "count": 24, "type": "VEC2" },
    { "bufferView": 3, "componentType": 5123, "count": 36, "type": "SCALAR" }
  ],
  "bufferViews": [
    { "buffer": 0, "byteOffset": 0, "byteLength": ${positionBytes.size} },
    { "buffer": 0, "byteOffset": $normalOffset, "byteLength": ${normalBytes.size} },
    { "buffer": 0, "byteOffset": $uvOffset, "byteLength": ${uvBytes.size} },
    { "buffer": 0, "byteOffset": $indexOffset, "byteLength": ${indexBytes.size} }
  ],
  "buffers": [{ "byteLength": ${buffer.size}, "uri": "data:application/octet-stream;base64,$base64" }]
}
        """.trimIndent()
}

/** 8x8 two-tone checkerboard PNG (nearest-filtered for crisp squares). */
private const val CRATE_TEXTURE_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAGUlEQVR42mPYU+V0b1UeJsmAVRRIMgxKHQD/fm3BYWe/ygAAAABJRU5ErkJggg=="

/**
 * Creates a GLTF JSON string for a simple 5-pointed star.
 *
 * This demonstrates how GLTF data can be embedded and loaded.
 * In production, you'd load this from a file or network.
 */
private fun createStarGltf(): String {
    // Star vertices: 5 outer points + 5 inner points + center
    val outerRadius = 0.1f
    val innerRadius = 0.04f
    val vertices = mutableListOf<Float>()
    val indices = mutableListOf<Int>()

    // Center vertex
    vertices.addAll(listOf(0f, 0f, 0f))

    // Generate star points (alternating outer/inner)
    for (i in 0 until 10) {
        val angle = (i * 36f - 90f) * (PI.toFloat() / 180f)
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        vertices.add(cos(angle) * radius)
        vertices.add(sin(angle) * radius)
        vertices.add(0f)
    }

    // Generate triangles (center to each edge)
    for (i in 0 until 10) {
        indices.addAll(listOf(0, i + 1, ((i + 1) % 10) + 1))
    }

    // Encode vertices and indices as base64
    val vertexBytes = floatsToBytes(vertices.toFloatArray())
    val indexBytes = intsToUShortBytes(indices.toIntArray())

    val vertexBase64 = bytesToBase64(vertexBytes)
    val indexBase64 = bytesToBase64(indexBytes)

    // Build GLTF JSON
    return """
{
  "asset": { "version": "2.0" },
  "buffers": [
    { "uri": "data:application/octet-stream;base64,$vertexBase64", "byteLength": ${vertexBytes.size} },
    { "uri": "data:application/octet-stream;base64,$indexBase64", "byteLength": ${indexBytes.size} }
  ],
  "bufferViews": [
    { "buffer": 0, "byteOffset": 0, "byteLength": ${vertexBytes.size} },
    { "buffer": 1, "byteOffset": 0, "byteLength": ${indexBytes.size} }
  ],
  "accessors": [
    { "bufferView": 0, "componentType": 5126, "count": ${vertices.size / 3}, "type": "VEC3" },
    { "bufferView": 1, "componentType": 5123, "count": ${indices.size}, "type": "SCALAR" }
  ],
  "meshes": [{
    "primitives": [{
      "attributes": { "POSITION": 0 },
      "indices": 1
    }]
  }]
}
        """.trimIndent()
}

private fun floatsToBytes(floats: FloatArray): ByteArray {
    val bytes = ByteArray(floats.size * 4)
    for (i in floats.indices) {
        val bits = floats[i].toBits()
        bytes[i * 4] = (bits and 0xFF).toByte()
        bytes[i * 4 + 1] = ((bits shr 8) and 0xFF).toByte()
        bytes[i * 4 + 2] = ((bits shr 16) and 0xFF).toByte()
        bytes[i * 4 + 3] = ((bits shr 24) and 0xFF).toByte()
    }
    return bytes
}

private fun intsToUShortBytes(ints: IntArray): ByteArray {
    val bytes = ByteArray(ints.size * 2)
    for (i in ints.indices) {
        bytes[i * 2] = (ints[i] and 0xFF).toByte()
        bytes[i * 2 + 1] = ((ints[i] shr 8) and 0xFF).toByte()
    }
    return bytes
}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
private fun bytesToBase64(bytes: ByteArray): String =
    kotlin.io.encoding.Base64
        .encode(bytes)

fun Float.round2() = round(this * 100) / 100
