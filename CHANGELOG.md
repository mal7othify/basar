# Changelog

All notable changes to the `com.spatialkit` (Basar) library modules are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project adheres to [Semantic Versioning](https://semver.org/) once `1.0.0` ships.
Before that, minor versions may contain breaking API changes (tracked by the
binary-compatibility validator dumps in each module's `api/` directory).

## [Unreleased]

### Added
- Maven publishing for all `spatial-*` modules under the `com.spatialkit` group
  (Apache-2.0, sources + javadoc jars, Central Portal ready).
- `explicitApi()` across all library modules; committed binary-compatibility API dumps.
- `SpatialLog` logging facade with pluggable sink; no logging on the per-frame path.
- `EngineError.RuntimeError(message, cause)` for frame-callback failures (wrapped once).
- Observable engine state: `SpatialEngine.state: StateFlow<EngineState>` and
  `suspend fun startSession(mode)` that completes on Running/Error.
- `LocalSpatialEngine` composition local and `SpatialView(engine = …)` so apps choose
  their platform engine; Android no longer hard-bundles ARCore via `spatial-compose`.
- Animated spatial values: `Vec3.VectorConverter`, `Quaternion.VectorConverter` (nlerp
  with renormalization), short-arc `animatedSpatialVec3` / `animatedSpatialRotation`.
- Unified hit-test contract: normalized 0–1 coordinates on all platforms; WebXR
  hit-testing implemented (XRHitTestSource with Three.js raycast fallback).
- CI (build + checks), release-on-tag publishing, and Dokka docs workflows.

### Changed
- Dependency scopes corrected (`implementation` → `api`) so consumers of a single
  published artifact can reference the transitive public types.
- Hierarchy math: `worldPosition` composes parent scale correctly; `addChild` rejects
  cycles; mesh nodes with children emit group scene nodes.
- Anchor re-tracking recomputes node offsets absolutely (no drift) and anchor
  callbacks are multicast with unsubscribe handles.
- `Quaternion.lookRotation` honors its `up` parameter; forward convention documented.
- WebXR: GPU resources disposed on node removal/dispose, material/geometry diffs
  applied to cached meshes, 32-bit index buffers for large meshes, no host-page
  restyling (replaced by `onEnterXR`/`onExitXR` hooks).
- ARCore: anchors created on the GL thread inside the frame loop; near/far planes
  configurable via `EngineConfig`; deprecated display APIs replaced.

### Removed
- Committed TLS key material, ghost `spatial-renderer-canvas` module, root-level
  fix-log docs, and duplicate unit-conversion DSL in `spatial-dsl`.

## [0.1.0] — not yet released

First publishable snapshot of the library line (`0.1.0-SNAPSHOT`). See the
[production plan](PRODUCTION_PLAN.md) for the milestone history and the
[README](README.md) for current platform support.
