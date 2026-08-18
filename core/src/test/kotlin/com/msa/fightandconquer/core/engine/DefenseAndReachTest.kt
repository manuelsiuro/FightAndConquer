package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withFlora
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefenseTest {

    // strip: P0 owns 0..2 (capital 0), neutral 3..5, P1 owns 6..8 (capital 8)
    private val base = strip(9, 0..2, 6..8)

    @Test
    fun `bare neutral hex defends at zero`() {
        assertEquals(0, Rules.defenseOf(base, hex(4)))
    }

    @Test
    fun `unit on hex defends with its tier`() {
        val s = base.withUnit(owner = 1, tier = 2, at = hex(7))
        assertEquals(2, Rules.defenseOf(s, hex(7)))
    }

    @Test
    fun `adjacent friendly unit protects a hex`() {
        val s = base.withUnit(owner = 1, tier = 3, at = hex(7))
        assertEquals(3, Rules.defenseOf(s, hex(6)))
    }

    @Test
    fun `enemy units do not protect a hex they do not own`() {
        // P0 unit adjacent to neutral hex 3 gives it no defense
        val s = base.withUnit(owner = 0, tier = 4, at = hex(2))
        assertEquals(0, Rules.defenseOf(s, hex(3)))
    }

    @Test
    fun `tower protects itself and adjacent own hexes`() {
        val s = base.withBuilding(Building.TOWER, at = hex(7))
        assertEquals(2, Rules.defenseOf(s, hex(7)))
        assertEquals(2, Rules.defenseOf(s, hex(6)))
        // hex 8 also adjacent — capital(1) vs tower(2): tower wins
        assertEquals(2, Rules.defenseOf(s, hex(8)))
    }

    @Test
    fun `strong tower defends at 3`() {
        val s = base.withBuilding(Building.STRONG_TOWER, at = hex(7))
        assertEquals(3, Rules.defenseOf(s, hex(7)))
        assertEquals(3, Rules.defenseOf(s, hex(6)))
    }

    @Test
    fun `capital defends itself and neighbors at 1`() {
        assertEquals(1, Rules.defenseOf(base, hex(8)))
        assertEquals(1, Rules.defenseOf(base, hex(7)))
        assertEquals(0, Rules.defenseOf(base, hex(6)))
    }

    @Test
    fun `defense takes the max of all contributors`() {
        val s = base
            .withBuilding(Building.TOWER, at = hex(7))
            .withUnit(owner = 1, tier = 4, at = hex(6))
        // hex 7: tower(2) on it, knight(4) adjacent
        assertEquals(4, Rules.defenseOf(s, hex(7)))
    }
}

/** [Rules.defenseSourceOf]: which piece explains a hex's defense ("Guarded by Tower"). */
class DefenseSourceTest {

    // strip: P0 owns 0..2 (capital 0), neutral 3..5, P1 owns 6..8 (capital 8)
    private val base = strip(9, 0..2, 6..8)

    @Test
    fun `a bare neutral hex has no defense source`() {
        assertEquals(null, Rules.defenseSourceOf(base, hex(4)))
        assertEquals(1, Rules.captureRequirement(base, hex(4)))
    }

    @Test
    fun `the garrison is its own source`() {
        val s = base.withUnit(owner = 1, tier = 2, at = hex(7))
        val source = Rules.defenseSourceOf(s, hex(7))
        assertTrue(source is Rules.DefenseSource.Unit && source.unit.hex == hex(7))
        assertEquals(Rules.defenseOf(s, hex(7)) + 1, Rules.captureRequirement(s, hex(7)))
    }

    @Test
    fun `a tower out-guarding the garrison becomes the source`() {
        val s = base
            .withUnit(owner = 1, tier = 1, at = hex(7))
            .withBuilding(Building.TOWER, at = hex(6))
        val source = Rules.defenseSourceOf(s, hex(7))
        assertTrue(source is Rules.DefenseSource.Fortification && source.building == Building.TOWER)
        assertEquals(3, Rules.captureRequirement(s, hex(7))) // tower 2, strictly greater
    }

    @Test
    fun `on a tie the garrison wins over adjacent cover`() {
        // Peasant (1) on hex 7, capital at 8 also covers it at 1.
        val s = base.withUnit(owner = 1, tier = 1, at = hex(7))
        val source = Rules.defenseSourceOf(s, hex(7))
        assertTrue(source is Rules.DefenseSource.Unit && source.unit.hex == hex(7))
    }

