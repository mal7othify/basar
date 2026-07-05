
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
            // Compose dependencies (runtime/ui appear in the SpatialView signature,
            // animation-core types in the animatedSpatial* signatures)
            api(compose.runtime)
            api(compose.ui)
            api(compose.animation)
            implementation(compose.foundation)

            // api: SpatialView exposes SpatialSceneScope, SessionMode, EngineConfig,
            // SpatialSession, FrameData, EngineError
            api(project(":spatial-dsl"))
            api(project(":spatial-scene"))
            api(project(":spatial-engine"))
            api(project(":spatial-core"))
        }

        // No Android engine dependency on purpose: ARCore needs a Context the
        // library can't supply, and bundling it would force it on every consumer.
        // Apps provide an engine via SpatialView's `engine` parameter or
        // LocalSpatialEngine (see spatial-engine-arcore).

        iosMain.dependencies {
            // ARKit engine for iOS
            implementation(project(":spatial-engine-arkit"))
        }

        jsMain.dependencies {
            // WebXR engine for JavaScript/Browser
            implementation(project(":spatial-engine-webxr"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.spatialkit.compose"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
