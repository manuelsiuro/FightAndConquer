package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withResearch
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.model.ActiveResearch
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.ResearchState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The [Legality.checkStartResearch] gate order — one pinned test per line. */
class ResearchLegalityTest {

    private val rules = RuleConstants(researchEnabled = true)

    private fun lab(r: RuleConstants = rules): GameState =
        strip(9, 0..2, 6..8, rules = r).withBuilding(Building.UNIVERSITY, hex(1))

    private fun reasonOf(state: GameState, tech: Tech): RejectionReason {
        val result = Reducer.reduce(state, GameAction.StartResearch(tech))
        assertEquals("a rejected action must not change state", state, result.state)
        return result.events.filterIsInstance<GameEvent.ActionRejected>().single().reason
    }

    @Test
    fun `research disabled rejects first`() {
        val off = strip(9, 0..2, 6..8, rules = RuleConstants(researchEnabled = false))
            .withBuilding(Building.UNIVERSITY, hex(1))
        assertEquals(RejectionReason.RESEARCH_DISABLED, reasonOf(off, Tech.COINAGE))
    }

    @Test
    fun `sail branch is unavailable without naval rules`() {
        val landlocked = lab(RuleConstants(researchEnabled = true, navalEnabled = false))
        assertEquals(RejectionReason.NAVAL_DISABLED, reasonOf(landlocked, Tech.NAVIGATION))
        // The other branches stay open.
        assertTrue(
            Legality.check(landlocked, GameAction.StartResearch(Tech.COINAGE)) is LegalityResult.Ok,
        )
    }

    @Test
    fun `a completed tech cannot be restarted`() {
        val s = lab().withResearch(0, ResearchState.of(listOf(Tech.COINAGE)))
        assertEquals(RejectionReason.RESEARCH_ALREADY_COMPLETE, reasonOf(s, Tech.COINAGE))
    }

    @Test
    fun `one research at a time`() {
        val s = lab().withResearch(0, ResearchState(active = ActiveResearch(Tech.COINAGE, 1)))
        assertEquals(RejectionReason.RESEARCH_IN_PROGRESS, reasonOf(s, Tech.SMITHING))
    }

    @Test
    fun `linear branches - each tier needs the one below`() {
        assertEquals(RejectionReason.RESEARCH_NEEDS_PREREQUISITE, reasonOf(lab(), Tech.BANKING))
        assertEquals(RejectionReason.RESEARCH_NEEDS_PREREQUISITE, reasonOf(lab(), Tech.TREASURY))
        val banked = lab().withResearch(0, ResearchState.of(listOf(Tech.COINAGE)))
        assertTrue(Legality.check(banked, GameAction.StartResearch(Tech.BANKING)) is LegalityResult.Ok)
        // Tier 3 still needs tier 2 even with tier 1 done.
        assertEquals(RejectionReason.RESEARCH_NEEDS_PREREQUISITE, reasonOf(banked, Tech.TREASURY))
    }

    @Test
    fun `no university - no research`() {
        val bare = strip(9, 0..2, 6..8, rules = rules)
        assertEquals(RejectionReason.NO_UNIVERSITY, reasonOf(bare, Tech.COINAGE))
        // A starving university does not count either.
        val starving = lab().let {
            it.copy(tiles = it.tiles + (hex(1) to it.tiles.getValue(hex(1)).copy(starving = true)))
        }
        assertEquals(RejectionReason.NO_UNIVERSITY, reasonOf(starving, Tech.COINAGE))
    }

    @Test
    fun `affordability is the last gate and carries the cost`() {
        val broke = lab().withTreasury(0, rules.techCostByTier[0] - 1)
        val result = Reducer.reduce(broke, GameAction.StartResearch(Tech.COINAGE))
        val rejected = result.events.filterIsInstance<GameEvent.ActionRejected>().single()
        assertEquals(RejectionReason.CANNOT_AFFORD, rejected.reason)
        assertEquals(rules.techCostByTier[0], rejected.amount)
    }

    @Test
    fun `the ok path`() {
        assertTrue(Legality.check(lab(), GameAction.StartResearch(Tech.COINAGE)) is LegalityResult.Ok)
        assertTrue(Legality.check(lab(), GameAction.StartResearch(Tech.NAVIGATION)) is LegalityResult.Ok)
    }
}
