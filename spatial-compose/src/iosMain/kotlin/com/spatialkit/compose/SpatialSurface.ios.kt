package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIView

/**
 * iOS implementation of SpatialSurface.
 *
 * Uses UIKitView to embed a native iOS view for spatial rendering.
 * The actual rendering view depends on the engine being used:
 * - ARKit: ARView or custom MTKView
 * - Fallback: MTKView (Metal rendering)
 *
 * Note: This is a minimal stub. For full ARKit support,
 * use the spatial-engine-arkit module (when implemented).
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // Create a native iOS view for spatial rendering
    UIKitView(
        modifier = modifier,
        factory = {
            // TODO: Create ARView or MTKView based on engine
            // For now, just a placeholder
            UIView()
        },
        update = { view ->
            // Update view with scene changes
        },
    )
}
