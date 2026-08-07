package com.spatialkit.scene

import java.util.Timer
import java.util.TimerTask

/** JVM (test/preview) ticker: a plain 60 Hz timer — no real vsync exists. */
public actual class FrameTicker {
    private var timer: Timer? = null

    public actual fun start(onFrame: (frameTimeNanos: Long) -> Unit) {
        stop()
        timer =
            Timer("spatial-frame-ticker", true).also { t ->
                t.scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            onFrame(System.nanoTime())
                        }
                    },
                    0L,
                    16L,
                )
            }
    }

    public actual fun stop() {
        timer?.cancel()
        timer = null
    }
}
