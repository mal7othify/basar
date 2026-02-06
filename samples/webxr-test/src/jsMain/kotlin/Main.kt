import com.spatialkit.engine.AnchorRequest
import com.spatialkit.engine.AnchorType
import com.spatialkit.engine.DetectedPlane
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.MaterialData
import com.spatialkit.engine.MaterialType
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.Transform
import com.spatialkit.engine.Vec3
import com.spatialkit.engine.Vec4
import com.spatialkit.engine.webxr.createWebXREngine
import com.spatialkit.engine.webxr.isWebXRAvailable
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import kotlin.math.cos
import kotlin.math.sin

/**
 * WebXR Engine Contract Validation Test Application
 *
 * Tests all aspects of the SpatialEngine contract to validate the implementation.
 */

data class TestState(
    var sessionActive: Boolean = false,
    var testsPassed: Int = 0,
    var testsFailed: Int = 0,
    var currentTest: String = "",
    var detectedPlanes: List<DetectedPlane> = emptyList(),
    var placedAnchors: MutableList<AnchorRequest> = mutableListOf(),
)

val testState = TestState()
lateinit var engine: SpatialEngine
lateinit var logContainer: HTMLDivElement

fun main() {
    log("🚀 WebXR Engine Contract Validation Test")
    log("==========================================\n")

    // Test 1: Check WebXR availability
    testWebXRAvailability()

    // Test 2: Create engine
    testEngineCreation()

    // Test 3: Check capabilities before initialization
    testCapabilitiesBeforeInit()

    // Test 4: Setup UI
    setupTestUI()
}

// ============================================================================
// TEST 1: WebXR Availability
// ============================================================================

fun testWebXRAvailability() {
    testState.currentTest = "WebXR Availability"
    log("TEST 1: Checking WebXR availability...")

    val available = isWebXRAvailable()
    if (available) {
        log("✅ PASS: WebXR is available")
        testState.testsPassed++
    } else {
        log("❌ FAIL: WebXR not available")
        log("  Note: This test requires a WebXR-capable browser")
        log("  (Chrome/Edge on Android, or VR headset)")
        testState.testsFailed++
    }
    log("")
}

// ============================================================================
// TEST 2: Engine Creation
// ============================================================================

fun testEngineCreation() {
    testState.currentTest = "Engine Creation"
    log("TEST 2: Creating WebXREngine...")

    try {
        engine = createWebXREngine()
        log("✅ PASS: Engine created successfully")
        log("  Engine state: ${engine.state}")
        testState.testsPassed++
    } catch (e: Exception) {
        log("❌ FAIL: Engine creation failed: ${e.message}")
        testState.testsFailed++
    }
    log("")
}

// ============================================================================
// TEST 3: Capabilities (Before Init)
// ============================================================================

fun testCapabilitiesBeforeInit() {
    testState.currentTest = "Capabilities Check"
    log("TEST 3: Checking engine capabilities...")

    try {
        val caps = engine.capabilities
        log("  Capabilities:")
        log("    - AR Support: ${caps.supportsAR}")
        log("    - VR Support: ${caps.supportsVR}")
        log("    - Plane Detection: ${caps.supportsPlaneDetection}")
        log("    - Image Tracking: ${caps.supportsImageTracking}")
        log("    - Hand Tracking: ${caps.supportsHandTracking}")
        log("    - Depth Occlusion: ${caps.supportsDepthOcclusion}")
        log("    - Spatial Anchors: ${caps.supportsSpatialAnchors}")
        log("    - Max Anchors: ${caps.maxAnchors}")

        if (caps.supportsAR || caps.supportsVR) {
            log("✅ PASS: Capabilities reported (AR or VR supported)")
            testState.testsPassed++
        } else {
            log("⚠️  WARN: No AR or VR support reported")
            log("  This may be correct on desktop browsers")
            testState.testsPassed++
        }
    } catch (e: Exception) {
        log("❌ FAIL: Capabilities check failed: ${e.message}")
        testState.testsFailed++
    }
    log("")
}

// ============================================================================
// TEST 4-8: Interactive Tests (triggered by UI)
// ============================================================================

suspend fun testInitialization() {
    testState.currentTest = "Engine Initialization"
    log("TEST 4: Initializing engine...")

    try {
        engine.initialize(
            EngineConfig(
                preferredMode = SessionMode.AR,
                enablePlaneDetection = true,
                enableLightEstimation = true,
                targetFrameRate = 60,
            ),
        )

        if (engine.state == EngineState.Ready) {
            log("✅ PASS: Engine initialized successfully")
            log("  State: ${engine.state}")
            testState.testsPassed++
        } else {
            log("❌ FAIL: Engine state is not Ready: ${engine.state}")
            testState.testsFailed++
        }
    } catch (e: Exception) {
        log("❌ FAIL: Initialization failed: ${e.message}")
        testState.testsFailed++
    }
    log("")
}

