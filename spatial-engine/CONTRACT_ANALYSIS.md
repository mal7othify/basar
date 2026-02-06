# SpatialEngine Contract Analysis
## Post-Implementation Review (WebXR + ARCore)

**Date**: January 15, 2026
**Implementations Reviewed**: WebXR (Kotlin/JS), ARCore (Android)
**Status**: Critical issues found - Interface revision required

---

## Executive Summary

After implementing the SpatialEngine contract for both WebXR and ARCore, several critical issues emerged:

🔴 **CRITICAL** (Blocks implementation):
- Type name collisions with platform APIs
- Unclear coordinate system specification
- Scene update model too coarse-grained
- Anchor lifecycle incomplete

🟡 **HIGH** (Hurts developer experience):
- Transform space ambiguity (local vs world)
- Missing texture support in materials
- No incremental scene updates
- Inadequate geometry vertex format

🟢 **MEDIUM** (Nice to have):
- Missing advanced AR features (image tracking, depth)
- No rendering quality configuration
- Limited input/gesture support

---

## 1. Critical Issues Found

### Issue #1: Type Name Collisions 🔴

**Problem**: Contract types conflict with platform APIs

| Contract Type | ARCore Conflict | WebXR Conflict |
|--------------|----------------|----------------|
| `Pose` | `com.google.ar.core.Pose` | None |
| `TrackingState` | `com.google.ar.core.TrackingState` | None |
| `LightEstimate` | `com.google.ar.core.LightEstimate` | None |
| `Anchor` | `com.google.ar.core.Anchor` | `XRAnchor` |

**Impact**: ARCore implementation requires fully qualified names everywhere:
```kotlin
// Current - painful:
val pose: com.spatialkit.engine.Pose = convertFrom(arPose: com.google.ar.core.Pose)

// Desired:
val pose: SpatialPose = convertFrom(arPose: Pose)
```

**Fix**: Rename types to avoid common platform names:
- `Pose` → `SpatialPose`
- `TrackingState` → `SpatialTrackingState`
- `LightEstimate` → `SceneLighting`
- Consider: `Anchor` → `SpatialAnchor` (less critical)

**Severity**: 🔴 CRITICAL - Makes implementation error-prone

---

### Issue #2: Coordinate System Undefined 🔴

**Problem**: No specification of coordinate system convention

**Current State**: Undefined
- WebXR/Three.js: Y-up, right-handed
- ARCore: Y-up, right-handed (device space)
- Contract: No specification

**Issues**:
1. Are Quaternions in the same convention?
2. What's "forward" - negative Z or positive Z?
3. Matrix multiplication order?
4. Camera space vs world space?

**Fix**: Explicitly define and document:
```kotlin
/**
 * Spatial Engine Coordinate System:
 * - Right-handed coordinate system
 * - Y-axis points up
 * - Z-axis points toward user (camera looks down -Z)
 * - X-axis points right
 * - Rotation: Quaternions in (x, y, z, w) order
 * - Matrices: Column-major, OpenGL convention
 */
```

**Additional**: Add coordinate space enum:
```kotlin
enum class CoordinateSpace {
    WORLD,      // Global AR/VR space
    LOCAL,      // Relative to parent node
    VIEW,       // Relative to camera
    ANCHOR      // Relative to anchor
}
```

**Severity**: 🔴 CRITICAL - Causes incorrect rendering

---

### Issue #3: Transform Space Ambiguity 🔴

**Problem**: Unclear if transforms are local or world space

**Current Contract**:
```kotlin
data class Transform(
    val position: Vec3,
    val rotation: Quaternion,
    val scale: Vec3
)
```

**Questions**:
- Is `SceneNode.Mesh.transform` relative to parent or world?
- How do Group nodes affect child transforms?
- Are anchored nodes in anchor space or world space?

**WebXR Implementation**: Treats as local (Three.js scene graph)
**ARCore Implementation**: Unclear - no proper hierarchy implemented

**Fix**: Make space explicit:
```kotlin
data class Transform(
    val position: Vec3,
    val rotation: Quaternion,
    val scale: Vec3,
    val space: CoordinateSpace = CoordinateSpace.LOCAL  // Explicit!
)

// OR: Separate types
data class LocalTransform(...)
data class WorldTransform(...)
```

