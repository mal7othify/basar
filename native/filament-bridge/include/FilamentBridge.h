// FilamentBridge — a minimal C surface over Filament (C++) for Kotlin/Native.
//
// Kotlin cannot cinterop C++ directly, so this header is deliberately plain C
// (plus UIKit types, which cinterop maps to platform.UIKit). The ObjC++
// implementation (src/FilamentBridge.mm) is compiled and merged with the
// prebuilt Filament static libraries into FilamentBridge.xcframework by
// scripts/build-filament-bridge.sh; the app's Xcode project links that
// framework (the Kotlin static framework carries these symbols unresolved).
//
// Threading: every function must be called on the main thread. Rendering is
// driven by the view's internal display link (MTKView), also on the main
// thread; Filament pipelines actual GPU work on its own backend thread.

#ifndef FILAMENT_BRIDGE_H
#define FILAMENT_BRIDGE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __OBJC__
#import <UIKit/UIKit.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif

/// Opaque renderer instance: one Filament engine + scene + view + swapchain.
typedef struct FbrEngine FbrEngine;

/// Model instance id. 0 is never a valid id (returned on load failure).
typedef int64_t FbrAssetId;

/// Creates a renderer with the R0 fixed rig: perspective camera, directional
/// sun, solid-color skybox. Returns NULL if the Metal backend can't start.
FbrEngine* _Nullable fbr_create(void);

/// Destroys everything created by this engine, including its view.
void fbr_destroy(FbrEngine* _Nonnull engine);

#ifdef __OBJC__
/// Returns the CAMetalLayer-backed view (an MTKView) rendering this engine's
/// scene, driving the frame loop with its internal display link. The engine
/// retains the view; callers must not outlive the engine with it. Call once.
UIView* _Nonnull fbr_make_view(FbrEngine* _Nonnull engine);
#endif

/// Loads a binary glTF, adds it to the scene (auto-fitted into a ~1.5 m cube
/// for the R0 fixed camera), and fully loads its resources synchronously.
/// Returns 0 on parse/load failure. The bytes are copied; the caller may free
/// them immediately.
FbrAssetId fbr_load_glb(FbrEngine* _Nonnull engine, const uint8_t* _Nonnull bytes, size_t len);

/// Number of animation clips baked into the asset.
int32_t fbr_animation_count(FbrEngine* _Nonnull engine, FbrAssetId asset);

/// Clip name (may be empty). The returned pointer is valid until the next
/// bridge call on this engine — copy it out immediately.
const char* _Nullable fbr_animation_name(FbrEngine* _Nonnull engine, FbrAssetId asset, int32_t index);

/// Clip duration in seconds.
float fbr_animation_duration(FbrEngine* _Nonnull engine, FbrAssetId asset, int32_t index);

/// Declaratively sets the asset's animation; the render loop advances time.
void fbr_set_animation(
    FbrEngine* _Nonnull engine,
    FbrAssetId asset,
    int32_t clip,
    bool playing,
    bool loop,
    float speed,
    float timeOffset);

/// Stops all animation on the asset.
void fbr_clear_animation(FbrEngine* _Nonnull engine, FbrAssetId asset);

/// Sets the asset's root transform, column-major 4x4 (right-handed, +Y up,
/// -Z forward, meters).
void fbr_set_transform(FbrEngine* _Nonnull engine, FbrAssetId asset, const float* _Nonnull matrix16);

/// Removes the asset from the scene and frees its resources.
void fbr_destroy_asset(FbrEngine* _Nonnull engine, FbrAssetId asset);

#ifdef __cplusplus
}
#endif

#endif  // FILAMENT_BRIDGE_H
