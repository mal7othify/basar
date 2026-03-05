package com.spatialkit.scene

/**
 * Platform-agnostic lock for thread-safe operations.
 *
 * P0.3 FIX: Cross-platform synchronization primitive.
 */
internal expect class SpatialLock() {
    /**
     * Execute the given block while holding this lock.
     * Inline to support non-local returns.
     */
    inline fun <T> withLock(block: () -> T): T
}
