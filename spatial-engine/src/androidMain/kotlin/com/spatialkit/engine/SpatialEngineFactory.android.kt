package com.spatialkit.engine

/**
 * Android implementation of SpatialEngineFactory.
 *
 * NOTE: This is a minimal fallback implementation.
 * For full ARCore support, depend on the spatial-engine-arcore module
 * and use SpatialEngineFactory.createWithContext(context).
 */
actual object SpatialEngineFactory {
    actual fun isSupported(): Boolean {
        // Fallback implementation always returns false
        // Use spatial-engine-arcore for real ARCore support
        return false
    }

    actual fun getSupportedModes(): List<SessionMode> = emptyList()

    actual fun create(): SpatialEngine {
        android.util.Log.w(
            "SpatialEngineFactory",
            "Using FallbackEngine. For ARCore support, use spatial-engine-arcore module.",
        )
        return FallbackEngine()
    }

    actual fun getEngineName(): String = "Fallback (Android)"
}
