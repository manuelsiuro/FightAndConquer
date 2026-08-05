package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Per-tier movement ranges: a land unit moves up to
 * [RuleConstants.soldierMoveRanges] BFS steps through its own territory, the
 * final step of which may capture — no more region-wide teleporting.
 * Ranges are pinned explicitly so these tests exercise the mechanics, not the
 * shipped default values.
 */
class MovementRangeTest {

    private val pinned = com.msa.fightandconquer.core.model.RuleConstants(
        soldierMoveRanges = listOf(2, 3, 4, 5),
    )

    @Test
    fun `a peasant reaches two steps, a knight five`() {
        // P0 owns a long strip 0..7; P1 far away at 9.
        val s = strip(10, 0..7, 9..9, rules = pinned)
            .withUnit(owner = 0, tier = 1, at = hex(0))
        val peasant = Rules.reachable(s, s.unitIdAt(hex(0)))
        assertEquals(setOf(hex(1), hex(2)), peasant.moveTargets)

        val s2 = strip(10, 0..7, 9..9, rules = pinned).withUnit(owner = 0, tier = 4, at = hex(0))
        val knight = Rules.reachable(s2, s2.unitIdAt(hex(0)))
        assertEquals(setOf(hex(1), hex(2), hex(3), hex(4), hex(5)), knight.moveTargets)
    }

    @Test
    fun `capture is the final step of the march`() {
        // P0 owns 0..1; the rest is neutral. Range 2 = one step inside own
        // ground, then storm the frontier: hex 2 falls, hex 3 is out of reach.
        val s = strip(6, 0..1, 5..5).withUnit(owner = 0, tier = 1, at = hex(0))
        val reach = Rules.reachable(s, s.unitIdAt(hex(0)))
        assertTrue(hex(2) in reach.captureTargets)
        assertFalse("beyond the last step", hex(3) in reach.captureTargets)
    }

    @Test
    fun `the path counts steps through own territory, not the crow's flight`() {
        // Own ground bends around a bay: (0,0)-(0,1)-(1,1)-(2,0). The mouth of
        // the bay (1,0) is open sea, so (2,0) is 3 marching steps away even
        // though it sits only 2 hexes from the start as the crow flies.
        val s = com.msa.fightandconquer.core.TestStates.custom(
            owners = mapOf(
                hex(0, 0) to 0, hex(0, 1) to 0, hex(1, 1) to 0, hex(2, 0) to 0,
                hex(4, 0) to 1,
            ),
            capital0 = hex(0, 0),
            capital1 = hex(4, 0),
            rules = pinned,
        ).withSea(hex(1, 0))

        val peasant = s.withUnit(owner = 0, tier = 1, at = hex(0, 0))
        assertFalse(
            "range 2 cannot round the bay",
            hex(2, 0) in Rules.reachable(peasant, peasant.unitIdAt(hex(0, 0))).moveTargets,
        )
        val spearman = s.withUnit(owner = 0, tier = 2, at = hex(0, 0))
        assertTrue(
            "range 3 can",
            hex(2, 0) in Rules.reachable(spearman, spearman.unitIdAt(hex(0, 0))).moveTargets,
        )
    }

    @Test
    fun `merging obeys the same range`() {
        // Two peasants 4 steps apart cannot merge; 2 steps apart they can.
        val far = strip(10, 0..7, 9..9)
            .withUnit(owner = 0, tier = 1, at = hex(0))
            .withUnit(owner = 0, tier = 1, at = hex(4))
        assertFalse(hex(4) in Rules.reachable(far, far.unitIdAt(hex(0))).mergeTargets)

        val near = strip(10, 0..7, 9..9)
            .withUnit(owner = 0, tier = 1, at = hex(0))
            .withUnit(owner = 0, tier = 1, at = hex(2))
        assertTrue(hex(2) in Rules.reachable(near, near.unitIdAt(hex(0))).mergeTargets)
    }

    @Test
    fun `friendly units and buildings do not block the path`() {
        // A peasant standing mid-path is passable ground; so is a farm. Only
        // the destination must be stand-able and empty.
        val s = strip(10, 0..7, 9..9)
            .withUnit(owner = 0, tier = 1, at = hex(0))
            .withUnit(owner = 0, tier = 2, at = hex(1))
        val reach = Rules.reachable(s, s.unitIdAt(hex(0)))
        assertFalse("occupied hex is not a destination", hex(1) in reach.moveTargets)
        assertTrue("but the path runs through it", hex(2) in reach.moveTargets)
    }

    @Test
    fun `too-defended frontier hexes within range are reported as blocked`() {
        // P1's tier-2 defender makes its hex defense 2 — a peasant (strength 1)
        // sees it as blocked, not capturable.
        val s = strip(6, 0..2, 3..5)
            .withUnit(owner = 0, tier = 1, at = hex(2))
            .withUnit(owner = 1, tier = 2, at = hex(3))
        val reach = Rules.reachable(s, s.unitIdAt(hex(2)))
        assertFalse(hex(3) in reach.captureTargets)
        assertTrue(hex(3) in reach.blockedTargets)
    }
}
