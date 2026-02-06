package com.spatialkit.compose

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession

/**
 * Android implementation of SpatialSurface.
 *
 * Uses AndroidView to embed a native Android view for spatial rendering.
 * The actual rendering view depends on the engine being used:
 * - ARCore: ARSceneView or GLSurfaceView
 * - Fallback: GLSurfaceView
 *
 * Note: This is a minimal implementation. For full ARCore support,
 * use the spatial-engine-arcore module which provides ARSceneView integration.
 */
@Composable
actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // Create a simple view for now
    // Full implementation would create GLSurfaceView or ARSceneView
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // TODO: Create proper rendering view based on engine
            // For now, just a placeholder view
            View(context).apply {
                // Configure view for spatial rendering
            }
        },
        update = { view ->
            // Update view with scene changes
            // This would trigger re-rendering with the new scene
        },
    )
}
