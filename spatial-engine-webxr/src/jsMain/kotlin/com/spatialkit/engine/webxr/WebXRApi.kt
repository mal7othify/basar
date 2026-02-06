package com.spatialkit.engine.webxr

import kotlin.js.Promise

/**
 * External declarations for WebXR Device API.
 *
 * Based on: https://immersive-web.github.io/webxr/
 */

// === Navigator XR ===

external interface Navigator {
    val xr: XRSystem?
}

external interface XRSystem {
    fun isSessionSupported(mode: String): Promise<Boolean>
    fun requestSession(mode: String, options: dynamic = definedExternally): Promise<XRSession>
}

// === XR Session ===

external interface XRSession {
    val inputSources: Array<XRInputSource>
    val renderState: XRRenderState

    fun requestReferenceSpace(type: String): Promise<XRReferenceSpace>
    fun requestAnimationFrame(callback: (Double, XRFrame) -> Unit): Int
    fun cancelAnimationFrame(handle: Int)
    fun requestHitTestSource(options: dynamic): Promise<XRHitTestSource>
    fun end(): Promise<Unit>
    fun updateRenderState(state: dynamic)

    // Event handlers
    var onend: ((dynamic) -> Unit)?
    var oninputsourceschange: ((dynamic) -> Unit)?
}

external interface XRRenderState {
    val baseLayer: XRWebGLLayer?
}

external class XRWebGLLayer(
    session: XRSession,
    context: dynamic,
    options: dynamic = definedExternally,
)

// === XR Frame ===

external interface XRFrame {
    val session: XRSession

    fun getViewerPose(referenceSpace: XRReferenceSpace): XRViewerPose?
    fun getPose(space: XRSpace, referenceSpace: XRReferenceSpace): XRPose?
    fun getHitTestResults(source: XRHitTestSource): Array<XRHitTestResult>
}

// === XR Pose ===

external interface XRViewerPose {
    val transform: XRRigidTransform
    val views: Array<XRView>
}

external interface XRPose {
    val transform: XRRigidTransform
}

external interface XRRigidTransform {
    val position: DOMPointReadOnly
    val orientation: DOMPointReadOnly
    val matrix: Float32Array
}

external interface DOMPointReadOnly {
    val x: Double
    val y: Double
    val z: Double
    val w: Double
}

external class Float32Array(length: Int) {
    operator fun get(index: Int): Float
    operator fun set(index: Int, value: Float)
    val length: Int
}

// === XR View ===

external interface XRView {
    val eye: String
    val projectionMatrix: Float32Array
    val transform: XRRigidTransform
}

// === XR Spaces ===

external interface XRSpace

external interface XRReferenceSpace : XRSpace

// === XR Input ===

external interface XRInputSource {
    val handedness: String
    val targetRayMode: String
    val targetRaySpace: XRSpace
    val gripSpace: XRSpace?
    val gamepad: Gamepad?
    val hand: XRHand?
}

external interface Gamepad {
    val id: String
    val buttons: Array<GamepadButton>
    val axes: Array<Double>
}

external interface GamepadButton {
    val pressed: Boolean
    val touched: Boolean
    val value: Double
}

external interface XRHand {
    fun get(joint: String): XRJointSpace?
    val size: Int
}

external interface XRJointSpace : XRSpace

// === XR Hit Testing ===

external interface XRHitTestSource

external interface XRHitTestResult {
    fun getPose(referenceSpace: XRReferenceSpace): XRPose?
}