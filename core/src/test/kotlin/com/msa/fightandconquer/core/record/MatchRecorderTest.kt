package com.msa.fightandconquer.core.record

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recorder is the debrief's only data source — the engine keeps no full-game log,
 * so everything the Chronicle shows must come out of this fold, deterministically.
 */
class MatchRecorderTest {

    private fun meta(state: GameState) = MatchMeta(
        kind = MatchKind.SKIRMISH_VS_AI,
        seed = state.config.seed,
        landHexes = state.tiles.size,
        fogOfWar = false,
    )

    /** Drives the engine and folds like the ViewModel's scoreboard step would. */
    private fun fold(recorder: MatchRecorderState, engine: GameEngine, action: GameAction): MatchRecorderState {
        val before = engine.state.value
        engine.submit(action)
        return MatchRecorderState.step(recorder, before, engine.state.value, engine.lastEvents)
    }

    @Test
    fun `every living seat gets a baseline and then one sample per round`() {
        val state = TestStates.strip(9, 0..2, 6..8)
        val engine = GameEngine(state)
        var recorder = MatchRecorderState.start(state, meta(state))

        assertEquals("baseline for seat 0", listOf(0), recorder.series[0].rounds)
        assertEquals("baseline for seat 1", listOf(0), recorder.series[1].rounds)
        assertEquals(3, recorder.series[0].hexes.single())
        assertEquals(100, recorder.series[1].treasury.single())

        // Two full rounds: P0, P1, P0, P1. Seat 1's first TurnStarted lands in round 0,
        // which the baseline already covers — no double point.
        repeat(4) { recorder = fold(recorder, engine, GameAction.EndTurn) }

        assertEquals(listOf(0, 1, 2), recorder.series[0].rounds)
        assertEquals(listOf(0, 1), recorder.series[1].rounds)
        assertEquals("hex income sampled off the event", 3, recorder.series[0].income.last())
    }

    @Test
    fun `sinking an enemy boat is credited and retold, losing your own is only a loss`() {
        val state = TestStates.strip(9, 0..2, 6..8)
            .withSea(listOf(hex(3), hex(4)))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.WARSHIP)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.TRANSPORT)
        val engine = GameEngine(state)
        var recorder = MatchRecorderState.start(state, meta(state))

        recorder = fold(recorder, engine, GameAction.MoveUnit(state.unitIdAt(hex(3)), hex(4)))

        assertEquals(1, recorder.totals[0].boatsSunk)
        assertEquals(1, recorder.totals[1].unitsLost)
        assertEquals(0, recorder.totals[1].boatsSunk)
        val sunk = recorder.moments.filterIsInstance<KeyMoment.ShipSunk>().single()
        assertEquals(1, sunk.owner)
        assertEquals(0, sunk.by)
    }

    @Test
    fun `a conquest records the loot, the elimination and the crowning`() {
        // P1's capital is its only hex: one capture loots, eliminates, and ends the war.
        val state = TestStates.custom(
            owners = (0..5).associate { hex(it) to 0 } + mapOf(hex(6) to 1),
            capital0 = hex(0),
            capital1 = hex(6),
        ).withUnit(owner = 0, tier = 3, at = hex(5))
        val engine = GameEngine(state)
        var recorder = MatchRecorderState.start(state, meta(state))

        recorder = fold(recorder, engine, GameAction.MoveUnit(state.unitIdAt(hex(5)), hex(6)))

        val loot = recorder.moments.filterIsInstance<KeyMoment.CapitalLooted>().single()
        assertEquals(0, loot.by)
        assertEquals(1, loot.victim)
        assertEquals("half of the 100 treasury", 50, loot.loot)
        assertEquals(1, recorder.moments.filterIsInstance<KeyMoment.Eliminated>().single().seat)
        assertEquals(0, recorder.moments.filterIsInstance<KeyMoment.Crowned>().single().winner)
        assertEquals(1, recorder.totals[0].hexesCaptured)
        assertTrue(engine.state.value.phase is GamePhase.Finished)

        recorder = recorder.finish(engine.state.value)
        assertEquals(0, recorder.winnerSeat)
        assertTrue(recorder.finished)
    }

    @Test
    fun `the fold is deterministic and a finished record is inert`() {
        val state = TestStates.strip(9, 0..2, 6..8)

        fun playThrough(): MatchRecorderState {
            val engine = GameEngine(state)
            var recorder = MatchRecorderState.start(state, meta(state))
            recorder = fold(recorder, engine, GameAction.BuyUnit(1, hex(1)))
            repeat(4) { recorder = fold(recorder, engine, GameAction.EndTurn) }
            return recorder
        }

        assertEquals(playThrough(), playThrough())

        val engine = GameEngine(state)
        val finished = MatchRecorderState.start(state, meta(state)).finish(engine.state.value)
        assertSame(finished, fold(finished, engine, GameAction.EndTurn))
    }
}
