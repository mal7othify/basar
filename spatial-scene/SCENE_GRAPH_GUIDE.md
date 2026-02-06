# Spatial Scene Graph Guide

## Overview

The `spatial-scene` module provides a mutable, developer-friendly scene graph on top of the proven `spatial-engine` contract.

**Key Features**:
- 🎯 **Mutable API** - Add/remove/modify nodes easily
- 🌲 **Hierarchy Support** - Parent-child relationships with transform inheritance
- ⚓ **Anchor Management** - High-level API for spatial anchors
- 🔄 **Automatic Sync** - Scene automatically submitted to engine each frame
- 🎨 **Built-in Shapes** - Cube, Sphere, Plane, Cylinder, Capsule

---

## Quick Start

### Basic Scene

```kotlin
import com.spatialkit.scene.*
import com.spatialkit.engine.*
import com.spatialkit.core.types.Color

// Create session
val runtime = SpatialRuntime()
val session = SpatialSession(runtime)

// Add a cube
val cube = CubeNode(size = 0.1f, color = Color.RED)
cube.position = Vec3(0f, 0f, -1f)  // 1 meter in front
session.scene.add(cube)

// Start session
session.start(
    config = EngineConfig(preferredMode = SessionMode.AR),
    onFrame = { frameData ->
        // Animate cube
        cube.rotation = Quaternion.fromEuler(
            x = 0f,
            y = (frameData.timestamp / 1000f) * 45f,  // Rotate 45°/sec
            z = 0f
        )
    },
    onError = { error ->
        println("Error: $error")
    }
)
```

---

## Scene Graph Basics

### Creating Nodes

```kotlin
// Basic shapes
val cube = CubeNode(size = 0.1f, color = Color.RED)
val sphere = SphereNode(radius = 0.05f, color = Color.BLUE)
val plane = PlaneNode(width = 0.5f, height = 0.5f, color = Color.GREEN)
val cylinder = CylinderNode(radius = 0.05f, height = 0.2f)
val capsule = CapsuleNode(radius = 0.05f, height = 0.2f)

// Custom mesh
val custom = CustomMeshNode(
    geometry = GeometryData(
        type = GeometryType.CUSTOM_MESH,
        vertices = floatArrayOf(...),
        normals = floatArrayOf(...),
        indices = intArrayOf(...)
    ),
    color = Color.CYAN
)

// Group (container for other nodes)
val group = GroupNode()
```

### Transform Properties

```kotlin
// Position (local space)
cube.position = Vec3(x = 1f, y = 0f, z = -2f)

// Rotation (quaternion)
cube.rotation = Quaternion.fromEuler(x = 0f, y = 45f, z = 0f)

// Scale
cube.scale = Vec3(2f, 1f, 1f)  // Stretch on X axis

// Visibility
cube.visible = false
```

### Material Properties

```kotlin
cube.materialType = MaterialType.PBR
cube.color = Color.RED
cube.metallic = 0.8f  // More metal-like
cube.roughness = 0.2f  // Smooth
```

---

## Scene Hierarchy

### Parent-Child Relationships

```kotlin
// Create hierarchy
val ship = GroupNode()  // Root
val body = CubeNode(size = 0.2f, color = Color.GRAY)
val engine = CylinderNode(radius = 0.05f, height = 0.1f, color = Color.ORANGE)

ship.addChild(body)
ship.addChild(engine)

// Position children relative to parent
body.position = Vec3(0f, 0f, 0f)
engine.position = Vec3(0f, 0f, -0.15f)  // Behind body

// Move entire ship
ship.position = Vec3(0f, 0f, -1f)  // Children move with it
ship.rotation = Quaternion.fromEuler(0f, 90f, 0f)  // Children rotate with it

// Add to scene
session.scene.add(ship)
```

### World-Space Transforms

```kotlin
// Get world-space transform (accumulated from parents)
val worldPos = engine.worldPosition     // Actual position in world
val worldRot = engine.worldRotation     // Actual rotation in world
val worldScale = engine.worldScale      // Actual scale in world

// Local vs World
println("Engine local pos: ${engine.position}")     // (0, 0, -0.15)
println("Engine world pos: ${engine.worldPosition}") // (0, 0, -1.15)
```

---

## Spatial Anchors

### Creating Anchors

```kotlin
// Anchor to detected plane
session.start(
    onFrame = { frameData ->
        frameData.detectedPlanes.firstOrNull()?.let { plane ->
            val anchor = session.scene.createAnchor(
                type = AnchorType.Plane(
                    planeId = plane.id,
                    offsetFromCenter = Vec3(0f, 0.1f, 0f)  // 10cm above plane
                )
            )

            // Attach cube to anchor
            anchor.onTrack {
                cube.position = anchor.pose!!.position
                cube.rotation = anchor.pose!!.rotation
            }

            anchor.onLost {
                println("Anchor lost tracking")
            }

            anchor.onFailed {
                println("Anchor creation failed")
            }
        }
    }
)
```

