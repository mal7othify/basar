package com.spatialkit.example.arcore

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spatialkit.compose.SpatialView
import com.spatialkit.dsl.cm
import com.spatialkit.dsl.rgb
import com.spatialkit.dsl.vec3
import com.spatialkit.engine.SessionMode

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            permissionGranted = isGranted
        }

    private var permissionGranted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check camera permission
        permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MaterialTheme {
                if (permissionGranted) {
                    App()
                } else {
                    PermissionScreen {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Camera Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                "This app needs camera access to display AR content.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(24.dp))

            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun App() {
    var sessionReady by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Spatial-KMP ARCore Example",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text("Session: ${if (sessionReady) "Ready" else "Starting"}")
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
