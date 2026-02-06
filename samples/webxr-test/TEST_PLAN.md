# WebXR Engine - Contract Validation Test Plan

## Overview

This test application validates the `SpatialEngine` contract implementation with real WebXR scenarios. It tests all interface methods, data types, and expected behaviors to ensure the contract is production-ready.

## Test Environment

### Requirements
- ✅ WebXR-capable browser (Chrome/Edge 90+)
- ✅ HTTPS connection (or localhost)
- ✅ AR-capable device (Android phone) or VR headset
- ✅ Sufficient lighting (for AR plane detection)

### Setup
```bash
# Build the test app
./gradlew :samples:webxr-test:compileKotlinJs

# Serve over HTTPS (required for WebXR)
./gradlew :samples:webxr-test:jsBrowserDevelopmentRun

# Or use any HTTPS server pointing to:
# samples/webxr-test/build/dist/js/developmentExecutable/
```

## Test Scenarios

### TEST 1: WebXR Availability ✅
**Purpose**: Verify browser supports WebXR

**Steps**:
1. Load test page
2. Check `navigator.xr` exists

**Expected**:
- ✅ `isWebXRAvailable()` returns true
- ✅ Function executes without errors

**Contract Elements Tested**:
- Factory function accessibility

---

### TEST 2: Engine Creation ✅
**Purpose**: Verify engine can be instantiated

**Steps**:
1. Call `createWebXREngine()`
2. Check engine object created

**Expected**:
- ✅ Engine object returned
- ✅ Initial state is `Uninitialized`
- ✅ No exceptions thrown

**Contract Elements Tested**:
- `SpatialEngine` interface
- Factory function
- `state` property

---

### TEST 3: Capabilities Check ✅
**Purpose**: Verify capabilities reporting before initialization

**Steps**:
1. Access `engine.capabilities`
2. Verify all properties present

**Expected**:
- ✅ `EngineCapabilities` object returned
- ✅ All boolean flags accessible
- ✅ `maxAnchors` returns integer
- ⚠️  Values may be false on desktop (expected)

**Contract Elements Tested**:
- `capabilities` property
- `EngineCapabilities` data class

**Potential Issues**:
- [ ] Capabilities available before `initialize()`?
- [ ] Values accurate for current platform?

---

### TEST 4: Engine Initialization ✅
**Purpose**: Verify async initialization works

**Steps**:
1. Call `engine.initialize(config)`
2. Wait for completion
3. Check state changed to `Ready`

**Expected**:
- ✅ `initialize()` completes without error
- ✅ State transitions to `EngineState.Ready`
- ✅ Config parameters accepted

**Contract Elements Tested**:
- `initialize()` method (suspend)
- `EngineConfig` data class
- State management

**Potential Issues**:
- [ ] Does `initialize()` block properly?
- [ ] Can it be called twice?
- [ ] Error handling for permissions?

---

### TEST 5: Session Start ✅
**Purpose**: Verify AR/VR session can start

**Steps**:
1. Call `engine.startSession(SessionMode.AR)`
2. Wait for state change
3. Verify session running

**Expected**:
- ✅ State transitions to `EngineState.Running`
- ✅ `sessionMode` matches request
- ✅ No exceptions thrown
- ⚠️  May require user permission

**Contract Elements Tested**:
- `startSession()` method
- `SessionMode` sealed interface
- State transitions

**Potential Issues**:
- [ ] Permission prompts block?
- [ ] Error reporting clear?
- [ ] Can switch modes?

---

### TEST 6: Scene Submission ✅
**Purpose**: Verify scene data can be submitted

**Steps**:
1. Create `SceneData` with multiple nodes
2. Call `engine.submitScene(scene)`
3. Verify no errors

**Expected**:
- ✅ Scene accepted without error
- ✅ All geometry types supported
- ✅ All material types supported
- ✅ Objects visible in AR/VR

**Contract Elements Tested**:
- `submitScene()` method
- `SceneData` data class
- `SceneNode.Mesh` and `SceneNode.Group`
- `GeometryData` and all `GeometryType` values
- `MaterialData` and all `MaterialType` values
- `Transform` with position/rotation/scale

**Potential Issues**:
- [ ] Scene applies immediately?
- [ ] Old nodes cleaned up?
- [ ] Transform order correct?
- [ ] Material properties map correctly?

---

### TEST 7: Frame Loop ✅
**Purpose**: Verify frame callback works at target FPS

**Steps**:
1. Register `onFrame()` callback
2. Count frames over 1 second
3. Verify frame rate and data

**Expected**:
- ✅ Callback invoked every frame
- ✅ ~60 FPS achieved
- ✅ `FrameData` complete and valid
- ✅ `deltaTime` accurate
- ✅ Camera pose updates
- ✅ Timestamp increases monotonically

**Contract Elements Tested**:
- `onFrame()` method
- `FrameData` data class
- `Pose` data class
- Projection/view matrices
- `TrackingState` enum

**Potential Issues**:
- [ ] Frame drops under load?
- [ ] Callback can be replaced?
- [ ] Matrix format correct (column-major)?
- [ ] Pose coordinates correct (right-handed)?