### Anchor Types

```kotlin
// World-space anchor (fixed position)
val worldAnchor = scene.createAnchor(
    type = AnchorType.World(
        position = Vec3(0f, 0f, -1f),
        rotation = Quaternion.IDENTITY
    )
)

// Hit test anchor (from touch)
val hitResult = session.performHitTest(screenX, screenY)
hitResult?.let {
    val hitAnchor = scene.createAnchor(
        type = AnchorType.HitTest(
            hitPoint = it.position,
            hitNormal = it.normal
        )
    )
}

// Persistent cloud anchor (future)
val cloudAnchor = scene.createAnchor(
    type = AnchorType.Persistent(cloudId = "my-saved-anchor")
)
```

### Anchor Management

```kotlin
// Check anchor status
if (anchor.isTracking) {
    println("Anchor is tracking")
}

if (anchor.isPending) {
    println("Anchor creation in progress")
}

if (anchor.hasFailed) {
    println("Anchor failed to create")
}

// Remove anchor
session.scene.removeAnchor(anchor)
```

---

## Scene Management

### Adding/Removing Nodes

```kotlin
// Add to scene
session.scene.add(cube)
session.scene.add(sphere)

// Remove from scene
session.scene.remove(cube)

// Remove by ID
session.scene.removeById("node-123")

// Clear entire scene
session.scene.clear()
```

### Finding Nodes

```kotlin
// Find by ID
val node = session.scene.findNodeById("my-cube")

// Get all nodes (root + descendants)
val allNodes = session.scene.getAllNodes()

// Get only root nodes
val roots = session.scene.nodes
```

---

## Advanced Patterns

### Animation

```kotlin
session.start(
    onFrame = { frameData ->
        val time = frameData.timestamp / 1000f

        // Rotate cube
        cube.rotation = Quaternion.fromEuler(
            x = 0f,
            y = time * 45f,  // 45°/sec
            z = 0f
        )

        // Bob sphere up and down
        sphere.position = Vec3(
            x = 0f,
            y = sin(time * 2f) * 0.1f,  // Oscillate
            z = -1f
        )

        // Pulse scale
        val pulse = 1f + sin(time * 3f) * 0.1f
        plane.scale = Vec3(pulse, pulse, pulse)
    }
)
```

### Interactive Objects

```kotlin
// Tap to place object
session.start(
    onFrame = { frameData ->
        // Handle touch input
        frameData.inputSources
            .filterIsInstance<InputSource.Screen>()
            .forEach { screen ->
                screen.touches.forEach { touch ->
                    if (touch.phase == TouchPhase.BEGAN) {
                        handleTap(touch.position)
                    }
                }
            }
    }
)

fun handleTap(position: Vec2) {
    // Perform hit test
    val hit = session.performHitTest(
        screenX = position.x,
        screenY = position.y
    )

    hit?.let {
        // Place object at hit position
        val cube = CubeNode(size = 0.05f, color = Color.random())
        cube.position = it.position
        session.scene.add(cube)
    }
}
```

### Plane Visualization

```kotlin
session.start(
    onFrame = { frameData ->
        // Visualize detected planes
        frameData.detectedPlanes.forEach { plane ->
            // Create/update plane visualization
            var planeNode = session.scene.findNodeById("plane-${plane.id}") as? PlaneNode

            if (planeNode == null) {
                planeNode = PlaneNode(
                    width = plane.extentX,
                    height = plane.extentZ,
                    color = Color.WHITE.copy(a = 0.5f)  // Semi-transparent
                )
                planeNode.id = "plane-${plane.id}"
                planeNode.position = plane.pose.position
                planeNode.rotation = plane.pose.rotation
                session.scene.add(planeNode)
            } else {
                // Update existing
                planeNode.width = plane.extentX
                planeNode.height = plane.extentZ
                planeNode.position = plane.pose.position
                planeNode.rotation = plane.pose.rotation
            }
        }
    }
)
```

### Dynamic Content

```kotlin
// Spawn cubes every second
var lastSpawnTime = 0L

session.start(
    onFrame = { frameData ->
        if (frameData.timestamp - lastSpawnTime > 1000) {
            val cube = CubeNode(
                size = 0.05f,
                color = Color.random()
            )
            cube.position = Vec3(
                x = Random.nextFloat() * 0.5f - 0.25f,
                y = 0f,
                z = -1f
            )
            session.scene.add(cube)

            lastSpawnTime = frameData.timestamp
        }

        // Remove cubes that fall too low
        session.scene.getAllNodes()
            .filterIsInstance<CubeNode>()
            .forEach { cube ->
                cube.position = cube.position.copy(
                    y = cube.position.y - 0.01f  // Gravity
                )

                if (cube.position.y < -2f) {
                    session.scene.remove(cube)
                }
            }
    }
)
```

