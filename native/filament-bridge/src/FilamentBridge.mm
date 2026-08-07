// FilamentBridge implementation: ObjC++ over the prebuilt Filament static
// libraries. See FilamentBridge.h for the contract; built by
// scripts/build-filament-bridge.sh (ARC, no exceptions, no RTTI — matching
// Filament's own build flags).

#import "FilamentBridge.h"

#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>
#import <QuartzCore/CAMetalLayer.h>

#include <cstring>
#include <string>
#include <unordered_map>

#include <filament/Box.h>
#include <filament/Camera.h>
#include <filament/Engine.h>
#include <filament/LightManager.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/Skybox.h>
#include <filament/SwapChain.h>
#include <filament/TransformManager.h>
#include <filament/View.h>
#include <filament/Viewport.h>

#include <gltfio/Animator.h>
#include <gltfio/AssetLoader.h>
#include <gltfio/FilamentAsset.h>
#include <gltfio/FilamentInstance.h>
#include <gltfio/MaterialProvider.h>
#include <gltfio/ResourceLoader.h>
#include <gltfio/TextureProvider.h>
#include <gltfio/materials/uberarchive.h>

#include <math/mat4.h>
#include <math/vec3.h>
#include <math/vec4.h>

#include <utils/Entity.h>
#include <utils/EntityManager.h>

using namespace filament;
using namespace filament::gltfio;
using namespace filament::math;
using namespace utils;

@class FbrViewDelegate;

namespace {

struct FbrAsset {
    FilamentAsset* asset = nullptr;
    // Declarative animation state; advanced by the render loop.
    bool hasAnimation = false;
    int32_t clip = 0;
    bool playing = false;
    bool loop = true;
    float speed = 1.0f;
    float timeOffset = 0.0f;
    double startTime = -1.0;  // CACurrentMediaTime() at state change
};

}  // namespace

struct FbrEngine {
    Engine* engine = nullptr;
    Renderer* renderer = nullptr;
    Scene* scene = nullptr;
    View* view = nullptr;
    Camera* camera = nullptr;
    Entity cameraEntity;
    Entity sunEntity;
    Skybox* skybox = nullptr;
    SwapChain* swapChain = nullptr;

    MaterialProvider* materials = nullptr;
    TextureProvider* stbProvider = nullptr;
    TextureProvider* ktx2Provider = nullptr;
    AssetLoader* assetLoader = nullptr;
    ResourceLoader* resourceLoader = nullptr;

    std::unordered_map<int64_t, FbrAsset> assets;
    int64_t nextId = 1;
    std::string nameScratch;

    // ObjC strong refs (ARC-managed inside a C++ struct under ObjC++).
    MTKView* mtkView = nil;
    FbrViewDelegate* viewDelegate = nil;
};

static void fbrRenderFrame(FbrEngine* e);
static void fbrResize(FbrEngine* e, CGSize drawableSize);

@interface FbrViewDelegate : NSObject <MTKViewDelegate>
@property(nonatomic, assign) FbrEngine* bridge;
@end

@implementation FbrViewDelegate
- (void)mtkView:(nonnull MTKView*)view drawableSizeWillChange:(CGSize)size {
    if (self.bridge != nullptr) {
        fbrResize(self.bridge, size);
    }
}

- (void)drawInMTKView:(nonnull MTKView*)view {
    if (self.bridge != nullptr) {
        fbrRenderFrame(self.bridge);
    }
}
@end

