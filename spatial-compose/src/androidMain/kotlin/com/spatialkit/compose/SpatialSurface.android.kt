package com.spatialkit.compose

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.spatialkit.engine.GLSurfaceDrivenEngine
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Android implementation of SpatialSurface.
 *
 * Engine selection happens in [SpatialView] (via its `engine` parameter or
 * [LocalSpatialEngine]); this surface has no engine of its own. If the
 * provided engine renders through a GL surface (implements
 * [GLSurfaceDrivenEngine], like the ARCore engine), a [GLSurfaceView] is
 * created and its lifecycle forwarded to the engine:
 * - GLSurfaceView provides the OpenGL context and rendering thread
 * - `engine.onDrawFrame()` renders the camera background + 3D scene
 * - Scene updates are submitted automatically each frame
 *
 * Without an engine (or with one that renders elsewhere) nothing is drawn;
 * the session start path reports the error (e.g. [FallbackEngine] →
 * `EngineError.NotSupported`) through `SpatialView.onError`.
 */
@Composable
public actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // Set by SpatialView (parameter or LocalSpatialEngine) before this
    // composable runs; null when the app provided no engine.
    val engine = session.runtime.getEngine() as? GLSurfaceDrivenEngine ?: return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            GLSurfaceView(context).apply {
                // OpenGL ES 3.0
                setEGLContextClientVersion(3)

                // Preserve the EGL context when paused (better for AR)
                preserveEGLContextOnPause = true

                setRenderer(GLSurfaceRendererAdapter(engine))

                // Render continuously (60 FPS for AR)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
    )
}

/**
 * Bridges the [GLSurfaceView.Renderer] lifecycle to a [GLSurfaceDrivenEngine]:
 * - onSurfaceCreated: OpenGL context is ready
 * - onSurfaceChanged: Surface size changed (rotation, resize)
 * - onDrawFrame: Render a frame (called continuously at ~60 FPS)
 */
private class GLSurfaceRendererAdapter(
    private val engine: GLSurfaceDrivenEngine,
) : GLSurfaceView.Renderer {
    override fun onSurfaceCreated(
        gl: GL10?,
        config: EGLConfig?,
    ) = engine.onSurfaceCreated()

    override fun onSurfaceChanged(
        gl: GL10?,
        width: Int,
        height: Int,
    ) = engine.onSurfaceChanged(width, height)

    override fun onDrawFrame(gl: GL10?) = engine.onDrawFrame()
}
