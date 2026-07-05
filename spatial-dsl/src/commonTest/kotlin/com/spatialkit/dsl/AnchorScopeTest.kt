package com.spatialkit.dsl

import com.spatialkit.core.math.Vec3
import com.spatialkit.engine.AnchorState
import com.spatialkit.engine.AnchorStatus
import com.spatialkit.engine.Pose
import com.spatialkit.engine.Quaternion
import kotlin.test.Test
import kotlin.test.assertEquals

class AnchorScopeTest {
    private fun trackingState(
        id: String,
        position: Vec3,
    ) = AnchorState(
        id = id,
        status = AnchorStatus.TRACKING,
        pose = Pose(position, Quaternion.IDENTITY),
    )

    private fun lostState(id: String) =
        AnchorState(
            id = id,
            status = AnchorStatus.LOST,
            pose = null,
        )

    @Test
    fun anchoredNodePositionedAtAnchorPlusOffset() {
        val scene =
            spatialScene {
                anchorAtPosition(Vec3(5f, 0f, 0f), id = "anchor") {
                    cube(id = "cube") {
                        position = Vec3(0f, 1f, 0f) // offset above the anchor
                    }
                }
            }

        scene.getAnchor("anchor")!!.update(trackingState("anchor", Vec3(5f, 0f, 0f)))

        val cube = scene.findNodeById("cube")!!
        assertEquals(5f, cube.position.x, 0.001f)
        assertEquals(1f, cube.position.y, 0.001f)
    }

    @Test
    fun reTrackingDoesNotAccumulateOffsets() {
        val scene =
            spatialScene {
                anchorAtPosition(Vec3.ZERO, id = "anchor") {
                    cube(id = "cube") {
                        position = Vec3(0f, 1f, 0f)
                    }
                }
            }
        val anchor = scene.getAnchor("anchor")!!
        val cube = scene.findNodeById("cube")!!

        // Track, lose, re-track several times at the same anchor pose
        repeat(3) {
            anchor.update(trackingState("anchor", Vec3(2f, 0f, 0f)))
            anchor.update(lostState("anchor"))
        }
        anchor.update(trackingState("anchor", Vec3(2f, 0f, 0f)))

        // Position must be anchor + original offset, not compounded
        assertEquals(2f, cube.position.x, 0.001f)
        assertEquals(1f, cube.position.y, 0.001f)
        assertEquals(0f, cube.position.z, 0.001f)
    }

    @Test
    fun reTrackingFollowsAnchorToNewPose() {
        val scene =
            spatialScene {
                anchorAtPosition(Vec3.ZERO, id = "anchor") {
                    cube(id = "cube") {
                        position = Vec3(0f, 1f, 0f)
                    }
                }
            }
        val anchor = scene.getAnchor("anchor")!!
        val cube = scene.findNodeById("cube")!!

        anchor.update(trackingState("anchor", Vec3(1f, 0f, 0f)))
        anchor.update(lostState("anchor"))
        anchor.update(trackingState("anchor", Vec3(4f, 0f, 0f)))

        assertEquals(4f, cube.position.x, 0.001f)
        assertEquals(1f, cube.position.y, 0.001f)
    }
}
