package com.spatialkit.engine.preview

import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.DetectedPlane
import com.spatialkit.engine.EngineCapabilities
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.EngineState
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.PlaneType
import com.spatialkit.engine.Pose
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.TrackingState
import com.spatialkit.engine.Vec3
import com.spatialkit.engine.webxr.PerspectiveCamera
import com.spatialkit.engine.webxr.Raycaster
import com.spatialkit.engine.webxr.ThreeSceneRenderer
import com.spatialkit.engine.webxr.Vector2
import com.spatialkit.engine.webxr.WebGLRenderer
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.Event
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Non-XR preview implementation of [SpatialEngine] (M10.1).
 *
 * Renders the scene with Three.js into a full-page canvas **without** an XR
 * session, so scenes built with the shared DSL can be seen and iterated on in
 * any desktop browser — no AR hardware required. Scene synchronization is the
 * same [ThreeSceneRenderer] the WebXR engine uses, so what you preview is
 * what the AR path renders.
 *
 * In place of device tracking there is an orbit camera ([orbit]):
 * - **drag** orbits around the target
 * - **right-drag / shift-drag** pans
 * - **wheel / pinch** zooms
 *
 * The "real world" is a synthetic ground plane at y = 0, drawn as a grid:
 * [performHitTest] intersects it (planeId `"preview-floor"`) and every frame
 * reports it in [FrameData.detectedPlanes], so plane-based placement flows
 * behave like they do in AR. [SessionMode] is accepted but ignored — there is
 * no immersive session.
 *
 * Taps that should reach scene content are surfaced through [onTap] (the
 * preview canvas overlays the page, so Compose gesture handling never sees
 * pointer input); the hosting surface forwards them to its interaction layer.
 */
