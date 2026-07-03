# Raycasting Debug Guide

## What Was Changed

Added comprehensive debug logging to diagnose why raycasting isn't detecting shapes.

### Improvements Made

1. **Better Ray Calculation** - Using proper FOV-based ray calculation
2. **Debug Logging** - Prints detailed info on every tap
3. **Dual Hit Testing** - Tries both ARCore hit test and our raycasting

## How to Debug

### Step 1: Run the App

```bash
./gradlew :composeApp:assembleDebug
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb logcat | grep "TAP DEBUG"
```

### Step 2: Tap a Shape

Tap any of the colored shapes (red cube, green sphere, etc.)

### Step 3: Check Logcat Output

You should see output like this:

```
=== TAP DEBUG ===
Tap at: (540.0, 960.0)
Screen size: 1080.0x2400.0
Camera pos: Vec3(x=0.0, y=0.0, z=0.0)
Ray dir: Vec3(x=0.0, y=-0.2, z=-0.98)
Scene nodes count: 7
  Node: node-0-123, pos: Vec3(x=-0.3, y=0.1, z=-0.8), visible: true
  Node: node-1-456, pos: Vec3(x=0.0, y=0.1, z=-0.8), visible: true
  Node: node-2-789, pos: Vec3(x=0.3, y=0.1, z=-0.8), visible: true
  ... (more nodes)
Raycast result: RaycastResult(nodeId=node-0-123, position=..., distance=0.85)
==================
```

## What to Look For

### ✅ Good Signs

1. **Scene nodes count: 7** - All shapes are in the scene
2. **visible: true** - Shapes are set to visible
3. **Raycast result: RaycastResult(...)** - Hit detected!
4. **Camera pos is NOT (0,0,0)** - Camera is tracking

### ⚠️ Problem Signs

1. **Scene nodes count: 0** - Shapes not added to scene
2. **visible: false** - Shapes invisible
3. **Raycast result: null** - No hit detected
4. **Camera pos is (0,0,0)** - Camera not tracking yet

## Common Issues & Fixes

### Issue 1: No Nodes in Scene

**Symptom:**
```
Scene nodes count: 0
```

**Cause:** Shapes not being added to scene properly

**Fix:** Check that SpatialView is rendering and session is ready

### Issue 2: Camera Not Tracking

**Symptom:**
```
Camera pos: Vec3(x=0.0, y=0.0, z=0.0)
```

**Cause:** ARCore hasn't started tracking yet

**Fix:** Wait a few seconds for ARCore to initialize

### Issue 3: Ray Misses Objects

**Symptom:**
```
Scene nodes count: 7
Raycast result: null
```

**Cause:** Ray direction calculation might be off, or objects are too small

**Possible Fixes:**
1. Check ray direction makes sense (should point roughly where you tapped)
2. Check object positions are in front of camera (negative Z)
3. Try tapping directly in center of a shape

### Issue 4: Objects Too Far/Small

**Symptom:** Shapes visible but hard to hit

**Fix:** Make shapes bigger or move them closer:
```kotlin
// In App.kt, increase sizes
cube(size = 15.cm, ...) // was 8cm
sphere(radius = 8.cm, ...) // was 4cm
```

## Expected Values

When tapping the **center** of the screen on a shape at -0.8m:

- **Tap**: ~(540, 960) on 1080x1920 screen
- **NDC**: ~(0.0, 0.0) (center)
- **Camera pos**: Something like (0.02, 1.5, 0.0)
- **Ray dir**: Roughly (0.0, 0.0, -1.0) for center tap
- **Distance**: ~0.8m to 1.6m depending on which shape

## Testing Checklist

Try tapping:

1. ✅ **Red cube** (left side, close)
2. ✅ **Green sphere** (center, close)
3. ✅ **Blue cylinder** (right side, close)
4. ✅ **Yellow capsule** (left, middle)
5. ✅ **Magenta plane** (right, middle)
6. ✅ **Empty space** (should show "No hit")

## Advanced Debugging

### Enable Verbose Logging

To see even more detail:

```bash
adb logcat -s System.out:V ARCoreEngine:V SimpleRenderer:V
```

### Check Node IDs

The hit node ID should match one of the scene nodes:
```
Hit: node-0-123 at 0.85m
  ↑ Should match a node from the scene list
```

### Verify Ray Math

For a center tap:
- NDC should be close to (0, 0)
- Ray direction should be close to camera forward (0, 0, -1)
- Distance should match object depth (0.8m, 1.0m, or 1.6m)

## Next Steps Based on Results

### If Nodes Count = 0
→ Scene not populating. Check SpatialView rendering.

### If Raycast Always Null
→ Ray calculation or intersection math issue. Try making shapes bigger.

### If Hits Only Work in Center
→ Ray offset calculation needs tuning. The FOV-based calc should work better.

### If Everything Looks Good But Still No Hit
→ Check that shapes are actually being rendered (can you see them?)
→ Try tapping very precisely on shape centers

## Quick Fix: Make Shapes Bigger

If raycasting is working but hard to hit objects, temporarily make them bigger for testing:

```kotlin
// Temporarily 3x bigger for testing
cube(size = 24.cm, color = rgb(0xFF0000)) {
    position = vec3(-0.3f, 0.1f, -0.8f)
}
```

This will make hit testing much easier and confirm raycasting works!

---

**After running the app and tapping, share the logcat output and I can help diagnose the specific issue!**