# Spatial-KMP (Basar)

A **Kotlin Multiplatform** library for building AR/VR/Spatial applications with a **Compose-like API**.

Write spatial logic once, deploy to **Android AR**, **iOS AR**, and **Web (WebXR)**.

```kotlin
@Composable
fun App() {
    SpatialView(mode = SessionMode.AR) {
        cube(size = 10.cm, color = Color.Red) {
            position = vec3(0f, 1.5.meters, -1.meters)
        }
    }
}
```

## Status

**`0.1.0-SNAPSHOT` (alpha)** — the API is still stabilizing; expect breaking changes
until `0.3.0`. Not yet published to Maven Central (Maven Local only, see
[Installation](#installation)).

- ✅ Full architecture stack: Compose integration → DSL → scene graph → engine contract → platform renderers
- ✅ Android (ARCore) and Web (WebXR/Three.js) renderers
- ✅ Engine contract with observable state (`StateFlow`) and awaitable session start
- ✅ iOS (ARKit + SceneKit) renderer — newest of the three; custom meshes supported, on-device validation pending
- 🚧 Samples and docs — in progress

Progress is tracked milestone-by-milestone in **[PRODUCTION_PLAN.md](PRODUCTION_PLAN.md)**.

### Repository layout: library vs. playground

The **library** is the `spatial-*` modules — that's what gets published.
`composeApp`, `server`, and `shared` are a development playground / demo app
and are not part of the published artifacts.

## Architecture

```
Compose DSL (@Composable)
        ↓
   Kotlin DSL (spatialScene)
        ↓
  Scene Graph (SpatialScene)
        ↓
Engine Interface (SceneData)
        ↓
Platform Renderers (ARCore/WebXR/ARKit)
```

**Contract-first design** — the engine interface drives the architecture.

## Quick Start

### Installation

Artifacts are published under the `com.spatialkit` group, currently to Maven Local
(`./gradlew publishToMavenLocal`); Maven Central publication is planned.

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Pulls in spatial-dsl, spatial-scene, spatial-engine, spatial-core transitively
            implementation("com.spatialkit:spatial-compose:0.1.0-SNAPSHOT")
        }
        // Web and iOS bundle a default engine (WebXR / ARKit) — nothing else needed.
        // Android has no default: pick an engine and provide it (see below).
        androidMain.dependencies {
            implementation("com.spatialkit:spatial-engine-arcore:0.1.0-SNAPSHOT")
        }
    }
}
```

**Android engine wiring** — ARCore needs an Android `Context`, so the library
doesn't bundle it. Provide the engine once at the root of your UI (or pass it
directly via `SpatialView(engine = …)`):

```kotlin
import com.spatialkit.compose.LocalSpatialEngine
import com.spatialkit.engine.arcore.createARCoreEngine

setContent {
    val context = LocalContext.current
    val engine = remember { createARCoreEngine(context) }
    CompositionLocalProvider(LocalSpatialEngine provides engine) {
        App()
    }
}
```

### Basic Usage

```kotlin
import androidx.compose.runtime.Composable
import com.spatialkit.compose.SpatialView
import com.spatialkit.core.math.meters
import com.spatialkit.core.math.cm
import com.spatialkit.core.types.Color
import com.spatialkit.engine.SessionMode
import com.spatialkit.dsl.*

@Composable
fun MyARApp() {
    SpatialView(mode = SessionMode.AR) {
        // Red cube floating in front
        cube(size = 10.cm, color = rgb(0xFF0000)) {
            position = vec3(0f, 1.5.meters, -1.meters)
        }

        // Green sphere to the right
        sphere(radius = 5.cm, color = rgb(0x00FF00)) {
            position = vec3(0.5f, 1.5.meters, -1.meters)
        }

        // Group objects together
        group {
            position = vec3(-0.5f, 1.meters, -1.meters)
            rotation = eulerDegrees(0, 45, 0)

            cylinder(radius = 3.cm, height = 15.cm, color = rgb(0x0000FF))
        }
    }
}
```

### The update model: build once, mutate imperatively

The `SpatialView` content block is **not** recomposed when state it reads
changes — the scene is built once per content lambda instance, then updated
imperatively. Keep a reference to nodes you want to change and mutate them,
preferably from `onFrame` (the render thread):

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
        cube = cube(size = 10.cm, color = Color.Blue) {
            position = vec3(0f, 1.5f, -1f)
        }
    }
}
```

See the `SpatialView` KDoc for the full contract.

### Textured 3D models (glTF / GLB)

Load an asset once with the suspend `loadGltf`, then place it with the
`model(...)` builder. Base-color textures and PBR factors render consistently
on all three platforms:

```kotlin
@Composable
fun DuckViewer() {
    var duck by remember { mutableStateOf<GltfModel?>(null) }
    LaunchedEffect(Unit) {
        duck = loadGltf(Res.readBytes("files/duck.glb"))
    }

    duck?.let { model ->
        SpatialView(
            onError = { error -> /* AssetError surfaces malformed models */ },
        ) {
            model(model) {
                position = vec3(0f, 0f, -1f)
            }
        }
    }
}
```

`loadGltf` parses off the main thread and caches by content, so placing N
copies of one asset parses it once; instances share geometry and textures.
Malformed assets throw `GltfParseException` (or arrive as
`EngineError.AssetError` via `onError` when parsed inside the content block).

## Examples

### Animated Values

`animatedSpatialVec3` / `animatedSpatialRotation` animate spatial types with
Compose's animation system (springs, tweens); apply the value in `onFrame`:

