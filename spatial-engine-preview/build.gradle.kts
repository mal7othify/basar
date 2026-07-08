plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    // jvm() carries no renderer — it exists so the pure-Kotlin camera model
    // in commonMain gets fast unit tests and a tracked API surface.
    jvm()

    js(IR) {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            // api: OrbitCameraModel exposes engine math types (Vec3, Pose)
            api(project(":spatial-engine"))
            api(project(":spatial-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            // Reuses ThreeSceneRenderer, shared with the WebXR engine
            implementation(project(":spatial-engine-webxr"))
            implementation(npm("three", "0.160.0"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
