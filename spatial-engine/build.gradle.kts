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

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // api: the engine contract exposes spatial-core types
            // (Math.kt typealiases, Transform in SceneData, Vec3/Quaternion in Pose)
            api(project(":spatial-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
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
