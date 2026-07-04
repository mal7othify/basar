package com.spatialkit.compose

import androidx.compose.runtime.Composable
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
 * Uses the engine provided through [SpatialView] (parameter or
 * [LocalSpatialEngine]) when there is one, and defaults to a bundled
 * [ARKitEngine] otherwise. When the engine is ARKit-backed, an ARSCNView is
 * created and connected to it; the engine handles all SceneKit rendering via
 * submitScene().
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
public actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // Honor an engine provided via SpatialView; create the platform default
    // only when none was set. Runs during composition so the engine exists
    // before SpatialView's LaunchedEffect calls session.start().
    val engine =
        remember {
            session.runtime.getEngine()
                ?: ARKitEngine().also { session.runtime.setEngine(it) }
        }

    // A custom non-ARKit engine renders through its own means; there is no
    // view to host in that case.
    val arkitEngine = engine as? ARKitEngine ?: return

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
}
