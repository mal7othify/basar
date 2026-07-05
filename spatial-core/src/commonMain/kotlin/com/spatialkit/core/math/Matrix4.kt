package com.spatialkit.core.math

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A 4x4 matrix for 3D transformations.
 *
 * The matrix is stored in column-major order (like OpenGL):
 * m[0..3]   = column 0 (x-axis for rotation matrices)
 * m[4..7]   = column 1 (y-axis for rotation matrices)
 * m[8..11]  = column 2 (z-axis for rotation matrices)
 * m[12..15] = column 3 (translation)
 *
 * Matrix layout:
 * ```
 * [ m0  m4  m8  m12 ]   [ 0  4  8  12 ]
 * [ m1  m5  m9  m13 ] = [ 1  5  9  13 ]
 * [ m2  m6  m10 m14 ]   [ 2  6  10 14 ]
 * [ m3  m7  m11 m15 ]   [ 3  7  11 15 ]
 * ```
 *
 * Instances are immutable: the constructor copies the input array, and the
 * elements are exposed through [get] and [toFloatArray] (which returns a copy).
 */
public class Matrix4 private constructor(
    internal val m: FloatArray,
    @Suppress("UNUSED_PARAMETER") direct: Boolean,
) {
    /**
     * Creates a matrix from 16 elements in column-major order.
     * The array is copied; later mutation of [values] does not affect the matrix.
     */
    public constructor(values: FloatArray) : this(values.copyOf(), true)

    init {
        require(m.size == 16) { "Matrix4 requires exactly 16 elements" }
    }

    /**
     * Returns the element at [index] (column-major, 0..15).
     */
    public operator fun get(index: Int): Float = m[index]

    /**
     * Returns the element at [row], [col] (0..3 each).
     */
    public operator fun get(
        row: Int,
        col: Int,
    ): Float = m[col * 4 + row]

    /**
     * Returns the 16 elements in column-major order as a new array.
     */
    public fun toFloatArray(): FloatArray = m.copyOf()

    /**
     * Multiplies this matrix by another matrix.
     *
     * @param other The matrix to multiply with
     * @return A new matrix representing the product
     */
    public operator fun times(other: Matrix4): Matrix4 {
        val result = FloatArray(16)
        for (col in 0..3) {
            for (row in 0..3) {
                result[col * 4 + row] =
                    m[row] * other.m[col * 4] +
                    m[4 + row] * other.m[col * 4 + 1] +
                    m[8 + row] * other.m[col * 4 + 2] +
                    m[12 + row] * other.m[col * 4 + 3]
            }
        }
        return of(result)
    }

    /**
     * Transforms a Vec3 by this matrix (assumes w = 1 for point transformation).
     *
     * @param vec The vector to transform
     * @return The transformed vector
     */
    public operator fun times(vec: Vec3): Vec3 {
        val x = m[0] * vec.x + m[4] * vec.y + m[8] * vec.z + m[12]
        val y = m[1] * vec.x + m[5] * vec.y + m[9] * vec.z + m[13]
        val z = m[2] * vec.x + m[6] * vec.y + m[10] * vec.z + m[14]
        val w = m[3] * vec.x + m[7] * vec.y + m[11] * vec.z + m[15]

        return if (w != 1f && w != 0f) {
            Vec3(x / w, y / w, z / w)
        } else {
            Vec3(x, y, z)
        }
    }

    /**
     * Transforms a Vec4 by this matrix.
     *
     * @param vec The vector to transform
     * @return The transformed vector
     */
    public operator fun times(vec: Vec4): Vec4 =
        Vec4(
            m[0] * vec.x + m[4] * vec.y + m[8] * vec.z + m[12] * vec.w,
            m[1] * vec.x + m[5] * vec.y + m[9] * vec.z + m[13] * vec.w,
            m[2] * vec.x + m[6] * vec.y + m[10] * vec.z + m[14] * vec.w,
            m[3] * vec.x + m[7] * vec.y + m[11] * vec.z + m[15] * vec.w,
        )

    /**
     * Computes the transpose of this matrix.
     *
     * @return A new transposed matrix
     */
    public fun transpose(): Matrix4 =
        of(
            floatArrayOf(
                m[0],
                m[4],
                m[8],
                m[12],
                m[1],
                m[5],
                m[9],
                m[13],
                m[2],
                m[6],
                m[10],
                m[14],
                m[3],
                m[7],
                m[11],
                m[15],
            ),
        )

    /**
     * Computes the inverse of this matrix.
     *
     * @return A new inverted matrix, or identity if matrix is not invertible
     */
    public fun inverse(): Matrix4 {
        val inv = FloatArray(16)

        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]

        if (abs(det) < 0.000001f) {
            return identity()
        }

        val invDet = 1f / det
        for (i in 0..15) {
            inv[i] *= invDet
        }

        return of(inv)
    }

    /**
     * Extracts the translation component from this matrix.
     *
     * @return The translation as a Vec3
     */
    public fun getTranslation(): Vec3 = Vec3(m[12], m[13], m[14])

    /**
     * Extracts the scale component from this matrix.
     *
     * @return The scale as a Vec3
     */
    public fun getScale(): Vec3 =
        Vec3(
            sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2]),
            sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6]),
            sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]),
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Matrix4
        return m.contentEquals(other.m)
    }

    override fun hashCode(): Int = m.contentHashCode()

    public companion object {
        /** Wraps a freshly allocated array without copying. */
        private fun of(values: FloatArray): Matrix4 = Matrix4(values, true)

        /**
         * Creates an identity matrix.
         */
        public fun identity(): Matrix4 =
            of(
                floatArrayOf(
                    1f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                ),
            )

        /**
         * Creates a translation matrix.
         *
         * @param x The x translation
         * @param y The y translation
         * @param z The z translation
         * @return A new translation matrix
         */
        public fun translation(
            x: Float,
            y: Float,
            z: Float,
        ): Matrix4 =
            of(
                floatArrayOf(
                    1f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                    0f,
                    x,
                    y,
                    z,
                    1f,
                ),
            )

        /**
         * Creates a translation matrix from a Vec3.
         *
         * @param translation The translation vector
         * @return A new translation matrix
         */
        public fun translation(translation: Vec3): Matrix4 = translation(translation.x, translation.y, translation.z)

        /**
         * Creates a rotation matrix from a quaternion.
         *
         * @param quaternion The rotation quaternion
         * @return A new rotation matrix
         */
        public fun rotation(quaternion: Quaternion): Matrix4 {
            val q = quaternion.normalized()

            val xx = q.x * q.x
            val yy = q.y * q.y
            val zz = q.z * q.z
            val xy = q.x * q.y
            val xz = q.x * q.z
            val yz = q.y * q.z
            val wx = q.w * q.x
            val wy = q.w * q.y
            val wz = q.w * q.z

            return of(
                floatArrayOf(
                    1f - 2f * (yy + zz),
                    2f * (xy + wz),
                    2f * (xz - wy),
                    0f,
                    2f * (xy - wz),
                    1f - 2f * (xx + zz),
                    2f * (yz + wx),
                    0f,
                    2f * (xz + wy),
                    2f * (yz - wx),
                    1f - 2f * (xx + yy),
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                ),
            )
        }

        /**
         * Creates a scale matrix.
         *
         * @param x The x scale
         * @param y The y scale
         * @param z The z scale
         * @return A new scale matrix
         */
        public fun scale(
            x: Float,
            y: Float,
            z: Float,
        ): Matrix4 =
            of(
                floatArrayOf(
                    x,
                    0f,
                    0f,
                    0f,
                    0f,
                    y,
                    0f,
                    0f,
                    0f,
                    0f,
                    z,
                    0f,
                    0f,
                    0f,
                    0f,
                    1f,
                ),
            )

        /**
         * Creates a scale matrix from a Vec3.
         *
         * @param scale The scale vector
         * @return A new scale matrix
         */
        public fun scale(scale: Vec3): Matrix4 = scale(scale.x, scale.y, scale.z)

        /**
         * Creates a uniform scale matrix.
         *
         * @param scale The uniform scale factor
         * @return A new scale matrix
         */
        public fun scale(scale: Float): Matrix4 = scale(scale, scale, scale)

        /**
         * Creates a perspective projection matrix.
         *
         * @param fov Field of view in radians
         * @param aspectRatio Width divided by height
         * @param near Near clipping plane
         * @param far Far clipping plane
         * @return A new perspective projection matrix
         */
        public fun perspective(
            fov: Float,
            aspectRatio: Float,
            near: Float,
            far: Float,
        ): Matrix4 {
            val tanHalfFov = tan(fov / 2f)
            val range = far - near

            return of(
                floatArrayOf(
                    1f / (aspectRatio * tanHalfFov),
                    0f,
                    0f,
                    0f,
                    0f,
                    1f / tanHalfFov,
                    0f,
                    0f,
                    0f,
                    0f,
                    -(far + near) / range,
                    -1f,
                    0f,
                    0f,
                    -(2f * far * near) / range,
                    0f,
                ),
            )
        }

        /**
         * Creates an orthographic projection matrix.
         *
         * @param left Left clipping plane
         * @param right Right clipping plane
         * @param bottom Bottom clipping plane
         * @param top Top clipping plane
         * @param near Near clipping plane
         * @param far Far clipping plane
         * @return A new orthographic projection matrix
         */
        public fun orthographic(
            left: Float,
            right: Float,
            bottom: Float,
            top: Float,
            near: Float,
            far: Float,
        ): Matrix4 {
            val width = right - left
            val height = top - bottom
            val depth = far - near

            return of(
                floatArrayOf(
                    2f / width,
                    0f,
                    0f,
                    0f,
                    0f,
                    2f / height,
                    0f,
                    0f,
                    0f,
                    0f,
                    -2f / depth,
                    0f,
                    -(right + left) / width,
                    -(top + bottom) / height,
                    -(far + near) / depth,
                    1f,
                ),
            )
        }

        /**
         * Creates a view matrix looking at a target from a position.
         *
         * @param eye The camera position
         * @param target The target position to look at
         * @param up The up direction (typically Vec3.UP)
         * @return A new look-at view matrix
         */
        public fun lookAt(
            eye: Vec3,
            target: Vec3,
            up: Vec3,
        ): Matrix4 {
            val forward = (target - eye).normalized()
            val right = forward.cross(up).normalized()
            val newUp = right.cross(forward)

            return of(
                floatArrayOf(
                    right.x,
                    newUp.x,
                    -forward.x,
                    0f,
                    right.y,
                    newUp.y,
                    -forward.y,
                    0f,
                    right.z,
                    newUp.z,
                    -forward.z,
                    0f,
                    -right.dot(eye),
                    -newUp.dot(eye),
                    forward.dot(eye),
                    1f,
                ),
            )
        }

        /**
         * Creates a transformation matrix from translation, rotation, and scale.
         *
         * @param translation The translation vector
         * @param rotation The rotation quaternion
         * @param scale The scale vector
         * @return A new transformation matrix
         */
        public fun trs(
            translation: Vec3,
            rotation: Quaternion,
            scale: Vec3,
        ): Matrix4 = translation(translation) * rotation(rotation) * scale(scale)
    }
}
