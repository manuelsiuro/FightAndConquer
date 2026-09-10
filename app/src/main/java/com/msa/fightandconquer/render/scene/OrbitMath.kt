package com.msa.fightandconquer.render.scene

import kotlin.math.hypot
import kotlin.math.tan

/**
 * Pure camera math for the orbiting (menu) board — no Filament, no state, so it is
 * unit-tested on the JVM. The game path never calls it.
 */
object OrbitMath {

    const val TWO_PI: Float = (2.0 * Math.PI).toFloat()

    /** Breathing room around the framed footprint — same 10 % the game's single-axis fit uses. */
    const val DEFAULT_MARGIN: Float = 1.1f

    /**
     * Camera distance that keeps a board of [spanX] x [spanZ] fully inside the frustum at
     * EVERY yaw: a rotating board sweeps the circle circumscribing its footprint, so the
     * fit uses that circle's diameter on both axes (vertical FOV and horizontal FOV, the
     * latter narrowed by [aspect] on portrait screens) and takes the binding one.
     */
    fun orbitFitDistance(
        spanX: Float,
        spanZ: Float,
        aspect: Float,
        fovDegrees: Double,
        margin: Float = DEFAULT_MARGIN,
    ): Float {
        val diameter = hypot(spanX, spanZ)
        val tanHalf = tan(Math.toRadians(fovDegrees / 2).toFloat())
        val fitVertical = diameter * 0.5f / tanHalf
        val fitHorizontal = diameter * 0.5f / (tanHalf * aspect)
        return maxOf(fitVertical, fitHorizontal) * margin
    }

    /** [yaw] + [radPerSec] * [dt], wrapped into [0, 2pi) for positive and negative rates. */
    fun advanceYaw(yaw: Float, radPerSec: Float, dt: Float): Float {
        val advanced = yaw + radPerSec * dt
        val wrapped = advanced % TWO_PI
        if (wrapped >= 0f) return wrapped
        // A tiny negative remainder rounds up to exactly TWO_PI in float — keep it half-open.
        val lifted = wrapped + TWO_PI
        return if (lifted >= TWO_PI) 0f else lifted
    }
}
