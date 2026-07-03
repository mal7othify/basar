package com.spatialkit.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession

/**
 * JVM/Desktop implementation of SpatialSurface.
 *
 * Since JVM desktop doesn't have native AR/VR support, this is a stub
 * implementation that does nothing visible.
 *
 * For testing/development, you can use a web browser with WebXR support.
 */
@Composable
public actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // JVM desktop doesn't support AR/VR natively
    // Empty placeholder
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Stub - no actual rendering on JVM desktop
    }
}
