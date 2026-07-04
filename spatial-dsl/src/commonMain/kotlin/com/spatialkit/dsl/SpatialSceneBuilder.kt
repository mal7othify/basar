package com.spatialkit.dsl

import com.spatialkit.scene.SpatialScene

/**
 * Creates a spatial scene using a Compose-like DSL.
 *
 * This is the main entry point for building scenes with the DSL.
 *
 * Example:
 * ```kotlin
 * val scene = spatialScene {
 *     cube(size = 0.1f, color = Color.RED) {
 *         position = vec3(0f, 1.5f, -1f)
 *         rotation = Quaternion.fromEuler(0f, 45f, 0f)
 *     }
 *
 *     sphere(radius = 0.05f) {
 *         position = vec3(0.2f, 1.5f, -1f)
 *     }
 *
 *     group {
 *         cube { position = vec3(-0.1f, 0f, 0f) }
 *         cube { position = vec3(0.1f, 0f, 0f) }
 *     }
 * }
 * ```
 *
 * @param build Lambda with SpatialSceneScope receiver to build the scene
 * @return A configured SpatialScene
 */
public fun spatialScene(build: SpatialSceneScope.() -> Unit): SpatialScene =
    SpatialScene().also { scene ->
        SpatialSceneScope(scene).build()
    }
