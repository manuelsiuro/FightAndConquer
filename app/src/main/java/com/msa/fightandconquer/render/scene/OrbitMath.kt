package com.msa.fightandconquer.render.scene

import kotlin.math.hypot
import kotlin.math.sqrt
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
     * Radius of the circle a board really sweeps while it turns: the distance from
     * ([cx], [cz]) to the farthest tile center in [centers], plus [hexRadius] for that
     * tile's own reach. The bounding-box diagonal over-states this by up to ~40 % on the
     * roundish maps the generator makes, which frames the world far too small.
     */
    fun circumscribedRadius(
        centers: Iterable<Pair<Float, Float>>,
        cx: Float,
        cz: Float,
        hexRadius: Float,
    ): Float {
        var farthestSq = 0f
        for ((x, z) in centers) {
            val dx = x - cx
            val dz = z - cz
            val distanceSq = dx * dx + dz * dz
            if (distanceSq > farthestSq) farthestSq = distanceSq
        }
        return sqrt(farthestSq) + hexRadius
    }

    /**
     * Camera distance that keeps a footprint circle of [diameter] inside the frustum at
     * EVERY yaw: the circle is fitted against the vertical FOV and the horizontal one
     * (narrowed by [aspect] on portrait screens), the binding one wins. A [margin] below
     * 1 deliberately lets the circle overflow the screen — the menu world does that so
     * the board fills the width; the sea rim clipping at the sides is the point.
     */
    fun orbitFitDistanceForCircle(
        diameter: Float,
        aspect: Float,
        fovDegrees: Double,
        margin: Float = DEFAULT_MARGIN,
    ): Float {
        val tanHalf = tan(Math.toRadians(fovDegrees / 2).toFloat())
        val fitVertical = diameter * 0.5f / tanHalf
        val fitHorizontal = diameter * 0.5f / (tanHalf * aspect)
        return maxOf(fitVertical, fitHorizontal) * margin
    }

    /**
     * [orbitFitDistanceForCircle] for callers that only know a bounding box: the circle
     * circumscribing a [spanX] x [spanZ] box has the box's diagonal as its diameter.
     */
    fun orbitFitDistance(
        spanX: Float,
        spanZ: Float,
        aspect: Float,
        fovDegrees: Double,
        margin: Float = DEFAULT_MARGIN,
    ): Float = orbitFitDistanceForCircle(hypot(spanX, spanZ), aspect, fovDegrees, margin)

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
