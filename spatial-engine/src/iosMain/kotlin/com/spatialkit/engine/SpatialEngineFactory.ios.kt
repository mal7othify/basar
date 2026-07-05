package com.spatialkit.engine

import com.spatialkit.core.log.SpatialLog
import platform.ARKit.ARWorldTrackingConfiguration

/**
 * iOS implementation of SpatialEngineFactory.
 *
 * **Important**: To use ARKit, depend on the `spatial-engine-arkit` module
 * and use `createARKitEngine()` from `com.spatialkit.engine.arkit`.
 *
 * Example:
 * ```kotlin
 * // In your iOS code
 * val engine = createARKitEngine()
 * ```
 *
 * This factory returns [FallbackEngine] for compatibility with cross-platform code.
 * The platform-specific module provides the real implementation.
 */
public actual object SpatialEngineFactory {
    public actual fun isSupported(): Boolean = ARWorldTrackingConfiguration.isSupported

    public actual fun getSupportedModes(): List<SessionMode> =
        if (isSupported()) {
            listOf(SessionMode.AR) // ARKit supports AR mode only
        } else {
            emptyList()
        }

    public actual fun create(): SpatialEngine {
        SpatialLog.warn(
            "SpatialEngineFactory",
        ) { "Use createARKitEngine() from spatial-engine-arkit for ARKit support" }
        return FallbackEngine()
    }

    public actual fun getEngineName(): String = "Fallback (iOS - use spatial-engine-arkit)"
}
