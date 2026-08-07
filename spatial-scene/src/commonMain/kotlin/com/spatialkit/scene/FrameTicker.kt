package com.spatialkit.scene

/**
 * Platform vsync ticker driving the v2 frame loop on the main thread
 * (Choreographer on Android, CADisplayLink on iOS, a plain timer on JVM).
 *
 * The callback receives the frame timestamp in nanoseconds. [start] and
 * [stop] must be called from the main thread; the callback fires there too.
 */
public expect class FrameTicker() {
    public fun start(onFrame: (frameTimeNanos: Long) -> Unit)

    public fun stop()
}
