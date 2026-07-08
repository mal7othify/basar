package com.mal7othify.basar

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // Deep link straight into a demo: ?demo=furniture|measure|solar|showcase
    val demoParam =
        window.location.search
            .substringAfter("demo=", "")
            .substringBefore('&')
            .takeIf { it.isNotEmpty() }

    ComposeViewport(document.getElementById("ComposeTarget")!!) {
        App(initialScreen = DemoScreen.fromDeepLink(demoParam))
    }
}
