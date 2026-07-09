package com.mal7othify.basar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Demo app entry point: a home screen linking to one demo per business use
 * case (retail furniture preview, construction measuring, education content)
 * plus the API showcase playground.
 *
 * Navigation uses multiplatform navigation-compose, so the system back
 * gesture/button pops to home on Android, the edge-swipe works on iOS, and
 * the browser back/forward buttons work on web (bound via [onNavHostReady]
 * in the js main). Each demo screen hosts its own [SpatialView]; leaving a
 * screen disposes its session and engine, entering one starts fresh.
 *
 * @param initialScreen Screen to open on launch (web deep links via
 *   `?demo=`); pushed on top of home so back returns to the home screen
 * @param onNavHostReady Called once the nav graph is set up — web binds the
 *   controller to browser history here
 */
@Composable
fun App(
    initialScreen: DemoScreen = DemoScreen.HOME,
    onNavHostReady: suspend (NavController) -> Unit = {},
) {
    val navController = rememberNavController()
    val goHome: () -> Unit = { navController.navigateUp() }

    MaterialTheme {
        NavHost(navController = navController, startDestination = DemoScreen.HOME.route) {
            composable(DemoScreen.HOME.route) {
                HomeScreen(onOpen = { demo -> navController.navigate(demo.route) })
            }
            composable(DemoScreen.FURNITURE.route) { FurnitureDemo(onBack = goHome) }
            composable(DemoScreen.MEASURE.route) { MeasureDemo(onBack = goHome) }
            composable(DemoScreen.SOLAR_SYSTEM.route) { SolarSystemDemo(onBack = goHome) }
            composable(DemoScreen.SHOWCASE.route) { ShowcaseDemo(onBack = goHome) }
        }
    }

    LaunchedEffect(Unit) {
        // Deep link on top of home, so back from the demo lands on home
        if (initialScreen != DemoScreen.HOME) {
            navController.navigate(initialScreen.route)
        }
        onNavHostReady(navController)
    }
}
