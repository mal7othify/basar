package com.spatialkit.scene

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneRaycaster
import com.spatialkit.engine.screenPointToRay
import com.spatialkit.render.AnchorRequest
import com.spatialkit.render.AnchorType
import com.spatialkit.render.SceneNode
import com.spatialkit.render.SceneRenderer
import com.spatialkit.tracking.TrackingConfig
import com.spatialkit.tracking.TrackingFrame
import com.spatialkit.tracking.TrackingProvider

/**
 * The v2 session: the v1 [SpatialSession] programming model — a mutable
 * [SpatialScene], gesture routing, per-frame callback, automatic anchor
 * updates — running on the v2 runtime ([TrackingProvider] + [SceneRenderer]
 * composed by [SpatialRuntime2]).
 *
 * Per tick (frame thread): tracking update → anchor states applied to the
 * scene → queued gestures → user [onFrame] callback (which mutates nodes,
 * v1's update model) → scene snapshot → [SceneRenderer.submitScene] →
 * render. Exceptions are reported once as [EngineError.RuntimeError] and
 * the loop keeps ticking.
 *
 * The frame callback still receives v1's [FrameData] (adapted from
 * [TrackingFrame]) so existing demo/app code ports without changes.
 *
 * Named SpatialSession2 only while the v1 SpatialSession exists — renamed
 * to SpatialSession when the v1 engine modules are deleted.
 */
