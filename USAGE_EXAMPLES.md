# Spatial-KMP Usage Examples

Complete guide to using the Spatial-KMP library, from basic to advanced patterns.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Basic Examples](#basic-examples)
3. [Compose Integration](#compose-integration)
4. [Advanced Patterns](#advanced-patterns)
5. [Platform-Specific](#platform-specific)

---

## Quick Start

### Installation

Add to your `build.gradle.kts`:

```kotlin
commonMain.dependencies {
    implementation(project(":spatial-compose"))

    // For Android ARCore support:
    implementation(project(":spatial-engine-arcore"))

    // For WebXR support:
    implementation(project(":spatial-engine-webxr"))
}
```

### Minimal Example

```kotlin
import androidx.compose.runtime.Composable
import com.spatialkit.compose.SpatialView
import com.spatialkit.engine.SessionMode
import com.spatialkit.dsl.*

@Composable
fun App() {
    SpatialView(mode = SessionMode.AR) {
        // Place a red cube 1 meter in front of user
        cube(size = 10.cm, color = rgb(0xFF0000)) {
            position = vec3(0f, 1.5.meters, -1.meters)
        }
    }
}
```

That's it! Your first spatial AR app in 10 lines.

---

## Basic Examples

### Example 1: Simple Scene with Multiple Objects

```kotlin
@Composable
fun SimpleScene() {
    SpatialView(mode = SessionMode.AR) {
        // Red cube
        cube(size = 10.cm, color = rgb(0xFF0000)) {
            position = vec3(-0.3f, 1.5f, -1f)
        }

        // Green sphere
        sphere(radius = 5.cm, color = rgb(0x00FF00)) {
            position = vec3(0f, 1.5f, -1f)
        }

        // Blue cylinder
        cylinder(radius = 5.cm, height = 20.cm, color = rgb(0x0000FF)) {
            position = vec3(0.3f, 1.5f, -1f)
        }
    }
}
```

### Example 2: Grouped Objects

```kotlin
@Composable
fun GroupedObjects() {
    SpatialView {
        // Create a group of objects that move together
        group(id = "solar-system") {
            position = vec3(0f, 1.5f, -2f)
            rotation = eulerDegrees(0, 45, 0)

            // Sun (center)
            sphere(radius = 10.cm, color = rgba(255, 200, 0, 255))

            // Earth (orbiting)
            sphere(radius = 3.cm, color = rgb(0x0088FF)) {
                position = vec3(30.cm, 0f, 0f)
            }
        }
    }
}
```

### Example 3: Plane Anchoring

```kotlin
@Composable
fun PlaneAnchoredContent() {
    var detectedPlane by remember { mutableStateOf<TrackedPlane?>(null) }

    SpatialView(
        mode = SessionMode.AR,
        onFrame = { frameData ->
            // Track first horizontal plane
            if (detectedPlane == null) {
                detectedPlane = frameData.planes.firstOrNull {
                    it.type == PlaneType.HORIZONTAL
                }
            }
        }
    ) {
        // Place content on detected plane
        detectedPlane?.let { plane ->
            anchorToPlane(planeId = plane.id) {
                // Place a table on the floor
                cube(
                    width = 1.meters,
                    height = 5.cm,
                    depth = 60.cm,
                    color = rgb(0x8B4513)
                )

                // Place objects on the table
                sphere(radius = 5.cm, color = rgb(0xFF0000)) {
                    position = vec3(0f, 10.cm, 0f)
                }
            }
        }
    }
}
```

---

## Compose Integration

### Example 4: Reactive State

```kotlin
@Composable
fun ReactiveAnimation() {
    // Animate rotation over time
    var rotation by rememberSpatialState { Quaternion.IDENTITY }

    SpatialView {
        cube(size = 10.cm, color = rgb(0xFF00FF)) {
            position = vec3(0f, 1.5f, -1f)
            this.rotation = rotation  // Updates when rotation changes
        }
    }

    // Animate in background
    LaunchedEffect(Unit) {
        var angle = 0f
        while (true) {
            delay(16) // 60 FPS
            angle += 1f
            rotation = eulerDegrees(0, angle, 0)
        }
    }
}
```

### Example 5: User Interaction

```kotlin
@Composable
fun InteractiveObjects() {
    var selectedColor by remember { mutableStateOf(rgb(0xFF0000)) }

    Column {
        // Color picker UI
        Row {
            Button(onClick = { selectedColor = rgb(0xFF0000) }) { Text("Red") }
            Button(onClick = { selectedColor = rgb(0x00FF00) }) { Text("Green") }
            Button(onClick = { selectedColor = rgb(0x0000FF) }) { Text("Blue") }
        }

        // Spatial content with reactive color
        SpatialView {
            cube(size = 15.cm, color = selectedColor) {
                position = vec3(0f, 1.5f, -1f)
            }
        }
    }
}
```

### Example 6: Session Lifecycle

```kotlin
@Composable
fun LifecycleExample() {
    var sessionReady by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    SpatialView(
        mode = SessionMode.AR,
        onSessionReady = { session ->
            sessionReady = true
            println("Session capabilities: ${session.capabilities}")
        },
        onFrame = { frameData ->
            // Called every frame
            println("Camera pose: ${frameData.cameraPose}")
        },
        onError = { error ->
            errorMessage = when (error) {
                is EngineError.NotSupported -> "AR not supported"
                is EngineError.PermissionDenied -> "Camera permission required"
                is EngineError.InitializationFailed -> "Failed: ${error.reason}"
                is EngineError.SessionLost -> "Session lost, restart required"
            }
        }
    ) {
        if (sessionReady) {
            sphere(radius = 10.cm, color = rgb(0x00FF00)) {
                position = vec3(0f, 1.5f, -1f)
            }
        }
    }

    errorMessage?.let { error ->
        Text(error, color = Color.Red)
    }
}
```

---

## Advanced Patterns

### Example 7: Dynamic Scene Building

```kotlin
@Composable
fun DynamicScene() {
    var objectCount by remember { mutableStateOf(5) }

    Column {
        Slider(
            value = objectCount.toFloat(),
            onValueChange = { objectCount = it.toInt() },
            valueRange = 1f..20f
        )

        SpatialView {
            // Generate objects dynamically
            repeat(objectCount) { i ->
                val angle = (i.toFloat() / objectCount) * 360f
                val radius = 50.cm

                cube(
                    size = 5.cm,
                    color = rgb((i * 255 / objectCount) shl 16),
                    id = "cube-$i"
                ) {
                    position = vec3(
                        x = radius * cos(angle.radians),
                        y = 1.5f,
                        z = -1f + radius * sin(angle.radians)
                    )
                }
            }
        }
    }
}
```

### Example 8: Hit Testing

```kotlin
@Composable
fun HitTestExample() {
    var hitPosition by remember { mutableStateOf<Vec3?>(null) }
    var session by remember { mutableStateOf<SpatialSession?>(null) }

    Column {
        Box(modifier = Modifier.fillMaxSize()) {
            SpatialView(
                onSessionReady = { session = it }
            ) {
                // Show marker at hit location
                hitPosition?.let { pos ->
                    sphere(radius = 2.cm, color = rgb(0xFF0000)) {
                        position = pos
                    }
                }
            }

            // Tap detector overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            session?.performHitTest(offset.x, offset.y)?.let { result ->
                                hitPosition = result.worldPosition
                            }
                        }
                    }
            )
        }
    }
}
```

### Example 9: Multiple Scenes

```kotlin
@Composable
fun MultiSceneExample() {
    var currentScene by remember { mutableStateOf("scene1") }

    Column {
        Row {
            Button(onClick = { currentScene = "scene1" }) { Text("Scene 1") }
            Button(onClick = { currentScene = "scene2" }) { Text("Scene 2") }
        }

        SpatialView {
            when (currentScene) {
                "scene1" -> {
                    cube(size = 10.cm, color = rgb(0xFF0000)) {
                        position = vec3(0f, 1.5f, -1f)
                    }
                }
                "scene2" -> {
                    sphere(radius = 10.cm, color = rgb(0x0000FF)) {
                        position = vec3(0f, 1.5f, -1f)
                    }
                }
            }
        }
    }
}
```

---

## Platform-Specific

### Android (ARCore)

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        // ARCore requires Android 7.0+
        minSdk = 24
    }
}

dependencies {
    implementation(project(":spatial-compose"))
    implementation(project(":spatial-engine-arcore"))
}

// AndroidManifest.xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.ar" android:required="true" />

// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App() // Your spatial composable
        }
    }
}
```

### Web (WebXR)

```kotlin
// build.gradle.kts
dependencies {
    implementation(project(":spatial-compose"))
    implementation(project(":spatial-engine-webxr"))
}

