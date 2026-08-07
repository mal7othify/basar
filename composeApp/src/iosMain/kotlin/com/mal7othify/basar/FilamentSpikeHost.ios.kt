package com.mal7othify.basar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.spatialkit.render.SceneRenderer
import com.spatialkit.render.filament.FilamentRenderer

@Composable
actual fun rememberFilamentRenderer(): SceneRenderer {
    val renderer = remember { FilamentRenderer() }
    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }
    return renderer
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun FilamentSurface(
    renderer: SceneRenderer,
    modifier: Modifier,
) {
    UIKitView(
        factory = { (renderer as FilamentRenderer).makeView() },
        modifier = modifier,
        // Non-interactive interop view: Compose keeps the touches (the
        // ARSCNView-swallowed-gestures lesson from 547ae24).
        properties = UIKitInteropProperties(interactionMode = null),
    )
}
