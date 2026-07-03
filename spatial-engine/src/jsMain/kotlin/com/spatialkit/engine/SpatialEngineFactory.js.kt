package com.spatialkit.engine

/**
 * JavaScript implementation of SpatialEngineFactory.
 *
 * **Important**: To use WebXR, depend on the `spatial-engine-webxr` module
 * and use `createWebXREngine()` from `com.spatialkit.engine.webxr`.
 *
 * Example:
 * ```kotlin
 * // In your JS/Browser code
 * val engine = createWebXREngine()
 * ```
 *
 * This factory returns [FallbackEngine] for compatibility with cross-platform code.
 * The platform-specific module provides the real implementation.
 */
public actual object SpatialEngineFactory {
    public actual fun isSupported(): Boolean {
        // Check if WebXR API is available
        val navigator = js("typeof navigator !== 'undefined' ? navigator : undefined")
        if (navigator === undefined) return false

        val xr = js("navigator.xr")
        return xr !== undefined && xr !== null
    }

    public actual fun getSupportedModes(): List<SessionMode> {
        return if (isSupported()) {
            listOf(SessionMode.AR, SessionMode.VR)
        } else {
            emptyList()
        }
    }

    public actual fun create(): SpatialEngine {
        console.warn("Use createWebXREngine() from spatial-engine-webxr for WebXR support")
        return FallbackEngine()
    }

    public actual fun getEngineName(): String = "Fallback (JS - use spatial-engine-webxr)"
}
