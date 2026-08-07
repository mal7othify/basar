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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.compilations.getByName("main").cinterops {
            // Header-only interop: Kotlin compiles against the checked-in C
            // header. The actual FilamentBridge.xcframework (built by
            // scripts/build-filament-bridge.sh) is linked by the app's Xcode
            // project — the Kotlin static framework carries unresolved
            // symbols by design.
            create("filamentbridge") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/filamentbridge.def"))
                includeDirs(rootProject.file("native/filament-bridge/include"))
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api: FilamentRenderer implements/returns spatial-render types
            api(project(":spatial-render"))
            api(project(":spatial-core"))

            implementation(libs.kotlinx.coroutines.core)
        }

        androidMain.dependencies {
            api(libs.filament.android)
            api(libs.filament.gltfio)
            implementation(libs.filament.utils)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

android {
    namespace = "com.spatialkit.render.filament"
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
