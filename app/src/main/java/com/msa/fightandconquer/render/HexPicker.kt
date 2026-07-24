package com.msa.fightandconquer.render

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.render.mesh.Primitives
import kotlin.math.abs

/**
 * CPU picking: cast the camera ray against every possible tile-top plane
 * (raised, land, sea — highest first), convert each hit to axial coordinates,
 * and accept the first plane whose hex actually has that top height.
 * Eliminates parallax error at borders between tiles of different heights.
 */
class HexPicker(
    /** The world-space top Y of the tile at a hex, or null when no tile is there. */
    private val topYOf: (Hex) -> Float?,
) {
    private val planes = floatArrayOf(
        Primitives.HEX_TOP_Y + Primitives.CAPTURE_RAISE,
        Primitives.HEX_TOP_Y,
        Primitives.HEX_TOP_Y - Primitives.SEA_SINK,
    )

    fun pick(
        xPx: Float,
        yPx: Float,
        viewportW: Int,
        viewportH: Int,
        rig: CameraRig,
    ): Hex? {
        if (viewportW <= 0 || viewportH <= 0) return null
        val (origin, dir) = rig.rayThrough(xPx, yPx, viewportW, viewportH)
        if (dir.y >= -1e-5f) return null // looking up: never hits the board

        for (planeY in planes) {
            val t = (planeY - origin.y) / dir.y
            if (t <= 0f) continue
            val hx = origin.x + dir.x * t
            val hz = origin.z + dir.z * t
            val hex = HexWorld.worldToHex(hx, hz)
            val top = topYOf(hex) ?: continue
            if (abs(top - planeY) < 1e-3f) return hex
        }
        return null
    }
}
