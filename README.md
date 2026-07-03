# Spatial-KMP (Basar)

A **Kotlin Multiplatform** library for building AR/VR/Spatial applications with a **Compose-like API**.

Write spatial logic once, deploy to **Android AR**, **iOS AR**, **VR headsets**, and **Web**.

```kotlin
@Composable
fun App() {
    SpatialView(mode = SessionMode.AR) {
        cube(size = 10.cm, color = Color.RED) {
            position = vec3(0f, 1.5.meters, -1.meters)
        }
    }
}
```

## ⚠️ Production Readiness Warning

> **IMPORTANT**: This library is **NOT production-ready** for Android.
>
> **Status**: `0.1.0-alpha` - Architecture Complete, Critical Bugs Present
>
> **3 Critical (P0) Issues Found**:
> - ❌ **Memory leak** in Android renderer (will crash apps in minutes)
> - ❌ **OpenGL resource leaks** (GPU memory not freed)
> - ❌ **Thread safety issues** (race conditions in scene mutations)
>
> **Safe for**: Prototypes, demos, learning
> **NOT safe for**: Production apps, app store submissions
>
> **See**: [PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md) for full assessment

---

## Status

✅ **Architecture Complete** (Phase 1-7)
⚠️ **Critical Bugs Present** (3 P0 issues)

- ✅ Full architecture stack implemented
- ✅ Compiles on Android, iOS, and Web
- ⚠️ ARCore engine has critical memory leaks
- ✅ WebXR engine structurally complete (untested)
- ❌ iOS ARKit has syntax errors
- ✅ Comprehensive documentation

**Detailed Status**:

- [PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md) - Honest production assessment
- [PROJECT_STATUS.md](docs/PROJECT_STATUS.md) - Detailed implementation status

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

**Contract-first design** - Engine interface drives architecture.

## Quick Start

### Installation

Artifacts are published under the `com.spatialkit` group (currently to Maven Local
via `./gradlew publishToMavenLocal`; Maven Central publication is planned).

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Pulls in spatial-dsl, spatial-scene, spatial-engine, spatial-core transitively
            implementation("com.spatialkit:spatial-compose:0.1.0-SNAPSHOT")
        }
        // Platform engines (spatial-compose already wires these per platform):
        // androidMain: com.spatialkit:spatial-engine-arcore
        // jsMain:      com.spatialkit:spatial-engine-webxr
        // iosMain:     com.spatialkit:spatial-engine-arkit (work in progress)
    }
}
```

### Basic Usage

```kotlin
import androidx.compose.runtime.Composable
import com.spatialkit.compose.SpatialView
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

## Features

- **Compose Integration**: Use `@Composable` functions with reactive state
- **Kotlin DSL**: Declarative scene building with zero-overhead syntax
- **Type-Safe Math**: Vec3, Quaternion, Transform with operators
- **Platform Agnostic**: Same code runs on Android, iOS, Web
- **Anchoring**: Attach content to detected planes
- **Hit Testing**: Raycast against real-world geometry
- **Progressive API**: Simple tasks easy, complex tasks possible

## Documentation

| Document                                                 | Description                               |
|----------------------------------------------------------|-------------------------------------------|
| **[PROJECT_STATUS.md](docs/PROJECT_STATUS.md)**               | Complete project status and roadmap       |
| **[USAGE_EXAMPLES.md](docs/USAGE_EXAMPLES.md)**               | Comprehensive examples (basic → advanced) |
| **[spatial-dsl/DSL_GUIDE.md](spatial-dsl/DSL_GUIDE.md)** | Complete DSL reference                    |
| **[.claude/CLAUDE.md](.claude/CLAUDE.md)**               | Project principles and philosophy         |

## Examples

### Reactive Animation

```kotlin
@Composable
fun AnimatedCube() {
    var rotation by rememberSpatialState { Quaternion.IDENTITY }

    SpatialView {
        cube(size = 10.cm, color = Color.BLUE) {
            position = vec3(0f, 1.5f, -1f)
            this.rotation = rotation
        }
    }

    LaunchedEffect(Unit) {
        var angle = 0f
        while (true) {
            delay(16) // 60 FPS
            rotation = eulerDegrees(0, angle, 0)
            angle += 1f
        }
    }
}
```

### Plane Anchoring

```kotlin
@Composable
fun PlaneAnchored() {
    var plane by remember { mutableStateOf<TrackedPlane?>(null) }

    SpatialView(
        onFrame = { frameData ->
            if (plane == null) {
                plane = frameData.planes.firstOrNull {
                    it.type == PlaneType.HORIZONTAL
                }
            }
        }
    ) {
        plane?.let {
            anchorToPlane(planeId = it.id) {
                // Place a table on the detected floor
                cube(
                    width = 1.meters,
                    height = 5.cm,
                    depth = 60.cm,
                    color = rgb(0x8B4513)
                )
            }
        }
    }
}
```

### User Interaction

