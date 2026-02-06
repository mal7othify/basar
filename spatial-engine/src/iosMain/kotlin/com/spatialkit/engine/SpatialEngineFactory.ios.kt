package com.spatialkit.engine

import com.spatialkit.engine.arkit.ARKitEngine

/**
 * iOS implementation of SpatialEngineFactory.
 *
 * Provides ARKit-based spatial engine when available,
 * falls back to FallbackEngine otherwise.
 */
actual object SpatialEngineFactory {
    actual fun isSupported(): Boolean = ARKitEngine().capabilities.supportsAR

    actual fun getSupportedModes(): List<SessionMode> =
        if (isSupported()) {
            listOf(SessionMode.AR) // ARKit supports AR mode only (no VR)
        } else {
            emptyList()
        }

    actual fun create(): SpatialEngine =
        if (isSupported()) {
            ARKitEngine()
        } else {
            println("⚠️ ARKit not supported on this device, using FallbackEngine")
            FallbackEngine()
        }

    actual fun getEngineName(): String = if (isSupported()) "ARKit" else "Fallback (iOS)"
}
