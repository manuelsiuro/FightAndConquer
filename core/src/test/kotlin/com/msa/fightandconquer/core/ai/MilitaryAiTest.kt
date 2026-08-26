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
