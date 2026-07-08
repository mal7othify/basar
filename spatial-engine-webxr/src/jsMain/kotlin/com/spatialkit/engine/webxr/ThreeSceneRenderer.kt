package com.spatialkit.engine.webxr

import com.spatialkit.engine.Aabb
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import com.spatialkit.engine.HitTestResult
import com.spatialkit.engine.MaterialData
import com.spatialkit.engine.MaterialType
import com.spatialkit.engine.RaycastResult
import com.spatialkit.engine.SceneBounds
import com.spatialkit.engine.SceneData
import com.spatialkit.engine.SceneNode
import com.spatialkit.engine.Transform
import com.spatialkit.engine.Vec3
import kotlinx.browser.document
import org.w3c.dom.HTMLCanvasElement

/**
 * Synchronizes the engine-contract scene ([SceneData]) into a Three.js
 * [Scene] and answers raycast queries against it.
 *
 * This is the rendering core shared by [WebXREngine] (immersive XR sessions)
 * and the non-XR preview engine (`spatial-engine-preview`): node lifecycle
 * with GPU resource disposal, geometry/material creation (including textured
 * custom meshes), M9.2 blob shadows, and content raycasting. The owner is
 * responsible for the camera, the [WebGLRenderer], and calling
 * `renderer.render(sceneRenderer.scene, camera)` each frame.
 */
public class ThreeSceneRenderer {
    /**
     * Baseline scene lights, exposed so the owner can drive them from a
     * light estimate (M9.1). Intensities are 0.6 ambient / 0.8 directional
     * for a nominally lit scene.
     */
    public val ambientLight: AmbientLight = AmbientLight(0xffffff, 0.6)
    public val directionalLight: DirectionalLight =
        DirectionalLight(0xffffff, 0.8).apply {
            position.set(1.0, 1.0, 1.0)
        }

    /** The Three.js scene all content is synced into. */
    public val scene: Scene =
        Scene().apply {
            add(ambientLight)
            add(directionalLight)
        }

    // Scene management
    private val nodeMap = mutableMapOf<String, Object3D>()
    private val appliedMaterials = mutableMapOf<String, MaterialData>()
    private val appliedGeometry = mutableMapOf<String, GeometryData>()
    private val textureCache = ThreeTextureCache()

    // M9.2 blob shadows: one radial-gradient plane per shadow-casting subtree
    private val shadowMeshMap = mutableMapOf<String, Mesh>()
    private var blobShadowTexture: Texture? = null

    // Cached local AABBs for custom meshes (submitScene runs per frame)
    private val customMeshLocalBounds = mutableMapOf<String, Aabb?>()

    /**
     * Syncs [sceneData] into the Three.js scene: removes vanished nodes
     * (freeing their GPU resources), adds/updates the rest, and refreshes
     * blob shadows. Safe to call every frame.
     */
    public fun submitScene(sceneData: SceneData) {
        // Remove old nodes that aren't in new scene and free their GPU resources
        val newNodeIds = collectNodeIds(sceneData.nodes)
        val toRemove = nodeMap.keys.filter { it !in newNodeIds }
        toRemove.forEach { id ->
            nodeMap[id]?.let { obj ->
                obj.asDynamic().parent?.remove(obj)
                scene.remove(obj)
                disposeObject(obj)
            }
            nodeMap.remove(id)
            appliedMaterials.remove(id)?.baseColorTexture?.let { textureCache.release(it.id) }
            appliedGeometry.remove(id)
            customMeshLocalBounds.remove(id)
        }

        // Add or update nodes
        sceneData.nodes.forEach { node ->
            processSceneNodeIntoScene(node, scene)
        }

        updateBlobShadows(sceneData)
    }

    /**
     * Casts a camera ray through the normalized screen point (0..1, top-left
     * origin) against scene content. Real-world geometry is the owner's
     * concern; this only sees submitted nodes.
     */
    public fun performHitTest(
        screenX: Float,
        screenY: Float,
        camera: Camera,
    ): HitTestResult? {
        // Normalized (0..1, top-left origin) -> NDC (-1..1, bottom-left origin)
        val ndc = Vector2((screenX * 2f - 1f).toDouble(), (1f - screenY * 2f).toDouble())
        val raycaster = Raycaster()
        raycaster.setFromCamera(ndc, camera)

        val hit = raycaster.intersectObjects(scene.children, recursive = true).firstOrNull() ?: return null
        return HitTestResult(
            position = Vec3(hit.point.x.toFloat(), hit.point.y.toFloat(), hit.point.z.toFloat()),
            normal = hit.worldNormal(),
            distance = hit.distance.toFloat(),
            planeId = null,
        )
    }

