package com.spatialkit.render.filament

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.android.UiHelper

/**
 * A [SurfaceView] that hosts a [FilamentRenderer]: manages the swapchain via
 * Filament's [UiHelper] and drives the render loop with a [Choreographer]
 * frame callback on the main thread (Filament pipelines actual GPU work on
 * its own backend thread).
 *
 * R0 shape — in R1 the Choreographer moves behind the shared `FrameTicker`
 * abstraction so tracking and rendering tick together.
 */
public class FilamentSurfaceHost(
    context: Context,
    private val renderer: FilamentRenderer,
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
                    }
                }
        }

    private val frameCallback =
        object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                Choreographer.getInstance().postFrameCallback(this)
                if (uiHelper.isReadyToRender) {
                    renderer.renderFrame(frameTimeNanos)
                }
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        uiHelper.attachTo(this)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        uiHelper.detach()
        super.onDetachedFromWindow()
    }
}
