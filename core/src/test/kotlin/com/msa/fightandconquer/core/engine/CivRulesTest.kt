package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withDeposit
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Civ deltas flowing through the Rules accessors and the engine (phase 2). */
class CivRulesTest {

    private val rules = RuleConstants()

    // ----- Vikings: warship strength -----

    /** Warship (P0) on sea at (2,-1), P1 tower on the adjacent coast at (2,0). */
    private fun bombardState(civs: List<Civilization>?) =
        strip(4, 0..0, 2..3, civs = civs)
            .withSea(hex(2, -1))
            .withBuilding(Building.TOWER, hex(2))
            .withUnit(0, 1, hex(2, -1), type = UnitType.WARSHIP)

    @Test
    fun `a Viking warship has strength 3 and bombards through a tower a Kingdom one cannot`() {
        val kingdom = bombardState(civs = null)
        val viking = bombardState(civs = listOf(Civilization.VIKINGS, Civilization.KINGDOM))
        assertEquals(2, Rules.strengthOf(kingdom, kingdom.unitAt(hex(2, -1))!!))
        assertEquals(3, Rules.strengthOf(viking, viking.unitAt(hex(2, -1))!!))

        // Tower hex defends at 2: the Kingdom hull ties and is refused...
        val bombard = GameAction.Bombard(kingdom.unitIdAt(hex(2, -1)), hex(2))
        val rejected = Legality.check(kingdom, bombard)
        assertTrue(rejected is LegalityResult.Rejected &&
            rejected.reason == RejectionReason.DEFENSE_TOO_HIGH)
        // ...while the Viking hull outguns it.
        assertEquals(LegalityResult.Ok, Legality.check(viking, bombard))
    }

    @Test
    fun `a Viking warship as DEFENDER cannot be sunk by a Kingdom warship`() {
        fun duel(civs: List<Civilization>?) =
            strip(4, 0..0, 2..3, civs = civs)
                .withSea(listOf(hex(2, -1), hex(3, -1)))
                .withUnit(0, 1, hex(2, -1), type = UnitType.WARSHIP)
                .withUnit(1, 1, hex(3, -1), type = UnitType.WARSHIP)

        // Equal hulls: naval ties go to the attacker — the kill is on the table.
        val mirror = duel(civs = null)
        assertTrue(
            hex(3, -1) in Rules.reachable(mirror, mirror.unitIdAt(hex(2, -1))).captureTargets,
        )
        // A Viking defender outguns the Kingdom attacker: no kill.
        val vikingDefender = duel(civs = listOf(Civilization.KINGDOM, Civilization.VIKINGS))
        assertFalse(
            hex(3, -1) in Rules.reachable(vikingDefender, vikingDefender.unitIdAt(hex(2, -1))).captureTargets,
        )
        // Mirror the civs: the Viking attacker sinks the Kingdom hull.
        val vikingAttacker = duel(civs = listOf(Civilization.VIKINGS, Civilization.KINGDOM))
        assertTrue(
            hex(3, -1) in Rules.reachable(vikingAttacker, vikingAttacker.unitIdAt(hex(2, -1))).captureTargets,
        )
    }

    // ----- Shogunate: catapult range -----

    @Test
    fun `a Shogunate catapult reaches range 3`() {
        fun state(civs: List<Civilization>?) =
            strip(6, 0..4, 5..5, civs = civs).withUnit(0, 1, hex(1), type = UnitType.CATAPULT)

        val kingdom = state(civs = null)
        assertEquals(rules.catapultMoveRange, Rules.moveRangeOf(kingdom, kingdom.unitAt(hex(1))!!))
        assertFalse(hex(4) in Rules.reachable(kingdom, kingdom.unitIdAt(hex(1))).moveTargets)

        val shogun = state(civs = listOf(Civilization.SHOGUNATE, Civilization.KINGDOM))
        assertEquals(rules.catapultMoveRange + 1, Rules.moveRangeOf(shogun, shogun.unitAt(hex(1))!!))
        assertTrue(hex(4) in Rules.reachable(shogun, shogun.unitIdAt(hex(1))).moveTargets)
    }

    // ----- Building prices -----

    @Test
    fun `buildingCost prices each player at their own civ`() {
        val s = strip(6, 0..2, 3..5, civs = listOf(Civilization.SHOGUNATE, Civilization.SULTANATE))
        // Shogunate towers are cheap; its markets stay at list price.
        assertEquals(rules.towerCost - 3, Rules.buildingCost(s, PlayerId(0), BuildingType.TOWER))
        assertEquals(rules.marketCost, Rules.buildingCost(s, PlayerId(0), BuildingType.MARKET))
        // The Sultanate across the strip prices the same buildings differently.
        assertEquals(rules.marketCost - 4, Rules.buildingCost(s, PlayerId(1), BuildingType.MARKET))
        assertEquals(rules.towerCost, Rules.buildingCost(s, PlayerId(1), BuildingType.TOWER))
    }

