package com.spatialkit.engine.arcore

import android.content.Context
import android.graphics.BitmapFactory
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.Aabb
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.LightEstimate
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneBounds
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SceneRaycaster
import com.spatialkit.engine.TextureData
import com.spatialkit.engine.TextureFilter
import com.spatialkit.engine.TextureWrap
import com.spatialkit.engine.Vec3
import com.spatialkit.render.SceneNode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Data class to hold VBO/VAO resources for a geometry.
 *
 * @property uvVbo UV coordinate buffer, or 0 if the geometry has no UVs
 */
private data class GeometryBuffers(
    val vao: Int,
    val vertexVbo: Int,
    val normalVbo: Int,
    val indexVbo: Int,
    val indexCount: Int,
    val uvVbo: Int = 0,
) {
    val hasUvs: Boolean get() = uvVbo != 0
}

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

    // GL texture cache keyed by TextureData.id (decode + upload happens once)
    private val textureCache = mutableMapOf<String, Int>()

    // Texture ids that failed to decode; skipped instead of retried every frame
    private val failedTextures = mutableSetOf<String>()

    // Bumped by updateScene; render thread evicts stale GPU resources on change
    private var sceneRevision = 0
    private var renderedRevision = -1

    // Cached uniform locations for the texture path (avoids per-frame lookups)
    private var hasTextureLoc = -1
    private var baseColorTexLoc = -1

    // M9.1 light estimation: lit color is scaled by estimate × color correction
    private var lightColorLoc = -1

    // M9.3 depth occlusion (ARCore Depth API)
    private var depthTextureId = 0
    private var hasDepthTexture = false
    private var useOcclusionLoc = -1
    private var depthTexLoc = -1
    private var depthUvOriginLoc = -1
    private var depthUvDxLoc = -1
    private var depthUvDyLoc = -1
    private var viewportLoc = -1
    private var viewLoc = -1
    private var viewportWidth = 1
    private var viewportHeight = 1

    // M9.2 blob shadows
    private var shadowProgram = 0
    private var shadowVertexShaderId = 0
    private var shadowFragmentShaderId = 0
    private var shadowVao = 0
    private var shadowVbo = 0
    private var shadowMvpLoc = -1
    private var shadowOpacityLoc = -1

    // Local AABBs of custom meshes, cached by node id (vertex scans are O(n))
    private val customMeshLocalBounds = mutableMapOf<String, Aabb?>()

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

        SpatialLog.debug(TAG) { "initializing OpenGL resources" }
        initializeCameraTexture()
        initializeBackgroundShader()
        initialize3DShader()
        isInitialized = true
        SpatialLog.debug(TAG) { "backgroundProgram=$backgroundProgram cameraTextureId=$cameraTextureId" }
        SpatialLog.debug(TAG) { "sceneProgram=$sceneProgram" }
        SpatialLog.debug(TAG) { "initialization complete" }
    }

    public fun resize(
        width: Int,
        height: Int,
    ) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
    }

    /**
     * Update the scene with new data.
     */
    public fun updateScene(sceneData: SceneData) {
        sceneLock.withLock {
            sceneNodes.clear()
            sceneNodes.addAll(sceneData.nodes)
            sceneRevision++
        }
    }

    /**
     * Render one full frame: camera background, then scene content.
     *
     * @param lightEstimate This frame's light estimate; scales scene lighting
     *   to match the environment (M9.1). Null renders with neutral lighting.
     * @param depthImage This frame's ARCore DEPTH16 image for occlusion
     *   (M9.3), or null. The caller keeps ownership and closes it; the
     *   renderer copies it into a GL texture. The last uploaded depth image
     *   keeps occluding when a frame briefly has none.
     */
    public fun render(
        frame: Frame,
        arCamera: Camera,
        lightEstimate: LightEstimate? = null,
        depthImage: Image? = null,
    ) {
        // Ensure initialization happened (should be done in onSurfaceCreated)
        if (!isInitialized) {
            SpatialLog.warn(TAG) { "render() called before initialize()" }
            initialize()
        }

        if (depthImage != null) {
            uploadDepthImage(depthImage)
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
        render3DScene(frame, arCamera, lightEstimate)
    }

    /**
     * Copies an ARCore DEPTH16 image into an R16UI texture (unit 1) for the
     * occlusion test in the scene shader. DEPTH16 rows may be padded, so the
     * row stride is passed via GL_UNPACK_ROW_LENGTH (GLES 3.0).
     */
    private fun uploadDepthImage(image: Image) {
        val plane = image.planes.firstOrNull() ?: return
        val buffer = plane.buffer ?: return

        if (depthTextureId == 0) {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            depthTextureId = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTextureId)
            // Integer textures require NEAREST filtering
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        } else {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTextureId)
        }

        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, plane.rowStride / 2)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_R16UI,
            image.width,
            image.height,
            0,
            GLES30.GL_RED_INTEGER,
            GLES30.GL_UNSIGNED_SHORT,
            buffer,
        )
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ROW_LENGTH, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        hasDepthTexture = true
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
            layout(location = 2) in vec2 a_TexCoord;

            uniform mat4 u_MVP;
            uniform mat4 u_Model;
            uniform mat4 u_View;

            out vec3 v_Normal;
            out vec3 v_Position;
            out vec2 v_TexCoord;
            out vec3 v_ViewPos;

            void main() {
                vec4 worldPos = u_Model * vec4(a_Position, 1.0);
                gl_Position = u_MVP * vec4(a_Position, 1.0);
                v_Normal = mat3(u_Model) * a_Normal;
                v_Position = worldPos.xyz;
                v_TexCoord = a_TexCoord;
                v_ViewPos = (u_View * worldPos).xyz;
            }
            """.trimIndent()

        // Simple fragment shader with basic lighting.
        // The base-color texture is multiplied with u_Color, matching glTF's
        // baseColorFactor × baseColorTexture semantics. The lit color is
        // scaled by u_LightColor (light estimate × color correction; (1,1,1)
        // when estimation is off — M9.1). When u_UseOcclusion is set, the
        // fragment's view-space depth is tested against the ARCore depth map
        // and hidden fragments fade out over a soft window (M9.3).
        val fragmentShader =
            """
            #version 300 es
            precision mediump float;

            in vec3 v_Normal;
            in vec3 v_Position;
            in vec2 v_TexCoord;
            in vec3 v_ViewPos;

            uniform vec3 u_Color;
            uniform vec3 u_LightDir;
            uniform vec3 u_LightColor;
            uniform bool u_HasTexture;
            uniform sampler2D u_BaseColorTex;

            uniform bool u_UseOcclusion;
            uniform highp usampler2D u_DepthTex;
            uniform vec2 u_DepthUvOrigin;
            uniform vec2 u_DepthUvDx;
            uniform vec2 u_DepthUvDy;
            uniform vec2 u_Viewport;

            out vec4 fragColor;

            void main() {
                vec3 baseColor = u_Color;
                if (u_HasTexture) {
                    baseColor *= texture(u_BaseColorTex, v_TexCoord).rgb;
                }
                vec3 normal = normalize(v_Normal);
                float diffuse = max(dot(normal, u_LightDir), 0.0);
                vec3 color = (baseColor * 0.3 + baseColor * diffuse * 0.7) * u_LightColor;

                float visibility = 1.0;
                if (u_UseOcclusion) {
                    // Screen (0-1, bottom-left origin) -> camera-image UV via
                    // the display transform sampled at the frame's corners
                    vec2 s = gl_FragCoord.xy / u_Viewport;
                    vec2 uv = u_DepthUvOrigin + u_DepthUvDx * s.x + u_DepthUvDy * s.y;
                    // DEPTH16: low 13 bits = depth in millimeters
                    uint raw = texture(u_DepthTex, uv).r;
                    float realMm = float(raw & 0x1FFFu);
                    if (realMm > 0.0) {
                        float virtualMm = -v_ViewPos.z * 1000.0;
                        // Fade only fragments clearly BEHIND the measured
                        // depth, over a soft 80mm band. Full visibility at
                        // equal depth: content resting on a surface sits at
                        // the surface's own depth reading and must not
                        // self-fade (a band centered on the edge would leave
                        // it shimmering at ~50% alpha from depth noise).
                        visibility = clamp((realMm - virtualMm) / 80.0 + 1.0, 0.0, 1.0);
                    }
                }
                fragColor = vec4(color, visibility);
            }
            """.trimIndent()

        sceneProgram = createProgram(vertexShader, fragmentShader, saveShaders = true)
        hasTextureLoc = GLES30.glGetUniformLocation(sceneProgram, "u_HasTexture")
        baseColorTexLoc = GLES30.glGetUniformLocation(sceneProgram, "u_BaseColorTex")
        lightColorLoc = GLES30.glGetUniformLocation(sceneProgram, "u_LightColor")
        useOcclusionLoc = GLES30.glGetUniformLocation(sceneProgram, "u_UseOcclusion")
        depthTexLoc = GLES30.glGetUniformLocation(sceneProgram, "u_DepthTex")
        depthUvOriginLoc = GLES30.glGetUniformLocation(sceneProgram, "u_DepthUvOrigin")
        depthUvDxLoc = GLES30.glGetUniformLocation(sceneProgram, "u_DepthUvDx")
        depthUvDyLoc = GLES30.glGetUniformLocation(sceneProgram, "u_DepthUvDy")
        viewportLoc = GLES30.glGetUniformLocation(sceneProgram, "u_Viewport")
        viewLoc = GLES30.glGetUniformLocation(sceneProgram, "u_View")

        // The depth sampler is an integer sampler; park it on its own unit so
        // it never aliases the float base-color sampler on unit 0 (undefined
        // behavior even when the occlusion branch is not taken)
        GLES30.glUseProgram(sceneProgram)
        GLES30.glUniform1i(depthTexLoc, 1)
        GLES30.glUseProgram(0)

        initializeShadowShader()
    }

    /**
     * Blob-shadow pass resources (M9.2): a unit XZ quad faded radially in the
     * fragment shader, alpha-blended under shadow-casting nodes.
     */
    private fun initializeShadowShader() {
        val vertexShader =
            """
            #version 300 es
            precision mediump float;

            layout(location = 0) in vec2 a_Position;

            uniform mat4 u_MVP;

            out vec2 v_Local;

            void main() {
                gl_Position = u_MVP * vec4(a_Position.x, 0.0, a_Position.y, 1.0);
                v_Local = a_Position;
            }
            """.trimIndent()

        val fragmentShader =
            """
            #version 300 es
            precision mediump float;

            in vec2 v_Local;

            uniform float u_Opacity;

            out vec4 fragColor;

            void main() {
                // 0 at the quad center, 1 at the inscribed circle's rim
                float r = length(v_Local) * 2.0;
                float alpha = u_Opacity * (1.0 - smoothstep(0.35, 1.0, r));
                fragColor = vec4(0.0, 0.0, 0.0, alpha);
            }
            """.trimIndent()

        val vShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexShader)
        val fShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentShader)
        shadowProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(shadowProgram, vShader)
        GLES30.glAttachShader(shadowProgram, fShader)
        GLES30.glLinkProgram(shadowProgram)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(shadowProgram, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val error = GLES30.glGetProgramInfoLog(shadowProgram)
            GLES30.glDeleteProgram(shadowProgram)
            GLES30.glDeleteShader(vShader)
            GLES30.glDeleteShader(fShader)
            shadowProgram = 0
            SpatialLog.error(TAG) { "shadow program link failed: $error" }
            return
        }
        shadowVertexShaderId = vShader
        shadowFragmentShaderId = fShader
        shadowMvpLoc = GLES30.glGetUniformLocation(shadowProgram, "u_MVP")
        shadowOpacityLoc = GLES30.glGetUniformLocation(shadowProgram, "u_Opacity")

        // Unit quad in XZ, two triangles
        val quad =
            floatArrayOf(
                -0.5f,
                -0.5f,
                0.5f,
                -0.5f,
                -0.5f,
                0.5f,
                0.5f,
                -0.5f,
                0.5f,
                0.5f,
                -0.5f,
                0.5f,
            )
        val vaoIds = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoIds, 0)
        shadowVao = vaoIds[0]
        val vboIds = IntArray(1)
        GLES30.glGenBuffers(1, vboIds, 0)
        shadowVbo = vboIds[0]

        val buffer =
            ByteBuffer
                .allocateDirect(quad.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(quad)
                .apply { position(0) }
        GLES30.glBindVertexArray(shadowVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, shadowVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, buffer, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun renderBackground(frame: Frame) {
        if (backgroundProgram == 0) {
            SpatialLog.error(TAG) { "backgroundProgram is 0" }
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
        uvs: FloatArray? = null,
    ): GeometryBuffers {
        // Generate VAO
        val vaoIds = IntArray(1)
        GLES30.glGenVertexArrays(1, vaoIds, 0)
        val vao = vaoIds[0]

        // Generate VBOs
        val vboCount = if (uvs != null) 4 else 3
        val vboIds = IntArray(vboCount)
        GLES30.glGenBuffers(vboCount, vboIds, 0)
        val vertexVbo = vboIds[0]
        val normalVbo = vboIds[1]
        val indexVbo = vboIds[2]
        val uvVbo = if (uvs != null) vboIds[3] else 0

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

        // Upload UV data (location 2), if present
        if (uvs != null) {
            val uvBuffer =
                ByteBuffer
                    .allocateDirect(uvs.size * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(uvs)
                    .apply { position(0) }

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, uvVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, uvs.size * 4, uvBuffer, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 0, 0)
        }

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

        return GeometryBuffers(vao, vertexVbo, normalVbo, indexVbo, indices.size, uvVbo)
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
    private fun getOrCreateCustomMeshBuffers(
        nodeId: String,
        geometry: GeometryData,
    ): GeometryBuffers? {
        return customMeshCache.getOrPut(nodeId) {
            val vertices = geometry.vertices ?: return null
            val indices = geometry.indices ?: return null
            val normals =
                geometry.normals?.takeIf { it.size == vertices.size }
                    ?: com.spatialkit.engine.generateVertexNormals(vertices, indices)
            // Two UV components per three vertex components
            val uvs = geometry.uvs?.takeIf { it.size / 2 == vertices.size / 3 }

            val shortIndices = ShortArray(indices.size) { indices[it].toShort() }
            return@getOrPut createGeometryBuffers(vertices, normals, shortIndices, uvs)
        }
    }

    // ===== Texture Caching Implementation =====

    /**
     * Returns the GL texture for [data], decoding and uploading it on first
     * use (GL thread). Returns 0 if decoding failed; failures are recorded so
     * they aren't retried every frame.
     */
    private fun getOrCreateTexture(data: TextureData): Int {
        textureCache[data.id]?.let { return it }
        if (data.id in failedTextures) return 0

        val bitmap = BitmapFactory.decodeByteArray(data.data, 0, data.data.size)
        if (bitmap == null) {
            SpatialLog.warn(TAG) { "texture decode failed for '${data.id}' (${data.data.size} bytes)" }
            failedTextures.add(data.id)
            return 0
        }

        val textureIds = IntArray(1)
        GLES30.glGenTextures(1, textureIds, 0)
        val textureId = textureIds[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, data.sampler.wrapU.toGl())
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, data.sampler.wrapV.toGl())
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MIN_FILTER,
            when (data.sampler.minFilter) {
                TextureFilter.NEAREST -> GLES30.GL_NEAREST
                // Trilinear for quality at glancing angles (mipmaps generated below)
                TextureFilter.LINEAR -> GLES30.GL_LINEAR_MIPMAP_LINEAR
            },
        )
        GLES30.glTexParameteri(
            GLES30.GL_TEXTURE_2D,
            GLES30.GL_TEXTURE_MAG_FILTER,
            when (data.sampler.magFilter) {
                TextureFilter.NEAREST -> GLES30.GL_NEAREST
                TextureFilter.LINEAR -> GLES30.GL_LINEAR
            },
        )
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        if (data.sampler.minFilter == TextureFilter.LINEAR) {
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        bitmap.recycle()

        textureCache[data.id] = textureId
        return textureId
    }

    private fun TextureWrap.toGl(): Int =
        when (this) {
            TextureWrap.REPEAT -> GLES30.GL_REPEAT
            TextureWrap.MIRRORED_REPEAT -> GLES30.GL_MIRRORED_REPEAT
            TextureWrap.CLAMP_TO_EDGE -> GLES30.GL_CLAMP_TO_EDGE
        }

    /**
     * Frees GPU resources for nodes/textures no longer in the scene. Runs on
     * the GL thread, and only when the scene actually changed (see
     * [sceneRevision]) so steady-state frames do no extra work.
     */
    private fun evictStaleResources(nodes: List<SceneNode>) {
        val liveNodeIds = mutableSetOf<String>()
        val liveTextureIds = mutableSetOf<String>()

        fun collect(nodes: List<SceneNode>) {
            nodes.forEach { node ->
                liveNodeIds.add(node.id)
                when (node) {
                    is SceneNode.Mesh -> node.material.baseColorTexture?.let { liveTextureIds.add(it.id) }
                    is SceneNode.Group -> collect(node.children)
                    // v2-only node type; the v1 GLES renderer never receives it.
                    is SceneNode.Model -> Unit
                }
            }
        }
        collect(nodes)

        customMeshCache.entries.removeAll { (nodeId, buffers) ->
            (nodeId !in liveNodeIds).also { stale ->
                if (stale) deleteGeometryBuffers(buffers)
            }
        }
        customMeshLocalBounds.keys.retainAll(liveNodeIds)
        textureCache.entries.removeAll { (textureId, glTexture) ->
            (textureId !in liveTextureIds).also { stale ->
                if (stale) GLES30.glDeleteTextures(1, intArrayOf(glTexture), 0)
            }
        }
        failedTextures.retainAll(liveTextureIds)
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

        val vboIds =
            if (buffers.hasUvs) {
                intArrayOf(buffers.vertexVbo, buffers.normalVbo, buffers.indexVbo, buffers.uvVbo)
            } else {
                intArrayOf(buffers.vertexVbo, buffers.normalVbo, buffers.indexVbo)
            }
        GLES30.glDeleteBuffers(vboIds.size, vboIds, 0)
    }

    private fun render3DScene(
        frame: Frame,
        arCamera: Camera,
        lightEstimate: LightEstimate?,
    ) {
        // Copy scene nodes under lock to avoid race conditions
        val (nodesToRender, revision) =
            sceneLock.withLock {
                if (sceneNodes.isEmpty()) {
                    return // Early return if empty
                }
                sceneNodes.toList() to sceneRevision // Create a copy
            }

        if (sceneProgram == 0) {
            return
        }

        // Free GPU resources for removed nodes/textures (GL thread only)
        if (revision != renderedRevision) {
            evictStaleResources(nodesToRender)
            renderedRevision = revision
        }

        GLES30.glUseProgram(sceneProgram)

        // Get camera matrices
        arCamera.getViewMatrix(viewMatrix, 0)
        arCamera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        GLES30.glUniformMatrix4fv(viewLoc, 1, false, viewMatrix, 0)

        // Set light direction (from above-front)
        val lightDirLoc = GLES30.glGetUniformLocation(sceneProgram, "u_LightDir")
        GLES30.glUniform3f(lightDirLoc, 0.0f, 0.7f, 0.7f)

        // M9.1: scale lighting by the environment estimate (neutral without one)
        if (lightEstimate != null) {
            val intensity = lightEstimate.ambientIntensity.coerceIn(0f, 2f)
            GLES30.glUniform3f(
                lightColorLoc,
                lightEstimate.ambientColor.x * intensity,
                lightEstimate.ambientColor.y * intensity,
                lightEstimate.ambientColor.z * intensity,
            )
        } else {
            GLES30.glUniform3f(lightColorLoc, 1f, 1f, 1f)
        }

        // M9.3: occlusion against the last uploaded depth image. The
        // screen->camera-UV affine comes from the same display transform the
        // camera background was drawn with this frame (see renderBackground).
        val useOcclusion = hasDepthTexture && depthTextureId != 0
        GLES30.glUniform1i(useOcclusionLoc, if (useOcclusion) 1 else 0)
        if (useOcclusion) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, depthTextureId)
            GLES30.glUniform2f(viewportLoc, viewportWidth.toFloat(), viewportHeight.toFloat())
            GLES30.glUniform2f(depthUvOriginLoc, transformedUvs[0], transformedUvs[1])
            GLES30.glUniform2f(
                depthUvDxLoc,
                transformedUvs[2] - transformedUvs[0],
                transformedUvs[3] - transformedUvs[1],
            )
            GLES30.glUniform2f(
                depthUvDyLoc,
                transformedUvs[4] - transformedUvs[0],
                transformedUvs[5] - transformedUvs[1],
            )
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }

        // Occluded fragments (and blob shadows) blend against the background
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        // Render each node recursively, composing parent transforms so
        // groups (and glTF model hierarchies) carry their children
        val rootMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

        fun renderNodes(
            nodes: List<SceneNode>,
            parentMatrix: FloatArray,
        ) {
            nodes.forEach { node ->
                when (node) {
                    is SceneNode.Mesh -> renderMesh(node, parentMatrix)
                    is SceneNode.Group ->
                        if (node.visible) {
                            val composed = FloatArray(16)
                            // Matrix4 is column-major like android.opengl.Matrix
                            Matrix.multiplyMM(composed, 0, parentMatrix, 0, node.transform.toMatrix().toFloatArray(), 0)
                            renderNodes(node.children, composed)
                        }
                    // v2-only node type; the v1 GLES renderer never receives it.
                    is SceneNode.Model -> Unit
                }
            }
        }
        renderNodes(nodesToRender, rootMatrix)

        renderShadows(nodesToRender)

        GLES30.glDisable(GLES30.GL_BLEND)
    }

    /**
     * Local bounds provider for [SceneBounds.worldBounds]; custom-mesh vertex
     * scans are cached by node id (evicted with the mesh's GPU buffers).
     */
    private fun cachedLocalBounds(mesh: SceneNode.Mesh): Aabb? =
        if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
            customMeshLocalBounds.getOrPut(mesh.id) { SceneBounds.localBounds(mesh.geometry) }
        } else {
            SceneBounds.localBounds(mesh.geometry)
        }

    /**
     * Draws a soft blob shadow (M9.2) under each shadow-casting subtree: a
     * radially faded dark quad at the base of the subtree's world AABB,
     * sized slightly beyond its footprint. Depth-tested but not depth-written,
     * so shadows never occlude content. Requires blending to be enabled.
     */
    private fun renderShadows(nodes: List<SceneNode>) {
        if (shadowProgram == 0) return
        val casters = SceneBounds.shadowCasters(nodes, ::cachedLocalBounds)
        if (casters.isEmpty()) return

        GLES30.glUseProgram(shadowProgram)
        GLES30.glDepthMask(false)
        GLES30.glBindVertexArray(shadowVao)

        val model = FloatArray(16)
        val viewModel = FloatArray(16)
        val mvp = FloatArray(16)

        casters.forEach { (_, bounds) ->
            val size = bounds.size
            val diameter = maxOf(size.x, size.z) * SHADOW_FOOTPRINT_SCALE
            if (diameter <= 0f) return@forEach

            Matrix.setIdentityM(model, 0)
            // Slightly above the base to avoid z-fighting with the surface
            Matrix.translateM(model, 0, bounds.center.x, bounds.min.y + 0.002f, bounds.center.z)
            Matrix.scaleM(model, 0, diameter, 1f, diameter)
            Matrix.multiplyMM(viewModel, 0, viewMatrix, 0, model, 0)
            Matrix.multiplyMM(mvp, 0, projectionMatrix, 0, viewModel, 0)

            GLES30.glUniformMatrix4fv(shadowMvpLoc, 1, false, mvp, 0)
            GLES30.glUniform1f(shadowOpacityLoc, SHADOW_OPACITY)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        }

        GLES30.glBindVertexArray(0)
        GLES30.glDepthMask(true)
    }

    private fun renderMesh(
        mesh: SceneNode.Mesh,
        parentMatrix: FloatArray,
    ) {
        if (!mesh.visible) return

        // Model matrix = parent chain × local TRS
        Matrix.multiplyMM(modelMatrix, 0, parentMatrix, 0, mesh.transform.toMatrix().toFloatArray(), 0)

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
        val buffers =
            if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
                getOrCreateCustomMeshBuffers(mesh.id, mesh.geometry)
            } else {
                getOrCreateGeometryBuffers(mesh.geometry.type)
            }
        if (buffers != null) {
            // Bind base-color texture if the material has one and the mesh has UVs
            val textureData = mesh.material.baseColorTexture
            val glTexture = if (textureData != null && buffers.hasUvs) getOrCreateTexture(textureData) else 0
            if (glTexture != 0) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, glTexture)
                GLES30.glUniform1i(baseColorTexLoc, 0)
                GLES30.glUniform1i(hasTextureLoc, 1)
            } else {
                GLES30.glUniform1i(hasTextureLoc, 0)
            }

            // Bind VAO and draw
            GLES30.glBindVertexArray(buffers.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, buffers.indexCount, GLES30.GL_UNSIGNED_SHORT, 0)
            GLES30.glBindVertexArray(0)
            if (glTexture != 0) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            }
        }
    }

    /**
     * Perform world-space raycasting against scene content.
     *
     * Delegates to the shared [SceneRaycaster]: all geometry types (including
     * custom meshes) are tested, and group transforms are accumulated, so
     * picking matches what is rendered.
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
        return SceneRaycaster.raycast(nodesToTest, origin, direction)
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

        if (depthTextureId != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(depthTextureId), 0)
            depthTextureId = 0
            hasDepthTexture = false
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
        if (shadowProgram != 0) {
            GLES30.glDeleteProgram(shadowProgram)
            shadowProgram = 0
        }
        if (shadowVertexShaderId != 0) {
            GLES30.glDeleteShader(shadowVertexShaderId)
            shadowVertexShaderId = 0
        }
        if (shadowFragmentShaderId != 0) {
            GLES30.glDeleteShader(shadowFragmentShaderId)
            shadowFragmentShaderId = 0
        }
        if (shadowVao != 0) {
            GLES30.glDeleteVertexArrays(1, intArrayOf(shadowVao), 0)
            shadowVao = 0
        }
        if (shadowVbo != 0) {
            GLES30.glDeleteBuffers(1, intArrayOf(shadowVbo), 0)
            shadowVbo = 0
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

        // Delete all cached scene textures
        if (textureCache.isNotEmpty()) {
            val ids = textureCache.values.toIntArray()
            GLES30.glDeleteTextures(ids.size, ids, 0)
            textureCache.clear()
        }
        failedTextures.clear()
        customMeshLocalBounds.clear()
        renderedRevision = -1

        // Clear scene data under lock
        sceneLock.withLock {
            sceneNodes.clear()
        }

        // Reset initialization flag
        isInitialized = false
    }

    private companion object {
        const val TAG = "SimpleRenderer"

        // Blob shadows (M9.2): quad size relative to the caster's footprint,
        // and peak darkness at the blob center
        const val SHADOW_FOOTPRINT_SCALE = 1.25f
        const val SHADOW_OPACITY = 0.35f
    }
}
