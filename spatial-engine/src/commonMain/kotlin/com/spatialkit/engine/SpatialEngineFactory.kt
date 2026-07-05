package com.spatialkit.engine

/**
 * Cross-platform factory for creating SpatialEngine instances.
 *
 * Uses expect/actual pattern to provide the correct engine implementation
 * for each platform (WebXR, ARCore, ARKit, etc.).
 *
 * Usage:
 * ```kotlin
 * if (SpatialEngineFactory.isSupported()) {
 *     val engine = SpatialEngineFactory.create()
 *     // ... use engine
 * }
 * ```
 *
 * Note: Some platforms (Android) require additional context and should use
 * platform-specific factory methods instead.
 */
public expect object SpatialEngineFactory {
    /**
     * Check if spatial AR/VR is supported on this platform.
     *
     * Returns false on:
     * - Desktop browsers without WebXR
     * - Android devices without ARCore
     * - iOS devices without ARKit
     * - Unsupported platforms
     *
     * @return true if at least one session mode is available
     */
    public fun isSupported(): Boolean

    /**
     * Get the list of session modes supported on this platform.
     *
     * - WebXR: May support AR, VR, or both
     * - ARCore: Only AR
     * - ARKit: Only AR
     * - Desktop: Empty list
     *
     * @return List of supported SessionMode types
     */
    public fun getSupportedModes(): List<SessionMode>

    /**
     * Create a SpatialEngine instance for this platform.
     *
     * WARNING: On Android, this throws IllegalStateException.
     * Use createWithContext(context) from the Android-specific factory instead.
     *
     * @return Platform-specific SpatialEngine implementation
     * @throws IllegalStateException if platform requires additional context
     * @throws UnsupportedOperationException if platform doesn't support spatial
     */
    public fun create(): SpatialEngine

    /**
     * Get the name of the engine implementation for this platform.
     *
     * Examples: "WebXR", "ARCore", "ARKit", "Fallback"
     *
     * @return Engine implementation name
     */
    public fun getEngineName(): String
}
