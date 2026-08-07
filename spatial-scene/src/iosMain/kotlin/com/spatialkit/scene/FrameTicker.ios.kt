package com.spatialkit.scene

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.NSSelectorFromString
import platform.QuartzCore.CADisplayLink
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
public actual class FrameTicker {
    private var displayLink: CADisplayLink? = null
    private var target: TickTarget? = null

    public actual fun start(onFrame: (frameTimeNanos: Long) -> Unit) {
        stop()
        val tickTarget = TickTarget { link -> onFrame((link.timestamp * 1e9).toLong()) }
        target = tickTarget
        displayLink =
            CADisplayLink.displayLinkWithTarget(tickTarget, NSSelectorFromString("tick:")).also {
                it.addToRunLoop(NSRunLoop.mainRunLoop, NSDefaultRunLoopMode)
            }
    }

    public actual fun stop() {
        displayLink?.invalidate()
        displayLink = null
        target = null
    }

    private class TickTarget(
        private val onTick: (CADisplayLink) -> Unit,
    ) : NSObject() {
        @Suppress("unused")
        @kotlinx.cinterop.ObjCAction
        fun tick(link: CADisplayLink) {
            onTick(link)
        }
    }
}
