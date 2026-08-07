package com.spatialkit.tracking

import java.nio.ByteBuffer

/**
 * Android camera image: a GL_TEXTURE_EXTERNAL_OES texture id, created on the
 * EGL context shared with the renderer and updated by `Session.update()`,
 * plus the display UV transform mapping NDC quad corners to texture UVs
 * (from `Frame.transformCoordinates2d`, 8 floats for the 4 corners).
 */
public actual class CameraImage(
    public val textureId: Int,
    public val uvTransform: FloatArray,
)

/**
 * Android depth image: ARCore DEPTH16 contents copied out of the platform
 * image (so the `Image` can be closed immediately), row-major, tightly
 * packed (rowStride handled at copy time). Each pixel: uint16, low 13 bits
 * = millimeters.
 */
public actual class DepthImage(
    public val buffer: ByteBuffer,
    public val width: Int,
    public val height: Int,
)

/**
 * Android HDR environment cubemap: six RGBA16F face buffers (+x, -x, +y,
 * -y, +z, -z) of [size]x[size] texels, copied from
 * `acquireEnvironmentalHdrCubeMap`.
 */
public actual class EnvironmentCubemap(
    public val faces: List<ByteBuffer>,
    public val size: Int,
)
