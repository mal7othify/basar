package com.mal7othify.basar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.spatialkit.render.SceneRenderer
import com.spatialkit.render.filament.FilamentRenderer
import com.spatialkit.render.filament.FilamentSurfaceHost

@Composable
actual fun rememberFilamentRenderer(): SceneRenderer {
    val renderer = remember { FilamentRenderer() }
    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }
    return renderer
}

@Composable
actual fun FilamentSurface(
    renderer: SceneRenderer,
    modifier: Modifier,
) {
    AndroidView(
        factory = { context -> FilamentSurfaceHost(context, renderer as FilamentRenderer) },
        modifier = modifier,
    )
}
