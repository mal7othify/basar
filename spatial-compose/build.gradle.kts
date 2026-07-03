
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
            // Compose dependencies (runtime/ui appear in the SpatialView signature)
            api(compose.runtime)
            api(compose.ui)
            implementation(compose.foundation)

            // api: SpatialView exposes SpatialSceneScope, SessionMode, EngineConfig,
            // SpatialSession, FrameData, EngineError
            api(project(":spatial-dsl"))
            api(project(":spatial-scene"))
            api(project(":spatial-engine"))
            api(project(":spatial-core"))
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // ARCore engine for Android
            implementation(project(":spatial-engine-arcore"))
        }

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
