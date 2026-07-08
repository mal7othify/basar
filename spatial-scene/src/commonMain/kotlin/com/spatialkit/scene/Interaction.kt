package com.spatialkit.scene

import com.spatialkit.engine.Vec3

/**
 * A tap that resolved to a scene node.
 *
 * Delivered to [SpatialNode.onTap] on the render thread (the same thread as
 * the session's frame callback), so handlers may mutate node state directly.
 *
 * @property node The node the tap resolved to (the handler's owner — the hit
 *   mesh itself or the nearest ancestor with a handler)
 * @property position World-space position where the tap ray hit
 * @property normal World-space surface normal at the hit point
 * @property distance Distance from the camera to the hit point
 */
public class TapEvent(
    public val node: SpatialNode,
    public val position: Vec3,
    public val normal: Vec3,
    public val distance: Float,
)

/**
 * How a draggable node follows the finger.
 */
public enum class DragMode {
    /**
     * Follow real-world surfaces via AR hit testing (crossing from a table to
     * the floor moves the node onto the floor). Falls back to
     * [HorizontalPlane] wherever no surface is detected under the finger.
     */
    DetectedSurfaces,

    /**
     * Slide on the horizontal plane through the point where the drag started.
     * Predictable, and works without any detected planes.
     */
    HorizontalPlane,
}

/**
 * Drag behavior of a node. Set via [SpatialNode.draggable].
 *
 * @property mode Which surface the node follows while dragged
 */
public class DragBehavior(
    public val mode: DragMode = DragMode.DetectedSurfaces,
)

/**
 * Pinch-to-scale behavior of a node. Set via [SpatialNode.scalable].
 *
 * Bounds are factors relative to the node's scale when it first started being
 * scaled, so repeated pinch gestures cannot drift the node outside
 * [minFactor]..[maxFactor] of its authored size.
 *
 * @property minFactor Smallest allowed scale, as a factor of the base scale
 * @property maxFactor Largest allowed scale, as a factor of the base scale
 */
public class ScaleBehavior(
    public val minFactor: Float = 0.25f,
    public val maxFactor: Float = 4f,
) {
    init {
        require(minFactor > 0f && maxFactor >= minFactor) {
            "Invalid scale bounds: minFactor=$minFactor, maxFactor=$maxFactor"
        }
    }
}

/**
 * Two-finger-twist rotation behavior of a node. Set via
 * [SpatialNode.rotatable].
 *
 * @property axis World-space rotation axis (default: [Vec3.UP], i.e. turning
 *   furniture on the floor)
 */
public class RotateBehavior(
    public val axis: Vec3 = Vec3.UP,
)

/**
 * Raw spatial gesture events, produced by the platform input layer
 * (`spatial-compose`) and consumed by [InteractionManager] on the render
 * thread.
 *
 * Screen coordinates are normalized: (0,0) = top-left, (1,1) = bottom-right.
 * Most applications never touch these — configure nodes with
 * [SpatialNode.onTap], [SpatialNode.draggable], [SpatialNode.scalable] and
 * [SpatialNode.rotatable] instead. Submit events yourself only when driving
 * interaction from a custom input source.
 */
public sealed interface GestureEvent {
    /** A tap (touch down + up without significant movement). */
    public data class Tap(
        val screenX: Float,
        val screenY: Float,
    ) : GestureEvent

    /**
     * A continuous transform gesture (drag / pinch / twist) began. The node
     * under the start point becomes the gesture's target.
     */
    public data class TransformStart(
        val screenX: Float,
        val screenY: Float,
    ) : GestureEvent

    /**
     * Incremental transform-gesture update.
     *
     * @property screenX Current centroid X (normalized)
     * @property screenY Current centroid Y (normalized)
     * @property zoom Multiplicative pinch factor since the previous event (1 = none)
     * @property twist Two-finger twist since the previous event, in radians;
     *   positive = clockwise on screen
     */
    public data class Transform(
        val screenX: Float,
        val screenY: Float,
        val zoom: Float = 1f,
        val twist: Float = 0f,
    ) : GestureEvent

    /** The transform gesture ended (all pointers up or cancelled). */
    public data object TransformEnd : GestureEvent
}
