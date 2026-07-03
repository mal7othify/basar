package com.spatialkit.core.log

/**
 * Severity levels for library logging.
 */
public enum class SpatialLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Receives log records emitted by the library.
 *
 * Implement this to route library logs into your app's logging framework
 * (Timber, Napier, os_log, ...), then install it via [SpatialLog.sink].
 */
public fun interface SpatialLogSink {
    public fun log(
        level: SpatialLogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    )
}

/**
 * Logging facade used by all Spatial-KMP modules.
 *
 * By default only [SpatialLogLevel.WARN] and above are emitted, via `println`.
 * Applications can lower [minLevel] to see diagnostics, or replace [sink] to
 * route records elsewhere. Message parameters are lambdas so that suppressed
 * levels cost no string construction — safe on per-frame paths.
 *
 * ```kotlin
 * SpatialLog.minLevel = SpatialLogLevel.DEBUG
 * SpatialLog.sink = SpatialLogSink { level, tag, message, throwable ->
 *     Log.println(level.toAndroidPriority(), tag, message)
 * }
 * ```
 */
public object SpatialLog {
    /**
     * Minimum level that is emitted. Records below this level are dropped
     * before their message is built.
     */
    public var minLevel: SpatialLogLevel = SpatialLogLevel.WARN

    /**
     * Destination for emitted records. Defaults to `println`.
     */
    public var sink: SpatialLogSink =
        SpatialLogSink { level, tag, message, throwable ->
            println("[$level] $tag: $message" + (throwable?.let { " (${it.message})" } ?: ""))
        }

    public fun debug(
        tag: String,
        message: () -> String,
    ) {
        log(SpatialLogLevel.DEBUG, tag, null, message)
    }

    public fun info(
        tag: String,
        message: () -> String,
    ) {
        log(SpatialLogLevel.INFO, tag, null, message)
    }

    public fun warn(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        log(SpatialLogLevel.WARN, tag, throwable, message)
    }

    public fun error(
        tag: String,
        throwable: Throwable? = null,
        message: () -> String,
    ) {
        log(SpatialLogLevel.ERROR, tag, throwable, message)
    }

    private fun log(
        level: SpatialLogLevel,
        tag: String,
        throwable: Throwable?,
        message: () -> String,
    ) {
        if (level >= minLevel) {
            sink.log(level, tag, message(), throwable)
        }
    }
}
