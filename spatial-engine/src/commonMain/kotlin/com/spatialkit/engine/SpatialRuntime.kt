package com.spatialkit.engine

import com.spatialkit.core.log.SpatialLog

/**
 * High-level convenience wrapper for SpatialEngine.
 *
 * Simplifies engine lifecycle management and provides a clean API
 * that works consistently across all platforms.
 *
 * Usage:
 * ```kotlin
 * val runtime = SpatialRuntime()
 *
 * if (runtime.isSupported) {
 *     runtime.start(
 *         config = EngineConfig(preferredMode = SessionMode.AR),
 *         onFrame = { frameData ->
 *             // Update scene based on frame
 *             runtime.submitScene(myScene)
 *         },
 *         onError = { error ->
 *             // Handle error
 *         }
 *     )
 * }
 *
 * // Later...
 * runtime.stop()
 * ```
 */
public class SpatialRuntime {
    private var engine: SpatialEngine? = null
    private var errorCallback: ((EngineError) -> Unit)? = null

    /**
     * Access the underlying engine for platform-specific configuration.
     *
     * Use this to configure platform-specific features like setting an ARView on iOS.
     * Returns null if the engine hasn't been created yet.
     */
    public fun getEngine(): SpatialEngine? = engine

    /**
     * Set a custom engine instance.
     *
     * Use this for platform-specific engine setup where the engine needs to be
     * created with platform-specific configuration (like an ARSCNView on iOS).
     *
     * Must be called before start() if you want to use a custom engine.
     */
    public fun setEngine(customEngine: SpatialEngine) {
        engine = customEngine
    }

    /**
     * Check if AR/VR is supported on this platform.
     *
     * NOTE: On Android, this may return false until you call
     * start() with a Context. Use the Android-specific check instead.
     */
    public val isSupported: Boolean
        get() = SpatialEngineFactory.isSupported()

    /**
     * Get the list of session modes supported on this platform.
     */
    public val supportedModes: List<SessionMode>
        get() = SpatialEngineFactory.getSupportedModes()

    /**
     * Get the current engine state.
     */
    public val state: EngineState?
        get() = engine?.state

    /**
     * Get the engine capabilities (available after start()).
     */
    public val capabilities: EngineCapabilities?
        get() = engine?.capabilities

    /**
     * Get the name of the engine implementation.
     */
    public val engineName: String
        get() = SpatialEngineFactory.getEngineName()

    /**
     * Start the spatial runtime.
     *
     * Initializes the engine, starts a session, and registers callbacks.
     *
     * @param config Engine configuration
     * @param onFrame Callback invoked each frame with FrameData
     * @param onError Callback invoked when errors occur
     * @throws IllegalStateException if platform doesn't support spatial
     */
    public suspend fun start(
        config: EngineConfig = EngineConfig(),
        onFrame: (FrameData) -> Unit,
        onError: (EngineError) -> Unit = {},
    ) {
        SpatialLog.debug(TAG) { "start() called" }
        errorCallback = onError

        // Skip isSupported check if an engine was already set (e.g., via setEngine())
        if (engine == null && !isSupported) {
            SpatialLog.warn(TAG) { "start() failed: platform not supported" }
            onError(EngineError.NotSupported)
            return
        }

        try {
            // Create and initialize engine (use existing if already set)
            val spatialEngine = engine ?: SpatialEngineFactory.create()
            engine = spatialEngine
            SpatialLog.debug(TAG) { "engine acquired: ${spatialEngine::class.simpleName}" }

            spatialEngine.initialize(config)
            SpatialLog.debug(TAG) { "engine initialized" }

            // Check if initialization succeeded
            when (val engineState = spatialEngine.state) {
                is EngineState.Error -> {
                    onError(engineState.error)
                    return
                }

                EngineState.Ready -> {
                    // Good, continue
                }

                else -> {
                    onError(
                        EngineError.InitializationFailed(
                            "Unexpected state after init: $engineState",
                        ),
                    )
                    return
                }
            }

            // Register frame callback with error handling
            spatialEngine.onFrame { frameData ->
                try {
                    onFrame(frameData)
                } catch (e: Exception) {
                    SpatialLog.error(TAG, e) { "frame callback threw" }
                    onError(EngineError.RuntimeError("Frame callback error: ${e.message}", e))
                }
            }

            // Start session
            spatialEngine.startSession(config.preferredMode)

            // Check if session started
            when (val engineState = spatialEngine.state) {
                is EngineState.Running -> {
                    // Success!
                }

                is EngineState.Error -> {
                    onError(engineState.error)
                }

                else -> {
                    onError(
                        EngineError.SessionFailed(
                            "Unexpected state after startSession: $engineState",
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            SpatialLog.error(TAG, e) { "exception during start" }
            onError(
                EngineError.InitializationFailed(
                    "Failed to start runtime: ${e.message}",
                ),
            )
        }
    }

    /**
     * Submit a scene to render.
     *
     * Call this each frame or whenever your scene changes.
     *
     * @param scene Scene description
     */
    public fun submitScene(scene: SceneData) {
        engine?.submitScene(scene) ?: run {
            errorCallback?.invoke(
                EngineError.RuntimeError("submitScene() called before the engine was started"),
            )
        }
    }

    /**
     * Perform a hit test from screen coordinates.
     *
     * @param screenX X coordinate in pixels
     * @param screenY Y coordinate in pixels
     * @return Hit test result or null if no hit
     */
    public fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? = engine?.performHitTest(screenX, screenY)

    /**
     * Perform a world-space raycast.
     *
     * @param origin Ray origin in world space
     * @param direction Ray direction (normalized)
     * @return Raycast result or null if no hit
     */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = engine?.performRaycast(origin, direction)

    /**
     * Stop the spatial session.
     *
     * Stops rendering and tracking. The engine remains initialized.
     * Call start() again to resume, or dispose() to fully clean up.
     */
    public fun stop() {
        engine?.stopSession()
    }

    /**
     * Dispose of the runtime and clean up all resources.
     *
     * After calling this, you must create a new SpatialRuntime
     * instance to start again.
     */
    public fun dispose() {
        engine?.dispose()
        engine = null
        errorCallback = null
    }

    /**
     * Check if the runtime is currently running.
     */
    public val isRunning: Boolean
        get() = engine?.state is EngineState.Running

    private companion object {
        const val TAG = "SpatialRuntime"
    }
}
