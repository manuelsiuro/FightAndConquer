package com.msa.fightandconquer.render.mesh

import dev.romainguy.kotlin.math.Float3
import dev.romainguy.kotlin.math.cross
import dev.romainguy.kotlin.math.normalize
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Filament's lit shading model consumes per-vertex tangent frames as quaternions
 * (TANGENTS attribute, float4). With no normal maps in play, only the normal (the
 * frame's Z axis) matters — the tangent is an arbitrary perpendicular.
 */
object TangentFrames {

    /** Quaternion (x, y, z, w) whose frame has [normal] as its +Z axis. */
    fun quatFromNormal(normal: Float3): FloatArray {
        val n = normalize(normal)
        // Arbitrary stable tangent perpendicular to n.
        val reference = if (abs(n.y) < 0.99f) Float3(0f, 1f, 0f) else Float3(1f, 0f, 0f)
        val t = normalize(cross(reference, n))
        val b = cross(n, t)

        // Column-major rotation matrix [t b n] -> quaternion (Shepperd's method).
        val m00 = t.x; val m01 = b.x; val m02 = n.x
        val m10 = t.y; val m11 = b.y; val m12 = n.y
        val m20 = t.z; val m21 = b.z; val m22 = n.z
        val trace = m00 + m11 + m22
        var x: Float
        var y: Float
        var z: Float
        var w: Float
        when {
            trace > 0f -> {
                val s = sqrt(trace + 1f) * 2f
                w = s / 4f
                x = (m21 - m12) / s
                y = (m02 - m20) / s
                z = (m10 - m01) / s
            }
            m00 > m11 && m00 > m22 -> {
                val s = sqrt(1f + m00 - m11 - m22) * 2f
                w = (m21 - m12) / s
                x = s / 4f
                y = (m01 + m10) / s
                z = (m02 + m20) / s
            }
            m11 > m22 -> {
                val s = sqrt(1f + m11 - m00 - m22) * 2f
                w = (m02 - m20) / s
                x = (m01 + m10) / s
                y = s / 4f
                z = (m12 + m21) / s
            }
            else -> {
                val s = sqrt(1f + m22 - m00 - m11) * 2f
                w = (m10 - m01) / s
                x = (m02 + m20) / s
                y = (m12 + m21) / s
                z = s / 4f
            }
        }
        // Normalize and keep w positive (sign flip encodes handedness, unused here).
        val invLen = 1f / sqrt(x * x + y * y + z * z + w * w)
        x *= invLen; y *= invLen; z *= invLen; w *= invLen
        return if (w < 0f) floatArrayOf(-x, -y, -z, -w) else floatArrayOf(x, y, z, w)
    }
}
