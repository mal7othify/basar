package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spatialkit.dsl.SpatialSceneScope
import com.spatialkit.dsl.spatialScene
import com.spatialkit.engine.EngineConfig
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.FrameData
import com.spatialkit.engine.SessionMode
import com.spatialkit.engine.SpatialEngine
import com.spatialkit.engine.SpatialRuntime
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession

/**
 * Main composable for rendering spatial content.
 *
 * This is the primary entry point for integrating spatial AR/VR content
 * into a Compose Multiplatform application.
 *
 * Example:
 * ```kotlin
 * SpatialView(
 *     mode = SessionMode.AR,
 *     onSessionReady = { session -> /* session ready */ }
 * ) {
 *     cube(size = 0.1f, color = Color.Red) {
 *         position = vec3(0f, 1.5f, -1f)
 *     }
 * }
 * ```
 *
 * ## Update model: build once, then mutate imperatively
 *
 * Unlike composable UI, the [content] DSL is **not** re-executed when state it
 * reads changes. The scene is built once per `content` lambda *instance* and
 * then updated imperatively:
 *
 * - Reading `State` inside [content] does **not** rebuild the scene. To change
 *   the scene structurally, pass a different lambda instance (e.g. a lambda
 *   capturing different values) — this rebuilds the whole scene.
 * - To move/animate existing nodes, keep a reference to them (capture the DSL
 *   return value, or use [rememberSpatialNode]) and mutate `position`,
 *   `rotation`, `scale`, etc. directly.
 * - Per the scene threading contract, prefer mutating node state from
 *   [onFrame] (the render thread); Compose snapshot state can be read there.
 *
 * ```kotlin
 * val position by animatedSpatialVec3(target)
 * var cube: CubeNode? = null
 *
 * SpatialView(
 *     onFrame = { cube?.position = position },  // imperative per-frame update
 * ) {
 *     cube = cube(size = 0.1f, color = Color.Red)  // built once
 * }
 * ```
 *
 * ## Engine selection
 *
 * The engine is chosen in this order: the [engine] parameter, then
 * [LocalSpatialEngine], then the platform default (WebXR on web, ARKit on
 * iOS). Android has no default — provide an ARCore engine from
 * `spatial-engine-arcore` (or your own) via either mechanism.
 *
 * @param modifier Compose modifier for layout
 * @param mode AR or VR mode
 * @param config Optional engine configuration
 * @param engine Engine to render with; overrides [LocalSpatialEngine] and the
 *   platform default. See "Engine selection" above.
 * @param onSessionReady Callback when session is ready
 * @param onFrame Callback for each frame update (invoked on the render thread)
 * @param onError Callback for errors
 * @param content DSL block for building the scene — executed once per lambda
 *   instance, see "Update model" above
 */
@Composable
public fun SpatialView(
    modifier: Modifier = Modifier,
    mode: SessionMode = SessionMode.AR,
    config: EngineConfig = EngineConfig(preferredMode = mode),
    engine: SpatialEngine? = null,
    onSessionReady: (SpatialSession) -> Unit = {},
    onFrame: (FrameData) -> Unit = {},
    onError: (EngineError) -> Unit = {},
    content: SpatialSceneScope.() -> Unit,
) {
    // Create runtime and session (remember across recompositions)
    val runtime = remember { SpatialRuntime() }
    val session = remember { SpatialSession(runtime) }

    // Engine selection: explicit parameter > LocalSpatialEngine > the
    // platform surface's default (where one exists).
    val providedEngine = engine ?: LocalSpatialEngine.current
    remember(providedEngine) {
        providedEngine?.let { runtime.setEngine(it) }
    }

    // Build scene from DSL (rebuild when content changes)
    val scene =
        remember(content) {
            spatialScene(content)
        }

    // Assign scene to session
    LaunchedEffect(scene) {
        session.scene.clear()
        scene.nodes.forEach { session.scene.add(it) }
    }

    // Platform-specific rendering surface
    SpatialSurface(
        modifier = modifier,
        session = session,
        scene = scene,
    )

    // (Re)start the session when the mode changes. SpatialRuntime.start stops
    // a running session before starting the new one, so mode switches work.
    // Note: because the engine is only initialized once, changes to [config]
    // fields other than preferredMode require recreating the SpatialView
    // (or disposing the session) to take effect.
    LaunchedEffect(mode, config) {
        try {
            session.start(
                config = config.copy(preferredMode = mode),
                onFrame = { frameData ->
                    onFrame(frameData)
                },
                onError = { error ->
                    onError(error)
                },
            )
            onSessionReady(session)
        } catch (e: Exception) {
            onError(EngineError.InitializationFailed(e.message ?: "Unknown error"))
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            session.stop()
            session.dispose()
        }
    }
}

/**
 * Platform-specific rendering surface.
 *
 * Implementations:
 * - Android: GLSurfaceView or ARSceneView
 * - iOS: MTKView or ARView
 * - JS: Canvas element for Three.js
 */
@Composable
public expect fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
)