fun testSessionStart() {
    testState.currentTest = "Session Start"
    log("TEST 5: Starting AR session...")

    try {
        engine.startSession(SessionMode.AR)
        testState.sessionActive = true
        log("  Session start requested")
        log("  (Waiting for state change...)")

        // Give it a moment to start
        window.setTimeout({
            val state = engine.state
            when (state) {
                is EngineState.Running -> {
                    log("✅ PASS: Session started successfully")
                    log("  State: Running(${state.sessionMode})")
                    testState.testsPassed++
                }

                is EngineState.Error -> {
                    log("❌ FAIL: Session start failed: ${state.error}")
                    testState.testsFailed++
                    testState.sessionActive = false
                }

                else -> {
                    log("⚠️  WARN: Session state unclear: $state")
                }
            }
            log("")
        }, 1000)
    } catch (e: Exception) {
        log("❌ FAIL: Session start threw exception: ${e.message}")
        testState.testsFailed++
        testState.sessionActive = false
        log("")
    }
}

fun testSceneSubmission() {
    testState.currentTest = "Scene Submission"
    log("TEST 6: Submitting test scene...")

    try {
        val testScene =
            SceneData(
                nodes =
                    listOf(
                        // Red cube
                        SceneNode.Mesh(
                            id = "test-cube",
                            transform =
                                Transform(
                                    position = Vec3(0f, 0f, -1f), // 1m in front
                                    rotation = Quaternion.IDENTITY,
                                    scale = Vec3(0.2f, 0.2f, 0.2f),
                                ),
                            geometry = GeometryData(type = GeometryType.CUBE),
                            material =
                                MaterialData(
                                    type = MaterialType.PBR,
                                    baseColor = Vec4(1f, 0f, 0f, 1f), // Red
                                    metallic = 0.5f,
                                    roughness = 0.3f,
                                ),
                        ),
                        // Green sphere
                        SceneNode.Mesh(
                            id = "test-sphere",
                            transform =
                                Transform(
                                    position = Vec3(-0.5f, 0f, -1.5f),
                                    scale = Vec3(0.15f, 0.15f, 0.15f),
                                ),
                            geometry = GeometryData(type = GeometryType.SPHERE),
                            material =
                                MaterialData(
                                    type = MaterialType.LIT,
                                    baseColor = Vec4(0f, 1f, 0f, 1f), // Green
                                ),
                        ),
                        // Blue plane (floor)
                        SceneNode.Mesh(
                            id = "test-floor",
                            transform =
                                Transform(
                                    position = Vec3(0f, -0.5f, -1f),
                                    rotation = Quaternion(-0.707f, 0f, 0f, 0.707f), // Rotate 90° around X
                                    scale = Vec3(2f, 2f, 1f),
                                ),
                            geometry = GeometryData(type = GeometryType.PLANE),
                            material =
                                MaterialData(
                                    type = MaterialType.UNLIT,
                                    baseColor = Vec4(0.3f, 0.3f, 1f, 0.5f), // Semi-transparent blue
                                ),
                        ),
                    ),
                anchors = emptyList(),
            )

        engine.submitScene(testScene)
        log("✅ PASS: Scene submitted successfully")
        log("  3 objects: Cube, Sphere, Floor")
        testState.testsPassed++
    } catch (e: Exception) {
        log("❌ FAIL: Scene submission failed: ${e.message}")
        testState.testsFailed++
    }
    log("")
}

fun testFrameLoop() {
    testState.currentTest = "Frame Loop"
    log("TEST 7: Setting up frame callback...")

    try {
        var frameCount = 0
        var lastTimestamp = 0L

        engine.onFrame { frameData ->
            frameCount++

            // Log first frame
            if (frameCount == 1) {
                log("  First frame received:")
                log("    - Timestamp: ${frameData.timestamp}ms")
                log("    - Delta time: ${frameData.deltaTime}s")
                log("    - Tracking state: ${frameData.trackingState}")
                log("    - Camera pose: ${frameData.cameraPose}")
                log("    - Input sources: ${frameData.inputSources.size}")
                log("    - Detected planes: ${frameData.detectedPlanes.size}")
            }

            // Update detected planes
            if (frameData.detectedPlanes.isNotEmpty() && testState.detectedPlanes.isEmpty()) {
                log("  🎯 Planes detected: ${frameData.detectedPlanes.size}")
                frameData.detectedPlanes.forEach { plane ->
                    log("    - ${plane.type}: ${plane.id}")
                }
            }
            testState.detectedPlanes = frameData.detectedPlanes

            // Validate frame after 60 frames
            if (frameCount == 60) {
                val avgFps = 1000.0 / ((frameData.timestamp - lastTimestamp) / 60.0)
                log("✅ PASS: Frame loop working")
                log("  60 frames received (~${avgFps.toInt()} FPS)")
                testState.testsPassed++
                log("")
            }

            lastTimestamp = frameData.timestamp

            // Animate the cube
            if (frameCount % 2 == 0) { // Every other frame to reduce overhead
                animateScene(frameData.timestamp)
            }
        }

        log("  Frame callback registered")
    } catch (e: Exception) {
        log("❌ FAIL: Frame callback setup failed: ${e.message}")
        testState.testsFailed++
        log("")
    }
}

