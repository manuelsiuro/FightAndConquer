package com.msa.fightandconquer.render

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.render.mesh.Primitives

/**
 * CPU picking: cast the camera ray against the two possible tile-top planes
 * (raised first, then base), convert the hit to axial coordinates, and accept the
 * first plane whose hex actually has that height. Eliminates parallax error at
 * borders between raised and flush tiles.
 */
class HexPicker(
    private val exists: (Hex) -> Boolean,
    private val isRaised: (Hex) -> Boolean,
) {
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

        val raisedY = Primitives.HEX_TOP_Y + Primitives.CAPTURE_RAISE
        val baseY = Primitives.HEX_TOP_Y

        for ((planeY, wantRaised) in listOf(raisedY to true, baseY to false)) {
            val t = (planeY - origin.y) / dir.y
            if (t <= 0f) continue
            val hx = origin.x + dir.x * t
            val hz = origin.z + dir.z * t
            val hex = HexWorld.worldToHex(hx, hz)
            if (exists(hex) && isRaised(hex) == wantRaised) return hex
        }
        return null
    }
}
