package com.spatialkit.scene

import com.spatialkit.core.annotations.ExperimentalSpatialApi
import com.spatialkit.core.types.Color
import com.spatialkit.engine.GeometryData
import com.spatialkit.engine.GeometryType
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Simple GLTF loader for parsing embedded GLTF JSON data.
 *
 * This is a minimal implementation that supports:
 * - Embedded base64 buffers (data URIs)
 * - Basic mesh primitives (POSITION, NORMAL, indices)
 * - Single mesh extraction
 *
 * Known limitations (this loader scans the JSON textually rather than parsing
 * it): accessor `byteOffset` and bufferView `byteStride` are ignored, POSITION
 * and NORMAL accessors are assumed to be tightly packed VEC3 floats, and only
 * the first mesh primitive is read. Files produced by exporters that use
 * interleaved or offset buffer layouts will load incorrectly.
 *
 * For production assets, use a full glTF library and feed the result into
 * [CustomMeshNode] via [GeometryData].
 *
 * Usage:
 * ```kotlin
 * val gltfJson = """{ "asset": { "version": "2.0" }, ... }"""
 * val loader = GltfLoader(gltfJson)
 * val meshNode = loader.loadFirstMesh()
 * ```
 */
@ExperimentalSpatialApi
public class GltfLoader(private val json: String) {

    private val buffers = mutableMapOf<Int, ByteArray>()

    /**
     * Loads the first mesh found in the GLTF as a CustomMeshNode.
     *
     * @param color Color to apply to the mesh
     * @return CustomMeshNode or null if no mesh found
     */
    public fun loadFirstMesh(color: Color = Color.White): CustomMeshNode? {
        val geometry = parseGeometry() ?: return null
        return CustomMeshNode(geometry, color)
    }

