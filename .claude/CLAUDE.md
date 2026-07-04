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

✅ **Phase 7: Compose Integration - COMPLETE**

All core phases completed! Full architecture stack:
**Compose DSL (@Composable) → Kotlin DSL (spatialScene) → Scene Graph → Engine Interface → Platform Renderers**

### Completed Phases

1. ✅ **Phase 1-3**: Core Foundation (math, types, primitives)
2. ✅ **Phase 4**: Engine Interface Layer (contract-first design)
3. ✅ **Phase 5**: Scene Graph (nodes, shapes, anchors, sessions)
4. ✅ **Phase 6**: Kotlin DSL (zero-overhead builders, extension functions)
5. ✅ **Phase 7**: Compose Integration (reactive UI integration)

### Platform Status

- **Android**: ARCore engine + Compose integration ✅
- **Web**: WebXR engine + Compose integration ✅
- **iOS**: ARKit engine (SceneKit) + Compose integration ✅ (custom meshes TODO)

### Next Steps

The library foundation is complete. Ready for:
- Example applications showcasing the full stack
- ARKit: custom mesh support, on-device validation
- Performance optimization and benchmarking
- Advanced features (physics, gestures, lighting)



