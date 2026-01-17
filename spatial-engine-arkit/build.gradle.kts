plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    iosX64()
    iosArm64()
    iosSimulatorArm64()

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