    /** Tests a world-space ray against scene content. */
    public fun performRaycast(
        origin: Vec3,
        direction: Vec3,
    ): RaycastResult? {
        val raycaster =
            Raycaster(
                Vector3(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble()),
                Vector3(direction.x.toDouble(), direction.y.toDouble(), direction.z.toDouble()),
            )

        val intersections = raycaster.intersectObjects(scene.children, recursive = true)

        return intersections.firstOrNull()?.let { hit ->
            RaycastResult(
                position = Vec3(hit.point.x.toFloat(), hit.point.y.toFloat(), hit.point.z.toFloat()),
                normal = hit.worldNormal(),
                distance = hit.distance.toFloat(),
                nodeId = hit.`object`.name.takeIf { it.isNotEmpty() },
            )
        }
    }

    /**
     * Releases all GPU resources (geometries, materials, textures, shadow
     * meshes). The renderer instance is dead afterwards — create a new one
     * to render again.
     */
    public fun dispose() {
        nodeMap.values.forEach { disposeObject(it) }
        nodeMap.clear()
        appliedMaterials.clear()
        appliedGeometry.clear()
        textureCache.disposeAll()
        shadowMeshMap.values.forEach { disposeObject(it) }
        shadowMeshMap.clear()
        blobShadowTexture?.dispose()
        blobShadowTexture = null
        customMeshLocalBounds.clear()
    }

    /**
     * Maintains one blob-shadow plane (M9.2) under each shadow-casting
     * subtree: a radial-gradient disc at the base of the subtree's world
     * AABB, sized slightly beyond its footprint. Runs per submitScene (once
     * per frame), so shadows follow dragged/scaled content.
     */
    private fun updateBlobShadows(sceneData: SceneData) {
        val casters =
            SceneBounds.shadowCasters(sceneData.nodes) { mesh ->
                if (mesh.geometry.type == GeometryType.CUSTOM_MESH) {
                    customMeshLocalBounds.getOrPut(mesh.id) {
                        SceneBounds.localBounds(mesh.geometry)
                    }
                } else {
                    SceneBounds.localBounds(mesh.geometry)
                }
            }

        val liveIds = mutableSetOf<String>()
        casters.forEach { (node, bounds) ->
            val size = bounds.size
            val diameter = maxOf(size.x, size.z) * SHADOW_FOOTPRINT_SCALE
            if (diameter <= 0f) return@forEach
            liveIds.add(node.id)

            val shadow =
                shadowMeshMap.getOrPut(node.id) {
                    createBlobShadowMesh().also { scene.add(it) }
                }
            shadow.position.set(
                bounds.center.x.toDouble(),
                // Slightly above the base to avoid z-fighting the surface
                (bounds.min.y + 0.002f).toDouble(),
                bounds.center.z.toDouble(),
            )
            // Plane local XY spans world XZ after the flat rotation
            shadow.scale.set(diameter.toDouble(), diameter.toDouble(), 1.0)
        }

        (shadowMeshMap.keys - liveIds).forEach { id ->
            shadowMeshMap.remove(id)?.let { mesh ->
                scene.remove(mesh)
                mesh.geometry.dispose()
                mesh.material.dispose()
            }
        }
    }

    /**
     * A 1×1 plane rotated flat with a radial-gradient "blob" texture:
     * transparent, non-depth-writing, and excluded from raycasts so it never
     * intercepts node picking.
     */
    private fun createBlobShadowMesh(): Mesh {
        val texture =
            blobShadowTexture ?: createBlobShadowTexture().also { blobShadowTexture = it }

        val params = js("{}")
        params.map = texture
        params.transparent = true
        params.depthWrite = false
        val material = MeshBasicMaterial(params)

        val mesh = Mesh(PlaneGeometry(1.0, 1.0), material)
        // Three.js planes face +Z; rotate to face +Y (flat on the ground)
        mesh.rotation.x = -kotlin.math.PI / 2
        // Opt out of raycasting (performRaycast/performHitTest walk the scene)
        mesh.asDynamic().raycast = js("function() {}")
        return mesh
    }

