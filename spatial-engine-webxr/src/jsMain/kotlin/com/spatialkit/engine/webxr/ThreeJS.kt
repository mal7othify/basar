@file:JsModule("three")
@file:JsNonModule

package com.spatialkit.engine.webxr

import org.w3c.dom.HTMLCanvasElement

/**
 * External declarations for Three.js library.
 *
 * Only includes the subset needed for WebXR engine implementation.
 */

external class Scene {
    val children: Array<Object3D>

    fun add(obj: Object3D)

    fun remove(obj: Object3D)

    fun getObjectByName(name: String): Object3D?
}

external class WebGLRenderer(
    params: dynamic = definedExternally,
) {
    val domElement: HTMLCanvasElement
    val xr: WebXRManager

    fun setSize(
        width: Int,
        height: Int,
    )

    fun setAnimationLoop(callback: ((Double, dynamic) -> Unit)?)

    fun render(
        scene: Scene,
        camera: Camera,
    )
}

external class WebXRManager {
    var enabled: Boolean

    fun getSession(): XRSession?

    fun setReferenceSpaceType(type: String)

    fun setSession(session: XRSession?): dynamic
}

// === Cameras ===

abstract external class Camera : Object3D {
    val projectionMatrix: Matrix4
    val matrixWorldInverse: Matrix4
}

external class PerspectiveCamera(
    fov: Double = definedExternally,
    aspect: Double = definedExternally,
    near: Double = definedExternally,
    far: Double = definedExternally,
) : Camera

// === Object3D Hierarchy ===

open external class Object3D {
    var position: Vector3
    var rotation: Euler
    var quaternion: Quaternion
    var scale: Vector3
    var visible: Boolean
    val matrix: Matrix4
    val matrixWorld: Matrix4
    var name: String

    fun add(obj: Object3D)

    fun remove(obj: Object3D)

    fun updateMatrix()

    fun updateMatrixWorld(force: Boolean = definedExternally)
}

external class Group : Object3D

external class Mesh(
    geometry: BufferGeometry = definedExternally,
    material: Material = definedExternally,
) : Object3D {
    var geometry: BufferGeometry
    var material: Material
}

// === Geometries ===

open external class BufferGeometry {
    fun dispose()

    fun setAttribute(
        name: String,
        attribute: BufferAttribute,
    )

    fun setIndex(index: BufferAttribute)
}

external class BoxGeometry(
    width: Double = definedExternally,
    height: Double = definedExternally,
    depth: Double = definedExternally,
) : BufferGeometry

external class SphereGeometry(
    radius: Double = definedExternally,
    widthSegments: Int = definedExternally,
    heightSegments: Int = definedExternally,
) : BufferGeometry

external class PlaneGeometry(
    width: Double = definedExternally,
    height: Double = definedExternally,
) : BufferGeometry

external class CylinderGeometry(
    radiusTop: Double = definedExternally,
    radiusBottom: Double = definedExternally,
    height: Double = definedExternally,
    radialSegments: Int = definedExternally,
) : BufferGeometry

external class CapsuleGeometry(
    radius: Double = definedExternally,
    length: Double = definedExternally,
    capSegments: Int = definedExternally,
    radialSegments: Int = definedExternally,
) : BufferGeometry

external class BufferAttribute(
    array: dynamic,
    itemSize: Int,
)

// === Materials ===

abstract external class Material {
    fun dispose()
}

external class MeshBasicMaterial(
    params: dynamic = definedExternally,
) : Material

external class MeshStandardMaterial(
    params: dynamic = definedExternally,
) : Material

// === Lights ===

abstract external class Light(
    color: Int = definedExternally,
    intensity: Double = definedExternally,
) : Object3D

external class AmbientLight(
    color: Int = definedExternally,
    intensity: Double = definedExternally,
) : Light

external class DirectionalLight(
    color: Int = definedExternally,
    intensity: Double = definedExternally,
) : Light

// === Math ===

external class Vector3(
    x: Double = definedExternally,
    y: Double = definedExternally,
    z: Double = definedExternally,
) {
    var x: Double
    var y: Double
    var z: Double

    fun set(
        x: Double,
        y: Double,
        z: Double,
    ): Vector3

    fun copy(v: Vector3): Vector3

    fun length(): Double

    fun normalize(): Vector3
}

external class Quaternion(
    x: Double = definedExternally,
    y: Double = definedExternally,
    z: Double = definedExternally,
    w: Double = definedExternally,
) {
    var x: Double
    var y: Double
    var z: Double
    var w: Double

    fun set(
        x: Double,
        y: Double,
        z: Double,
        w: Double,
    ): Quaternion

    fun copy(q: Quaternion): Quaternion
}

external class Euler(
    x: Double = definedExternally,
    y: Double = definedExternally,
    z: Double = definedExternally,
    order: String = definedExternally,
) {
    var x: Double
    var y: Double
    var z: Double
    var order: String
}

external class Matrix4 {
    val elements: Array<Double>

    fun toArray(): Array<Double>

    fun fromArray(array: Array<Double>): Matrix4
}

external class Color(
    color: dynamic = definedExternally,
) {
    var r: Double
    var g: Double
    var b: Double

    fun setRGB(
        r: Double,
        g: Double,
        b: Double,
    ): Color
}

// === Raycaster ===

external class Raycaster(
    origin: Vector3 = definedExternally,
    direction: Vector3 = definedExternally,
) {
    var ray: Ray

    fun set(
        origin: Vector3,
        direction: Vector3,
    )

    fun intersectObjects(
        objects: Array<Object3D>,
        recursive: Boolean = definedExternally,
    ): Array<Intersection>
}

external class Ray {
    var origin: Vector3
    var direction: Vector3
}

external interface Intersection {
    val distance: Double
    val point: Vector3
    val `object`: Object3D
    val face: dynamic
}
