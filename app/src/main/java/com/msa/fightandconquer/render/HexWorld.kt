package com.msa.fightandconquer.render

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.render.mesh.Primitives
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Axial (core topology) <-> world-space (XZ plane) conversion for pointy-top hexes
 * of circumradius [Primitives.HEX_RADIUS]. Mesh corners and this mapping must agree;
 * both put corner 0 toward -Z.
 */
object HexWorld {
    private const val R = Primitives.HEX_RADIUS
    private val SQRT3 = sqrt(3f)

    fun centerX(hex: Hex): Float = R * SQRT3 * (hex.q + hex.r / 2f)
    fun centerZ(hex: Hex): Float = R * 1.5f * hex.r

    /** Continuous world XZ -> axial with cube rounding. */
    fun worldToHex(x: Float, z: Float): Hex {
        val qf = (SQRT3 / 3f * x - 1f / 3f * z) / R
        val rf = (2f / 3f * z) / R
        return axialRound(qf, rf)
    }

    private fun axialRound(qf: Float, rf: Float): Hex {
        val sf = -qf - rf
        var q = qf.roundToInt()
        var r = rf.roundToInt()
        val s = sf.roundToInt()
        val dq = abs(q - qf)
        val dr = abs(r - rf)
        val ds = abs(s - sf)
        if (dq > dr && dq > ds) {
            q = -r - s
        } else if (dr > ds) {
            r = -q - s
        }
        return Hex.of(q, r)
    }
}
