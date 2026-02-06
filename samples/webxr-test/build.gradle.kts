plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(project(":spatial-core"))
            implementation(project(":spatial-engine"))
            implementation(project(":spatial-engine-webxr"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
