# Spatial DSL Guide

## Overview

The `spatial-dsl` module provides a Compose-like DSL for building spatial scenes. It's pure syntax sugar on top of the `spatial-scene` module, making scene construction more intuitive and readable.

**Architecture**: DSL → Scene Graph → Engine Interface

---

## Quick Start

### Basic Scene

```kotlin
import com.spatialkit.dsl.*
import com.spatialkit.core.types.Color

val scene = spatialScene {
    cube(size = 0.1f, color = Color.Red) {
        position = vec3(0f, 1.5f, -1f)
    }

    sphere(radius = 0.05f, color = Color.Blue) {
        position = vec3(0.2f, 1.5f, -1f)
    }
}
```

### With Convenient Units

```kotlin
val scene = spatialScene {
    cube(size = 10.cm, color = Color.Red) {
        position = vec3(0f, 1.5.meters, -1.meters)
        rotation = eulerDegrees(0, 45, 0)
    }
}
```

---

## Shape Builders

### Cube

```kotlin
cube(
    size = 0.1f,              // Size on all axes (meters)
    color = Color.Red,        // Color
    id = "my-cube"            // Optional unique ID
) {
    position = vec3(0f, 0f, -1f)
    rotation = eulerDegrees(0, 45, 0)
    scale = Vec3(1f, 2f, 1f)  // Stretch on Y axis
    visible = true

    // Material properties
    materialType = MaterialType.PBR
    metallic = 0.8f
    roughness = 0.2f
}
```

### Sphere

```kotlin
sphere(
    radius = 0.05f,
    color = Color.Blue
) {
    position = vec3(0f, 1f, -1f)
}
```

### Plane

```kotlin
plane(
    width = 1f,
    height = 1f,
    color = Color.Green
) {
    position = vec3(0f, 0f, -1f)
    rotation = eulerDegrees(90, 0, 0)  // Horizontal
}
```

### Cylinder

```kotlin
cylinder(
    radius = 0.05f,
    height = 0.2f,
    color = Color.Yellow
) {
    position = vec3(0f, 0f, -1f)
}
```

### Capsule

```kotlin
capsule(
    radius = 0.05f,
    height = 0.2f,
    color = Color.Cyan
) {
    position = vec3(0f, 0f, -1f)
}
```

---

## Hierarchies with Groups

Groups allow you to organize nodes hierarchically. Child transforms are relative to the parent.

```kotlin
spatialScene {
    // Create a robot
    group {
        // Body (center of robot)
        cube(size = 0.1f, color = Color.Blue) {
            position = vec3(0f, 0f, 0f)
        }

        // Head (above body)
        sphere(radius = 0.05f, color = Color.Gray) {
            position = vec3(0f, 0.15f, 0f)
        }

        // Left arm
        cylinder(radius = 0.02f, height = 0.1f, color = Color.Gray) {
            position = vec3(-0.08f, 0.05f, 0f)
            rotation = eulerDegrees(0, 0, 90)
        }

        // Right arm
        cylinder(radius = 0.02f, height = 0.1f, color = Color.Gray) {
            position = vec3(0.08f, 0.05f, 0f)
            rotation = eulerDegrees(0, 0, -90)
        }
    }
}
```

### Nested Groups

```kotlin
spatialScene {
    group {
        // Parent transform
        position = vec3(0f, 0f, -1f)
        rotation = eulerDegrees(0, 45, 0)

        cube { position = vec3(-0.1f, 0f, 0f) }

        // Nested group inherits parent transform
        group {
            position = vec3(0.1f, 0f, 0f)

            sphere { position = vec3(0f, 0.1f, 0f) }
            sphere { position = vec3(0f, -0.1f, 0f) }
        }
    }
}
```

---

## Anchors

Anchors pin content to the real world in AR.

### Anchor to Plane

```kotlin
spatialScene {
    anchorToPlane(
        planeId = "detected-plane-1",
        offset = vec3(0f, 0.1f, 0f)  // 10cm above plane
    ) {
        cube(size = 0.2f, color = Color.Red)

        group {
            sphere { position = vec3(0.1f, 0f, 0f) }
            sphere { position = vec3(-0.1f, 0f, 0f) }
        }
    }
}
```

### Anchor at World Position

```kotlin
spatialScene {
    anchorAtPosition(
        position = vec3(0f, 0f, -1f),
        rotation = Quaternion.IDENTITY
    ) {
        sphere(radius = 0.1f, color = Color.Blue)
    }
}
```

### Dynamic Anchoring

`SpatialSceneScope` can wrap an existing scene, so you can keep building into
it after construction:

