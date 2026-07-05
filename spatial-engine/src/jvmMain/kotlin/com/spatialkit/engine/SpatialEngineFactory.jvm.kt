package com.spatialkit.engine

/**
 * JVM implementation of SpatialEngineFactory.
 *
 * Returns fallback engine as JVM desktop doesn't have native AR/VR support.
 * For AR/VR on desktop, use a browser-based WebXR implementation.
 */
public actual object SpatialEngineFactory {
    public actual fun isSupported(): Boolean {
        // JVM desktop doesn't have native AR/VR support
        return false
    }

    public actual fun getSupportedModes(): List<SessionMode> {
        // No native AR/VR support on JVM
        return emptyList()
    }

    public actual fun create(): SpatialEngine {
        // Return fallback engine for testing/simulation purposes
        return FallbackEngine()
    }

    public actual fun getEngineName(): String = "Fallback (JVM)"
}
