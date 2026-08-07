package com.spatialkit.render.filament

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.android.UiHelper

/**
 * A [SurfaceView] that hosts a [FilamentRenderer]: manages the swapchain via
 * Filament's [UiHelper] and (optionally) drives the render loop with a
 * [Choreographer] frame callback on the main thread.
 *
 * Two driving modes:
 * - **Self-driven** (`driveRenderLoop = true`, the R0 spike): this view's
 *   Choreographer callback calls `renderer.render(null, nanos)`.
 * - **Externally driven** (`driveRenderLoop = false`, AR): the
 *   `SpatialRuntime` ticker owns the loop (tracking update → render), and
 *   this view only manages the swapchain and size.
 *
 * [onResized] reports surface size changes so AR wiring can forward display
 * geometry to the tracking provider.
 */
public class FilamentSurfaceHost(
    context: Context,
    private val renderer: FilamentRenderer,
    private val driveRenderLoop: Boolean = true,
    private val onResized: ((width: Int, height: Int) -> Unit)? = null,
) : SurfaceView(context) {
    private val uiHelper =
        UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            isOpaque = true
            renderCallback =
                object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(surface: Surface) {
                        renderer.swapChain?.let(renderer.engine::destroySwapChain)
                        renderer.swapChain = renderer.engine.createSwapChain(surface)
                    }

                    override fun onDetachedFromSurface() {
                        renderer.swapChain?.let {
                            renderer.engine.destroySwapChain(it)
                            renderer.engine.flushAndWait()
                        }
                        renderer.swapChain = null
                    }

                    override fun onResized(
                        width: Int,
                        height: Int,
                    ) {
                        renderer.onResized(width, height)
                        this@FilamentSurfaceHost.onResized?.invoke(width, height)
                    }
                }
        }

    /** True once the swapchain exists and frames can be rendered. */
    public val isReadyToRender: Boolean
        get() = uiHelper.isReadyToRender

    private val frameCallback =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                Choreographer.getInstance().postFrameCallback(this)
                if (uiHelper.isReadyToRender) {
                    renderer.render(null, frameTimeNanos)
                }
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        uiHelper.attachTo(this)
        if (driveRenderLoop) {
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun onDetachedFromWindow() {
        if (driveRenderLoop) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
        uiHelper.detach()
        super.onDetachedFromWindow()
    }
}