```kotlin
val scene = spatialScene {
    // Start with no anchors
}

// Later, in onFrame callback:
frameData.detectedPlanes.firstOrNull()?.let { plane ->
    SpatialSceneScope(scene).anchorToPlane(planeId = plane.id) {
        cube(size = 0.2f, color = Color.Green)
    }
}
```

---

## Unit Conversion Extensions

### Distance

```kotlin
val scene = spatialScene {
    cube(size = 10.cm) {
        position = vec3(0f, 1.5.meters, -100.mm)
    }
}
```

Available units:
- `.meters` (identity, for readability)
- `.cm` (centimeters → meters)
- `.mm` (millimeters → meters)

### Angles

```kotlin
val scene = spatialScene {
    cube {
        rotation = eulerDegrees(0, 45, 0)
        // or
        rotation = Quaternion.fromEuler(0f, 90.degrees, 0f)
    }
}
```

Available units:
- `.degrees` (degrees → radians)
- `.radians` (identity)

Helper functions:
- `eulerDegrees(pitch, yaw, roll)` - Creates quaternion from degrees
- `eulerRadians(pitch, yaw, roll)` - Creates quaternion from radians

---

## Vector Helpers

### vec3()

Convenient vector construction:

```kotlin
position = vec3(0f, 1.5f, -1f)
// Instead of: Vec3(0f, 1.5f, -1f)
```

### position()

Semantic alias for vec3:

```kotlin
position = position(0f, 1.5f, -1f)
```

---

## Color Helpers

### From Hex

```kotlin
val scene = spatialScene {
    cube(color = rgb(0xFF0000))      // Red
    sphere(color = rgba(0xFF0000FF)) // Red with alpha
}
```

---

## Complete Examples

### Example 1: Simple AR Scene

```kotlin
import com.spatialkit.dsl.*
import com.spatialkit.core.types.Color
import com.spatialkit.scene.SpatialSession
import com.spatialkit.engine.SpatialRuntime
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.SessionMode

suspend fun createARScene() {
    val runtime = SpatialRuntime()
    val session = SpatialSession(runtime)

    // Build into the session's scene with the DSL
    // (session.scene is a val — wrap it in a scope rather than replacing it)
    SpatialSceneScope(session.scene).apply {
        cube(size = 10.cm, color = Color.Red) {
            position = vec3(0f, 0f, -1.meters)
        }
    }

    session.start(
        config = EngineConfig(preferredMode = SessionMode.AR)
    )
}
```

### Example 2: Interactive Objects

```kotlin
val scene = spatialScene {
    // Create a row of cubes (id is a constructor parameter, not settable later)
    repeat(5) { i ->
        cube(
            size = 5.cm,
            color = rgb(0xFF0000 + i * 0x003300),
            id = "cube-$i"
        ) {
            position = vec3(i * 0.1f - 0.2f, 1.5f, -1f)
        }
    }
}

// Later, modify specific cubes — from the frame callback (render thread)
scene.findNodeById("cube-2")?.let { node ->
    node.scale = Vec3(2f, 2f, 2f)
}
```

### Example 3: Animated Scene

```kotlin
val scene = spatialScene {
    group(id = "spinning-group") {
        position = vec3(0f, 1.5f, -1f)

        cube(size = 10.cm, color = Color.Red) {
            position = vec3(0.1f, 0f, 0f)
        }

        sphere(radius = 5.cm, color = Color.Blue) {
            position = vec3(-0.1f, 0f, 0f)
        }
    }
}

// In onFrame callback:
session.start(
    onFrame = { frameData ->
        val time = frameData.timestamp / 1000f
        scene.findNodeById("spinning-group")?.let { group ->
            group.rotation = eulerDegrees(0f, time * 45f, 0f)
        }
    }
)
```

### Example 4: Plane Detection + Anchoring

```kotlin
val scene = spatialScene {
    // Scene starts empty
}

session.start(
    onFrame = { frameData ->
        // When first plane detected, anchor content to it
        frameData.detectedPlanes.firstOrNull()?.let { plane ->
            // Add anchor to scene (only once)
            if (scene.allAnchors.isEmpty()) {
                scene.anchorToPlane(
                    planeId = plane.id,
                    offset = vec3(0f, 10.cm, 0f)
                ) {
                    // Content on the plane
                    group {
                        cube(size = 20.cm, color = Color.Red)

                        sphere(radius = 5.cm, color = Color.White) {
                            position = vec3(0f, 15.cm, 0f)
                        }
                    }
                }
            }
        }
    }
)
```

### Example 5: Complex Hierarchy

