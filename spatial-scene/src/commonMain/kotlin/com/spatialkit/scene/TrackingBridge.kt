package com.spatialkit.scene

import com.spatialkit.engine.FrameData
import com.spatialkit.tracking.TrackingFrame

/**
 * Converts v2 tracking types into their v1 engine equivalents so the scene
 * layer (InteractionManager, screenPointToRay, demo `onFrame` code) runs
 * unchanged on the v2 runtime. The two families are field-compatible by
 * construction — v2's types were modeled on v1's — except [FrameData]'s
 * `inputSources` (v1 WebXR controllers; none in v2 yet) and the richer v2
 * light estimate, which collapses onto v1's ambient + directional pair.
 *
 * These shims die with `spatial-engine` once the v1 types are gone.
 */
internal fun TrackingFrame.toFrameData(): FrameData =
    FrameData(
        timestamp = timestamp,
        deltaTime = deltaTime,
        cameraPose = cameraPose.toEngine(),
        projectionMatrix = projectionMatrix,
        viewMatrix = viewMatrix,
        trackingState = trackingState.toEngine(),
        detectedPlanes = planes.map { it.toEngine() },
        lightEstimate = lightEstimate?.toEngine(),
        inputSources = emptyList(),
        anchors = emptyList(), // remapped separately by the session (scene ids)
        trackedImages = trackedImages.map { it.toEngine() },
    )

internal fun com.spatialkit.tracking.Pose.toEngine(): com.spatialkit.engine.Pose =
    com.spatialkit.engine.Pose(position = position, rotation = rotation)

internal fun com.spatialkit.engine.Pose.toTracking(): com.spatialkit.tracking.Pose =
    com.spatialkit.tracking.Pose(position = position, rotation = rotation)

internal fun com.spatialkit.tracking.TrackingState.toEngine(): com.spatialkit.engine.TrackingState =
    when (this) {
        com.spatialkit.tracking.TrackingState.NOT_AVAILABLE -> com.spatialkit.engine.TrackingState.NOT_AVAILABLE
        com.spatialkit.tracking.TrackingState.LIMITED -> com.spatialkit.engine.TrackingState.LIMITED
        com.spatialkit.tracking.TrackingState.NORMAL -> com.spatialkit.engine.TrackingState.NORMAL
    }

internal fun com.spatialkit.tracking.DetectedPlane.toEngine(): com.spatialkit.engine.DetectedPlane =
    com.spatialkit.engine.DetectedPlane(
        id = id,
        type =
            when (type) {
                com.spatialkit.tracking.PlaneType.HORIZONTAL_UP -> com.spatialkit.engine.PlaneType.HORIZONTAL_UP
                com.spatialkit.tracking.PlaneType.HORIZONTAL_DOWN -> com.spatialkit.engine.PlaneType.HORIZONTAL_DOWN
                com.spatialkit.tracking.PlaneType.VERTICAL -> com.spatialkit.engine.PlaneType.VERTICAL
                com.spatialkit.tracking.PlaneType.UNKNOWN -> com.spatialkit.engine.PlaneType.UNKNOWN
            },
        centerPose = centerPose.toEngine(),
        extentX = extentX,
        extentZ = extentZ,
        vertices = vertices,
    )

internal fun com.spatialkit.tracking.LightEstimate.toEngine(): com.spatialkit.engine.LightEstimate =
    com.spatialkit.engine.LightEstimate(
        ambientIntensity = ambientIntensity,
        ambientColor = ambientColor,
        directionalIntensity = mainLightIntensityLux,
        directionalDirection = mainLightDirection,
    )

internal fun com.spatialkit.tracking.AnchorStatus.toEngine(): com.spatialkit.engine.AnchorStatus =
    when (this) {
        com.spatialkit.tracking.AnchorStatus.PENDING -> com.spatialkit.engine.AnchorStatus.PENDING
        com.spatialkit.tracking.AnchorStatus.TRACKING -> com.spatialkit.engine.AnchorStatus.TRACKING
        com.spatialkit.tracking.AnchorStatus.PAUSED -> com.spatialkit.engine.AnchorStatus.PAUSED
        com.spatialkit.tracking.AnchorStatus.LOST -> com.spatialkit.engine.AnchorStatus.LOST
        com.spatialkit.tracking.AnchorStatus.ERROR -> com.spatialkit.engine.AnchorStatus.ERROR
    }

internal fun com.spatialkit.tracking.HitTestResult.toEngine(): com.spatialkit.engine.HitTestResult =
    com.spatialkit.engine.HitTestResult(
        position = position,
        normal = normal,
        distance = distance,
        planeId = planeId,
    )

internal fun com.spatialkit.tracking.TrackedImage.toEngine(): com.spatialkit.engine.TrackedImage =
    com.spatialkit.engine.TrackedImage(
        imageId = imageId,
        status =
            when (status) {
                com.spatialkit.tracking.TrackedImageStatus.TRACKING ->
                    com.spatialkit.engine.TrackedImageStatus.TRACKING
                com.spatialkit.tracking.TrackedImageStatus.LAST_KNOWN_POSE ->
                    com.spatialkit.engine.TrackedImageStatus.LAST_KNOWN_POSE
                com.spatialkit.tracking.TrackedImageStatus.NOT_TRACKING ->
                    com.spatialkit.engine.TrackedImageStatus.NOT_TRACKING
            },
        pose = pose?.toEngine(),
        extentX = extentX,
        extentZ = extentZ,
    )
