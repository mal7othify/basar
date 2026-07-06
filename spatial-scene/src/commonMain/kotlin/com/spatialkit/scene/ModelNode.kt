package com.spatialkit.scene

import com.spatialkit.core.math.Transform
import com.spatialkit.engine.SceneNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A scene-graph node that renders a loaded glTF model.
 *
 * The node instantiates the model's hierarchy as its children (a tree of
 * groups and custom meshes). Mesh geometry and textures are shared with the
 * [GltfModel] and other instances of it; the node's own transform is
 * per-instance, so one model can be placed many times:
 *
 * ```kotlin
 * val duck = loadGltf(duckBytes)          // suspend; parsed once, cached
 * scene.add(ModelNode(duck).apply { position = vec3(0f, 0f, -1f) })
 * scene.add(ModelNode(duck).apply { position = vec3(0.5f, 0f, -1f) })
 * ```
 *
 * @property model The loaded model this node instantiates
 */
public class ModelNode(
    public val model: GltfModel,
    id: String = generateId(),
) : SpatialNode(id) {
    init {
        model.instantiateChildren().forEach(::addChild)
    }

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
 * Loads a glTF/GLB model off the caller's thread ([Dispatchers.Default]),
 * caching parsed models by content: loading the same bytes again returns the
 * already-parsed [GltfModel], so placing N copies of one asset parses once.
 *
 * Sourcing the bytes is platform-specific and up to the caller — e.g. Compose
 * resources (`Res.readBytes("files/model.glb")`), an HTTP fetch, or platform
 * file APIs. Typical Compose usage:
 *
 * ```kotlin
 * var duck by remember { mutableStateOf<GltfModel?>(null) }
 * LaunchedEffect(Unit) { duck = loadGltf(Res.readBytes("files/duck.glb")) }
 *
 * duck?.let { model ->
 *     SpatialView { model(model) { position = vec3(0f, 0f, -1f) } }
 * }
 * ```
 *
 * @throws GltfParseException if the asset is malformed or uses an unsupported
 *   glTF feature
 */
public suspend fun loadGltf(bytes: ByteArray): GltfModel = GltfModelCache.load(bytes)

/**
 * Content-keyed cache of parsed models. Bounded so a long-lived session that
 * loads many distinct assets doesn't grow without limit (entries are only
 * parse results; evicted models keep working, they just re-parse next time).
 */
internal object GltfModelCache {
    private const val MAX_ENTRIES = 8

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, GltfModel>()

    suspend fun load(bytes: ByteArray): GltfModel {
        val key = contentKey(bytes)
        mutex.withLock {
            // Re-insert on hit: insertion order then approximates LRU
            cache.remove(key)?.let { hit ->
                cache[key] = hit
                return hit
            }
        }

        val model = withContext(Dispatchers.Default) { GltfLoader.load(bytes) }

        mutex.withLock {
            // A concurrent load of the same bytes may have won; keep one copy
            cache[key]?.let { return it }
            if (cache.size >= MAX_ENTRIES) {
                cache.remove(cache.keys.first())
            }
            cache[key] = model
        }
        return model
    }

    // FNV-1a 64-bit over the asset bytes; size prefix cheaply disambiguates
    private fun contentKey(bytes: ByteArray): String {
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325, FNV offset basis
        for (b in bytes) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= 0x100000001b3L // FNV prime
        }
        return "${bytes.size}:${hash.toULong().toString(16)}"
    }
}