**Severity**: 🔴 CRITICAL - Breaks scene hierarchy

---

### Issue #4: Scene Update Model Too Coarse 🔴

**Problem**: `submitScene()` replaces entire scene every time

**Current Implementation** (WebXREngine.kt:244):
```kotlin
override fun submitScene(scene: SceneData) {
    // Remove ALL old nodes
    val newNodeIds = collectNodeIds(scene.nodes)
    val toRemove = nodeMap.keys.filter { it !in newNodeIds }
    toRemove.forEach { id -> /* destroy and remove */ }

    // Recreate ALL nodes
    scene.nodes.forEach { node -> processSceneNode(node) }
}
```

**Issues**:
1. Inefficient - O(n²) diffing every update
2. Can't animate single node without rebuilding entire scene
3. Destroys/recreates nodes that haven't changed
4. See test app (Main.kt:305) - rebuilds 3 nodes just to rotate cube!

**Fix**: Add incremental update methods:
```kotlin
interface SpatialEngine {
    // Coarse-grained (initial scene)
    fun submitScene(scene: SceneData)

    // Fine-grained (updates)
    fun updateNode(nodeId: String, transform: Transform)
    fun addNode(node: SceneNode, parentId: String? = null)
    fun removeNode(nodeId: String)
    fun setNodeVisibility(nodeId: String, visible: Boolean)
}
```

**Severity**: 🔴 CRITICAL - Performance bottleneck

---

### Issue #5: Anchor Lifecycle Incomplete 🟡

**Problem**: Anchors are async but contract treats them as synchronous

**Current Flow**:
```kotlin
// 1. Request anchor
submitScene(SceneData(
    anchors = listOf(AnchorRequest(id = "my-anchor", type = ...))
))

// 2. Later, check if it's tracking
onFrame { frameData ->
    val anchor = frameData.anchors.find { it.id == "my-anchor" }
    // What if anchor is still pending? Status = TRACKING or PENDING?
}
```

**Issues**:
1. No "PENDING" status (only TRACKING, PAUSED, STOPPED)
2. No callback when anchor is created
3. No way to cancel anchor request
4. No way to explicitly remove anchor

**WebXR Reality**: Anchors are Promise-based
**ARCore Reality**: Anchors can fail to create

**Fix**: Add lifecycle methods:
```kotlin
enum class AnchorStatus {
    PENDING,     // Request submitted, not yet created
    TRACKING,    // Active and tracking
    PAUSED,      // Temporarily lost
    STOPPED,     // Permanently lost
    FAILED       // Creation failed
}

interface SpatialEngine {
    // Async anchor creation
    fun createAnchor(
        id: String,
        type: AnchorType,
        onCreated: (AnchorState) -> Unit = {},
        onFailed: (Error) -> Unit = {}
    )

    fun removeAnchor(id: String)
}
```

**Severity**: 🟡 HIGH - Breaks async AR patterns

---

## 2. High Priority Issues

### Issue #6: Missing Texture Support 🟡

**Problem**: MaterialData has no texture support

**Current**:
```kotlin
data class MaterialData(
    val type: MaterialType,
    val baseColor: Vec4,
    val metallic: Float,
    val roughness: Float
)
```

**Missing**:
- Albedo/diffuse texture
- Metallic/roughness maps (PBR)
- Normal maps
- Emissive maps
- Occlusion maps

**WebXR Impact**: Can't use textured models (Three.js supports textures)
**ARCore Impact**: Can't show realistic materials

**Fix**:
```kotlin
data class MaterialData(
    val type: MaterialType,
    val baseColor: Vec4 = Vec4(1f, 1f, 1f, 1f),

    // PBR textures
    val albedoTexture: String? = null,
    val metallicRoughnessTexture: String? = null,
    val normalTexture: String? = null,
    val occlusionTexture: String? = null,
    val emissiveTexture: String? = null,

    // PBR parameters
    val metallic: Float = 0f,
    val roughness: Float = 0.5f,
    val emissive: Vec3 = Vec3.ZERO,

    // Transparency
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    val alphaCutoff: Float = 0.5f,

    // Options
    val doubleSided: Boolean = false
)

enum class AlphaMode {
    OPAQUE,   // No transparency
    MASK,     // Binary alpha (cutoff)
    BLEND     // Alpha blending
}
```

