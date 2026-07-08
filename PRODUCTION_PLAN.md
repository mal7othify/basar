# Production Readiness Plan

Goal: make the `spatial-*` modules a publishable, consumable Kotlin Multiplatform library.

This plan comes from a full code review (2026-07-03); M7+ added 2026-07-05. Work
top-to-bottom: each milestone builds on the previous one. Tasks are checkboxes so
progress is trackable in PRs; reference the task ID (e.g. `M1.3`) in commit messages.

**Milestone summary**

| Milestone | Theme | Outcome |
|---|---|---|
| M0 | Repo hygiene | Clean repo, no secrets, no ghost modules | ✅ |
| M1 | Library packaging | Artifacts other apps can actually depend on | ✅ |
| M2 | Shared-code correctness | The engine contract behaves as documented | ✅ |
| M3 | Logging & errors | No debug spam; errors carry real information | ✅ |
| M4 | Platform renderers | WebXR/ARCore fixes; consistent hit-testing | ✅ |
| M5 | API hardening | Observable state, honest APIs, docs that compile | ✅ |
| M6 | Quality infrastructure | CI, lint, tests, API stability guarantees | ✅ |
| M7 | Textured 3D models | Load a textured GLB and render it identically on all 3 platforms | 🚧 code-complete, device validation pending |
| M8 | Interaction | Tap/drag/pinch/rotate gestures on nodes | 🚧 code-complete, Android ✅; iPhone + WebXR validation pending |
| M9 | AR realism | Light estimation, shadows, depth occlusion | planned |
| M10 | Preview renderer | Non-XR 3D preview for desktop/simulator dev loop | planned |
| M11 | 2D UI in space | Text labels, billboards, Compose-content panels | planned |
| M12 | Tracking & persistence | Image tracking, anchor persistence, WebXR anchors/planes | planned |
| M13 | Real VR | Per-view stereo rendering + controller input model | planned |

---

## M0 — Repo hygiene (half a day)

- [x] **M0.1 Remove committed TLS key material.**
  `localhost-cert.pem` and `localhost-key.pem` are tracked (the `*.pem` gitignore rule
  was added after they were committed). Run `git rm --cached localhost-cert.pem
  localhost-key.pem`, commit, and regenerate the dev cert locally.
