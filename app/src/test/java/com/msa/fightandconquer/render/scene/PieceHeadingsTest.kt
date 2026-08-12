package com.msa.fightandconquer.render.scene

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.render.HexWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class PieceHeadingsTest {

    private val center = Hex.of(0, 0)

    private fun neighborAt(direction: Int): Hex {
        val (dq, dr) = HexMath.DIRECTIONS[direction]
        return Hex.of(dq, dr)
    }

    /** Compare angles modulo 2π (yaw is periodic). */
    private fun assertSameAngle(message: String, expected: Float, actual: Float) {
        val delta = atan2(sin(expected - actual), cos(expected - actual))
        assertTrue("$message (expected $expected, got $actual)", abs(delta) < 1e-4f)
    }

    @Test
    fun `heading toward each neighbor points at its center`() {
        for (direction in 0..5) {
            val n = neighborAt(direction)
            val yaw = PieceHeadings.headingYaw(center, n)
            // Local +Z maps to world (sin yaw, 0, cos yaw): walk one unit of the
            // yaw direction and check it lands on the neighbor's bearing.
            val dx = HexWorld.centerX(n) - HexWorld.centerX(center)
            val dz = HexWorld.centerZ(n) - HexWorld.centerZ(center)
            assertSameAngle("direction $direction", atan2(dx, dz), yaw)
        }
    }

    @Test
    fun `opposite directions differ by half a turn`() {
        for (axis in 0..2) {
            val head = PieceHeadings.headingYaw(center, neighborAt(axis))
            val tail = PieceHeadings.headingYaw(center, neighborAt(axis + 3))
            assertSameAngle("axis $axis reversed", head + PI.toFloat(), tail)
        }
    }

    @Test
    fun `lerpAngle takes the short way around`() {
        val from = Math.toRadians(-170.0).toFloat()
        val to = Math.toRadians(170.0).toFloat()
        // Short way is 20° backwards through ±180°, not 340° forwards.
        assertSameAngle("halfway", Math.toRadians(180.0).toFloat(), PieceHeadings.lerpAngle(from, to, 0.5f))
        assertSameAngle("arrived", to, PieceHeadings.lerpAngle(from, to, 1f))
        assertEquals("no motion at t=0", from, PieceHeadings.lerpAngle(from, to, 0f), 1e-6f)
    }

    @Test
    fun `axis yaw is a neighbor bearing, never a hex corner`() {
        for (axis in 0..2) {
            assertSameAngle(
                "axis $axis",
                PieceHeadings.headingYaw(center, neighborAt(axis)),
                PieceHeadings.bridgeAxisYaw(center, axis),
            )
        }
    }

    @Test
    fun `auto axis prefers the through-axis over first-neighbor order`() {
        // Land on axis 0's head (first in ring order) but BOTH ends of axis 2:
        // the chain axis must win even though direction 0 is scanned first.
        val connected = setOf(neighborAt(0), neighborAt(2), neighborAt(5))
        assertEquals(2, PieceHeadings.bridgeAutoAxis(center) { it in connected })
    }

    @Test
    fun `auto axis falls back to a single connected end, then to axis 0`() {
        // Only axis 1's TAIL (direction 4) touches land.
        val tailOnly = setOf(neighborAt(4))
        assertEquals(1, PieceHeadings.bridgeAutoAxis(center) { it in tailOnly })
        // Nothing connected at all: still a neighbor axis, never a corner.
        assertEquals(0, PieceHeadings.bridgeAutoAxis(center) { false })
    }

    @Test
    fun `an explicit orientation overrides the auto axis`() {
        val connected = setOf(neighborAt(0), neighborAt(3)) // through-axis 0
        assertSameAngle(
            "player choice wins",
            PieceHeadings.bridgeAxisYaw(center, 1),
            PieceHeadings.bridgeYaw(center, 1) { it in connected },
        )
        assertSameAngle(
            "null falls back to auto",
            PieceHeadings.bridgeAxisYaw(center, 0),
            PieceHeadings.bridgeYaw(center, null) { it in connected },
        )
    }
}
