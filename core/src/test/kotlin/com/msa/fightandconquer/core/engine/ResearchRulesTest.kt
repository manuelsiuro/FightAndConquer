package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withResearch
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.ResearchModifiers
import com.msa.fightandconquer.core.model.ResearchState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tech
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The research modifier layer: per-player deltas resolved through
 * [Rules.effectiveRules], asymmetric by construction — one seat's tech never
 * moves the other seat's numbers.
 */
class ResearchRulesTest {

    private val rules = RuleConstants(researchEnabled = true)
    private val base = strip(9, 0..3, 6..8, rules = rules)

    private fun done(vararg techs: Tech) = ResearchState.of(techs.toList())

    // ----- WAR branch -----

    @Test
    fun `smithing raises attack for its owner only`() {
        val s = base.withResearch(0, done(Tech.SMITHING))
            .withUnit(0, 1, hex(1))
            .withUnit(1, 1, hex(7))
        assertEquals(2, Rules.strengthOf(s, s.units.values.first { it.owner == PlayerId(0) }))
        assertEquals(1, Rules.strengthOf(s, s.units.values.first { it.owner == PlayerId(1) }))
        assertEquals(2, Rules.buyStrength(s, PlayerId(0), 1, UnitType.SOLDIER))
        assertEquals(3, Rules.buyStrength(s, PlayerId(0), 1, UnitType.CATAPULT))
    }

    @Test
    fun `smithing lets a tier-1 soldier meet a capture bar of two`() {
        // Capture requires STRICTLY more than the defense: 1 vs 1 fails, 2 vs 1 takes.
        val guarded = base.copy(
            tiles = base.tiles +
                (hex(4) to com.msa.fightandconquer.core.model.Tile(owner = PlayerId(1), building = Building.CAPITAL)),
        )
        assertEquals(2, Rules.captureRequirement(guarded, hex(4)))
        val smith = guarded.withResearch(0, done(Tech.SMITHING))
        assertEquals(
            Rules.captureRequirement(smith, hex(4)),
            Rules.buyStrength(smith, PlayerId(0), 1, UnitType.SOLDIER),
        )
    }

    @Test
    fun `armory raises garrison defense but never attack`() {
        val s = base.withResearch(0, done(Tech.SMITHING, Tech.ARMORY)).withUnit(0, 2, hex(1))
        val unit = s.units.values.single()
        assertEquals(3, Rules.strengthOf(s, unit)) // tier 2 + SMITHING
        assertEquals(3, Rules.unitDefenseOf(s, unit)) // tier 2 + ARMORY
        assertEquals(3, Rules.defenseOf(s, hex(1))) // garrison contribution carries it
    }

    @Test
    fun `attack research never leaks into defense`() {
        val s = base.withResearch(0, done(Tech.SMITHING)).withUnit(0, 2, hex(1))
        assertEquals(2, Rules.unitDefenseOf(s, s.units.values.single()))
    }

    @Test
    fun `armory does not raise the warship sink threshold - siegecraft does`() {
        assertEquals(
            rules.warshipStrength,
            Rules.buyDefense(base.withResearch(0, done(Tech.SMITHING, Tech.ARMORY)), PlayerId(0), 1, UnitType.WARSHIP),
        )
        assertEquals(
            rules.warshipStrength + 1,
            Rules.buyDefense(
                base.withResearch(0, done(Tech.SMITHING, Tech.ARMORY, Tech.SIEGECRAFT)),
                PlayerId(0),
                1,
                UnitType.WARSHIP,
            ),
        )
    }

    // ----- COIN branch -----

    @Test
    fun `coinage scales total income and treasury supersedes it`() {
        val raw = Rules.incomeOf(base, PlayerId(0))
        val coinage = Rules.incomeOf(base.withResearch(0, done(Tech.COINAGE)), PlayerId(0))
        val treasury = Rules.incomeOf(
            base.withResearch(0, done(Tech.COINAGE, Tech.BANKING, Tech.TREASURY)),
            PlayerId(0),
        )
        assertEquals(raw * 110 / 100, coinage)
        assertEquals(raw * 120 / 100, treasury) // 120, not 130 — replacement, not stacking
    }

