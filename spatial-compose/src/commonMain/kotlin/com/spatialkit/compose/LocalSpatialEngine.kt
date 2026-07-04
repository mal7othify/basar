package com.spatialkit.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.spatialkit.engine.SpatialEngine

/**
 * Supplies the [SpatialEngine] that [SpatialView] should use.
 *
 * On platforms where the library bundles a default engine (Web/WebXR,
 * iOS/ARKit) this can stay unset. On Android there is no default — the
 * ARCore engine needs a `Context`, and bundling it would force every
 * consumer to ship ARCore — so provide one at the root of your Android UI:
 *
 * ```kotlin
 * // in your Activity
 * val engine = remember { createARCoreEngine(context) } // spatial-engine-arcore
 * CompositionLocalProvider(LocalSpatialEngine provides engine) {
 *     App()
 * }
 * ```
 */
public val LocalSpatialEngine: ProvidableCompositionLocal<SpatialEngine?> =
    staticCompositionLocalOf { null }
