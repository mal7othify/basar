package com.spatialkit.engine

import com.spatialkit.core.log.SpatialLog

/**
 * Fallback engine implementation for unsupported platforms.
 *
 * This engine:
 * - Always reports as "not supported"
 * - Logs warnings when methods are called
 * - Returns empty/default data to prevent crashes
 * - Allows apps to run gracefully without AR/VR
 *
 * Use cases:
 * - Desktop browsers without WebXR
 * - Testing without AR hardware
 * - Graceful degradation for unsupported devices
 */
public class FallbackEngine : SpatialEngine {
    private var _state: EngineState = EngineState.Uninitialized
    private var frameCallback: ((FrameData) -> Unit)? = null

    override val capabilities: EngineCapabilities =
        EngineCapabilities(
            supportsAR = false,
            supportsVR = false,
            supportsPlaneDetection = false,
            supportsImageTracking = false,
            supportsFaceTracking = false,
            supportsHandTracking = false,
            supportsDepthOcclusion = false,
            supportsSpatialAnchors = false,
            maxAnchors = 0,
        )

    override val state: EngineState
        get() = _state

    override suspend fun initialize(config: EngineConfig) {
        SpatialLog.warn(TAG) { "initialize() called on unsupported platform" }
        _state =
            EngineState.Error(
                EngineError.NotSupported,
            )
    }

    override fun startSession(mode: SessionMode) {
        SpatialLog.warn(TAG) { "startSession() called on unsupported platform" }
        _state =
            EngineState.Error(
                EngineError.NotSupported,
            )
    }

    override fun stopSession() {
        SpatialLog.debug(TAG) { "stopSession() called" }
        // Deliberately stays in Error(NotSupported): this engine can never
        // reach a usable Ready state.
    }

    override fun dispose() {
        SpatialLog.debug(TAG) { "dispose() called" }
        _state = EngineState.Disposed
        frameCallback = null
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        SpatialLog.warn(TAG) { "onFrame() registered but will never be called" }
        frameCallback = callback
    }

    override fun submitScene(scene: SceneData) {
        // No-op: nothing renders on an unsupported platform. Logged at debug
        // because this can be called every frame.
        SpatialLog.debug(TAG) { "submitScene() ignored (unsupported platform)" }
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        SpatialLog.debug(TAG) { "performHitTest() -> null (unsupported platform)" }
        return null
    }

    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        SpatialLog.debug(TAG) { "performRaycast() -> null (unsupported platform)" }
        return null
    }

    private companion object {
        const val TAG = "FallbackEngine"
    }
}