```kotlin
@Composable
fun LiftOnTap() {
    var lifted by remember { mutableStateOf(false) }
    val position by animatedSpatialVec3(
        targetValue = if (lifted) vec3(0f, 2f, -1f) else vec3(0f, 1.5f, -1f),
        animationSpec = tween(1000),
    )
    var cube: CubeNode? = null

    SpatialView(
        onFrame = { cube?.position = position },
    ) {
        cube = cube(size = 10.cm, color = Color.Red)
    }
}
```

### Plane Anchoring

```kotlin
@Composable
fun PlaneAnchored() {
    SpatialView(
        onSessionReady = { session ->
            // React to detected planes via the session's scene + frame data
        },
        onFrame = { frame ->
            val floor = frame.detectedPlanes.firstOrNull {
                it.type == PlaneType.HORIZONTAL_UP
            }
            // anchor content to floor?.id — see USAGE_EXAMPLES.md
        },
    ) {
        // Anchor to a plane id known up front:
        anchorToPlane(planeId = "floor") {
            cube(size = 20.cm, color = rgb(0x8B4513))
        }
    }
}
```

### User Interaction

```kotlin
@Composable
fun Interactive() {
    var color by remember { mutableStateOf(Color.Red) }
    var cube: CubeNode? = null

    Column {
        Button(onClick = { color = Color.Blue }) {
            Text("Blue")
        }

        SpatialView(
            // Node state is applied on the render thread (see SpatialView KDoc)
            onFrame = { cube?.color = color },
        ) {
            cube = cube(size = 15.cm, color = Color.Red) {
                position = vec3(0f, 1.5f, -1f)
            }
        }
    }
}
```

**See [docs/USAGE_EXAMPLES.md](docs/USAGE_EXAMPLES.md) for more examples.**

## Platform Support

| Platform | Math | Scene Graph | DSL | Compose | Rendering            |
|----------|------|-------------|-----|---------|----------------------|
| Android  | ✅    | ✅           | ✅   | ✅       | ✅ ARCore             |
| Web      | ✅    | ✅           | ✅   | ✅       | ✅ WebXR + Three.js   |
| iOS      | ✅    | ✅           | ✅   | ✅       | ✅ ARKit + SceneKit    |

## Modules

| Module                  | Description                                   |
|-------------------------|-----------------------------------------------|
| `spatial-core`          | Math primitives (Vec3, Quaternion, Transform) |
| `spatial-engine`        | Engine interface + abstractions               |
| `spatial-engine-arcore` | ARCore renderer (Android)                     |
| `spatial-engine-webxr`  | WebXR renderer (Web/Three.js)                 |
| `spatial-engine-arkit`  | ARKit renderer (iOS/SceneKit)                 |
| `spatial-scene`         | Scene graph and session management            |
| `spatial-dsl`           | Kotlin DSL builders and extensions            |
| `spatial-compose`       | Compose Multiplatform integration             |

## Requirements

- **Kotlin**: 2.0+
- **Compose Multiplatform**: 1.6+
- **Android**: API 24+ (for ARCore)
- **iOS**: iOS 13+, physical device (ARKit does not run in the Simulator)
- **Web**: Modern browser with WebXR support

## Building

```bash
# Compile all modules for all platforms
./gradlew build

# Publish the library to Maven Local
./gradlew publishToMavenLocal

# Run tests
./gradlew :spatial-core:jvmTest :spatial-engine:jvmTest \
          :spatial-scene:jvmTest :spatial-dsl:jvmTest :spatial-compose:jvmTest
```

## Design Principles

1. **Compose-like DSL** — Familiar to Compose developers
2. **Write once, run anywhere** — Shared Kotlin code
3. **Progressive complexity** — Simple things easy, complex things possible
4. **Type safety** — Leverage Kotlin's type system
5. **Contract-first** — Engine interface drives design

## Roadmap

Tracked in **[PRODUCTION_PLAN.md](PRODUCTION_PLAN.md)**. Foundation milestones
(M0–M6 — hygiene, packaging, correctness, logging, renderers, API hardening,
CI and API stability) are done. **Current focus: M7 — textured 3D models**
(real glTF/GLB loading with textures, rendered identically on all three
platforms, plus a sample app). After that: gestures (M8), AR realism (M9),
a non-XR preview renderer (M10), Compose UI in space (M11), tracking &
persistence (M12), and real VR support (M13). First Maven Central release is
pending — the release workflow exists and just needs a tag.

## Documentation

| Document                                                 | Description                               |
|----------------------------------------------------------|-------------------------------------------|
| **[PRODUCTION_PLAN.md](PRODUCTION_PLAN.md)**             | Milestone plan and current progress       |
| **[docs/USAGE_EXAMPLES.md](docs/USAGE_EXAMPLES.md)**     | Comprehensive examples (basic → advanced) |
| **[spatial-dsl/DSL_GUIDE.md](spatial-dsl/DSL_GUIDE.md)** | Complete DSL reference                    |
| **[.claude/CLAUDE.md](.claude/CLAUDE.md)**               | Project principles and philosophy         |

## Contributing

Contributions welcome! Please:

1. Read [.claude/CLAUDE.md](.claude/CLAUDE.md) for project principles
2. Check [PRODUCTION_PLAN.md](PRODUCTION_PLAN.md) for what's in flight
3. Run tests before submitting: `./gradlew test`
4. Follow Kotlin coding conventions

## License

[Apache-2.0](LICENSE)

## Credits

Built with:

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [ARCore](https://developers.google.com/ar) (Android)
- [WebXR](https://www.w3.org/TR/webxr/) + [Three.js](https://threejs.org/) (Web)
- [ARKit](https://developer.apple.com/arkit/) + SceneKit (iOS)
