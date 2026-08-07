package com.spatialkit.tracking

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRelease
import platform.CoreVideo.CVPixelBufferRetain
import platform.Metal.MTLTextureProtocol

/**
 * iOS camera image: a CF-retained CVPixelBuffer (the ARFrame's
 * capturedImage). The provider retains at construction; whoever consumes it
 * (the renderer, after handing it to Filament which retains internally) MUST
 * call [release] within the same frame tick — Kotlin never owns an ARFrame,
 * and holding pixel buffers starves ARKit's fixed camera-buffer pool (the
 * v1 lesson, stricter in v2).
 */
@OptIn(ExperimentalForeignApi::class)
public actual class CameraImage(
    pixelBuffer: CVPixelBufferRef,
) {
    public val pixelBuffer: CVPixelBufferRef = CVPixelBufferRetain(pixelBuffer)!!
    private var released = false

    /** Releases the CF retain. Idempotent. */
    public fun release() {
        if (!released) {
            released = true
            CVPixelBufferRelease(pixelBuffer)
        }
    }
}

/**
 * iOS depth image: a CF-retained Float32 sceneDepth CVPixelBuffer (LiDAR).
 * Same release contract as [CameraImage].
 */
@OptIn(ExperimentalForeignApi::class)
public actual class DepthImage(
    pixelBuffer: CVPixelBufferRef,
) {
    public val pixelBuffer: CVPixelBufferRef = CVPixelBufferRetain(pixelBuffer)!!
    private var released = false

    /** Releases the CF retain. Idempotent. */
    public fun release() {
        if (!released) {
            released = true
            CVPixelBufferRelease(pixelBuffer)
        }
    }
}

/**
 * iOS HDR environment cubemap: the environment probe's MTLTexture (ObjC
 * object, ARC-managed — no manual release needed).
 */
public actual class EnvironmentCubemap(
    public val texture: MTLTextureProtocol,
)
