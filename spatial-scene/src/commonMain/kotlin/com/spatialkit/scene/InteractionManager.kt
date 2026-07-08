package com.spatialkit.scene

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.types.Ray
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.Vec3
import com.spatialkit.engine.screenPointToRay

/**
 * Routes spatial gestures to scene nodes.
 *
 * The platform input layer submits [GestureEvent]s from the UI thread via
 * [submit]; the session drains and applies them **on the render thread** at
 * the start of each frame, before the app's frame callback — honoring the
 * scene threading contract (node state is only mutated from the frame
 * callback's thread).
 *
 * Routing:
 * - **Tap** → picks the mesh under the finger, walks up the parent chain to
 *   the nearest node with [SpatialNode.onTap], and invokes it. If nothing
 *   interactive is hit, [onTapMissed] fires instead (use it for e.g.
 *   tap-to-place).
 * - **Drag / pinch / twist** → the node picked when the gesture starts
 *   (nearest self-or-ancestor with any behavior configured) is the target for
 *   the whole gesture; each update applies [SpatialNode.dragBehavior],
 *   [SpatialNode.scaleBehavior] and [SpatialNode.rotateBehavior].
 *
 * Access it as `session.interactions`.
 */
public class InteractionManager internal constructor() {
    /**
     * Called (on the render thread) when a tap hits no interactive node —
     * including taps that hit nothing at all. Typical use: place content via
     * `performHitTest`.
     *
     * Coordinates are normalized: (0,0) = top-left, (1,1) = bottom-right.
     */
    public var onTapMissed: ((screenX: Float, screenY: Float) -> Unit)? = null

    private val lock = SpatialLock()
    private val pending = ArrayDeque<GestureEvent>()

    // Gesture state below is touched only on the render thread
    private var activeNode: SpatialNode? = null
    private var grabPoint: Vec3 = Vec3.ZERO
    private var grabOffset: Vec3 = Vec3.ZERO

    /** Scale captured when a node is first scaled; clamp reference. */
    private val scaleBaselines = mutableMapOf<String, Vec3>()

    /**
     * Queues a gesture event for processing on the next frame.
     * Safe to call from any thread.
     */
    public fun submit(event: GestureEvent) {
        lock.withLock {
            pending.addLast(event)
            // A stalled render loop must not grow the queue without bound
            while (pending.size > MAX_PENDING_EVENTS) pending.removeFirst()
        }
    }

    /**
     * Drains and applies queued events. Called by [SpatialSession] each frame
     * on the render thread.
     */
    internal fun processFrame(
        frame: FrameData,
        scene: SpatialScene,
        raycast: (origin: Vec3, direction: Vec3) -> RaycastResult?,
        hitTest: (screenX: Float, screenY: Float) -> HitTestResult?,
    ) {
        val events =
            lock.withLock {
                if (pending.isEmpty()) return
                val drained = pending.toList()
                pending.clear()
                drained
            }

        events.forEach { event ->
            when (event) {
                is GestureEvent.Tap -> handleTap(event, frame, scene, raycast)
                is GestureEvent.TransformStart -> beginTransform(event, frame, scene, raycast)
                is GestureEvent.Transform -> applyTransform(event, frame, hitTest)
                is GestureEvent.TransformEnd -> activeNode = null
            }
        }
    }

    private fun handleTap(
        event: GestureEvent.Tap,
        frame: FrameData,
        scene: SpatialScene,
        raycast: (Vec3, Vec3) -> RaycastResult?,
    ) {
        val hit = pick(event.screenX, event.screenY, frame, raycast)
        val node = hit?.let { resolveNode(scene, it.nodeId) }
        val target = node?.firstSelfOrAncestor { it.onTap != null }
        if (target != null) {
            target.onTap?.invoke(
                TapEvent(
                    node = target,
                    position = hit.position,
                    normal = hit.normal,
                    distance = hit.distance,
                ),
            )
        } else {
            onTapMissed?.invoke(event.screenX, event.screenY)
        }
    }

