import kotlin.jvm.java

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
include(":spatial-engine-webxr")
include(":spatial-engine-arcore")
include(":spatial-engine-arkit")
include(":spatial-scene")
include(":spatial-dsl")
include(":spatial-compose")
include(":spatial-renderer-canvas")

// Samples
include(":samples:webxr-test")
include(":samples:compose-webxr-example")
include(":samples:compose-arcore-example")
