package com.spatialkit.engine

/**
 * Android implementation of SpatialEngineFactory.
 *
 * **Important**: ARCore requires an Android Context for initialization,
 * which cannot be provided through the cross-platform factory API.
 *
 * To use ARCore on Android:
 * 1. Add dependency: `implementation(project(":spatial-engine-arcore"))`
 * 2. Use `createARCoreEngine(context)` from `com.spatialkit.engine.arcore`
 *
 * Example:
 * ```kotlin
 * // In your Android Activity or ViewModel
 * val engine = createARCoreEngine(context)
 * ```
 *
 * This factory returns [FallbackEngine] for compatibility with cross-platform code
 * that doesn't have access to platform-specific context.
 */
public actual object SpatialEngineFactory {
    public actual fun isSupported(): Boolean {
        // Returns false because ARCore requires Context for proper detection
        // Use isARCoreAvailable(context) from spatial-engine-arcore for accurate check
        return false
    }

    public actual fun getSupportedModes(): List<SessionMode> = emptyList()

    public actual fun create(): SpatialEngine {
        android.util.Log.w(
            "SpatialEngineFactory",
            "ARCore requires Context. Use createARCoreEngine(context) from spatial-engine-arcore module.",
        )
        return FallbackEngine()
    }

    public actual fun getEngineName(): String = "Fallback (Android - use spatial-engine-arcore)"
}