---

### TEST 8: Plane Detection (AR) ⚠️
**Purpose**: Verify plane detection works in AR

**Steps**:
1. Start AR session with plane detection enabled
2. Move device to scan environment
3. Check `frameData.detectedPlanes`

**Expected**:
- ✅ Planes appear in frame data
- ✅ Plane IDs stable across frames
- ✅ Plane types correct (horizontal/vertical)
- ✅ Extents and poses accurate

**Contract Elements Tested**:
- `FrameData.detectedPlanes`
- `DetectedPlane` data class
- `PlaneType` enum

**Potential Issues**:
- [ ] WebXR doesn't expose planes directly (known limitation)
- [ ] Plane vertices provided?
- [ ] Plane updates tracked?

---

### TEST 9: Anchor Placement ⚠️
**Purpose**: Verify anchors can be created and tracked

**Steps**:
1. Detect a plane
2. Submit `AnchorRequest` for plane
3. Check `frameData.anchors` for tracking

**Expected**:
- ✅ Anchor creation accepted
- ✅ Anchor appears in frame data
- ✅ Status transitions (PENDING → TRACKING)
- ✅ Pose updates each frame

**Contract Elements Tested**:
- `AnchorRequest` data class
- `AnchorType` sealed interface
- `AnchorState` data class
- `AnchorStatus` enum

**Potential Issues**:
- [ ] Anchors tracked per-frame?
- [ ] Anchor IDs stable?
- [ ] Lost anchors reported?
- [ ] Multiple anchor types supported?

---

### TEST 10: Hit Testing (AR) ⚠️
**Purpose**: Verify screen-space hit testing

**Steps**:
1. Call `performHitTest(screenX, screenY)`
2. Check result contains hit point

**Expected**:
- ✅ Hit test returns result or null
- ✅ Hit position in world space
- ✅ Normal vector correct
- ✅ Plane ID if hit plane

**Contract Elements Tested**:
- `performHitTest()` method
- `HitTestResult` data class

**Potential Issues**:
- [ ] Coordinates normalized (0-1)?
- [ ] Results synchronized with frame?
- [ ] WebXR hit test integration?

---

### TEST 11: Raycasting ✅
**Purpose**: Verify world-space raycasting

**Steps**:
1. Call `performRaycast(origin, direction)`
2. Check if ray hits scene objects

**Expected**:
- ✅ Returns hit or null
- ✅ Hit distance accurate
- ✅ Node ID identifies object
- ✅ Works on submitted scene

**Contract Elements Tested**:
- `performRaycast()` method
- `RaycastResult` data class

**Potential Issues**:
- [ ] Direction needs normalization?
- [ ] Hits environment vs scene?
- [ ] Performance acceptable?

---

### TEST 12: Input Handling ✅
**Purpose**: Verify input sources reported

**Steps**:
1. Use AR/VR with controllers
2. Check `frameData.inputSources`
3. Verify button states and poses

**Expected**:
- ✅ Controllers appear in input sources
- ✅ Pose updates each frame
- ✅ Button states correct
- ✅ Axes report analog values

**Contract Elements Tested**:
- `InputSource` sealed interface
- `InputSource.Controller`
- `Button` enum
- `ButtonState` enum
- `Axis` enum

**Potential Issues**:
- [ ] Touch input on phones?
- [ ] Hand tracking support?
- [ ] Input latency?

---

### TEST 13: Session Stop ✅
**Purpose**: Verify session can be stopped cleanly

**Steps**:
1. Call `engine.stopSession()`
2. Verify state returns to Ready
3. Check resources cleaned up

**Expected**:
- ✅ Session stops without error
- ✅ State returns to `EngineState.Ready`
- ✅ Frame callbacks stop
- ✅ Can restart session

**Contract Elements Tested**:
- `stopSession()` method
- State transitions

**Potential Issues**:
- [ ] Resources leaked?
- [ ] Can restart immediately?
- [ ] Anchors persisted?

---

### TEST 14: Dispose ✅
**Purpose**: Verify engine can be disposed

**Steps**:
1. Call `engine.dispose()`
2. Verify state is Disposed
3. Check cleanup complete

**Expected**:
- ✅ Disposal completes
- ✅ State is `EngineState.Disposed`
- ✅ Cannot call methods after disposal

**Contract Elements Tested**:
- `dispose()` method

**Potential Issues**:
- [ ] DOM elements removed?
- [ ] Memory freed?
- [ ] Can reinitialize after dispose?

---

## Test Execution Checklist

### Automated Tests (Run on Load)
- [x] TEST 1: WebXR Availability
- [x] TEST 2: Engine Creation
- [x] TEST 3: Capabilities Check

### Interactive Tests (Button Click)
- [ ] TEST 4: Engine Initialization (Initialize button)
- [ ] TEST 5: Session Start (Start AR button)
- [ ] TEST 6: Scene Submission (automatic after session start)
- [ ] TEST 7: Frame Loop (automatic after session start)
- [ ] TEST 8: Plane Detection (move device around)
- [ ] TEST 9: Anchor Placement (Place Anchor button)
- [ ] TEST 10: Hit Testing (tap screen - future)
- [ ] TEST 11: Raycasting (Raycast button)
- [ ] TEST 12: Input Handling (use controllers if available)
- [ ] TEST 13: Session Stop (Stop button)
- [ ] TEST 14: Dispose (automatic after stop)

