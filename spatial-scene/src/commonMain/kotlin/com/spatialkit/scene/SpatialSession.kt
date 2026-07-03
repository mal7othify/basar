package com.spatialkit.scene

import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SpatialRuntime
import com.spatialkit.engine.Vec3

/**
 * High-level session combining scene graph with engine runtime.
 *
 * SpatialSession:
 * - Manages a mutable SpatialScene
 * - Integrates with SpatialRuntime
 * - Automatically updates anchors each frame
 * - Submits scene to engine each frame
 * - Provides frame callbacks for app logic
 *
 * Usage:
 * ```kotlin
 * val runtime = SpatialRuntime()
 * val session = SpatialSession(runtime)
 *
 * // Add content to scene
 * val cube = CubeNode(size = 0.1f, color = Color.RED)
 * cube.position = Vec3(0f, 0f, -1f)
 * session.scene.add(cube)
 *
 * // Start session
 * session.start(
 *     config = EngineConfig(preferredMode = SessionMode.AR),
 *     onFrame = { frameData ->
 *         // Update scene based on frame data
 *         updateFromFrame(frameData)
 *     },
 *     onError = { error ->
 *         handleError(error)
 *     }
 * )
 * ```
 */
public class SpatialSession(
    public val runtime: SpatialRuntime,
) {
    /**
     * The mutable scene graph.
     *
     * Add/remove nodes and anchors from this scene.
     * The session automatically submits the scene to the engine each frame.
     */
    public val scene: SpatialScene = SpatialScene()

    private var userFrameCallback: ((FrameData) -> Unit)? = null
    private var errorCallback: ((EngineError) -> Unit)? = null

    /**
     * Whether the session is currently running.
     */
    public val isRunning: Boolean
        get() = runtime.isRunning

    /**
     * Current engine state.
     */
    public val state: EngineState?
        get() = runtime.state

    /**
     * Engine capabilities.
     */
    public val capabilities: EngineCapabilities?
        get() = runtime.capabilities

    /**
     * Start the spatial session.
     *
     * Initializes the engine and starts tracking/rendering.
     * The scene will be submitted to the engine each frame.
     *
     * @param config Engine configuration
     * @param onFrame Callback for each frame (for app logic)
     * @param onError Callback for errors
     */
    public suspend fun start(
        config: EngineConfig = EngineConfig(),
        onFrame: (FrameData) -> Unit = {},
        onError: (EngineError) -> Unit = {},
    ) {
        userFrameCallback = onFrame
        errorCallback = onError

        runtime.start(
            config = config,
            onFrame = { frameData ->
                handleFrame(frameData)
            },
            onError = { error ->
                errorCallback?.invoke(error)
            },
        )
    }

    /**
     * Stop the session.
     *
     * Stops tracking and rendering but keeps the engine initialized.
     * Call start() again to resume.
     */
    public fun stop() {
        runtime.stop()
    }

    /**
     * Dispose of the session and clean up resources.
     *
     * After calling this, create a new SpatialSession to start again.
     */
    public fun dispose() {
        runtime.dispose()
        scene.clear()
        userFrameCallback = null
        errorCallback = null
    }

    /**
     * Perform a hit test from screen coordinates.
     *
     * @param screenX X coordinate in pixels
     * @param screenY Y coordinate in pixels
     * @return Hit test result or null
     */
    public fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? = runtime.performHitTest(screenX, screenY)

    /**
     * Perform a world-space raycast.
     *
     * @param origin Ray origin in world space
     * @param direction Ray direction (normalized)
     * @return Raycast result or null
     */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = runtime.performRaycast(origin, direction)

    /**
     * Handle each frame from the engine.
     *
     * 1. Update anchor states from frame data
     * 2. Call user's frame callback
     * 3. Submit updated scene to engine
     */
    private fun handleFrame(frameData: FrameData) {
        // Exceptions (including from the user callback) propagate to
        // SpatialRuntime's frame wrapper, which reports them once as
        // EngineError.RuntimeError with the original cause attached.

        // Update anchors
        scene.updateAnchors(frameData.anchors)

        // User frame logic
        userFrameCallback?.invoke(frameData)

        // Submit scene to engine
        val sceneData = scene.toSceneData()
        runtime.submitScene(sceneData)
    }
}
