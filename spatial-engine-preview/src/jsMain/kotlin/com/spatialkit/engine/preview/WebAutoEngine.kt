package com.spatialkit.engine.preview

import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.Vec3
import com.spatialkit.engine.webxr.WebXREngine
import com.spatialkit.engine.webxr.isWebXRAvailable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Web engine that picks immersive WebXR or the non-XR preview at
 * [initialize] time (M10.2).
 *
 * Detection needs an async WebXR capability probe, and the frame pipeline is
 * wired to one engine instance for the lifetime of a session — so the choice
 * has to happen inside the engine contract rather than by swapping engines
 * afterwards. This wrapper initializes a [WebXREngine] when the browser
 * supports an immersive AR or VR session, and falls back to [PreviewEngine]
 * (camera-orbit rendering, no XR hardware needed) otherwise — typically on
 * desktop browsers, replacing the old render-nothing fallback for
 * development.
 *
 * Note: [state] returns the resolved engine's flow once [initialize] has run;
 * a flow instance grabbed *before* initialization won't see later
 * transitions. Read `state.value` through this property, or subscribe after
 * [onResolved] fires.
 */
public class WebAutoEngine : SpatialEngine {
    private var delegate: SpatialEngine? = null
    private val unresolvedState = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    private var pendingFrameCallback: ((FrameData) -> Unit)? = null

    /**
     * Invoked when an immersive XR session starts (never fires in preview
     * mode). Set before `initialize()`; forwarded to the XR engine.
     */
    public var onEnterXR: (() -> Unit)? = null

    /** Invoked when the immersive XR session ends. See [onEnterXR]. */
    public var onExitXR: (() -> Unit)? = null

    /**
     * Taps on the preview canvas (never fires in XR mode), normalized 0..1
     * with top-left origin. See [PreviewEngine.onTap].
     */
    public var onTap: ((screenX: Float, screenY: Float) -> Unit)? = null

    /**
     * Invoked at the end of [initialize] with whether the engine resolved to
     * the non-XR preview (`true`) or immersive WebXR (`false`). The hosting
     * UI can use this to show XR-only chrome such as a "Start AR" button.
     */
    public var onResolved: ((isPreview: Boolean) -> Unit)? = null

    /** True once resolved to the non-XR preview. False before [initialize]. */
    public val isPreview: Boolean
        get() = delegate is PreviewEngine

    override val state: StateFlow<EngineState>
        get() = delegate?.state ?: unresolvedState

    override val capabilities: EngineCapabilities
        get() =
            delegate?.capabilities ?: EngineCapabilities(
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

    override suspend fun initialize(config: EngineConfig) {
        val current = state.value
        if (current != EngineState.Uninitialized && current != EngineState.Disposed) {
            throw IllegalStateException("Engine already initialized")
        }

        // Probe immersive support through the XR engine's own capability
        // check (initialize() awaits isSessionSupported); keep it only if an
        // immersive session can actually start.
        if (isWebXRAvailable()) {
            val xrEngine =
                WebXREngine().also {
                    it.onEnterXR = { onEnterXR?.invoke() }
                    it.onExitXR = { onExitXR?.invoke() }
                }
            xrEngine.initialize(config)
            val caps = xrEngine.capabilities
            if (caps.supportsAR || caps.supportsVR) {
                SpatialLog.debug(TAG) { "resolved to WebXR (AR=${caps.supportsAR}, VR=${caps.supportsVR})" }
                adopt(xrEngine)
                onResolved?.invoke(false)
                return
            }
            // navigator.xr exists but no immersive session is available
            // (typical desktop browser) — drop it and fall back to preview
            xrEngine.dispose()
        }

        SpatialLog.debug(TAG) { "no immersive XR available — resolved to non-XR preview" }
        val preview =
            PreviewEngine().also {
                it.onTap = { x, y -> onTap?.invoke(x, y) }
            }
        preview.initialize(config)
        adopt(preview)
        onResolved?.invoke(true)
    }

    private fun adopt(engine: SpatialEngine) {
        delegate = engine
        pendingFrameCallback?.let { engine.onFrame(it) }
    }

    override suspend fun startSession(mode: SessionMode) {
        val engine = delegate ?: throw IllegalStateException("Engine not initialized")
        engine.startSession(mode)
    }

    override fun stopSession() {
        delegate?.stopSession()
    }

    override fun dispose() {
        delegate?.dispose()
        delegate = null
        pendingFrameCallback = null
        unresolvedState.value = EngineState.Disposed
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        pendingFrameCallback = callback
        delegate?.onFrame(callback)
    }

    override fun submitScene(scene: SceneData) {
        delegate?.submitScene(scene)
    }

    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? = delegate?.performHitTest(screenX, screenY)

    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = delegate?.performRaycast(origin, direction)

    override suspend fun persistAnchor(anchorId: String): String? = delegate?.persistAnchor(anchorId)

    override suspend fun deletePersistedAnchor(handle: String) {
        delegate?.deletePersistedAnchor(handle)
    }

    private companion object {
        const val TAG = "WebAutoEngine"
    }
}
