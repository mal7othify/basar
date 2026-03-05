package com.spatialkit.scene

import platform.Foundation.NSRecursiveLock

/**
 * iOS implementation of SpatialLock using NSRecursiveLock.
 *
 * P0.3 FIX: Thread-safe synchronization for iOS platform.
 */
internal actual class SpatialLock {
    private val lock = NSRecursiveLock()

    actual inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}
