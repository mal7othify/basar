# Changelog

All notable changes to the `com.spatialkit` (Basar) library modules are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project adheres to [Semantic Versioning](https://semver.org/) once `1.0.0` ships.
Before that, minor versions may contain breaking API changes (tracked by the
binary-compatibility validator dumps in each module's `api/` directory).

## [Unreleased]

### Added (M8 — interaction/gestures; on-device validation pending)
- Node picking (M8.1): `screenPointToRay(screenX, screenY)` on `FrameData`
  unprojects a normalized screen point through the frame's actual projection
  matrix (including off-center terms from cropped AR viewports) into a world
  ray; `SpatialSession.raycastNodeAt(screenX, screenY)` picks the scene node
  under a screen point. `SceneRaycaster` in `spatial-engine` gives CPU
  raycasting over `SceneData` for every geometry type — analytic tests for the
  unit primitives (including cylinder end caps, previously missed) and
  Möller–Trumbore triangle tests for custom meshes — with group transforms
  accumulated; the ARCore renderer's raycast (previously cube/sphere/cylinder
  only, no hierarchy) now delegates to it, so tapping glTF models works on
  Android.
- Gesture recognition in `spatial-compose` (M8.2): `SpatialView` recognizes
  tap, one-finger drag, pinch and two-finger twist on the rendering surface
  (`gesturesEnabled = true` by default; disable to run your own pointer
  handling). Recognition happens on the UI thread; events are queued and
  applied to the scene on the render thread before the frame callback, per
  the scene threading contract.
- Node-level interaction API (M8.3): `onTap` handler plus modifier-style
  `draggable(mode)`, `scalable(minFactor, maxFactor)` and `rotatable(axis)`
  on `SpatialNode`, usable directly inside DSL builder blocks. Gestures on a
  mesh resolve to the nearest self-or-ancestor node with the matching
  behavior, so a group or model root covers all its meshes. Dragging follows
  detected real-world surfaces (`DragMode.DetectedSurfaces`, with a
  horizontal-plane fallback) or a pure horizontal plane
  (`DragMode.HorizontalPlane`); pinch scaling clamps relative to the node's
  base scale so repeated gestures cannot drift; twist rotates about a
  configurable axis (default up). Taps that hit no interactive node fire
  `session.interactions.onTapMissed` (e.g. tap-to-place). Custom input
  sources can submit `GestureEvent`s to `InteractionManager` directly.
- `GroupScope.node` (DSL): access the group node being built to set its
  transform or interaction behavior.
- `SpatialNode.worldPosition` is now writable: setting it converts the
  world-space point into parent-local space at any hierarchy depth.
- Sample app (M8.4): place-and-arrange flow — tap content to bounce it (via
  `onTap`), tap a surface to place a crate (via `onTapMissed`), then drag /
  pinch / twist placed crates to arrange them.

### Fixed
- ARCore renderer ignored group transforms: meshes rendered with only their
  local transform, so `GroupNode`/`ModelNode` position, rotation and scale
  were dropped on Android (glTF models rendered at the origin). Parent
  matrices now compose down the hierarchy, matching SceneKit/Three.js
  parenting on the other platforms.

### Added (M7 — textured 3D models; on-device validation pending)
- `TextureData` in the engine contract: encoded PNG/JPEG bytes plus
  `TextureSampler` (wrap and filter modes). `MaterialData.textureId` replaced
  by `baseColorTexture: TextureData?`; `MeshNode.baseColorTexture` flows into
  submitted scenes.
- `GltfLoader` rewritten on `kotlinx.serialization` (M7.1–M7.3): GLB container
  and glTF JSON with embedded buffers, correct accessor `byteOffset` /
  bufferView `byteStride` (interleaved layouts), TEXCOORD_0 UVs, PBR
  metallic-roughness materials with base-color textures and sampler modes,
  node hierarchies (TRS and matrix transforms). New API:
  `GltfLoader.load(bytes|json): GltfModel` → `GltfModel.toNode()`; malformed
  assets throw `GltfParseException` with actionable messages. No longer
  experimental.
- Texture rendering in all three engines (M7.4–M7.6): WebXR decodes via
  `createImageBitmap` into refcount-cached Three.js textures (`flipY=false`,
  sRGB color space, sampler wrap/filter mapped, disposed with the last
  user); ARCore decodes via `BitmapFactory` and uploads with `GLUtils`, with
  UV attributes and base-color sampling added to the scene shader, textures
  cached by id and freed on node removal and `destroy()`; ARKit decodes to
  `UIImage` on `SCNMaterial.diffuse` with the `multiply` slot carrying the
  base-color factor and a `Texcoord` geometry source for UVs.
- Rendering conventions documented in the engine contract (M7.7): Y-up
  right-handed meters, CCW front faces, top-left UV origin, sRGB base-color
  textures multiplied with linear factors — all matching glTF 2.0.
- `ModelNode` + `model(...)` DSL builder in scene, group, and anchor scopes
  (M7.8); `GltfModel.toNode()` now returns `ModelNode`.
- Suspend `loadGltf(bytes)` (M7.9): parses off the caller's thread and caches
  parsed models by content, so N placements of one asset parse once.
- `EngineError.AssetError` (M7.10): a model that fails to parse inside a
  `SpatialView` content block is reported through `onError` instead of
  crashing the composition or rendering a silent blank scene.
- Sample app (M7.11): textured crate demo — a GLB-style glTF with an embedded
  checkerboard texture rendered on all three platforms; tapping a detected
  surface places anchored copies (tap → hit test → anchor → model).

### Documentation
- Full documentation audit against the current API: guides now use the real
  symbol names (`Color.Red`, `detectedPlanes`, `PlaneType.HORIZONTAL_UP`,
  `EngineError` subtypes, normalized 0–1 hit-test coordinates, radians-based
  `Quaternion.fromEuler`), reflect ARKit custom-mesh support, and follow the
  documented build-once/imperative-update model. Historical phase reports and
  the superseded 2026-03 production-readiness assessment moved to `docs/archive/`.
- Roadmap extended in PRODUCTION_PLAN.md: M7 (textured 3D models — current
  focus) and future milestones M8–M13 (gestures, AR realism, preview renderer,
  2D UI in space, tracking & persistence, VR).

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
