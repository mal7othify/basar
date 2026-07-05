# Spatial Engine - WebXR Implementation

WebXR + Three.js implementation of the `SpatialEngine` contract (Kotlin/JS).

## Implementation

- **WebXREngine.kt** - Complete SpatialEngine implementation
- **ThreeJS.kt** - External declarations for Three.js
- **WebXRApi.kt** - External declarations for WebXR Device API
- **Factory.kt** - `createWebXREngine()` / `isWebXRAvailable()`

## What Works

- ✅ Engine lifecycle (initialize → startSession → frame loop → dispose)
- ✅ AR and VR session modes
- ✅ Geometry primitives (Cube, Sphere, Plane, Cylinder, Capsule)
- ✅ Custom mesh support (32-bit indices for large meshes)
- ✅ PBR materials with metallic/roughness; material/geometry updates diffed on submit
- ✅ Scene hierarchy (Groups with children)
- ✅ Hit testing (XRHitTestSource with Three.js raycast fallback, normalized 0–1 coords)
- ✅ Raycasting (world-space face normals)
- ✅ Controller input (buttons, axes, pose)
- ✅ Capabilities detection
- ✅ GPU resource disposal on node removal/dispose
- ✅ `onEnterXR`/`onExitXR` hooks (the engine never restyles its host page)

## Key Mappings

| Contract | WebXR + Three.js |
|----------|------------------|
| `GeometryType.CUBE` | `THREE.BoxGeometry` |
| `GeometryType.SPHERE` | `THREE.SphereGeometry` |
| `MaterialType.PBR` | `THREE.MeshStandardMaterial` |
| `Transform` | `Object3D.position/rotation/scale` |
| `XRFrame.getViewerPose()` | `FrameData.cameraPose` |
| `XRInputSource` | `InputSource.Controller` |

## Building

```bash
# Compile Kotlin → JavaScript
./gradlew :spatial-engine-webxr:compileKotlinJs

# Build full bundle with webpack
./gradlew :spatial-engine-webxr:jsBrowserDevelopmentWebpack

# Run dev server
./gradlew :spatial-engine-webxr:jsBrowserDevelopmentRun
```

## Testing

Use the repo's demo app (`composeApp`, JS target) to exercise the engine
end-to-end.

**Requirements:**
- WebXR-capable browser (Chrome/Edge on Android, or VR headset browser)
- HTTPS connection (WebXR security requirement)

**To test:**
1. Build the demo: `./gradlew :composeApp:jsBrowserDevelopmentWebpack`
2. Serve the output over HTTPS
3. Start an AR/VR session and verify content renders and tracks

## Known Limitations

| Feature | Status | Notes |
|---------|--------|-------|
| Anchors | ⚠️ | Not backed by XRAnchor yet (content stays where placed, no re-localization) |
| Plane detection | ⚠️ | `detectedPlanes` is empty — WebXR Plane Detection API not integrated |
| Stereo rendering | ⚠️ | Frame data uses the first view only; documented mono-only for now |

## Next Steps

See the roadmap in [PRODUCTION_PLAN.md](../PRODUCTION_PLAN.md). For this module:

- XRAnchor-backed anchors and plane detection
- Texture support (rides along with the textured-model milestone)
- Per-view stereo frame data for VR headsets

## Troubleshooting

**Network errors with yarn:**
```bash
# Switch to npm (more reliable)
echo "kotlin.js.yarn=false" >> gradle.properties
rm -rf kotlin-js-store build/js
```

**"WebXR not available" error:**
- Requires HTTPS
- Requires WebXR-capable browser
- Desktop browsers don't support AR (need Android phone)