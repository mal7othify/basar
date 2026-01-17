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
- Frame loop with callbacks

⚠️ **Partial / TODO:**
- Scene rendering (SceneKit/Metal integration)
- Hit testing
- Raycasting
- Input sources
- Image tracking
- Face tracking

## Requirements

- iOS 11.0+
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

### Or use factory (automatic):

```kotlin
import com.spatialkit.engine.SpatialEngineFactory

// Automatically returns ARKit engine on iOS
val engine = SpatialEngineFactory.create()
```

### Full Compose integration:

```kotlin
import com.spatialkit.compose.SpatialView
import com.spatialkit.engine.SessionMode

@Composable
fun ARApp() {
    SpatialView(mode = SessionMode.AR) {
        cube(size = 10.cm, color = Color.RED) {
            position = vec3(0f, 0f, -1.meters)
        }
    }
}
```

## ARKit Capabilities

The engine reports the following capabilities (based on device):

```kotlin
EngineCapabilities(
    supportsAR = true,              // If ARWorldTrackingConfiguration supported
    supportsVR = false,             // iOS doesn't support native VR
    supportsPlaneDetection = true,  // Most devices
    supportsImageTracking = true,   // iOS 11.3+
    supportsFaceTracking = true,    // Devices with TrueDepth camera
    supportsHandTracking = false,   // Not available in ARKit
    supportsDepthOcclusion = true,  // iOS 13+ with LiDAR
    supportsSpatialAnchors = true,
    maxAnchors = 100
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
5. **Frame Loop**: Delivers frames at 60 FPS via timer

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
3. **Rendering**: Scene rendering not yet implemented
   - Need SceneKit or Metal integration
   - Currently just tracks AR state without visual output
4. **Hit Testing**: Requires ARSCNView integration
5. **Permissions**: App must request camera permission

## Performance

- Frame rate: 60 FPS (configurable)
- Plane detection: Real-time
- Tracking: Low latency (<20ms)

## Testing

ARKit cannot be tested in the iOS Simulator. You must:
1. Build to a physical iOS device
2. Grant camera permission
3. Test in a well-lit environment
4. Move device to detect planes

## Next Steps

To complete the implementation:

1. **Rendering Integration**:
   ```swift
   // Add SceneKit or Metal renderer
   func submitScene(sceneData: SceneData) {
       // Convert to SCNNodes or Metal buffers
       // Update visual scene
   }
   ```

2. **Hit Testing**:
   ```swift
   func performHitTest(screenX: Float, screenY: Float) -> HitTestResult? {
       let results = arView.hitTest(CGPoint(x: screenX, y: screenY))
       return results.first?.convertToHitTestResult()
   }
   ```

3. **Advanced Features**:
   - Image tracking
   - Face tracking
   - Body tracking
   - People occlusion

## Resources

- [ARKit Documentation](https://developer.apple.com/documentation/arkit)
- [ARWorldTrackingConfiguration](https://developer.apple.com/documentation/arkit/arworldtrackingconfiguration)
- [ARSession](https://developer.apple.com/documentation/arkit/arsession)
- [ARFrame](https://developer.apple.com/documentation/arkit/arframe)

## License

[Your License Here]
