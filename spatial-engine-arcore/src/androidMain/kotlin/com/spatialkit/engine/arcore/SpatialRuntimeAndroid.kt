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
import com.spatialkit.engine.SpatialRuntime
import com.spatialkit.engine.Vec3

/**
 * Android-specific convenience wrapper around [SpatialRuntime].
 *
 * Creates the ARCore engine with the given [Context] up front (the
 * cross-platform [com.spatialkit.engine.SpatialEngineFactory] cannot, because
 * it has no Context) and delegates everything else to [SpatialRuntime].
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
 *                     onFrame = { frameData -> runtime.submitScene(myScene) },
 *                     onError = { error -> /* handle */ },
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
    context: Context,
) {
    private val delegate =
        SpatialRuntime().apply {
            setEngine(createARCoreEngine(context))
        }

    /**
     * Check if ARCore is supported on this device.
     */
    public val isSupported: Boolean = isARCoreSupported(context)

    /**
     * Get supported session modes (ARCore only supports AR).
     */
    public val supportedModes: List<SessionMode>
        get() = if (isSupported) listOf(SessionMode.AR) else emptyList()

    /**
     * Get current engine state.
     */
    public val state: EngineState?
        get() = delegate.state

    /**
     * Get engine capabilities.
     */
    public val capabilities: EngineCapabilities?
        get() = delegate.capabilities

    /**
     * Check if running.
     */
    public val isRunning: Boolean
        get() = delegate.isRunning

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
        if (!isSupported) {
            onError(EngineError.NotSupported)
            return
        }
        delegate.start(config, onFrame, onError)
    }

    /**
     * Submit scene to render.
     */
    public fun submitScene(scene: SceneData) {
        delegate.submitScene(scene)
    }

    /**
     * Perform a hit test from normalized screen coordinates
     * ((0,0) = top-left, (1,1) = bottom-right).
     */
    public fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? = delegate.performHitTest(screenX, screenY)

    /**
     * Perform a world-space raycast.
     */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = delegate.performRaycast(origin, direction)

    /**
     * Stop the session.
     */
    public fun stop() {
        delegate.stop()
    }

    /**
     * Dispose and clean up.
     */
    public fun dispose() {
        delegate.dispose()
    }

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
