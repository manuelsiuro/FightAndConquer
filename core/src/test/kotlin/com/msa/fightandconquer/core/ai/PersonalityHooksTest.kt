package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.AiPersonality
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.DiplomacyState
import com.msa.fightandconquer.core.model.Pact
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Tech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The policies must actually read the profile — each knob gets one hook test. */
class PersonalityHooksTest {

    @Test
    fun `a small contested map founds a university the old fixed gate never allowed`() {
        // 8 owned hexes on a 10-land-hex board: under the historical NORMAL gate
        // (12 hexes) research never started here; map-scaled it must.
        val owners = HashMap<Hex, Int?>()
        HexMath.range(Hex.of(0, 0), 1).forEach { owners[it] = 0 } // 7 hexes
        owners[Hex.of(-2, 0)] = 0
        owners[Hex.of(4, 0)] = 1
        owners[Hex.of(5, 0)] = 1
        // Naval off: with boats on, this two-blob board reads sea-locked, which
        // funds the school through the survival branch and not the gate under test.
        val state = TestStates.custom(
            owners,
            capital0 = Hex.of(0, 0),
            capital1 = Hex.of(5, 0),
            treasury = 60,
            rules = com.msa.fightandconquer.core.model.RuleConstants(navalEnabled = false),
        )
        val action = ResearchPolicy.action(state, Difficulty.NORMAL)
        assertTrue(
            "expected a university on the small map, got $action",
            action is GameAction.BuyBuilding && action.type == BuildingType.UNIVERSITY,
        )
    }

    @Test
    fun `research order follows the personality flavor`() {
        val ready = strip(30, 0..12, 27..29)
            .withBuilding(Building.UNIVERSITY, hex(5))
            .withTreasury(0, 200)
        val offense = AiProfile.of(Difficulty.NORMAL, AiPersonality.RAIDER)
        val scholarly = AiProfile.of(Difficulty.NORMAL, AiPersonality.SCHEMER)
        assertEquals(
            GameAction.StartResearch(Tech.SMITHING),
            ResearchPolicy.action(ready, Difficulty.NORMAL, offense),
        )
        assertEquals(
            GameAction.StartResearch(Tech.COINAGE),
            ResearchPolicy.action(ready, Difficulty.NORMAL, scholarly),
        )
    }

    @Test
    fun `the schemer betrays inside the band the neutral profile still honors`() {
        // P0 towers over P1 at ~1.7x power in a two-player pact: the schemer's
        // 1.5x band opens, the neutral 2.0x band stays shut, the turtle's never.
        val base = strip(20, 0..12, 13..19)
            .withUnit(owner = 0, tier = 2, at = hex(2))
            .withUnit(owner = 1, tier = 1, at = hex(15))
        val pacted = base.copy(
            diplomacy = DiplomacyState(pacts = listOf(Pact(PlayerId(0), PlayerId(1), expiresAtRound = 50))),
        )
        val schemer = AiProfile.of(Difficulty.HARD, AiPersonality.SCHEMER)
        val turtle = AiProfile.of(Difficulty.HARD, AiPersonality.TURTLE)
        assertEquals(
            setOf(PlayerId(1)),
            DiplomacyPolicy.betrayalTargets(pacted, PlayerId(0), schemer),
        )
        assertEquals(emptySet<PlayerId>(), DiplomacyPolicy.betrayalTargets(pacted, PlayerId(0)))
        assertEquals(emptySet<PlayerId>(), DiplomacyPolicy.betrayalTargets(pacted, PlayerId(0), turtle))
    }

    @Test
    fun `easy stays blind to every personality hook`() {
        val state = strip(30, 0..12, 27..29).withTreasury(0, 200)
        assertNull(ResearchPolicy.action(state, Difficulty.EASY, AiProfile.of(Difficulty.HARD, AiPersonality.SCHEMER)))
    }
}
