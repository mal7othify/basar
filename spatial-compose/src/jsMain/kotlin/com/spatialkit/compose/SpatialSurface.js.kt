package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.webxr.WebXREngine
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement

/**
 * JavaScript/Browser implementation of SpatialSurface.
 *
 * For WebXR, we need a user gesture to start the AR session.
 * The button triggers the WebXR session request.
 */
@Composable
public actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    val webxrEngine = remember { WebXREngine() }

    // Set engine on runtime
    LaunchedEffect(webxrEngine) {
        session.runtime.setEngine(webxrEngine)
    }

    // Note: SpatialView will auto-call session.start() which will fail without user gesture.
    // The button below retries with proper user gesture context.

    DisposableEffect(Unit) {
        // Create overlay with "Start AR" button
        val overlay = document.createElement("div") as HTMLDivElement
        overlay.id = "ar-start-overlay"
        overlay.style.apply {
            position = "fixed"
            top = "0"
            left = "0"
            width = "100%"
            height = "100%"
            display = "flex"
            alignItems = "center"
            justifyContent = "center"
            backgroundColor = "rgba(0, 0, 0, 0.8)"
            zIndex = "1000"
        }

        val button = document.createElement("button") as HTMLButtonElement
        button.textContent = "Start AR Experience"
        button.style.apply {
            padding = "16px 32px"
            fontSize = "18px"
            backgroundColor = "#667eea"
            color = "white"
            border = "none"
            borderRadius = "8px"
            cursor = "pointer"
            fontFamily = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        }

        var hasStarted = false
        button.onclick = {
            if (!hasStarted) {
                hasStarted = true
                console.log("Button clicked - starting WebXR session with user gesture...")
                try {
                    // Engine is already initialized, just start the session
                    // This call now has user gesture context
                    webxrEngine.startSession(SessionMode.AR)
                    overlay.style.display = "none"
                    console.log("WebXR session start requested")
                } catch (e: Throwable) {
                    console.error("Failed to start AR:", e)
                    button.textContent = "Failed to start AR. Tap to retry."
                    hasStarted = false
                }
            }
            null
        }

        overlay.appendChild(button)
        document.body?.appendChild(overlay)

        onDispose {
            document.body?.removeChild(overlay)
        }
    }
}
