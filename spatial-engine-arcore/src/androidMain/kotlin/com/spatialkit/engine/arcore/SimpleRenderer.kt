package com.spatialkit.engine.arcore

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.Quaternion
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Data class to hold VBO/VAO resources for a geometry.
 */
private data class GeometryBuffers(
    val vao: Int,
    val vertexVbo: Int,
    val normalVbo: Int,
    val indexVbo: Int,
    val indexCount: Int,
)

/**
 * Simple OpenGL renderer for ARCore scenes.
 *
 * Renders the AR camera background as a fullscreen texture, then renders 3D scene nodes on top.
 * This is a minimal implementation - a production app would use Filament or SceneView.
 */
public class SimpleRenderer(
    private val context: Context,
) {
    private var cameraTextureId: Int = 0

    // Thread-safe scene node list
    private val sceneNodes = mutableListOf<SceneNode>()
    private val sceneLock = ReentrantLock()

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

    // VBO/VAO caching to prevent memory leaks
    private val geometryCache = mutableMapOf<GeometryType, GeometryBuffers>()

    // Custom mesh cache keyed by node ID (each custom mesh has unique geometry)
    private val customMeshCache = mutableMapOf<String, GeometryBuffers>()

    // Store shader references for cleanup
    private var vertexShader: Int = 0
    private var fragmentShader: Int = 0
    private var backgroundVertexShader: Int = 0
    private var backgroundFragmentShader: Int = 0

    // NDC coordinates for the fullscreen quad (in 0-1 range for ARCore transformation)
    private val ndcCoordinates =
        floatArrayOf(
            0f,
            0f, // bottom-left
            1f,
            0f, // bottom-right
            0f,
            1f, // top-left
            1f,
            1f, // top-right
        )

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

        backgroundProgram = createProgram(vertexShader, fragmentShader, saveShaders = true)

        // Fullscreen quad vertices (NDC coordinates)
        val quadVertices =
            floatArrayOf(
                -1.0f,
                -1.0f, // bottom-left
                1.0f,
                -1.0f, // bottom-right
                -1.0f,
                1.0f, // top-left
                1.0f,
                1.0f, // top-right
            )

        // Texture coordinates
        val texCoords =
            floatArrayOf(
                0.0f,
                0.0f, // bottom-left
                1.0f,
                0.0f, // bottom-right
                0.0f,
                1.0f, // top-left
                1.0f,
                1.0f, // top-right
            )

        backgroundVertexBuffer =
            ByteBuffer
                .allocateDirect(quadVertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(quadVertices)
                .apply { position(0) }

        backgroundTexCoordBuffer =
            ByteBuffer
                .allocateDirect(texCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(texCoords)
                .apply { position(0) }
    }

    private fun createProgram(
        vertexSource: String,
        fragmentSource: String,
        saveShaders: Boolean = false,
    ): Int {
        val vShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vShader)
        GLES30.glAttachShader(program, fShader)
        GLES30.glLinkProgram(program)

        // Check for linking errors
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            GLES30.glDeleteShader(vShader)
            GLES30.glDeleteShader(fShader)
            throw RuntimeException("Program link failed: $error")
        }

        // Save shader references for cleanup
        if (saveShaders) {
            if (program == backgroundProgram || backgroundProgram == 0) {
                backgroundVertexShader = vShader
                backgroundFragmentShader = fShader
            } else {
                vertexShader = vShader
                fragmentShader = fShader
            }
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

    public fun getCameraTextureId(): Int = cameraTextureId

    /**
     * Initialize OpenGL resources (must be called from GL thread)
     */
    public fun initialize() {
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

    public fun resize(
        width: Int,
        height: Int,
    ) {
        GLES30.glViewport(0, 0, width, height)
    }

    /**
     * Update the scene with new data.
     */
    public fun updateScene(sceneData: SceneData) {
        sceneLock.withLock {
            sceneNodes.clear()
            sceneNodes.addAll(sceneData.nodes)
        }
    }

    public fun render(
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

    /**
     * Render only the camera background (used when camera isn't tracking yet).
     * This prevents the brown screen during initialization.
     */
    public fun renderCameraOnly(
        frame: Frame,
        arCamera: Camera,
    ) {
        // Ensure initialization happened
        if (!isInitialized) {
            initialize()
        }

        // Clear buffers
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Disable depth test for background rendering
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        // Render camera background only
        renderBackground(frame)

        // Don't render 3D scene - camera isn't tracking yet
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

        sceneProgram = createProgram(vertexShader, fragmentShader, saveShaders = true)
    }

    private fun renderBackground(frame: Frame) {
        if (backgroundProgram == 0) {
            println("SimpleRenderer: ERROR - backgroundProgram is 0!")
            return
        }

        GLES30.glUseProgram(backgroundProgram)

        // Transform NDC coordinates to texture coordinates based on camera/display orientation
        // This handles all rotation and flipping automatically
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            ndcCoordinates,
            Coordinates2d.TEXTURE_NORMALIZED,
            transformedUvs,
        )

        // Update texture coordinate buffer with transformed values
        backgroundTexCoordBuffer?.apply {
            position(0)
            put(transformedUvs)
            position(0)
        }

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
        backgroundTexCoordBuffer?.let { buffer ->
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, buffer)
        }

        // Draw fullscreen quad
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // Cleanup
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    // ===== VBO/VAO Caching Implementation =====

    /**
     * Create and upload geometry to GPU buffers.
     * Called once per geometry type and reused across all frames.
     */
    private fun createGeometryBuffers(
        vertices: FloatArray,
        normals: FloatArray,
        indices: ShortArray,
    ): GeometryBuffers {
        // Generate VAO
        val vaoIds = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoIds, 0)
        val vao = vaoIds[0]

        // Generate VBOs
        val vboIds = IntArray(3)
        GLES30.glGenBuffers(3, vboIds, 0)
        val vertexVbo = vboIds[0]
        val normalVbo = vboIds[1]
        val indexVbo = vboIds[2]

        // Bind VAO
        GLES30.glBindVertexArray(vao)

        // Upload vertex data
        val vertexBuffer =
            ByteBuffer
                .allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertices)
                .apply { position(0) }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertexBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)

        // Upload normal data
        val normalBuffer =
            ByteBuffer
                .allocateDirect(normals.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(normals)
                .apply { position(0) }

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, normalVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, normals.size * 4, normalBuffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)

        // Upload index data
        val indexBuffer =
            ByteBuffer
                .allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
                .put(indices)
                .apply { position(0) }

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexVbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, indexBuffer, GLES30.GL_STATIC_DRAW)

        // Unbind
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        return GeometryBuffers(vao, vertexVbo, normalVbo, indexVbo, indices.size)
    }

    /**
     * Get or create cached geometry buffers for a type.
     */
    private fun getOrCreateGeometryBuffers(type: GeometryType): GeometryBuffers? {
        return geometryCache.getOrPut(type) {
            when (type) {
                GeometryType.CUBE -> createCubeGeometry()
                GeometryType.SPHERE -> createSphereGeometry()
                GeometryType.CYLINDER -> createCylinderGeometry()
                else -> return null
            }
        }
    }

    /**
     * Get or create cached geometry buffers for a custom mesh, keyed by node ID.
     */
    private fun getOrCreateCustomMeshBuffers(nodeId: String, geometry: GeometryData): GeometryBuffers? {
        return customMeshCache.getOrPut(nodeId) {
            val vertices = geometry.vertices ?: return null
            val indices = geometry.indices ?: return null
            val normals = geometry.normals ?: FloatArray(vertices.size) // default zero normals

            val shortIndices = ShortArray(indices.size) { indices[it].toShort() }
            return@getOrPut createGeometryBuffers(vertices, normals, shortIndices)
        }
    }

    /**
     * Create cube geometry data.
     */
    private fun createCubeGeometry(): GeometryBuffers {
        val halfSize = 0.5f
        val vertices =
            floatArrayOf(
                // Front face
                -halfSize,
                -halfSize,
                halfSize,
                halfSize,
                -halfSize,
                halfSize,
                halfSize,
                halfSize,
                halfSize,
                -halfSize,
                halfSize,
                halfSize,
                // Back face
                -halfSize,
                -halfSize,
                -halfSize,
                halfSize,
                -halfSize,
                -halfSize,
                halfSize,
                halfSize,
                -halfSize,
                -halfSize,
                halfSize,
                -halfSize,
            )

        val normals =
            floatArrayOf(
                // Front
                0f,
                0f,
                1f,
                0f,
                0f,
                1f,
                0f,
                0f,
                1f,
                0f,
                0f,
                1f,
                // Back
                0f,
                0f,
                -1f,
                0f,
                0f,
                -1f,
                0f,
                0f,
                -1f,
                0f,
                0f,
                -1f,
            )

        val indices =
            shortArrayOf(
                // Front
                0,
                1,
                2,
                0,
                2,
                3,
                // Right
                1,
                5,
                6,
                1,
                6,
                2,
                // Back
                5,
                4,
                7,
                5,
                7,
                6,
                // Left
                4,
                0,
                3,
                4,
                3,
                7,
                // Top
                3,
                2,
                6,
                3,
                6,
                7,
                // Bottom
                4,
                5,
                1,
                4,
                1,
                0,
            )

        return createGeometryBuffers(vertices, normals, indices)
    }

    /**
     * Create sphere geometry data.
     */
    private fun createSphereGeometry(segments: Int = 16): GeometryBuffers {
        val radius = 1.0f
        val rings = segments / 2
        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        val indices = mutableListOf<Short>()

        // Generate vertices
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

        return createGeometryBuffers(vertices.toFloatArray(), normals.toFloatArray(), indices.toShortArray())
    }

    /**
     * Create cylinder geometry data.
     */
    private fun createCylinderGeometry(segments: Int = 16): GeometryBuffers {
        val radius = 0.5f
        val halfHeight = 0.5f
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

        // Side indices
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
        val centerTop = (vertices.size / 3).toShort()
        vertices.add(0f)
        vertices.add(halfHeight)
        vertices.add(0f)
        normals.add(0f)
        normals.add(1f)
        normals.add(0f)

        val centerBottom = (vertices.size / 3).toShort()
        vertices.add(0f)
        vertices.add(-halfHeight)
        vertices.add(0f)
        normals.add(0f)
        normals.add(-1f)
        normals.add(0f)

        // Top cap indices
        for (i in 0 until segments) {
            indices.add(centerTop)
            indices.add((i * 2).toShort())
            indices.add(((i + 1) * 2).toShort())
        }

        // Bottom cap indices
        for (i in 0 until segments) {
            indices.add(centerBottom)
            indices.add(((i + 1) * 2 + 1).toShort())
            indices.add((i * 2 + 1).toShort())
        }

        return createGeometryBuffers(vertices.toFloatArray(), normals.toFloatArray(), indices.toShortArray())
    }

    /**
     * Delete geometry buffers.
     */
    private fun deleteGeometryBuffers(buffers: GeometryBuffers) {
        val vaoIds = intArrayOf(buffers.vao)
        GLES30.glDeleteVertexArrays(1, vaoIds, 0)

        val vboIds = intArrayOf(buffers.vertexVbo, buffers.normalVbo, buffers.indexVbo)
        GLES30.glDeleteBuffers(3, vboIds, 0)
    }


    private fun render3DScene(
        frame: Frame,
        arCamera: Camera,
    ) {
        // Copy scene nodes under lock to avoid race conditions
        val nodesToRender =
            sceneLock.withLock {
                if (sceneNodes.isEmpty()) {
                    return // Early return if empty
                }
                sceneNodes.toList() // Create a copy
            }

        if (sceneProgram == 0) {
            return
        }

        GLES30.glUseProgram(sceneProgram)

        // Get camera matrices
        arCamera.getViewMatrix(viewMatrix, 0)
        arCamera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)

        // Set light direction (from above-front)
        val lightDirLoc = GLES30.glGetUniformLocation(sceneProgram, "u_LightDir")
        GLES30.glUniform3f(lightDirLoc, 0.0f, 0.7f, 0.7f)

        // Render each node recursively (groups contain children)
        fun renderNodes(nodes: List<SceneNode>) {
            nodes.forEach { node ->
                when (node) {
                    is SceneNode.Mesh -> renderMesh(node)
                    is SceneNode.Group -> if (node.visible) renderNodes(node.children)
                }
            }
        }
        renderNodes(nodesToRender)
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

        // Use cached geometry buffers instead of creating new ones every frame
        val buffers = if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
            getOrCreateCustomMeshBuffers(mesh.id, mesh.geometry)
        } else {
            getOrCreateGeometryBuffers(mesh.geometry.type)
        }
        if (buffers != null) {
            // Bind VAO and draw
            GLES30.glBindVertexArray(buffers.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, buffers.indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
            GLES30.glBindVertexArray(0)
        }
    }

    /**
     * Perform world-space raycasting against scene meshes.
     *
     * Tests a ray against all visible mesh nodes in the scene and returns
     * the closest intersection point.
     *
     * @param origin Ray origin in world space
     * @param direction Ray direction (should be normalized)
     * @return Closest intersection or null if no hit
     */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        val nodesToTest =
            sceneLock.withLock {
                sceneNodes.toList()
            }

        var closestHit: RaycastResult? = null
        var closestDistance = Float.MAX_VALUE

        fun testNodes(nodes: List<SceneNode>) {
            nodes.forEach { node ->
                when (node) {
                    is SceneNode.Mesh -> {
                        if (!node.visible) return@forEach

                        val hit = raycastMesh(origin, direction, node)
                        if (hit != null && hit.distance < closestDistance) {
                            closestDistance = hit.distance
                            closestHit = hit
                        }
                    }
                    is SceneNode.Group -> if (node.visible) testNodes(node.children)
                }
            }
        }
        testNodes(nodesToTest)

        return closestHit
    }

    /**
     * Test ray against a single mesh node.
     */
    private fun raycastMesh(
        origin: Vec3,
        direction: Vec3,
        mesh: SceneNode.Mesh,
    ): RaycastResult? {
        val transform = mesh.transform

        // Build inverse model matrix to transform ray to model space
        val invPosition = Vec3(-transform.position.x, -transform.position.y, -transform.position.z)
        val invRotation = transform.rotation.conjugate()
        val invScale =
            Vec3(
                if (transform.scale.x != 0f) 1f / transform.scale.x else 1f,
                if (transform.scale.y != 0f) 1f / transform.scale.y else 1f,
                if (transform.scale.z != 0f) 1f / transform.scale.z else 1f,
            )

        // Transform ray to model space
        val localOrigin = (invRotation * (origin + invPosition)) * invScale
        val localDir = (invRotation * direction) * invScale

        // Perform intersection test based on geometry type
        val localHit =
            when (mesh.geometry.type) {
                GeometryType.CUBE -> raycastBox(localOrigin, localDir)
                GeometryType.SPHERE -> raycastSphere(localOrigin, localDir)
                GeometryType.CYLINDER -> raycastCylinder(localOrigin, localDir)
                else -> null
            }

        if (localHit == null) return null

        // Transform hit back to world space
        val worldHitPos = (transform.rotation * (localHit * transform.scale)) + transform.position

        val distance = (worldHitPos - origin).length()
        val normal = transform.rotation * localHit.normalized()

        return RaycastResult(
            position = worldHitPos,
            normal = normal,
            distance = distance,
            nodeId = mesh.id,
        )
    }

    /**
     * Ray-box intersection (unit cube centered at origin).
     */
    private fun raycastBox(
        origin: Vec3,
        direction: Vec3,
    ): Vec3? {
        val bounds = 0.5f
        var tMin = -Float.MAX_VALUE
        var tMax = Float.MAX_VALUE

        // Test each axis
        for (axis in 0..2) {
            val o = origin.component(axis)
            val d = direction.component(axis)

            if (kotlin.math.abs(d) < 0.0001f) {
                // Ray is parallel to slab
                if (o < -bounds || o > bounds) return null
            } else {
                val t1 = (-bounds - o) / d
                val t2 = (bounds - o) / d

                val tNear = kotlin.math.min(t1, t2)
                val tFar = kotlin.math.max(t1, t2)

                tMin = kotlin.math.max(tMin, tNear)
                tMax = kotlin.math.min(tMax, tFar)

                if (tMin > tMax) return null
            }
        }

        if (tMin < 0) return null // Behind ray origin

        return Vec3(
            origin.x + direction.x * tMin,
            origin.y + direction.y * tMin,
            origin.z + direction.z * tMin,
        )
    }

    /**
     * Ray-sphere intersection (unit sphere at origin).
     */
    private fun raycastSphere(
        origin: Vec3,
        direction: Vec3,
    ): Vec3? {
        val radius = 0.5f

        // Solve quadratic equation: |origin + t*direction| = radius
        val a = direction.dot(direction)
        val b = 2f * origin.dot(direction)
        val c = origin.dot(origin) - radius * radius

        val discriminant = b * b - 4f * a * c
        if (discriminant < 0) return null

        val t = (-b - kotlin.math.sqrt(discriminant)) / (2f * a)
        if (t < 0) return null

        return Vec3(
            origin.x + direction.x * t,
            origin.y + direction.y * t,
            origin.z + direction.z * t,
        )
    }

    /**
     * Ray-cylinder intersection (unit cylinder along Y axis).
     */
    private fun raycastCylinder(
        origin: Vec3,
        direction: Vec3,
    ): Vec3? {
        val radius = 0.5f
        val halfHeight = 0.5f

        // Test infinite cylinder first (ignore Y)
        val a = direction.x * direction.x + direction.z * direction.z
        val b = 2f * (origin.x * direction.x + origin.z * direction.z)
        val c = origin.x * origin.x + origin.z * origin.z - radius * radius

        val discriminant = b * b - 4f * a * c
        if (discriminant < 0) return null

        val t1 = (-b - kotlin.math.sqrt(discriminant)) / (2f * a)
        val t2 = (-b + kotlin.math.sqrt(discriminant)) / (2f * a)

        // Check if intersection is within height bounds
        val y1 = origin.y + t1 * direction.y
        val y2 = origin.y + t2 * direction.y

        val t =
            when {
                t1 >= 0 && y1 >= -halfHeight && y1 <= halfHeight -> t1
                t2 >= 0 && y2 >= -halfHeight && y2 <= halfHeight -> t2
                else -> return null
            }

        return Vec3(
            origin.x + direction.x * t,
            origin.y + direction.y * t,
            origin.z + direction.z * t,
        )
    }

    // Helper function for Vec3
    private fun Vec3.component(index: Int): Float =
        when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IllegalArgumentException("Invalid component index: $index")
        }

    /**
     * Clean up all OpenGL resources.
     * Must be called when the renderer is no longer needed.
     */
    public fun destroy() {
        // Delete all OpenGL resources to prevent GPU memory leaks

        // Delete textures
        if (cameraTextureId != 0) {
            val textureIds = intArrayOf(cameraTextureId)
            GLES30.glDeleteTextures(1, textureIds, 0)
            cameraTextureId = 0
        }

        // Delete programs
        if (backgroundProgram != 0) {
            GLES30.glDeleteProgram(backgroundProgram)
            backgroundProgram = 0
        }
        if (sceneProgram != 0) {
            GLES30.glDeleteProgram(sceneProgram)
            sceneProgram = 0
        }

        // Delete shaders
        if (backgroundVertexShader != 0) {
            GLES30.glDeleteShader(backgroundVertexShader)
            backgroundVertexShader = 0
        }
        if (backgroundFragmentShader != 0) {
            GLES30.glDeleteShader(backgroundFragmentShader)
            backgroundFragmentShader = 0
        }
        if (vertexShader != 0) {
            GLES30.glDeleteShader(vertexShader)
            vertexShader = 0
        }
        if (fragmentShader != 0) {
            GLES30.glDeleteShader(fragmentShader)
            fragmentShader = 0
        }

        // Delete all cached VBOs/VAOs
        geometryCache.values.forEach { deleteGeometryBuffers(it) }
        geometryCache.clear()
        customMeshCache.values.forEach { deleteGeometryBuffers(it) }
        customMeshCache.clear()

        // Clear scene data under lock
        sceneLock.withLock {
            sceneNodes.clear()
        }

        // Reset initialization flag
        isInitialized = false
    }
}
