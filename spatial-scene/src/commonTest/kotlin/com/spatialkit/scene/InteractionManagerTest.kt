package com.spatialkit.scene

import com.spatialkit.core.math.Quaternion
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.Color
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.Pose
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.TrackingState
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InteractionManagerTest {
    private val manager = InteractionManager()
    private val scene = SpatialScene()

    /** Camera at (0, 2, 0) looking straight down, 90° FOV. */
    private fun lookingDownFrame(): FrameData {
        val proj = FloatArray(16)
        proj[0] = 1f
        proj[5] = 1f
        proj[10] = -1.001f
        proj[11] = -1f
        proj[14] = -0.2f
        return FrameData(
            timestamp = 0L,
            deltaTime = 0.016f,
            cameraPose =
                Pose(
                    position = Vec3(0f, 2f, 0f),
                    rotation = Quaternion.fromAxisAngle(Vec3(1f, 0f, 0f), -PI.toFloat() / 2f),
                ),
            projectionMatrix = proj,
            viewMatrix = FloatArray(16),
            trackingState = TrackingState.NORMAL,
            detectedPlanes = emptyList(),
            lightEstimate = null,
            inputSources = emptyList(),
            anchors = emptyList(),
        )
    }

    private fun hitOn(
        node: SpatialNode,
        position: Vec3 = node.worldPosition,
        nodeId: String = node.id,
    ): (Vec3, Vec3) -> RaycastResult? =
        { origin, _ ->
            RaycastResult(
                position = position,
                normal = Vec3(0f, 1f, 0f),
                distance = (position - origin).length(),
                nodeId = nodeId,
            )
        }

    private val missRaycast: (Vec3, Vec3) -> RaycastResult? = { _, _ -> null }
    private val noSurfaces: (Float, Float) -> HitTestResult? = { _, _ -> null }

    private fun process(
        raycast: (Vec3, Vec3) -> RaycastResult? = missRaycast,
        hitTest: (Float, Float) -> HitTestResult? = noSurfaces,
    ) = manager.processFrame(lookingDownFrame(), scene, raycast, hitTest)

    // === Taps ===

    @Test
    fun tapRoutesToHitNode() {
        var tapped: TapEvent? = null
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.onTap = { tapped = it }
        scene.add(cube)

        manager.submit(GestureEvent.Tap(0.5f, 0.5f))
        process(raycast = hitOn(cube))

        assertNotNull(tapped)
        assertEquals(cube.id, tapped.node.id)
    }

    @Test
    fun tapPropagatesToAncestorWithHandler() {
        var tappedNode: SpatialNode? = null
        val child = CubeNode(size = 0.1f, color = Color.White)
        val group = GroupNode()
        group.addChild(child)
        group.onTap = { tappedNode = it.node }
        scene.add(group)

        manager.submit(GestureEvent.Tap(0.5f, 0.5f))
        process(raycast = hitOn(child))

        assertEquals(group.id, tappedNode?.id)
    }

    @Test
    fun tapResolvesSyntheticMeshSubId() {
        // Composite nodes report "ownerId#mesh" from renderers
        var tapped = false
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.onTap = { tapped = true }
        scene.add(cube)

        manager.submit(GestureEvent.Tap(0.5f, 0.5f))
        process(raycast = hitOn(cube, nodeId = "${cube.id}#mesh"))

        assertTrue(tapped)
    }

    @Test
    fun tapWithoutInteractiveNodeFiresOnTapMissed() {
        var missed: Pair<Float, Float>? = null
        manager.onTapMissed = { x, y -> missed = x to y }
        val plain = CubeNode(size = 0.1f, color = Color.White)
        scene.add(plain)

        // Hits a node, but nothing in its chain has onTap
        manager.submit(GestureEvent.Tap(0.25f, 0.75f))
        process(raycast = hitOn(plain))

        assertEquals(0.25f to 0.75f, missed)
    }

    @Test
    fun eventsAreOnlyAppliedOnProcessFrame() {
        var tapped = false
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.onTap = { tapped = true }
        scene.add(cube)

        manager.submit(GestureEvent.Tap(0.5f, 0.5f))

        assertTrue(!tapped, "handler must not run on submit")
        process(raycast = hitOn(cube))
        assertTrue(tapped)
    }

    // === Drag ===

    @Test
    fun dragMovesNodeAlongHorizontalPlane() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.position = Vec3.ZERO
        cube.draggable(DragMode.HorizontalPlane)
        scene.add(cube)

        // Grab at world origin (screen center, camera looking straight down)
        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        // Move finger right by a quarter screen: ndcX = 0.5 → camera-space
        // dir (0.5, ·, ·) → plane y=0 hit at x = 1 (camera height 2)
        manager.submit(GestureEvent.Transform(0.75f, 0.5f))
        process(raycast = hitOn(cube))

        assertEquals(1f, cube.position.x, 1e-4f)
        assertEquals(0f, cube.position.y, 1e-4f)
    }

    @Test
    fun dragKeepsGrabOffset() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.position = Vec3(0.2f, 0f, 0f)
        cube.draggable(DragMode.HorizontalPlane)
        scene.add(cube)

        // Grabbed at the world origin while the node sits at x = 0.2:
        // the offset must persist through the drag
        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.Transform(0.75f, 0.5f))
        process(raycast = hitOn(cube, position = Vec3.ZERO))

        assertEquals(1.2f, cube.position.x, 1e-4f)
    }

    @Test
    fun dragPrefersDetectedSurfaces() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.draggable(DragMode.DetectedSurfaces)
        scene.add(cube)

        val surfacePoint = Vec3(0.5f, -0.3f, -1f)
        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.Transform(0.6f, 0.5f))
        process(
            raycast = hitOn(cube),
            hitTest = { _, _ -> HitTestResult(surfacePoint, Vec3(0f, 1f, 0f), 1f, "plane-1") },
        )

        assertEquals(surfacePoint.x, cube.position.x, 1e-4f)
        assertEquals(surfacePoint.y, cube.position.y, 1e-4f)
        assertEquals(surfacePoint.z, cube.position.z, 1e-4f)
    }

    @Test
    fun dragTargetsAncestorWithBehavior() {
        val child = CubeNode(size = 0.1f, color = Color.White)
        val group = GroupNode()
        group.addChild(child)
        group.draggable(DragMode.HorizontalPlane)
        scene.add(group)

        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.Transform(0.75f, 0.5f))
        process(raycast = hitOn(child))

        assertEquals(1f, group.position.x, 1e-4f)
        assertEquals(0f, child.position.x, 1e-4f, "child local position must not change")
    }

    @Test
    fun gestureEndsOnTransformEnd() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.draggable(DragMode.HorizontalPlane)
        scene.add(cube)

        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.TransformEnd)
        // Transform after the gesture ended must not move anything
        manager.submit(GestureEvent.Transform(0.75f, 0.5f))
        process(raycast = hitOn(cube))

        assertEquals(0f, cube.position.x, 1e-4f)
    }

    @Test
    fun nonInteractiveNodeIsNotDragged() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        scene.add(cube)

        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.Transform(0.75f, 0.5f))
        process(raycast = hitOn(cube))

        assertEquals(0f, cube.position.x, 1e-4f)
    }

    // === Pinch ===

    @Test
    fun pinchScalesNode() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.scalable(minFactor = 0.5f, maxFactor = 2f)
        scene.add(cube)

        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.Transform(0.5f, 0.5f, zoom = 1.5f))
        process(raycast = hitOn(cube))

        assertEquals(1.5f, cube.scale.x, 1e-4f)
        assertEquals(1.5f, cube.scale.y, 1e-4f)
    }

    @Test
    fun pinchClampsToConfiguredBounds() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.scalable(minFactor = 0.5f, maxFactor = 2f)
        scene.add(cube)

        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.Transform(0.5f, 0.5f, zoom = 10f))
        process(raycast = hitOn(cube))

        assertEquals(2f, cube.scale.x, 1e-4f)
    }

    @Test
    fun pinchClampIsRelativeToOriginalScaleAcrossGestures() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.scalable(minFactor = 0.5f, maxFactor = 2f)
        scene.add(cube)

        // Two max-out pinches must not compound past maxFactor of the base
        repeat(2) {
            manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
            manager.submit(GestureEvent.Transform(0.5f, 0.5f, zoom = 10f))
            manager.submit(GestureEvent.TransformEnd)
            process(raycast = hitOn(cube))
        }

        assertEquals(2f, cube.scale.x, 1e-4f)
    }

    // === Twist ===

    @Test
    fun twistRotatesAroundConfiguredAxis() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.rotatable()
        scene.add(cube)

        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        manager.submit(GestureEvent.Transform(0.5f, 0.5f, twist = PI.toFloat() / 2f))
        process(raycast = hitOn(cube))

        // Clockwise twist = -90° about +Y: forward (-Z, "north") turns to +X
        // ("east") — clockwise seen from above
        val forward = cube.rotation * Vec3(0f, 0f, -1f)
        assertEquals(1f, forward.x, 1e-4f)
        assertEquals(0f, forward.z, 1e-4f)
    }

    @Test
    fun missedTransformStartLeavesNoActiveGesture() {
        val cube = CubeNode(size = 0.1f, color = Color.White)
        cube.draggable(DragMode.HorizontalPlane)
        scene.add(cube)

        manager.submit(GestureEvent.TransformStart(0.5f, 0.5f))
        process(raycast = missRaycast)
        manager.submit(GestureEvent.Transform(0.75f, 0.5f))
        process(raycast = hitOn(cube))

        assertEquals(0f, cube.position.x, 1e-4f)
    }

    @Test
    fun raycastNodeAtReturnsNullWithoutFrame() {
        val runtime = com.spatialkit.engine.SpatialRuntime()
        val session = SpatialSession(runtime)

        assertNull(session.raycastNodeAt(0.5f, 0.5f))
    }
}
