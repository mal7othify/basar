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
import com.spatialkit.dsl.cm
import com.spatialkit.dsl.rgb
import com.spatialkit.dsl.vec3
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.SessionMode
import com.spatialkit.scene.Easing
import com.spatialkit.scene.SpatialAnimator
import com.spatialkit.scene.SpatialSession
import kotlin.math.round

/**
 * Comprehensive demo of Spatial-KMP features:
 * - All shape types (Cube, Sphere, Cylinder, Capsule, Plane)
 * - Raycasting (tap to detect shapes)
 * - Animations (rotating and bouncing objects)
 */
@Composable
fun App() {
    var sessionReady by remember { mutableStateOf(false) }
    var lastTapInfo by remember { mutableStateOf("Tap objects to test raycasting") }
    var session: SpatialSession? by remember { mutableStateOf(null) }
    var lastFrameData by remember { mutableStateOf<FrameData?>(null) }

    // Create animator for animations
    val animator = remember { SpatialAnimator() }

    // Remember the nodes we want to animate
    var redCube by remember { mutableStateOf<com.spatialkit.scene.CubeNode?>(null) }
    var greenSphere by remember { mutableStateOf<com.spatialkit.scene.SphereNode?>(null) }
    var yellowCapsule by remember { mutableStateOf<com.spatialkit.scene.CapsuleNode?>(null) }
    var magentaPlane by remember { mutableStateOf<com.spatialkit.scene.PlaneNode?>(null) }

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
                        "Spatial-KMP Demo",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("Session: ${if (sessionReady) "✓ Ready" else "⌛ Starting..."}")
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
                                    // Get screen dimensions
                                    val screenWidth = size.width.toFloat()
                                    val screenHeight = size.height.toFloat()

                                    // Convert tap position to normalized coordinates
                                    val normalizedX = offset.x / screenWidth
                                    val normalizedY = offset.y / screenHeight

                                    // Convert screen point to ray (simplified version)
                                    // In production, you'd use the projection matrix properly
                                    val cameraPose = frameData.cameraPose

                                    // Create a ray from camera forward direction
                                    // Offset slightly based on tap position
                                    val forward = cameraPose.rotation * Vec3(0f, 0f, -1f)
                                    val right = cameraPose.rotation * Vec3(1f, 0f, 0f)
                                    val up = cameraPose.rotation * Vec3(0f, 1f, 0f)

                                    // Offset ray direction based on tap (simplified)
                                    val offsetX = (normalizedX - 0.5f) * 0.5f
                                    val offsetY = -(normalizedY - 0.5f) * 0.5f

                                    val rayDirection =
                                        (forward + right * offsetX + up * offsetY).normalized()

                                    // Perform raycast
                                    val result =
                                        currentSession.performRaycast(
                                            origin = cameraPose.position,
                                            direction = rayDirection,
                                        )

                                    if (result != null) {
                                        lastTapInfo = "Hit: ${result.nodeId?.take(8)} at ${result.distance.round2()}m"

                                        // Find the hit node and animate it
                                        val hitNode = currentSession.scene.findNodeById(result.nodeId ?: "")
                                        if (hitNode != null) {
                                            // Pulse animation on hit
                                            val originalScale = hitNode.scale
                                            animator.animateScale(
                                                node = hitNode,
                                                from = originalScale,
                                                to = originalScale * 1.3f,
                                                duration = 0.2f,
                                                easing = Easing.EaseOut,
                                                onComplete = {
                                                    // Scale back down
                                                    animator.animateScale(
                                                        node = hitNode,
                                                        from = hitNode.scale,
                                                        to = originalScale,
                                                        duration = 0.2f,
                                                        easing = Easing.EaseOut,
                                                    )
                                                },
                                            )
                                        }
                                    } else {
                                        lastTapInfo =
                                            "No hit - tapped at (${normalizedX.round2()}, ${normalizedY.round2()})"
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

                        // Start animations when session is ready
                        redCube?.let { cube ->
                            fun rotateRedCube() {
                                animator.animateRotation(
                                    node = cube,
                                    from = cube.rotation,
                                    to = cube.rotation * Quaternion.fromEuler(0f, 360f, 0f),
                                    duration = 3.0f,
                                    easing = Easing.Linear,
                                    onComplete = { rotateRedCube() },
                                )
                            }
                            rotateRedCube()
                        }

                        greenSphere?.let { sphere ->
                            fun bounceGreenSphere() {
                                val currentY = sphere.position.y
                                animator.animatePosition(
                                    node = sphere,
                                    from = sphere.position,
                                    to = sphere.position.copy(y = currentY + 0.15f),
                                    duration = 0.8f,
                                    easing = Easing.EaseOutBounce,
                                    onComplete = {
                                        animator.animatePosition(
                                            node = sphere,
                                            from = sphere.position,
                                            to = sphere.position.copy(y = currentY),
                                            duration = 0.8f,
                                            easing = Easing.EaseOutBounce,
                                            onComplete = { bounceGreenSphere() },
                                        )
                                    },
                                )
                            }
                            bounceGreenSphere()
                        }
                    },
                    onFrame = { frameData ->
                        // Store latest frame data for raycasting
                        lastFrameData = frameData
                        // Update animations each frame
                        animator.update(frameData.deltaTime)
                    },
                ) {
                    // === ROW 1: Basic Shapes (Front) ===

                    // Red Cube (animated rotation)
                    redCube =
                        cube(size = 8.cm, color = rgb(0xFF0000)) {
                            position = vec3(-0.3f, 0.1f, -0.8f)
                        }

                    // Green Sphere (animated bounce)
                    greenSphere =
                        sphere(radius = 4.cm, color = rgb(0x00FF00)) {
                            position = vec3(0f, 0.1f, -0.8f)
                        }

                    // Blue Cylinder
                    cylinder(radius = 3.cm, height = 12.cm, color = rgb(0x0000FF)) {
                        position = vec3(0.3f, 0.1f, -0.8f)
                    }

                    // === ROW 2: Advanced Shapes (Middle - moved closer and bigger) ===

                    // Yellow Capsule (made bigger and closer)
                    yellowCapsule =
                        capsule(radius = 5.cm, height = 15.cm, color = rgb(0xFFFF00)) {
                            position = vec3(-0.4f, 0.15f, -1.0f)
                        }

                    // Magenta Plane (facing camera, made bigger)
                    magentaPlane =
                        plane(width = 15.cm, height = 15.cm, color = rgb(0xFF00FF)) {
                            position = vec3(0.4f, 0.15f, -1.0f)
                            // No rotation - keep it facing the camera
                        }

                    // === ROW 3: Multi-colored shapes (Far) ===

                    // Cyan Cube
                    cube(size = 6.cm, color = rgb(0x00FFFF)) {
                        position = vec3(-0.15f, 0.1f, -1.6f)
                    }

                    // Orange Sphere
                    sphere(radius = 3.cm, color = rgb(0xFFA500)) {
                        position = vec3(0.15f, 0.1f, -1.6f)
                    }
                }
            }
        }
    }
}

fun Float.round2() = round(this * 100) / 100
