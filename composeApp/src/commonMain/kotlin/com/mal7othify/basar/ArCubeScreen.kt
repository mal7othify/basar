package com.mal7othify.basar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * v2 R1 demo: live camera composited through the Filament renderer, tracked
 * camera pose, and tap-to-place on detected planes — the
 * TrackingProvider + SceneRenderer + SpatialRuntime2 stack end to end.
 *
 * Android-only until the R2 ARKit adapter; the iOS actual shows a notice.
 */
@Composable
fun ArCubeScreen(onBack: () -> Unit) {
    var status by remember { mutableStateOf("Starting AR…") }
    DemoScaffold(screen = DemoScreen.AR_CUBE, status = status, onBack = onBack) {
        ArCubeContent(onStatus = { status = it }, modifier = Modifier.fillMaxSize())
    }
}

/** Platform AR wiring (Android: ARCore + Filament; iOS: pending R2). */
@Composable
expect fun ArCubeContent(
    onStatus: (String) -> Unit,
    modifier: Modifier,
)
