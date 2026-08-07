plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
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

    // v2: js target removed with the web tier (revisit post-R8).

    sourceSets {
        commonMain.dependencies {
            // api: SpatialNode/SpatialSession expose engine + core types
            // (Vec3, Quaternion, FrameData, SpatialRuntime, ...)
            api(project(":spatial-engine"))
            api(project(":spatial-core"))

            // v2 runtime composition: TrackingProvider + SceneRenderer
            api(project(":spatial-tracking"))
            api(project(":spatial-render"))

            // glTF parsing (GltfLoader); internal only
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.spatialkit.scene"
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
