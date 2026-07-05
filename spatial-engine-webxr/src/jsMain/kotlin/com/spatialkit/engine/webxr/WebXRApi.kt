package com.spatialkit.engine.webxr

import kotlin.js.Promise

/*
 * External declarations for WebXR Device API.
 *
 * Based on: https://immersive-web.github.io/webxr/
 */

// === Navigator XR ===

public external interface Navigator {
    public val xr: XRSystem?
}

public external interface XRSystem {
    public fun isSessionSupported(mode: String): Promise<Boolean>

    public fun requestSession(
        mode: String,
        options: dynamic = definedExternally,
    ): Promise<XRSession>
}

// === XR Session ===

public external interface XRSession {
    public val inputSources: Array<XRInputSource>
    public val renderState: XRRenderState

    public fun requestReferenceSpace(type: String): Promise<XRReferenceSpace>

    public fun requestAnimationFrame(callback: (Double, XRFrame) -> Unit): Int

    public fun cancelAnimationFrame(handle: Int)

    public fun requestHitTestSource(options: dynamic): Promise<XRHitTestSource>

    public fun end(): Promise<Unit>

    public fun updateRenderState(state: dynamic)

    // Event handlers
    public var onend: ((dynamic) -> Unit)?
    public var oninputsourceschange: ((dynamic) -> Unit)?
}

public external interface XRRenderState {
    public val baseLayer: XRWebGLLayer?
}

public external class XRWebGLLayer(
    session: XRSession,
    context: dynamic,
    options: dynamic = definedExternally,
)

// === XR Frame ===

public external interface XRFrame {
    public val session: XRSession

    public fun getViewerPose(referenceSpace: XRReferenceSpace): XRViewerPose?

    public fun getPose(
        space: XRSpace,
        referenceSpace: XRReferenceSpace,
    ): XRPose?

    public fun getHitTestResults(source: XRHitTestSource): Array<XRHitTestResult>
}

// === XR Pose ===

public external interface XRViewerPose {
    public val transform: XRRigidTransform
    public val views: Array<XRView>
}

public external interface XRPose {
    public val transform: XRRigidTransform
}

public external interface XRRigidTransform {
    public val position: DOMPointReadOnly
    public val orientation: DOMPointReadOnly
    public val matrix: Float32Array
}

public external interface DOMPointReadOnly {
    public val x: Double
    public val y: Double
    public val z: Double
    public val w: Double
}

public external class Float32Array(
    length: Int,
) {
    public operator fun get(index: Int): Float

    public operator fun set(
        index: Int,
        value: Float,
    )

    public val length: Int
}

// === XR View ===

public external interface XRView {
    public val eye: String
    public val projectionMatrix: Float32Array
    public val transform: XRRigidTransform
}

// === XR Spaces ===

public external interface XRSpace

public external interface XRReferenceSpace : XRSpace

// === XR Input ===

public external interface XRInputSource {
    public val handedness: String
    public val targetRayMode: String
    public val targetRaySpace: XRSpace
    public val gripSpace: XRSpace?
    public val gamepad: Gamepad?
    public val hand: XRHand?
}

public external interface Gamepad {
    public val id: String
    public val buttons: Array<GamepadButton>
    public val axes: Array<Double>
}

public external interface GamepadButton {
    public val pressed: Boolean
    public val touched: Boolean
    public val value: Double
}

public external interface XRHand {
    public fun get(joint: String): XRJointSpace?

    public val size: Int
}

public external interface XRJointSpace : XRSpace

// === XR Hit Testing ===

public external interface XRHitTestSource

public external interface XRHitTestResult {
    public fun getPose(referenceSpace: XRReferenceSpace): XRPose?
}
