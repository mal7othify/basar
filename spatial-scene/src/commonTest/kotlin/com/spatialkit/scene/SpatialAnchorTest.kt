package com.spatialkit.scene

import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.render.AnchorType
import com.spatialkit.engine.Pose
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpatialAnchorTest {
    private fun anchor() = SpatialAnchor("a1", AnchorType.World(Vec3.ZERO, Quaternion.IDENTITY))

    private fun state(status: AnchorStatus) =
        AnchorState(
            id = "a1",
            status = status,
            pose = Pose(Vec3(1f, 2f, 3f), Quaternion.IDENTITY),
        )

    @Test
    fun startsPending() {
        val a = anchor()
        assertTrue(a.isPending)
        assertFalse(a.isTracking)
        assertFalse(a.hasFailed)
    }

    @Test
    fun trackingTransitionFiresCallbackAndSetsPose() {
        val a = anchor()
        var fired = 0
        a.onTrack { fired++ }

        a.update(state(AnchorStatus.TRACKING))
        assertEquals(1, fired)
        assertTrue(a.isTracking)
        assertEquals(Vec3(1f, 2f, 3f), a.pose?.position)

        // Staying in TRACKING does not re-fire
        a.update(state(AnchorStatus.TRACKING))
        assertEquals(1, fired)
    }

    @Test
    fun multipleCallbacksAllFire() {
        val a = anchor()
        var first = 0
        var second = 0
        a.onTrack { first++ }
        a.onTrack { second++ }

        a.update(state(AnchorStatus.TRACKING))
        assertEquals(1, first, "first registered callback must fire")
        assertEquals(1, second, "second registered callback must not replace the first")
    }

    @Test
    fun unsubscribeHandleStopsCallback() {
        val a = anchor()
        var fired = 0
        val unsubscribe = a.onTrack { fired++ }
        unsubscribe()

        a.update(state(AnchorStatus.TRACKING))
        assertEquals(0, fired)
    }

    @Test
    fun onTrackFiresImmediatelyIfAlreadyTracking() {
        val a = anchor()
        a.update(state(AnchorStatus.TRACKING))

        var fired = 0
        a.onTrack { fired++ }
        assertEquals(1, fired)
    }

    @Test
    fun lostAndRegainedFiresBothCallbacks() {
        val a = anchor()
        var tracked = 0
        var lost = 0
        a.onTrack { tracked++ }
        a.onLost { lost++ }

        a.update(state(AnchorStatus.TRACKING))
        a.update(state(AnchorStatus.LOST))
        a.update(state(AnchorStatus.TRACKING))

        assertEquals(2, tracked)
        assertEquals(1, lost)
    }

    @Test
    fun errorFiresFailureCallback() {
        val a = anchor()
        var failed = 0
        a.onFailed { failed++ }

        a.update(state(AnchorStatus.ERROR))
        assertEquals(1, failed)
        assertTrue(a.hasFailed)
    }

    @Test
    fun generateIdIsUnique() {
        val ids = (1..10_000).map { SpatialAnchor.generateId() }.toSet()
        assertEquals(10_000, ids.size)
    }
}
