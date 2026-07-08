package com.mal7othify.basar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The app's screens: a home screen plus one screen per business use case.
 */
enum class DemoScreen(
    val title: String,
    val tagline: String,
) {
    HOME("Basar Demos", ""),
    FURNITURE(
        "Furniture Preview",
        "Retail: place and arrange products on real surfaces",
    ),
    MEASURE(
        "Tape Measure",
        "Construction: measure distances by tapping surfaces",
    ),
    SOLAR_SYSTEM(
        "Solar System",
        "Education: animated, tappable 3D lesson content",
    ),
    SHOWCASE(
        "API Showcase",
        "Playground: primitives, custom meshes, glTF, gestures",
    ),
    ;

    companion object {
        /** Resolves a `?demo=` deep-link value ("furniture", "measure", ...). */
        fun fromDeepLink(value: String?): DemoScreen =
            when (value?.lowercase()) {
                "furniture" -> FURNITURE
                "measure" -> MEASURE
                "solar", "solarsystem", "solar-system" -> SOLAR_SYSTEM
                "showcase" -> SHOWCASE
                else -> HOME
            }
    }
}

/**
 * Shared frame for the demo screens: a Compose header (title, back
 * navigation, live status) above the spatial content, plus [PlatformDemoChrome]
 * for platforms where the render canvas covers the Compose UI (web).
 */
@Composable
fun DemoScaffold(
    screen: DemoScreen,
    status: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Web: the Three.js canvas overlays the whole page, hiding Compose UI —
    // this renders an equivalent DOM header instead (no-op elsewhere)
    PlatformDemoChrome(title = screen.title, status = status, onBack = onBack)

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("← Home") }
                    Text(
                        screen.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        content()
    }
}

/**
 * Platform hook for demo-screen chrome (title + status + back navigation).
 *
 * On Android/iOS this is a no-op — the Compose header in [DemoScaffold] is
 * visible above the AR view. On web the spatial canvas is a fixed full-page
 * element *above* the Compose canvas, so the actual renders a small DOM bar
 * over it (mirroring how the library's own "Start AR" overlay works).
 */
@Composable
expect fun PlatformDemoChrome(
    title: String,
    status: String,
    onBack: () -> Unit,
)
