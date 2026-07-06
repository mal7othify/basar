@file:JsModule("three")
@file:JsNonModule

package com.spatialkit.engine.webxr

import org.w3c.dom.HTMLCanvasElement

/**
 * External declarations for Three.js library.
 *
 * Only includes the subset needed for WebXR engine implementation.
 */

public external class Scene {
    public val children: Array<Object3D>

    public fun add(obj: Object3D)

    public fun remove(obj: Object3D)

    public fun getObjectByName(name: String): Object3D?
}

public external class WebGLRenderer(
    params: dynamic = definedExternally,
) {
    public val domElement: HTMLCanvasElement
    public val xr: WebXRManager

    public fun setSize(
        width: Int,
        height: Int,
    )

    public fun setAnimationLoop(callback: ((Double, dynamic) -> Unit)?)

    public fun render(
        scene: Scene,
        camera: Camera,
    )
}

public external class WebXRManager {
    public var enabled: Boolean

    public fun getSession(): XRSession?

    public fun setReferenceSpaceType(type: String)

    public fun setSession(session: XRSession?): dynamic
}

// === Cameras ===

public abstract external class Camera : Object3D {
    public val projectionMatrix: Matrix4
    public val matrixWorldInverse: Matrix4
}

public external class PerspectiveCamera(
    fov: Double = definedExternally,
    aspect: Double = definedExternally,
    near: Double = definedExternally,
    far: Double = definedExternally,
) : Camera

// === Object3D Hierarchy ===

public open external class Object3D {
    public var position: Vector3
    public var rotation: Euler
    public var quaternion: Quaternion
    public var scale: Vector3
    public var visible: Boolean
    public val matrix: Matrix4
    public val matrixWorld: Matrix4
    public var name: String

    public fun add(obj: Object3D)

    public fun remove(obj: Object3D)

    public fun updateMatrix()

    public fun updateMatrixWorld(force: Boolean = definedExternally)
}

public external class Group : Object3D

public external class Mesh(
    geometry: BufferGeometry = definedExternally,
    material: Material = definedExternally,
) : Object3D {
    public var geometry: BufferGeometry
    public var material: Material
}

// === Geometries ===

public open external class BufferGeometry {
    public fun dispose()

    public fun setAttribute(
        name: String,
        attribute: BufferAttribute,
    )

    public fun setIndex(index: BufferAttribute)
}

public external class BoxGeometry(
    width: Double = definedExternally,
    height: Double = definedExternally,
    depth: Double = definedExternally,
) : BufferGeometry

public external class SphereGeometry(
    radius: Double = definedExternally,
    widthSegments: Int = definedExternally,
    heightSegments: Int = definedExternally,
) : BufferGeometry

public external class PlaneGeometry(
    width: Double = definedExternally,
    height: Double = definedExternally,
) : BufferGeometry

public external class CylinderGeometry(
    radiusTop: Double = definedExternally,
    radiusBottom: Double = definedExternally,
    height: Double = definedExternally,
    radialSegments: Int = definedExternally,
) : BufferGeometry

public external class CapsuleGeometry(
    radius: Double = definedExternally,
    length: Double = definedExternally,
    capSegments: Int = definedExternally,
    radialSegments: Int = definedExternally,
) : BufferGeometry

public external class BufferAttribute(
    array: dynamic,
    itemSize: Int,
)

// === Textures ===

public external class Texture(
    image: dynamic = definedExternally,
) {
    public var needsUpdate: Boolean
    public var wrapS: Int
    public var wrapT: Int
    public var minFilter: Int
    public var magFilter: Int
    public var flipY: Boolean
    public var colorSpace: String

    public fun dispose()
}

// === Materials ===

public abstract external class Material {
    public fun dispose()
}

public external class MeshBasicMaterial(
    params: dynamic = definedExternally,
) : Material

public external class MeshStandardMaterial(
    params: dynamic = definedExternally,
) : Material

// === Lights ===

public abstract external class Light(
    color: Int = definedExternally,
    intensity: Double = definedExternally,
) : Object3D

public external class AmbientLight(
    color: Int = definedExternally,
    intensity: Double = definedExternally,
) : Light

public external class DirectionalLight(
    color: Int = definedExternally,
    intensity: Double = definedExternally,
) : Light

// === Math ===

public external class Vector2(
    x: Double = definedExternally,
    y: Double = definedExternally,
) {
    public var x: Double
    public var y: Double

    public fun set(
        x: Double,
        y: Double,
    ): Vector2
}

public external class Vector3(
    x: Double = definedExternally,
    y: Double = definedExternally,
    z: Double = definedExternally,
) {
    public var x: Double
    public var y: Double
    public var z: Double

    public fun set(
        x: Double,
        y: Double,
        z: Double,
    ): Vector3

    public fun copy(v: Vector3): Vector3

    public fun length(): Double

    public fun normalize(): Vector3
}

public external class Quaternion(
    x: Double = definedExternally,
    y: Double = definedExternally,
    z: Double = definedExternally,
    w: Double = definedExternally,
) {
    public var x: Double
    public var y: Double
    public var z: Double
    public var w: Double

    public fun set(
        x: Double,
        y: Double,
        z: Double,
        w: Double,
    ): Quaternion

    public fun copy(q: Quaternion): Quaternion
}

public external class Euler(
    x: Double = definedExternally,
    y: Double = definedExternally,
    z: Double = definedExternally,
    order: String = definedExternally,
) {
    public var x: Double
    public var y: Double
    public var z: Double
    public var order: String
}

public external class Matrix4 {
    public val elements: Array<Double>

    public fun toArray(): Array<Double>

    public fun fromArray(array: Array<Double>): Matrix4
}

public external class Color(
    color: dynamic = definedExternally,
) {
    public var r: Double
    public var g: Double
    public var b: Double

    public fun setRGB(
        r: Double,
        g: Double,
        b: Double,
    ): Color
}

// === Raycaster ===

public external class Raycaster(
    origin: Vector3 = definedExternally,
    direction: Vector3 = definedExternally,
) {
    public var ray: Ray

    public fun set(
        origin: Vector3,
        direction: Vector3,
    )

    public fun setFromCamera(
        coords: Vector2,
        camera: Camera,
    )

    public fun intersectObjects(
        objects: Array<Object3D>,
        recursive: Boolean = definedExternally,
    ): Array<Intersection>
}

public external class Ray {
    public var origin: Vector3
    public var direction: Vector3
}

public external interface Intersection {
    public val distance: Double
    public val point: Vector3
    public val `object`: Object3D
    public val face: dynamic
}
