package com.spatialkit.engine.arcore

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.spatialkit.engine.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Simple OpenGL renderer for ARCore scenes.
 *
 * Renders the AR camera background as a fullscreen texture, then renders 3D scene nodes on top.
 * This is a minimal implementation - a production app would use Filament or SceneView.
 */
class SimpleRenderer(
    private val context: Context,
) {
    private var cameraTextureId: Int = 0
    private val sceneNodes = mutableListOf<SceneNode>()

    // Background rendering
    private var backgroundProgram: Int = 0
    private var backgroundVertexBuffer: FloatBuffer? = null
    private var backgroundTexCoordBuffer: FloatBuffer? = null
    private var isInitialized = false

    // 3D rendering
    private var sceneProgram: Int = 0
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Display rotation handling
    private val transformedUvs = FloatArray(8)

    private fun initializeCameraTexture() {
        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        cameraTextureId = textureIds[0]

        // ARCore requires GL_TEXTURE_EXTERNAL_OES for camera feed
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun initializeBackgroundShader() {
        // Simple vertex shader for fullscreen quad
        val vertexShader =
            """
            #version 300 es
            precision mediump float;

            layout(location = 0) in vec2 a_Position;
            layout(location = 1) in vec2 a_TexCoord;

            out vec2 v_TexCoord;

            void main() {
                gl_Position = vec4(a_Position, 0.0, 1.0);
                v_TexCoord = a_TexCoord;
            }
            """.trimIndent()

        // Fragment shader for camera texture (external OES)
        val fragmentShader =
            """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;

            uniform samplerExternalOES u_Texture;

            in vec2 v_TexCoord;
            out vec4 fragColor;

            void main() {
                fragColor = texture(u_Texture, v_TexCoord);
            }
            """.trimIndent()

        backgroundProgram = createProgram(vertexShader, fragmentShader)

        // Fullscreen quad vertices (NDC coordinates)
        val quadVertices =
            floatArrayOf(
                -1.0f, -1.0f, // bottom-left
                1.0f, -1.0f, // bottom-right
                -1.0f, 1.0f, // top-left
                1.0f, 1.0f, // top-right
            )

        // Texture coordinates
        val texCoords =
            floatArrayOf(
                0.0f, 0.0f, // bottom-left
                1.0f, 0.0f, // bottom-right
                0.0f, 1.0f, // top-left
                1.0f, 1.0f, // top-right
            )

        backgroundVertexBuffer =
            ByteBuffer.allocateDirect(quadVertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(quadVertices)
                .apply { position(0) }

        backgroundTexCoordBuffer =
            ByteBuffer.allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(texCoords)
                .apply { position(0) }
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String,
    ): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        // Check for linking errors
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $error")
        }

        return program
    }

    private fun loadShader(
        type: Int,
        source: String,
    ): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        // Check for compilation errors
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed: $error")
        }

        return shader
    }

    fun getCameraTextureId(): Int = cameraTextureId

    /**
     * Initialize OpenGL resources (must be called from GL thread)
     */
    fun initialize() {
        if (isInitialized) return

        println("SimpleRenderer: Initializing OpenGL resources")
        initializeCameraTexture()
        initializeBackgroundShader()
        initialize3DShader()
        isInitialized = true
        println("SimpleRenderer: backgroundProgram = $backgroundProgram, cameraTextureId = $cameraTextureId")
        println("SimpleRenderer: sceneProgram = $sceneProgram")
        println("SimpleRenderer: Initialization complete")
    }

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
        // Ensure initialization happened (should be done in onSurfaceCreated)
        if (!isInitialized) {
            println("SimpleRenderer: WARNING - render() called before initialize()!")
            initialize()
        }

        // Clear buffers
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Disable depth test for background rendering
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        // Render camera background with proper rotation
        renderBackground(frame)

        // Enable depth test for 3D scene rendering
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)

        // Render 3D scene nodes
        render3DScene(frame, arCamera)
    }

    private fun initialize3DShader() {
        // Simple 3D vertex shader with MVP matrix
        val vertexShader =
            """
            #version 300 es
            precision mediump float;

            layout(location = 0) in vec3 a_Position;
            layout(location = 1) in vec3 a_Normal;

            uniform mat4 u_MVP;
            uniform mat4 u_Model;

            out vec3 v_Normal;
            out vec3 v_Position;

            void main() {
                gl_Position = u_MVP * vec4(a_Position, 1.0);
                v_Normal = mat3(u_Model) * a_Normal;
                v_Position = (u_Model * vec4(a_Position, 1.0)).xyz;
            }
            """.trimIndent()

        // Simple fragment shader with basic lighting
        val fragmentShader =
            """
            #version 300 es
            precision mediump float;

            in vec3 v_Normal;
            in vec3 v_Position;

            uniform vec3 u_Color;
            uniform vec3 u_LightDir;

            out vec4 fragColor;

            void main() {
                vec3 normal = normalize(v_Normal);
                float diffuse = max(dot(normal, u_LightDir), 0.0);
                vec3 ambient = u_Color * 0.3;
                vec3 color = ambient + u_Color * diffuse * 0.7;
                fragColor = vec4(color, 1.0);
            }
            """.trimIndent()

        sceneProgram = createProgram(vertexShader, fragmentShader)
    }

    private fun renderBackground(frame: Frame) {
        if (backgroundProgram == 0) {
            println("SimpleRenderer: ERROR - backgroundProgram is 0!")
            return
        }

        GLES30.glUseProgram(backgroundProgram)

        // Get display-rotated UV coordinates from ARCore
        frame.transformCoordinates2d(
            android.opengl.GLES20.GL_TEXTURE_COORD_ARRAY,
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f),
            android.opengl.GLES20.GL_TEXTURE_COORD_ARRAY,
            transformedUvs
        )

        // Update texture coordinate buffer with transformed UVs
        val texCoordBuffer = ByteBuffer.allocateDirect(transformedUvs.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(transformedUvs)
            .apply { position(0) }

        // Bind camera texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(backgroundProgram, "u_Texture"), 0)

        // Set vertex positions
        backgroundVertexBuffer?.let { buffer ->
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, buffer)
        }

        // Set transformed texture coordinates
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, texCoordBuffer)

        // Draw fullscreen quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // Cleanup
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun render3DScene(
        frame: Frame,
        arCamera: Camera,
    ) {
        if (sceneNodes.isEmpty() || sceneProgram == 0) {
            return
        }

        GLES30.glUseProgram(sceneProgram)

        // Get camera matrices
        arCamera.getViewMatrix(viewMatrix, 0)
        arCamera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

        // Set light direction (from above-front)
        val lightDirLoc = GLES30.glGetUniformLocation(sceneProgram, "u_LightDir")
        GLES30.glUniform3f(lightDirLoc, 0.0f, 0.7f, 0.7f)

        // Render each node (only Mesh nodes)
        sceneNodes.forEach { node ->
            when (node) {
                is SceneNode.Mesh -> renderMesh(node)
                is SceneNode.Group -> {} // Groups don't render directly
            }
        }
    }

    private fun renderMesh(mesh: SceneNode.Mesh) {
        if (!mesh.visible) return

        val transform = mesh.transform

        // Build model matrix from transform
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(
            modelMatrix,
            0,
            transform.position.x,
            transform.position.y,
            transform.position.z,
        )

        // Apply rotation
        val rot = transform.rotation
        val angle = 2.0f * Math.acos(rot.w.toDouble()).toFloat()
        if (angle > 0.001f) {
            val axis = floatArrayOf(rot.x, rot.y, rot.z)
            val axisLength = Math.sqrt((axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2]).toDouble()).toFloat()
            if (axisLength > 0.001f) {
                Matrix.rotateM(
                    modelMatrix,
                    0,
                    Math.toDegrees(angle.toDouble()).toFloat(),
                    axis[0] / axisLength,
                    axis[1] / axisLength,
                    axis[2] / axisLength,
                )
            }
        }

        // Apply scale
        Matrix.scaleM(modelMatrix, 0, transform.scale.x, transform.scale.y, transform.scale.z)

        // Calculate MVP matrix
        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // Set uniforms
        val mvpLoc = GLES30.glGetUniformLocation(sceneProgram, "u_MVP")
        GLES30.glUniformMatrix4fv(mvpLoc, 1, false, mvpMatrix, 0)

        val modelLoc = GLES30.glGetUniformLocation(sceneProgram, "u_Model")
        GLES30.glUniformMatrix4fv(modelLoc, 1, false, modelMatrix, 0)

        val colorLoc = GLES30.glGetUniformLocation(sceneProgram, "u_Color")
        GLES30.glUniform3f(
            colorLoc,
            mesh.material.baseColor.x,
            mesh.material.baseColor.y,
            mesh.material.baseColor.z,
        )

        // Render geometry based on type
        when (mesh.geometry.type) {
            GeometryType.CUBE -> renderCube()
            GeometryType.SPHERE -> renderSphere()
            GeometryType.CYLINDER -> renderCylinder()
            else -> {} // Other types not implemented yet
        }
    }

    private fun renderCube() {
        // Unit cube (1x1x1) - scale is applied via transform
        val halfSize = 0.5f

        val vertices =
            floatArrayOf(
                // Front face
                -halfSize, -halfSize, halfSize, // 0
                halfSize, -halfSize, halfSize, // 1
                halfSize, halfSize, halfSize, // 2
                -halfSize, halfSize, halfSize, // 3
                // Back face
                -halfSize, -halfSize, -halfSize, // 4
                halfSize, -halfSize, -halfSize, // 5
                halfSize, halfSize, -halfSize, // 6
                -halfSize, halfSize, -halfSize, // 7
            )

        val normals =
            floatArrayOf(
                // Front
                0f, 0f, 1f,
                0f, 0f, 1f,
                0f, 0f, 1f,
                0f, 0f, 1f,
                // Back
                0f, 0f, -1f,
                0f, 0f, -1f,
                0f, 0f, -1f,
                0f, 0f, -1f,
            )

        val indices =
            shortArrayOf(
                // Front
                0, 1, 2, 0, 2, 3,
                // Right
                1, 5, 6, 1, 6, 2,
                // Back
                5, 4, 7, 5, 7, 6,
                // Left
                4, 0, 3, 4, 3, 7,
                // Top
                3, 2, 6, 3, 6, 7,
                // Bottom
                4, 5, 1, 4, 1, 0,
            )

        drawGeometry(vertices, normals, indices)
    }

    private fun renderSphere(segments: Int = 16) {
        // Unit sphere (radius 1) - scale is applied via transform
        val radius = 1.0f
        val rings = segments / 2
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        // Generate sphere vertices
        for (ring in 0..rings) {
            val theta = ring * Math.PI / rings
            val sinTheta = Math.sin(theta).toFloat()
            val cosTheta = Math.cos(theta).toFloat()

            for (seg in 0..segments) {
                val phi = seg * 2 * Math.PI / segments
                val sinPhi = Math.sin(phi).toFloat()
                val cosPhi = Math.cos(phi).toFloat()

                val x = cosPhi * sinTheta
                val y = cosTheta
                val z = sinPhi * sinTheta

                vertices.add(x * radius)
                vertices.add(y * radius)
                vertices.add(z * radius)

                normals.add(x)
                normals.add(y)
                normals.add(z)
            }
        }

        // Generate indices
        for (ring in 0 until rings) {
            for (seg in 0 until segments) {
                val first = (ring * (segments + 1) + seg).toShort()
                val second = (first + segments + 1).toShort()

                indices.add(first)
                indices.add(second)
                indices.add((first + 1).toShort())

                indices.add(second)
                indices.add((second + 1).toShort())
                indices.add((first + 1).toShort())
            }
        }

        drawGeometry(vertices.toFloatArray(), normals.toFloatArray(), indices.toShortArray())
    }

    private fun renderCylinder(segments: Int = 16) {
        // Unit cylinder (radius 0.5, height 1) - scale is applied via transform
        val radius = 0.5f
        val height = 1.0f
        val halfHeight = height / 2f

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        // Top and bottom circles + sides
        for (i in 0..segments) {
            val angle = i * 2 * Math.PI / segments
            val x = Math.cos(angle).toFloat() * radius
            val z = Math.sin(angle).toFloat() * radius

            // Top vertex
            vertices.add(x)
            vertices.add(halfHeight)
            vertices.add(z)
            normals.add(x / radius)
            normals.add(0f)
            normals.add(z / radius)

            // Bottom vertex
            vertices.add(x)
            vertices.add(-halfHeight)
            vertices.add(z)
            normals.add(x / radius)
            normals.add(0f)
            normals.add(z / radius)
        }

        // Generate side indices
        for (i in 0 until segments) {
            val topFirst = (i * 2).toShort()
            val bottomFirst = (i * 2 + 1).toShort()
            val topSecond = ((i + 1) * 2).toShort()
            val bottomSecond = ((i + 1) * 2 + 1).toShort()

            indices.add(topFirst)
            indices.add(bottomFirst)
            indices.add(topSecond)

            indices.add(topSecond)
            indices.add(bottomFirst)
            indices.add(bottomSecond)
        }

        // Add caps
        val centerTop = vertices.size / 3
        vertices.add(0f)
        vertices.add(halfHeight)
        vertices.add(0f)
        normals.add(0f)
        normals.add(1f)
        normals.add(0f)

        val centerBottom = vertices.size / 3
        vertices.add(0f)
        vertices.add(-halfHeight)
        vertices.add(0f)
        normals.add(0f)
        normals.add(-1f)
        normals.add(0f)

        // Top cap indices
        for (i in 0 until segments) {
            indices.add(centerTop.toShort())
            indices.add((i * 2).toShort())
            indices.add(((i + 1) * 2).toShort())
        }

        // Bottom cap indices
        for (i in 0 until segments) {
            indices.add(centerBottom.toShort())
            indices.add(((i + 1) * 2 + 1).toShort())
            indices.add((i * 2 + 1).toShort())
        }

        drawGeometry(vertices.toFloatArray(), normals.toFloatArray(), indices.toShortArray())
    }

    private fun drawGeometry(
        vertices: FloatArray,
        normals: FloatArray,
        indices: ShortArray,
    ) {
        // Create vertex buffer
        val vertexBuffer =
            ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
                .apply { position(0) }

        // Create normal buffer
        val normalBuffer =
            ByteBuffer.allocateDirect(normals.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(normals)
                .apply { position(0) }

        // Create index buffer
        val indexBuffer =
            ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .put(indices)
                .apply { position(0) }

        // Set vertex attributes
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, normalBuffer)

        // Draw
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indices.size, GLES30.GL_UNSIGNED_SHORT, indexBuffer)

        // Cleanup
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
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
