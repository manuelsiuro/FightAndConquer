package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI under the muster gate: Tiers solves through what the halls allow,
 * MoveGenerator never proposes a doomed recruit, and flag-off stays the exact
 * pre-muster candidate stream (the dormant-identity contract).
 */
class MilitaryAiTest {

    private val gatedRules = RuleConstants(militaryBuildingsRequired = true)

    private fun wide(rules: RuleConstants = gatedRules): GameState =
        strip(30, 0..12, 27..29, rules = rules)

    // ----- Tiers -----

    @Test
    fun `gated solvers skip locked tiers and the ungated probe sees demand`() {
        val s = wide()
        val me = s.currentPlayer
        // Defense 2 needs tier 3 — locked without a Barracks.
        assertEquals(null, Tiers.cheapestBreaker(s, me, defense = 2))
        assertEquals(3, Tiers.cheapestBreaker(s, me, defense = 2, gated = false))
        assertEquals(1, Tiers.maxRecruitable(s, me))
        val armed = s.withBuilding(Building.BARRACKS, hex(1))
        assertEquals(3, Tiers.cheapestBreaker(armed, me, defense = 2))
        assertEquals(3, Tiers.maxRecruitable(armed, me))
        assertEquals(4, Tiers.maxRecruitable(armed.withBuilding(Building.FORTRESS, hex(2)), me))
    }

    // ----- MoveGenerator -----

    @Test
    fun `no tier-2+ buys or special buys are generated without the halls`() {
        val s = wide().withTreasury(0, 500)
        val candidates = MoveGenerator.candidates(s, com.msa.fightandconquer.core.model.Difficulty.HARD)
        val buys = candidates.filterIsInstance<GameAction.BuyUnit>()
        assertTrue("no gated soldier buys", buys.none { it.type == UnitType.SOLDIER && it.tier > 1 })
        assertTrue("no archer buys", buys.none { it.type == UnitType.ARCHER })
        assertTrue("no catapult buys", buys.none { it.type == UnitType.CATAPULT })
        assertTrue("no merges past the gate", candidates.none { it is GameAction.MergeUnits })
    }

    // ----- MilitaryPolicy -----

    /** An enemy tower on the frontier: cracking it needs tier 3 — hall demand. */
    private fun GameState.withEnemyTower(at: com.msa.fightandconquer.core.hex.Hex): GameState =
        copy(
            tiles = tiles + (
                at to tiles.getValue(at).copy(
                    owner = com.msa.fightandconquer.core.model.PlayerId(1),
                    building = Building.TOWER,
                )
                ),
        )

    @Test
    fun `a fortified frontier makes every difficulty found a barracks`() {
        val s = wide().withEnemyTower(hex(13))
        for (difficulty in listOf(
            com.msa.fightandconquer.core.model.Difficulty.EASY,
            com.msa.fightandconquer.core.model.Difficulty.NORMAL,
            com.msa.fightandconquer.core.model.Difficulty.HARD,
        )) {
            val action = MilitaryPolicy.action(s, difficulty)
            assertTrue(
                "$difficulty founds a barracks, got $action",
                action is GameAction.BuyBuilding &&
                    action.type == com.msa.fightandconquer.core.model.BuildingType.BARRACKS,
            )
        }
    }

    @Test
    fun `with the barracks standing the walled town triggers the siege workshop`() {
        val s = wide().withEnemyTower(hex(13)).withBuilding(Building.BARRACKS, hex(1))
        val action = MilitaryPolicy.action(s, com.msa.fightandconquer.core.model.Difficulty.HARD)
        assertTrue(
            "workshop next, got $action",
            action is GameAction.BuyBuilding &&
                action.type == com.msa.fightandconquer.core.model.BuildingType.SIEGE_WORKSHOP,
        )
        // And with the workshop standing, the greedy loop proposes the catapult.
        val armed = s.withBuilding(Building.SIEGE_WORKSHOP, hex(2)).withTreasury(0, 200)
        val buys = MoveGenerator.candidates(armed, com.msa.fightandconquer.core.model.Difficulty.HARD)
            .filterIsInstance<GameAction.BuyUnit>()
        assertTrue("catapult candidates appear", buys.any { it.type == UnitType.CATAPULT })
    }

    @Test
    fun `a sea-locked realm founds the barracks its marines wait on`() {
        // P0's island: hex 6 turned to open sea severs the only land route.
        val s = strip(13, 0..5, 7..12, rules = gatedRules)
            .let { base ->
                base.copy(
                    tiles = base.tiles + (
                        hex(6) to com.msa.fightandconquer.core.model.Tile(
                            terrain = com.msa.fightandconquer.core.model.Terrain.SEA,
                        )
                        ),
                )
            }
        val action = MilitaryPolicy.action(s, com.msa.fightandconquer.core.model.Difficulty.HARD)
        assertTrue(
            "overseas war demands the hall, got $action",
            action is GameAction.BuyBuilding &&
                action.type == com.msa.fightandconquer.core.model.BuildingType.BARRACKS,
        )
    }

    @Test
    fun `the policy is null flag-off and never proposes an illegal action`() {
        assertEquals(
            null,
            MilitaryPolicy.action(wide(RuleConstants()), com.msa.fightandconquer.core.model.Difficulty.HARD),
        )
        for (fixture in listOf(
            wide(),
            wide().withEnemyTower(hex(13)),
            wide().withEnemyTower(hex(13)).withBuilding(Building.BARRACKS, hex(1)),
        )) {
            for (difficulty in com.msa.fightandconquer.core.model.Difficulty.entries) {
                val action = MilitaryPolicy.action(fixture, difficulty) ?: continue
                assertTrue(
                    "$difficulty proposed illegal $action",
                    com.msa.fightandconquer.core.engine.Legality.check(fixture, action)
                        is com.msa.fightandconquer.core.engine.LegalityResult.Ok,
                )
            }
        }
    }

    @Test
    fun `flag off keeps the candidate stream byte-identical`() {
        val flagOff = wide(RuleConstants())
        val withField = wide(RuleConstants(militaryBuildingsRequired = false))
        assertEquals(
            MoveGenerator.candidates(flagOff, com.msa.fightandconquer.core.model.Difficulty.HARD),
            MoveGenerator.candidates(withField, com.msa.fightandconquer.core.model.Difficulty.HARD),
        )
    }
}
