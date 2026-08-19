package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Corner cases surfaced by the 2026-08 rules audit — each pins the engine's
 * current answer so a future change is a conscious one.
 */
class RulesAuditCornerTest {

    private val rules = RuleConstants()

    @Test
    fun `a market counts an owned bridge hex as a neighbor although the bridge itself earns nothing`() {
        // docs/game-rules.md sells the market as "+1 per producing neighbor";
        // the code counts owned, non-starving, flora-free neighbors — which an
        // owned BRIDGE sea hex is, despite producing zero itself. Pinned as-is.
        val seaSide = hex(1, -1) // neighbors hex(1) in axial space
        val base = strip(9, 0..3, 6..8)
            .withBuilding(Building.MARKET, hex(1))
            .withSea(seaSide)
        val withNeutralSea = Rules.incomeOf(base, PlayerId(0))
        val bridged = base.copy(
            tiles = base.tiles + (
                seaSide to base.tiles.getValue(seaSide)
                    .copy(owner = PlayerId(0), building = Building.BRIDGE)
                ),
        )
        assertEquals(
            "owned bridge raises market income by one neighbor step",
            withNeutralSea + rules.marketNeighborIncome,
            Rules.incomeOf(bridged, PlayerId(0)),
        )
    }

    @Test
    fun `bankruptcy sinks the fishing fleet with everything else`() {
        // P1: 3 hexes (income 3) against a knight's upkeep — turn start goes
        // negative, so ALL units die, the parked dory included, sunk not buried.
        val shoal = hex(5)
        val s = strip(9, 0..2, 6..8)
            .withSea(listOf(hex(3), hex(4), hex(5)))
            .withUnit(owner = 1, tier = 3, at = hex(7))
            .withUnit(owner = 1, tier = 1, at = shoal, type = UnitType.FISHING_BOAT)
            .withTreasury(1, 0)
        val (next, events) = Reducer.reduce(s, GameAction.EndTurn)
        assertTrue(events.any { it is GameEvent.Bankruptcy })
        assertTrue(
            "every P1 unit dies to bankruptcy",
            next.units.values.none { it.owner == PlayerId(1) },
        )
        assertTrue(
            events.any {
                it is GameEvent.UnitDied && it.cause == DeathCause.BANKRUPTCY && it.hex == shoal
            },
        )
        assertNull("no gravestone at sea", next.tiles.getValue(shoal).flora)
        assertEquals(0, next.player(PlayerId(1)).treasury)
        assertInvariants(next)
    }

    @Test
    fun `demolishing a fishery pays the standard partial refund`() {
        val s = strip(9, 0..2, 6..8)
            .withSea(hex(3))
            .withBuilding(Building.FISHERY, hex(2))
        val engine = GameEngine(s)
        val before = engine.state.value.player(PlayerId(0)).treasury
        assertTrue(engine.submit(GameAction.DemolishBuilding(hex(2))) is LegalityResult.Ok)
        val after = engine.state.value
        assertNull(after.tiles.getValue(hex(2)).building)
        assertEquals(
            before + rules.fisheryCost * rules.demolishRefundPercent / 100,
            after.player(PlayerId(0)).treasury,
        )
        assertInvariants(after)
    }

    @Test
    fun `a fishing boat is not a ferry - land units cannot board it`() {
        val s = strip(9, 0..2, 6..8)
            .withSea(listOf(hex(3), hex(4)))
            .withUnit(owner = 0, tier = 1, at = hex(2))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
        val soldier = s.unitIdAt(hex(2))
        assertTrue(
            "a dory is never an embark target",
            Rules.reachable(s, soldier).embarkTargets.isEmpty(),
        )
        val move = GameEngine(s).submit(GameAction.MoveUnit(soldier, hex(3)))
        assertEquals(
            RejectionReason.DESTINATION_UNREACHABLE,
            (move as LegalityResult.Rejected).reason,
        )
    }

    @Test
    fun `disembark from a fishing boat is rejected as not a transport`() {
        val s = strip(9, 0..2, 6..8)
            .withSea(hex(3))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.FISHING_BOAT)
        val result = GameEngine(s).submit(GameAction.Disembark(s.unitIdAt(hex(3)), hex(2)))
        assertEquals(RejectionReason.NOT_A_TRANSPORT, (result as LegalityResult.Rejected).reason)
    }
}
