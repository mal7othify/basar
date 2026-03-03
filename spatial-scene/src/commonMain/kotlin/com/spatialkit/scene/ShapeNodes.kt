package com.spatialkit.scene

import com.spatialkit.core.math.Transform
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.Color
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.MaterialData
import com.spatialkit.engine.MaterialType
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.Vec4

/**
 * A cubic mesh node.
 *
 * @param size Size of the cube on all axes (default 0.1m = 10cm)
 * @param color Color of the cube
 */
class CubeNode(
    var size: Float = 0.1f,
    var color: Color = Color.White,
    id: String = generateId(),
) : SpatialNode(id) {
    /**
     * Material type for rendering.
     * Defaults to PBR for realistic lighting.
     */
    var materialType: MaterialType = MaterialType.PBR

    /**
     * Metallic factor (0 = dielectric, 1 = metal).
     * Only used with PBR material.
     */
    var metallic: Float = 0f

    /**
     * Roughness factor (0 = smooth, 1 = rough).
     * Only used with PBR material.
     */
    var roughness: Float = 0.5f

    override fun toSceneNode(): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform =
                Transform(
                    position = position,
                    rotation = rotation,
                    scale = Vec3(size, size, size) * this.scale,
                ),
            visible = visible,
            geometry = GeometryData(type = GeometryType.CUBE),
            material =
                MaterialData(
                    type = materialType,
                    baseColor = color.toVec4(),
                    metallic = metallic,
                    roughness = roughness,
                ),
        )
}

/**
 * A spherical mesh node.
 *
 * @param radius Radius of the sphere (default 0.05m = 5cm)
 * @param color Color of the sphere
 */
class SphereNode(
    var radius: Float = 0.05f,
    var color: Color = Color.White,
    id: String = generateId(),
) : SpatialNode(id) {
    var materialType: MaterialType = MaterialType.PBR
    var metallic: Float = 0f
    var roughness: Float = 0.5f

    override fun toSceneNode(): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform =
                Transform(
                    position = position,
                    rotation = rotation,
                    scale = Vec3(radius * 2, radius * 2, radius * 2) * this.scale,
                ),
            visible = visible,
            geometry = GeometryData(type = GeometryType.SPHERE),
            material =
                MaterialData(
                    type = materialType,
                    baseColor = color.toVec4(),
                    metallic = metallic,
                    roughness = roughness,
                ),
        )
}

/**
 * A planar mesh node.
 *
 * @param width Width along X axis (default 0.2m = 20cm)
 * @param height Height along Z axis (default 0.2m = 20cm)
 * @param color Color of the plane
 */
class PlaneNode(
    var width: Float = 0.2f,
    var height: Float = 0.2f,
    var color: Color = Color.White,
    id: String = generateId(),
) : SpatialNode(id) {
    var materialType: MaterialType = MaterialType.LIT
    var metallic: Float = 0f
    var roughness: Float = 0.5f

    /**
     * Whether the plane is double-sided.
     * If false, back-face culling applies.
     */
    var doubleSided: Boolean = true

    override fun toSceneNode(): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform =
                Transform(
                    position = position,
                    rotation = rotation,
                    scale = Vec3(width, 1f, height) * this.scale,
                ),
            visible = visible,
            geometry = GeometryData(type = GeometryType.PLANE),
            material =
                MaterialData(
                    type = materialType,
                    baseColor = color.toVec4(),
                    metallic = metallic,
                    roughness = roughness,
                ),
        )
}

/**
 * A cylindrical mesh node.
 *
 * @param radius Radius of the cylinder (default 0.05m = 5cm)
 * @param height Height of the cylinder (default 0.2m = 20cm)
 * @param color Color of the cylinder
 */
class CylinderNode(
    var radius: Float = 0.05f,
    var height: Float = 0.2f,
    var color: Color = Color.White,
    id: String = generateId(),
) : SpatialNode(id) {
    var materialType: MaterialType = MaterialType.PBR
    var metallic: Float = 0f
    var roughness: Float = 0.5f

    override fun toSceneNode(): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform =
                Transform(
                    position = position,
                    rotation = rotation,
                    scale = Vec3(radius * 2, height, radius * 2) * this.scale,
                ),
            visible = visible,
            geometry = GeometryData(type = GeometryType.CYLINDER),
            material =
                MaterialData(
                    type = materialType,
                    baseColor = color.toVec4(),
                    metallic = metallic,
                    roughness = roughness,
                ),
        )
}

/**
 * A capsule mesh node (cylinder with hemispherical ends).
 *
 * @param radius Radius of the capsule (default 0.05m = 5cm)
 * @param height Total height including hemispheres (default 0.2m = 20cm)
 * @param color Color of the capsule
 */
class CapsuleNode(
    var radius: Float = 0.05f,
    var height: Float = 0.2f,
    var color: Color = Color.White,
    id: String = generateId(),
) : SpatialNode(id) {
    var materialType: MaterialType = MaterialType.PBR
    var metallic: Float = 0f
    var roughness: Float = 0.5f

    override fun toSceneNode(): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform =
                Transform(
                    position = position,
                    rotation = rotation,
                    scale = Vec3(radius * 2, height, radius * 2) * this.scale,
                ),
            visible = visible,
            geometry = GeometryData(type = GeometryType.CAPSULE),
            material =
                MaterialData(
                    type = materialType,
                    baseColor = color.toVec4(),
                    metallic = metallic,
                    roughness = roughness,
                ),
        )
}

/**
 * A group node that contains other nodes.
 *
 * Groups are useful for:
 * - Organizing scene hierarchy
 * - Applying transforms to multiple nodes
 * - Toggling visibility of multiple nodes
 */
class GroupNode(
    id: String = generateId(),
) : SpatialNode(id) {
    override fun toSceneNode(): SceneNode.Group =
        SceneNode.Group(
            id = id,
            transform =
                Transform(
                    position = position,
                    rotation = rotation,
                    scale = scale,
                ),
            visible = visible,
            children = children.map { it.toSceneNode() },
        )
}

/**
 * A custom mesh node with explicit geometry data.
 *
 * @param geometry Custom geometry data
 * @param color Color of the mesh
 */
class CustomMeshNode(
    var geometry: GeometryData,
    var color: Color = Color.White,
    id: String = generateId(),
) : SpatialNode(id) {
    var materialType: MaterialType = MaterialType.PBR
    var metallic: Float = 0f
    var roughness: Float = 0.5f

    override fun toSceneNode(): SceneNode.Mesh =
        SceneNode.Mesh(
            id = id,
            transform =
                Transform(
                    position = position,
                    rotation = rotation,
                    scale = scale,
                ),
            visible = visible,
            geometry = geometry,
            material =
                MaterialData(
                    type = materialType,
                    baseColor = color.toVec4(),
                    metallic = metallic,
                    roughness = roughness,
                ),
        )
}

/**
 * Extension function to convert Color to Vec4.
 * Color values are already in 0-1 range, so no conversion needed.
 */
private fun Color.toVec4(): Vec4 =
    Vec4(
        this.r,
        this.g,
        this.b,
        this.a,
    )

/**
 * Operator to multiply Vec3 component-wise.
 */
private operator fun Vec3.times(other: Vec3): Vec3 =
    Vec3(
        this.x * other.x,
        this.y * other.y,
        this.z * other.z,
    )
