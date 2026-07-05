# spatial-engine-arkit

ARKit engine implementation for Spatial-KMP on iOS.

## Overview

This module provides a complete ARKit-based implementation of the `SpatialEngine` contract for iOS platforms. It enables AR experiences using Apple's ARKit framework.

## Features

✅ **Implemented:**
- ARSession management
- World tracking
- Plane detection (horizontal & vertical)
- Camera pose tracking
- Anchor management
- Light estimation support
- Transform conversions (ARKit ↔ Spatial-KMP)
- Frame loop with callbacks (driven by the ARSCNView render delegate)
- Scene rendering via SceneKit (`submitScene` → `SCNNode` graph)
- Custom mesh geometry (`GeometryType.CUSTOM_MESH` → `SCNGeometry`)
- Hit testing (`ARSCNView.hitTest`) and raycasting

⚠️ **Partial / TODO:**
- On-device validation of custom meshes
- Input sources
- Image tracking
- Face tracking

## Requirements

- iOS 13+
- Physical device with A9 chip or later
- Camera permission

**Note:** ARKit does not work in iOS Simulator - must test on real hardware.

## Usage

### In your iOS app:

```kotlin
// build.gradle.kts
iosMain.dependencies {
    implementation(project(":spatial-compose"))
    implementation(project(":spatial-engine-arkit"))
}
```

### Check ARKit availability:

```kotlin
import com.spatialkit.engine.arkit.isARKitAvailable

if (isARKitAvailable()) {
    println("ARKit supported on this device")
} else {
    println("ARKit not available")
}
```

### Create ARKit engine directly:

```kotlin
import com.spatialkit.engine.arkit.createARKitEngine

val engine = createARKitEngine()
```

Note: the common `SpatialEngineFactory.create()` does **not** return an ARKit
engine — it returns the fallback. Use `createARKitEngine()` from this module
(the `spatial-compose` iOS surface does this wiring for you by default).

### Full Compose integration:

```kotlin
import com.spatialkit.compose.SpatialView
import com.spatialkit.engine.SessionMode

@Composable
fun ARApp() {
    SpatialView(mode = SessionMode.AR) {
        cube(size = 10.cm, color = Color.Red) {
            position = vec3(0f, 0f, -1.meters)
        }
    }
}
```

## ARKit Capabilities

The engine reports the following capabilities (based on device):

```kotlin
EngineCapabilities(
    supportsAR = ARWorldTrackingConfiguration.isSupported,
    supportsVR = false,             // iOS doesn't support native VR
    supportsPlaneDetection = ARWorldTrackingConfiguration.isSupported,
    supportsImageTracking = ARImageTrackingConfiguration.isSupported,
    supportsFaceTracking = ARFaceTrackingConfiguration.isSupported,  // TrueDepth devices
    supportsHandTracking = false,   // ARKit has no generic hand tracking
    supportsDepthOcclusion = ...,   // person-segmentation-with-depth frame semantics
    supportsSpatialAnchors = true,
    maxAnchors = 100,               // heuristic; ARKit publishes no hard limit
)
```

## Architecture

```
ARKitEngine
    ↓
ARSession (ARKit)
    ↓
ARFrame → FrameData
    ↓
User callback
```

### Key Components:

1. **ARKitEngine**: Main engine class implementing `SpatialEngine`
2. **ARSession**: Apple's AR session (from ARKit framework)
3. **Transform Conversion**: Converts ARKit's `simd_float4x4` to `Pose`
4. **Plane Detection**: Converts `ARPlaneAnchor` to `DetectedPlane`
5. **Frame Loop**: Delivers frames from the ARSCNView render delegate (per ARKit frame)

## Implementation Details

### Coordinate System

ARKit uses a right-handed coordinate system:
- +X: Right
- +Y: Up
- +Z: Towards user (out of screen)

This matches Spatial-KMP's coordinate system, so no conversion needed.

### Transforms

ARKit provides transforms as `simd_float4x4` matrices. The engine:
1. Extracts position from the last column
2. Converts rotation matrix to quaternion
3. Returns as `Pose(position, rotation)`

### Plane Detection

Planes are detected automatically when enabled:
- Horizontal planes: floors, tables
- Vertical planes: walls

Each plane includes:
- ID (UUID)
- Type (horizontal/vertical)
- Center pose (position + rotation)
- Extents (width, height, depth)

### Session Modes

- **AR Mode**: Uses `ARWorldTrackingConfiguration`
- **VR Mode**: Not supported (throws error)

## Limitations

1. **VR Not Supported**: iOS doesn't have native VR APIs
2. **Simulator**: ARKit requires physical device
3. **Permissions**: App must request camera permission

## Performance

- Frame rate: 60 FPS (driven by the ARSCNView render delegate)
- Plane detection: Real-time
- Tracking: Low latency (<20ms)

## Testing

ARKit cannot be tested in the iOS Simulator. You must:
1. Build to a physical iOS device
2. Grant camera permission
3. Test in a well-lit environment
4. Move device to detect planes

## Next Steps

See the roadmap in [PRODUCTION_PLAN.md](../PRODUCTION_PLAN.md). For this module:

- On-device validation (custom meshes, hit testing, anchors)
- Texture support (rides along with the textured-model milestone)
- Image tracking
- Face tracking
- People occlusion

## Resources

- [ARKit Documentation](https://developer.apple.com/documentation/arkit)
- [ARWorldTrackingConfiguration](https://developer.apple.com/documentation/arkit/arworldtrackingconfiguration)
- [ARSession](https://developer.apple.com/documentation/arkit/arsession)
- [ARFrame](https://developer.apple.com/documentation/arkit/arframe)

## License

[Apache-2.0](../LICENSE)
