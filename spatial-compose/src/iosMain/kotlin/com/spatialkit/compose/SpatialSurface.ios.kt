package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.spatialkit.engine.arkit.ARKitEngine
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.ARKit.ARSCNView
import platform.CoreGraphics.CGRectMake

/**
 * iOS implementation of SpatialSurface.
 *
 * Creates an ARSCNView for ARKit rendering and connects it to the ARKitEngine.
 * The engine handles all SceneKit rendering via submitScene().
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // Create the ARKitEngine and configure it with the runtime
    val arkitEngine = remember { ARKitEngine() }

    // Set the engine on the runtime before start() is called
    LaunchedEffect(arkitEngine) {
        session.runtime.setEngine(arkitEngine)
    }

    // Create a native ARSCNView for spatial rendering
    UIKitView(
        modifier = modifier,
        factory = {
            // Create ARSCNView with a default frame
            ARSCNView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)).apply {
                // Connect the view to the engine
                arkitEngine.setARView(this)

                // Enable AR features
                autoenablesDefaultLighting = true
                automaticallyUpdatesLighting = true

                // Performance optimizations
                preferredFramesPerSecond = 60

                // Ensure the view is visible
                hidden = false
                alpha = 1.0
            }
        },
        update = { view ->
            // Update viewport size on layout changes
            view.bounds.useContents {
                arkitEngine.updateViewportSize(size.width, size.height)
            }
        },
    )

    // Clean up on dispose
    DisposableEffect(arkitEngine) {
        onDispose {
            // Engine cleanup is handled by SpatialSession.dispose()
        }
    }
}