// Configure Kotlin/JS
kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
}
```

```html
<!-- index.html -->
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Spatial App</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body>
    <div id="root"></div>
    <script src="app.js"></script>
</body>
</html>
```

### iOS (Future ARKit)

```kotlin
// build.gradle.kts
kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}

dependencies {
    implementation(project(":spatial-compose"))
    implementation(project(":spatial-engine-arkit")) // TODO: Not yet implemented
}
```

---

## Performance Tips

1. **Avoid creating objects in frame callbacks**:
```kotlin
// ❌ Bad - creates new objects every frame
onFrame = {
    val position = Vec3(0f, 1f, 0f)
}

// ✅ Good - reuse objects
val position = Vec3(0f, 1f, 0f)
onFrame = {
    position.x += 0.01f
}
```

2. **Use remember for expensive operations**:
```kotlin
// ✅ Good - computed once
val scene = remember {
    computeComplexScene()
}
```

3. **Batch updates**:
```kotlin
// ✅ Update multiple properties in one configure block
cube(size = 10.cm) {
    position = vec3(0f, 1f, 0f)
    rotation = eulerDegrees(0, 45, 0)
    scale = vec3(1f, 2f, 1f)
}
```

---

## Best Practices

1. **Use meaningful IDs**:
```kotlin
cube(id = "player-marker") { /* ... */ }
sphere(id = "health-indicator") { /* ... */ }
```

2. **Handle errors gracefully**:
```kotlin
SpatialView(
    onError = { error ->
        when (error) {
            is EngineError.NotSupported -> showARNotSupportedDialog()
            is EngineError.PermissionDenied -> requestCameraPermission()
            else -> showGenericError(error)
        }
    }
)
```

3. **Cleanup resources**:
```kotlin
DisposableEffect(Unit) {
    val resource = allocateResource()
    onDispose {
        resource.release()
    }
}
```

---

## Architecture Layers

The library follows a layered architecture:

```
┌─────────────────────────────────────┐
│     Compose Integration Layer       │  @Composable SpatialView
│     (spatial-compose)               │  Reactive state management
├─────────────────────────────────────┤
│     Kotlin DSL Layer                │  spatialScene { cube { } }
│     (spatial-dsl)                   │  Extension functions, builders
├─────────────────────────────────────┤
│     Scene Graph Layer               │  SpatialScene, SpatialNode
│     (spatial-scene)                 │  SpatialSession, Anchors
├─────────────────────────────────────┤
│     Engine Interface Layer          │  SpatialEngine contract
│     (spatial-engine)                │  SceneData, FrameData
├─────────────────────────────────────┤
│     Platform Renderers              │  ARCore, WebXR, ARKit
│     (spatial-engine-*)              │  expect/actual implementations
└─────────────────────────────────────┘
```

You can use any layer directly:
- **Compose layer**: Most convenient, reactive
- **DSL layer**: Declarative scene building
- **Scene graph layer**: Imperative, fine control
- **Engine layer**: Maximum control, custom rendering

---

## Further Reading

- **DSL Guide**: See `spatial-dsl/DSL_GUIDE.md` for comprehensive DSL documentation
- **Engine Contracts**: See `spatial-engine/CONTRACTS.md` for renderer requirements
- **Architecture**: See `.claude/CLAUDE.md` for project philosophy

---

## License

[Your License Here]
