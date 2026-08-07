package com.spatialkit.tracking

import kotlinx.coroutines.flow.StateFlow

/**
 * THE tracking contract of the v2 architecture: a thin per-platform adapter
 * over the AR runtime (ARCore / ARKit) with **zero rendering responsibility**
 * (`docs/V2_REALISM_REWRITE.md` §4). It answers exactly two questions —
 * where is the camera, and what does the world look like — and manages
 * anchors. One Filament renderer consumes its output on every platform.
 *
 * ## Update model
 *
 * Tracking is **pulled, not pushed**: the runtime's frame ticker (vsync on
 * the platform main thread) calls [update] once per frame and hands the
 * resulting [TrackingFrame] to the renderer. Providers must not spin their
 * own callbacks into user code.
 *
 * ## Threading
 *
 * [update], [hitTest] and anchor mutations are called from the frame thread
 * (platform main thread). [initialize]/[start]/[stop]/[dispose] are also
 * main-thread. Implementations are not required to be thread-safe beyond
 * exposing [state] safely.
 */
public interface TrackingProvider {
    /** Honest capability reporting — never fake an unsupported feature. */
    public val capabilities: TrackingCapabilities

    /** Observable session lifecycle state. */
    public val state: StateFlow<TrackingSessionState>

    /**
     * Prepares the platform session with the given configuration. Must be
     * called before [start]; throws on unrecoverable setup failure (e.g. AR
     * runtime not installed and installation declined).
     */
    public suspend fun initialize(config: TrackingConfig)

    /** Starts tracking; suspends until Running or a failure state. */
    public suspend fun start()

    /** Pauses tracking; [start] may be called again to resume. */
    public fun stop()

    /** Releases the platform session. The provider is unusable afterwards. */
    public fun dispose()

    /**
     * Pumps the platform session and returns the world state for this frame,
     * or null while tracking has no frame to offer (not started, paused, or
     * the platform skipped a frame).
     *
     * @param frameTimeNanos The vsync timestamp driving this frame
     */
    public fun update(frameTimeNanos: Long): TrackingFrame?

    /**
     * Casts a ray from normalized screen coordinates (0..1, top-left origin)
     * into the real world, testing detected planes and depth. Uses the most
     * recent frame; null when nothing is hit or tracking isn't running.
     */
    public fun hitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult?

    /**
     * Creates a world anchor at [pose] and returns its id, or null when the
     * platform refused (no tracking, anchor limit). Anchor states are
     * reported every frame via [TrackingFrame.anchors].
     */
    public fun createAnchor(pose: Pose): String?

    /** Removes a previously created anchor. Unknown ids are ignored. */
    public fun removeAnchor(anchorId: String)

    /**
     * Persists [anchorId] beyond this session, returning an opaque handle to
     * store app-side and later pass to [resolveAnchor] (v1 M12.3 shape).
     * Null when unsupported ([TrackingCapabilities.supportsAnchorPersistence])
     * or the anchor isn't in a persistable state.
     */
    public suspend fun persistAnchor(anchorId: String): String? = null

    /** Deletes a persisted anchor handle from platform storage. */
    public suspend fun deletePersistedAnchor(handle: String) {}

    /**
     * Restores a persisted (or, from R7, geospatial) anchor handle as a live
     * anchor, returning its anchor id — resolution progress is reported via
     * [TrackingFrame.anchors]. Null when the handle is invalid or
     * persistence is unsupported.
     */
    public suspend fun resolveAnchor(handle: String): String? = null
}

/** What this provider can actually do on this device — reported honestly. */
public data class TrackingCapabilities(
    val supportsPlaneDetection: Boolean,
    val supportsImageTracking: Boolean,
    /** Hardware depth (ARCore Depth API / LiDAR) available on this device. */
    val supportsDepth: Boolean,
    /** HDR light estimation: spherical harmonics + main light (+ cubemap). */
    val supportsHdrLightEstimation: Boolean,
    val supportsAnchors: Boolean,
    val supportsAnchorPersistence: Boolean,
    /** Geospatial (VPS) anchors — the R7 layer. */
    val supportsGeospatial: Boolean = false,
    val maxAnchors: Int? = null,
)

/** Requested session behavior; providers honor what the device supports. */
public data class TrackingConfig(
    val planeDetection: Boolean = true,
    /** The provider picks its best mode (ENVIRONMENTAL_HDR on ARCore). */
    val lightEstimation: Boolean = true,
    /** Produce [TrackingFrame.depthImage] on depth-capable devices. */
    val depth: Boolean = false,
    val referenceImages: List<ReferenceImage> = emptyList(),
    /** Enable the geospatial subsystem (R7; needs platform API keys). */
    val geospatial: Boolean = false,
    val nearPlane: Float = 0.1f,
    val farPlane: Float = 100f,
)

/** Session lifecycle (v1's EngineState minus rendering concerns). */
public sealed class TrackingSessionState {
    public data object Uninitialized : TrackingSessionState()

    public data object Initializing : TrackingSessionState()

    public data object Ready : TrackingSessionState()

    public data object Running : TrackingSessionState()

    public data class Failed(
        val message: String,
        val cause: Throwable? = null,
    ) : TrackingSessionState()

    public data object Disposed : TrackingSessionState()
}
