package com.spatialkit.engine

/**
 * Type aliases for math types from spatial-core.
 *
 * The engine contract reuses the fully-featured math types from spatial-core
 * instead of defining minimal versions. This ensures type compatibility across
 * all modules and provides implementations with access to full math operations.
 */

typealias Vec2 = com.spatialkit.core.math.Vec2
typealias Vec3 = com.spatialkit.core.math.Vec3
typealias Vec4 = com.spatialkit.core.math.Vec4
typealias Quaternion = com.spatialkit.core.math.Quaternion
typealias Transform = com.spatialkit.core.math.Transform
