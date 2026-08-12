package com.msa.fightandconquer.render.scene

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.render.HexWorld
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Pure yaw math for pieces — Filament-free so it unit-tests on the JVM
 * (same extraction pattern as [FogRules]).
 *
 * Convention: `Transforms.trs` maps local +Z to world `(sin yaw, 0, cos yaw)`,
 * and every piece is authored facing +Z (Blender front −Y), so
 * `atan2(dx, dz)` aims a piece along the world direction `(dx, dz)`.
 */
object PieceHeadings {

    /** Yaw that makes a +Z-facing piece at (fromX, fromZ) look toward (toX, toZ). */
    fun headingYaw(fromX: Float, fromZ: Float, toX: Float, toZ: Float): Float =
        atan2(toX - fromX, toZ - fromZ)

    fun headingYaw(from: Hex, to: Hex): Float = headingYaw(
        HexWorld.centerX(from), HexWorld.centerZ(from),
        HexWorld.centerX(to), HexWorld.centerZ(to),
    )

    /** Shortest-arc angle lerp — never swings the long way around. */
    fun lerpAngle(from: Float, to: Float, t: Float): Float {
        val tau = (2.0 * PI).toFloat()
        var delta = (to - from) % tau
        if (delta > PI) delta -= tau
        if (delta < -PI) delta += tau
        return from + delta * t
    }

    /**
     * Yaw of bridge deck axis [axis] at [hex] — an index 0..2 into
     * [HexMath.DIRECTIONS] (the deck is 180°-symmetric, so direction k and k+3
     * are the same axis). Always a neighbor direction, never a hex corner.
     */
    fun bridgeAxisYaw(hex: Hex, axis: Int): Float {
        val (dq, dr) = HexMath.DIRECTIONS[axis]
        return headingYaw(hex, Hex.of(hex.q + dq, hex.r + dr))
    }

    /**
     * Default deck axis for the bridge at [hex]: prefer an axis whose BOTH ends
     * touch land/bridge (the through-axis of a chain), else any single connected
     * end, else axis 0. [isConnected] answers "does the deck reach something
     * there?" — a LAND tile or another BRIDGE.
     */
    fun bridgeAutoAxis(hex: Hex, isConnected: (Hex) -> Boolean): Int {
        var single = -1
        for (axis in 0..2) {
            val (dq, dr) = HexMath.DIRECTIONS[axis]
            val head = isConnected(Hex.of(hex.q + dq, hex.r + dr))
            val tail = isConnected(Hex.of(hex.q - dq, hex.r - dr))
            if (head && tail) return axis
            if ((head || tail) && single == -1) single = axis
        }
        return if (single >= 0) single else 0
    }

    /**
     * The bridge's rendered yaw: the player-stored [orientation]
     * ([com.msa.fightandconquer.core.model.Tile.bridgeOrientation]) wins,
     * else the auto axis. A pure function of board state, so event handling
     * and reconcile always agree.
     */
    fun bridgeYaw(hex: Hex, orientation: Int?, isConnected: (Hex) -> Boolean): Float =
        bridgeAxisYaw(hex, orientation ?: bridgeAutoAxis(hex, isConnected))
}
