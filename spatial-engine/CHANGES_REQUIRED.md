# Required Contract Changes - Action Plan

Based on analysis of WebXR and ARCore implementations.

## Phase 1: Critical Fixes (MUST DO NOW)

### 1. Rename Types to Avoid Platform Collisions

```kotlin
// OLD → NEW
Pose → SpatialPose
TrackingState → SpatialTrackingState
LightEstimate → SceneLighting
```

**Files to update**:
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/FrameData.kt`
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/Anchors.kt`
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/Input.kt`

**Reasoning**: ARCore has identically-named types causing compilation errors.

---

### 2. Add Incremental Scene Update Methods

**Add to `SpatialEngine.kt`**:
```kotlin
interface SpatialEngine {
    // Existing
    fun submitScene(scene: SceneData)

    // NEW - Incremental updates
    fun updateNodeTransform(nodeId: String, transform: Transform)
    fun setNodeVisibility(nodeId: String, visible: Boolean)
    fun removeNode(nodeId: String)
    fun addNode(node: SceneNode, parentId: String? = null)
}
```

**Reasoning**: Current `submitScene()` causes O(n²) diff and rebuilds entire scene just to rotate one cube.

---

### 3. Document Coordinate System

**Add to `SpatialEngine.kt` header**:
```kotlin
/**
 * COORDINATE SYSTEM:
 * - Right-handed coordinate system
 * - +Y axis points up
 * - -Z axis is camera forward direction
 * - +X axis points right
 * - Rotations: Quaternions in (x, y, z, w) order
 * - Matrices: Column-major 4x4, OpenGL convention
 *
 * TRANSFORM SPACES:
 * - All node transforms are LOCAL to their parent
 * - World-space transforms computed by accumulating hierarchy
 * - Camera pose is in WORLD space
 * - Anchor poses are in WORLD space
 */
```

**Reasoning**: Both implementations make different assumptions, causing bugs.

---

###4. Fix Transform Semantics

**Update `SceneData.kt`**:
```kotlin
data class Transform(
    val position: Vec3 = Vec3.ZERO,
    val rotation: Quaternion = Quaternion.IDENTITY,
    val scale: Vec3 = Vec3.ONE
) {
    companion object {
        val IDENTITY = Transform()
    }
}

sealed interface SceneNode {
    val id: String
    val localTransform: Transform  // RENAMED: was just "transform"
    val parentId: String?           // NEW: explicit parent reference
    val visible: Boolean
}
```

**Reasoning**: Make local vs world explicit, no more ambiguity.

---

### 5. Improve Anchor Lifecycle

**Update `Anchors.kt`**:
```kotlin
enum class AnchorStatus {
    PENDING,     // NEW: Request submitted, creation in progress
    TRACKING,    // Active and tracking
    PAUSED,      // Temporarily lost
    STOPPED,     // Permanently lost
    FAILED       // NEW: Creation failed
}

// Add to SpatialEngine.kt:
interface SpatialEngine {
    // NEW - Async anchor creation
    fun createAnchor(
        id: String,
        type: AnchorType,
        onResult: (Result<AnchorState>) -> Unit = {}
    )

    fun removeAnchor(id: String)
}
```

**Reasoning**: Anchors are async in both WebXR and ARCore - contract should reflect this.

---

### 6. Standardize maxAnchors

**Update `SpatialEngine.kt`**:
```kotlin
data class EngineCapabilities(
    // ...
    /**
     * Maximum simultaneous spatial anchors:
     * - Positive number: Hard platform limit
     * - 0: Anchors not supported
     * - -1: Unlimited anchors
     */
    val maxAnchors: Int
)
```

**Reasoning**: WebXR used 0, ARCore used -1 for "unlimited" - inconsistent.

---

## Phase 2: High Priority (SHOULD DO SOON)

### 7. Add Texture Support

**Update `SceneData.kt`**:
```kotlin
data class MaterialData(
    val type: MaterialType,
    val baseColor: Vec4 = Vec4(1f, 1f, 1f, 1f),

    // NEW - Texture support
    val albedoTexture: String? = null,
    val metallicRoughnessTexture: String? = null,
    val normalTexture: String? = null,
    val emissiveTexture: String? = null,

    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val emissive: Vec3 = Vec3.ZERO,

    // NEW - Transparency
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    val alphaCutoff: Float = 0.5f,
    val doubleSided: Boolean = false
)

enum class AlphaMode {
    OPAQUE, MASK, BLEND
}
```

---

### 8. Flexible Geometry Attributes

**Update `SceneData.kt`**:
```kotlin
data class GeometryData(
    val type: GeometryType,
    val vertexCount: Int = 0,

    // NEW - Flexible attributes
    val attributes: Map<VertexAttribute, FloatArray> = emptyMap(),
    val indices: IntArray? = null,
    val primitiveType: PrimitiveType = PrimitiveType.TRIANGLES
)

