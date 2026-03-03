package com.spatialkit.scene

import com.spatialkit.engine.AnchorRequest
import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.engine.AnchorType
import com.spatialkit.engine.Pose
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
class SpatialAnchor(
    val id: String,
    val type: AnchorType,
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
    var status: AnchorStatus = AnchorStatus.PENDING
        private set

    /**
     * Current pose of the anchor in world space.
     *
     * null if anchor is not tracking (PENDING or FAILED state).
     */
    var pose: Pose? = null
        private set

    /**
     * Whether the anchor is currently tracking.
     */
    val isTracking: Boolean
        get() = status == AnchorStatus.TRACKING

    /**
     * Whether the anchor has failed.
     */
    val hasFailed: Boolean
        get() = status == AnchorStatus.ERROR

    /**
     * Whether the anchor is pending creation.
     */
    val isPending: Boolean
        get() = status == AnchorStatus.PENDING

    private var onTrackingCallback: ((SpatialAnchor) -> Unit)? = null
    private var onLostCallback: ((SpatialAnchor) -> Unit)? = null
    private var onFailedCallback: ((SpatialAnchor) -> Unit)? = null

    /**
     * Register a callback for when anchor starts tracking.
     *
     * Called when status changes to TRACKING.
     *
     * @param callback Function to call when tracking starts
     */
    fun onTrack(callback: (SpatialAnchor) -> Unit) {
        onTrackingCallback = callback
        // If already tracking, call immediately
        if (isTracking) {
            callback(this)
        }
    }

    /**
     * Register a callback for when anchor loses tracking.
     *
     * Called when status changes to PAUSED or LOST.
     *
     * @param callback Function to call when tracking is lost
     */
    fun onLost(callback: (SpatialAnchor) -> Unit) {
        onLostCallback = callback
    }

    /**
     * Register a callback for when anchor creation fails.
     *
     * Called when status changes to ERROR.
     *
     * @param callback Function to call on failure
     */
    fun onFailed(callback: (SpatialAnchor) -> Unit) {
        onFailedCallback = callback
        // If already failed, call immediately
        if (hasFailed) {
            callback(this)
        }
    }

    /**
     * Update anchor state from engine.
     *
     * Called internally by SpatialScene.
     */
    internal fun update(state: AnchorState) {
        val previousStatus = status

        status = state.status
        pose = state.pose

        // Trigger callbacks on state changes
        when {
            status == AnchorStatus.TRACKING && previousStatus != AnchorStatus.TRACKING -> {
                onTrackingCallback?.invoke(this)
            }

            (status == AnchorStatus.PAUSED || status == AnchorStatus.LOST) &&
                previousStatus == AnchorStatus.TRACKING -> {
                onLostCallback?.invoke(this)
            }

            status == AnchorStatus.ERROR && previousStatus != AnchorStatus.ERROR -> {
                onFailedCallback?.invoke(this)
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

    companion object {
        private var anchorCounter = 0

        /**
         * Generate a unique anchor ID.
         */
        fun generateId(): String = "anchor-${anchorCounter++}-${Random.nextInt(1000)}"
    }
}
