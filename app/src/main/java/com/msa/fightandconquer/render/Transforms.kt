package com.msa.fightandconquer.render

import kotlin.math.cos
import kotlin.math.sin

/**
 * Column-major 4x4 transform helpers (Filament's TransformManager convention).
 * Hand-rolled to avoid row/column-major ambiguity with math libraries.
 */
object Transforms {

    /** Translate * RotateY * UniformScale, column-major. */
    fun trs(
        tx: Float,
        ty: Float,
        tz: Float,
        angleYRadians: Float = 0f,
        scale: Float = 1f,
        scaleY: Float = scale,
    ): FloatArray {
        val c = cos(angleYRadians) * scale
        val s = sin(angleYRadians) * scale
        return floatArrayOf(
            c, 0f, -s, 0f,          // column 0
            0f, scaleY, 0f, 0f,     // column 1
            s, 0f, c, 0f,           // column 2
            tx, ty, tz, 1f,         // column 3
        )
    }

    fun translation(tx: Float, ty: Float, tz: Float): FloatArray = trs(tx, ty, tz)
}
