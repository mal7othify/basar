package com.spatialkit.dsl

import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.Color
import com.spatialkit.render.AnchorType
import com.spatialkit.engine.GeometryData
import com.spatialkit.scene.CapsuleNode
import com.spatialkit.scene.CubeNode
import com.spatialkit.scene.CustomMeshNode
import com.spatialkit.scene.CylinderNode
import com.spatialkit.scene.GltfModel
import com.spatialkit.scene.GroupNode
import com.spatialkit.scene.ModelNode
import com.spatialkit.scene.PlaneNode
import com.spatialkit.scene.SpatialAnchor
import com.spatialkit.scene.SpatialNode
import com.spatialkit.scene.SpatialScene
import com.spatialkit.scene.SphereNode

/**
 * DSL scope for building spatial scenes.
 *
 * Provides Compose-like syntax for creating and configuring scene nodes.
 *
 * Usage:
 * ```kotlin
 * val scene = spatialScene {
 *     cube(size = 0.1f, color = Color.RED) {
 *         position = vec3(0f, 1.5f, -1f)
 *     }
 *
 *     sphere(radius = 0.05f) {
 *         position = vec3(0.2f, 1.5f, -1f)
 *     }
 *
 *     group {
 *         cube { position = vec3(-0.1f, 0f, 0f) }
 *         cube { position = vec3(0.1f, 0f, 0f) }
 *     }
 * }
 * ```
 */
