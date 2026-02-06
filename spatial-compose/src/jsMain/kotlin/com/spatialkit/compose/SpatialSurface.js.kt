package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

/**
 * JavaScript/Browser implementation of SpatialSurface.
 *
 * Creates an HTML canvas element for WebXR rendering with Three.js.
 *
 * Note: This is a minimal stub. For full WebXR support,
 * use the spatial-engine-webxr module which provides Three.js integration.
 */
@Composable
actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // Create canvas element
    val canvasId = remember { "spatial-canvas-${(kotlin.random.Random.nextInt(1000000))}" }

    DisposableEffect(canvasId) {
        // Create canvas in DOM
        val canvas =
            document.createElement("canvas") as HTMLCanvasElement
        canvas.id = canvasId
        canvas.style.width = "100%"
        canvas.style.height = "100%"

        // Add to document
        document.body?.appendChild(canvas)

        // Initialize Three.js renderer here
        // This would connect to the WebXR engine

        onDispose {
            // Cleanup
            document.body?.removeChild(canvas)
        }
    }
}
