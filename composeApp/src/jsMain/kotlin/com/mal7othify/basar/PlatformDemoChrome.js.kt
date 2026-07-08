package com.mal7othify.basar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLSpanElement

/**
 * Web: the spatial canvas (WebXR or preview) is a fixed full-page element
 * layered above the Compose canvas, hiding the Compose header. This actual
 * renders an equivalent DOM bar above the canvas instead — same technique as
 * the library's own "Start AR" overlay (canvas z-index 999, AR overlay 1000,
 * this bar 1010).
 */
@Composable
actual fun PlatformDemoChrome(
    title: String,
    status: String,
    onBack: () -> Unit,
) {
    val currentOnBack by rememberUpdatedState(onBack)
    var statusElement by remember { mutableStateOf<HTMLSpanElement?>(null) }

    DisposableEffect(Unit) {
        val bar = document.createElement("div") as HTMLDivElement
        bar.id = "demo-chrome"
        bar.style.apply {
            position = "fixed"
            top = "0"
            left = "0"
            right = "0"
            zIndex = "1010"
            display = "flex"
            alignItems = "center"
            padding = "10px 14px"
            backgroundColor = "rgba(20, 22, 26, 0.82)"
            color = "white"
            fontFamily = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
            fontSize = "14px"
            setProperty("gap", "14px")
            setProperty("backdrop-filter", "blur(6px)")
        }

        val backButton = document.createElement("button") as HTMLButtonElement
        backButton.textContent = "← Home"
        backButton.style.apply {
            padding = "6px 14px"
            backgroundColor = "#667eea"
            color = "white"
            border = "none"
            borderRadius = "6px"
            cursor = "pointer"
            fontSize = "14px"
        }
        backButton.onclick = {
            currentOnBack()
            null
        }

        val titleSpan = document.createElement("span") as HTMLSpanElement
        titleSpan.textContent = title
        titleSpan.style.fontWeight = "600"

        val statusSpan = document.createElement("span") as HTMLSpanElement
        statusSpan.style.opacity = "0.85"
        statusElement = statusSpan

        bar.appendChild(backButton)
        bar.appendChild(titleSpan)
        bar.appendChild(statusSpan)
        document.body?.appendChild(bar)

        onDispose {
            statusElement = null
            document.body?.removeChild(bar)
        }
    }

    SideEffect {
        statusElement?.textContent = status.replace('\n', ' ')
    }
}
