package com.spatialkit.scene

import android.view.Choreographer

public actual class FrameTicker {
    private var callback: Choreographer.FrameCallback? = null

    public actual fun start(onFrame: (frameTimeNanos: Long) -> Unit) {
        stop()
        val frameCallback =
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (callback !== this) return
                    Choreographer.getInstance().postFrameCallback(this)
                    onFrame(frameTimeNanos)
                }
            }
        callback = frameCallback
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    public actual fun stop() {
        callback?.let(Choreographer.getInstance()::removeFrameCallback)
        callback = null
    }
}
