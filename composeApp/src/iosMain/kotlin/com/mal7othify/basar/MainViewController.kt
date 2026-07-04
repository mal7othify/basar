package com.mal7othify.basar

import androidx.compose.ui.window.ComposeUIViewController

// PascalCase is the Compose Multiplatform convention for this entry point,
// and the Swift side (iosApp) calls it by this name.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { App() }
