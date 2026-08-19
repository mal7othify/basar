package com.spatialkit.scene

import com.spatialkit.engine.AnchorRequest
import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.engine.Pose
import com.spatialkit.render.AnchorType
import kotlin.random.Random

/**
 * A spatial anchor that pins content to the real world.
 *
 * Anchors are used in AR to:
 * - Place virtual objects on detected planes
 * - Pin content to specific locations
 * - Persist locations across sessions (cloud anchors)
 *
 * Usage:
 * ```kotlin
 * // Create anchor on detected plane
 * val plane = frameData.detectedPlanes.first()
 * val anchor = scene.createAnchor(
 *     type = AnchorType.Plane(plane.id)
 * )
 *
 * // Attach nodes to anchor
 * val cube = CubeNode()
 * anchor.onTrack {
 *     cube.position = anchor.pose!!.position
 *     cube.rotation = anchor.pose!!.rotation
 * }
 * ```
 */
public class SpatialAnchor(
    public val id: String,
    public val type: AnchorType,
) {
    /**
     * Current tracking status of the anchor.
     *
     * - PENDING: Anchor creation in progress
     * - TRACKING: Anchor is actively tracking
     * - PAUSED: Tracking temporarily lost
     * - LOST: Tracking permanently lost
     * - ERROR: Anchor creation failed
     */
    public var status: AnchorStatus = AnchorStatus.PENDING
        private set

    /**
     * Current pose of the anchor in world space.
     *
     * null if anchor is not tracking (PENDING or FAILED state).
     */
    public var pose: Pose? = null
        private set

    /**
     * Whether the anchor is currently tracking.
     */
    public val isTracking: Boolean
        get() = status == AnchorStatus.TRACKING

    /**
     * Whether the anchor has failed.
     */
    public val hasFailed: Boolean
        get() = status == AnchorStatus.ERROR

    /**
     * Whether the anchor is pending creation.
     */
    public val isPending: Boolean
        get() = status == AnchorStatus.PENDING

    private val onTrackingCallbacks = mutableListOf<(SpatialAnchor) -> Unit>()
    private val onLostCallbacks = mutableListOf<(SpatialAnchor) -> Unit>()
    private val onFailedCallbacks = mutableListOf<(SpatialAnchor) -> Unit>()

    /**
     * Register a callback for when anchor starts tracking.
     *
     * Called when status changes to TRACKING. Multiple callbacks can be
     * registered; each registration is independent.
     *
     * @param callback Function to call when tracking starts
     * @return A handle that unregisters this callback when invoked
     */
    public fun onTrack(callback: (SpatialAnchor) -> Unit): () -> Unit {
        onTrackingCallbacks.add(callback)
        // If already tracking, call immediately
        if (isTracking) {
            callback(this)
        }
        return { onTrackingCallbacks.remove(callback) }
    }

    /**
     * Register a callback for when anchor loses tracking.
     *
     * Called when status changes to PAUSED or LOST. Multiple callbacks can be
     * registered; each registration is independent.
     *
     * @param callback Function to call when tracking is lost
     * @return A handle that unregisters this callback when invoked
     */
    public fun onLost(callback: (SpatialAnchor) -> Unit): () -> Unit {
        onLostCallbacks.add(callback)
        return { onLostCallbacks.remove(callback) }
    }

    /**
     * Register a callback for when anchor creation fails.
     *
     * Called when status changes to ERROR. Multiple callbacks can be
     * registered; each registration is independent.
     *
     * @param callback Function to call on failure
     * @return A handle that unregisters this callback when invoked
     */
    public fun onFailed(callback: (SpatialAnchor) -> Unit): () -> Unit {
        onFailedCallbacks.add(callback)
        // If already failed, call immediately
        if (hasFailed) {
            callback(this)
        }
        return { onFailedCallbacks.remove(callback) }
    }

    /**
     * Update anchor state from an engine-provided [AnchorState].
     *
     * Called by the session each frame for tracked anchors. Only call this
     * directly when integrating a custom engine that manages anchors itself.
     */
    public fun update(state: AnchorState) {
        val previousStatus = status

        status = state.status
        pose = state.pose

        // Trigger callbacks on state changes (snapshot the list so a callback
        // may unregister itself without breaking iteration)
        when {
            status == AnchorStatus.TRACKING && previousStatus != AnchorStatus.TRACKING -> {
                onTrackingCallbacks.toList().forEach { it(this) }
            }

            (status == AnchorStatus.PAUSED || status == AnchorStatus.LOST) &&
                previousStatus == AnchorStatus.TRACKING -> {
                onLostCallbacks.toList().forEach { it(this) }
            }

            status == AnchorStatus.ERROR && previousStatus != AnchorStatus.ERROR -> {
                onFailedCallbacks.toList().forEach { it(this) }
            }
        }
    }

    /**
     * Convert to engine anchor request.
     *
     * Called internally by SpatialScene.
     */
    internal fun toAnchorRequest(): AnchorRequest =
        AnchorRequest(
            id = id,
            type = type,
        )

    override fun toString(): String = "SpatialAnchor(id=$id, type=$type, status=$status, tracking=$isTracking)"

    public companion object {
        /**
         * Generate a unique anchor ID.
         *
         * Uses 64 bits of randomness: safe to call from any thread and the
         * collision probability is negligible.
         */
        public fun generateId(): String = "anchor-${Random.nextLong().toULong().toString(16)}"
    }
}