---

## Performance Tips

### 1. Reuse Nodes Instead of Creating New Ones

```kotlin
✅ GOOD:
val cubes = List(10) { CubeNode() }
session.scene.nodes.forEach { session.scene.add(it) }

// Update existing cubes
onFrame {
    cubes.forEachIndexed { i, cube ->
        cube.position = Vec3(i * 0.1f, 0f, -1f)
    }
}

❌ BAD:
onFrame {
    session.scene.clear()
    repeat(10) { i ->
        val cube = CubeNode()  // Creating 10 cubes per frame!
        cube.position = Vec3(i * 0.1f, 0f, -1f)
        session.scene.add(cube)
    }
}
```

### 2. Use Visibility Instead of Add/Remove

```kotlin
✅ GOOD:
cube.visible = false  // Hide without removing

❌ BAD:
session.scene.remove(cube)  // Remove and recreate later
```

### 3. Batch Hierarchy Changes

```kotlin
✅ GOOD:
val group = GroupNode()
children.forEach { group.addChild(it) }
session.scene.add(group)  // One addition

❌ BAD:
children.forEach { session.scene.add(it) }  // Multiple additions
```

---

## API Reference

### SpatialSession

```kotlin
class SpatialSession(runtime: SpatialRuntime) {
    val scene: SpatialScene
    val isRunning: Boolean
    val state: EngineState?
    val capabilities: EngineCapabilities?

    suspend fun start(
        config: EngineConfig,
        onFrame: (FrameData) -> Unit,
        onError: (EngineError) -> Unit
    )

    fun stop()
    fun dispose()
    fun performHitTest(screenX: Float, screenY: Float): HitTestResult?
    fun performRaycast(origin: Vec3, direction: Vec3): RaycastResult?
}
```

### SpatialScene

```kotlin
class SpatialScene {
    val nodes: List<SpatialNode>
    val allAnchors: List<SpatialAnchor>

    fun add(node: SpatialNode)
    fun remove(node: SpatialNode): Boolean
    fun removeById(id: String): Boolean
    fun clear()
    fun findNodeById(id: String): SpatialNode?
    fun getAllNodes(): List<SpatialNode>

    fun createAnchor(type: AnchorType, id: String = ...): SpatialAnchor
    fun getAnchor(id: String): SpatialAnchor?
    fun removeAnchor(id: String): Boolean
}
```

### SpatialNode

```kotlin
abstract class SpatialNode(id: String) {
    var position: Vec3
    var rotation: Quaternion
    var scale: Vec3
    var visible: Boolean
    var parent: SpatialNode?
    val children: List<SpatialNode>

    val worldPosition: Vec3
    val worldRotation: Quaternion
    val worldScale: Vec3

    fun addChild(child: SpatialNode)
    fun removeChild(child: SpatialNode): Boolean
    fun removeFromParent()
    fun getAllDescendants(): List<SpatialNode>
    fun findNodeById(id: String): SpatialNode?
}
```

### Shape Nodes

```kotlin
class CubeNode(size: Float, color: Color, id: String) : SpatialNode
class SphereNode(radius: Float, color: Color, id: String) : SpatialNode
class PlaneNode(width: Float, height: Float, color: Color, id: String) : SpatialNode
class CylinderNode(radius: Float, height: Float, color: Color, id: String) : SpatialNode
class CapsuleNode(radius: Float, height: Float, color: Color, id: String) : SpatialNode
class GroupNode(id: String) : SpatialNode
class CustomMeshNode(geometry: GeometryData, color: Color, id: String) : SpatialNode
```

### SpatialAnchor

```kotlin
class SpatialAnchor(id: String, type: AnchorType) {
    var status: AnchorStatus
    var pose: Pose?
    val isTracking: Boolean
    val hasFailed: Boolean
    val isPending: Boolean

    fun onTrack(callback: (SpatialAnchor) -> Unit)
    fun onLost(callback: (SpatialAnchor) -> Unit)
    fun onFailed(callback: (SpatialAnchor) -> Unit)
}
```

---

## Best Practices

1. **Use Groups for Organization**
   - Group related objects together
   - Apply transforms to groups instead of individual nodes

2. **Reuse Nodes**
   - Don't create/destroy nodes every frame
   - Toggle visibility instead

3. **Cache Node References**
   - Store references to nodes you'll update frequently
   - Don't search by ID every frame

4. **Handle Anchors Properly**
   - Check anchor status before using pose
   - Register callbacks for state changes

5. **Clean Up**
   - Call `session.dispose()` when done
   - Remove anchors you're not using

---

**Last Updated**: January 15, 2026
**Module Version**: spatial-scene v1.0
