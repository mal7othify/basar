package com.spatialkit.engine.arcore

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.spatialkit.engine.*

/**
 * Simple OpenGL renderer for ARCore scenes.
 *
 * This is a minimal implementation focused on validating the SpatialEngine contract.
 * A production implementation would use a full rendering engine like Filament or SceneView.
 */
class SimpleRenderer(
    private val context: Context,
) {
    private var cameraTextureId: Int = 0
    private val sceneNodes = mutableListOf<SceneNode>()

    init {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        cameraTextureId = textureIds[0]

        // ARCore requires GL_TEXTURE_EXTERNAL_OES, not GL_TEXTURE_2D
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    fun getCameraTextureId(): Int = cameraTextureId

    fun resize(
        width: Int,
        height: Int,
    ) {
        GLES30.glViewport(0, 0, width, height)
    }

    fun updateScene(sceneData: SceneData) {
        sceneNodes.clear()
        sceneNodes.addAll(sceneData.nodes)
    }

    fun render(
        frame: Frame,
        arCamera: Camera,
    ) {
        // Clear buffers
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // In a full implementation, this would:
        // 1. Render the AR camera background
        // 2. Render scene nodes with proper transforms
        // 3. Apply materials and lighting
        //
        // For contract validation, we just need to demonstrate
        // that ARCore frame data is being converted correctly
    }

    fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        // Raycasting would be implemented here
        // For now, return null
        return null
    }

    fun destroy() {
        val textureIds = intArrayOf(cameraTextureId)
        GLES30.glDeleteTextures(1, textureIds, 0)
        sceneNodes.clear()
    }
}
