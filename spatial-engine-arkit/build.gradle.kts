plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    // Enable default hierarchy for iosMain shared source set
    applyDefaultHierarchyTemplate()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SpatialEngineARKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api: createARKitEngine() returns SpatialEngine; ARKitEngine
            // imports spatial-core math types directly
            api(project(":spatial-engine"))
            api(project(":spatial-core"))
        }

        iosMain.dependencies {
            // ARKit itself is part of the iOS SDK. Coroutines are used
            // directly (ARKitEngine.state is a MutableStateFlow) — declared
            // here because metadata compilation does not see it transitively.
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
