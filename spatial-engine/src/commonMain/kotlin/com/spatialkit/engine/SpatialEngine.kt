package com.spatialkit.engine

/**
 * The core AR/VR engine interface.
 *
 * This is THE CONTRACT that all platform implementations must satisfy.
 * It defines the complete abstraction between the spatial library and
 * platform-specific rendering (ARCore, ARKit, WebXR, etc.).
 *
 * Lifecycle:
 * 1. Create engine instance (platform-specific)
 * 2. Call initialize() with config
 * 3. Call startSession() with desired mode
 * 4. Register onFrame() callback
 * 5. Each frame: receive FrameData, update logic, submitScene()
 * 6. Call stopSession() to pause
 * 7. Call dispose() when done
 */
public interface SpatialEngine {
    /**
     * What features this engine implementation supports.
     *
     * Check this before attempting to use optional features like
     * plane detection or hand tracking.
     */
    public val capabilities: EngineCapabilities

    /**
     * Current state of the engine.
     *
     * Use this to determine if the engine is ready for session start,
     * currently running, or in an error state.
     */
    public val state: EngineState

    /**
     * Initialize the engine with the given configuration.
     *
     * Must be called before startSession(). Can be called again after
     * dispose() to reinitialize.
     *
     * @param config Engine configuration
     * @throws IllegalStateException if already initialized
     */
    public suspend fun initialize(config: EngineConfig)

    /**
     * Start an AR/VR session.
     *
     * Engine must be in Ready state. After starting, the engine will
     * begin calling registered onFrame() callbacks.
     *
     * @param mode The session mode (AR, VR, or Passthrough)
     * @throws IllegalStateException if not initialized or already running
     */
    public fun startSession(mode: SessionMode)

    /**
     * Stop the current session.
     *
     * Pauses rendering and tracking. Can call startSession() again to resume.
     * Engine returns to Ready state.
     */
    public fun stopSession()

    /**
     * Clean up engine resources.
     *
     * After calling this, the engine returns to Uninitialized state.
     * Must call initialize() again before starting a new session.
     */
    public fun dispose()

    /**
     * Register a callback for each rendered frame.
     *
     * The callback receives complete frame data including tracking state,
     * detected environment, and input. Use this to update your application
     * logic and submit updated scene data.
     *
     * Only one callback can be registered at a time. Calling this again
     * replaces the previous callback.
     *
     * @param callback Function to call each frame with FrameData
     */
    public fun onFrame(callback: (FrameData) -> Unit)

    /**
     * Submit a complete scene to render.
     *
     * Call this each frame (or whenever scene changes) to update what
     * the engine renders. The engine will render this scene until a
     * new one is submitted.
     *
     * @param scene Complete scene description
     */
    public fun submitScene(scene: SceneData)

    /**
     * Perform a hit test from screen coordinates (AR).
     *
     * Casts a ray from the screen point into the AR world, testing against
     * detected planes and estimated depth. Returns the closest hit or null.
     *
     * Screen coordinates are normalized: (0,0) = top-left, (1,1) = bottom-right
     *
     * @param screenX Normalized X coordinate (0-1)
     * @param screenY Normalized Y coordinate (0-1)
     * @return Hit result, or null if no hit
     */
    public fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult?

    /**
     * Perform a raycast from a world-space ray.
     *
     * Tests a ray against scene content and detected environment geometry.
     * Returns the closest hit or null.
     *
     * @param origin Ray origin in world space
     * @param direction Ray direction (should be normalized)
     * @return Raycast result, or null if no hit
     */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult?
}

/**
 * Engine capabilities - what features this implementation supports.
 *
 * Different platforms support different features. Check these before
 * relying on optional functionality.
 *
 * @property supportsAR Can run AR sessions
 * @property supportsVR Can run VR sessions
 * @property supportsPlaneDetection Can detect planes (floors, tables, walls)
 * @property supportsImageTracking Can track reference images
 * @property supportsFaceTracking Can track face
 * @property supportsHandTracking Can track hand/finger poses
 * @property supportsDepthOcclusion Can occlude virtual objects with real world
 * @property supportsSpatialAnchors Can create persistent spatial anchors
 * @property maxAnchors Maximum number of simultaneous anchors
 *   (null = no engine-imposed limit, 0 = anchors unsupported)
 */
public data class EngineCapabilities(
    val supportsAR: Boolean,
    val supportsVR: Boolean,
    val supportsPlaneDetection: Boolean,
    val supportsImageTracking: Boolean,
    val supportsFaceTracking: Boolean,
    val supportsHandTracking: Boolean,
    val supportsDepthOcclusion: Boolean,
    val supportsSpatialAnchors: Boolean,
    val maxAnchors: Int? = null,
)

/**
 * Current state of the engine.
 *
 * The engine transitions through these states during its lifecycle.
 */
public sealed interface EngineState {
    /** Engine has not been initialized yet */
    public data object Uninitialized : EngineState

    /** Engine is currently initializing (async operation in progress) */
    public data object Initializing : EngineState

    /** Engine is initialized and ready to start a session */
    public data object Ready : EngineState

    /**
     * Engine is running an active session.
     *
     * @property sessionMode The current session mode
     */
    public data class Running(
        val sessionMode: SessionMode,
    ) : EngineState

    /**
     * Engine encountered an error.
     *
     * @property error The error that occurred
     */
    public data class Error(
        val error: EngineError,
    ) : EngineState

    /** Engine has been disposed and must be reinitialized */
    public data object Disposed : EngineState
}

/**
 * AR/VR session modes.
 */
public sealed interface SessionMode {
    /** Augmented Reality - real world with virtual overlay */
    public data object AR : SessionMode

    /** Virtual Reality - fully immersive virtual environment */
    public data object VR : SessionMode

    /**
     * Passthrough VR - VR with camera passthrough of real world.
     *
     * @property opacity Passthrough opacity (0=full VR, 1=full AR)
     */
    public data class Passthrough(
        val opacity: Float = 1f,
    ) : SessionMode
}

/**
 * Configuration for engine initialization.
 *
 * @property preferredMode Preferred session mode (engine may fall back)
 * @property enablePlaneDetection Enable plane detection (AR)
 * @property enableLightEstimation Enable light estimation (AR)
 * @property nearPlane Near clipping plane distance in meters
 * @property farPlane Far clipping plane distance in meters
 */
public data class EngineConfig(
    val preferredMode: SessionMode = SessionMode.AR,
    val enablePlaneDetection: Boolean = true,
    val enableLightEstimation: Boolean = false,
    val nearPlane: Float = 0.1f,
    val farPlane: Float = 100f,
)
