package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The muster line (Barracks / Archery Range / Siege Workshop). This file covers
 * the buildings as inert structures; the recruiting gate behind
 * `militaryBuildingsRequired` gets its own sections as the feature lands.
 */
class MilitaryBuildingsTest {

    private val base = strip(9, 0..2, 6..8)
    private val rules = com.msa.fightandconquer.core.model.RuleConstants()

    private val musterCosts = mapOf(
        BuildingType.BARRACKS to rules.barracksCost,
        BuildingType.ARCHERY_RANGE to rules.archeryRangeCost,
        BuildingType.SIEGE_WORKSHOP to rules.siegeWorkshopCost,
    )

    @Test
    fun `each muster building goes on any empty owned hex at its rules cost`() {
        for ((type, cost) in musterCosts) {
            val s = base.withTreasury(0, cost)
            val (next, events) = Reducer.reduce(s, GameAction.BuyBuilding(type, hex(2)))
            assertEquals(type.building, next.tiles.getValue(hex(2)).building)
            assertEquals("$type spends its whole cost", 0, next.player(com.msa.fightandconquer.core.model.PlayerId(0)).treasury)
            assertTrue(events.any { it is GameEvent.BuildingBuilt && it.building == type.building })
            assertInvariants(next)
        }
    }

    @Test
    fun `muster buildings grant no defense - a tier-1 buy-capture takes the hex`() {
        // A tower here would reject the peasant (defense 2); the barracks must not.
        for (building in listOf(Building.BARRACKS, Building.ARCHERY_RANGE, Building.SIEGE_WORKSHOP)) {
            val s = base.withBuilding(building, at = hex(3))
                .copy(tiles = base.tiles.let { it + (hex(3) to it.getValue(hex(3)).copy(owner = com.msa.fightandconquer.core.model.PlayerId(1), building = building)) })
                .withTreasury(0, 100)
            val (next, _) = Reducer.reduce(s, GameAction.BuyUnit(1, hex(3)))
            assertEquals(com.msa.fightandconquer.core.model.PlayerId(0), next.tiles.getValue(hex(3)).owner)
            assertInvariants(next)
        }
    }

    // ----- the recruiting gate (militaryBuildingsRequired = true) -----

    private val gated = strip(
        9, 0..2, 6..8,
        rules = com.msa.fightandconquer.core.model.RuleConstants(militaryBuildingsRequired = true),
    )

    private fun reasonOf(state: com.msa.fightandconquer.core.model.GameState, action: GameAction): RejectionReason {
        val (_, events) = Reducer.reduce(state, action)
        return events.filterIsInstance<GameEvent.ActionRejected>().single().reason
    }

    private fun ok(state: com.msa.fightandconquer.core.model.GameState, action: GameAction) {
        assertTrue("$action should be legal", Legality.check(state, action) is LegalityResult.Ok)
    }