    // ----- STONE branch -----

    @Test
    fun `bastions lifts every fortification for its owner`() {
        // One structure per state — defenseOf is a neighborhood max, and a strip
        // puts hex 0's capital beside hex 1, so isolated fixtures keep each
        // building's number readable.
        val bastions = done(Tech.MASONRY, Tech.ENGINEERING, Tech.BASTIONS)
        val tower = base.withBuilding(Building.TOWER, hex(2)).withResearch(0, bastions)
        val fort = base.withBuilding(Building.FORTRESS, hex(2)).withResearch(0, bastions)
        val capitalOnly = base.withResearch(0, bastions)
        assertEquals(rules.towerDefense + 1, Rules.defenseOf(tower, hex(2)))
        assertEquals(rules.fortressDefense + 1, Rules.defenseOf(fort, hex(2)))
        assertEquals(rules.capitalDefense + 1, Rules.defenseOf(capitalOnly, hex(0)))
        // Catapults still ignore every one of them.
        assertEquals(0, Rules.defenseOf(fort, hex(2), attackerType = UnitType.CATAPULT))
        // The un-teched opponent's forts are untouched.
        assertEquals(rules.towerDefense, Rules.defenseOf(base.withBuilding(Building.TOWER, hex(2)), hex(2)))
    }

    // ----- SAIL branch -----

    @Test
    fun `shipwrights cheapens transports with a floor of one`() {
        val s = base.withResearch(0, done(Tech.NAVIGATION, Tech.SHIPWRIGHTS))
        assertEquals(rules.transportCost - 5, Rules.unitCostOf(s, PlayerId(0), 1, UnitType.TRANSPORT))
        val cheapBase = RuleConstants(researchEnabled = true, transportCost = 3)
        val floor = ResearchModifiers.effective(cheapBase, done(Tech.NAVIGATION, Tech.SHIPWRIGHTS))
        assertEquals(1, floor.transportCost)
    }

    @Test
    fun `admiralty raises port and fishery income`() {
        val eff = ResearchModifiers.effective(rules, done(Tech.NAVIGATION, Tech.SHIPWRIGHTS, Tech.ADMIRALTY))
        assertEquals(rules.portIncome + 1, eff.portIncome)
        assertEquals(rules.fisheryShoalIncome + 1, eff.fisheryShoalIncome)
    }

    // ----- Layering, identity, cache -----

    @Test
    fun `civ and research deltas stack`() {
        val s = strip(9, 0..3, 6..8, rules = rules, civs = listOf(Civilization.VIKINGS, Civilization.KINGDOM))
            .withResearch(0, done(Tech.SMITHING, Tech.ARMORY, Tech.SIEGECRAFT))
        // Vikings warship 3, +1 SIEGECRAFT, +1 SMITHING attack = 5.
        assertEquals(5, Rules.buyStrength(s, PlayerId(0), 1, UnitType.WARSHIP))
    }

    @Test
    fun `research off returns the identity even with completed techs`() {
        val off = RuleConstants(researchEnabled = false)
        assertSame(off, ResearchModifiers.effective(off, done(Tech.SMITHING, Tech.COINAGE)))
    }

    @Test
    fun `empty research returns the identity instance`() {
        assertSame(rules, ResearchModifiers.effective(rules, ResearchState()))
    }

    @Test
    fun `two seats with different tech sets resolve independently in one defenseOf`() {
        // P1 garrisons hex 7 with ARMORY; P0 attacks with SMITHING. defenseOf reads
        // the DEFENDER's rules, buyStrength the ATTACKER's — interleaved queries
        // must not cross-contaminate through the cache.
        val s = base
            .withResearch(0, done(Tech.SMITHING))
            .withResearch(1, done(Tech.SMITHING, Tech.ARMORY))
            .withUnit(1, 1, hex(7))
        repeat(3) {
            assertEquals(2, Rules.defenseOf(s, hex(7))) // tier 1 + ARMORY
            assertEquals(2, Rules.buyStrength(s, PlayerId(0), 1, UnitType.SOLDIER))
            assertEquals(2, Rules.buyStrength(s, PlayerId(1), 1, UnitType.SOLDIER))
        }
    }
}
