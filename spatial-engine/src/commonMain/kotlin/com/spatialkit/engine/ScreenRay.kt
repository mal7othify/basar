package com.spatialkit.engine

import com.spatialkit.core.types.Ray

/**
 * Unprojects a normalized screen point into a world-space ray through this
 * frame's camera.
 *
 * The returned ray starts at the camera position and passes through the given
 * point on the near plane, using the frame's actual [FrameData.projectionMatrix]
 * — including the off-center terms AR platforms produce when the camera image
 * is cropped to the viewport — so the ray matches what is rendered under the
 * pixel. Feed the result to [SpatialEngine.performRaycast] to pick scene nodes.
 *
 * Screen coordinates are normalized: (0,0) = top-left, (1,1) = bottom-right,
 * matching [SpatialEngine.performHitTest].
 *
 * @param screenX Normalized X coordinate (0-1)
 * @param screenY Normalized Y coordinate (0-1)
 * @return Ray with origin at the camera and normalized world-space direction
 */
public fun FrameData.screenPointToRay(
    screenX: Float,
    screenY: Float,
): Ray {
    // Normalized (0..1, top-left origin) -> NDC (-1..1, bottom-left origin)
    val ndcX = screenX * 2f - 1f
    val ndcY = 1f - screenY * 2f

    // Column-major perspective projection: clip.x = P[0]*x + P[8]*z and
    // clip.w = -z. For a camera-space point at z = -1 this gives
    // ndc = P[0]*x - P[8], so x = (ndc + P[8]) / P[0] (same for y with
    // P[5]/P[9]). P[8]/P[9] are non-zero for off-center projections.
    val p0 = projectionMatrix[0]
    val p5 = projectionMatrix[5]
    val dirCamera =
        Vec3(
            x = if (p0 != 0f) (ndcX + projectionMatrix[8]) / p0 else ndcX,
            y = if (p5 != 0f) (ndcY + projectionMatrix[9]) / p5 else ndcY,
            z = -1f,
        )

    return Ray(
        origin = cameraPose.position,
        direction = (cameraPose.rotation * dirCamera).normalized(),
    )
}
