plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // Enable default hierarchy for iosMain shared source set
    applyDefaultHierarchyTemplate()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SpatialEngineARKit"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":spatial-engine"))
//            implementation(project(":spatial-core"))
        }

        iosMain.dependencies {
            // ARKit is part of iOS SDK, no external dependencies needed
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