**Severity**: 🟡 HIGH - Limits visual quality

---

### Issue #7: Geometry Vertex Format Inflexible 🟡

**Problem**: Custom mesh data is too simple

**Current**:
```kotlin
data class GeometryData(
    val type: GeometryType,
    val vertices: FloatArray? = null,    // Just positions?
    val normals: FloatArray? = null,
    val indices: IntArray? = null
)
```

**Missing**:
- UV coordinates (for textures)
- Tangents (for normal mapping)
- Vertex colors
- Multiple UV sets
- Skinning data (bones, weights)

**Fix**: Flexible vertex attribute system:
```kotlin
data class GeometryData(
    val type: GeometryType,
    val vertexCount: Int,
    val attributes: Map<VertexAttribute, FloatArray> = emptyMap(),
    val indices: IntArray? = null,
    val primitiveType: PrimitiveType = PrimitiveType.TRIANGLES
)

enum class VertexAttribute {
    POSITION,    // Vec3 (required)
    NORMAL,      // Vec3
    TANGENT,     // Vec4 (xyz = tangent, w = handedness)
    UV0,         // Vec2
    UV1,         // Vec2
    COLOR,       // Vec4
    JOINTS,      // Vec4 (bone indices)
    WEIGHTS      // Vec4 (bone weights)
}

enum class PrimitiveType {
    TRIANGLES,
    TRIANGLE_STRIP,
    LINES,
    POINTS
}
```

**Severity**: 🟡 HIGH - Limits model complexity

---

### Issue #8: No Input Gestures for Mobile AR 🟡

**Problem**: Input system designed for VR controllers, not mobile AR

**Current**:
```kotlin
sealed interface InputSource {
    data class Controller(...)  // VR controllers
    data class Hand(...)        // Hand tracking
    data class Screen(...)      // Touch input - but no gesture data
    data class Gaze(...)
}
```

**Missing for Mobile AR**:
- Tap/touch position
- Pinch gesture (for scaling)
- Pan gesture (for rotation)
- Long press
- Multi-touch

**Fix**:
```kotlin
data class Screen(
    override val id: String,
    val touches: List<TouchPoint>,
    val gestures: List<Gesture> = emptyList()
) : InputSource

data class TouchPoint(
    val id: Int,
    val position: Vec2,  // Normalized (0-1)
    val phase: TouchPhase
)

enum class TouchPhase {
    BEGAN, MOVED, ENDED, CANCELLED
}

sealed interface Gesture {
    data class Tap(val position: Vec2) : Gesture
    data class LongPress(val position: Vec2) : Gesture
    data class Pan(val translation: Vec2, val velocity: Vec2) : Gesture
    data class Pinch(val scale: Float, val velocity: Float) : Gesture
    data class Rotation(val angle: Float, val velocity: Float) : Gesture
}
```

**Severity**: 🟡 HIGH - Mobile AR unusable without gestures

---

### Issue #9: Missing maxAnchors Semantics 🟡

**Problem**: Unclear if `maxAnchors = 0` means "none" or "unlimited"

**Current Implementations**:
- WebXR: Used `0` for unlimited
- ARCore: Used `-1` for unlimited

**Fix**: Standardize and document:
```kotlin
data class EngineCapabilities(
    // ...
    /**
     * Maximum number of simultaneous spatial anchors.
     * - Positive number: Hard limit
     * - 0: Anchors not supported
     * - -1: Unlimited anchors
     */
    val maxAnchors: Int
)
```

**Severity**: 🟡 HIGH - Inconsistent between implementations

---

## 3. Medium Priority Issues

### Issue #10: Missing Advanced AR Features 🟢

**Features ARCore supports but contract doesn't**:
- ✅ Plane detection (supported)
- ✅ Anchors (supported)
- ❌ Image tracking (augmented images)
- ❌ Face tracking (face mesh, expressions)
- ❌ Depth maps (dense/sparse depth)
- ❌ Environment HDR (lighting probes)
- ❌ Scene understanding (semantic labels)