    /**
     * Parses geometry data from the GLTF.
     */
    public fun parseGeometry(): GeometryData? {
        // Parse buffers first
        parseBuffers()

        // Find mesh primitive
        val meshStart = json.indexOf("\"meshes\"")
        if (meshStart == -1) return null

        // Find primitives
        val primitivesStart = json.indexOf("\"primitives\"", meshStart)
        if (primitivesStart == -1) return null

        // Find attributes
        val attributesStart = json.indexOf("\"attributes\"", primitivesStart)
        if (attributesStart == -1) return null

        // Parse accessor indices
        val positionAccessor = findAttributeAccessor("POSITION", attributesStart)
        val normalAccessor = findAttributeAccessor("NORMAL", attributesStart)
        val indicesAccessor = findIndicesAccessor(primitivesStart)

        // Extract data from accessors
        val vertices = positionAccessor?.let { extractFloatData(it) }
        val normals = normalAccessor?.let { extractFloatData(it) }
        val indices = indicesAccessor?.let { extractIntData(it) }

        if (vertices == null) return null

        return GeometryData(
            type = GeometryType.CUSTOM_MESH,
            vertices = vertices,
            normals = normals,
            indices = indices
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun parseBuffers() {
        val buffersStart = json.indexOf("\"buffers\"")
        if (buffersStart == -1) return

        var index = 0
        var searchStart = buffersStart

        while (true) {
            val uriStart = json.indexOf("\"uri\"", searchStart)
            if (uriStart == -1) break

            val dataPrefix = "data:application/octet-stream;base64,"
            val base64Start = json.indexOf(dataPrefix, uriStart)
            if (base64Start == -1) break

            val base64DataStart = base64Start + dataPrefix.length
            val base64End = json.indexOf("\"", base64DataStart)
            if (base64End == -1) break

            val base64Data = json.substring(base64DataStart, base64End)
            buffers[index] = Base64.decode(base64Data)

            index++
            searchStart = base64End
        }
    }

    private fun findAttributeAccessor(name: String, startIndex: Int): Int? {
        val pattern = "\"$name\""
        val attrStart = json.indexOf(pattern, startIndex)
        if (attrStart == -1) return null

        val colonPos = json.indexOf(":", attrStart + pattern.length)
        if (colonPos == -1) return null

        return parseIntAfter(colonPos)
    }

    private fun findIndicesAccessor(startIndex: Int): Int? {
        val pattern = "\"indices\""
        val indicesStart = json.indexOf(pattern, startIndex)
        if (indicesStart == -1) return null

        val colonPos = json.indexOf(":", indicesStart + pattern.length)
        if (colonPos == -1) return null

        return parseIntAfter(colonPos)
    }

    private fun parseIntAfter(colonPos: Int): Int? {
        var i = colonPos + 1
        while (i < json.length && json[i].isWhitespace()) i++

        val numStart = i
        while (i < json.length && json[i].isDigit()) i++

        if (i == numStart) return null
        return json.substring(numStart, i).toIntOrNull()
    }

    private fun extractFloatData(accessorIndex: Int): FloatArray? {
        val accessor = findAccessor(accessorIndex) ?: return null
        val bufferView = findBufferView(accessor.bufferView) ?: return null
        val buffer = buffers[bufferView.buffer] ?: return null

        val floats = FloatArray(accessor.count * 3) // Assuming VEC3
        val offset = bufferView.byteOffset

        for (i in 0 until accessor.count * 3) {
            val byteIndex = offset + i * 4
            if (byteIndex + 4 > buffer.size) break
            floats[i] = bytesToFloat(buffer, byteIndex)
        }

        return floats
    }

    private fun extractIntData(accessorIndex: Int): IntArray? {
        val accessor = findAccessor(accessorIndex) ?: return null
        val bufferView = findBufferView(accessor.bufferView) ?: return null
        val buffer = buffers[bufferView.buffer] ?: return null

        val ints = IntArray(accessor.count)
        val offset = bufferView.byteOffset

        // Component type 5123 = UNSIGNED_SHORT (2 bytes)
        // Component type 5125 = UNSIGNED_INT (4 bytes)
        val bytesPerElement = if (accessor.componentType == 5123) 2 else 4

        for (i in 0 until accessor.count) {
            val byteIndex = offset + i * bytesPerElement
            if (byteIndex + bytesPerElement > buffer.size) break
            ints[i] = if (bytesPerElement == 2) {
                bytesToUShort(buffer, byteIndex)
            } else {
                bytesToInt(buffer, byteIndex)
            }
        }

        return ints
    }

    private data class AccessorInfo(
        val bufferView: Int,
        val componentType: Int,
        val count: Int
    )

    private data class BufferViewInfo(
        val buffer: Int,
        val byteOffset: Int,
        val byteLength: Int
    )

    private fun findAccessor(index: Int): AccessorInfo? {
        val accessorsStart = json.indexOf("\"accessors\"")
        if (accessorsStart == -1) return null

        var currentIndex = 0
        var searchStart = accessorsStart

        while (currentIndex <= index) {
            val objStart = json.indexOf("{", searchStart)
            if (objStart == -1) return null

            if (currentIndex == index) {
                val objEnd = findMatchingBrace(objStart)
                val objJson = json.substring(objStart, objEnd + 1)

                val bufferView = extractInt(objJson, "bufferView") ?: 0
                val componentType = extractInt(objJson, "componentType") ?: 5126
                val count = extractInt(objJson, "count") ?: 0

                return AccessorInfo(bufferView, componentType, count)
            }

            searchStart = findMatchingBrace(objStart) + 1
            currentIndex++
        }

        return null
    }

    private fun findBufferView(index: Int): BufferViewInfo? {
        val bufferViewsStart = json.indexOf("\"bufferViews\"")
        if (bufferViewsStart == -1) return null

        var currentIndex = 0
        var searchStart = bufferViewsStart

        while (currentIndex <= index) {
            val objStart = json.indexOf("{", searchStart)
            if (objStart == -1) return null

            if (currentIndex == index) {
                val objEnd = findMatchingBrace(objStart)
                val objJson = json.substring(objStart, objEnd + 1)

                val buffer = extractInt(objJson, "buffer") ?: 0
                val byteOffset = extractInt(objJson, "byteOffset") ?: 0
                val byteLength = extractInt(objJson, "byteLength") ?: 0

                return BufferViewInfo(buffer, byteOffset, byteLength)
            }

            searchStart = findMatchingBrace(objStart) + 1
            currentIndex++
        }

        return null
    }

    private fun extractInt(json: String, key: String): Int? {
        val keyStart = json.indexOf("\"$key\"")
        if (keyStart == -1) return null

        val colonPos = json.indexOf(":", keyStart)
        if (colonPos == -1) return null

        return parseIntAfter(colonPos)
    }

    private fun findMatchingBrace(openPos: Int): Int {
        var depth = 1
        var i = openPos + 1
        while (i < json.length && depth > 0) {
            when (json[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return i - 1
    }

    private fun bytesToFloat(bytes: ByteArray, offset: Int): Float {
        val bits = (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(bits)
    }

    private fun bytesToUShort(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun bytesToInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}