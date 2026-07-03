plugins {
    alias(libs.plugins.kotlinMultiplatform)
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

    sourceSets {
        androidMain.dependencies {
            // api: createARCoreEngine() returns SpatialEngine; ARCoreEngine
            // signatures expose engine + core types
            api(project(":spatial-engine"))
            api(project(":spatial-core"))

            // ARCore SDK
            implementation(libs.arcore)

            // Kotlin coroutines (Android Main dispatcher)
            implementation(libs.kotlinx.coroutines.android)

            // AndroidX
            implementation(libs.androidx.core.ktx)
        }
    }
}

android {
    namespace = "com.spatialkit.engine.arcore"
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
