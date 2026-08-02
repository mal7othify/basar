# Basar v2 — Realism-First Rewrite Plan

**Status: design draft — not scheduled.** This documents how we would rebuild
Basar if realism (Pokémon GO-class outdoor AR) became the top priority,
while keeping the Kotlin Multiplatform identity and the Compose-like API.
v1 (M0–M12, this repo) stays the shipping library until a v2 decision is made.

**The buy-vs-build check comes first:** if the goal is shipping a
Pokémon GO-like *product* rather than a Kotlin *library*, the honest answer
is Unity + AR Foundation + Niantic Lightship — that stack ships today with
skinned characters, ML occlusion on non-LiDAR phones, VPS and the map layer
built in. Everything below is the plan for the *library* path: realism-first
**Basar**, in Kotlin.

---

## 1. What v1 taught us

1. **"ENGINE FIRST" was right, but `SpatialEngine` merges two contracts.**
   Tracking (where is the camera, what surfaces exist) and rendering (draw
   the scene) are different jobs. Merging them forced **three full scene
   renderers** (GLES/Android, SceneKit/iOS, Three.js/web), so realism is
   capped by the weakest backend — SceneKit can't even composite depth for
   occlusion, and every visual feature (shadows, light estimation, textured
   models) was built three times.
2. **The top half of the stack is renderer-agnostic and good.** spatial-core
   math, the scene graph, the DSL, the interaction manager, SceneRaycaster,
   and the Compose integration pattern survive a rewrite nearly untouched.
3. **The update model works.** Build the scene once, mutate imperatively
   from `onFrame`; structural ops synchronized. Keep it.
4. **Honest capability reporting works.** `supportsX = false` beats a broken
   fake. Keep it.
5. **Validate on devices per milestone, not in a deferred mega-pass.** The
   iOS "interop view swallows all touches" bug (fixed in `547ae24`) sat
   invisible through five code-complete milestones. v2 milestones each carry
   their own device-validation acceptance criteria.

## 2. North star

A creature standing in a park: **PBR-lit to match the sky, idle-animating,
hiding behind a real tree (occlusion), casting a real shadow, anchored to a
GPS+VPS location that another phone resolves to the same spot.**
Every stack choice below serves that sentence.

