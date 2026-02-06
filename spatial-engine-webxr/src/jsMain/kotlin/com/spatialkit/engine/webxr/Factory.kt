package com.spatialkit.engine.webxr

import com.spatialkit.engine.SpatialEngine

/**
 * Factory function to create a WebXR engine instance.
 *
 * This is the primary entry point for creating a WebXR-based spatial engine.
 */
@JsExport
fun createWebXREngine(): SpatialEngine {
    return WebXREngine()
}

/**
 * Check if WebXR is available in the current browser.
 */
@JsExport
fun isWebXRAvailable(): Boolean {
    val navigator = kotlinx.browser.window.asDynamic().navigator
    return navigator.xr != null && navigator.xr != undefined
}