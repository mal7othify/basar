
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

    // v2: js target removed with the WebXR/preview engines (revisit post-R8).

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

        // v1 note: no Android *engine* dependency on purpose — apps provide
        // one via SpatialView's `engine` parameter or LocalSpatialEngine.
        // v2 (SpatialView2) is deliberately opinionated instead: one
        // Filament renderer + the platform tracking adapter, created from
        // the composition's Context.
        androidMain.dependencies {
            implementation(project(":spatial-render-filament"))
            implementation(project(":spatial-tracking-arcore"))
        }

        iosMain.dependencies {
            // ARKit engine for iOS
            implementation(project(":spatial-engine-arkit"))
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