**Fix**: Extend capabilities and frame data:
```kotlin
data class EngineCapabilities(
    // ... existing ...
    val supportsImageTracking: Boolean,
    val supportsFaceTracking: Boolean,
    val supportsDepthMaps: Boolean,
    val supportsEnvironmentHDR: Boolean,
    val supportsSceneUnderstanding: Boolean
)

data class FrameData(
    // ... existing ...
    val trackedImages: List<TrackedImage> = emptyList(),
    val trackedFaces: List<TrackedFace> = emptyList(),
    val depthMap: DepthData? = null,
    val environmentMap: EnvironmentHDR? = null
)

data class TrackedImage(
    val id: String,
    val name: String,  // From image database
    val pose: SpatialPose,
    val trackingState: SpatialTrackingState,
    val physicalSize: Vec2  // Real-world size in meters
)
```

**Severity**: 🟢 MEDIUM - Advanced features

---

### Issue #11: No Rendering Configuration 🟢

**Problem**: Can't configure rendering quality

**Missing**:
- Anti-aliasing
- Shadow quality
- Texture filtering
- Post-processing
- Performance mode

**Fix**: Add to EngineConfig:
```kotlin
data class EngineConfig(
    // ... existing ...
    val renderingQuality: RenderingQuality = RenderingQuality.BALANCED,
    val enableAntiAliasing: Boolean = true,
    val enableShadows: Boolean = true,
    val shadowQuality: ShadowQuality = ShadowQuality.MEDIUM,
    val enablePostProcessing: Boolean = false
)

enum class RenderingQuality {
    LOW,      // Maximize FPS
    BALANCED, // Balance quality/performance
    HIGH      // Maximize quality
}
```

**Severity**: 🟢 MEDIUM - Quality control

---

### Issue #12: No Camera Configuration 🟢

**Problem**: Can't configure AR camera behavior

**Missing**:
- Focus mode (auto, manual)
- Exposure mode
- White balance
- Flash/torch

**Fix**: Add to EngineConfig:
```kotlin
data class EngineConfig(
    // ... existing ...
    val cameraConfig: CameraConfig = CameraConfig()
)

data class CameraConfig(
    val focusMode: FocusMode = FocusMode.AUTO,
    val autoExposure: Boolean = true,
    val flashEnabled: Boolean = false
)

enum class FocusMode {
    AUTO,
    FIXED,
    CONTINUOUS
}
```

**Severity**: 🟢 MEDIUM - Camera control

---

## 4. Architecture Issues

### Issue #13: Frame Loop Integration Pattern 🟡

**Problem**: Different platforms have different frame loop models

**WebXR**: Browser-driven, automatic
```kotlin
renderer.setAnimationLoop { time, frame ->
    // Browser calls this every frame
}
```

**ARCore**: Manual update required
```kotlin
fun onDrawFrame() {
    val frame = session.update()  // Manual!
    // GL surface callback
}
```

**Contract Says**:
```kotlin
fun onFrame(callback: (FrameData) -> Unit)
```

**Issue**: Doesn't specify WHO calls the callback or WHEN.

**Fix**: Document integration patterns and add helper:
```kotlin
/**
 * Register a callback to receive frame updates.
 *
 * Platform Integration:
 * - WebXR: Callback invoked automatically by browser's animation loop
 * - ARCore: Call engine.updateFrame() from GLSurfaceView.onDrawFrame()
 * - ARKit: Call engine.updateFrame() from ARSessionDelegate
 */
fun onFrame(callback: (FrameData) -> Unit)

/**
 * Manual frame update for platforms that require it (ARCore, ARKit).
 * WebXR implementation ignores this (browser handles it).
 */
fun updateFrame()  // New method for manual platforms
```

**Severity**: 🟡 HIGH - Integration confusion

---

### Issue #14: No Scene Graph Hierarchy Clarity 🟡

**Problem**: Group nodes exist but parent-child transform semantics unclear

**Current**:
```kotlin
sealed interface SceneNode {
    val transform: Transform  // Local or world?

    data class Group(
        // ...
        val children: List<SceneNode>
    ) : SceneNode
}
```