    @Test
    fun `an archer's aura guards the neighbor hex`() {
        val s = base.withUnit(owner = 1, tier = 1, at = hex(7), type = UnitType.ARCHER)
        val source = Rules.defenseSourceOf(s, hex(6))
        assertTrue(source is Rules.DefenseSource.Unit && source.unit.type == UnitType.ARCHER)
    }

    @Test
    fun `a catapult attacker sees no building source`() {
        val s = base.withBuilding(Building.TOWER, at = hex(7))
        val plain = Rules.defenseSourceOf(s, hex(6))
        assertTrue(plain is Rules.DefenseSource.Fortification && plain.building == Building.TOWER)
        assertEquals(null, Rules.defenseSourceOf(s, hex(6), UnitType.CATAPULT))
    }

    @Test
    fun `the source always agrees with defenseOf`() {
        val rules = RuleConstants()
        val s = base
            .withBuilding(Building.STRONG_TOWER, at = hex(6))
            .withUnit(owner = 1, tier = 1, at = hex(7), type = UnitType.ARCHER)
        for (q in 5..8) {
            val hex = hex(q)
            val value = when (val source = Rules.defenseSourceOf(s, hex)) {
                is Rules.DefenseSource.Unit -> Rules.unitDefenseOf(s, source.unit)
                is Rules.DefenseSource.Fortification -> when (source.building) {
                    Building.TOWER -> rules.towerDefense
                    Building.STRONG_TOWER -> rules.strongTowerDefense
                    Building.CAPITAL -> rules.capitalDefense
                    else -> 0
                }
                null -> 0
            }
            assertEquals("source value at $hex", Rules.defenseOf(s, hex), value)
        }
    }
}

/** The per-unit display pair the HUD shows: [Rules.strengthOf] (attack) vs [Rules.unitDefenseOf]. */
class UnitDefenseValueTest {

    private val rules = RuleConstants()
    private val base = strip(9, 0..2, 6..8)

    @Test
    fun `a soldier defends at its tier`() {
        for (tier in 1..rules.maxTier) {
            val s = base.withUnit(owner = 0, tier = tier, at = hex(1))
            assertEquals(tier, Rules.unitDefenseOf(s, s.unitAt(hex(1))!!))
        }
    }

    @Test
    fun `an archer attacks at 1 but defends at its aura value`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1), type = UnitType.ARCHER)
        val archer = s.unitAt(hex(1))!!
        assertEquals(rules.archerStrength, Rules.strengthOf(s, archer))
        assertEquals(rules.archerAuraDefense, Rules.unitDefenseOf(s, archer))
    }

    @Test
    fun `a catapult defends at its strength`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1), type = UnitType.CATAPULT)
        assertEquals(rules.catapultStrength, Rules.unitDefenseOf(s, s.unitAt(hex(1))!!))
    }

    @Test
    fun `a transport defends at zero and a warship at its sink threshold`() {
        val s = base
            .withSea(listOf(hex(1, -1), hex(2, -1)))
            .withUnit(owner = 0, tier = 1, at = hex(1, -1), type = UnitType.TRANSPORT)
            .withUnit(owner = 0, tier = 1, at = hex(2, -1), type = UnitType.WARSHIP)
        assertEquals(0, Rules.unitDefenseOf(s, s.unitAt(hex(1, -1))!!))
        assertEquals(rules.warshipStrength, Rules.unitDefenseOf(s, s.unitAt(hex(2, -1))!!))
    }

    @Test
    fun `buyDefense mirrors unitDefenseOf for units that do not exist yet`() {
        assertEquals(3, Rules.buyDefense(base, base.currentPlayer, 3, UnitType.SOLDIER))
        assertEquals(rules.archerAuraDefense, Rules.buyDefense(base, base.currentPlayer, 1, UnitType.ARCHER))
        assertEquals(0, Rules.buyDefense(base, base.currentPlayer, 1, UnitType.TRANSPORT))
    }

    @Test
    fun `boats still garrison nothing - unitDefenseOf is a display value, not a land contribution`() {
        // Guards the defenseContribution refactor: the warship's sink threshold (2)
        // must never leak into hex defense.
        val s = base.withSea(hex(2, -1)).withUnit(owner = 0, tier = 1, at = hex(2, -1), type = UnitType.WARSHIP)
        val warship = s.unitAt(hex(2, -1))!!
        assertEquals(rules.warshipStrength, Rules.unitDefenseOf(s, warship))
        assertEquals(0, Rules.defenseContribution(s, warship))
        assertEquals(0, Rules.defenseOf(s, hex(2)))
    }
}