public class SpatialSession2(
    provider: TrackingProvider,
    renderer: SceneRenderer,
) {
    public val runtime: SpatialRuntime2 = SpatialRuntime2(provider, renderer)

    /** The mutable scene graph; submitted to the renderer every frame. */
    public val scene: SpatialScene = SpatialScene()

    /**
     * Gesture routing for this session. The platform input layer submits
     * gesture events here; they are applied to the scene on the frame
     * thread each tick.
     */
    public val interactions: InteractionManager = InteractionManager()

    private val provider: TrackingProvider get() = runtime.provider
    private val renderer: SceneRenderer get() = runtime.renderer

    private var userFrameCallback: ((FrameData) -> Unit)? = null
    private var errorCallback: ((EngineError) -> Unit)? = null
    private var errorReported = false

    /** Latest adapted frame, cached for screen-point picking. */
    private var lastFrame: FrameData? = null

    /** Latest scene snapshot, for content raycasts between submits. */
    private var lastNodes: List<SceneNode> = emptyList()

    /** Scene anchor id → provider anchor id, for anchors already created. */
    private val anchorBindings = LinkedHashMap<String, String>()

    /** Scene anchor ids whose type can never resolve on this provider. */
    private val failedAnchors = mutableSetOf<String>()

    /**
     * Starts tracking and the frame loop. Suspends until tracking runs;
     * throws on setup failure.
     */
    public suspend fun start(
        config: TrackingConfig = TrackingConfig(),
        onFrame: (FrameData) -> Unit = {},
        onError: (EngineError) -> Unit = {},
    ) {
        userFrameCallback = onFrame
        errorCallback = onError
        errorReported = false
        runtime.onFrame = ::handleFrame
        runtime.onFrameError = { t ->
            if (!errorReported) {
                errorReported = true
                errorCallback?.invoke(
                    EngineError.RuntimeError(
                        reason = "Frame loop error: ${t.message}",
                        cause = t,
                    ),
                )
            }
        }
        runtime.start(config)
    }

    /** Stops the frame loop and pauses tracking; [start] resumes. */
    public fun stop() {
        runtime.stop()
    }

    /** Stops and releases the provider, renderer, and scene. */
    public fun dispose() {
        runtime.dispose()
        scene.clear()
        anchorBindings.clear()
        failedAnchors.clear()
        userFrameCallback = null
        errorCallback = null
        lastFrame = null
        lastNodes = emptyList()
    }

    /**
     * Real-world hit test from normalized screen coordinates
     * ((0,0) = top-left, (1,1) = bottom-right).
     */
    public fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? = provider.hitTest(screenX, screenY)?.toEngine()

    /** World-space raycast against scene content (CPU-side, renderer-free). */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = SceneRaycaster.raycast(lastNodes, origin, direction)

    /**
     * Picks the scene node under a screen point (normalized coordinates)
     * by unprojecting through the latest frame's camera.
     */
    public fun raycastNodeAt(
        screenX: Float,
        screenY: Float,
    ): RaycastResult? {
        val frame = lastFrame ?: return null
        val ray = frame.screenPointToRay(screenX, screenY)
        return performRaycast(ray.origin, ray.direction)
    }

    // Internal (not private) so unit tests can drive ticks without a ticker.
    internal fun handleFrame(trackingFrame: TrackingFrame?) {
        if (trackingFrame != null) {
            val frameData = trackingFrame.toFrameData()
            lastFrame = frameData

            scene.updateAnchors(buildAnchorStates(trackingFrame))

            // Gestures before app logic so the frame callback observes
            // post-gesture node state (v1 ordering).
            interactions.processFrame(
                frame = frameData,
                scene = scene,
                raycast = ::performRaycast,
                hitTest = ::performHitTest,
            )

            userFrameCallback?.invoke(frameData)
        }

        val sceneData = scene.toSceneData()
        lastNodes = sceneData.nodes
        syncAnchorRequests(sceneData.anchors, trackingFrame)
        renderer.submitScene(sceneData)
        // runtime renders after this callback returns
    }

    /**
     * Routes scene anchor requests to the provider: creates provider
     * anchors for new requests (retrying ones that are not yet resolvable,
     * e.g. a plane anchor whose plane has not been detected), and removes
     * provider anchors whose requests disappeared.
     */
    private fun syncAnchorRequests(
        requests: List<AnchorRequest>,
        frame: TrackingFrame?,
    ) {
        val requestedIds = requests.mapTo(mutableSetOf()) { it.id }

        val bindingIterator = anchorBindings.entries.iterator()
        while (bindingIterator.hasNext()) {
            val (sceneId, providerId) = bindingIterator.next()
            if (sceneId !in requestedIds) {
                provider.removeAnchor(providerId)
                bindingIterator.remove()
            }
        }
        failedAnchors.retainAll(requestedIds)

        for (request in requests) {
            if (request.id in anchorBindings || request.id in failedAnchors) continue
            val pose =
                when (val type = request.type) {
                    is AnchorType.World ->
                        com.spatialkit.tracking.Pose(type.position, type.rotation)

                    is AnchorType.HitTest ->
                        com.spatialkit.tracking.Pose(type.hitPoint, Quaternion.IDENTITY)

                    is AnchorType.Plane -> {
                        val plane = frame?.planes?.firstOrNull { it.id == type.planeId }
                        plane?.let {
                            com.spatialkit.tracking.Pose(
                                position =
                                    it.centerPose.position +
                                        (it.centerPose.rotation * type.offsetFromCenter),
                                rotation = it.centerPose.rotation,
                            )
                        } // null → not resolvable yet, retried next tick
                    }

                    is AnchorType.Persistent -> {
                        // No v2 provider supports anchor persistence yet
                        // (returns to the roadmap with R7 geospatial);
                        // honest failure instead of a silent pin.
                        failedAnchors.add(request.id)
                        null
                    }
                }
            if (pose != null) {
                provider.createAnchor(pose)?.let { providerId ->
                    anchorBindings[request.id] = providerId
                }
            }
        }
    }

    /** Provider anchor states remapped to scene anchor ids. */
    private fun buildAnchorStates(frame: TrackingFrame): List<com.spatialkit.engine.AnchorState> {
        if (anchorBindings.isEmpty() && failedAnchors.isEmpty()) return emptyList()
        val providerStates = frame.anchors.associateBy { it.id }
        return buildList {
            for ((sceneId, providerId) in anchorBindings) {
                val state = providerStates[providerId]
                add(
                    com.spatialkit.engine.AnchorState(
                        id = sceneId,
                        status = state?.status?.toEngine() ?: com.spatialkit.engine.AnchorStatus.PENDING,
                        pose = state?.pose?.toEngine(),
                    ),
                )
            }
            for (sceneId in failedAnchors) {
                add(
                    com.spatialkit.engine.AnchorState(
                        id = sceneId,
                        status = com.spatialkit.engine.AnchorStatus.ERROR,
                        pose = null,
                    ),
                )
            }
        }
    }
}
