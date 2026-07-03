package com.spatialkit.scene

/**
 * JS implementation of SpatialLock.
 *
 * JavaScript is single-threaded, so no actual locking is needed.
 * This is a pass-through implementation that maintains API compatibility.
 */
internal actual class SpatialLock {
    actual inline fun <T> withLock(block: () -> T): T {
        // JS is single-threaded (event loop), no actual locking needed
        return block()
    }
}
