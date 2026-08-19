import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    // Enable default hierarchy for shared source sets (iosMain, etc.)
    applyDefaultHierarchyTemplate()

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm()

    // js target dropped on the v2-realism branch: the web tier is parked
    // (webxr/preview modules are out of settings.gradle.kts) and the scene
    // payload types now live in :spatial-render, which has no js target.
    // Revisit post-R8 per docs/V2_REALISM_REWRITE.md.

    sourceSets {
        commonMain.dependencies {
            // api: the engine contract exposes spatial-core types
            // (Math.kt typealiases, Transform in SceneData, Vec3/Quaternion in Pose)
            api(project(":spatial-core"))
            // api: the scene payload types (SceneData, GeometryData, MaterialData,
            // AnchorRequest, ...) moved to :spatial-render in R3; this module
            // re-exports them as typealiases for v1 source compatibility.
            api(project(":spatial-render"))
            // api: SpatialEngine.state is a StateFlow
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // NOTE: Platform-specific engine modules (arcore, arkit, webxr) are NOT
        // dependencies here to avoid circular dependencies. Those modules depend
        // on spatial-engine, not the other way around.
        //
        // Usage pattern:
        // - Android: depend on :spatial-engine-arcore, use createARCoreEngine(context)
        // - iOS: depend on :spatial-engine-arkit, use createARKitEngine()
        // - JS: depend on :spatial-engine-webxr, use createWebXREngine()
        //
        // The SpatialEngineFactory.create() returns FallbackEngine unless you're
        // using the platform-specific modules which override the behavior.
    }
}

android {
    namespace = "com.spatialkit.engine"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
}
