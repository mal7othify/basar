package com.mal7othify.basar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Demo app entry point: a home screen linking to one demo per business use
 * case (retail furniture preview, construction measuring, education content)
 * plus the API showcase playground.
 *
 * Navigation is plain state — each demo screen hosts its own [SpatialView];
 * leaving a screen disposes its session and engine, entering one starts
 * fresh.
 *
 * @param initialScreen Screen to open on launch (web deep links via `?demo=`)
 */
@Composable
fun App(initialScreen: DemoScreen = DemoScreen.HOME) {
    var screen by remember { mutableStateOf(initialScreen) }
    val goHome = { screen = DemoScreen.HOME }

    MaterialTheme {
        when (screen) {
            DemoScreen.HOME -> HomeScreen(onOpen = { screen = it })
            DemoScreen.FURNITURE -> FurnitureDemo(onBack = goHome)
            DemoScreen.MEASURE -> MeasureDemo(onBack = goHome)
            DemoScreen.SOLAR_SYSTEM -> SolarSystemDemo(onBack = goHome)
            DemoScreen.SHOWCASE -> ShowcaseDemo(onBack = goHome)
        }
    }
}
