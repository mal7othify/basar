package com.spatialkit.scene

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock as ktWithLock

/**
 * Android implementation of SpatialLock using ReentrantLock.
 *
 * P0.3 FIX: Thread-safe synchronization for Android platform.
 */
internal actual class SpatialLock {
    private val lock = ReentrantLock()

    actual inline fun <T> withLock(block: () -> T): T = lock.ktWithLock(block)
}
