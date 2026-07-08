@file:JsModule("three")
@file:JsNonModule

package com.spatialkit.engine.preview

import com.spatialkit.engine.webxr.Object3D

/**
 * Extra Three.js declarations used only by the preview renderer
 * (the shared bindings live in spatial-engine-webxr's ThreeJS.kt).
 */

public external class GridHelper(
    size: Double = definedExternally,
    divisions: Int = definedExternally,
    colorCenterLine: Int = definedExternally,
    colorGrid: Int = definedExternally,
) : Object3D
