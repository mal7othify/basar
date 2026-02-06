# Spatial Engine - The Core AR/VR Contract

This module defines **THE CONTRACT** between the Spatial-KMP library and platform-specific renderers.

## Architecture Principle

**ENGINE FIRST.** The renderer interface defines the contract. Scene graph and DSL are shaped by what renderers actually need.

## Module Purpose

`spatial-engine` contains **pure interfaces and data structures** with zero platform-specific code. It defines:

1. What an AR/VR engine must provide (`SpatialEngine` interface)
2. What data the engine gives us each frame (`FrameData`)
3. What data we give the engine to render (`SceneData`)
4. How spatial anchoring works (`Anchors`)
5. How input is unified across platforms (`Input`)

## The Contract Files

### Core Engine Interface
- **SpatialEngine.kt** - Main engine interface with lifecycle and rendering methods
- **EngineError.kt** - Error types that can occur

### Frame Loop
- **FrameData.kt** - What the engine provides each frame (camera, tracking, detected environment)
- **SceneData.kt** - What we submit to the engine (nodes, geometry, materials)

### Spatial Features
- **Anchors.kt** - Spatial anchor system (the key AR abstraction)
- **HitTest.kt** - Hit testing and raycasting results

### Input System
- **Input.kt** - Unified input abstraction (controllers, hands, touch, gaze)

### Foundation
- **Math.kt** - Minimal math types (Vec3, Vec4, Quaternion, Transform)

## Engine Lifecycle

```kotlin
// 1. Create platform-specific engine
val engine: SpatialEngine = createPlatformEngine()

// 2. Initialize
engine.initialize(
    EngineConfig(
        preferredMode = SessionMode.AR,
        enablePlaneDetection = true
    )
)

// 3. Start session
engine.startSession(SessionMode.AR)

// 4. Register frame callback
engine.onFrame { frameData ->
    // Update your app based on tracking, input, detected planes
    updateScene(frameData)

    // Submit updated scene
    engine.submitScene(buildScene())
}

// 5. Stop when done
engine.stopSession()

// 6. Clean up
engine.dispose()
```

## Platform Implementations

This contract will be implemented by:

- `spatial-engine-webxr` - WebXR implementation (Kotlin/JS + Three.js)
- `spatial-engine-arcore` - ARCore implementation (Android)
- `spatial-engine-arkit` - ARKit implementation (iOS)

Each implementation converts between native platform APIs and this contract.

## Design Principles

1. **Minimal but Complete** - Only what's needed for AR/VR, nothing more
2. **Platform Agnostic** - No platform-specific types leak through
3. **Type Safe** - Sealed interfaces for exhaustive when expressions
4. **Immutable** - All data classes are immutable by default
5. **Extensible** - Can add to the contract, but changing it breaks implementations

## Key Abstractions

### Session Modes
- **AR** - Augmented Reality (real world + virtual overlay)
- **VR** - Virtual Reality (fully immersive)
- **Passthrough** - VR with camera passthrough (mixed reality)

### Geometry Types
- Primitives: Cube, Sphere, Plane, Cylinder, Capsule
- Custom meshes with vertices/indices

### Material Types
- **Unlit** - No lighting calculations
- **Lit** - Basic diffuse/specular
- **PBR** - Full physically-based rendering

### Anchor Types
- **Plane** - Attach to detected surface (most stable)
- **HitTest** - Attach to raycast hit point
- **World** - Free-floating world position (may drift)
- **Persistent** - Cloud anchors (cross-device/session)

### Input Sources
- **Controller** - VR controllers with buttons/axes
- **Hand** - Hand tracking with joint poses
- **Screen** - Touch input for AR on phones
- **Gaze** - Head/eye tracking for devices without controllers

## Important Notes

⚠️ **This is critical infrastructure** - changes here affect all implementations and all apps using the library.

✅ **You can add** new optional fields to data classes, new enum values, new sealed interface subtypes

❌ **Don't remove or rename** existing fields, methods, or types without a major version bump

## Next Steps

After this contract is stable:
1. Build first implementation (`spatial-engine-webxr`)
2. Validate contract works in practice
3. Iterate if needed (before 1.0)
4. Build scene graph on top of validated contract
5. Build Compose DSL as final layer