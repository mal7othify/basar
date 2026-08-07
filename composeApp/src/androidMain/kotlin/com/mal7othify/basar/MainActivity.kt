package com.mal7othify.basar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.spatialkit.compose.LocalSpatialEngine
import com.spatialkit.engine.arcore.createARCoreEngine

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
                    // Android has no default engine (ARCore needs a Context),
                    // so SpatialView renders nothing unless one is provided here.
                    val engine = remember { createARCoreEngine(this@MainActivity) }
                    // Intent-extra deep link, mirroring the web's `?demo=`:
                    //   adb shell am start ... -e demo filament
                    val initialScreen = remember { DemoScreen.fromDeepLink(intent.getStringExtra("demo")) }
                    CompositionLocalProvider(LocalSpatialEngine provides engine) {
                        App(initialScreen = initialScreen)
                    }
                } else {
                    PermissionScreen {
                        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
    }
}

// TODO: Replace this with KMP Permission later on!
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
