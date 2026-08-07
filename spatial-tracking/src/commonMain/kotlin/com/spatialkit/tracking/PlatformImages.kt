package com.spatialkit.tracking

/**
 * Opaque platform handle to the camera image of one frame.
 *
 * Android: a GL external-OES texture id (created on the EGL context shared
 * with the renderer) plus the display UV transform. iOS: a retained
 * CVPixelBuffer with an explicit release contract — Kotlin never owns an
 * ARFrame (the v1 buffer-pool-starvation lesson).
 *
 * Valid only during the frame tick it was produced in.
 */
public expect class CameraImage

/**
 * Opaque platform handle to the depth image of one frame (ARCore DEPTH16
 * copy on Android, LiDAR sceneDepth CVPixelBuffer on iOS).
 *
 * Valid only during the frame tick it was produced in.
 */
public expect class DepthImage

/**
 * Opaque platform handle to an HDR environment cubemap estimate (ARCore
 * acquireEnvironmentalHdrCubeMap faces on Android, environment-probe
 * MTLTexture on iOS).
 */
public expect class EnvironmentCubemap