    @Test
    fun `Viking farms escalate faster and Sultanate farms start cheaper`() {
        fun withOneFarm(civs: List<Civilization>?) =
            strip(6, 0..2, 3..5, civs = civs).withBuilding(Building.FARM, hex(1))

        assertEquals(rules.farmCostBase + rules.farmCostStep, Rules.nextFarmCost(withOneFarm(null), PlayerId(0)))
        assertEquals(
            rules.farmCostBase + rules.farmCostStep + 1,
            Rules.nextFarmCost(withOneFarm(listOf(Civilization.VIKINGS, Civilization.KINGDOM)), PlayerId(0)),
        )
        assertEquals(
            rules.farmCostBase - 2 + rules.farmCostStep,
            Rules.nextFarmCost(withOneFarm(listOf(Civilization.SULTANATE, Civilization.KINGDOM)), PlayerId(0)),
        )
    }

    // ----- Special-unit prices and refunds -----

    @Test
    fun `unitCostOf and disbandRefund follow the owner's civ`() {
        val s = strip(6, 0..2, 3..5, civs = listOf(Civilization.VIKINGS, Civilization.SULTANATE))
            .withSea(hex(2, -1))
            .withUnit(0, 1, hex(2, -1), type = UnitType.TRANSPORT)
        // Viking boats are cheap, archers dear; soldier prices stay universal.
        assertEquals(rules.transportCost - 5, Rules.unitCostOf(s, PlayerId(0), 1, UnitType.TRANSPORT))
        assertEquals(rules.archerCost + 4, Rules.unitCostOf(s, PlayerId(0), 1, UnitType.ARCHER))
        assertEquals(rules.unitCost[2], Rules.unitCostOf(s, PlayerId(0), 3, UnitType.SOLDIER))
        // Sultanate shipwrights overcharge for warships.
        assertEquals(rules.warshipCost + 5, Rules.unitCostOf(s, PlayerId(1), 1, UnitType.WARSHIP))
        // The refund halves the OWNER's price, not the list price.
        assertEquals(
            (rules.transportCost - 5) * rules.demolishRefundPercent / 100,
            Rules.disbandRefund(s, s.unitAt(hex(2, -1))!!),
        )
    }

    // ----- Sultanate: mine income -----

    @Test
    fun `Sultanate mine income flows through incomeOf`() {
        fun mined(civs: List<Civilization>?) =
            strip(6, 0..2, 3..5, civs = civs)
                .withDeposit(com.msa.fightandconquer.core.model.Deposit.GOLD_VEIN, hex(1))
                .withBuilding(Building.MINE, hex(1))

        assertEquals(
            Rules.incomeOf(mined(null), PlayerId(0)) + 1,
            Rules.incomeOf(mined(listOf(Civilization.SULTANATE, Civilization.KINGDOM)), PlayerId(0)),
        )
    }

    // ----- Shogunate: archer upkeep -----

    @Test
    fun `a Shogunate archer costs one less upkeep through upkeepOf`() {
        fun archered(civs: List<Civilization>?) =
            strip(6, 0..2, 3..5, civs = civs).withUnit(0, 1, hex(1), type = UnitType.ARCHER)

        assertEquals(rules.archerUpkeep, Rules.upkeepOf(archered(null), PlayerId(0)))
        assertEquals(
            rules.archerUpkeep - 1,
            Rules.upkeepOf(archered(listOf(Civilization.SHOGUNATE, Civilization.KINGDOM)), PlayerId(0)),
        )
    }

    // ----- Gate off: pure identity in play -----

    @Test
    fun `with civBonusesEnabled off every civ plays at list price`() {
        val gated = RuleConstants(civBonusesEnabled = false)
        val s = strip(6, 0..2, 3..5, rules = gated, civs = listOf(Civilization.SHOGUNATE, Civilization.VIKINGS))
        assertEquals(gated.towerCost, Rules.buildingCost(s, PlayerId(0), BuildingType.TOWER))
        assertEquals(gated.archerCost, Rules.unitCostOf(s, PlayerId(1), 1, UnitType.ARCHER))
        assertSameRules(gated, Rules.effectiveRules(s, PlayerId(0)))
    }

    private fun assertSameRules(expected: RuleConstants, actual: RuleConstants) {
        assertTrue("expected the identical rules instance", expected === actual)
    }
}
