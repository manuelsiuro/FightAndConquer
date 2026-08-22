package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withResearch
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.ResearchState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tech
import com.msa.fightandconquer.core.model.Tile
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three research-line buildings (University/Bank/Fortress) and the per-player
 * purchase gates on them plus the gated classics (Strong Tower, Port). Flag-off
 * semantics are pinned in [RuleVariantGateTest].
 */
class ResearchBuildingsTest {

    private val rules = RuleConstants(researchEnabled = true)
    private val base = strip(9, 0..2, 6..8, rules = rules)

    private fun rejected(result: ReduceResult): RejectionReason =
        result.events.filterIsInstance<GameEvent.ActionRejected>().single().reason

    // ----- University: the ungated bootstrap -----

    @Test
    fun `university builds on plain own land and debits its cost`() {
        val (next, events) = Reducer.reduce(base, GameAction.BuyBuilding(BuildingType.UNIVERSITY, hex(1)))
        assertEquals(Building.UNIVERSITY, next.tiles.getValue(hex(1)).building)
        assertEquals(100 - rules.universityCost, next.player(PlayerId(0)).treasury)
        assertTrue(events.any { it is GameEvent.BuildingBuilt && it.building == Building.UNIVERSITY })
        assertInvariants(next)
    }

    @Test
    fun `university needs no tech even with research on`() {
        val engine = GameEngine(base)
        assertTrue(engine.submit(GameAction.BuyBuilding(BuildingType.UNIVERSITY, hex(2))) is LegalityResult.Ok)
    }

    // ----- Unlock gates -----

    @Test
    fun `gated buildings reject without their tech and pass with it`() {
        val gates = mapOf(
            BuildingType.STRONG_TOWER to ResearchState.of(listOf(Tech.MASONRY)),
            BuildingType.BANK to ResearchState.of(listOf(Tech.COINAGE, Tech.BANKING)),
            BuildingType.FORTRESS to ResearchState.of(listOf(Tech.MASONRY, Tech.ENGINEERING)),
        )
        for ((type, research) in gates) {
            val (_, events) = Reducer.reduce(base, GameAction.BuyBuilding(type, hex(1)))
            assertEquals(
                "locked $type",
                RejectionReason.BUILDING_NEEDS_RESEARCH,
                events.filterIsInstance<GameEvent.ActionRejected>().single().reason,
            )
            val unlocked = base.withResearch(0, research)
            assertTrue(
                "unlocked $type",
                Legality.check(unlocked, GameAction.BuyBuilding(type, hex(1))) is LegalityResult.Ok,
            )
        }
    }

    @Test
    fun `port needs navigation when research is on`() {
        val coastal = base.withSea(hex(3))
        assertEquals(
            RejectionReason.BUILDING_NEEDS_RESEARCH,
            rejected(Reducer.reduce(coastal, GameAction.BuyBuilding(BuildingType.PORT, hex(2)))),
        )
        val navigator = coastal.withResearch(0, ResearchState.of(listOf(Tech.NAVIGATION)))
        assertTrue(Legality.check(navigator, GameAction.BuyBuilding(BuildingType.PORT, hex(2))) is LegalityResult.Ok)
    }

    @Test
    fun `research is per player - an opponent's tech unlocks nothing for me`() {
        val state = base.withResearch(1, ResearchState.of(listOf(Tech.MASONRY)))
        assertEquals(
            RejectionReason.BUILDING_NEEDS_RESEARCH,
            rejected(Reducer.reduce(state, GameAction.BuyBuilding(BuildingType.STRONG_TOWER, hex(1)))),
        )
    }

    // ----- Bank income, Fortress defense -----

    @Test
    fun `bank income lands in incomeOf - authored banks earn without the tech`() {
        // withBuilding bypasses purchase legality: the map-authored/captured case.
        // No research completed, so the flat delta is exact under any income scaling.
        val banked = base.withBuilding(Building.BANK, hex(1))
        assertEquals(
            Rules.incomeOf(base, PlayerId(0)) + rules.bankIncome,
            Rules.incomeOf(banked, PlayerId(0)),
        )
    }