- [x] **M0.2 Delete the ghost module.**
  `spatial-renderer-canvas` is included in `settings.gradle.kts` but has no build file
  and no sources. Remove the `include(":spatial-renderer-canvas")` line and delete the
  directory (or create a tracking issue if it's planned work).
- [x] **M0.3 Archive the fix-log markdown files.**
  Move the 18 root-level history docs (`BROWN_SCREEN_FIX.md`, `RAYCAST_CRASH_FIX.md`,
  `P2_BUGS_FIXED.md`, `FINAL_STATUS.md`, …) into `docs/archive/`. Keep only `README.md`
  and this plan at the root.
- [x] **M0.4 Strip fix-tag comments from code.**
  Remove "P0.1 FIX", "P0.3 FIX", "P2 bug #8 fix" style comments (e.g.
  `SpatialScene.kt`, `SimpleRenderer.kt`, `SpatialAnimator.kt`) — they reference
  internal history that means nothing to a library consumer.
- [x] **M0.5 Remove stray imports.**
  `import kotlin.jvm.java` at `settings.gradle.kts:1` and `ARCoreEngine.kt:45`.
- [x] **M0.6 Separate library from playground (structural, optional but recommended).**
  DONE (minimum variant): the README now documents the library/playground split
  (see M5.6); `composeApp` serves as the demo app. Moving demos under `samples/`
  is deferred to the 1.0 line.
  `composeApp`, `server`, `shared` are demo/template code. Either move them under
  `samples/`, or at minimum make the README distinguish "the library" from "the demo app".

## M1 — Library packaging (1–2 days) — *the actual blocker*

- [x] **M1.1 Add a LICENSE file.**
  Nothing can be legally used or published to Maven Central without one. Decide the
  license (Apache-2.0 is the KMP-ecosystem default) and add it.
- [x] **M1.2 Create a publishing convention plugin.**
  Add `build-logic/` (or `buildSrc`) with a convention plugin applied by every
  `spatial-*` module that sets: `group = "com.spatialkit"`, a shared `version`,
  the `com.vanniktech.maven.publish` plugin, POM metadata (name, description, url,
  license, scm, developer), and sources/javadoc jars. This also removes the copy-pasted
  target/android blocks across the seven `spatial-*/build.gradle.kts` files.
- [x] **M1.3 Fix dependency scopes: `implementation` → `api` where types leak.**
  Every module currently hides its dependencies, but every module's public API exposes
  them. Required changes:
  - `spatial-engine/build.gradle.kts:31` → `api(project(":spatial-core"))`
    (`Math.kt` typealiases *are* spatial-core types)
  - `spatial-scene` → `api(":spatial-engine")`, `api(":spatial-core")`
    (`SpatialNode.position: Vec3`, `SpatialSession(runtime: SpatialRuntime)`)
  - `spatial-dsl` → `api(":spatial-scene")` and transitively core/engine
    (scope functions return `CubeNode`, accept `Color`, `GeometryData`)
  - `spatial-compose` → `api(":spatial-dsl")` (+ scene/engine)
    (`SpatialView` signature uses `SpatialSceneScope`, `SessionMode`, `EngineConfig`,
    `SpatialSession`, `FrameData`, `EngineError`)
  - `spatial-engine-arcore` / `-arkit` / `-webxr` → `api(":spatial-engine")`
    (factories return `SpatialEngine`)
  - `spatial-engine-arkit/build.gradle.kts:23`: un-comment and restore the
    spatial-core dependency (it imports `com.spatialkit.core.math.*` directly today
    and only compiles by accident).
  **Acceptance:** a scratch project depending *only* on the published
  `spatial-compose` artifact can reference `Vec3`, `SessionMode`, and `cube {}`.
- [x] **M1.4 Enable `explicitApi()` in all `spatial-*` modules** (via the convention
  plugin). Then sweep the fallout: mark internals `internal`
  (`GeometryBuffers`, `SpatialNode.rotate`, renderer helpers), and make the intended
  API explicitly `public`.
- [x] **M1.5 Fix the JS test browser matrix.**
  `spatial-core/build.gradle.kts:24-29` requires Chrome + Firefox + Safari + headless
  Chrome; any machine missing one fails the build. Keep `useChromeHeadless()` only.
- [x] **M1.6 Update README installation section** to use published coordinates
  (`com.spatialkit:spatial-compose:<version>`) once M1.2 lands.

## M2 — Shared-code correctness (2–3 days)

These bugs live in common code and affect every platform. Each fix must land with a
unit test (they're all pure Kotlin).

- [x] **M2.1 `SpatialNode.worldPosition` ignores parent scale** (`SpatialNode.kt:71-80`).
  Correct composition is `parent.worldPosition + parent.worldRotation * (parent.worldScale ⊙ localPosition)`.
  Today a child of a scaled group renders in one place (renderers use the transform
  hierarchy) while `worldPosition` reports another.
  *Test:* group scaled 2× with child at (1,0,0) → worldPosition (2,0,0).
- [x] **M2.2 Children of non-group nodes silently vanish.**
  `addChild` is on `SpatialNode`, but `CubeNode.toSceneNode()` (`ShapeNodes.kt:42`)
  emits a childless `Mesh`. Either restrict child management to `GroupNode`, or emit
  `SceneNode.Group(mesh, children)` when a mesh node has children. Pick one; document it.
  *Test:* cube with sphere child → sphere present in `toSceneData()` output.
- [x] **M2.3 `addChild` permits cycles** (`SpatialNode.kt:118-124`).
  `a.addChild(b); b.addChild(a)` → infinite recursion. Reject (require + message) if
  `child` is `this` or an ancestor of `this`.
- [x] **M2.4 `AnchorScope` compounds transforms and clobbers callbacks**
  (`AnchorScope.kt:26-34`, `SpatialAnchor.kt:87`).
  - On every re-track, `node.position = anchor.pose!!.position + node.position`
    accumulates — content drifts after each tracking loss/regain. Store each node's
    original local offset at registration and recompute absolutely.
  - `SpatialAnchor.onTrack/onLost/onFailed` are single-slot; `AnchorScope`'s internal
    registration and any user callback silently replace each other. Make callbacks
    multicast (list of listeners) with an unsubscribe handle.
  *Test:* simulate TRACKING → LOST → TRACKING; node position identical both times;
  two `onTrack` listeners both fire.
- [x] **M2.5 `Quaternion.lookRotation` ignores its `up` parameter**
  (`Quaternion.kt:332-340`). Implement proper basis construction
  (forward/right/up → rotation matrix → quaternion) instead of delegating to
  `fromToRotation`. While here: resolve the forward-convention conflict —
  `lookRotation` assumes forward = (0,0,1) but `Vec3.FORWARD = (0,0,-1)`. Pick one
  convention, apply it everywhere, document it in `Vec3` and `Transform`.
  *Test:* lookRotation with tilted `up` produces no roll around forward axis.
- [x] **M2.6 Decide and enforce the threading model.**
  DECIDED (differs from the original recommendation): the lock is **kept** for
  structural operations (root list, anchor map, `toSceneData()` snapshot) because
  the Compose integration currently mutates the scene from the main thread while
  frames arrive on the GL thread — deleting it would regress real safety. The
  documentation now states the true contract: structural ops are synchronized;
  node-level state (transforms, child lists) must only be mutated from the frame
  callback. Full single-thread enforcement is revisited with the M5.3 SpatialView
  rework.
- [x] **M2.7 Fix `generateId()` races** (`SpatialNode.kt:181-186`, `SpatialAnchor.kt:163-168`).
  Non-atomic shared counter + `Random.nextInt(1000)`. Use `kotlinx.atomicfu` counters,
  or 64-bit random hex ids.
- [x] **M2.8 Deduplicate the unit-conversion DSL.**
  `spatial-core/math/Units.kt` (`Number.meters/cm/mm/degrees/radians`) and
  `spatial-dsl/Extensions.kt:48-109` (`Float.meters`, `Int.cm`, `Float.degrees`, …)
  conflict — importing both causes overload ambiguity. Delete the duplicates in
  `spatial-dsl/Extensions.kt`; the DSL re-exports core via its `api` dependency (M1.3).
- [x] **M2.9 Small correctness sweep** (one PR):
  - `PlaneNode.doubleSided` (`ShapeNodes.kt:120`) is never mapped into `MaterialData`
    — add `doubleSided` to `MaterialData` and honor it in renderers.
  - Delete the private duplicate `Vec3.times(Vec3)` at `ShapeNodes.kt:295`
    (already a public operator in `Vec3.kt:51`).
  - `Matrix4` exposes its mutable `FloatArray` and `copy()` shares it — defensively
    copy in the constructor/accessor, or replace the data class with explicit
    `copy()` that clones.
  - `EngineCapabilities.maxAnchors = 0` means "unlimited" per KDoc but `FallbackEngine`
    uses 0 for "none" — change to `Int? = null` for unlimited.
  - `EngineConfig.targetFrameRate` is read by nothing — implement or remove.
  - Move the optimized `Quaternion.rotate(Vec3)` from `SpatialNode.kt:193` into
    `spatial-core`'s `Quaternion.times(Vec3)` (it allocates two quaternions today)
    and delete the scene-module copy.
- [x] **M2.10 `GltfLoader`: contain or fix** (`GltfLoader.kt`).
  The string-scanning parser mis-parses valid glTF (accessor `byteOffset` and
  `byteStride` ignored, `extractFloatData` hardcodes VEC3, key search can match
  outside the target object). Short-term: mark the class `@ExperimentalSpatialApi`
  (create the opt-in annotation in spatial-core) and document the limitations.
  Proper fix: parse with `kotlinx.serialization.json`.

## M3 — Logging & error handling (1 day)

- [x] **M3.1 Introduce a logging facade.**
  Tiny `SpatialLog` object in `spatial-core` (or an expect/actual) with
  `debug/info/warn/error` and a pluggable sink defaulting to no-op for debug and
  platform-native for warn/error. Replace all 31 `println`/`console.log` sites and the
  `Log.d` spam. Hard rule: **nothing logs on the per-frame path** (`SpatialRuntime.kt:102-121`,
  `ARCoreEngine.onDrawFrame`, `WebXREngine.onFrame` frame counters,
  `SpatialSurface.android.kt` SideEffect logs, `FallbackEngine`'s emoji warnings).
- [x] **M3.2 Fix the error taxonomy.**
  Frame-callback exceptions are currently reported as
  `EngineError.InitializationFailed("Frame callback error…")` — and wrapped **twice**
  (`SpatialRuntime.kt:145-155` and `SpatialSession.kt:171-176`), discarding the stack.
  Add `EngineError.RuntimeError(message: String, cause: Throwable?)`, wrap once
  (in `SpatialRuntime`), and delete the inner wrap in `SpatialSession.handleFrame`.
- [x] **M3.3 `FallbackEngine.stopSession()` must not report `Ready`**
  (`FallbackEngine.kt:53-56`). A never-supported engine transitioning to Ready defeats
  `SpatialRuntime`'s state checks. Keep it in `Error(NotSupported)`.

## M4 — Platform renderer fixes (2–4 days)

### WebXR (`spatial-engine-webxr`)

- [x] **M4.1 Dispose GPU resources.**
  Node removal (`WebXREngine.kt:407-413`) and `dispose()` must call
  `geometry.dispose()` and `material.dispose()` on removed meshes — long sessions
  currently leak GPU memory.
- [x] **M4.2 Apply material/geometry updates to cached meshes.**
  Meshes are cached by id at creation (`WebXREngine.kt:427-430`); only
  transform/visibility update afterwards, so `color`/`materialType` changes do nothing.
  Diff `MaterialData` (and geometry for custom meshes) on submit and update or rebuild.
- [x] **M4.3 Index buffer width** (`WebXREngine.kt:567`): choose `Uint32Array` when any
  index > 65535 instead of always `Uint16Array` (silently corrupts large meshes).
- [x] **M4.4 Remove host-page styling.**
  The engine sets `document.body.style.background` to a hardcoded purple gradient
  (`WebXREngine.kt:377`) and hides a canvas found by `querySelector` (`:93`). Replace
  with `onEnterXR`/`onExitXR` hooks (constructor parameters) that the compose surface
  wires up; the library must not restyle its host page.
- [x] **M4.5 Stereo rendering data**: `convertToFrameData` uses `viewerPose.views[0]`
  only (`WebXREngine.kt:665`) — wrong for VR headsets. Expose per-view data in
  `FrameData` (or document mono-only for now and pick the correct combined matrix).

### ARCore (`spatial-engine-arcore`)

- [x] **M4.6 Stop calling `session.update()` outside the frame loop.**
  `createAnchor` (`ARCoreEngine.kt:578`) calls `arSession.update()` from
  `submitScene`, violating ARCore's once-per-frame/GL-thread contract. Queue anchor
  requests and create them inside `onDrawFrame` after the real `update()`.
- [x] **M4.7 Stop caching the `Frame` object.**
  DONE (guarded variant): the cached frame is still used because the synchronous
  `performHitTest` API cannot hop to the GL thread; the call is now wrapped so a
  superseded frame returns null instead of crashing, and the cache is cleared on
  stop/dispose. A fully async hit-test API rides along with the M5.1 API rework.
- [x] **M4.8 Config values**: near/far planes hardcoded `0.1f/100f`
  (`ARCoreEngine.kt:471`) → move to `EngineConfig`; replace deprecated
  `windowManager.defaultDisplay` (`ARCoreEngine.kt:412`) with
  `context.display` / `DisplayManager`.

### Cross-platform contract

- [x] **M4.9 Unify the hit-test coordinate contract.**
  `SpatialEngine.performHitTest` KDoc says normalized 0–1 (`SpatialEngine.kt:105`);
  `SpatialSession` KDoc says pixels (`SpatialSession.kt:131`); ARCore passes values
  straight to `frame.hitTest()` which expects **pixels**; WebXR returns `null`
  unconditionally (`WebXREngine.kt:458`). Decide: **normalized 0–1 everywhere**.
  Fix ARCore (multiply by viewport size), implement WebXR via
  `XRHitTestSource` / Three.js raycast fallback, update both KDocs.
  *Acceptance:* same app code hit-tests correctly on Android and Web.
- [x] **M4.10 Fix WebXR raycast normal** (`WebXREngine.kt:480`): hardcoded (0,1,0);
  use `intersection.face.normal` transformed to world space.

## M5 — API hardening (2–3 days, before any 1.0 tag)

- [x] **M5.1 Make engine state observable and session start awaitable.**
  `SpatialEngine.state` is a polled `val`, and `startSession()` is fire-and-forget
  while WebXR resolves promises asynchronously — callers cannot know when or whether
  the session started. Change the contract to `val state: StateFlow<EngineState>` and
  `suspend fun startSession(mode)` completing on Running/Error. Ripple through
  `SpatialRuntime`, `SpatialSession`, all engines. This is the largest single API
  change — do it before anything is published, not after.
- [x] **M5.2 Decouple `spatial-compose` from ARCore.**
  DONE via `LocalSpatialEngine` / `SpatialView(engine = …)` plus the
  `GLSurfaceDrivenEngine` interface in `spatial-engine` androidMain: the Android
  surface drives whatever GL-surface engine the app provides and `spatial-compose`
  no longer depends on `spatial-engine-arcore`. iOS/Web keep bundled defaults but
  now honor a provided engine instead of clobbering it.
  `spatial-compose` androidMain hard-depends on `spatial-engine-arcore`
  (`build.gradle.kts:43`) and instantiates `ARCoreEngine` directly
  (`SpatialSurface.android.kt:41`), so every Android consumer bundles ARCore whether
  they want it or not — contradicting the README's "choose your platform engine".
  Introduce an engine provider (parameter on `SpatialView` or a `CompositionLocal`)
  and ship the ARCore wiring as a small `spatial-compose-arcore` module (or a factory
  the app passes in).
- [x] **M5.3 Fix `SpatialView` restart semantics** (`SpatialView.kt:78-93`).
  `LaunchedEffect(mode, config)` calls `session.start()` again without stopping the
  previous session; `ARCoreEngine.initialize` then throws "already initialized" which
  surfaces as `onError`. Tear down (stop + reinit or dispose/recreate) before
  restarting; add support for re-initialization to the engines.
- [x] **M5.4 Remove or implement the fake animation APIs**
  Implemented via `animateValueAsState` with public `Vec3.VectorConverter`
  (component lerp) and `Quaternion.VectorConverter` (nlerp with renormalization);
  `animatedSpatialRotation` sign-aligns successive targets so interpolation takes
  the short arc. Converter unit tests added.
  (`SpatialState.kt:77-111`). `animatedSpatialVec3`/`animatedSpatialRotation` document
  smooth animation but return `rememberUpdatedState(target)`. Implement via
  `animateValueAsState` with a `TwoWayConverter` (Vec3 component lerp; Quaternion via
  slerp) — or delete them until implemented.
- [x] **M5.5 Document the DSL update model.**
  Documented as build-once + imperative mutation in the `SpatialView` KDoc
  ("Update model" section, with the node-reference/onFrame pattern) and in the
  README. True composition remains out of scope (tracked below).
  `SpatialView` rebuilds the scene only when the `content` lambda instance changes
  (`SpatialView.kt:60`); state read inside the DSL does not recompose the scene.
  Document that the DSL is build-once + imperative mutation (and show the
  `rememberSpatialNode` pattern), or redesign toward true composition later — but
  don't leave it ambiguous.
- [x] **M5.6 Make the README compile.**
  `Color.RED`/`Color.BLUE` → `Color.Red`/`Color.Blue`; `1.5.meters` works only via
  core's `Number` extensions (fine after M2.8); installation snippet per M1.6.
  README rewritten against the real API (verified signatures: `detectedPlanes`,
  `PlaneType.HORIZONTAL_UP`, `AnchorScope.cube(size=…)`, Android engine wiring
  via `LocalSpatialEngine`), stale P0-bug warnings removed, library/playground
  split documented (also closes the M0.6 minimum). Dedicated `samples/` apps
  deferred to the 1.0 line (see Versioning path) — `composeApp` is the demo
  meanwhile.

## M6 — Quality infrastructure (1–2 days to set up, ongoing after)

- [x] **M6.1 CI (GitHub Actions).**
  DONE: `.github/workflows/ci.yml` (build + check on macOS, PRs and main) and
  `release.yml` (publish to Maven Central on `v*` tags via the Central Portal).
  PR workflow: `./gradlew build` on macOS (covers JVM, JS, Android, iOS klib
  compilation), JS tests headless-Chrome only (M1.5). Release workflow: publish on tag.
- [x] **M6.2 Static analysis.** DONE: ktlint applied to every `spatial-*` module from
  the root build; `ktlintCheck` runs as part of `check`, so CI fails on violations.
- [x] **M6.3 API stability.** DONE: `binary-compatibility-validator` applied at the
  root; `api/*.api` dumps committed per module and `apiCheck` runs in CI.
- [x] **M6.4 Docs.** DONE: `.github/workflows/docs.yml` publishes aggregated Dokka
  HTML for all `spatial-*` modules to GitHub Pages on pushes to main.
- [x] **M6.5 Test debt.** DONE — all suites below exist in `commonTest`
  (SpatialNodeTest, SpatialSceneTest, SpatialAnchorTest, SpatialAnimatorTest,
  DslExampleTest/AnchorScopeTest, GltfLoaderTest, FallbackEngineTest):
  - `SpatialNode` hierarchy math (world transforms, cycles, reparenting) — covers M2.1–M2.3
  - `SpatialScene`/`toSceneData` snapshot content
  - `SpatialAnchor` state machine + multicast callbacks — covers M2.4
  - `SpatialAnimator` progress/easing/completion
  - DSL output shape (nodes, groups, anchors)
  - `GltfLoader` against a known-good embedded glTF fixture
  - `FallbackEngine` state transitions

---

## M7 — Textured 3D models (~1 month, current focus)

Target: **load a GLB file with a base-color texture and PBR factors, and render
it identically on Android, iOS, and Web.** This is the "furniture app" bar and
the gate for real-world use cases.

**Explicit non-goals for M7** (write them down so scope doesn't creep):
animations/skinning, morph targets, KHR extensions, Draco/compressed textures,
sparse accessors, USDZ. These come later, if at all.

### Week 1 — Contract + loader (ENGINE FIRST)

- [x] **M7.1 Add texture data to the engine contract.**
  `MaterialData.textureId` is a bare string today — no payload path exists. Add
  `TextureData` (image bytes, dimensions/format, sampler settings: wrap, filter)
  and a `baseColorTexture: TextureData?` reference on `MaterialData` (keep
  `textureId` semantics for renderer-side caching). Update `api/*.api` dumps.
- [x] **M7.2 Rewrite `GltfLoader` on `kotlinx.serialization.json`.**
  Replace the string-scanning parser. Support: glTF 2.0 JSON + GLB binary
  container, accessors with correct `byteOffset`/`byteStride`, UV extraction
  (TEXCOORD_0), base-color texture + `metallicRoughnessFactor`, node hierarchy
  → `SceneNode.Group` trees. Remove `@ExperimentalSpatialApi` once solid.
- [x] **M7.3 Test against synthetic glTF/GLB fixtures.**
  Embed a small fixture set (e.g. Box, BoxTextured, Duck) in `commonTest`;
  assert geometry counts, UVs, texture bytes, and material factors. Malformed
  input must produce a helpful error, not a crash.

### Week 2 — Renderers

- [x] **M7.4 WebXR texture support.** Map `TextureData` → Three.js `DataTexture`
  (or `TextureLoader` from blob), wire UVs into `BufferGeometry`, PBR factors →
  `MeshStandardMaterial.map/metalness/roughness`. Dispose textures in the
  M4.1 disposal paths. *(Done via `createImageBitmap` + refcounted
  `ThreeTextureCache`; `flipY=false`, sRGB color space.)*
- [x] **M7.5 ARCore texture support.** GLES texture upload + UV attributes and
  sampling in `SimpleRenderer`'s scene shader; cache textures by id; delete GL
  textures in `destroy()` and node-removal paths (extend the P0.1/P0.2-era
  VBO/resource caching — do not reintroduce per-frame allocations).
  *(Stale GPU resources evicted on the GL thread when the scene revision
  changes.)*
- [x] **M7.6 ARKit texture support.** `TextureData` → `UIImage`/`CGImage` →
  `SCNMaterial.diffuse.contents`; metallic/roughness factors on the
  physicallyBased lighting model. *(Base-color factor via the `multiply`
  slot; `Texcoord` geometry source for UVs; images cached by id.)*
- [x] **M7.7 Cross-platform parity check.** Same GLB renders with the same
  orientation, scale, and approximate color on all three renderers (winding
  order, sRGB vs linear, Y-up conventions documented in the contract).
  *(Conventions documented in `SceneData` KDoc; visual confirmation happens
  in M7.12.)*

### Week 3 — Surface it in the API

- [x] **M7.8 `ModelNode` + `model(...)` DSL builder.** Scene-graph node backed
  by loader output (a group of meshes), exposed in `SpatialSceneScope`,
  `GroupScope`, `AnchorScope`.
- [x] **M7.9 Asset loading.** Suspend `loadGltf(bytes: ByteArray)` in common;
  document per-platform byte sourcing (compose resources / URL fetch). Cache
  parsed models so N nodes of one GLB don't parse N times.
- [x] **M7.10 Error propagation.** Malformed model → `EngineError.RuntimeError`
  (or a dedicated `AssetError`) through `onError`, never a silent blank node.
  *(Added `EngineError.AssetError`; `SpatialView` catches `GltfParseException`
  from the content block.)*

### Week 4 — Prove it

- [x] **M7.11 Sample app.** A small "place a model" demo in `composeApp`
  (tap → hit test → anchor → textured model), running on all three platforms.
  *(Textured crate with embedded checkerboard PNG; tap a detected surface to
  place anchored copies.)*
- [ ] **M7.12 On-device validation.** Physical Android + iPhone + WebXR browser
  run-through; capture screenshots in the PR. (If device access is tight,
  front-load the iOS check into week 2–3 — ARKit has no simulator.)
  *Status (2026-07-06):*
  - *Android (physical device): ✅ validated — checkerboard texture renders,
    tap-to-bounce and surface placement work. Caught and fixed three engine
    bugs: sensor-oriented `camera.pose` breaking screen-space rays,
    ARFrame retention in the iOS raycast, and tap-ray FOV assumptions.*
  - *iOS (physical device): texture parity confirmed. Scene-content raycast
    (SceneKit segment hit test) + primitive scale rebuild implemented so
    bounce/placement match Android — **on-device retest deferred until
    after M8** (gestures will exercise the same paths properly).
    Deploy with `scripts/run-ios.sh`.*
  - *WebXR browser run-through: pending.*
- [x] **M7.13 Docs.** README + USAGE_EXAMPLES model example; DSL_GUIDE section;
  CHANGELOG entry.

## Future milestones (M8+, post-M7 order negotiable)

### M8 — Interaction (gestures) — code-complete

Table stakes for consumer AR: tap-to-select, drag along detected planes,
pinch-to-scale, two-finger rotate.

- [x] M8.1 Node picking: screen-point → node raycast.
  `FrameData.screenPointToRay()` (shared unprojection, honoring off-center
  projection terms) + `SpatialSession.raycastNodeAt()`. New common
  `SceneRaycaster` covers **all** geometry types (custom-mesh triangles via
  Möller–Trumbore, cylinder end caps) with group-transform accumulation; the
  ARCore renderer delegates to it. *Also fixed: the ARCore renderer dropped
  group transforms when rendering — nested content (ModelNode/GroupNode)
  rendered at its local transform only.*
- [x] M8.2 Gesture recognition in `spatial-compose`: tap + drag/pinch/twist
  detector on the rendering surface (`SpatialView(gesturesEnabled = …)`,
  default on), events queued thread-safely and applied on the render thread
  by `InteractionManager` before the app frame callback.
- [x] M8.3 Node-level API: `onTap`, `draggable(mode)`, `scalable(min, max)`,
  `rotatable(axis)` on `SpatialNode` (works inside DSL builder blocks;
  `GroupScope.node` exposes the group). Gestures resolve up the parent chain;
  drag follows detected surfaces with horizontal-plane fallback; pinch clamps
  against a per-node base scale; misses fire
  `session.interactions.onTapMissed`.
- [x] M8.4 Sample: place-and-arrange flow — tap to bounce, tap surface to
  place a crate, drag/pinch/twist to arrange. Placed crates are no longer
  world-anchored (an anchor's tracked pose would fight the drag; re-anchoring
  on drag end is a follow-up).
- [ ] M8.5 On-device validation (folds into the pending M7.12 pass): Android +
  iPhone + WebXR run-through of tap/drag/pinch/twist; verify the twist
  direction feels right (sign chosen so a clockwise twist turns the node
  clockwise seen from above) and that drag tracking across surfaces behaves.
  *Android (physical device): ✅ validated 2026-07-08 — tap-to-bounce on the
  flower (parent-chain resolution via a petal tap), tap-missed crate
  placement, drag, pinch (clamps at 0.3×–3×), twist direction correct, and
  tap still hits a crate after it was moved/scaled. Non-interactive nodes
  (flower/star) correctly ignore drag — interactivity is opt-in per node.*
  *iPhone + WebXR: deferred (no iOS device on hand as of 2026-07-08). Deploy
  with `scripts/run-ios.sh` when hardware is available; the WebXR leg can run
  in desktop Chrome with the WebXR emulator extension. All JVM tests pass and
  Android/iOS/JS targets compile.*

M8 follow-ups (deliberately out of scope): drag lifecycle callbacks
(`onDragStart`/`onDragEnd`, needed for re-anchoring dragged content),
long-press, two-finger vertical drag for height, per-node hit-test masks.

### M9 — AR realism

Placed objects currently "float". Ship the three highest-impact cues:

- [ ] M9.1 Light estimation: `FrameData.lightEstimate` already exists — feed it
  into renderer lighting on all three platforms.
- [ ] M9.2 Soft shadows: plane-projected shadow or blob shadow under anchored
  content (per-renderer).
- [ ] M9.3 Depth occlusion where supported (`supportsDepthOcclusion` is already
  in `EngineCapabilities`): ARCore Depth API, ARKit scene depth; document the
  capability check.

### M10 — Non-XR preview renderer

Biggest dev-loop improvement: see a scene without AR hardware.

- [ ] M10.1 `spatial-engine-preview`: camera-orbit 3D renderer implementing
  `SpatialEngine` (desktop JVM via OpenGL, and/or browser via Three.js without
  XR session).
- [ ] M10.2 `SpatialView` falls back to preview when no XR engine is available
  (replaces the render-nothing `FallbackEngine` path for development).

### M11 — 2D UI in space

The "Compose for spatial" pitch implies this.

- [ ] M11.1 Text/billboard nodes (always-face-camera flag on a quad + texture).
- [ ] M11.2 Compose-content-to-texture panel node (render a composable into a
  bitmap, map onto a plane; interaction later).

### M12 — Tracking & persistence

- [ ] M12.1 WebXR anchors (XRAnchor) + plane detection module — closes the
  README "Known Limitations".
- [ ] M12.2 Image tracking (ARCore Augmented Images / ARKit reference images)
  behind `supportsImageTracking`.
- [ ] M12.3 Anchor persistence across sessions (local world map / cloud anchors
  — scope after M8–M10).

### M13 — Real VR (or narrow the claim)

- [ ] M13.1 Per-view stereo `FrameData` (closes the M4.5 mono-only note) +
  WebXR two-view rendering.
- [ ] M13.2 Controller input surfaced through the DSL/Compose layer (the
  `InputSource.Controller` contract already exists).
- [ ] M13.3 If M13.1–2 don't get scheduled, change "AR/VR/Spatial" claims in
  README/docs to "AR (VR experimental)".

---

## Versioning path

The M0–M6 work is done but **no release has been cut yet** — everything still
sits at `0.1.0-SNAPSHOT` on Maven Local. The release workflow (publish on `v*`
tag) already exists; using it is the single highest-leverage remaining step.

- `0.1.0`: cut now from the M0–M6 state → first public artifacts on Maven
  Central, API unstable.
- `0.2.0`: after M7 → textured GLB models on all three platforms + sample app.
- `0.3.0`: after M8 (gestures) → API shape final-candidate.
- `1.0.0`: on-device validation on all platforms + samples + docs; no known
  contract bugs.

## Explicitly out of scope (tracked separately)

- Physics engine integration
- glTF animations/skinning, KHR extensions, Draco, USDZ (see M7 non-goals)
- Face/body tracking, people occlusion
- Multi-user shared sessions
- wasmJs targets for the `spatial-*` modules
- True composable scene graph (see M5.5 decision)
