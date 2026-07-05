# Spatial Engine Usage Examples

## Basic AR Session

```kotlin
import com.spatialkit.engine.*

// Platform creates the engine (implementation-specific)
val engine: SpatialEngine = createWebXREngine() // or createARCoreEngine(), etc.

suspend fun startARSession() {
    // Initialize with configuration
    engine.initialize(
        EngineConfig(
            preferredMode = SessionMode.AR,
            enablePlaneDetection = true,
            enableLightEstimation = true
        )
    )

    // Check capabilities
    if (!engine.capabilities.supportsAR) {
        error("AR not supported on this device")
    }

    // Start AR session
    engine.startSession(SessionMode.AR)

    // Register frame callback
    engine.onFrame { frame ->
        handleFrame(frame)
    }
}

fun handleFrame(frame: FrameData) {
    // Check tracking quality
    when (frame.trackingState) {
        TrackingState.NORMAL -> {
            // Good tracking - update normally
        }
        TrackingState.LIMITED -> {
            // Show "Move device to improve tracking" message
        }
        TrackingState.NOT_AVAILABLE -> {
            // Show error or pause
        }
    }

    // Handle detected planes
    frame.detectedPlanes.forEach { plane ->
        when (plane.type) {
            PlaneType.HORIZONTAL_UP -> {
                // Floor or table detected
                // Could place furniture here
            }
            else -> {}
        }
    }

    // Handle input
    frame.inputSources.forEach { input ->
        when (input) {
            is InputSource.Screen -> {
                input.touches.forEach { touch ->
                    if (touch.phase == TouchPhase.BEGAN) {
                        handleTap(touch.screenX, touch.screenY)
                    }
                }
            }
            else -> {}
        }
    }

    // Build and submit scene
    val scene = buildMyScene(frame)
    engine.submitScene(scene)
}
```

## Hit Testing and Anchors

```kotlin
fun handleTap(screenX: Float, screenY: Float) {
    // Perform hit test to find where user tapped.
    // Coordinates are normalized 0–1: (0,0) = top-left, (1,1) = bottom-right.
    val hit = engine.performHitTest(screenX, screenY) ?: return

    // Create anchor at hit point
    val anchor = AnchorRequest(
        id = "user_tap_$tapCounter",
        type = if (hit.planeId != null) {
            // Prefer plane anchor for stability
            AnchorType.Plane(
                planeId = hit.planeId,
                offsetFromCenter = Vec3.ZERO
            )
        } else {
            // Fall back to hit test anchor
            AnchorType.HitTest(
                hitPoint = hit.position,
                hitNormal = hit.normal
            )
        }
    )

    // Place object at anchor (will be included in next submitScene call)
    placedAnchors.add(anchor)
}

fun buildMyScene(frame: FrameData): SceneData {
    val nodes = mutableListOf<SceneNode>()

    // Create objects at each tracked anchor
    frame.anchors.forEach { anchor ->
        if (anchor.status == AnchorStatus.TRACKING && anchor.pose != null) {
            nodes.add(
                SceneNode.Mesh(
                    id = "mesh_${anchor.id}",
                    transform = Transform(
                        position = anchor.pose.position,
                        rotation = anchor.pose.rotation,
                        scale = Vec3.ONE
                    ),
                    geometry = GeometryData(type = GeometryType.CUBE),
                    material = MaterialData(
                        type = MaterialType.PBR,
                        baseColor = Vec4(1f, 0f, 0f, 1f), // Red
                        metallic = 0f,
                        roughness = 0.5f
                    )
                )
            )
        }
    }

    return SceneData(
        nodes = nodes,
        anchors = placedAnchors
    )
}
```

## VR Session with Controllers

