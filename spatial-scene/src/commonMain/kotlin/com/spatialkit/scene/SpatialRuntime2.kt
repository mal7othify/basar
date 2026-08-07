package com.spatialkit.scene

import com.spatialkit.render.SceneRenderer
import com.spatialkit.tracking.TrackingConfig
import com.spatialkit.tracking.TrackingFrame
import com.spatialkit.tracking.TrackingProvider

/**
 * The v2 runtime: composes a [TrackingProvider] and a [SceneRenderer] and
 * owns the frame loop (`docs/V2_REALISM_REWRITE.md` §4).
 *
 * Per vsync tick (main thread): `provider.update()` → [onFrame] callback →
 * `renderer.render(frame)`. Node/scene mutation therefore happens on the
 * frame thread, preserving v1's update model. (SpatialSession moves on top
 * of this in R3; until then [onFrame] is the direct hook.)
 *
 * Named SpatialRuntime2 only while the v1 SpatialRuntime still exists —
 * renamed to SpatialRuntime when the v1 engine modules are deleted in R3.
 */
public class SpatialRuntime2(
    public val provider: TrackingProvider,
    public val renderer: SceneRenderer,
    private val ticker: FrameTicker = FrameTicker(),
) {
    private var running = false
    private var disposed = false

    /** Called every tick with this frame's tracking state (null while none). */
    public var onFrame: ((TrackingFrame?) -> Unit)? = null

    /**
     * Initializes and starts tracking, then begins ticking the frame loop.
     * Suspends until tracking is running; throws on setup failure.
     */
    public suspend fun start(config: TrackingConfig = TrackingConfig()) {
        check(!disposed) { "Runtime is disposed" }
        if (running) return
        provider.initialize(config)
        provider.start()
        running = true
        ticker.start(::tick)
    }

    /** Stops the frame loop and pauses tracking; [start] resumes. */
    public fun stop() {
        if (!running) return
        running = false
        ticker.stop()
        provider.stop()
    }

    /** Stops and releases the provider and renderer. */
    public fun dispose() {
        if (disposed) return
        stop()
        disposed = true
        provider.dispose()
        renderer.dispose()
    }

    private fun tick(frameTimeNanos: Long) {
        if (!running) return
        val frame = provider.update(frameTimeNanos)
        onFrame?.invoke(frame)
        renderer.render(frame, frameTimeNanos)
    }
}
