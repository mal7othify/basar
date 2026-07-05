package com.spatialkit.dsl

import com.spatialkit.core.math.Vec3
import com.spatialkit.core.math.cm
import com.spatialkit.core.math.meters
import com.spatialkit.core.math.mm
import com.spatialkit.core.types.Color
import com.spatialkit.scene.CubeNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Example tests demonstrating DSL usage.
 *
 * These are not exhaustive tests, but rather examples
 * showing how the DSL works.
 */
class DslExampleTest {
    @Test
    fun testSimpleScene() {
        val scene =
            spatialScene {
                cube(size = 0.1f, color = Color.Red) {
                    position = vec3(0f, 0f, -1f)
                }
            }

        assertEquals(1, scene.nodes.size)
    }

    @Test
    fun testUnitsConversion() {
        val scene =
            spatialScene {
                cube(size = 10.cm) {
                    position = vec3(0f, 1.5.meters, -100.mm)
                }
            }

        val cube = scene.nodes.first() as CubeNode
        assertEquals(0.1f, cube.size, 0.001f) // 10cm = 0.1m
        assertEquals(1.5f, cube.position.y, 0.001f)
        assertEquals(-0.1f, cube.position.z, 0.001f) // -100mm = -0.1m
    }

    @Test
    fun testGroupHierarchy() {
        val scene =
            spatialScene {
                group(id = "parent") {
                    cube(id = "child1") {
                        position = vec3(-0.1f, 0f, 0f)
                    }

                    cube(id = "child2") {
                        position = vec3(0.1f, 0f, 0f)
                    }
                }
            }

        assertEquals(1, scene.nodes.size)
        val group = scene.nodes.first()
        assertEquals(2, group.children.size)
    }

    @Test
    fun testNestedGroups() {
        val scene =
            spatialScene {
                group {
                    cube()

                    group {
                        sphere()
                        sphere()
                    }
                }
            }

        val topGroup = scene.nodes.first()
        assertEquals(2, topGroup.children.size) // 1 cube + 1 nested group
        val nestedGroup = topGroup.children[1]
        assertEquals(2, nestedGroup.children.size) // 2 spheres
    }

    @Test
    fun testRotationHelpers() {
        val scene =
            spatialScene {
                cube {
                    rotation = eulerDegrees(0, 45, 0)
                }
            }

        val cube = scene.nodes.first()
        assertNotNull(cube.rotation)
    }

    @Test
    fun testColorHelpers() {
        val scene =
            spatialScene {
                cube(color = rgb(0xFF0000)) // Red
                sphere(color = rgba(0x00FF00FF)) // Green with alpha
            }

        assertEquals(2, scene.nodes.size)
    }

    @Test
    fun testMultipleShapes() {
        val scene =
            spatialScene {
                cube(id = "cube")
                sphere(id = "sphere")
                plane(id = "plane")
                cylinder(id = "cylinder")
                capsule(id = "capsule")
            }

        assertEquals(5, scene.nodes.size)
        assertNotNull(scene.findNodeById("cube"))
        assertNotNull(scene.findNodeById("sphere"))
        assertNotNull(scene.findNodeById("plane"))
        assertNotNull(scene.findNodeById("cylinder"))
        assertNotNull(scene.findNodeById("capsule"))
    }

    @Test
    fun testConfigureBlock() {
        val scene =
            spatialScene {
                cube(size = 0.1f, color = Color.Red) {
                    position = vec3(1f, 2f, 3f)
                    scale = Vec3(2f, 2f, 2f)
                    visible = false
                }
            }

        val cube = scene.nodes.first()
        assertEquals(1f, cube.position.x)
        assertEquals(2f, cube.position.y)
        assertEquals(3f, cube.position.z)
        assertEquals(2f, cube.scale.x)
        assertEquals(false, cube.visible)
    }
}
