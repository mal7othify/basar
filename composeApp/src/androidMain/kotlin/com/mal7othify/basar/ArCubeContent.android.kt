package com.mal7othify.basar

import android.content.Context
import android.os.Build
import android.view.Surface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.spatialkit.render.ModelHandle
import com.spatialkit.render.filament.EglContextHelper
import com.spatialkit.render.filament.FilamentRenderer
import com.spatialkit.render.filament.FilamentSurfaceHost
import com.spatialkit.scene.SpatialRuntime2
import com.spatialkit.tracking.TrackingConfig
import com.spatialkit.tracking.TrackingState
import com.spatialkit.tracking.arcore.ARCoreTrackingProvider
import kotlinx.coroutines.launch

@Composable
actual fun ArCubeContent(
    onStatus: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // EGL context + OES texture on the main thread; Filament shares it so
    // ARCore's camera image is sampleable (see EglContextHelper docs).
    val egl = remember { EglContextHelper() }
    val renderer = remember { FilamentRenderer(context, egl.eglContext) }
    val provider = remember { ARCoreTrackingProvider(context) }
    val runtime = remember { SpatialRuntime2(provider, renderer) }

    var crate by remember { mutableStateOf<ModelHandle?>(null) }
    var planesSeen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runtime.dispose()
            egl.destroy()
        }
    }

    LaunchedEffect(Unit) {
        // Several texture names: ARCore rotates through them (its internal
        // double-buffering), reporting the per-frame one via cameraTextureName.
        provider.setCameraTextureIds(IntArray(6) { egl.createExternalTexture() })
        runtime.onFrame = { frame ->
            if (!planesSeen && frame != null && frame.planes.isNotEmpty()) {
                planesSeen = true
            }
        }
        try {
            runtime.start(TrackingConfig())
            onStatus("Move the phone to find a surface, then tap to place")
        } catch (e: Exception) {
            onStatus("AR unavailable: ${e.message}")
        }
    }

    LaunchedEffect(planesSeen) {
        if (planesSeen) onStatus("Surface found — tap to place the crate")
    }

    AndroidView(
        factory = { ctx ->
            FilamentSurfaceHost(
                ctx,
                renderer,
                driveRenderLoop = false,
                onResized = { w, h -> provider.setDisplayGeometry(ctx.displayRotation(), w, h) },
            )
        },
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures { offset ->
                    val hit =
                        provider.hitTest(
                            offset.x / size.width,
                            offset.y / size.height,
                        ) ?: run {
                            onStatus("No surface there yet — aim at a textured floor")
                            return@detectTapGestures
                        }
                    scope.launch {
                        val model = crate ?: renderer.loadModel(createCrateGltf().encodeToByteArray()).also { crate = it }
                        renderer.setModelTransform(
                            model,
                            floatArrayOf(
                                1f, 0f, 0f, 0f,
                                0f, 1f, 0f, 0f,
                                0f, 0f, 1f, 0f,
                                hit.position.x, hit.position.y, hit.position.z, 1f,
                            ),
                        )
                        onStatus(
                            "Crate at (%.2f, %.2f, %.2f) — walk around it".format(
                                hit.position.x,
                                hit.position.y,
                                hit.position.z,
                            ),
                        )
                    }
                }
            },
    )
}

private fun Context.displayRotation(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.rotation
    }
