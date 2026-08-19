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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.spatialkit.render.filament.EglContextHelper
import com.spatialkit.render.filament.FilamentRenderer
import com.spatialkit.render.filament.FilamentSurfaceHost
import com.spatialkit.scene.SpatialRuntime2
import com.spatialkit.tracking.TrackingConfig
import com.spatialkit.tracking.TrackingState
import com.spatialkit.tracking.arcore.ARCoreTrackingProvider

@Composable
actual fun ArCubeContent(
    onStatus: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current

    // EGL context + OES texture on the main thread; Filament shares it so
    // ARCore's camera image is sampleable (see EglContextHelper docs).
    val egl = remember { EglContextHelper() }
    val renderer = remember { FilamentRenderer(context, egl.eglContext) }
    val provider = remember { ARCoreTrackingProvider(context) }
    val runtime = remember { SpatialRuntime2(provider, renderer) }

    val crateGlb = remember { createCrateGltf().encodeToByteArray() }
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
                    // R3 smoke test: place through the retained submitScene
                    // path — a Model node (GLB crate) plus a lit Mesh cube.
                    val p = hit.position
                    renderer.submitScene(
                        com.spatialkit.render.SceneData(
                            nodes =
                                listOf(
                                    com.spatialkit.render.SceneNode.Model(
                                        id = "crate",
                                        // The crate GLB is a 10cm cube centered at its
                                        // origin; lift by its half-height so it sits on
                                        // the plane instead of half-buried.
                                        transform =
                                            com.spatialkit.core.math.Transform(
                                                position =
                                                    com.spatialkit.core.math
                                                        .Vec3(p.x, p.y + 0.05f, p.z),
                                            ),
                                        glb = crateGlb,
                                        contentKey = "crate-v1",
                                    ),
                                    com.spatialkit.render.SceneNode.Mesh(
                                        id = "red-cube",
                                        transform =
                                            com.spatialkit.core.math.Transform(
                                                position =
                                                    com.spatialkit.core.math
                                                        .Vec3(p.x + 0.25f, p.y + 0.06f, p.z),
                                                scale =
                                                    com.spatialkit.core.math
                                                        .Vec3(0.12f, 0.12f, 0.12f),
                                            ),
                                        geometry =
                                            com.spatialkit.render.GeometryData(
                                                com.spatialkit.render.GeometryType.CUBE,
                                            ),
                                        material =
                                            com.spatialkit.render.MaterialData(
                                                type = com.spatialkit.render.MaterialType.PBR,
                                                baseColor =
                                                    com.spatialkit.core.math
                                                        .Vec4(0.85f, 0.12f, 0.09f, 1f),
                                                metallic = 0f,
                                                roughness = 0.4f,
                                            ),
                                    ),
                                ),
                        ),
                    )
                    onStatus("Crate + cube at (%.2f, %.2f, %.2f) — walk around them".format(p.x, p.y, p.z))
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
