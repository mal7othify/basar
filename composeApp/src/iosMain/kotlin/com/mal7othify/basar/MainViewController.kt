package com.mal7othify.basar

import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSProcessInfo

// PascalCase is the Compose Multiplatform convention for this entry point,
// and the Swift side (iosApp) calls it by this name.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() =
    ComposeUIViewController {
        // Launch-argument deep link, mirroring the web's `?demo=` support:
        //   xcrun simctl launch <device> <bundle-id> demo=filament
        val demoArg =
            NSProcessInfo.processInfo.arguments
                .filterIsInstance<String>()
                .firstOrNull { it.startsWith("demo=") }
                ?.removePrefix("demo=")
        App(initialScreen = DemoScreen.fromDeepLink(demoArg))
    }
