package com.spatialkit.compose

import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.spatialkit.engine.arcore.ARCoreEngine
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SpatialSession
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Android implementation of SpatialSurface.
 *
 * Creates a GLSurfaceView for ARCore rendering and connects it to the ARCoreEngine.
 * The engine handles all OpenGL rendering and AR camera integration.
 *
 * Architecture:
 * - GLSurfaceView provides the OpenGL context and rendering thread
 * - ARCoreEngine.onDrawFrame() renders the camera background + 3D scene
 * - Scene updates are submitted automatically each frame
 */
@Composable
public actual fun SpatialSurface(
    modifier: Modifier,
    session: SpatialSession,
    scene: SpatialScene,
) {
    // Get Android context
    val context = LocalContext.current

    // Create the ARCoreEngine early (during composition)
    // This ensures it exists before session.start() is called
    val arcoreEngine = remember(context) { ARCoreEngine(context) }

    // Set the engine on the runtime SYNCHRONOUSLY during composition
    // This ensures the engine is available BEFORE session.start() is called in SpatialView
    SideEffect {
        if (session.runtime.getEngine() == null) {
            session.runtime.setEngine(arcoreEngine)
        } else {
        }
    }

    // Create a native GLSurfaceView for spatial rendering
    AndroidView(
        modifier = modifier,
        factory = {
            GLSurfaceView(context).apply {
                // Set EGL context version (OpenGL ES 3.0)
                setEGLContextClientVersion(3)

                // Preserve the EGL context when paused (better for AR)
                preserveEGLContextOnPause = true

                // Set the renderer
                setRenderer(ARCoreSurfaceRenderer(arcoreEngine, scene))

                // Render continuously (60 FPS for AR)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        update = { glSurfaceView ->
            // Scene updates are handled automatically in onDrawFrame
            // No explicit update needed here - the renderer will pick up changes
        },
    )

    // Cleanup on dispose
    DisposableEffect(session) {
        onDispose {
            // Engine cleanup is handled by SpatialSession.dispose()
            // GLSurfaceView cleanup is automatic
        }
    }
}

/**
 * GLSurfaceView.Renderer implementation that bridges to ARCoreEngine.
 *
 * This adapter connects the OpenGL rendering lifecycle to the ARCore engine:
 * - onSurfaceCreated: OpenGL context is ready
 * - onSurfaceChanged: Surface size changed (rotation, resize)
 * - onDrawFrame: Render a frame (called continuously at ~60 FPS)
 */
private class ARCoreSurfaceRenderer(
    private val engine: ARCoreEngine,
    private val scene: SpatialScene,
) : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // OpenGL context is ready - initialize the renderer NOW
        // This ensures the camera texture is created before ARCore needs it
        engine.onSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Surface size changed - update ARCore display geometry
        engine.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Render a frame:
        // 1. ARCore updates camera pose and tracking
        // 2. Converts to FrameData and invokes callbacks
        // 3. Renders camera background + 3D scene
        engine.onDrawFrame()
    }
}