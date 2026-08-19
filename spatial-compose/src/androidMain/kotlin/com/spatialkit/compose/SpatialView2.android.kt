package com.spatialkit.compose

import android.content.Context
import android.os.Build
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.spatialkit.render.filament.EglContextHelper
import com.spatialkit.render.filament.FilamentRenderer
import com.spatialkit.render.filament.FilamentSurfaceHost
import com.spatialkit.scene.SpatialSession2
import com.spatialkit.tracking.arcore.ARCoreTrackingProvider

/**
 * Android v2 stack: shared-EGL Filament renderer + ARCore tracking-only
 * provider (the SceneView pattern — see [EglContextHelper]). Everything is
 * created on the main thread, which is also the frame thread.
 */
@Composable
internal actual fun rememberSpatialStack2(): SpatialStack2 {
    val context = LocalContext.current.applicationContext
    return remember {
        val egl = EglContextHelper()
        val renderer = FilamentRenderer(context, egl.eglContext)
        val provider = ARCoreTrackingProvider(context)
        // Several texture names: ARCore rotates through them (its internal
        // double-buffering), reporting the per-frame one to the renderer.
        provider.setCameraTextureIds(IntArray(6) { egl.createExternalTexture() })
        AndroidSpatialStack2(egl, SpatialSession2(provider, renderer))
    }
}

private class AndroidSpatialStack2(
    private val egl: EglContextHelper,
    override val session: SpatialSession2,
) : SpatialStack2 {
    override fun dispose() {
        // The session disposes the renderer (and with it the Filament
        // engine) before the shared EGL context goes away.
        session.dispose()
        egl.destroy()
    }
}

@Composable
internal actual fun SpatialSurface2(
    session: SpatialSession2,
    modifier: Modifier,
) {
    val renderer = session.runtime.renderer as FilamentRenderer
    val provider = session.runtime.provider as ARCoreTrackingProvider
    AndroidView(
        factory = { ctx ->
            FilamentSurfaceHost(
                ctx,
                renderer,
                // The session's ticker owns the frame loop; the host only
                // manages the swapchain and reports size changes.
                driveRenderLoop = false,
                onResized = { w, h -> provider.setDisplayGeometry(ctx.displayRotation(), w, h) },
            )
        },
        modifier = modifier,
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
