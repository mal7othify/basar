package com.spatialkit.engine.preview

import com.spatialkit.engine.SpatialEngine

/**
 * Factory function to create a non-XR preview engine instance (M10.1).
 *
 * The preview engine renders scenes in a plain browser window with an orbit
 * camera — no WebXR, no AR hardware. Use it as the development fallback when
 * immersive XR is unavailable (see `SpatialSurface` in spatial-compose, which
 * does this automatically).
 */
@JsExport
public fun createPreviewEngine(): SpatialEngine = PreviewEngine()
