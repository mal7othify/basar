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
import com.spatialkit.engine.screenPointToRay

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

    /**
     * Gesture routing for this session.
     *
     * The platform input layer (`SpatialView`) submits gesture events here;
     * they are applied to the scene on the render thread each frame. Configure
     * nodes with `onTap`/`draggable()`/`scalable()`/`rotatable()`, and use
     * [InteractionManager.onTapMissed] for taps that hit no interactive node
     * (e.g. tap-to-place).
     */
    public val interactions: InteractionManager = InteractionManager()

    private var userFrameCallback: ((FrameData) -> Unit)? = null
    private var errorCallback: ((EngineError) -> Unit)? = null

    /**
     * Latest frame, cached for screen-point picking between frames. Written on
     * the render thread; readers get the most recent complete frame.
     */
    private var lastFrame: FrameData? = null

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
        lastFrame = null
    }

    /**
     * Perform a hit test from screen coordinates.
     *
     * Coordinates are normalized: (0,0) = top-left, (1,1) = bottom-right.
     *
     * @param screenX Normalized X coordinate (0-1)
     * @param screenY Normalized Y coordinate (0-1)
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
     * Picks the scene node under a screen point (M8 node picking).
     *
     * Unprojects the point through the latest frame's camera (see
     * [com.spatialkit.engine.screenPointToRay]) and raycasts scene content.
     * The result's `nodeId` resolves via [SpatialScene.findNodeById].
     *
     * Coordinates are normalized: (0,0) = top-left, (1,1) = bottom-right.
     *
     * @return The closest content hit, or null if nothing was hit (or no
     *   frame has been rendered yet)
     */
    public fun raycastNodeAt(
        screenX: Float,
        screenY: Float,
    ): RaycastResult? {
        val frame = lastFrame ?: return null
        val ray = frame.screenPointToRay(screenX, screenY)
        return performRaycast(ray.origin, ray.direction)
    }

    /**
     * Handle each frame from the engine.
     *
     * 1. Update anchor states from frame data
     * 2. Apply queued gestures to the scene (render thread)
     * 3. Call user's frame callback
     * 4. Submit updated scene to engine
     */
    private fun handleFrame(frameData: FrameData) {
        // Exceptions (including from the user callback) propagate to
        // SpatialRuntime's frame wrapper, which reports them once as
        // EngineError.RuntimeError with the original cause attached.
        lastFrame = frameData

        // Update anchors
        scene.updateAnchors(frameData.anchors)

        // Apply gestures before app logic so the frame callback observes
        // post-gesture node state
        interactions.processFrame(
            frame = frameData,
            scene = scene,
            raycast = ::performRaycast,
            hitTest = ::performHitTest,
        )

        // User frame logic
        userFrameCallback?.invoke(frameData)

        // Submit scene to engine
        val sceneData = scene.toSceneData()
        runtime.submitScene(sceneData)
    }
}
