package com.msa.fightandconquer.render.anim

/** Easing curves; input and output in [0,1] (easeOutBack overshoots above 1). */
object Easings {
    fun linear(t: Float): Float = t

    fun easeOutCubic(t: Float): Float {
        val u = 1f - t
        return 1f - u * u * u
    }

    fun easeInCubic(t: Float): Float = t * t * t

    /** The doc's spawn curve: springs past 1 then settles. */
    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val u = t - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    /** Parabolic hop height factor: 0 at t=0 and t=1, 1 at t=0.5. */
    fun hop(t: Float): Float = 4f * t * (1f - t)
}
