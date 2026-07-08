package com.mal7othify.basar

import androidx.compose.runtime.Composable

/**
 * No-op on iOS: the AR view renders inline in the Compose layout, so the
 * [DemoScaffold] header is already visible above it.
 */
@Composable
actual fun PlatformDemoChrome(
    title: String,
    status: String,
    onBack: () -> Unit,
) {
}
