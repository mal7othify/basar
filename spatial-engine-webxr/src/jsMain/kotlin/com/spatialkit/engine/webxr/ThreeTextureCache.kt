package com.spatialkit.engine.webxr

import com.spatialkit.core.log.SpatialLog
import com.spatialkit.engine.TextureData
import com.spatialkit.engine.TextureFilter
import com.spatialkit.engine.TextureWrap
import kotlinx.browser.window
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

// Three.js sampler constants (three/src/constants.js)
private const val THREE_REPEAT_WRAPPING = 1000
private const val THREE_CLAMP_TO_EDGE_WRAPPING = 1001
private const val THREE_MIRRORED_REPEAT_WRAPPING = 1002
private const val THREE_NEAREST_FILTER = 1003
private const val THREE_LINEAR_FILTER = 1006
private const val THREE_LINEAR_MIPMAP_LINEAR_FILTER = 1008

/**
 * Decodes [TextureData] into Three.js [Texture]s and caches them by id.
 *
 * Browser image decode is asynchronous (Blob → createImageBitmap), so
 * [acquire] delivers the texture through a callback; materials render with
 * their base color until the texture arrives. Entries are reference-counted:
 * every [acquire] must be paired with a [release] so GPU memory is freed when
 * the last mesh using a texture leaves the scene.
 */
internal class ThreeTextureCache {
    private class Entry {
        var texture: Texture? = null
        var refCount = 0
        var failed = false
        val waiters = mutableListOf<(Texture) -> Unit>()
    }

    private val entries = mutableMapOf<String, Entry>()

    /**
     * Registers a user of [data]'s texture and invokes [onReady] once it is
     * decoded (immediately if already cached). If decoding fails, [onReady]
     * is never called and the material keeps its base color.
     */
    fun acquire(
        data: TextureData,
        onReady: (Texture) -> Unit,
    ) {
        val existing = entries[data.id]
        if (existing != null) {
            existing.refCount++
            val texture = existing.texture
            when {
                texture != null -> onReady(texture)
                existing.failed -> Unit
                else -> existing.waiters.add(onReady)
            }
            return
        }

        val entry = Entry()
        entry.refCount = 1
        entry.waiters.add(onReady)
        entries[data.id] = entry
        decode(data, entry)
    }

    /**
     * Drops one reference to the texture with [id], disposing it when the
     * last reference is gone.
     */
    fun release(id: String) {
        val entry = entries[id] ?: return
        entry.refCount--
        if (entry.refCount <= 0) {
            entries.remove(id)
            entry.waiters.clear()
            entry.texture?.let { disposeTexture(it) }
        }
    }

    /** Disposes every cached texture regardless of reference counts. */
    fun disposeAll() {
        entries.values.forEach { entry ->
            entry.waiters.clear()
            entry.texture?.let { disposeTexture(it) }
        }
        entries.clear()
    }

    private fun decode(
        data: TextureData,
        entry: Entry,
    ) {
        val blob =
            Blob(
                arrayOf<dynamic>(data.data),
                BlobPropertyBag(type = data.mimeType ?: "image/png"),
            )

        window
            .asDynamic()
            .createImageBitmap(blob)
            .then({ bitmap: dynamic ->
                // Everyone released while the decode was in flight, or the
                // entry was evicted; don't leak the bitmap.
                if (entries[data.id] !== entry) {
                    bitmap.close()
                    return@then
                }

                val texture = Texture(bitmap)
                // glTF UVs use a top-left image origin. Three.js flips images
                // on upload by default (bottom-left origin); keep the image
                // as-is and use the UVs directly, matching Three's GLTFLoader.
                texture.flipY = false
                // Base-color textures are authored in sRGB
                texture.colorSpace = "srgb"
                applySampler(texture, data)
                texture.needsUpdate = true

                entry.texture = texture
                entry.waiters.forEach { it(texture) }
                entry.waiters.clear()
            }, { error: dynamic ->
                SpatialLog.warn(TAG) { "texture decode failed for '${data.id}': $error" }
                entry.failed = true
                entry.waiters.clear()
            })
    }

    private fun applySampler(
        texture: Texture,
        data: TextureData,
    ) {
        texture.wrapS = data.sampler.wrapU.toThree()
        texture.wrapT = data.sampler.wrapV.toThree()
        texture.magFilter =
            when (data.sampler.magFilter) {
                TextureFilter.NEAREST -> THREE_NEAREST_FILTER
                TextureFilter.LINEAR -> THREE_LINEAR_FILTER
            }
        texture.minFilter =
            when (data.sampler.minFilter) {
                TextureFilter.NEAREST -> THREE_NEAREST_FILTER
                // Trilinear (Three's default) for quality at glancing angles
                TextureFilter.LINEAR -> THREE_LINEAR_MIPMAP_LINEAR_FILTER
            }
    }

    private fun TextureWrap.toThree(): Int =
        when (this) {
            TextureWrap.REPEAT -> THREE_REPEAT_WRAPPING
            TextureWrap.MIRRORED_REPEAT -> THREE_MIRRORED_REPEAT_WRAPPING
            TextureWrap.CLAMP_TO_EDGE -> THREE_CLAMP_TO_EDGE_WRAPPING
        }

    private fun disposeTexture(texture: Texture) {
        // Free CPU-side ImageBitmap memory in addition to the GPU texture
        val image = texture.asDynamic().image
        if (image != null && image.close != undefined) {
            image.close()
        }
        texture.dispose()
    }

    private companion object {
        const val TAG = "ThreeTextureCache"
    }
}
