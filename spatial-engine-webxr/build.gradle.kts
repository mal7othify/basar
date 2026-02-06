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
            implementation(project(":spatial-engine"))
            implementation(project(":spatial-core"))
            implementation(npm("three", "0.160.0"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