    @Test
    fun `tiers 2 and 3 need a working barracks - tier 1 never does`() {
        ok(gated, GameAction.BuyUnit(1, hex(2)))
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(gated, GameAction.BuyUnit(2, hex(2))))
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(gated, GameAction.BuyUnit(3, hex(2))))
        val withHall = gated.withBuilding(Building.BARRACKS, at = hex(1))
        ok(withHall, GameAction.BuyUnit(2, hex(2)))
        ok(withHall, GameAction.BuyUnit(3, hex(2)))
    }

    @Test
    fun `the knight needs barracks AND fortress`() {
        val withHall = gated.withBuilding(Building.BARRACKS, at = hex(1))
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(withHall, GameAction.BuyUnit(4, hex(2))))
        ok(withHall.withBuilding(Building.FORTRESS, at = hex(0)), GameAction.BuyUnit(4, hex(2)))
        // A fortress alone is not enough either — the barracks is the base school.
        assertEquals(
            RejectionReason.UNIT_NEEDS_BUILDING,
            reasonOf(gated.withBuilding(Building.FORTRESS, at = hex(1)), GameAction.BuyUnit(4, hex(2))),
        )
    }

    @Test
    fun `archer needs a range and catapult a workshop`() {
        assertEquals(
            RejectionReason.UNIT_NEEDS_BUILDING,
            reasonOf(gated, GameAction.BuyUnit(1, hex(2), UnitType.ARCHER)),
        )
        assertEquals(
            RejectionReason.UNIT_NEEDS_BUILDING,
            reasonOf(gated, GameAction.BuyUnit(1, hex(2), UnitType.CATAPULT)),
        )
        ok(
            gated.withBuilding(Building.ARCHERY_RANGE, at = hex(1)),
            GameAction.BuyUnit(1, hex(2), UnitType.ARCHER),
        )
        ok(
            gated.withBuilding(Building.SIEGE_WORKSHOP, at = hex(1)),
            GameAction.BuyUnit(1, hex(2), UnitType.CATAPULT),
        )
    }

    @Test
    fun `boats are never muster-gated`() {
        for (type in listOf(UnitType.TRANSPORT, UnitType.WARSHIP, UnitType.FISHING_BOAT)) {
            assertEquals(emptyList<Building>(), Rules.requiredBuildingsFor(1, type))
        }
    }

    @Test
    fun `buy-merge onto a peasant creates a spearman - so it needs the barracks`() {
        val s = gated.withUnit(owner = 0, tier = 1, at = hex(2))
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(s, GameAction.BuyUnit(1, hex(2))))
        ok(s.withBuilding(Building.BARRACKS, at = hex(1)), GameAction.BuyUnit(1, hex(2)))
    }

    @Test
    fun `merging two peasants needs the barracks and chips filter with it`() {
        val s = gated
            .withUnit(owner = 0, tier = 1, at = hex(1))
            .withUnit(owner = 0, tier = 1, at = hex(2))
        val a = s.unitIdAt(hex(1))
        val b = s.unitIdAt(hex(2))
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(s, GameAction.MergeUnits(a, b)))
        assertTrue(Rules.reachable(s, a).mergeTargets.isEmpty())
        // With a hall standing on a free own hex, the same merge goes through.
        val real = strip(
            12, 0..4, 8..10,
            rules = com.msa.fightandconquer.core.model.RuleConstants(militaryBuildingsRequired = true),
        )
            .withUnit(owner = 0, tier = 1, at = hex(1))
            .withUnit(owner = 0, tier = 1, at = hex(2))
            .withBuilding(Building.BARRACKS, at = hex(3))
        ok(real, GameAction.MergeUnits(real.unitIdAt(hex(1)), real.unitIdAt(hex(2))))
        assertTrue(Rules.reachable(real, real.unitIdAt(hex(1))).mergeTargets.contains(hex(2)))
    }

    @Test
    fun `a starving hall does not count and an opponent's unlocks nothing`() {
        val starving = gated.withBuilding(Building.BARRACKS, at = hex(1)).let { s ->
            s.copy(tiles = s.tiles + (hex(1) to s.tiles.getValue(hex(1)).copy(starving = true)))
        }
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(starving, GameAction.BuyUnit(2, hex(2))))
        val enemyHall = gated.withBuilding(Building.BARRACKS, at = hex(7))
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(enemyHall, GameAction.BuyUnit(2, hex(2))))
    }

    @Test
    fun `gate order is pinned - specials and tier range fire first, cost after`() {
        // SPECIAL_UNITS_DISABLED beats the muster gate.
        val noSpecials = strip(
            9, 0..2, 6..8,
            rules = com.msa.fightandconquer.core.model.RuleConstants(
                militaryBuildingsRequired = true,
                specialUnitsEnabled = false,
            ),
        )
        assertEquals(
            RejectionReason.SPECIAL_UNITS_DISABLED,
            reasonOf(noSpecials, GameAction.BuyUnit(1, hex(2), UnitType.ARCHER)),
        )
        // INVALID_TIER beats the muster gate.
        assertEquals(RejectionReason.INVALID_TIER, reasonOf(gated, GameAction.BuyUnit(5, hex(2))))
        // The muster gate beats CANNOT_AFFORD: the structural refusal is the story.
        val broke = strip(
            9, 0..2, 6..8, treasury = 0,
            rules = com.msa.fightandconquer.core.model.RuleConstants(militaryBuildingsRequired = true),
        )
        assertEquals(RejectionReason.UNIT_NEEDS_BUILDING, reasonOf(broke, GameAction.BuyUnit(2, hex(2))))
        // With the hall standing, poverty is the story again.
        assertEquals(
            RejectionReason.CANNOT_AFFORD,
            reasonOf(broke.withBuilding(Building.BARRACKS, at = hex(1)), GameAction.BuyUnit(2, hex(2))),
        )
    }

    @Test
    fun `flag off is the identity - everything above is legal with no halls`() {
        val off = strip(
            9, 0..2, 6..8,
            rules = com.msa.fightandconquer.core.model.RuleConstants(militaryBuildingsRequired = false),
        )
        ok(off.withTreasury(0, 100), GameAction.BuyUnit(2, hex(2)))
        ok(off.withTreasury(0, 100), GameAction.BuyUnit(1, hex(2), UnitType.ARCHER))
        val s = off
            .withUnit(owner = 0, tier = 1, at = hex(1))
            .withUnit(owner = 0, tier = 1, at = hex(2))
        ok(s, GameAction.MergeUnits(s.unitIdAt(hex(1)), s.unitIdAt(hex(2))))
    }

    @Test
    fun `buyableAt offers muster-locked unit cards only where the unit could stand`() {
        val engine = GameEngine(gated)
        val options = engine.buyableAt(hex(2))
        val units = options.filterIsInstance<PurchaseOption.Unit>()
        assertEquals(
            "tier 1 sells, 2-4 lock, archer and catapult lock",
            setOf(
                PurchaseOption.Unit(1, gated.config.rules.unitCost[0]),
                PurchaseOption.Unit(2, gated.config.rules.unitCost[1], lockedByBuilding = Building.BARRACKS),
                PurchaseOption.Unit(3, gated.config.rules.unitCost[2], lockedByBuilding = Building.BARRACKS),
                PurchaseOption.Unit(4, gated.config.rules.unitCost[3], lockedByBuilding = Building.BARRACKS),
                PurchaseOption.Unit(
                    1, gated.config.rules.archerCost, UnitType.ARCHER,
                    strength = 1, defense = 2, lockedByBuilding = Building.ARCHERY_RANGE,
                ),
                PurchaseOption.Unit(
                    1, gated.config.rules.catapultCost, UnitType.CATAPULT,
                    strength = 2, defense = 2, lockedByBuilding = Building.SIEGE_WORKSHOP,
                ),
            ),
            units.toSet(),
        )
        // A hex that could not host the unit anyway shows no locked card: the
        // enemy capital's defended hex rejects the probe too.
        val enemyOptions = engine.buyableAt(hex(6)).filterIsInstance<PurchaseOption.Unit>()
        assertTrue(enemyOptions.none { it.lockedByBuilding != null && it.tier > 1 })
    }

    @Test
    fun `capturing a muster building destroys it`() {
        val s = base.withBuilding(Building.BARRACKS, at = hex(3))
            .copy(tiles = base.tiles.let { it + (hex(3) to it.getValue(hex(3)).copy(owner = com.msa.fightandconquer.core.model.PlayerId(1), building = Building.BARRACKS)) })
            .withUnit(owner = 0, tier = 1, at = hex(2))
        val id = s.unitIdAt(hex(2))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(id, hex(3)))
        assertNull(next.tiles.getValue(hex(3)).building)
        assertTrue(events.any { it is GameEvent.BuildingDestroyed && it.building == Building.BARRACKS })
        assertInvariants(next)
    }
}
