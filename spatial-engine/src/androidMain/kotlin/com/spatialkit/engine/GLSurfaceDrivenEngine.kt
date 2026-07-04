package com.spatialkit.engine

/**
 * An engine whose rendering is driven by an Android GL surface.
 *
 * Engines that render through a `GLSurfaceView` (like the ARCore engine)
 * implement this interface; UI integrations forward the surface lifecycle to
 * it without needing a compile-time dependency on the concrete engine.
 */
public interface GLSurfaceDrivenEngine {
    /**
     * Called when the GL context has been created and is current.
     */
    public fun onSurfaceCreated()

    /**
     * Called when the rendering surface size changes (including rotation).
     */
    public fun onSurfaceChanged(
        width: Int,
        height: Int,
    )

    /**
     * Called for every frame on the GL thread.
     */
    public fun onDrawFrame()
}
