plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.mavenPublish)
}

kotlin {
    explicitApi()

    js(IR) {
        browser()
    }

    sourceSets {
        jsMain.dependencies {
            // api: createWebXREngine() returns SpatialEngine
            api(project(":spatial-engine"))
            api(project(":spatial-core"))
            implementation(npm("three", "0.160.0"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
