package com.spatialkit.engine.arcore

import android.content.Context
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.SpatialEngineFactory
import com.spatialkit.engine.Vec3

/**
 * Android-specific convenience wrapper for SpatialRuntime.
 *
 * Handles Android Context requirement automatically.
 *
 * Usage:
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     private val runtime = SpatialRuntimeAndroid(this)
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *
 *         if (runtime.isSupported) {
 *             lifecycleScope.launch {
 *                 runtime.start(
 *                     config = EngineConfig(preferredMode = SessionMode.AR),
 *                     onFrame = { frameData ->
 *                         // Update scene
 *                         runtime.submitScene(myScene)
 *                     },
 *                     onError = { error ->
 *                         // Handle error
 *                     }
 *                 )
 *             }
 *         }
 *     }
 *
 *     override fun onDestroy() {
 *         runtime.dispose()
 *         super.onDestroy()
 *     }
 * }
 * ```
 */
public class SpatialRuntimeAndroid(
    private val context: Context,
) {
    private var engine: SpatialEngine? = null
    private var errorCallback: ((EngineError) -> Unit)? = null

    /**
     * Check if ARCore is supported on this device.
     */
    public val isSupported: Boolean
        get() = SpatialEngineFactory.isSupported() // TODO: Context might be needed here to check!

    /**
     * Get supported session modes (ARCore only supports AR).
     */
    public val supportedModes: List<SessionMode>
        get() = if (isSupported) listOf(SessionMode.AR) else emptyList()

    /**
     * Get current engine state.
     */
    public val state: EngineState?
        get() = engine?.state

    /**
     * Get engine capabilities.
     */
    public val capabilities: EngineCapabilities?
        get() = engine?.capabilities

    /**
     * Start the spatial runtime.
     *
     * @param config Engine configuration
     * @param onFrame Callback invoked each frame
     * @param onError Callback for errors
     */
    public suspend fun start(
        config: EngineConfig = EngineConfig(preferredMode = SessionMode.AR),
        onFrame: (FrameData) -> Unit,
        onError: (EngineError) -> Unit = {},
    ) {
        errorCallback = onError

        if (!isSupported) {
            onError(EngineError.NotSupported)
            return
        }

        try {
            // Create ARCore engine with context
            val spatialEngine = createARCoreEngine(context)
            engine = spatialEngine

            // Initialize
            spatialEngine.initialize(config)

            when (val engineState = spatialEngine.state) {
                is EngineState.Error -> {
                    onError(engineState.error)
                    return
                }

                EngineState.Ready -> {
                    // Continue
                }

                else -> {
                    onError(
                        EngineError.InitializationFailed(
                            "Unexpected state: $engineState",
                        ),
                    )
                    return
                }
            }

            // Register frame callback
            spatialEngine.onFrame { frame: FrameData ->
                try {
                    onFrame(frame)
                } catch (e: Exception) {
                    onError(
                        EngineError.InitializationFailed(
                            "Frame callback error: ${e.message}",
                        ),
                    )
                }
            }

            // Start session
            spatialEngine.startSession(config.preferredMode)

            when (val engineState = spatialEngine.state) {
                is EngineState.Running -> {
                    // Success
                }

                is EngineState.Error -> {
                    onError(engineState.error)
                }

                else -> {
                    onError(
                        EngineError.SessionFailed(
                            "Failed to start: $engineState",
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            onError(
                EngineError.InitializationFailed(
                    "Failed to start: ${e.message}",
                ),
            )
        }
    }

    /**
     * Submit scene to render.
     */
    public fun submitScene(scene: SceneData) {
        engine?.submitScene(scene) ?: errorCallback?.invoke(
            EngineError.InitializationFailed("Engine not started"),
        )
    }

    /**
     * Perform hit test.
     */
    public fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? = engine?.performHitTest(screenX, screenY)

    /**
     * Perform raycast.
     */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = engine?.performRaycast(origin, direction)

    /**
     * Stop the session.
     */
    public fun stop() {
        engine?.stopSession()
    }

    /**
     * Dispose and clean up.
     */
    public fun dispose() {
        engine?.dispose()
        engine = null
        errorCallback = null
    }

    /**
     * Check if running.
     */
    public val isRunning: Boolean
        get() = engine?.state is EngineState.Running

    /**
     * Request ARCore installation if needed.
     *
     * Shows system dialog if ARCore is not installed.
     *
     * @param activity Activity for dialog
     * @param userRequested If user explicitly requested AR
     * @return Installation status
     */
    public fun requestInstallation(
        activity: android.app.Activity,
        userRequested: Boolean = true,
    ): com.google.ar.core.ArCoreApk.InstallStatus = requestARCoreInstallation(activity, userRequested)
}