    private fun beginTransform(
        event: GestureEvent.TransformStart,
        frame: FrameData,
        scene: SpatialScene,
        raycast: (Vec3, Vec3) -> RaycastResult?,
    ) {
        val hit = pick(event.screenX, event.screenY, frame, raycast) ?: return
        val node = resolveNode(scene, hit.nodeId) ?: return
        val target =
            node.firstSelfOrAncestor {
                it.dragBehavior != null || it.scaleBehavior != null || it.rotateBehavior != null
            } ?: return

        activeNode = target
        grabPoint = hit.position
        // Keep the point grabbed under the finger instead of snapping the
        // node's origin to it
        grabOffset = target.worldPosition - hit.position
    }

    private fun applyTransform(
        event: GestureEvent.Transform,
        frame: FrameData,
        hitTest: (Float, Float) -> HitTestResult?,
    ) {
        val target = activeNode ?: return

        target.dragBehavior?.let { behavior ->
            val surfacePoint =
                when (behavior.mode) {
                    DragMode.DetectedSurfaces ->
                        hitTest(event.screenX, event.screenY)?.position
                            ?: horizontalPlanePoint(event, frame)

                    DragMode.HorizontalPlane -> horizontalPlanePoint(event, frame)
                }
            if (surfacePoint != null) {
                target.worldPosition = surfacePoint + grabOffset
                // Follow surface changes (e.g. table → floor), so the
                // fallback plane tracks where the node actually is
                grabPoint = surfacePoint
            }
        }

        target.scaleBehavior?.let { behavior ->
            if (event.zoom != 1f && event.zoom > 0f) {
                val baseline =
                    scaleBaselines.getOrPut(target.id) {
                        // Bounded: stale entries (removed nodes) age out first
                        if (scaleBaselines.size >= MAX_SCALE_BASELINES) {
                            scaleBaselines.remove(scaleBaselines.keys.first())
                        }
                        target.scale
                    }
                val reference = if (baseline.x != 0f) baseline.x else 1f
                val factor =
                    ((target.scale.x * event.zoom) / reference)
                        .coerceIn(behavior.minFactor, behavior.maxFactor)
                target.scale = baseline * factor
            }
        }

        target.rotateBehavior?.let { behavior ->
            if (event.twist != 0f) {
                // Screen-clockwise twist turns the node clockwise seen from
                // along the (default up) axis, hence the negation
                target.rotation =
                    Quaternion.fromAxisAngle(behavior.axis, -event.twist) * target.rotation
            }
        }
    }

    /**
     * Ray through the current pointer intersected with the horizontal plane
     * through the last grab point — the drag fallback that needs no detected
     * surfaces. Null when the ray runs (near-)parallel to the plane.
     */
    private fun horizontalPlanePoint(
        event: GestureEvent.Transform,
        frame: FrameData,
    ): Vec3? {
        val ray = frame.screenPointToRay(event.screenX, event.screenY)
        val t = ray.intersectsPlane(grabPoint, Vec3.UP) ?: return null
        return ray.pointAt(t)
    }

    private fun pick(
        screenX: Float,
        screenY: Float,
        frame: FrameData,
        raycast: (Vec3, Vec3) -> RaycastResult?,
    ): RaycastResult? {
        val ray: Ray = frame.screenPointToRay(screenX, screenY)
        return raycast(ray.origin, ray.direction)
    }

    /**
     * Maps an engine node id back to the scene-graph node. Renderers may
     * report synthetic sub-ids ("nodeId#mesh") for meshes that render as part
     * of a composite node; those resolve to the owning node.
     */
    private fun resolveNode(
        scene: SpatialScene,
        nodeId: String?,
    ): SpatialNode? {
        if (nodeId == null) return null
        scene.findNodeById(nodeId)?.let { return it }
        val ownerId = nodeId.substringBefore('#')
        return if (ownerId != nodeId) scene.findNodeById(ownerId) else null
    }

    private inline fun SpatialNode.firstSelfOrAncestor(predicate: (SpatialNode) -> Boolean): SpatialNode? {
        var node: SpatialNode? = this
        while (node != null) {
            if (predicate(node)) return node
            node = node.parent
        }
        return null
    }

    private companion object {
        const val MAX_PENDING_EVENTS = 64
        const val MAX_SCALE_BASELINES = 256
    }
}
