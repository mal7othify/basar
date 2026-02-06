plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":spatial-engine"))
            implementation(project(":spatial-core"))

            // ARCore SDK
            implementation(libs.arcore)

            // Kotlin coroutines
            implementation(libs.kotlinx.coroutines.swing)

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
