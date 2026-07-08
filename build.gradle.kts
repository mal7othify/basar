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

// ── Kotlin/JS npm toolchain security pins ───────────────────────────────
// Clears the Dependabot alerts on kotlin-js-store/package-lock.json. All of
// these are build/dev-time tooling (webpack, karma, dev server) — none ship
// in published artifacts or the web bundle — but the lockfile is committed,
// so GitHub scans it. Transitive packages are pinned via npm `overrides`;
// webpack/dev-server are direct KGP-managed deps, so their versions are set
// on the toolchain itself. After changing anything here, delete
// kotlin-js-store/package-lock.json, build/js/package-lock.json and
// build/js/node_modules, then run `./gradlew kotlinUpgradePackageLock` —
// npm does not re-resolve an existing lock against changed overrides.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().versions.apply {
        webpack.version = "5.104.1"
        webpackDevServer.version = "5.2.5"
    }
    the<org.jetbrains.kotlin.gradle.targets.js.npm.NpmExtension>().apply {
        override("shell-quote") { include("1.8.4") } // critical: newline escape
        override("ws") { include("8.21.0") }
        override("tmp") { include("0.2.7") }
        override("lodash") { include("4.18.0") }
        override("minimatch@^3.0.0") { include("3.1.4") }
        override("minimatch@^9.0.0") { include("9.0.7") }
        override("node-forge") { include("1.4.0") }
        override("fast-uri") { include("3.1.2") }
        override("flatted") { include("3.4.2") }
        override("path-to-regexp@^0.1.0") { include("0.1.13") }
        override("picomatch@^2.0.0") { include("2.3.2") }
        override("serialize-javascript") { include("7.0.5") }
        override("socket.io-parser") { include("4.2.6") }
        override("ajv@^8.0.0") { include("8.18.0") }
        override("brace-expansion@^1.0.0") { include("1.1.13") }
        override("brace-expansion@^2.0.0") { include("2.0.3") }
        override("diff@^7.0.0") { include("8.0.3") }
        override("follow-redirects") { include("1.16.0") }
        override("http-proxy-middleware@^2.0.0") { include("2.0.10") }
        override("js-yaml@^4.0.0") { include("4.2.0") }
        override("launch-editor") { include("2.14.1") }
        override("qs") { include("6.15.2") }
    }
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // Generated sources are excluded from linting via .editorconfig
    // ([**/build/generated/**/*.kt] ktlint = disabled) — the plugin-level
    // filter { } DSL does not reliably apply to KMP source sets.

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
    dokka(project(":spatial-engine-preview"))
    dokka(project(":spatial-scene"))
    dokka(project(":spatial-dsl"))
    dokka(project(":spatial-compose"))
}
