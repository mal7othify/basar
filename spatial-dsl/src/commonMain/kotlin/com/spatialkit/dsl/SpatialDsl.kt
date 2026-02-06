package com.spatialkit.dsl

/**
 * DSL marker for spatial scene building.
 *
 * Prevents implicit receivers from being mixed, ensuring type safety
 * in nested DSL scopes.
 */
@DslMarker
annotation class SpatialDsl