public class PreviewEngine(
    /** Orbit-camera state; reposition it to change the initial framing. */
    public val orbit: OrbitCameraModel = OrbitCameraModel(),
) : SpatialEngine {
    private var sceneRenderer: ThreeSceneRenderer? = null
    private var camera: PerspectiveCamera? = null
    private var renderer: WebGLRenderer? = null
    private var grid: GridHelper? = null

    private val _state = MutableStateFlow<EngineState>(EngineState.Uninitialized)
    private var currentConfig: EngineConfig? = null
    private var frameCallback: ((FrameData) -> Unit)? = null
    private var lastFrameTime = 0.0

    /**
     * Invoked when a tap (pointer down + up without dragging) lands on the
     * preview canvas, with normalized (0..1, top-left origin) coordinates.
     * The hosting surface should submit it to its gesture/interaction layer.
     */
    public var onTap: ((screenX: Float, screenY: Float) -> Unit)? = null

    // Pointer tracking for the orbit controls: pointerId -> last client x/y
    private val activePointers = mutableMapOf<Int, Pair<Double, Double>>()
    private var downX = 0.0
    private var downY = 0.0
    private var dragDistance = 0.0
    private val domListeners = mutableListOf<Triple<String, (Event) -> Unit, Boolean>>()
    private var resizeListener: ((Event) -> Unit)? = null

    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override val capabilities: EngineCapabilities =
        EngineCapabilities(
            supportsAR = false,
            supportsVR = false,
            // The preview reports one synthetic floor plane each frame
            supportsPlaneDetection = true,
            supportsImageTracking = false,
            supportsFaceTracking = false,
            supportsHandTracking = false,
            supportsDepthOcclusion = false,
            supportsSpatialAnchors = false,
            maxAnchors = 0,
        )

    override suspend fun initialize(config: EngineConfig) {
        if (_state.value != EngineState.Uninitialized && _state.value != EngineState.Disposed) {
            throw IllegalStateException("Engine already initialized")
        }

        _state.value = EngineState.Initializing
        currentConfig = config

        try {
            sceneRenderer = ThreeSceneRenderer()

            val aspect = window.innerWidth.toDouble() / window.innerHeight.toDouble()
            camera =
                PerspectiveCamera(
                    60.0,
                    aspect,
                    config.nearPlane.toDouble(),
                    config.farPlane.toDouble(),
                )

            val canvas = document.createElement("canvas") as HTMLCanvasElement
            canvas.id = "preview-canvas"
            canvas.style.apply {
                position = "fixed"
                top = "0"
                left = "0"
                width = "100%"
                height = "100%"
                zIndex = "999"
                // Orbit gestures must not scroll/zoom the page on touch devices
                asDynamic().touchAction = "none"
            }
            document.body?.appendChild(canvas)

            val rendererParams = js("{}")
            rendererParams.canvas = canvas
            rendererParams.antialias = true

            renderer =
                WebGLRenderer(rendererParams).apply {
                    asDynamic().setPixelRatio(window.devicePixelRatio)
                    setSize(window.innerWidth, window.innerHeight)
                    asDynamic().setClearColor(BACKGROUND_COLOR, 1.0)
                }

            // Ground grid marking the synthetic y = 0 floor plane. Excluded
            // from raycasts so it never intercepts content picking.
            grid =
                GridHelper(FLOOR_EXTENT.toDouble(), 20, 0x556677, 0x333a44).also {
                    it.asDynamic().raycast = js("function() {}")
                    sceneRenderer?.scene?.add(it)
                }

            val onResize: (Event) -> Unit = {
                val w = window.innerWidth
                val h = window.innerHeight
                renderer?.setSize(w, h)
                camera?.let { cam ->
                    cam.asDynamic().aspect = w.toDouble() / h.toDouble()
                    cam.asDynamic().updateProjectionMatrix()
                }
            }
            window.addEventListener("resize", onResize)
            resizeListener = onResize

            _state.value = EngineState.Ready
            SpatialLog.debug(TAG) { "initialized (non-XR preview)" }
        } catch (e: Exception) {
            _state.value =
                EngineState.Error(
                    EngineError.InitializationFailed("Failed to initialize preview: ${e.message}"),
                )
            throw e
        }
    }

    override suspend fun startSession(mode: SessionMode) {
        if (_state.value != EngineState.Ready) {
            throw IllegalStateException("Engine not ready. Current state: ${_state.value}")
        }

        attachInputListeners()

        // Without an XR session Three.js drives this from window.requestAnimationFrame
        renderer?.setAnimationLoop { time: Double, _: dynamic ->
            onAnimationFrame(time)
        }

        _state.value = EngineState.Running(mode)
        SpatialLog.debug(TAG) { "preview session running (mode $mode ignored — windowed rendering)" }
    }

    override fun stopSession() {
        if (_state.value !is EngineState.Running) return

        renderer?.setAnimationLoop(null)
        detachInputListeners()
        lastFrameTime = 0.0
        _state.value = EngineState.Ready
    }

    override fun dispose() {
        stopSession()

        resizeListener?.let { window.removeEventListener("resize", it) }
        resizeListener = null

        grid?.let { g ->
            sceneRenderer?.scene?.remove(g)
            g.asDynamic().geometry?.dispose()
            g.asDynamic().material?.dispose()
        }
        grid = null

        sceneRenderer?.dispose()
        sceneRenderer = null

        camera = null
        renderer?.domElement?.remove()
        renderer = null

        _state.value = EngineState.Disposed
    }

    override fun onFrame(callback: (FrameData) -> Unit) {
        frameCallback = callback
    }

    override fun submitScene(scene: SceneData) {
        sceneRenderer?.submitScene(scene)
    }

    /**
     * Intersects the camera ray through the screen point with the synthetic
     * floor plane (y = 0) — the preview's stand-in for real-world geometry.
     * Scene content is intentionally not tested here; use [performRaycast]
     * (or the session's node picking) for content.
     */
    override fun performHitTest(
        screenX: Float,
        screenY: Float,
    ): HitTestResult? {
        val threeCamera = camera ?: return null

        val ndc = Vector2((screenX * 2f - 1f).toDouble(), (1f - screenY * 2f).toDouble())
        val raycaster = Raycaster()
        raycaster.setFromCamera(ndc, threeCamera)

        val origin = raycaster.ray.origin
        val direction = raycaster.ray.direction
        if (abs(direction.y) < 1e-6) return null

        val t = -origin.y / direction.y
        if (t <= 0) return null

        val x = origin.x + direction.x * t
        val z = origin.z + direction.z * t
        // Only the visible grid area counts as floor
        val half = FLOOR_EXTENT / 2f
        if (abs(x) > half || abs(z) > half) return null

        return HitTestResult(
            position = Vec3(x.toFloat(), 0f, z.toFloat()),
            normal = Vec3(0f, 1f, 0f),
            // setFromCamera yields a normalized direction, so t is the distance
            distance = t.toFloat(),
            planeId = FLOOR_PLANE_ID,
        )
    }

    override fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? = sceneRenderer?.performRaycast(origin, direction)

    // === Frame loop ===

    private fun onAnimationFrame(time: Double) {
        val threeScene = sceneRenderer?.scene ?: return
        val threeCamera = camera ?: return
        val threeRenderer = renderer ?: return

        val deltaTime =
            if (lastFrameTime > 0) {
                ((time - lastFrameTime) / 1000.0).toFloat()
            } else {
                0.0f
            }
        lastFrameTime = time

        // Apply the orbit model to the Three.js camera
        val pose = orbit.pose()
        threeCamera.position.set(
            pose.position.x.toDouble(),
            pose.position.y.toDouble(),
            pose.position.z.toDouble(),
        )
        threeCamera.quaternion.set(
            pose.rotation.x.toDouble(),
            pose.rotation.y.toDouble(),
            pose.rotation.z.toDouble(),
            pose.rotation.w.toDouble(),
        )
        threeCamera.updateMatrixWorld(true)

        frameCallback?.invoke(buildFrameData(time, deltaTime, pose, threeCamera))

        threeRenderer.render(threeScene, threeCamera)
    }

    private fun buildFrameData(
        time: Double,
        deltaTime: Float,
        pose: Pose,
        threeCamera: PerspectiveCamera,
    ): FrameData =
        FrameData(
            timestamp = time.toLong(),
            deltaTime = deltaTime,
            cameraPose = pose,
            projectionMatrix = threeCamera.projectionMatrix.toArray().toFloatArray16(),
            // Camera-to-world pose matrix, matching the WebXR engine's convention
            viewMatrix = threeCamera.matrixWorld.toArray().toFloatArray16(),
            trackingState = TrackingState.NORMAL,
            detectedPlanes = FLOOR_PLANES,
            lightEstimate = null,
            inputSources = emptyList(),
            anchors = emptyList(),
        )

    // === Orbit camera input ===

    private fun attachInputListeners() {
        val canvas = renderer?.domElement ?: return

        addListener(canvas, "pointerdown") { event ->
            val e = event.asDynamic()
            canvas.asDynamic().setPointerCapture(e.pointerId)
            activePointers[(e.pointerId as Number).toInt()] =
                (e.clientX as Number).toDouble() to (e.clientY as Number).toDouble()
            if (activePointers.size == 1) {
                downX = (e.clientX as Number).toDouble()
                downY = (e.clientY as Number).toDouble()
                dragDistance = 0.0
            }
        }

        addListener(canvas, "pointermove") { event ->
            val e = event.asDynamic()
            val id = (e.pointerId as Number).toInt()
            val previous = activePointers[id] ?: return@addListener
            val x = (e.clientX as Number).toDouble()
            val y = (e.clientY as Number).toDouble()
            val dx = x - previous.first
            val dy = y - previous.second
            dragDistance += abs(dx) + abs(dy)

            when (activePointers.size) {
                1 -> {
                    val panning = (e.buttons as Number).toInt() == 2 || e.shiftKey == true
                    if (panning) {
                        val panScale = orbit.distance * PAN_SPEED
                        orbit.pan((-dx * panScale).toFloat(), (dy * panScale).toFloat())
                    } else {
                        orbit.orbit((-dx * ROTATE_SPEED).toFloat(), (dy * ROTATE_SPEED).toFloat())
                    }
                }

                2 -> {
                    // Pinch: zoom by the ratio of the previous to the new
                    // distance between the two pointers
                    val other = activePointers.entries.first { it.key != id }.value
                    val previousSpread = distance(previous.first, previous.second, other.first, other.second)
                    val newSpread = distance(x, y, other.first, other.second)
                    if (previousSpread > 1.0 && newSpread > 1.0) {
                        orbit.zoomBy((previousSpread / newSpread).toFloat())
                    }
                }
            }

            activePointers[id] = x to y
        }

        val endPointer: (Event) -> Unit = { event ->
            val e = event.asDynamic()
            val id = (e.pointerId as Number).toInt()
            if (activePointers.remove(id) != null &&
                activePointers.isEmpty() &&
                dragDistance < TAP_SLOP_PX &&
                (e.button as? Number)?.toInt() == 0
            ) {
                val width = window.innerWidth.toDouble()
                val height = window.innerHeight.toDouble()
                if (width > 0 && height > 0) {
                    onTap?.invoke(
                        ((e.clientX as Number).toDouble() / width).toFloat(),
                        ((e.clientY as Number).toDouble() / height).toFloat(),
                    )
                }
            }
        }
        addListener(canvas, "pointerup", handler = endPointer)
        addListener(canvas, "pointercancel") { event ->
            activePointers.remove((event.asDynamic().pointerId as Number).toInt())
        }

        addListener(canvas, "wheel", preventDefault = true) { event ->
            val e = event.asDynamic()
            event.preventDefault()
            orbit.zoomBy(exp((e.deltaY as Number).toDouble() * WHEEL_ZOOM_SPEED).toFloat())
        }

        // Right-drag pans; the browser context menu would interrupt it
        addListener(canvas, "contextmenu", preventDefault = true) { event ->
            event.preventDefault()
        }
    }

    private fun addListener(
        canvas: HTMLCanvasElement,
        type: String,
        preventDefault: Boolean = false,
        handler: (Event) -> Unit,
    ) {
        if (preventDefault) {
            // { passive: false } so preventDefault() works in wheel handlers
            canvas.asDynamic().addEventListener(type, handler, js("{passive: false}"))
        } else {
            canvas.addEventListener(type, handler)
        }
        domListeners.add(Triple(type, handler, preventDefault))
    }

    private fun detachInputListeners() {
        val canvas = renderer?.domElement
        domListeners.forEach { (type, handler, _) ->
            canvas?.removeEventListener(type, handler)
        }
        domListeners.clear()
        activePointers.clear()
    }

    private fun distance(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    private fun Array<Double>.toFloatArray16(): FloatArray = FloatArray(size) { this[it].toFloat() }

    public companion object {
        /** Plane id reported for hits on the synthetic floor. */
        public const val FLOOR_PLANE_ID: String = "preview-floor"

        /** Side length of the synthetic floor plane / grid, meters. */
        public const val FLOOR_EXTENT: Float = 10f

        private const val TAG = "PreviewEngine"
        private const val BACKGROUND_COLOR = 0x1a1d21
        private const val ROTATE_SPEED = 0.005
        private const val PAN_SPEED = 0.0012
        private const val WHEEL_ZOOM_SPEED = 0.001
        private const val TAP_SLOP_PX = 8.0

        // The synthetic floor, reported in every frame's detectedPlanes
        private val FLOOR_PLANES =
            listOf(
                DetectedPlane(
                    id = FLOOR_PLANE_ID,
                    type = PlaneType.HORIZONTAL_UP,
                    centerPose = Pose(Vec3.ZERO, Quaternion.IDENTITY),
                    extentX = FLOOR_EXTENT,
                    extentZ = FLOOR_EXTENT,
                    vertices = null,
                ),
            )
    }
}