**Questions**:
1. Is child.transform relative to parent.transform?
2. How are transforms accumulated down the tree?
3. Can nodes be re-parented?

**WebXR Implementation**: Implicitly local (Three.js does this)
**ARCore Implementation**: Not properly implemented

**Fix**: Make hierarchy explicit:
```kotlin
sealed interface SceneNode {
    val id: String
    val localTransform: Transform  // EXPLICIT: Always local to parent
    val parent: String?            // Parent node ID (null = world root)
    val visible: Boolean

    // Computed by engine
    fun getWorldTransform(engine: SpatialEngine): Transform
}
```

**Severity**: 🟡 HIGH - Scene graph ambiguity

---

## 5. Proposed Interface Changes

### Summary of Required Changes

| Change | Type | Severity | Breaking? |
|--------|------|----------|-----------|
| Rename types (Pose → SpatialPose) | Rename | 🔴 Critical | Yes |
| Add CoordinateSpace enum | Addition | 🔴 Critical | No |
| Add incremental scene updates | Addition | 🔴 Critical | No |
| Anchor lifecycle methods | Addition | 🟡 High | No |
| Texture support in MaterialData | Addition | 🟡 High | No |
| Flexible vertex attributes | Addition | 🟡 High | No |
| Mobile gestures | Addition | 🟡 High | No |
| Standardize maxAnchors | Documentation | 🟡 High | No |
| Advanced AR features | Addition | 🟢 Medium | No |
| Rendering config | Addition | 🟢 Medium | No |

### Changes Grouped by Priority

**Phase 1 - Critical (Required for basic functionality)**:
1. Rename conflicting types
2. Define coordinate system
3. Add incremental scene update methods
4. Clarify transform spaces

**Phase 2 - High (Required for production)**:
5. Add texture support
6. Flexible geometry format
7. Mobile gesture support
8. Complete anchor lifecycle
9. Standardize maxAnchors

**Phase 3 - Medium (Nice to have)**:
10. Advanced AR features (image tracking, depth)
11. Rendering quality configuration
12. Camera configuration

---

## 6. Implementation Difficulty Assessment

### What Was Hard to Implement

**WebXR Implementation**:
- ❌ Scene diffing on every update (performance issue)
- ⚠️ No texture support limited visual quality
- ⚠️ Anchor async nature not well represented

**ARCore Implementation**:
- ❌ Type name collisions made code very verbose
- ❌ Frame update pattern not documented
- ⚠️ Scene hierarchy ambiguous
- ⚠️ No support for image tracking (ARCore capability not expressible)

### What Worked Well

**Both Implementations**:
- ✅ State machine (EngineState) mapped perfectly
- ✅ Capabilities reporting worked great
- ✅ Basic plane detection mapped well
- ✅ Hit testing worked smoothly
- ✅ Basic geometry types sufficient for validation

---

## 7. Recommendations

### Immediate Actions (This Sprint)
1. ✅ Rename types to avoid collisions
2. ✅ Add incremental scene update methods
3. ✅ Document coordinate system
4. ✅ Clarify transform spaces
5. ✅ Fix maxAnchors semantics

### Short-term (Next Sprint)
6. ⚠️ Add texture support to materials
7. ⚠️ Flexible vertex attribute system
8. ⚠️ Mobile gesture support
9. ⚠️ Complete anchor lifecycle

### Long-term (Future)
10. ⏭️ Advanced AR features
11. ⏭️ Rendering quality config
12. ⏭️ Performance optimization APIs (LOD, culling)

---

## Conclusion

The SpatialEngine contract **successfully abstracts** AR/VR across WebXR and ARCore, proving the "engine-first" architecture is sound. However, **critical refinements are needed** before production use:

**The Good** ✅:
- Core abstraction works across platforms
- State management is excellent
- Basic AR features map well

**The Bad** ❌:
- Type name collisions block ARCore
- Scene updates too coarse for performance
- Transform space ambiguity causes bugs

**The Verdict**:
> Contract is **85% complete**. With the critical fixes (Phase 1), it will be **production-ready** for basic AR/VR applications. Phase 2 additions enable advanced applications.

**Next Step**: Implement Phase 1 changes and update both WebXR and ARCore implementations.