    /** Radial gradient matching the other renderers' blob shadows. */
    private fun createBlobShadowTexture(): Texture {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 128
        canvas.height = 128
        val ctx = canvas.asDynamic().getContext("2d")
        val gradient = ctx.createRadialGradient(64.0, 64.0, 0.0, 64.0, 64.0, 64.0)
        gradient.addColorStop(0.0, "rgba(0,0,0,$SHADOW_OPACITY)")
        gradient.addColorStop(0.55, "rgba(0,0,0,${SHADOW_OPACITY * 0.75})")
        gradient.addColorStop(1.0, "rgba(0,0,0,0)")
        ctx.fillStyle = gradient
        ctx.fillRect(0, 0, 128, 128)

        return Texture(canvas).apply { needsUpdate = true }
    }

    private fun processSceneNodeIntoScene(
        node: SceneNode,
        scene: Scene,
    ) {
        processSceneNode(node) { obj ->
            if (obj.asDynamic().parent == null) {
                scene.add(obj)
            }
        }
    }

    /**
     * Extracts the intersected face normal in world space, falling back to +Y.
     */
    private fun Intersection.worldNormal(): Vec3 {
        val face = this.face ?: return Vec3(0f, 1f, 0f)
        val normal =
            Vector3(
                (face.normal.x as Number).toDouble(),
                (face.normal.y as Number).toDouble(),
                (face.normal.z as Number).toDouble(),
            )
        // Face normals are in object space; rotate into world space
        val worldQuaternion = Quaternion()
        this.`object`.asDynamic().getWorldQuaternion(worldQuaternion)
        normal.asDynamic().applyQuaternion(worldQuaternion)
        return Vec3(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    private fun processSceneNode(
        node: SceneNode,
        attach: (Object3D) -> Unit,
    ) {
        when (node) {
            is SceneNode.Mesh -> {
                val mesh =
                    nodeMap.getOrPut(node.id) {
                        createMesh(node)
                    } as Mesh
                updateMeshData(node, mesh)
                updateTransform(mesh, node.transform)
                mesh.visible = node.visible
                attach(mesh)
            }

            is SceneNode.Group -> {
                val group =
                    nodeMap.getOrPut(node.id) {
                        Group().also { it.name = node.id }
                    }
                updateTransform(group, node.transform)
                group.visible = node.visible
                attach(group)

                node.children.forEach { child ->
                    processSceneNode(child) { obj ->
                        if (obj.asDynamic().parent == null) {
                            group.add(obj)
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies material/geometry changes to an already-created mesh, disposing
     * the replaced GPU resources.
     */
    private fun updateMeshData(
        node: SceneNode.Mesh,
        mesh: Mesh,
    ) {
        val previousMaterial = appliedMaterials[node.id]
        if (previousMaterial != node.material) {
            if (previousMaterial != null) {
                mesh.material.dispose()
                previousMaterial.baseColorTexture?.let { textureCache.release(it.id) }
            }
            mesh.material = createMaterial(node.material)
            appliedMaterials[node.id] = node.material
        }

        val previousGeometry = appliedGeometry[node.id]
        if (previousGeometry != node.geometry) {
            if (previousGeometry != null) {
                mesh.geometry.dispose()
            }
            mesh.geometry = createGeometry(node.geometry)
            appliedGeometry[node.id] = node.geometry
        }
    }

    /**
     * Releases GPU resources of a cached object (geometry + material of meshes).
     */
    private fun disposeObject(obj: Object3D) {
        if (obj is Mesh) {
            obj.geometry.dispose()
            obj.material.dispose()
        }
    }

    private fun createMesh(node: SceneNode.Mesh): Mesh {
        val geometry = createGeometry(node.geometry)
        val material = createMaterial(node.material)
        appliedMaterials[node.id] = node.material
        appliedGeometry[node.id] = node.geometry
        return Mesh(geometry, material).also { it.name = node.id }
    }

    private fun createGeometry(data: GeometryData): BufferGeometry =
        when (data.type) {
            GeometryType.CUBE -> {
                BoxGeometry(1.0, 1.0, 1.0)
            }

            GeometryType.SPHERE -> {
                SphereGeometry(0.5, 32, 16)
            }

            GeometryType.PLANE -> {
                PlaneGeometry(1.0, 1.0)
            }

            GeometryType.CYLINDER -> {
                CylinderGeometry(0.5, 0.5, 1.0, 32)
            }

            GeometryType.CAPSULE -> {
                CapsuleGeometry(0.5, 1.0, 4, 8)
            }

            GeometryType.CUSTOM_MESH -> {
                // A Kotlin FloatArray IS a Float32Array at runtime in
                // Kotlin/JS, so the buffers pass to Three.js directly. (An
                // element-copy loop through the external `set` operator would
                // compile to TypedArray.prototype.set(value, offset) calls,
                // which throw RangeError for negative vertex coordinates.)
                val geometry = BufferGeometry()
                data.vertices?.let { vertices ->
                    geometry.setAttribute("position", BufferAttribute(vertices, 3))
                }
                data.normals?.let { normals ->
                    geometry.setAttribute("normal", BufferAttribute(normals, 3))
                }
                data.uvs?.let { uvs ->
                    geometry.setAttribute("uv", BufferAttribute(uvs, 2))
                }
                data.indices?.let { indices ->
                    // Uint16 only addresses 65535 vertices; switch to Uint32
                    // beyond that. IntArray is an Int32Array at runtime, so
                    // the typed-array constructors copy it element-wise.
                    val needsWideIndices = indices.any { it > 0xFFFF }
                    val indicesArray =
                        if (needsWideIndices) {
                            js("new Uint32Array(indices)")
                        } else {
                            js("new Uint16Array(indices)")
                        }
                    geometry.setIndex(BufferAttribute(indicesArray, 1))
                }
                geometry
            }
        }

    /**
     * Creates a Three.js material from MaterialData.
     *
     * Three.js expects color as a hex integer (e.g. 0xFF0000 for red),
     * not as an {r, g, b} object. We convert the float RGB components (0.0-1.0)
     * to a single packed integer. Three multiplies `map` by `color`, matching
     * glTF's baseColorFactor × baseColorTexture semantics.
     *
     * Texture decode is asynchronous: the material starts with its base color
     * and gets `map` assigned when the cache delivers the texture. The matching
     * release happens where the applied material is replaced or removed.
     */
    private fun createMaterial(data: MaterialData): Material {
        val r = (data.baseColor.x * 255).toInt().coerceIn(0, 255)
        val g = (data.baseColor.y * 255).toInt().coerceIn(0, 255)
        val b = (data.baseColor.z * 255).toInt().coerceIn(0, 255)
        val colorHex = (r shl 16) or (g shl 8) or b

        val material =
            when (data.type) {
                MaterialType.UNLIT -> {
                    val params = js("{}")
                    params.color = colorHex
                    if (data.doubleSided) params.side = 2 // THREE.DoubleSide
                    MeshBasicMaterial(params)
                }

                MaterialType.LIT, MaterialType.PBR -> {
                    val params = js("{}")
                    params.color = colorHex
                    params.metalness = data.metallic.toDouble()
                    params.roughness = data.roughness.toDouble()
                    if (data.doubleSided) params.side = 2 // THREE.DoubleSide
                    MeshStandardMaterial(params)
                }
            }

        data.baseColorTexture?.let { textureData ->
            textureCache.acquire(textureData) { texture ->
                material.asDynamic().map = texture
                material.asDynamic().needsUpdate = true
            }
        }

        return material
    }

    private fun updateTransform(
        obj: Object3D,
        transform: Transform,
    ) {
        obj.position.set(
            transform.position.x.toDouble(),
            transform.position.y.toDouble(),
            transform.position.z.toDouble(),
        )
        obj.quaternion.set(
            transform.rotation.x.toDouble(),
            transform.rotation.y.toDouble(),
            transform.rotation.z.toDouble(),
            transform.rotation.w.toDouble(),
        )
        obj.scale.set(
            transform.scale.x.toDouble(),
            transform.scale.y.toDouble(),
            transform.scale.z.toDouble(),
        )
    }

    private fun collectNodeIds(nodes: List<SceneNode>): Set<String> =
        buildSet {
            nodes.forEach { node ->
                add(node.id)
                if (node is SceneNode.Group) {
                    addAll(collectNodeIds(node.children))
                }
            }
        }

    private companion object {
        // Blob shadows (M9.2): quad size relative to the caster's footprint,
        // and peak darkness at the blob center — matches the other renderers
        const val SHADOW_FOOTPRINT_SCALE = 1.25f
        const val SHADOW_OPACITY = 0.35
    }
}