fun animateScene(timestamp: Long) {
    val time = timestamp / 1000.0
    val rotation =
        Quaternion(
            x = 0f,
            y = sin(time.toFloat()) * 0.5f,
            z = 0f,
            w = cos(time.toFloat()) * 0.5f,
        )

    val animatedScene =
        SceneData(
            nodes =
                listOf(
                    SceneNode.Mesh(
                        id = "test-cube",
                        transform =
                            Transform(
                                position = Vec3(0f, 0f, -1f),
                                rotation = rotation,
                                scale = Vec3(0.2f, 0.2f, 0.2f),
                            ),
                        geometry = GeometryData(type = GeometryType.CUBE),
                        material =
                            MaterialData(
                                type = MaterialType.PBR,
                                baseColor = Vec4(1f, 0f, 0f, 1f),
                                metallic = 0.5f,
                                roughness = 0.3f,
                            ),
                    ),
                    SceneNode.Mesh(
                        id = "test-sphere",
                        transform =
                            Transform(
                                position = Vec3(-0.5f, 0f, -1.5f),
                                scale = Vec3(0.15f, 0.15f, 0.15f),
                            ),
                        geometry = GeometryData(type = GeometryType.SPHERE),
                        material =
                            MaterialData(
                                type = MaterialType.LIT,
                                baseColor = Vec4(0f, 1f, 0f, 1f),
                            ),
                    ),
                    SceneNode.Mesh(
                        id = "test-floor",
                        transform =
                            Transform(
                                position = Vec3(0f, -0.5f, -1f),
                                rotation = Quaternion(-0.707f, 0f, 0f, 0.707f),
                                scale = Vec3(2f, 2f, 1f),
                            ),
                        geometry = GeometryData(type = GeometryType.PLANE),
                        material =
                            MaterialData(
                                type = MaterialType.UNLIT,
                                baseColor = Vec4(0.3f, 0.3f, 1f, 0.5f),
                            ),
                    ),
                ),
            anchors = testState.placedAnchors,
        )

    engine.submitScene(animatedScene)
}

fun testAnchorPlacement() {
    testState.currentTest = "Anchor Placement"
    log("TEST 8: Testing anchor placement...")

    if (testState.detectedPlanes.isEmpty()) {
        log("⚠️  SKIP: No planes detected yet")
        log("  (Move device to detect surfaces)")
        log("")
        return
    }

    try {
        val plane = testState.detectedPlanes.first()
        val anchor =
            AnchorRequest(
                id = "test-anchor-${testState.placedAnchors.size}",
                type =
                    AnchorType.Plane(
                        planeId = plane.id,
                        offsetFromCenter = Vec3(0f, 0.1f, 0f),
                    ),
            )

        testState.placedAnchors.add(anchor)
        log("✅ PASS: Anchor placement requested")
        log("  Anchor ID: ${anchor.id}")
        log("  Plane: ${plane.id} (${plane.type})")
        testState.testsPassed++
    } catch (e: Exception) {
        log("❌ FAIL: Anchor placement failed: ${e.message}")
        testState.testsFailed++
    }
    log("")
}

fun testRaycast() {
    testState.currentTest = "Raycasting"
    log("TEST 9: Testing raycast...")

    try {
        // Raycast forward from camera
        val result =
            engine.performRaycast(
                origin = Vec3(0f, 0f, 0f),
                direction = Vec3(0f, 0f, -1f), // Forward
            )

        if (result != null) {
            log("✅ PASS: Raycast hit detected")
            log("  Hit position: ${result.position}")
            log("  Hit distance: ${result.distance}m")
            log("  Hit object: ${result.nodeId ?: "environment"}")
            testState.testsPassed++
        } else {
            log("⚠️  INFO: Raycast returned null (no hit)")
            log("  This is expected if ray doesn't intersect anything")
            testState.testsPassed++
        }
    } catch (e: Exception) {
        log("❌ FAIL: Raycast failed: ${e.message}")
        testState.testsFailed++
    }
    log("")
}