enum class VertexAttribute {
    POSITION,   // Vec3 - required
    NORMAL,     // Vec3
    TANGENT,    // Vec4
    UV0,        // Vec2
    UV1,        // Vec2
    COLOR       // Vec4
}

enum class PrimitiveType {
    TRIANGLES,
    TRIANGLE_STRIP,
    LINES,
    POINTS
}
```

**Deprecate**: Old `vertices`, `normals` fields

---

### 9. Mobile Gesture Support

**Update `Input.kt`**:
```kotlin
data class Screen(
    override val id: String,
    val touches: List<TouchPoint>,
    val gestures: List<Gesture> = emptyList()  // NEW
) : InputSource

data class TouchPoint(
    val id: Int,
    val position: Vec2,  // Normalized 0-1
    val phase: TouchPhase
)

enum class TouchPhase {
    BEGAN, MOVED, ENDED, CANCELLED
}

sealed interface Gesture {
    data class Tap(val position: Vec2) : Gesture
    data class Pan(val translation: Vec2) : Gesture
    data class Pinch(val scale: Float) : Gesture
    data class Rotation(val angle: Float) : Gesture
}
```

---

## Phase 3: Medium Priority (NICE TO HAVE)

### 10. Advanced AR Features

**Update `FrameData.kt`**:
```kotlin
data class FrameData(
    // ... existing fields ...

    // NEW - Advanced features
    val trackedImages: List<TrackedImage> = emptyList(),
    val depthMap: DepthData? = null,
    val environmentProbe: EnvironmentProbe? = null
)

data class TrackedImage(
    val id: String,
    val name: String,
    val pose: SpatialPose,
    val trackingState: SpatialTrackingState,
    val physicalSize: Vec2  // Meters
)
```

**Update `EngineCapabilities`**:
```kotlin
data class EngineCapabilities(
    // ... existing ...
    val supportsImageTracking: Boolean,
    val supportsDepthMaps: Boolean,
    val supportsEnvironmentProbes: Boolean
)
```

---

### 11. Rendering Quality Config

**Update `SpatialEngine.kt`**:
```kotlin
data class EngineConfig(
    // ... existing ...

    // NEW
    val renderingQuality: RenderingQuality = RenderingQuality.BALANCED,
    val enableAntiAliasing: Boolean = true,
    val enableShadows: Boolean = true
)

enum class RenderingQuality {
    LOW, BALANCED, HIGH
}
```

---

## Implementation Order

1. ✅ **Rename types** (Pose → SpatialPose, etc.) - Unblocks ARCore compilation
2. ✅ **Document coordinate system** - Prevents rendering bugs
3. ✅ **Fix transform semantics** - Clarifies scene hierarchy
4. ✅ **Add incremental updates** - Fixes performance
5. ✅ **Improve anchor lifecycle** - Matches platform reality
6. ✅ **Standardize maxAnchors** - Consistency
7. ⚠️ **Texture support** - Enables realistic rendering
8. ⚠️ **Flexible geometry** - Enables complex models
9. ⚠️ **Gestures** - Enables mobile AR interaction
10. ⏭️ **Advanced features** - Future enhancement

---

## Breaking Changes

| Change | Breaking? | Migration Path |
|--------|-----------|----------------|
| Rename types | ✅ YES | Find-replace: `Pose` → `SpatialPose`, etc. |
| Add incremental methods | ❌ NO | New methods, old `submitScene()` still works |
| Transform semantics | ⚠️ MAYBE | `transform` → `localTransform`, add `parentId` |
| Anchor lifecycle | ⚠️ MAYBE | Old anchors via `submitScene()` deprecated |
| Texture support | ❌ NO | New optional fields |
| Geometry attributes | ⚠️ MAYBE | Old `vertices` deprecated, use `attributes` |
| Gestures | ❌ NO | New optional field |

---

## Estimated Impact

**Files to update**:
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/SpatialEngine.kt`
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/FrameData.kt`
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/SceneData.kt`
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/Anchors.kt`
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/Input.kt`
- `spatial-engine/src/commonMain/kotlin/com/spatialkit/engine/Math.kt`

**Implementations to update**:
- `spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt`
- `spatial-engine-arcore/src/androidMain/kotlin/com/spatialkit/engine/arcore/ARCoreEngine.kt`
- Test applications

**Estimated effort**:
- Phase 1: 2-3 hours
- Phase 2: 3-4 hours
- Phase 3: 2-3 hours
- **Total**: 7-10 hours

---

## Decision Required

**Question**: Should we proceed with:
1. ✅ **Phase 1 only** (critical fixes, minimal breaking changes)
2. ✅ **Phase 1 + 2** (critical + high priority, more complete)
3. ✅ **All phases** (complete overhaul, most breaking changes)

**Recommendation**: **Phase 1 + 2**
- Fixes critical issues (type collisions, performance)
- Adds essential features (textures, gestures)
- Reasonable scope (~6 hours)
- Leaves advanced features for later

---

**Ready to proceed?** Please confirm which phases to implement.
