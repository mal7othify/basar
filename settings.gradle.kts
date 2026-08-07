rootProject.name = "Basar"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
// }
//
// toolchainManagement {
//    jvm {
//        javaRepositories {
//            repository("foojay") {
//                resolverClass.set(org.gradle.toolchains.foojay.FoojayToolchainResolver::class.java)
//            }
//        }
//    }
// }

include(":composeApp")
include(":server")
include(":shared")
include(":spatial-core")
include(":spatial-engine")
include(":spatial-engine-arcore")
include(":spatial-engine-arkit")
include(":spatial-scene")
include(":spatial-dsl")
include(":spatial-compose")
include(":spatial-render")
include(":spatial-render-filament")
// v2 (Filament rewrite): spatial-engine-webxr and spatial-engine-preview are
// off the build — the web tier is revisited post-R8 (docs/V2_REALISM_REWRITE.md §3).
