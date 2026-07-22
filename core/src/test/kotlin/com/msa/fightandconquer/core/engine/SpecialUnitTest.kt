package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcherTest {

    @Test
    fun `archer aura defends its hex and adjacent own hexes at aura level`() {
        // P1 archer at hex 7: hexes 6,7,8 (all P1) defend at archerAuraDefense.
        val s = strip(9, 0..2, 6..8).withUnit(owner = 1, tier = 1, at = hex(7), type = UnitType.ARCHER)
        val rules = s.config.rules
        assertEquals(rules.archerAuraDefense, Rules.defenseOf(s, hex(7)))
        assertEquals(rules.archerAuraDefense, Rules.defenseOf(s, hex(6)))
        assertEquals(rules.archerAuraDefense, Rules.defenseOf(s, hex(8)))
        // The aura never covers neutral land.
        assertEquals(0, Rules.defenseOf(s, hex(5)))
    }

    @Test
    fun `tier two breaks the aura but tier one cannot`() {
        val s = strip(9, 0..5, 6..8)
            .withUnit(owner = 1, tier = 1, at = hex(7), type = UnitType.ARCHER)
            .withUnit(owner = 0, tier = 1, at = hex(5))
        val weak = s.unitIdAt(hex(5))
        val rejected = Reducer.reduce(s, GameAction.MoveUnit(weak, hex(6)))
        assertEquals(
            RejectionReason.DESTINATION_UNREACHABLE,
            rejected.events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
        )

        val s2 = strip(9, 0..5, 6..8)
            .withUnit(owner = 1, tier = 1, at = hex(7), type = UnitType.ARCHER)
            .withUnit(owner = 0, tier = 3, at = hex(5))
        val strong = s2.unitIdAt(hex(5))
        val (next, events) = Reducer.reduce(s2, GameAction.MoveUnit(strong, hex(6)))
        assertTrue(events.any { it is GameEvent.HexCaptured })
        assertEquals(PlayerId(0), next.tiles.getValue(hex(6)).owner)
        assertInvariants(next)
    }

    @Test
    fun `archer attacks at its own strength not its aura`() {
        // Defense 1 (enemy peasant adjacent): archer strength 1 is NOT strictly greater.
        val s = strip(9, 0..5, 6..8)
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.ARCHER)
            .withUnit(owner = 1, tier = 1, at = hex(7))
        val archer = s.unitIdAt(hex(5))
        val reach = Rules.reachable(s, archer)
        assertFalse(hex(6) in reach.captureTargets)
    }
}

class CatapultTest {

    @Test
    fun `catapult ignores building defense but not unit defense`() {
        // Strong tower on hex 6 covers itself at 3 — a catapult (strength 2) still takes it.
        val s = strip(9, 0..5, 6..8)
            .withBuilding(Building.STRONG_TOWER, at = hex(6))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.CATAPULT)
        val catapult = s.unitIdAt(hex(5))
        assertTrue(hex(6) in Rules.reachable(s, catapult).captureTargets)
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(catapult, hex(6)))
        assertTrue(events.any { it is GameEvent.BuildingDestroyed && it.building == Building.STRONG_TOWER })
        assertEquals(PlayerId(0), next.tiles.getValue(hex(6)).owner)
        assertInvariants(next)

        // A tier-2 defender next to the target outguns the catapult (2 is not > 2).
        val defended = strip(9, 0..5, 6..8)
            .withBuilding(Building.STRONG_TOWER, at = hex(6))
            .withUnit(owner = 1, tier = 2, at = hex(7))
            .withUnit(owner = 0, tier = 1, at = hex(5), type = UnitType.CATAPULT)
        val catapult2 = defended.unitIdAt(hex(5))
        assertFalse(hex(6) in Rules.reachable(defended, catapult2).captureTargets)
    }

    @Test
    fun `catapult movement is range capped`() {
        val s = strip(12, 0..9, 10..11)
            .withUnit(owner = 0, tier = 1, at = hex(1), type = UnitType.CATAPULT)
        val catapult = s.unitIdAt(hex(1))
        val reach = Rules.reachable(s, catapult)
        assertTrue(hex(3) in reach.moveTargets) // distance 2: fine
        assertFalse(hex(4) in reach.moveTargets) // distance 3: out of range
        val rejected = Reducer.reduce(s, GameAction.MoveUnit(catapult, hex(4)))
        assertEquals(
            RejectionReason.DESTINATION_UNREACHABLE,
            rejected.events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
        )
    }
}