FbrEngine* fbr_create(void) {
    Engine* engine = Engine::create(Engine::Backend::METAL);
    if (engine == nullptr) {
        return nullptr;
    }

    auto* e = new FbrEngine();
    e->engine = engine;
    e->renderer = engine->createRenderer();
    e->scene = engine->createScene();
    e->view = engine->createView();

    e->cameraEntity = EntityManager::get().create();
    e->camera = engine->createCamera(e->cameraEntity);
    // Physical exposure defaults (sunny-16), matching the Android renderer.
    e->camera->setExposure(16.0f, 1.0f / 125.0f, 100.0f);
    e->camera->lookAt(double3{0.0, 0.9, 2.6}, double3{0.0, 0.4, 0.0}, double3{0.0, 1.0, 0.0});

    e->skybox = Skybox::Builder().color(float4{0.035f, 0.037f, 0.045f, 1.0f}).build(*engine);
    e->scene->setSkybox(e->skybox);

    e->sunEntity = EntityManager::get().create();
    // castShadows stays OFF at R0: the iOS *simulator* GPU (MTLGPUFamilyApple2)
    // lacks sample-comparison support, so PCF shadow sampling reads as fully
    // shadowed and the whole model renders black. Real shadow maps are an R5
    // deliverable, enabled per-device capability there.
    LightManager::Builder(LightManager::Type::DIRECTIONAL)
        .color(float3{1.0f, 0.98f, 0.94f})
        .intensity(110'000.0f)
        .direction(float3{-0.28f, -0.78f, -0.56f})
        .castShadows(false)
        .build(*engine, e->sunEntity);
    e->scene->addEntity(e->sunEntity);

    e->view->setScene(e->scene);
    e->view->setCamera(e->camera);

    e->materials =
        createUbershaderProvider(engine, UBERARCHIVE_DEFAULT_DATA, UBERARCHIVE_DEFAULT_SIZE);
    e->assetLoader = AssetLoader::create({engine, e->materials, nullptr});
    e->resourceLoader = new ResourceLoader({engine, nullptr, true});
    e->stbProvider = createStbProvider(engine);
    e->ktx2Provider = createKtx2Provider(engine);
    e->resourceLoader->addTextureProvider("image/png", e->stbProvider);
    e->resourceLoader->addTextureProvider("image/jpeg", e->stbProvider);
    e->resourceLoader->addTextureProvider("image/ktx2", e->ktx2Provider);

    return e;
}

UIView* fbr_make_view(FbrEngine* e) {
    MTKView* view = [[MTKView alloc] initWithFrame:CGRectMake(0, 0, 1, 1)];
    view.device = MTLCreateSystemDefaultDevice();
    view.colorPixelFormat = MTLPixelFormatBGRA8Unorm;
    view.preferredFramesPerSecond = 60;

    FbrViewDelegate* delegate = [FbrViewDelegate new];
    delegate.bridge = e;
    view.delegate = delegate;  // MTKView.delegate is weak — retain below

    e->mtkView = view;
    e->viewDelegate = delegate;
    e->swapChain = e->engine->createSwapChain((__bridge void*)view.layer);
    return view;
}

FbrAssetId fbr_load_glb(FbrEngine* e, const uint8_t* bytes, size_t len) {
    FilamentAsset* asset = e->assetLoader->createAsset(bytes, (uint32_t)len);
    if (asset == nullptr) {
        return 0;
    }
    // Synchronous load, mirroring the Android R0 renderer; async streaming
    // replaces this in R3.
    e->resourceLoader->loadResources(asset);
    asset->releaseSourceData();
    e->scene->addEntities(asset->getEntities(), asset->getEntityCount());

    // Auto-fit into a ~1.5m cube slightly above the origin so any model shows
    // up in the fixed spike camera (Fox.glb is ~100 units tall).
    const Aabb box = asset->getBoundingBox();
    const float3 center = box.center();
    const float3 halfExtent = box.extent();
    const float maxExtent = 2.0f * std::max(halfExtent.x, std::max(halfExtent.y, halfExtent.z));
    if (maxExtent > 0.0f) {
        const float s = 1.5f / maxExtent;
        const float3 t = float3{0.0f, 0.4f, 0.0f} - center * s;
        const mat4f fit{float4{s, 0, 0, 0}, float4{0, s, 0, 0}, float4{0, 0, s, 0},
                        float4{t.x, t.y, t.z, 1}};
        auto& tm = e->engine->getTransformManager();
        tm.setTransform(tm.getInstance(asset->getRoot()), fit);
    }

    const int64_t id = e->nextId++;
    FbrAsset record;
    record.asset = asset;
    e->assets[id] = record;
    return id;
}

static FbrAsset* fbrFind(FbrEngine* e, FbrAssetId id) {
    auto it = e->assets.find(id);
    return it == e->assets.end() ? nullptr : &it->second;
}

static Animator* fbrAnimator(FbrEngine* e, FbrAssetId id) {
    FbrAsset* a = fbrFind(e, id);
    return a == nullptr ? nullptr : a->asset->getInstance()->getAnimator();
}

int32_t fbr_animation_count(FbrEngine* e, FbrAssetId asset) {
    Animator* animator = fbrAnimator(e, asset);
    return animator == nullptr ? 0 : (int32_t)animator->getAnimationCount();
}

const char* fbr_animation_name(FbrEngine* e, FbrAssetId asset, int32_t index) {
    Animator* animator = fbrAnimator(e, asset);
    if (animator == nullptr || index < 0 || (size_t)index >= animator->getAnimationCount()) {
        return nullptr;
    }
    const char* name = animator->getAnimationName((size_t)index);
    e->nameScratch = name == nullptr ? "" : name;
    return e->nameScratch.c_str();
}

float fbr_animation_duration(FbrEngine* e, FbrAssetId asset, int32_t index) {
    Animator* animator = fbrAnimator(e, asset);
    if (animator == nullptr || index < 0 || (size_t)index >= animator->getAnimationCount()) {
        return 0.0f;
    }
    return animator->getAnimationDuration((size_t)index);
}

void fbr_set_animation(FbrEngine* e, FbrAssetId asset, int32_t clip, bool playing, bool loop,
                       float speed, float timeOffset) {
    FbrAsset* a = fbrFind(e, asset);
    if (a == nullptr) {
        return;
    }
    a->hasAnimation = true;
    a->clip = clip;
    a->playing = playing;
    a->loop = loop;
    a->speed = speed;
    a->timeOffset = timeOffset;
    a->startTime = -1.0;
}

void fbr_clear_animation(FbrEngine* e, FbrAssetId asset) {
    FbrAsset* a = fbrFind(e, asset);
    if (a != nullptr) {
        a->hasAnimation = false;
    }
}

void fbr_set_transform(FbrEngine* e, FbrAssetId asset, const float* matrix16) {
    FbrAsset* a = fbrFind(e, asset);
    if (a == nullptr) {
        return;
    }
    mat4f m;
    std::memcpy(&m, matrix16, sizeof(mat4f));
    auto& tm = e->engine->getTransformManager();
    tm.setTransform(tm.getInstance(a->asset->getRoot()), m);
}

void fbr_destroy_asset(FbrEngine* e, FbrAssetId asset) {
    auto it = e->assets.find(asset);
    if (it == e->assets.end()) {
        return;
    }
    e->scene->removeEntities(it->second.asset->getEntities(), it->second.asset->getEntityCount());
    e->assetLoader->destroyAsset(it->second.asset);
    e->assets.erase(it);
}

static void fbrAdvanceAnimations(FbrEngine* e, double now) {
    for (auto& [id, a] : e->assets) {
        if (!a.hasAnimation || !a.playing) {
            continue;
        }
        Animator* animator = a.asset->getInstance()->getAnimator();
        if (a.clip < 0 || (size_t)a.clip >= animator->getAnimationCount()) {
            continue;
        }
        if (a.startTime < 0.0) {
            a.startTime = now;
        }
        const float duration = animator->getAnimationDuration((size_t)a.clip);
        float t = (float)((now - a.startTime) * a.speed) + a.timeOffset;
        if (duration > 0.0f) {
            t = a.loop ? std::fmod(t, duration) : std::min(t, duration);
        } else {
            t = 0.0f;
        }
        animator->applyAnimation((size_t)a.clip, t);
        animator->updateBoneMatrices();
    }
}

static void fbrRenderFrame(FbrEngine* e) {
    fbrAdvanceAnimations(e, CACurrentMediaTime());
    if (e->swapChain == nullptr) {
        return;
    }
    if (e->renderer->beginFrame(e->swapChain)) {
        e->renderer->render(e->view);
        e->renderer->endFrame();
    }
}

static void fbrResize(FbrEngine* e, CGSize drawableSize) {
    const uint32_t w = (uint32_t)drawableSize.width;
    const uint32_t h = (uint32_t)drawableSize.height;
    if (w == 0 || h == 0) {
        return;
    }
    e->view->setViewport(Viewport{0, 0, w, h});
    const double aspect = (double)w / (double)h;
    e->camera->setProjection(45.0, aspect, 0.05, 100.0, Camera::Fov::VERTICAL);
}

void fbr_destroy(FbrEngine* e) {
    if (e == nullptr) {
        return;
    }
    e->mtkView.delegate = nil;
    e->viewDelegate.bridge = nullptr;
    [e->mtkView removeFromSuperview];

    for (auto& [id, a] : e->assets) {
        e->scene->removeEntities(a.asset->getEntities(), a.asset->getEntityCount());
        e->assetLoader->destroyAsset(a.asset);
    }
    e->assets.clear();

    delete e->resourceLoader;
    e->materials->destroyMaterials();
    delete e->materials;
    delete e->stbProvider;
    delete e->ktx2Provider;
    AssetLoader::destroy(&e->assetLoader);

    if (e->swapChain != nullptr) {
        e->engine->destroy(e->swapChain);
        e->engine->flushAndWait();
    }
    e->engine->destroy(e->sunEntity);
    e->engine->destroy(e->skybox);
    e->engine->destroy(e->renderer);
    e->engine->destroy(e->view);
    e->engine->destroy(e->scene);
    e->engine->destroyCameraComponent(e->cameraEntity);
    EntityManager::get().destroy(e->cameraEntity);
    EntityManager::get().destroy(e->sunEntity);
    Engine::destroy(&e->engine);

    delete e;
}