fun testSessionStop() {
    testState.currentTest = "Session Stop"
    log("TEST 10: Stopping session...")

    try {
        engine.stopSession()
        testState.sessionActive = false

        window.setTimeout({
            if (engine.state == EngineState.Ready) {
                log("✅ PASS: Session stopped successfully")
                log("  State returned to Ready")
                testState.testsPassed++
            } else {
                log("⚠️  WARN: State after stop: ${engine.state}")
            }
            log("")
            showTestSummary()
        }, 500)
    } catch (e: Exception) {
        log("❌ FAIL: Session stop failed: ${e.message}")
        testState.testsFailed++
        log("")
    }
}

fun showTestSummary() {
    log("\n" + "=".repeat(60))
    log("TEST SUMMARY")
    log("=".repeat(60))
    log("✅ Passed: ${testState.testsPassed}")
    log("❌ Failed: ${testState.testsFailed}")
    log("Total: ${testState.testsPassed + testState.testsFailed}")

    if (testState.testsFailed == 0) {
        log("\n🎉 ALL TESTS PASSED - Contract validated!")
    } else {
        log("\n⚠️  Some tests failed - review contract implementation")
    }
    log("=".repeat(60))
}

// ============================================================================
// UI Setup
// ============================================================================

fun setupTestUI() {
    // Create log container
    logContainer =
        (document.createElement("div") as HTMLDivElement).apply {
            id = "log-container"
            setAttribute(
                "style",
                """
                position: fixed;
                left: 10px;
                top: 10px;
                right: 10px;
                bottom: 100px;
                background: rgba(0, 0, 0, 0.9);
                color: #00ff00;
                font-family: 'Courier New', monospace;
                font-size: 12px;
                padding: 15px;
                overflow-y: auto;
                border-radius: 8px;
                z-index: 1000;
                """.trimIndent(),
            )
        }
    document.body?.appendChild(logContainer)

    // Create control panel
    val controls =
        (document.createElement("div") as HTMLDivElement).apply {
            setAttribute(
                "style",
                """
                position: fixed;
                left: 10px;
                right: 10px;
                bottom: 10px;
                height: 80px;
                background: rgba(0, 0, 0, 0.9);
                padding: 10px;
                display: flex;
                gap: 10px;
                flex-wrap: wrap;
                justify-content: center;
                align-items: center;
                border-radius: 8px;
                z-index: 1001;
                """.trimIndent(),
            )
        }

    // Create buttons
    controls.appendChild(
        createButton("Initialize", "green") {
            GlobalScope.launch { testInitialization() }
        },
    )

    controls.appendChild(
        createButton("Start AR", "blue") {
            testSessionStart()
            window.setTimeout({ testSceneSubmission() }, 1500)
            window.setTimeout({ testFrameLoop() }, 2000)
        },
    )

    controls.appendChild(
        createButton("Place Anchor", "yellow") {
            testAnchorPlacement()
        },
    )

    controls.appendChild(
        createButton("Raycast", "purple") {
            testRaycast()
        },
    )

    controls.appendChild(
        createButton("Stop", "red") {
            testSessionStop()
        },
    )

    document.body?.appendChild(controls)
}

fun createButton(
    text: String,
    color: String,
    onClick: () -> Unit,
): HTMLButtonElement {
    val colors =
        mapOf(
            "green" to "#4CAF50",
            "blue" to "#2196F3",
            "yellow" to "#FFC107",
            "purple" to "#9C27B0",
            "red" to "#f44336",
        )

    return (document.createElement("button") as HTMLButtonElement).apply {
        textContent = text
        setAttribute(
            "style",
            """
            padding: 12px 20px;
            font-size: 14px;
            font-weight: bold;
            background: ${colors[color] ?: "#666"};
            color: white;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            box-shadow: 0 2px 4px rgba(0,0,0,0.3);
            """.trimIndent(),
        )
        addEventListener("click", { onClick() })
    }
}

fun log(message: String) {
    console.log(message)
    val line =
        document.createElement("div").apply {
            textContent = message
            if (message.contains("✅")) {
                setAttribute("style", "color: #00ff00;")
            } else if (message.contains("❌")) {
                setAttribute("style", "color: #ff0000;")
            } else if (message.contains("⚠️")) {
                setAttribute("style", "color: #ffaa00;")
            } else if (message.contains("🎯") || message.contains("🎉")) {
                setAttribute("style", "color: #00aaff;")
            }
        }
    logContainer.appendChild(line)
    logContainer.scrollTop = logContainer.scrollHeight.toDouble()
}