class SpecialUnitRulesTest {

    @Test
    fun `specials never merge by any path`() {
        val s = strip(9, 0..5, 6..8)
            .withUnit(owner = 0, tier = 1, at = hex(1), type = UnitType.ARCHER)
            .withUnit(owner = 0, tier = 1, at = hex(3))
        val archer = s.unitIdAt(hex(1))
        val peasant = s.unitIdAt(hex(3))

        // Explicit merge in both directions.
        for (action in listOf(GameAction.MergeUnits(archer, peasant), GameAction.MergeUnits(peasant, archer))) {
            val (_, events) = Reducer.reduce(s, action)
            assertEquals(
                RejectionReason.CANNOT_MERGE_SPECIAL,
                events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
            )
        }
        // Buy-merge onto a special, and buying a special onto a soldier.
        val onArcher = Reducer.reduce(s, GameAction.BuyUnit(1, hex(1)))
        assertEquals(
            RejectionReason.CANNOT_MERGE_SPECIAL,
            onArcher.events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
        )
        val archerOnPeasant = Reducer.reduce(s, GameAction.BuyUnit(1, hex(3), UnitType.ARCHER))
        assertEquals(
            RejectionReason.CANNOT_MERGE_SPECIAL,
            archerOnPeasant.events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
        )
        // Specials never appear as merge targets.
        assertTrue(Rules.reachable(s, peasant).mergeTargets.isEmpty())
    }

    @Test
    fun `special tier must be one and the flag gates them`() {
        val s = strip(9, 0..2, 6..8)
        val badTier = Reducer.reduce(s, GameAction.BuyUnit(2, hex(1), UnitType.ARCHER))
        assertEquals(
            RejectionReason.INVALID_TIER,
            badTier.events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
        )
        val disabled = strip(9, 0..2, 6..8, rules = RuleConstants(specialUnitsEnabled = false))
        val rejected = Reducer.reduce(disabled, GameAction.BuyUnit(1, hex(1), UnitType.CATAPULT))
        assertEquals(
            RejectionReason.SPECIAL_UNITS_DISABLED,
            rejected.events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
        )
    }

    @Test
    fun `special upkeep flows into turn economy and bankruptcy`() {
        // P1: 3 hexes income, catapult upkeep 10, treasury 5 -> 5 + 3 - 10 = -2 -> bankruptcy.
        val s = strip(9, 0..2, 6..8)
            .withTreasury(1, 5)
            .withUnit(owner = 1, tier = 1, at = hex(7), type = UnitType.CATAPULT)
        assertEquals(s.config.rules.catapultUpkeep, Rules.upkeepOf(s, PlayerId(1)))
        val (next, events) = Reducer.reduce(s, GameAction.EndTurn)
        assertTrue(events.any { it is GameEvent.Bankruptcy })
        assertTrue(next.units.values.none { it.owner == PlayerId(1) })
        assertInvariants(next)
    }

    @Test
    fun `buy capture with a catapult ignores buildings`() {
        // Tower-covered neutral-owned... enemy hex adjacent to P0 land: catapult buy-captures it.
        val s = strip(9, 0..5, 6..8).withBuilding(Building.TOWER, at = hex(6))
        val (next, events) = Reducer.reduce(s, GameAction.BuyUnit(1, hex(6), UnitType.CATAPULT))
        assertTrue(events.any { it is GameEvent.HexCaptured })
        assertTrue(events.any { it is GameEvent.BuildingDestroyed })
        val unit = next.units.values.single { it.owner == PlayerId(0) && it.type == UnitType.CATAPULT }
        assertEquals(hex(6), unit.hex)
        assertTrue(unit.spent)
        assertInvariants(next)
    }
}
