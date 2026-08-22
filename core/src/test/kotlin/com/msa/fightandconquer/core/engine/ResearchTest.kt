package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withResearch
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.ActiveResearch
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.ResearchState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The research lifecycle through the real reducer: start, tick, stack,
 * complete, freeze, undo — and the documented interactions with bankruptcy
 * and income scaling.
 */
class ResearchTest {

    private val rules = RuleConstants(researchEnabled = true)

    /** P0 owns 0..2 (capital at 0) with a University at hex 1. */
    private fun lab(): GameState =
        strip(9, 0..2, 6..8, rules = rules).withBuilding(Building.UNIVERSITY, hex(1))

    /** Advance a full round: P0's EndTurn, then P1's — back to P0's turn start. */
    private fun fullRound(state: GameState): ReduceResult {
        val afterP0 = Reducer.reduce(state, GameAction.EndTurn)
        return Reducer.reduce(afterP0.state, GameAction.EndTurn)
    }

    private fun research(state: GameState, seat: Int = 0): ResearchState =
        state.player(PlayerId(seat)).research

    @Test
    fun `starting research debits the cost and fills the active slot`() {
        val (next, events) = Reducer.reduce(lab(), GameAction.StartResearch(Tech.COINAGE))
        assertEquals(100 - rules.techCostByTier[0], next.player(PlayerId(0)).treasury)
        assertEquals(ActiveResearch(Tech.COINAGE, 0), research(next).active)
        val started = events.filterIsInstance<GameEvent.ResearchStarted>().single()
        assertEquals(Tech.COINAGE, started.tech)
        assertEquals(rules.techCostByTier[0], started.cost)
        assertInvariants(next)
    }

    @Test
    fun `one university adds one point at each own turn start`() {
        val started = Reducer.reduce(lab(), GameAction.StartResearch(Tech.COINAGE)).state
        val afterRound = fullRound(started).state
        assertEquals(1, research(afterRound).active?.progress)
        // The opponent's turn start ticked nothing for P0.
        assertEquals(ResearchState(), research(afterRound, seat = 1))
    }

    @Test
    fun `universities stack - two labs make two points a turn`() {
        val started = Reducer.reduce(
            lab().withBuilding(Building.UNIVERSITY, hex(2)),
            GameAction.StartResearch(Tech.COINAGE),
        ).state
        assertEquals(2, research(fullRound(started).state).active?.progress)
    }

    @Test
    fun `a tech completes at its duration and lands in canonical order`() {
        var s = Reducer.reduce(lab(), GameAction.StartResearch(Tech.COINAGE)).state
        repeat(rules.techDurationByTier[0] - 1) { s = fullRound(s).state }
        val (done, events) = fullRound(s)
        assertNull(research(done).active)
        assertEquals(listOf(Tech.COINAGE), research(done).completed.toList())
        assertEquals(
            Tech.COINAGE,
            events.filterIsInstance<GameEvent.ResearchCompleted>().single().tech,
        )
        assertInvariants(done)
    }

    @Test
    fun `overshoot is discarded - three labs on a three-point tech bank nothing`() {
        var s = lab()
            .withBuilding(Building.UNIVERSITY, hex(2))
            .withResearch(0, ResearchState(active = ActiveResearch(Tech.COINAGE, 2)))
        s = fullRound(s).state // 2 + 2 labs >= 3 completes
        assertEquals(listOf(Tech.COINAGE), research(s).completed.toList())
        // The next research starts from zero.
        val next = Reducer.reduce(s, GameAction.StartResearch(Tech.BANKING)).state
        assertEquals(0, research(next).active?.progress)
    }

