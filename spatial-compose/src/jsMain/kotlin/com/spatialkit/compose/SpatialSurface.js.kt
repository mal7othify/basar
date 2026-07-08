package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.preview.PreviewEngine
import com.spatialkit.engine.preview.WebAutoEngine
import com.spatialkit.scene.GestureEvent
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement

/**
 * JavaScript/Browser implementation of SpatialSurface.
 *
 * Uses the engine provided through [SpatialView] (parameter or
 * [LocalSpatialEngine]) when there is one, and defaults to a [WebAutoEngine]
 * otherwise: immersive WebXR where the browser supports it, and the non-XR
 * camera-orbit preview (`spatial-engine-preview`, M10.2) everywhere else —
 * so desktop browsers render the scene instead of nothing.
 *
 * WebXR needs a user gesture to start an AR session, so when the engine
 * resolves to immersive XR this surface shows a "Start AR" overlay button
 * that (re)requests the session with gesture context. The preview needs no
 * gesture and starts immediately, with no overlay.
 *
 * In preview mode the Three.js canvas sits above the Compose canvas, so
 * Compose never sees pointer input; taps are forwarded from the preview
 * canvas into the session's interaction manager (M8 gestures) here. Drags,
 * pinches and wheel input drive the preview's orbit camera instead of node
 * gestures.
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
                ?: WebAutoEngine()
                    .apply {
                        // Hide the Compose canvas while immersive XR runs (it would
                        // otherwise cover the camera feed), and restore it afterwards.
                        var previousBackground: String? = null
                        onEnterXR = {
                            val composeCanvas =
                                document.querySelector(
                                    "canvas:not(#webxr-canvas)",
                                ) as? HTMLCanvasElement
                            composeCanvas?.style?.display = "none"
                            previousBackground = document.body?.style?.background
                            document.body?.style?.background = "transparent"
                        }
                        onExitXR = {
                            val composeCanvas =
                                document.querySelector(
                                    "canvas:not(#webxr-canvas)",
                                ) as? HTMLCanvasElement
                            composeCanvas?.style?.display = ""
                            document.body?.style?.background = previousBackground ?: ""
                        }
                    }.also { session.runtime.setEngine(it) }
        }

    val scope = rememberCoroutineScope()

    // Note: SpatialView will auto-call session.start(). In preview mode that
    // succeeds directly; an immersive XR request fails without a user gesture,
    // and the overlay button below retries with proper gesture context.

    DisposableEffect(engine) {
        // Preview-canvas taps feed the same gesture pipeline the Compose
        // pointer modifier uses on the other platforms (M8).
        val forwardTap: (Float, Float) -> Unit = { x, y ->
            session.interactions.submit(GestureEvent.Tap(screenX = x, screenY = y))
        }

        var overlay: HTMLDivElement? = null

        when (engine) {
            is WebAutoEngine -> {
                engine.onTap = forwardTap
                // Only immersive XR needs the gesture-context overlay
                engine.onResolved = { isPreview ->
                    if (!isPreview && overlay == null) {
                        overlay = createStartAROverlay(scope, engine)
                    }
                }
            }

            is PreviewEngine -> engine.onTap = forwardTap

            // Provided XR/custom engine: assume immersive behavior
            else -> overlay = createStartAROverlay(scope, engine)
        }

        onDispose {
            overlay?.let { document.body?.removeChild(it) }
            when (engine) {
                is WebAutoEngine -> {
                    engine.onTap = null
                    engine.onResolved = null
                }

                is PreviewEngine -> engine.onTap = null

                else -> {}
            }
        }
    }
}

/**
 * Full-page overlay with a "Start AR Experience" button. WebXR session
 * requests need transient user activation, so the button (re)starts the
 * session from a click handler.
 */
private fun createStartAROverlay(
    scope: CoroutineScope,
    engine: SpatialEngine,
): HTMLDivElement {
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
    return overlay
}
