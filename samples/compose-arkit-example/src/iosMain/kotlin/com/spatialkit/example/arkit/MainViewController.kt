package com.spatialkit.example.arkit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.spatialkit.compose.SpatialView
import com.spatialkit.dsl.cm
import com.spatialkit.dsl.rgb
import com.spatialkit.dsl.vec3
import com.spatialkit.engine.SessionMode
import platform.UIKit.UIViewController

/**
 * Creates the main iOS view controller for the ARKit example.
 *
 * This is the entry point for the iOS app. It creates a ComposeUIViewController
 * that hosts the Compose Multiplatform UI.
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }

@Composable
fun App() {
    var sessionReady by remember { mutableStateOf(false) }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Spatial-KMP ARKit Example",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("Session: ${if (sessionReady) "Ready" else "Starting..."}")
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                SpatialView(
                    mode = SessionMode.AR,
                    onSessionReady = { sessionReady = true },
                ) {
                    // Red cube
                    cube(size = 10.cm, color = rgb(0xFF0000)) {
                        position = vec3(0f, 0f, -1f)
                    }

                    // Green sphere
                    sphere(radius = 5.cm, color = rgb(0x00FF00)) {
                        position = vec3(0.3f, 0f, -1f)
                    }

                    // Blue cylinder
                    cylinder(radius = 3.cm, height = 15.cm, color = rgb(0x0000FF)) {
                        position = vec3(-0.3f, 0f, -1f)
                    }
                }
            }
        }
    }
}