    @Test
    fun `fortress defends above the strong tower and yields to catapults`() {
        val fort = base.withBuilding(Building.FORTRESS, hex(1))
        assertEquals(rules.fortressDefense, Rules.defenseOf(fort, hex(1)))
        assertTrue(Rules.defenseOf(fort, hex(1)) > rules.strongTowerDefense)
        assertEquals(0, Rules.defenseOf(fort, hex(1), attackerType = UnitType.CATAPULT))
    }

    @Test
    fun `fortress joins the fortification vision arm`() {
        val fogRules = RuleConstants(researchEnabled = true, fogOfWar = true)
        val state = strip(9, 0..2, 6..8, rules = fogRules).withBuilding(Building.FORTRESS, hex(2))
        val plain = strip(9, 0..2, 6..8, rules = fogRules).withBuilding(Building.BANK, hex(2))
        val fortVision = Rules.visibleHexes(state, PlayerId(0))
        val bankVision = Rules.visibleHexes(plain, PlayerId(0))
        assertTrue(fortVision.size > bankVision.size)
    }

    // ----- Demolition, capture -----

    @Test
    fun `university demolishes for the standard refund`() {
        val standing = base.withBuilding(Building.UNIVERSITY, hex(1))
        assertEquals(
            rules.universityCost * rules.demolishRefundPercent / 100,
            Rules.demolishRefund(standing, PlayerId(0), Building.UNIVERSITY),
        )
        val (next, events) = Reducer.reduce(standing, GameAction.DemolishBuilding(hex(1)))
        assertNull(next.tiles.getValue(hex(1)).building)
        assertTrue(events.any { it is GameEvent.BuildingDestroyed })
    }

    @Test
    fun `capturing a hex razes the standing university`() {
        val state = base
            .copy(tiles = base.tiles + (hex(3) to Tile(owner = PlayerId(1), building = Building.UNIVERSITY)))
            .withUnit(0, 1, hex(2))
        val (next, events) = Reducer.reduce(state, GameAction.MoveUnit(state.unitIdAt(hex(2)), hex(3)))
        assertEquals(PlayerId(0), next.tiles.getValue(hex(3)).owner)
        assertNull(next.tiles.getValue(hex(3)).building)
        assertTrue(events.any { it is GameEvent.BuildingDestroyed && it.building == Building.UNIVERSITY })
        assertInvariants(next)
    }

    // ----- The tray: locked cards -----

    @Test
    fun `buyableAt offers locked cards only where the building could stand`() {
        val engine = GameEngine(base)
        val options = engine.buyableAt(hex(1)).filterIsInstance<PurchaseOption.Structure>()
        val byType = options.associateBy { it.type }

        assertNull("university is never tech-gated", byType.getValue(BuildingType.UNIVERSITY).lockedByTech)
        assertEquals(Tech.MASONRY, byType.getValue(BuildingType.STRONG_TOWER).lockedByTech)
        assertEquals(Tech.BANKING, byType.getValue(BuildingType.BANK).lockedByTech)
        assertEquals(Tech.ENGINEERING, byType.getValue(BuildingType.FORTRESS).lockedByTech)
        // Inland hex: no locked PORT card — the placement probe filters it.
        assertNull(byType[BuildingType.PORT])
    }

    @Test
    fun `a completed tech turns its locked card into a normal offer`() {
        val engine = GameEngine(base.withResearch(0, ResearchState.of(listOf(Tech.MASONRY))))
        val strongTower = engine.buyableAt(hex(1))
            .filterIsInstance<PurchaseOption.Structure>()
            .single { it.type == BuildingType.STRONG_TOWER }
        assertNull(strongTower.lockedByTech)
        assertEquals(rules.strongTowerCost, strongTower.cost)
    }

    @Test
    fun `locked port card appears on the coast only`() {
        val engine = GameEngine(base.withSea(hex(3)))
        val coastal = engine.buyableAt(hex(2)).filterIsInstance<PurchaseOption.Structure>()
        assertEquals(
            Tech.NAVIGATION,
            coastal.single { it.type == BuildingType.PORT }.lockedByTech,
        )
    }
}
