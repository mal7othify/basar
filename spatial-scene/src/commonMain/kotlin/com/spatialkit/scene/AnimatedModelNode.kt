package com.spatialkit.scene

import com.spatialkit.core.math.Transform
import com.spatialkit.render.AnimationState
import com.spatialkit.render.SceneNode

/**
 * A glTF binary asset owned by the renderer (v2 R4): geometry, materials,
 * textures, and — the point — **skinned/morph animation** are handled
 * natively (Filament gltfio), unlike [ModelNode]'s CPU-side expansion into
 * [MeshNode]s.
 *
 * Animation is declarative: set [animation] (or use the `animation {}` DSL)
 * and the renderer advances the clip on its own clock; assign null to stop.
 * Like all node state, mutate it from the frame callback.
 *
 * Limitations (honest, current):
 * - Children are transform-only followers; they render, but the glTF's own
 *   node hierarchy is internal to the renderer.
 * - Not pickable by tap/raycast yet — the CPU raycaster has no triangles
 *   for renderer-loaded assets (R6 work).
 *
 * @property glb Encoded .glb bytes (kept referenced; reuse one array per
 *   asset — the renderer caches by [contentKey])
 * @property contentKey Stable identity for [glb]; defaults to a content hash
 */
public class AnimatedModelNode(
    public val glb: ByteArray,
    id: String = generateId(),
    public val contentKey: String = "glb-${glb.contentHashCode()}",
) : SpatialNode(id) {
    /** Declarative animation state; null = static pose. */
    public var animation: AnimationState? = null

    override fun toSceneNode(): SceneNode {
        val model =
            SceneNode.Model(
                id = id,
                transform =
                    Transform(
                        position = position,
                        rotation = rotation,
                        scale = scale,
                    ),
                visible = visible,
                glb = glb,
                contentKey = contentKey,
                animation = animation,
                castsShadow = castsShadow,
            )
        return if (children.isEmpty()) {
            model
        } else {
            // Children follow this node's transform; the model itself becomes
            // a synthetic child, mirroring MeshNode's composite convention.
            SceneNode.Group(
                id = id,
                transform =
                    Transform(
                        position = position,
                        rotation = rotation,
                        scale = scale,
                    ),
                visible = visible,
                castsShadow = castsShadow,
                children =
                    listOf(
                        SceneNode.Model(
                            id = "$id#model",
                            transform = Transform(),
                            visible = visible,
                            glb = glb,
                            contentKey = contentKey,
                            animation = animation,
                        ),
                    ) + children.map { it.toSceneNode() },
            )
        }
    }
}
