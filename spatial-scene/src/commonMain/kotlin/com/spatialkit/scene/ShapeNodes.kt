package com.spatialkit.scene

import com.spatialkit.core.math.Transform
import com.spatialkit.core.math.Vec3
import com.spatialkit.core.types.Color
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.MaterialData
import com.spatialkit.engine.MaterialType
import com.spatialkit.engine.TextureData
import com.spatialkit.engine.Vec4
import com.spatialkit.render.SceneNode

/**
 * Base class for nodes that render a mesh.
 *
 * Subclasses provide the geometry ([buildGeometry]) and the geometry's
 * intrinsic size ([geometrySize], e.g. a cube's edge length), while MeshNode
 * owns the shared material properties and the conversion to engine format.
 *
 * A mesh node may have children ([addChild]); they inherit the node's
 * position/rotation/scale (but not its intrinsic geometry size).
 */
public abstract class MeshNode(
    id: String,
) : SpatialNode(id) {
    /**
     * Color of the mesh.
     */
    public var color: Color = Color.White

    /**
     * Material type for rendering.
     * Defaults to PBR for realistic lighting.
     */
    public var materialType: MaterialType = MaterialType.PBR

    /**
     * Metallic factor (0 = dielectric, 1 = metal).
     * Only used with PBR material.
     */
    public var metallic: Float = 0f

    /**
     * Roughness factor (0 = smooth, 1 = rough).
     * Only used with PBR material.
     */
    public var roughness: Float = 0.5f

    /**
     * Whether the mesh is rendered double-sided (no back-face culling).
     */
    public var doubleSided: Boolean = false

    /**
     * Optional base-color texture, sampled with the geometry's UVs and
     * multiplied with [color].
     */
    public var baseColorTexture: TextureData? = null

    /**
     * The intrinsic size of the geometry along each axis, applied on top of
     * [scale] when rendering this node's mesh (but not inherited by children).
     */
    internal abstract val geometrySize: Vec3

    /**
     * The geometry to render.
     */
    internal abstract fun buildGeometry(): GeometryData

    final override fun toSceneNode(): SceneNode {
        val material =
            MaterialData(
                type = materialType,
                baseColor = color.toVec4(),
                metallic = metallic,
                roughness = roughness,
                baseColorTexture = baseColorTexture,
                doubleSided = doubleSided,
            )
        val size = geometrySize
        return if (children.isEmpty()) {
            SceneNode.Mesh(
                id = id,
                transform =
                    Transform(
                        position = position,
                        rotation = rotation,
                        scale = Vec3(size.x * scale.x, size.y * scale.y, size.z * scale.z),
                    ),
                visible = visible,
                geometry = buildGeometry(),
                material = material,
                castsShadow = castsShadow,
            )
        } else {
            // A mesh with children renders as a group carrying the node's
            // transform, containing the mesh itself (with only the intrinsic
            // geometry size applied) plus the converted children.
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
                    buildList {
                        add(
                            SceneNode.Mesh(
                                id = "$id#mesh",
                                transform = Transform(scale = size),
                                visible = visible,
                                geometry = buildGeometry(),
                                material = material,
                            ),
                        )
                        children.forEach { add(it.toSceneNode()) }
                    },
            )
        }
    }
}

/**
 * A cubic mesh node.
 *
 * @param size Size of the cube on all axes (default 0.1m = 10cm)
 * @param color Color of the cube
 */
public class CubeNode(
    public var size: Float = 0.1f,
    color: Color = Color.White,
    id: String = generateId(),
) : MeshNode(id) {
    init {
        this.color = color
    }

    override val geometrySize: Vec3 get() = Vec3(size, size, size)

    override fun buildGeometry(): GeometryData = GeometryData(type = GeometryType.CUBE)
}

/**
 * A spherical mesh node.
 *
 * @param radius Radius of the sphere (default 0.05m = 5cm)
 * @param color Color of the sphere
 */
public class SphereNode(
    public var radius: Float = 0.05f,
    color: Color = Color.White,
    id: String = generateId(),
) : MeshNode(id) {
    init {
        this.color = color
    }

    override val geometrySize: Vec3 get() = Vec3(radius * 2, radius * 2, radius * 2)

    override fun buildGeometry(): GeometryData = GeometryData(type = GeometryType.SPHERE)
}

/**
 * A planar mesh node.
 *
 * Renders double-sided by default.
 *
 * @param width Width along X axis (default 0.2m = 20cm)
 * @param height Height along Z axis (default 0.2m = 20cm)
 * @param color Color of the plane
 */
public class PlaneNode(
    public var width: Float = 0.2f,
    public var height: Float = 0.2f,
    color: Color = Color.White,
    id: String = generateId(),
) : MeshNode(id) {
    init {
        this.color = color
        this.materialType = MaterialType.LIT
        this.doubleSided = true
    }

    override val geometrySize: Vec3 get() = Vec3(width, 1f, height)

    override fun buildGeometry(): GeometryData = GeometryData(type = GeometryType.PLANE)
}

/**
 * A cylindrical mesh node.
 *
 * @param radius Radius of the cylinder (default 0.05m = 5cm)
 * @param height Height of the cylinder (default 0.2m = 20cm)
 * @param color Color of the cylinder
 */
public class CylinderNode(
    public var radius: Float = 0.05f,
    public var height: Float = 0.2f,
    color: Color = Color.White,
    id: String = generateId(),
) : MeshNode(id) {
    init {
        this.color = color
    }

    override val geometrySize: Vec3 get() = Vec3(radius * 2, height, radius * 2)

    override fun buildGeometry(): GeometryData = GeometryData(type = GeometryType.CYLINDER)
}

/**
 * A capsule mesh node (cylinder with hemispherical ends).
 *
 * @param radius Radius of the capsule (default 0.05m = 5cm)
 * @param height Total height including hemispheres (default 0.2m = 20cm)
 * @param color Color of the capsule
 */
public class CapsuleNode(
    public var radius: Float = 0.05f,
    public var height: Float = 0.2f,
    color: Color = Color.White,
    id: String = generateId(),
) : MeshNode(id) {
    init {
        this.color = color
    }

    override val geometrySize: Vec3 get() = Vec3(radius * 2, height, radius * 2)

    override fun buildGeometry(): GeometryData = GeometryData(type = GeometryType.CAPSULE)
}

/**
 * A custom mesh node with explicit geometry data.
 *
 * @param geometry Custom geometry data
 * @param color Color of the mesh
 */
public class CustomMeshNode(
    public var geometry: GeometryData,
    color: Color = Color.White,
    id: String = generateId(),
) : MeshNode(id) {
    init {
        this.color = color
    }

    override val geometrySize: Vec3 get() = Vec3.ONE

    override fun buildGeometry(): GeometryData = geometry
}

/**
 * A group node that contains other nodes.
 *
 * Groups are useful for:
 * - Organizing scene hierarchy
 * - Applying transforms to multiple nodes
 * - Toggling visibility of multiple nodes
 */
public class GroupNode(
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
            castsShadow = castsShadow,
            children = children.map { it.toSceneNode() },
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