```kotlin
suspend fun startVRSession() {
    engine.initialize(
        EngineConfig(preferredMode = SessionMode.VR)
    )

    if (!engine.capabilities.supportsVR) {
        error("VR not supported")
    }

    engine.startSession(SessionMode.VR)

    engine.onFrame { frame ->
        handleVRFrame(frame)
    }
}

fun handleVRFrame(frame: FrameData) {
    val nodes = mutableListOf<SceneNode>()

    // Handle controller input
    frame.inputSources.forEach { input ->
        when (input) {
            is InputSource.Controller -> {
                // Draw controller model
                nodes.add(
                    SceneNode.Mesh(
                        id = "controller_${input.hand}",
                        transform = Transform(
                            position = input.pose.position,
                            rotation = input.pose.rotation
                        ),
                        geometry = GeometryData(type = GeometryType.CAPSULE),
                        material = MaterialData(
                            type = MaterialType.LIT,
                            baseColor = Vec4(0.8f, 0.8f, 0.8f, 1f)
                        )
                    )
                )

                // Handle button presses
                if (input.buttons[Button.TRIGGER] == ButtonState.PRESSED) {
                    performRaycast(input.pose.position, input.pose.rotation)
                }
            }
            else -> {}
        }
    }

    engine.submitScene(SceneData(nodes, emptyList()))
}
```

## Custom Mesh

```kotlin
fun createCustomMesh(): SceneNode.Mesh {
    // Triangle vertices
    val vertices = floatArrayOf(
        -0.5f, 0f, 0f,  // vertex 0
         0.5f, 0f, 0f,  // vertex 1
         0f, 1f, 0f     // vertex 2
    )

    val indices = intArrayOf(0, 1, 2)

    val normals = floatArrayOf(
        0f, 0f, 1f,  // normal for v0
        0f, 0f, 1f,  // normal for v1
        0f, 0f, 1f   // normal for v2
    )

    return SceneNode.Mesh(
        id = "custom_triangle",
        transform = Transform(),
        geometry = GeometryData(
            type = GeometryType.CUSTOM_MESH,
            vertices = vertices,
            indices = indices,
            normals = normals
        ),
        material = MaterialData(
            type = MaterialType.UNLIT,
            baseColor = Vec4(1f, 1f, 0f, 1f) // Yellow
        )
    )
}
```

## Scene Hierarchy

```kotlin
fun buildHierarchicalScene(): SceneData {
    // Parent group
    val solarSystem = SceneNode.Group(
        id = "solar_system",
        transform = Transform(position = Vec3(0f, 1f, -2f)),
        children = listOf(
            // Sun (parent)
            SceneNode.Mesh(
                id = "sun",
                transform = Transform(scale = Vec3(0.5f, 0.5f, 0.5f)),
                geometry = GeometryData(type = GeometryType.SPHERE),
                material = MaterialData(
                    type = MaterialType.UNLIT,
                    baseColor = Vec4(1f, 1f, 0f, 1f)
                )
            ),
            // Earth (orbits sun)
            SceneNode.Group(
                id = "earth_orbit",
                transform = Transform(position = Vec3(2f, 0f, 0f)),
                children = listOf(
                    SceneNode.Mesh(
                        id = "earth",
                        transform = Transform(scale = Vec3(0.3f, 0.3f, 0.3f)),
                        geometry = GeometryData(type = GeometryType.SPHERE),
                        material = MaterialData(
                            type = MaterialType.PBR,
                            baseColor = Vec4(0f, 0.3f, 1f, 1f),
                            roughness = 0.8f
                        )
                    )
                )
            )
        )
    )

    return SceneData(nodes = listOf(solarSystem), anchors = emptyList())
}
```

## Error Handling

```kotlin
suspend fun robustEngineSetup() {
    try {
        engine.initialize(EngineConfig())
    } catch (e: Exception) {
        // Check error state (engine.state is a StateFlow<EngineState>)
        when (val state = engine.state.value) {
            is EngineState.Error -> {
                when (val error = state.error) {
                    is EngineError.NotSupported -> {
                        // Show "AR not available" message
                    }
                    is EngineError.PermissionDenied -> {
                        // Request camera permission
                    }
                    is EngineError.InitializationFailed -> {
                        // Show error: error.reason
                    }
                    else -> {}
                }
            }
            else -> {}
        }
    }
}
```