package com.spatialkit.engine

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
class FallbackEngine : SpatialEngine {
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
        logWarning("FallbackEngine: initialize() called on unsupported platform")
        _state =
            EngineState.Error(
                EngineError.NotSupported,
            )
    }

    override fun startSession(mode: SessionMode) {
        logWarning("FallbackEngine: startSession() called on unsupported platform")
        _state =
            EngineState.Error(
                EngineError.NotSupported,
            )
    }

    override fun stopSession() {
        logWarning("FallbackEngine: stopSession() called")
        _state = EngineState.Ready
    }

    override fun dispose() {
        logWarning("FallbackEngine: dispose() called")
        _state = EngineState.Disposed
        frameCallback = null
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        logWarning("FallbackEngine: onFrame() registered but will never be called")
        frameCallback = callback
    }

    override fun submitScene(scene: SceneData) {
        logWarning("FallbackEngine: submitScene() called but nothing will render")
        // Silently ignore - no rendering to do
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        logWarning("FallbackEngine: performHitTest() called - returning null")
        return null
    }

    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        logWarning("FallbackEngine: performRaycast() called - returning null")
        return null
    }

    private fun logWarning(message: String) {
        // Platform-specific logging would go here
        // For now, just print
        println("⚠️ $message")
    }
}