## Contract Issues Log

### Issues Found During Testing

#### Issue #1: Naming Collision - Hand enum vs InputSource.Hand class
- **Severity**: Medium
- **Contract Element**: `com.spatialkit.engine.Hand` enum and `InputSource.Hand` class
- **Description**: Type name collision requires disambiguation in implementation
- **Expected**: Clear, unambiguous type names that don't collide
- **Actual**: Implementation required type alias: `import com.spatialkit.engine.Hand as ControllerHand`
- **Proposed Fix**: Rename enum to `HandSide` or `ControllerHand`, or rename class to `HandTracking`
- **Status**: Open
- **File Reference**: spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt:31

#### Issue #2: maxAnchors = 0 Ambiguous Meaning
- **Severity**: Low
- **Contract Element**: `EngineCapabilities.maxAnchors: Int`
- **Description**: Unclear whether 0 means "no anchors supported" or "unlimited anchors"
- **Expected**: Clear contract specification for the meaning of 0
- **Actual**: WebXR implementation uses 0 to mean "unlimited" (see comment at line 670)
- **Proposed Fix**: Either:
  - Use -1 for unlimited, 0 for none
  - Use nullable Int? where null = unlimited
  - Add documentation to contract specifying 0 = unlimited
- **Status**: Open
- **File Reference**: spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt:670

#### Issue #3: Re-initialization Behavior Unspecified
- **Severity**: Low
- **Contract Element**: `SpatialEngine.initialize()` method
- **Description**: Contract doesn't specify if calling initialize() twice is allowed
- **Expected**: Contract should specify idempotency or throw behavior
- **Actual**: WebXR implementation throws IllegalStateException if already initialized
- **Proposed Fix**: Document in contract that initialize() can only be called once, or must be called after dispose()
- **Status**: Open
- **File Reference**: spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt:76

#### Issue #4: Post-disposal Behavior Unspecified
- **Severity**: Low
- **Contract Element**: `SpatialEngine.dispose()` method
- **Description**: Contract doesn't specify behavior of calling methods after dispose()
- **Expected**: Clear specification of what happens after disposal
- **Actual**: Implementation allows method calls but they'll fail with NPEs
- **Proposed Fix**: Contract should specify either:
  - All methods after dispose() should throw IllegalStateException
  - Engine should be re-initializable after disposal
  - Disposal is one-way and engine should be discarded
- **Status**: Open
- **File Reference**: spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt:227-238

### Implementation Limitations (Not Contract Issues)

These are WebXR/implementation-specific limitations, not problems with the contract design:

#### Limitation #1: Plane Detection Not Exposed
- **Element**: `FrameData.detectedPlanes`
- **Description**: WebXR API doesn't directly expose plane detection results
- **Impact**: Implementation returns empty list
- **File Reference**: spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt:404

#### Limitation #2: Hit Testing Stub Implementation
- **Element**: `performHitTest()` method
- **Description**: Requires per-frame XRHitTestSource integration
- **Impact**: Currently returns null (not implemented)
- **File Reference**: spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt:301-309

#### Limitation #3: Anchor Tracking Not Implemented
- **Element**: `FrameData.anchors`
- **Description**: XRAnchor API integration not yet complete
- **Impact**: Anchors always return empty list
- **File Reference**: spatial-engine-webxr/src/jsMain/kotlin/com/spatialkit/engine/webxr/WebXREngine.kt:407

---

## Success Criteria

The contract is considered **validated** when:

✅ All automated tests pass
✅ All interactive tests can be executed without exceptions
✅ Scene renders correctly with all geometry types
✅ Materials apply correctly (color, metallic, roughness)
✅ Frame loop runs at target FPS
✅ Raycasting returns accurate results
✅ Session lifecycle works (init → start → stop → dispose)
✅ No critical contract issues found

Optional (nice to have):
⚠️ Plane detection works (WebXR limitation understood)
⚠️ Anchors track correctly (may need more implementation)
⚠️ Hit testing works (may need per-frame integration)

## Testing Notes

### WebXR Limitations
- Plane detection not directly exposed by WebXR API (implementation returns empty list)
- Hit testing requires per-frame XRHitTestSource (stub implementation)
- Anchors need XRAnchor API integration (future work)

These are **implementation limitations**, not contract issues.

### Performance Benchmarks
- Target: 60 FPS
- Scene submission: < 16ms
- Raycast query: < 1ms
- Memory: Monitor for leaks during long sessions

### Browser Compatibility
- Chrome 90+ (Android): Full support
- Chrome (Desktop): Limited (no AR)
- Edge 90+: Full support
- Firefox: WebXR support varies
- Safari: Limited WebXR support

---

**Last Updated**: January 15, 2026
**Test Version**: 1.0
**Contract Version**: spatial-engine v1.0
