package com.spatialkit.scene

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.render.AnchorType
import com.spatialkit.render.AnimationState
import com.spatialkit.render.ModelHandle
import com.spatialkit.render.RenderCapabilities
import com.spatialkit.render.SceneData
import com.spatialkit.render.SceneRenderer
import com.spatialkit.tracking.AnchorState
import com.spatialkit.tracking.AnchorStatus
import com.spatialkit.tracking.DetectedPlane
import com.spatialkit.tracking.HitTestResult
import com.spatialkit.tracking.PlaneType
import com.spatialkit.tracking.Pose
import com.spatialkit.tracking.TrackingCapabilities
import com.spatialkit.tracking.TrackingConfig
import com.spatialkit.tracking.TrackingFrame
import com.spatialkit.tracking.TrackingProvider
import com.spatialkit.tracking.TrackingSessionState
import com.spatialkit.tracking.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpatialSession2Test {
    private class FakeProvider : TrackingProvider {
        override val capabilities =
            TrackingCapabilities(
                supportsPlaneDetection = true,
                supportsImageTracking = false,
                supportsDepth = false,
                supportsHdrLightEstimation = false,
                supportsAnchors = true,
                supportsAnchorPersistence = false,
            )
        override val state: StateFlow<TrackingSessionState> =
            MutableStateFlow(TrackingSessionState.Running)

        val created = mutableListOf<Pair<String, Pose>>()
        val removed = mutableListOf<String>()
        var nextAnchorId = 1
        var anchorStates: List<AnchorState> = emptyList()

        override suspend fun initialize(config: TrackingConfig) {}

        override suspend fun start() {}

        override fun stop() {}

        override fun dispose() {}

        override fun update(frameTimeNanos: Long): TrackingFrame? = null

        override fun hitTest(
            screenX: Float,
            screenY: Float,
        ): HitTestResult? = null

        override fun createAnchor(pose: Pose): String {
            val id = "provider-${nextAnchorId++}"
            created += id to pose
            return id
        }

        override fun removeAnchor(anchorId: String) {
            removed += anchorId
        }
    }

    private class FakeRenderer : SceneRenderer {
        override val capabilities =
            RenderCapabilities(
                supportsIbl = false,
                supportsShadows = false,
                supportsOcclusion = false,
                supportsAnimation = false,
            )
        val submitted = mutableListOf<SceneData>()

        override fun submitScene(scene: SceneData) {
            submitted += scene
        }

        override fun render(
            frame: TrackingFrame?,
            frameTimeNanos: Long,
        ) {}

        override suspend fun loadModel(glb: ByteArray): ModelHandle = ModelHandle.create(1L, emptyList())

        override fun setModelTransform(
            handle: ModelHandle,
            matrix: FloatArray,
        ) {}

        override fun setAnimation(
            handle: ModelHandle,
            state: AnimationState?,
        ) {}

        override fun destroyModel(handle: ModelHandle) {}

        override fun dispose() {}
    }

    private fun frame(
        planes: List<DetectedPlane> = emptyList(),
        anchors: List<AnchorState> = emptyList(),
    ) = TrackingFrame(
        timestamp = 1L,
        deltaTime = 0.016f,
        cameraPose = Pose(Vec3.ZERO, Quaternion.IDENTITY),
        viewMatrix = identity(),
        projectionMatrix = identity(),
        trackingState = TrackingState.NORMAL,
        planes = planes,
        anchors = anchors,
    )

    private fun identity() =
        FloatArray(16).also {
            it[0] = 1f
            it[5] = 1f
            it[10] = 1f
            it[15] = 1f
        }

    @Test
    fun submitsSceneEveryTickEvenWithoutTracking() {
        val renderer = FakeRenderer()
        val session = SpatialSession2(FakeProvider(), renderer)
        session.scene.add(CubeNode(size = 0.1f))
        session.handleFrame(null)
        session.handleFrame(null)
        assertEquals(2, renderer.submitted.size)
        assertEquals(
            1,
            renderer.submitted
                .last()
                .nodes.size,
        )
    }

    @Test
    fun worldAnchorIsCreatedOnProviderAndTracksBack() {
        val provider = FakeProvider()
        val session = SpatialSession2(provider, FakeRenderer())
        val anchor =
            session.scene.createAnchor(
                AnchorType.World(Vec3(1f, 2f, 3f), Quaternion.IDENTITY),
            )

        session.handleFrame(frame())
        assertEquals(1, provider.created.size)
        assertEquals(Vec3(1f, 2f, 3f), provider.created[0].second.position)

        // Provider now reports the anchor as tracking; the scene anchor
        // updates under its own id with the provider pose.
        val providerId = provider.created[0].first
        session.handleFrame(
            frame(
                anchors =
                    listOf(
                        AnchorState(
                            id = providerId,
                            status = AnchorStatus.TRACKING,
                            pose = Pose(Vec3(1f, 2f, 3f), Quaternion.IDENTITY),
                        ),
                    ),
            ),
        )
        assertTrue(anchor.isTracking)
        assertEquals(Vec3(1f, 2f, 3f), anchor.pose?.position)
    }

    @Test
    fun planeAnchorWaitsForPlaneThenResolvesWithOffset() {
        val provider = FakeProvider()
        val session = SpatialSession2(provider, FakeRenderer())
        session.scene.createAnchor(
            AnchorType.Plane(planeId = "floor", offsetFromCenter = Vec3(0.5f, 0f, 0f)),
        )

        session.handleFrame(frame())
        assertEquals(0, provider.created.size, "must wait for the plane")

        val floor =
            DetectedPlane(
                id = "floor",
                type = PlaneType.HORIZONTAL_UP,
                centerPose = Pose(Vec3(0f, -1f, 0f), Quaternion.IDENTITY),
                extentX = 2f,
                extentZ = 2f,
                vertices = null,
            )
        session.handleFrame(frame(planes = listOf(floor)))
        assertEquals(1, provider.created.size)
        assertEquals(Vec3(0.5f, -1f, 0f), provider.created[0].second.position)
    }

    @Test
    fun removedAnchorIsRemovedFromProvider() {
        val provider = FakeProvider()
        val session = SpatialSession2(provider, FakeRenderer())
        val anchor = session.scene.createAnchor(AnchorType.World(Vec3.ZERO, Quaternion.IDENTITY))
        session.handleFrame(frame())
        val providerId = provider.created[0].first

        session.scene.removeAnchor(anchor)
        session.handleFrame(frame())
        assertEquals(listOf(providerId), provider.removed)
    }

    @Test
    fun persistentAnchorFailsHonestly() {
        val provider = FakeProvider()
        val session = SpatialSession2(provider, FakeRenderer())
        val anchor = session.scene.createAnchor(AnchorType.Persistent("old-handle"))

        session.handleFrame(frame())
        assertEquals(0, provider.created.size)
        session.handleFrame(frame())
        assertTrue(anchor.hasFailed)
        assertNull(anchor.pose)
    }

    @Test
    fun contentRaycastSeesLatestSnapshot() {
        val session = SpatialSession2(FakeProvider(), FakeRenderer())
        val cube = CubeNode(size = 1f)
        cube.position = Vec3(0f, 0f, -2f)
        session.scene.add(cube)

        assertNull(session.performRaycast(Vec3.ZERO, Vec3(0f, 0f, -1f)), "no snapshot before first tick")
        session.handleFrame(null)
        val hit = session.performRaycast(Vec3.ZERO, Vec3(0f, 0f, -1f))
        assertNotNull(hit)
        assertEquals(cube.id, hit.nodeId?.substringBefore('#'))
    }
}
