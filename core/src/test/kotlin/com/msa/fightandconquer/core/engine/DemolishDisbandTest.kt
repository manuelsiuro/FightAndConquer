package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withCargo
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Voluntary demolition and disbanding: partial refunds, no exploits, no strandings. */
class DemolishDisbandTest {

    private val rules = RuleConstants()

    private fun treasury(s: GameState, player: Int = 0) = s.players[player].treasury

    // ----- buildings -----

    @Test
    fun `demolishing a tower refunds half its cost`() {
        val s = strip(9, 0..5, 6..8).withBuilding(Building.TOWER, at = hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.DemolishBuilding(hex(2)))
        assertEquals(null, next.tiles.getValue(hex(2)).building)
        val expected = rules.towerCost * rules.demolishRefundPercent / 100
        assertEquals(treasury(s) + expected, treasury(next))
        assertTrue(events.any { it is GameEvent.BuildingDestroyed && it.building == Building.TOWER })
        assertTrue(events.any { it is GameEvent.RefundPaid && it.amount == expected })
        assertInvariants(next)
    }

    @Test
    fun `a farm refunds against the LAST farm's price, and the next farm gets cheaper`() {
        // Three farms chained off the capital: prices 12, 14, 16.
        val engine = GameEngine(strip(9, 0..5, 6..8))
        check(engine.submit(GameAction.BuyBuilding(BuildingType.FARM, hex(1))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.FARM, hex(2))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.FARM, hex(3))) is LegalityResult.Ok)
        val before = engine.state.value
        val lastFarmPrice = rules.farmCostBase + rules.farmCostStep * 2
        assertEquals(
            "next farm would cost base + 3 steps",
            rules.farmCostBase + rules.farmCostStep * 3,
            Rules.nextFarmCost(before, PlayerId(0)),
        )

        assertTrue(engine.submit(GameAction.DemolishBuilding(hex(3))) is LegalityResult.Ok)
        val after = engine.state.value
        val expected = lastFarmPrice * rules.demolishRefundPercent / 100
        assertEquals(treasury(before) + expected, treasury(after))
        assertEquals(
            "farm price steps back down",
            rules.farmCostBase + rules.farmCostStep * 2,
            Rules.nextFarmCost(after, PlayerId(0)),
        )
        assertInvariants(after)
    }

    @Test
    fun `the capital, enemy buildings and empty hexes all refuse`() {
        val s = strip(9, 0..2, 6..8).withBuilding(Building.TOWER, at = hex(7))
        fun reason(action: GameAction): RejectionReason {
            val (next, events) = Reducer.reduce(s, action)
            assertEquals("state untouched", s, next)
            return (events.single() as GameEvent.ActionRejected).reason
        }
        assertEquals(RejectionReason.CANNOT_DEMOLISH_CAPITAL, reason(GameAction.DemolishBuilding(hex(0))))
        assertEquals(RejectionReason.NOT_YOUR_HEX, reason(GameAction.DemolishBuilding(hex(7))))
        assertEquals(RejectionReason.NO_BUILDING_THERE, reason(GameAction.DemolishBuilding(hex(1))))
        assertEquals(RejectionReason.NOT_YOUR_HEX, reason(GameAction.DemolishBuilding(hex(4))))
    }

    @Test
    fun `demolishing a bridge reverts the hex to neutral sea and cuts the chain`() {
        val engine = GameEngine(strip(9, 0..2, 6..8).withSea(listOf(hex(3), hex(4), hex(5))))
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(4))) is LegalityResult.Ok)
        assertTrue(engine.submit(GameAction.DemolishBuilding(hex(3))) is LegalityResult.Ok)
        val s = engine.state.value
        val razed = s.tiles.getValue(hex(3))
        assertEquals(null, razed.building)
        assertEquals("open neutral water again", null, razed.owner)
        assertEquals(null, razed.bridgeOrientation)
        assertTrue("far span is cut off on the spot", s.tiles.getValue(hex(4)).starving)
        assertInvariants(s)
    }

    @Test
    fun `a bridge carrying a unit refuses to be demolished`() {
        val engine = GameEngine(
            strip(9, 0..2, 6..8)
                .withSea(listOf(hex(3), hex(4), hex(5)))
                .withUnit(owner = 0, tier = 1, at = hex(2)),
        )
        check(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3))) is LegalityResult.Ok)
        val soldier = engine.state.value.unitIdAt(hex(2))
        check(engine.submit(GameAction.MoveUnit(soldier, hex(3))) is LegalityResult.Ok)
        val result = engine.submit(GameAction.DemolishBuilding(hex(3)))
        assertEquals(RejectionReason.HEX_HAS_UNIT, (result as LegalityResult.Rejected).reason)
        assertEquals(Building.BRIDGE, engine.state.value.tiles.getValue(hex(3)).building)
    }

    @Test
    fun `demolishing the expedition port restarves the colony`() {
        // P0 home island q 0..2, overseas colony q 6..7 fed only by its port.
        val s = TestStates.custom(
            owners = mapOf(
                hex(0) to 0, hex(1) to 0, hex(2) to 0,
                hex(6) to 0, hex(7) to 0,
                hex(8) to 1,
            ),
            capital0 = hex(0),
            capital1 = hex(8),
        ).withSea(listOf(hex(3), hex(4), hex(5))).withBuilding(Building.PORT, at = hex(6))
        val (next, _) = Reducer.reduce(s, GameAction.DemolishBuilding(hex(6)))
        assertEquals(null, next.tiles.getValue(hex(6)).building)
        assertTrue("supply line cut on the spot", next.tiles.getValue(hex(6)).starving)
        assertTrue(next.tiles.getValue(hex(7)).starving)
        assertInvariants(next)
    }

    // ----- units -----

    @Test
    fun `disbanding refunds half the unit's cost and leaves no gravestone`() {
        val s = strip(9, 0..5, 6..8).withUnit(owner = 0, tier = 3, at = hex(2))
        val soldier = s.unitIdAt(hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.DisbandUnit(soldier))
        assertEquals(null, next.units[soldier])
        val tile = next.tiles.getValue(hex(2))
        assertEquals(null, tile.unit)
        assertEquals("no gravestone for a voluntary disband", null, tile.flora)
        val expected = rules.unitCost[2] * rules.demolishRefundPercent / 100
        assertEquals(treasury(s) + expected, treasury(next))
        assertTrue(
            events.any { it is GameEvent.UnitDied && it.cause == DeathCause.DISBANDED },
        )
        assertTrue(events.any { it is GameEvent.RefundPaid && it.amount == expected })
        assertInvariants(next)
    }

    @Test
    fun `spent units disband just as well`() {
        val s = strip(9, 0..5, 6..8).withUnit(owner = 0, tier = 1, at = hex(2), spent = true)
        val (next, _) = Reducer.reduce(s, GameAction.DisbandUnit(s.unitIdAt(hex(2))))
        assertEquals(null, next.tiles.getValue(hex(2)).unit)
        assertInvariants(next)
    }

    @Test
    fun `enemy units refuse to disband`() {
        val s = strip(9, 0..2, 6..8).withUnit(owner = 1, tier = 1, at = hex(7))
        val (next, events) = Reducer.reduce(s, GameAction.DisbandUnit(s.unitIdAt(hex(7))))
        assertEquals(s, next)
        assertEquals(
            RejectionReason.NOT_YOUR_UNIT,
            (events.single() as GameEvent.ActionRejected).reason,
        )
    }

    @Test
    fun `a loaded transport refunds boat and cargo together`() {
        val s = strip(9, 0..2, 6..8)
            .withSea(listOf(hex(3), hex(4)))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
            .withCargo(at = hex(3), tier = 2)
        val boat = s.unitIdAt(hex(3))
        val (next, events) = Reducer.reduce(s, GameAction.DisbandUnit(boat))
        val expected =
            (rules.transportCost + rules.unitCost[1]) * rules.demolishRefundPercent / 100
        assertEquals(treasury(s) + expected, treasury(next))
        assertTrue(events.any { it is GameEvent.RefundPaid && it.amount == expected })
        assertEquals("cargo went down with the boat", 0, next.units.size)
        assertInvariants(next)
    }

    @Test
    fun `undo restores building, treasury and unit`() {
        val engine = GameEngine(
            strip(9, 0..5, 6..8)
                .withBuilding(Building.TOWER, at = hex(2))
                .withUnit(owner = 0, tier = 2, at = hex(3)),
        )
        val before = engine.state.value
        check(engine.submit(GameAction.DemolishBuilding(hex(2))) is LegalityResult.Ok)
        check(engine.submit(GameAction.DisbandUnit(before.unitIdAt(hex(3)))) is LegalityResult.Ok)
        assertTrue(engine.undo())
        assertTrue(engine.undo())
        assertEquals(before, engine.state.value)
    }

    // ----- campaign scoreboard -----

    @Test
    fun `a voluntary disband is not a campaign unit loss`() {
        val s = strip(9, 0..2, 6..8).withUnit(owner = 0, tier = 1, at = hex(1))
        val result = Reducer.reduce(s, GameAction.DisbandUnit(s.unitIdAt(hex(1))))
        val tracker = com.msa.fightandconquer.core.campaign.CampaignTracker.step(
            com.msa.fightandconquer.core.campaign.CampaignTracker(),
            s,
            result.state,
            result.events,
            PlayerId(0),
            emptyList(),
        )
        assertEquals(0, tracker.unitsLost)
        assertFalse(result.events.none { it is GameEvent.UnitDied })
    }
}
