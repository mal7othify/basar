# Basar - Spatial-KMP Library

## Project Overview

A Kotlin Multiplatform library that provides a unified, Compose-like API for building AR/VR/Spatial applications.
Developers write spatial logic once and deploy to multiple platforms (Android AR, iOS AR, VR headsets, Web).

## Core Principles

1. **Compose-like DSL** - API should feel familiar to Compose developers
2. **Write once, run anywhere** - Shared Kotlin code with platform-specific renderers
3. **Progressive complexity** - Simple things easy, complex things possible
4. **Type safety** - Leverage Kotlin's type system for compile-time checks

## Architecture Principle

ENGINE FIRST. The renderer interface defines the contract. Scene graph and DSL are shaped by what renderers actually
need.

## Tech Stack

- Kotlin 2.0+
- Compose Multiplatform 1.6+
- Kotlin Multiplatform (KMP) with expect/actual
- Gradle with version catalogs

## Module Structure

- `spatial-core` - Pure Kotlin math primitives (Vec3, Quaternion, Transform)
- `spatial-engine` - Core renderer interface + abstractions (THE IMPORTANT PART)
- `spatial-engine-webxr` - WebXR implementation (Kotlin/JS + Three.js)
- `spatial-engine-arcore` - ARCore implementation (Android)
- `spatial-engine-arkit` - ARKit implementation (iOS, SceneKit-backed)
- `spatial-scene` - Scene graph built on engine requirements
- `spatial-dsl` - Kotlin DSL for building scenes (zero-overhead syntax sugar)
- `spatial-compose` - Compose Multiplatform integration

## Code Style

- FOLLOW BEST PRACTICE
- Keep performance on your mind and do the best in performance wise
- Use Kotlin idioms (data classes, extension functions, DSL builders)
- Follow Compose naming conventions (@Composable, Modifier pattern)
- Comprehensive KDoc for public APIs
- Unit tests for core logic

## Current Phase

✅ **Foundation complete (M0–M6)** — full architecture stack:
**Compose DSL (@Composable) → Kotlin DSL (spatialScene) → Scene Graph → Engine Interface → Platform Renderers**

✅ **M7 (textured GLB models), M8 (gestures), M9 (AR realism) — code-complete**
(see PRODUCTION_PLAN.md for per-milestone breakdowns).
M9 shipped: light estimation feeding renderer lighting on all three platforms
(`enableLightEstimation` defaults to true; `LightEstimate.ambientIntensity`
normalized so 1.0 = nominal), soft blob shadows via `castsShadow` on
nodes (shared `SceneBounds` + native per-renderer discs), and ARCore depth
occlusion behind opt-in `EngineConfig.enableDepthOcclusion` (iOS/Web report
`supportsDepthOcclusion = false` honestly — SceneKit can't composite depth).

🚧 **Pending: combined on-device validation pass** (M7.12 + M8.5 + M9.4):
Android already validated through M8; needs an M9 re-run (lighting response,
shadows, occlusion on a Depth phone). iPhone (`scripts/run-ios.sh`) + WebXR
runs pending — no iOS device on hand as of 2026-07-08.

### Platform Status

- **Android**: ARCore engine + Compose integration ✅ (only renderer with depth occlusion)
- **Web**: WebXR engine + Compose integration ✅ (anchors/planes not XRAnchor-backed yet; light estimation needs Chrome's `light-estimation` feature)
- **iOS**: ARKit engine (SceneKit) + Compose integration ✅ (custom meshes done; on-device validation pending)

### Roadmap (after M9)

Tracked milestone-by-milestone in PRODUCTION_PLAN.md:
- **M10** Non-XR preview renderer (desktop/simulator dev loop)
- **M11** 2D UI in space (labels, billboards, Compose panels)
- **M12** Tracking & persistence (image tracking, anchor persistence, WebXR anchors)
- **M13** Real VR (stereo rendering + controllers) — or narrow the AR/VR claim
- First Maven Central release: pending — cut a `v*` tag (workflow exists)

### Update model (important for docs and examples)

The `SpatialView` content block builds the scene **once**; afterwards nodes are
mutated **imperatively from `onFrame`** (the render thread). Docs and examples
must not show reactive recomposition of scene content. Node-level state is
mutated only from the frame callback; structural scene ops are synchronized.