```kotlin
val scene = spatialScene {
    // Solar system
    group(id = "solar-system") {
        position = vec3(0f, 0f, -2f)

        // Sun (center)
        sphere(radius = 20.cm, color = rgb(0xFFA500)) {
            id = "sun"
        }

        // Earth orbit
        group(id = "earth-orbit") {
            position = vec3(50.cm, 0f, 0f)

            sphere(radius = 10.cm, color = rgb(0x0000FF)) {
                id = "earth"
            }

            // Moon orbit (relative to Earth)
            group(id = "moon-orbit") {
                position = vec3(15.cm, 0f, 0f)

                sphere(radius = 3.cm, color = rgb(0xCCCCCC)) {
                    id = "moon"
                }
            }
        }
    }
}

// Animate orbits
session.start(
    onFrame = { frameData ->
        val time = frameData.timestamp / 1000f

        scene.findNodeById("earth-orbit")?.let { orbit ->
            orbit.rotation = eulerDegrees(0f, time * 30f, 0f)
        }

        scene.findNodeById("moon-orbit")?.let { orbit ->
            orbit.rotation = eulerDegrees(0f, time * 60f, 0f)
        }
    }
)
```

---

## DSL vs Direct Scene Graph API

### DSL (Recommended for Most Cases)

```kotlin
val scene = spatialScene {
    cube(size = 10.cm, color = Color.Red) {
        position = vec3(0f, 0f, -1f)
    }
}
```

**Pros:**
- Concise, readable syntax
- Familiar to Compose developers
- Convenient unit conversions
- Less boilerplate

### Direct Scene Graph API

```kotlin
val scene = SpatialScene()
val cube = CubeNode(size = 0.1f, color = Color.Red)
cube.position = Vec3(0f, 0f, -1f)
scene.add(cube)
```

**Pros:**
- More explicit control
- No magic, easier to debug
- Better for dynamic scene modifications

**Use the DSL for initial scene construction, use direct API for runtime modifications.**

---

## Best Practices

### 1. Use Units for Readability

```kotlin
✅ GOOD:
cube(size = 10.cm) {
    position = vec3(0f, 1.5.meters, -2.meters)
}

❌ BAD:
cube(size = 0.1f) {
    position = vec3(0f, 1.5f, -2f)
}
```

### 2. Use Semantic Names

```kotlin
✅ GOOD:
cube(id = "player-head") { ... }

❌ BAD:
cube { ... }  // Hard to find later
```

### 3. Group Related Objects

```kotlin
✅ GOOD:
group(id = "character") {
    cube(id = "body") { ... }
    sphere(id = "head") { ... }
}

❌ BAD:
cube(id = "body") { ... }
sphere(id = "head") { ... }
// Transforms must be manually kept in sync
```

### 4. Use Anchors for AR

```kotlin
✅ GOOD (AR):
anchorToPlane(planeId = plane.id) {
    cube { ... }
}

❌ BAD (AR):
cube {
    position = vec3(0f, 0f, -1f)  // Drifts in AR
}
```

---

## Performance Tips

1. **Build scene once, modify nodes at runtime**
   ```kotlin
   // Build once
   val scene = spatialScene {
       cube(id = "player") { ... }
   }

   // Modify in onFrame
   scene.findNodeById("player")?.position = newPosition
   ```

2. **Avoid rebuilding scene every frame**
   ```kotlin
   ❌ BAD:
   onFrame {
       scene = spatialScene {  // Rebuilds entire scene!
           cube { ... }
       }
   }
   ```

3. **Use visibility instead of add/remove**
   ```kotlin
   ✅ GOOD:
   node.visible = false

   ❌ BAD:
   scene.remove(node)  // More expensive
   ```

---

## API Reference

### Functions

- `spatialScene(build: SpatialSceneScope.() -> Unit): SpatialScene`
- `vec3(x: Float, y: Float, z: Float): Vec3`
- `position(x: Float, y: Float, z: Float): Vec3`
- `eulerDegrees(pitch: Float, yaw: Float, roll: Float): Quaternion`
- `eulerRadians(pitch: Float, yaw: Float, roll: Float): Quaternion`
- `rgb(hex: Int): Color`
- `rgba(hex: Int): Color`

### Extensions

- `Float.meters`, `Int.meters`
- `Float.cm`, `Int.cm`
- `Float.mm`, `Int.mm`
- `Float.degrees`, `Int.degrees`
- `Float.radians`

### Scopes

- `SpatialSceneScope` - Top-level scene building
- `GroupScope` - Building group hierarchies
- `AnchorScope` - Building anchored content

---

**Module**: `com.spatialkit:spatial-dsl` (see the root README for the current version)
