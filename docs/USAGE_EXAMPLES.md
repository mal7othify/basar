# Spatial-KMP Usage Examples

Complete guide to using the Spatial-KMP library, from basic to advanced patterns.

> **The update model in one sentence:** the `SpatialView` content block builds
> the scene **once**; afterwards you mutate nodes **imperatively**, preferably
> from `onFrame` (the render thread). Keep references to nodes you want to
> change. See the `SpatialView` KDoc for the full contract.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Basic Examples](#basic-examples)
3. [Compose Integration](#compose-integration)
4. [Advanced Patterns](#advanced-patterns)
5. [Platform-Specific](#platform-specific)

---

## Quick Start

### Installation

Artifacts are published under the `com.spatialkit` group (currently to Maven
Local via `./gradlew publishToMavenLocal`; Maven Central publication is planned):

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Pulls in spatial-dsl, spatial-scene, spatial-engine, spatial-core transitively
            implementation("com.spatialkit:spatial-compose:0.1.0-SNAPSHOT")
        }
        // Web and iOS bundle a default engine (WebXR / ARKit).
        // Android has no default: provide an engine (see Platform-Specific below).
        androidMain.dependencies {
            implementation("com.spatialkit:spatial-engine-arcore:0.1.0-SNAPSHOT")
        }
    }
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
            sphere(radius = 10.cm, color = rgb(0xFFC800))

            // Earth (orbiting)
            sphere(radius = 3.cm, color = rgb(0x0088FF)) {
                position = vec3(30.cm, 0f, 0f)
            }
        }
    }
}
```

### Example 3: Plane Anchoring

Detected planes arrive in `onFrame` via `frameData.detectedPlanes`. Anchor
content dynamically by wrapping the session's scene in a `SpatialSceneScope`:

```kotlin
@Composable
fun PlaneAnchoredContent() {
    var session: SpatialSession? = null
    var anchored = false

    SpatialView(
        mode = SessionMode.AR,
        onSessionReady = { session = it },
        onFrame = { frameData ->
            // Anchor to the first horizontal-up plane (floor/table), once
            if (!anchored) {
                frameData.detectedPlanes
                    .firstOrNull { it.type == PlaneType.HORIZONTAL_UP }
                    ?.let { plane ->
                        session?.let { s ->
                            SpatialSceneScope(s.scene).anchorToPlane(planeId = plane.id) {
                                // Place a table top on the floor
                                cube(size = 20.cm, color = rgb(0x8B4513))

                                // Object on the table
                                sphere(radius = 5.cm, color = rgb(0xFF0000)) {
                                    position = vec3(0f, 10.cm, 0f)
                                }
                            }
                        }
                        anchored = true
                    }
            }
        },
    ) {
        // If the plane id is known up front, anchor declaratively instead:
        // anchorToPlane(planeId = "floor") { cube(size = 20.cm) }
    }
}
```

---

## Compose Integration

### Example 4: Animated Values

`animatedSpatialVec3` / `animatedSpatialRotation` animate spatial types with
Compose's animation system (springs, tweens). Apply the value in `onFrame`:

```kotlin
@Composable
fun LiftOnTap() {
    var lifted by remember { mutableStateOf(false) }
    val position by animatedSpatialVec3(
        targetValue = if (lifted) vec3(0f, 2f, -1f) else vec3(0f, 1.5f, -1f),
        animationSpec = tween(1000),
    )
    var cube: CubeNode? = null

    Column {
        Button(onClick = { lifted = !lifted }) { Text("Lift") }

        SpatialView(
            onFrame = { cube?.position = position },
        ) {
            cube = cube(size = 10.cm, color = Color.Red)
        }
    }
}
```

### Example 5: User Interaction

Compose state drives node mutations through `onFrame` — the content block is
not recomposed:

```kotlin
@Composable
fun InteractiveObjects() {
    var selectedColor by remember { mutableStateOf(rgb(0xFF0000)) }
    var cube: CubeNode? = null

    Column {
        // Color picker UI
        Row {
            Button(onClick = { selectedColor = rgb(0xFF0000) }) { Text("Red") }
            Button(onClick = { selectedColor = rgb(0x00FF00) }) { Text("Green") }
            Button(onClick = { selectedColor = rgb(0x0000FF) }) { Text("Blue") }
        }

        // Node state is applied on the render thread; snapshot state can be read there
        SpatialView(
            onFrame = { cube?.color = selectedColor },
        ) {
            cube = cube(size = 15.cm, color = rgb(0xFF0000)) {
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
    var errorMessage by remember { mutableStateOf<String?>(null) }

    SpatialView(
        mode = SessionMode.AR,
        onSessionReady = { session ->
            println("Session capabilities: ${session.capabilities}")
        },
        onFrame = { frameData ->
            // Called every frame, on the render thread
        },
        onError = { error ->
            errorMessage = when (error) {
                EngineError.NotSupported -> "AR not supported"
                EngineError.PermissionDenied -> "Camera permission required"
                is EngineError.InitializationFailed -> "Failed: ${error.reason}"
                is EngineError.SessionFailed -> "Session error: ${error.reason}"
                is EngineError.TrackingFailed -> "Tracking problem: ${error.reason}"
                is EngineError.RuntimeError -> "Runtime error: ${error.reason}"
            }
        }
    ) {
        sphere(radius = 10.cm, color = rgb(0x00FF00)) {
            position = vec3(0f, 1.5f, -1f)
        }
    }

    errorMessage?.let { error ->
        Text(error, color = Color.Red)
    }
}
```

---

## Advanced Patterns

### Example 7: Toggling Content

Build all variants once and toggle `visible` — cheaper than rebuilding the
scene, and consistent with the build-once model:

```kotlin
@Composable
fun SceneSwitcher() {
    var showCube by remember { mutableStateOf(true) }
    var cube: CubeNode? = null
    var sphere: SphereNode? = null

    Column {
        Row {
            Button(onClick = { showCube = true }) { Text("Cube") }
            Button(onClick = { showCube = false }) { Text("Sphere") }
        }

        SpatialView(
            onFrame = {
                cube?.visible = showCube
                sphere?.visible = !showCube
            },
        ) {
            cube = cube(size = 10.cm, color = rgb(0xFF0000)) {
                position = vec3(0f, 1.5f, -1f)
            }
            sphere = sphere(radius = 10.cm, color = rgb(0x0000FF)) {
                position = vec3(0f, 1.5f, -1f)
                visible = false
            }
        }
    }
}
```

### Example 8: Hit Testing

`performHitTest` takes **normalized 0–1 coordinates** — divide tap offsets by
the surface size. Apply the result to nodes from `onFrame`:

```kotlin
@Composable
fun HitTestExample() {
    var session: SpatialSession? = null
    var marker: SphereNode? = null
    var pendingHit: Vec3? = null

    Box(modifier = Modifier.fillMaxSize()) {
        SpatialView(
            onSessionReady = { session = it },
            onFrame = {
                pendingHit?.let { hit ->
                    marker?.position = hit
                    marker?.visible = true
                    pendingHit = null
                }
            },
        ) {
            marker = sphere(radius = 2.cm, color = rgb(0xFF0000)) {
                visible = false
            }
        }

        // Tap detector overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val result = session?.performHitTest(
                            screenX = offset.x / size.width,
                            screenY = offset.y / size.height,
                        )
                        pendingHit = result?.position
                    }
                }
        )
    }
}
```

### Example 9: Per-Frame Animation

Drive continuous animation from `onFrame` using `deltaTime`:

```kotlin
@Composable
fun SpinningCube() {
    var cube: CubeNode? = null
    var angle = 0f

    SpatialView(
        onFrame = { frame ->
            angle += frame.deltaTime * 45f  // 45°/second
            cube?.rotation = eulerDegrees(0f, angle, 0f)
        },
    ) {
        cube = cube(size = 10.cm, color = rgb(0x0000FF)) {
            position = vec3(0f, 1.5f, -1f)
        }
    }
}
```

---

## Platform-Specific

### Android (ARCore)

ARCore needs an Android `Context`, so the library doesn't bundle a default
engine on Android — provide one at the root of your UI:

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        // ARCore requires Android 7.0+
        minSdk = 24
    }
}

// AndroidManifest.xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.ar" android:required="true" />

// MainActivity.kt
import com.spatialkit.compose.LocalSpatialEngine
import com.spatialkit.engine.arcore.createARCoreEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val engine = remember { createARCoreEngine(context) }
            CompositionLocalProvider(LocalSpatialEngine provides engine) {
                App() // Your spatial composable
            }
        }
    }
}
```