@SpatialDsl
public class SpatialSceneScope(
    private val scene: SpatialScene,
) {
    /**
     * Creates a cube node and adds it to the scene.
     *
     * @param size Size of the cube on all axes (in meters)
     * @param color Color of the cube
     * @param id Optional unique identifier
     * @param configure Lambda to configure the cube
     * @return The created cube node
     */
    public fun cube(
        size: Float = 0.1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CubeNode.() -> Unit = {},
    ): CubeNode = CubeNode(size, color, id).apply(configure).also { scene.add(it) }

    /**
     * Creates a sphere node and adds it to the scene.
     *
     * @param radius Radius of the sphere (in meters)
     * @param color Color of the sphere
     * @param id Optional unique identifier
     * @param configure Lambda to configure the sphere
     * @return The created sphere node
     */
    public fun sphere(
        radius: Float = 0.05f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: SphereNode.() -> Unit = {},
    ): SphereNode = SphereNode(radius, color, id).apply(configure).also { scene.add(it) }

    /**
     * Creates a plane node and adds it to the scene.
     *
     * @param width Width of the plane (in meters)
     * @param height Height of the plane (in meters)
     * @param color Color of the plane
     * @param id Optional unique identifier
     * @param configure Lambda to configure the plane
     * @return The created plane node
     */
    public fun plane(
        width: Float = 1f,
        height: Float = 1f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: PlaneNode.() -> Unit = {},
    ): PlaneNode = PlaneNode(width, height, color, id).apply(configure).also { scene.add(it) }

    /**
     * Creates a cylinder node and adds it to the scene.
     *
     * @param radius Radius of the cylinder (in meters)
     * @param height Height of the cylinder (in meters)
     * @param color Color of the cylinder
     * @param id Optional unique identifier
     * @param configure Lambda to configure the cylinder
     * @return The created cylinder node
     */
    public fun cylinder(
        radius: Float = 0.05f,
        height: Float = 0.2f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CylinderNode.() -> Unit = {},
    ): CylinderNode = CylinderNode(radius, height, color, id).apply(configure).also { scene.add(it) }

    /**
     * Creates a capsule node and adds it to the scene.
     *
     * @param radius Radius of the capsule (in meters)
     * @param height Height of the cylindrical part (in meters)
     * @param color Color of the capsule
     * @param id Optional unique identifier
     * @param configure Lambda to configure the capsule
     * @return The created capsule node
     */
    public fun capsule(
        radius: Float = 0.05f,
        height: Float = 0.2f,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CapsuleNode.() -> Unit = {},
    ): CapsuleNode = CapsuleNode(radius, height, color, id).apply(configure).also { scene.add(it) }

    /**
     * Creates a custom mesh node with explicit geometry data.
     *
     * This allows creating arbitrary shapes by specifying vertices, indices, and normals.
     *
     * @param geometry The geometry data (vertices, indices, normals, uvs)
     * @param color Color of the mesh
     * @param id Optional unique identifier
     * @param configure Lambda to configure the mesh
     * @return The created custom mesh node
     */
    public fun customMesh(
        geometry: GeometryData,
        color: Color = Color.White,
        id: String = SpatialNode.generateId(),
        configure: CustomMeshNode.() -> Unit = {},
    ): CustomMeshNode = CustomMeshNode(geometry, color, id).apply(configure).also { scene.add(it) }

    /**
     * Places a loaded glTF model in the scene.
     *
     * Load the model beforehand with `loadGltf(bytes)` (suspend) — asset
     * loading does not belong in the scene block, which builds synchronously
     * and only once. The same [GltfModel] can be placed any number of times;
     * geometry and textures are shared between instances.
     *
     * ```kotlin
     * model(duck) {
     *     position = vec3(0f, 0f, -1f)
     *     scale = vec3(0.5f, 0.5f, 0.5f)
     * }
     * ```
     *
     * @param model The loaded model to instantiate
     * @param id Optional unique identifier
     * @param configure Lambda to configure the model node
     * @return The created model node
     */
    public fun model(
        model: GltfModel,
        id: String = SpatialNode.generateId(),
        configure: ModelNode.() -> Unit = {},
    ): ModelNode = ModelNode(model, id).apply(configure).also { scene.add(it) }

    /**
     * Creates a group node with nested content.
     *
     * Groups allow hierarchical organization of nodes. Child nodes are
     * positioned relative to the group's transform.
     *
     * @param id Optional unique identifier
     * @param configure Lambda to configure the group and add children
     * @return The created group node
     */
    public fun group(
        id: String = SpatialNode.generateId(),
        configure: GroupScope.() -> Unit,
    ): GroupNode {
        val group = GroupNode(id)
        GroupScope(group).configure()
        scene.add(group)
        return group
    }

    /**
     * Creates an anchor on a detected surface.
     *
     * The anchor will be created when a suitable plane is detected.
     * Content is only added to the scene when the anchor is tracking.
     *
     * @param planeId ID of the plane to anchor to (if known)
     * @param offset Offset from the plane center (default: on plane)
     * @param id Optional unique identifier for the anchor
     * @param content Lambda to create content attached to the anchor
     * @return The created spatial anchor
     */
    public fun anchorToPlane(
        planeId: String,
        offset: Vec3 = Vec3.ZERO,
        id: String = SpatialAnchor.generateId(),
        content: AnchorScope.() -> Unit,
    ): SpatialAnchor {
        val anchor =
            scene.createAnchor(
                type = AnchorType.Plane(planeId, offset),
                id = id,
            )
        val scope = AnchorScope(anchor, scene)
        scope.content()
        return anchor
    }

    /**
     * Creates an anchor at a world-space position.
     *
     * @param position Position in world space
     * @param rotation Rotation in world space (default: identity)
     * @param id Optional unique identifier for the anchor
     * @param content Lambda to create content attached to the anchor
     * @return The created spatial anchor
     */
    public fun anchorAtPosition(
        position: Vec3,
        rotation: com.spatialkit.core.math.Quaternion = com.spatialkit.core.math.Quaternion.IDENTITY,
        id: String = SpatialAnchor.generateId(),
        content: AnchorScope.() -> Unit,
    ): SpatialAnchor {
        val anchor =
            scene.createAnchor(
                type = AnchorType.World(position, rotation),
                id = id,
            )
        val scope = AnchorScope(anchor, scene)
        scope.content()
        return anchor
    }
}
