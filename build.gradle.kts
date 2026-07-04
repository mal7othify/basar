plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.binaryCompatibilityValidator)
    alias(libs.plugins.dokka)
}

// Track the public API of the library modules: `apiCheck` (part of `check`)
// fails on any signature change until it is intentionally recorded with
// `apiDump` (committed .api files under each module's /api directory).
apiValidation {
    ignoredProjects += listOf("composeApp", "server", "shared")
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // Documentation is generated for the published library modules only.
    if (name.startsWith("spatial-")) {
        apply(plugin = "org.jetbrains.dokka")
    }
}

// Aggregated HTML docs for all library modules: ./gradlew :dokkaGenerate
// (output in build/dokka/html).
dependencies {
    dokka(project(":spatial-core"))
    dokka(project(":spatial-engine"))
    dokka(project(":spatial-engine-arcore"))
    dokka(project(":spatial-engine-arkit"))
    dokka(project(":spatial-engine-webxr"))
    dokka(project(":spatial-scene"))
    dokka(project(":spatial-dsl"))
    dokka(project(":spatial-compose"))
}