### Web (WebXR)

The web surface bundles the WebXR engine by default — just add the dependency:

```kotlin
// build.gradle.kts
kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    sourceSets {
        jsMain.dependencies {
            implementation("com.spatialkit:spatial-engine-webxr:0.1.0-SNAPSHOT")
        }
    }
}
```

WebXR requires **HTTPS** and a WebXR-capable browser (e.g. Chrome on Android).

### iOS (ARKit)

The iOS surface bundles the ARKit engine by default:

```kotlin
// build.gradle.kts
kotlin {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        iosMain.dependencies {
            implementation("com.spatialkit:spatial-engine-arkit:0.1.0-SNAPSHOT")
        }
    }
}
```

ARKit requires a **physical device** (iOS 13+) — it does not run in the
Simulator — and the app must declare `NSCameraUsageDescription` in Info.plist.

---

## Performance Tips

1. **Keep the frame callback allocation-light**:
```kotlin
// ❌ Bad - allocates a list and searches the scene every frame
onFrame = {
    scene.getAllNodes().filterIsInstance<CubeNode>().forEach { ... }
}

// ✅ Good - capture node references once, mutate them directly
var cube: CubeNode? = null
var angle = 0f
onFrame = { frame ->
    angle += frame.deltaTime * 45f
    cube?.rotation = eulerDegrees(0f, angle, 0f)
}
```

2. **Use remember for expensive setup**:
```kotlin
// ✅ Good - computed once
val geometry = remember { generateProceduralGeometry() }
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

4. **Prefer visibility toggles over add/remove** (see Example 7).

---

## Best Practices

1. **Use meaningful IDs** (passed at creation — `id` is immutable):
```kotlin
cube(id = "player-marker") { /* ... */ }
sphere(id = "health-indicator") { /* ... */ }
```

2. **Handle errors gracefully**:
```kotlin
SpatialView(
    onError = { error ->
        when (error) {
            EngineError.NotSupported -> showARNotSupportedDialog()
            EngineError.PermissionDenied -> requestCameraPermission()
            else -> showGenericError(error)
        }
    }
)
```

3. **Mutate nodes from the frame callback** — node-level state (transforms,
   colors) is applied on the render thread; structural scene operations are
   synchronized internally.

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
│     (spatial-engine-*)              │  contract implementations
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
- **Scene Graph Guide**: See `spatial-scene/SCENE_GRAPH_GUIDE.md` for the imperative API
- **Engine Contract**: See `spatial-engine/README.md` for renderer requirements
- **Roadmap**: See `PRODUCTION_PLAN.md` for milestones and current progress

---

## License

[Apache-2.0](../LICENSE)
