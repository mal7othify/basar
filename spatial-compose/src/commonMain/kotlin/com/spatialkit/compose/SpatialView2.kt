package com.spatialkit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spatialkit.dsl.SpatialSceneScope
import com.spatialkit.dsl.spatialScene
import com.spatialkit.engine.EngineError
import com.spatialkit.engine.FrameData
import com.spatialkit.scene.GltfParseException
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession2
import com.spatialkit.tracking.TrackingConfig

/**
 * The v2 AR view: the [SpatialView] programming model on the v2 runtime —
 * one Filament renderer fed by the platform's tracking provider.
 *
 * Semantics match v1 [SpatialView]: [content] builds the scene **once**
 * (per lambda identity); afterwards nodes are mutated imperatively from
 * [onFrame] on the frame thread. Gestures feed the session's
 * [com.spatialkit.scene.InteractionManager] and are applied per tick.
 *
 * Platform support: Android renders through Filament + ARCore. iOS joins
 * with the R2 ARKit adapter (until then this composable renders nothing
 * there); desktop has no AR runtime.
 */
@Composable
public fun SpatialView2(
    modifier: Modifier = Modifier,
    config: TrackingConfig = TrackingConfig(),
    gesturesEnabled: Boolean = true,
    onSessionReady: (SpatialSession2) -> Unit = {},
    onFrame: (FrameData) -> Unit = {},
    onError: (EngineError) -> Unit = {},
    content: SpatialSceneScope.() -> Unit = {},
) {
    val stack = rememberSpatialStack2()
    DisposableEffect(stack) {
        onDispose { stack.dispose() }
    }
    val session = stack.session ?: return

    // The content block is a build-once description (the v1 update model):
    // it runs when the lambda identity changes, never per frame. A model
    // that fails to parse inside the block surfaces through onError.
    val built =
        remember(content) {
            try {
                BuiltScene2(spatialScene(content), error = null)
            } catch (e: GltfParseException) {
                BuiltScene2(spatialScene {}, error = e)
            }
        }

    LaunchedEffect(built) {
        built.error?.let { e ->
            onError(EngineError.AssetError(e.message ?: "Failed to parse model asset", e))
        }
        session.scene.clear()
        built.scene.nodes.forEach { session.scene.add(it) }
    }

    SpatialSurface2(
        session = session,
        modifier = if (gesturesEnabled) modifier.spatialGestures(session.interactions) else modifier,
    )

    LaunchedEffect(session, config) {
        try {
            session.start(config = config, onFrame = onFrame, onError = onError)
            onSessionReady(session)
        } catch (e: Exception) {
            onError(
                EngineError.InitializationFailed(
                    reason = e.message ?: "Tracking session failed to start",
                ),
            )
        }
    }
}

/** A built scene plus the asset failure that emptied it, if any. */
private class BuiltScene2(
    val scene: SpatialScene,
    val error: GltfParseException?,
)

/**
 * The platform pieces behind [SpatialView2]: the tracking provider +
 * Filament renderer composed into a session, plus whatever native
 * resources they need (the shared EGL context on Android). [session] is
 * null on platforms without a v2 runtime yet.
 */
internal interface SpatialStack2 {
    val session: SpatialSession2?

    fun dispose()
}

@Composable
internal expect fun rememberSpatialStack2(): SpatialStack2

/** The platform rendering surface hosting the session's renderer. */
@Composable
internal expect fun SpatialSurface2(
    session: SpatialSession2,
    modifier: Modifier,
)
