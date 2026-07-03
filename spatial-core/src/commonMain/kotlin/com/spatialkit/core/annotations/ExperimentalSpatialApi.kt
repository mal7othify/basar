package com.spatialkit.core.annotations

/**
 * Marks API that is experimental and may change or be removed without notice.
 *
 * Annotated declarations are functional but do not carry the stability and
 * compatibility guarantees of the rest of the library. Opt in with
 * `@OptIn(ExperimentalSpatialApi::class)` to acknowledge this.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API is experimental and may change or be removed without notice.",
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.TYPEALIAS,
)
public annotation class ExperimentalSpatialApi
