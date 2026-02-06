package com.spatialkit.engine

/**
 * JavaScript implementation of SpatialEngineFactory.
 *
 * NOTE: This is a minimal implementation. The full WebXR implementation
 * is in the spatial-engine-webxr module. To use WebXR, depend on that module.
 */
actual object SpatialEngineFactory {
    actual fun isSupported(): Boolean {
        // Check if WebXR is available
        val navigator = js("typeof navigator !== 'undefined' ? navigator : undefined")
        if (navigator === undefined) return false

        val xr = js("navigator.xr")
        return xr !== undefined && xr !== null
    }

    actual fun getSupportedModes(): List<SessionMode> {
        return if (isSupported()) {
            // Return all possible modes - actual support checked at runtime
            listOf(SessionMode.AR, SessionMode.VR)
        } else {
            emptyList()
        }
    }

    actual fun create(): SpatialEngine {
        // Return fallback engine
        // Apps should use spatial-engine-webxr for full WebXR support
        console.warn("Using FallbackEngine. For WebXR support, use spatial-engine-webxr module.")
        return FallbackEngine()
    }

    actual fun getEngineName(): String = "Fallback (JS)"
}