class ReachabilityTest {

    private val base = strip(9, 0..2, 6..8)

    @Test
    fun `fresh unit reaches its whole region and the frontier`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1))
        val reach = Rules.reachable(s, s.unitIdAt(hex(1)))
        // move: hex 2 (hex 0 has capital building, own hex 1 is self)
        assertEquals(setOf(hex(2)), reach.moveTargets)
        // capture: neutral hex 3 (defense 0 < 1)
        assertEquals(setOf(hex(3)), reach.captureTargets)
        assertTrue(reach.mergeTargets.isEmpty())
    }

    @Test
    fun `peasant cannot capture a defended hex - strictly greater required`() {
        // P1 spearman on 7 defends 6 at 2; also capital at 8 defends 7&8 at 1.
        val s = strip(9, 0..5, 6..8) // P0 pushed up to hex 5, frontier is 6
            .withUnit(owner = 0, tier = 2, at = hex(5))
            .withUnit(owner = 1, tier = 2, at = hex(7))
        val spearman = s.unitIdAt(hex(5))
        val reach = Rules.reachable(s, spearman)
        // hex 6 defense = adjacent spearman (2); 2 > 2 is false
        assertFalse(hex(6) in reach.captureTargets)

        val s2 = strip(9, 0..5, 6..8)
            .withUnit(owner = 0, tier = 3, at = hex(5))
            .withUnit(owner = 1, tier = 2, at = hex(7))
        val baron = s2.unitIdAt(hex(5))
        assertTrue(hex(6) in Rules.reachable(s2, baron).captureTargets)
    }

    @Test
    fun `spent unit reaches nothing`() {
        val s = base.withUnit(owner = 0, tier = 1, at = hex(1), spent = true)
        assertEquals(ReachResult.EMPTY, Rules.reachable(s, s.unitIdAt(hex(1))))
    }

    @Test
    fun `same tier friendly unit is a merge target not a move target`() {
        val s = base
            .withUnit(owner = 0, tier = 1, at = hex(1))
            .withUnit(owner = 0, tier = 1, at = hex(2))
        val reach = Rules.reachable(s, s.unitIdAt(hex(1)))
        assertEquals(setOf(hex(2)), reach.mergeTargets)
        assertFalse(hex(2) in reach.moveTargets)
    }

    @Test
    fun `movement cannot jump across disconnected regions`() {
        // P0 owns two islands: 0..1 and 4..5 (3 is neutral gap... use 2..3 neutral)
        val tilesSplit = strip(9, 0..1, 7..8)
        val s = tilesSplit.copy(
            tiles = tilesSplit.tiles
                + (hex(4) to tilesSplit.tiles.getValue(hex(4)).copy(owner = com.msa.fightandconquer.core.model.PlayerId(0)))
                + (hex(5) to tilesSplit.tiles.getValue(hex(5)).copy(owner = com.msa.fightandconquer.core.model.PlayerId(0))),
        ).withUnit(owner = 0, tier = 1, at = hex(1))
        val reach = Rules.reachable(s, s.unitIdAt(hex(1)))
        assertFalse(hex(4) in reach.moveTargets)
        assertFalse(hex(5) in reach.moveTargets)
        // but can capture into the gap next to its own region
        assertTrue(hex(2) in reach.captureTargets)
        assertFalse(hex(3) in reach.captureTargets) // not adjacent to unit's region
    }

    @Test
    fun `tree hex in own region is a move target`() {
        val s = base.withFlora(Flora.Tree, at = hex(2)).withUnit(owner = 0, tier = 1, at = hex(1))
        val reach = Rules.reachable(s, s.unitIdAt(hex(1)))
        assertTrue(hex(2) in reach.moveTargets)
    }

    @Test
    fun `fixture invariants hold`() {
        assertInvariants(base.withUnit(owner = 0, tier = 1, at = hex(1)))
    }
}