```kotlin
@Composable
fun Interactive() {
    var color by remember { mutableStateOf(Color.RED) }

    Column {
        Button(onClick = { color = Color.BLUE }) {
            Text("Blue")
        }

        SpatialView {
            cube(size = 15.cm, color = color) {
                position = vec3(0f, 1.5f, -1f)
            }
        }
    }
}
```

**See [USAGE_EXAMPLES.md](docs/USAGE_EXAMPLES.md) for 9 complete examples.**

## Platform Support

| Platform | Math | Scene Graph | DSL | Compose | Rendering               | Production Ready |
|----------|------|-------------|-----|---------|-------------------------|------------------|
| Android  | ✅    | ✅           | ✅   | ✅       | ⚠️ ARCore (P0 bugs)     | ❌ **NO**         |
| iOS      | ✅    | ✅           | ✅   | ✅       | ❌ ARKit (syntax errors) | ❌ **NO**         |
| Web      | ✅    | ✅           | ✅   | ✅       | ⚠️ WebXR (untested)     | ⚠️ **MAYBE**     |

**Notes**:

- **Android**: ARCore has critical memory leaks and thread safety issues (P0 blockers)
- **iOS**: ARKit engine has Kotlin/Native syntax errors, needs 1-2 hours fix
- **Web**: WebXR structurally complete but needs integration testing

## Modules

| Module                  | Description                                   | Status                                |
|-------------------------|-----------------------------------------------|---------------------------------------|
| `spatial-core`          | Math primitives (Vec3, Quaternion, Transform) | ✅ Complete + Tested                   |
| `spatial-engine`        | Engine interface + abstractions               | ✅ Complete                            |
| `spatial-engine-arcore` | ARCore renderer (Android)                     | ⚠️ Architecture complete, **P0 bugs** |
| `spatial-engine-webxr`  | WebXR renderer (Web/Three.js)                 | ⚠️ Complete, untested                 |
| `spatial-engine-arkit`  | ARKit renderer (iOS)                          | ❌ Syntax errors (1-2h fix)            |
| `spatial-scene`         | Scene graph and session management            | ✅ Complete + Tested                   |
| `spatial-dsl`           | Kotlin DSL builders and extensions            | ✅ Complete + Tested                   |
| `spatial-compose`       | Compose Multiplatform integration             | ✅ Complete                            |

## Requirements

- **Kotlin**: 2.0+
- **Compose Multiplatform**: 1.6+
- **Android**: API 24+ (for ARCore)
- **iOS**: iOS 11+ (when ARKit engine implemented)
- **Web**: Modern browser with WebXR support

## Building

```bash
# Compile all modules for all platforms
./gradlew build

# Compile specific platforms
./gradlew compileKotlinJs           # Web
./gradlew compileKotlinIosArm64     # iOS
./gradlew compileKotlinAndroid      # Android

# Run tests
./gradlew :spatial-core:test
./gradlew :spatial-scene:test
./gradlew :spatial-dsl:test
```

## Design Principles

1. **Compose-like DSL** - Familiar to Compose developers
2. **Write once, run anywhere** - Shared Kotlin code
3. **Progressive complexity** - Simple things easy, complex things possible
4. **Type safety** - Leverage Kotlin's type system
5. **Contract-first** - Engine interface drives design

**See [.claude/CLAUDE.md](.claude/CLAUDE.md) for detailed philosophy.**

## Roadmap

### ✅ Completed (Phase 1-7)

- Core math primitives
- Engine interface layer
- Scene graph system
- Kotlin DSL layer
- Compose integration
- ARCore renderer (architecture)
- WebXR renderer (architecture)

### 🚨 Critical Path (P0 - Must Fix Before Production)

1. **Fix Android memory leak** (drawGeometry() VBO/VAO caching) - 4-6h
2. **Fix OpenGL resource cleanup** (shader/program deletion) - 2h
3. **Add thread safety** (scene mutation synchronization) - 4-6h
4. **Integration testing** on real Android devices - 2-3 days

### 🎯 Next Steps (P1/P2)

5. Fix iOS ARKit syntax errors (1-2h)
6. Fix Android example Gradle config (2-3h)
7. Complete animation system
8. Add gesture support
9. Performance optimization

**See [PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md) for detailed priority breakdown.**

## Contributing

Contributions welcome! Please:

1. Read [.claude/CLAUDE.md](.claude/CLAUDE.md) for project principles
2. Check [PROJECT_STATUS.md](docs/PROJECT_STATUS.md) for current status
3. Run tests before submitting: `./gradlew test`
4. Follow Kotlin coding conventions

## License

[Your License Here]

## Credits

Built with:

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [ARCore](https://developers.google.com/ar) (Android)
- [WebXR](https://www.w3.org/TR/webxr/) + [Three.js](https://threejs.org/) (Web)
- [ARKit](https://developer.apple.com/arkit/) (iOS - TODO)

---

**Status**: ⚠️ Architecture Complete, Critical Bugs Present
**Production Status**: ❌ NOT READY (3 P0 blockers)
**Version**: 0.1.0-alpha (development only)
**Last Updated**: 2026-03-05
**See**: [PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md) for details