    @Test
    fun `losing the last university freezes progress with no refund - rebuilding resumes`() {
        var s = Reducer.reduce(lab(), GameAction.StartResearch(Tech.COINAGE)).state
        s = fullRound(s).state // progress 1
        val treasuryBefore = s.player(PlayerId(0)).treasury
        s = Reducer.reduce(s, GameAction.DemolishBuilding(hex(1))).state
        assertEquals(
            "demolish refund only — the research fee stays spent",
            treasuryBefore + rules.universityCost * rules.demolishRefundPercent / 100,
            s.player(PlayerId(0)).treasury,
        )
        s = fullRound(s).state
        assertEquals("frozen", 1, research(s).active?.progress)
        s = Reducer.reduce(s, GameAction.BuyBuilding(com.msa.fightandconquer.core.model.BuildingType.UNIVERSITY, hex(1))).state
        s = fullRound(s).state
        assertEquals("resumed", 2, research(s).active?.progress)
    }

    @Test
    fun `a starving university does not count as working`() {
        val s = lab()
        val starved = s.copy(
            tiles = s.tiles + (hex(1) to s.tiles.getValue(hex(1)).copy(starving = true)),
        )
        assertEquals(1, Rules.workingUniversities(s.tiles, PlayerId(0)))
        assertEquals(0, Rules.workingUniversities(starved.tiles, PlayerId(0)))
    }

    @Test
    fun `research off - a hand-authored active slot never advances`() {
        val off = strip(9, 0..2, 6..8, rules = RuleConstants(researchEnabled = false))
            .withBuilding(Building.UNIVERSITY, hex(1))
            .withResearch(0, ResearchState(active = ActiveResearch(Tech.COINAGE, 1)))
        assertEquals(1, research(fullRound(off).state).active?.progress)
    }

    @Test
    fun `a bankruptcy turn still ticks - the research was prepaid`() {
        // A tier-4 soldier upkeeps 54 against a tiny economy: P0 goes bankrupt at
        // its own turn start, after the tick.
        var s = lab().withUnit(0, 4, hex(2)).withTreasury(0, rules.techCostByTier[0] + 2)
        s = Reducer.reduce(s, GameAction.StartResearch(Tech.COINAGE)).state
        val (next, events) = fullRound(s)
        val completedIdx = events.indexOfFirst { it is GameEvent.ResearchCompleted }
        val progressed = research(next).active?.progress == 1 || completedIdx >= 0
        assertTrue("tick ran on the bankruptcy turn", progressed)
        assertTrue(events.any { it is GameEvent.Bankruptcy })
        assertTrue(next.units.values.none { it.owner == PlayerId(0) })
        // When both fire on one turn start, scholarship reports before the collapse.
        val bankruptcyIdx = events.indexOfFirst { it is GameEvent.Bankruptcy }
        if (completedIdx >= 0) assertTrue(completedIdx < bankruptcyIdx)
        assertInvariants(next)
    }

    @Test
    fun `undo restores treasury and the active slot`() {
        val engine = GameEngine(lab())
        val before = engine.state.value
        assertTrue(engine.submit(GameAction.StartResearch(Tech.COINAGE)) is LegalityResult.Ok)
        assertTrue(engine.undo())
        assertEquals(before, engine.state.value)
    }

    @Test
    fun `coinage scales income from the turn after it completes`() {
        // Progress duration-1, so the next own turn start completes it — but that
        // turn's income was computed before the completion (the documented lag).
        val s = lab().withResearch(
            0,
            ResearchState(active = ActiveResearch(Tech.COINAGE, rules.techDurationByTier[0] - 1)),
        )
        val (completingTurn, completingEvents) = fullRound(s)
        val incomeAtCompletion = completingEvents
            .filterIsInstance<GameEvent.TurnStarted>()
            .single { it.player == PlayerId(0) }
            .income
        val (_, nextEvents) = fullRound(completingTurn)
        val incomeAfter = nextEvents
            .filterIsInstance<GameEvent.TurnStarted>()
            .single { it.player == PlayerId(0) }
            .income
        assertTrue(completingEvents.any { it is GameEvent.ResearchCompleted })
        assertEquals(incomeAtCompletion * 110 / 100, incomeAfter)
    }
}
