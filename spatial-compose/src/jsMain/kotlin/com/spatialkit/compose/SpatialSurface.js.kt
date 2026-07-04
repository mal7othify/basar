package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.webxr.WebXREngine
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import kotlinx.browser.document
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement

/**
 * JavaScript/Browser implementation of SpatialSurface.
 *
 * Uses the engine provided through [SpatialView] (parameter or
 * [LocalSpatialEngine]) when there is one, and defaults to a bundled
 * [WebXREngine] otherwise.
 *
 * WebXR needs a user gesture to start an AR session, so this surface shows a
 * "Start AR" overlay button that (re)requests the session with gesture context.
 */
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
                ?: WebXREngine()
                    .apply {
                        // Hide the Compose canvas while immersive XR runs (it would
                        // otherwise cover the camera feed), and restore it afterwards.
                        var previousBackground: String? = null
                        onEnterXR = {
                            val composeCanvas = document.querySelector("canvas:not(#webxr-canvas)") as? HTMLCanvasElement
                            composeCanvas?.style?.display = "none"
                            previousBackground = document.body?.style?.background
                            document.body?.style?.background = "transparent"
                        }
                        onExitXR = {
                            val composeCanvas = document.querySelector("canvas:not(#webxr-canvas)") as? HTMLCanvasElement
                            composeCanvas?.style?.display = ""
                            document.body?.style?.background = previousBackground ?: ""
                        }
                    }.also { session.runtime.setEngine(it) }
        }

    val scope = rememberCoroutineScope()

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

        var starting = false
        button.onclick = {
            if (!starting) {
                starting = true
                SpatialLog.debug("SpatialSurface") { "start button clicked, starting WebXR session" }
                // startSession is a suspend fun; the coroutine dispatches soon
                // enough that the browser's transient user activation from the
                // click still covers the XR session request.
                scope.launch {
                    try {
                        // Engine is already initialized, just start the session
                        engine.startSession(SessionMode.AR)
                        if (engine.state.value is EngineState.Running) {
                            overlay.style.display = "none"
                            SpatialLog.debug("SpatialSurface") { "WebXR session running" }
                        } else {
                            button.textContent = "Failed to start AR. Tap to retry."
                            starting = false
                        }
                    } catch (e: Throwable) {
                        SpatialLog.error("SpatialSurface", e) { "failed to start AR" }
                        button.textContent = "Failed to start AR. Tap to retry."
                        starting = false
                    }
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