## 3. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Rendering | **Filament** — the single renderer on Android *and* iOS (C++; Metal/Vulkan/GLES backends) | One physically-based renderer written once: IBL, clustered lighting, shadow maps, post-processing, and `gltfio` for animated/skinned glTF. Kills the three-renderer tax |
| Tracking | **ARCore + ARKit as tracking-only adapters** — pose, planes, meshes, depth, light probes, anchors. Zero rendering responsibility | Shrinks the per-platform surface to a data feed; visual parity becomes automatic |
| Geo layer | **ARCore Geospatial API** (also on iOS via ARCore SDK for iOS) — Earth anchors, VPS, Streetscape geometry | The heart of a Pokémon GO-like: persistent, shared, geolocated content. v1's M12.3 handle-based persistence API extends to it naturally |
| Shared logic | **Kotlin Multiplatform** — scene graph, DSL, gameplay state, networking (Ktor) | The part of v1 worth keeping is exactly this layer |
| 2D UI | **Compose Multiplatform** — map screen, menus, HUD over the AR view | Same pattern as v1 (with the interop-touch lesson applied) |
| Map screen | MapLibre (or Google Maps) — app-level, not part of `spatial-*` | The map is a product concern; the library ends at geospatial anchors |
| Web | **Demoted to dev preview** (keep v1's Three.js preview or Filament WASM later) | WebXR can't do VPS/geospatial; don't let it drag the realism ceiling again |

## 4. The core architectural change: split the engine contract

```
v1:  SpatialEngine  =  tracking + rendering + hit-testing   (×3 implementations)

v2:  TrackingProvider (per platform, thin)     SceneRenderer (ONE: Filament)
     ─ ARCoreTracking / ARKitTracking          ─ submitScene(SceneData)
     ─ start/stop/config                       ─ render(TrackingFrame, target)
     ─ TrackingFrame: camera pose, view/proj,  ─ loadModel(glb) → handles +
       planes, meshes, depth image, light        Animator
       estimate, anchors, geo pose             ─ IBL from light probes
     ─ anchors: create / persist / resolve     ─ depth-aware compositing
     ─ world hit-test
             └──────────── SpatialRuntime composes the two ────────────┘
                 + camera-background compositor (per-platform image path)
```

Sketch of the contracts (namespaces indicative):

```kotlin
interface TrackingProvider {
    val capabilities: TrackingCapabilities   // honest, like v1
    suspend fun start(config: TrackingConfig)
    fun stop()
    fun onFrame(callback: (TrackingFrame) -> Unit)   // no scene knowledge
    fun hitTest(ray: Ray): WorldHit?                  // real-world only
    suspend fun persistAnchor(id: String): String?    // v1 M12.3 shape
    fun resolveAnchor(handle: String)                 // incl. geospatial
}

interface SceneRenderer {                             // one impl: Filament
    fun submitScene(scene: SceneData)                 // v1 contract survives
    fun render(frame: TrackingFrame)
    suspend fun loadModel(glb: ByteArray): ModelHandle // with Animator
}
```

Scene-content raycasts (`SceneRaycaster`) stay CPU-side and shared, exactly
as in v1 — node picking must not depend on the renderer.

## 5. Module layout

| v2 module | Origin | Notes |
|---|---|---|
| `spatial-core` | **survives as-is** | math, types |
| `spatial-scene` | survives + grows | scene graph, interaction manager; add animation state (play/loop/blend per model node) |
| `spatial-dsl` | survives + grows | add `animation { }` controls on `model()` |
| `spatial-tracking` | new (from half of `spatial-engine`) | the `TrackingProvider` contract + `TrackingFrame` types |
| `spatial-tracking-arcore` | rewrite of `spatial-engine-arcore`, minus rendering | also hosts the Geospatial API surface |
| `spatial-tracking-arkit` | rewrite of `spatial-engine-arkit`, minus SceneKit | ARKit feed only |
| `spatial-render-filament` | **new — the big build** | KMP wrapper over Filament: JNI on Android, cinterop → ObjC++ XCFramework on iOS; gltfio, IBL, shadows, depth compositing, camera background |
| `spatial-geo` | new | geospatial anchor model on top of `TrackingProvider` persistence |
| `spatial-compose` | survives, new surface hosting | Android: `SurfaceView` + Filament swapchain; iOS: `CAMetalLayer`-backed view as a **non-interactive** interop view (the `547ae24` lesson, baked in) |
| `spatial-engine-webxr` / `-preview` | v1 code kept as the dev-preview path | not part of the realism target |

Dead in v2: the SceneKit renderer, the Android GLES renderer, Three.js as a
*product* renderer (survives only as the browser preview).

## 6. The realism feature set (why v2 exists)

- **PBR + image-based lighting**: Filament IBL fed by ARKit environment
  probes / ARCore's HDR cubemap estimate — objects pick up the actual sky
  and surroundings, not one ambient scalar (v1 M9's ceiling).
- **Animated glTF** via `gltfio`'s Animator — skinning + morph targets.
  Explicitly out of scope in v1; non-negotiable for creatures.
- **Occlusion everywhere feasible**: depth texture (ARCore Depth API, LiDAR
  on iOS) sampled in the material for per-pixel occlusion. Stretch goal:
  ML monocular depth for non-depth devices (this is Lightship's moat —
  scope honestly, ship without it first).
- **Real shadows**: directional-light shadow maps onto detected planes
  (contact-hardening where cheap), replacing v1's blob discs.
- **Camera background through Filament**: external OES texture (Android) /
  `CVPixelBuffer` → Metal texture (iOS), composited in the same pipeline so
  tone mapping applies to virtual content against the real image.

## 7. Milestones

Each milestone ends with an **on-device checkpoint on both platforms** — no
deferred validation debt this time.

- **R0 — Renderer spike (de-risk the whole plan).** Filament renders one
  animated PBR GLB in a plain (non-AR) view on Android + iOS, driven from
  common Kotlin. Proves the JNI/cinterop + build packaging story. *If R0
  fails or balloons, revisit buy-vs-build before writing anything else.*
- **R1 — Tracking contract + ARCore adapter.** Camera background, pose →
  Filament camera, planes surfaced. A cube sits on the floor with correct
  perspective.
- **R2 — ARKit adapter.** Same scene, same code, iPhone.
- **R3 — Scene graph + DSL port.** v1's `spatialScene {}` and Compose
  `SpatialView` run on the new runtime; keep source compatibility for the
  DSL layer where cheap.
- **R4 — Realism I: lighting + animation.** IBL from platform light
  estimates; glTF animations playing via DSL controls.
- **R5 — Realism II: occlusion + shadow maps.** Depth-aware compositing on
  supported devices; honest capability elsewhere.
- **R6 — Interaction port.** InteractionManager + SceneRaycaster on the new
  frame loop (mostly a move, not a rewrite). Gesture parity per v1 M8.
- **R7 — Geospatial.** Earth anchors + VPS resolve/persist through the
  M12.3-style handle API; two-device same-spot demo.
- **R8 — "Creatures in the park" sample + perf/battery pass.** The north
  star scene as the demo app's fifth screen; frame-time and thermal budget
  measured, not guessed.

## 8. Risks

| Risk | Reality check |
|---|---|
| Filament-on-iOS from Kotlin (cinterop → ObjC++ wrapper, XCFramework packaging) | The single biggest engineering unknown → that's why it is R0, not R4 |
| Occlusion coverage without ML depth | ARCore Depth + LiDAR only at first; a visible gap vs Lightship — say so in docs |
| Battery/thermal (camera + VPS + renderer) | Budget in R8; Pokémon GO's real constraint is thermals, not FPS |
| Web regression | Deliberate: web = preview tier. Revisit only if Filament WASM becomes cheap |
| Two codebases during transition | v1 stays released and untouched; v2 is additive modules until R3 reaches API parity |
| Geospatial API is a Google Cloud dependency (quota, keys, ToS) | Isolate in `spatial-geo` so the core library never requires it |

## 9. Open questions

1. v2 in this repo (new modules, shared `spatial-core`) or a fresh repo with
   v1 as a dependency for the surviving layers?
2. Minimum OS/device floor (Filament wants GLES 3.0+/Metal — effectively
   drops very old Androids; fine?).
3. Do we preserve v1's `SpatialEngine` as a compatibility shim over
   `TrackingProvider + SceneRenderer`, or break at 0.x and migrate the docs?
4. ML depth: buy (a vendor SDK), build (small monocular-depth model), or
   skip until hardware depth coverage grows?

---

*Prepared 2026-08-02, from the v1 state at M12 (commit `547ae24`).*
