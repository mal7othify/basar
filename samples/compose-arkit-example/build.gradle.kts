plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeARKitExample"
            isStatic = true
        }
    }

    sourceSets {
        iosMain.dependencies {
            // Compose
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            // Spatial libraries
            implementation(project(":spatial-compose"))
            implementation(project(":spatial-dsl"))
            implementation(project(":spatial-scene"))
            implementation(project(":spatial-engine"))
            implementation(project(":spatial-engine-arkit"))
            implementation(project(":spatial-core"))
        }
    }
}
