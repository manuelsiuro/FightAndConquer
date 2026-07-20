package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSimulationTest {

    private fun newAiGame(seed: Long, difficulties: List<Difficulty>, size: MapSize = MapSize.SMALL): GameState {
        val params = MapParams(
            seed = seed,
            size = size,
            playerCount = difficulties.size,
            shape = MapShape.entries[(seed % 3).toInt()],
        )
        return MapGenerator.generate(params).newGame(
            gameSeed = seed * 31 + 7,
            kinds = difficulties.map { PlayerKind.Ai(it) },
        )
    }

    /** Drives one full AI turn; returns the state after EndTurn. */
    private fun playTurn(state: GameState, ais: List<AiPlayer>): GameState {
        var s = state
        val ai = ais[s.currentPlayer.value]
        var actions = 0
        while (true) {
            val action = ai.chooseAction(s)
            s = Reducer.reduce(s, action).state
            actions++
            if (action == GameAction.EndTurn || s.phase !is GamePhase.Playing) return s
            if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                return Reducer.reduce(s, GameAction.EndTurn).state
            }
        }
    }

    private fun playGame(seed: Long, difficulties: List<Difficulty>, maxRounds: Int = 400): GameState {
        var state = newAiGame(seed, difficulties)
        val ais = difficulties.map { AiPlayer(it) }
        while (state.phase is GamePhase.Playing && state.turnNumber < maxRounds) {
            state = playTurn(state, ais)
            assertInvariants(state)
        }
        return state
    }

    @Test
    fun `full AI games terminate with a winner and invariants intact`() {
        var finished = 0
        for (seed in 1L..8L) {
            val playerCount = 2 + (seed % 3).toInt() // 2..4
            val final = playGame(seed, List(playerCount) { Difficulty.NORMAL })
            assertTrue(
                "seed $seed did not finish (round ${final.turnNumber})",
                final.phase is GamePhase.Finished,
            )
            finished++
        }
        assertEquals(8, finished)
    }

    @Test
    fun `easy AI expands from the very first turns instead of hoarding`() {
        // Regression: rookie weights once made every expansion net-negative, so EASY
        // sat motionless until ~150 coins. Easy must be weak, not catatonic.
        for (seed in 1L..3L) {
            var state = newAiGame(seed, listOf(Difficulty.EASY, Difficulty.EASY))
            val ais = List(2) { AiPlayer(Difficulty.EASY) }
            repeat(6) { // three rounds
                if (state.phase is GamePhase.Playing) state = playTurn(state, ais)
            }
            for (player in 0..1) {
                val hexes = state.tiles.values.count { it.owner == PlayerId(player) }
                assertTrue(
                    "seed $seed: EASY player $player still owns only $hexes hexes after 3 rounds",
                    hexes > 7,
                )
            }
        }
    }

    @Test
    fun `hard beats easy in at least 70 percent of mirror games`() {
        var hardWins = 0
        var games = 0
        for (seed in 1L..10L) {
            for (hardSeat in 0..1) {
                val difficulties = if (hardSeat == 0) {
                    listOf(Difficulty.HARD, Difficulty.EASY)
                } else {
                    listOf(Difficulty.EASY, Difficulty.HARD)
                }
                val final = playGame(seed, difficulties)
                games++
                val winner = (final.phase as? GamePhase.Finished)?.winner
                if (winner == PlayerId(hardSeat)) hardWins++
            }
        }
        assertTrue("hard won $hardWins/$games", hardWins * 100 >= games * 70)
    }

    @Test
    fun `ai turn completes within one second on a large map`() {
        var state = newAiGame(3L, List(4) { Difficulty.HARD }, size = MapSize.LARGE)
        val ais = List(4) { AiPlayer(Difficulty.HARD) }
        var worstMs = 0L
        repeat(12) {
            if (state.phase !is GamePhase.Playing) return@repeat
            val startNs = System.nanoTime()
            state = playTurn(state, ais)
            worstMs = maxOf(worstMs, (System.nanoTime() - startNs) / 1_000_000)
        }
        assertTrue("worst AI turn took ${worstMs}ms", worstMs < 1000)
    }

    @Test
    fun `ai games are fully deterministic`() {
        val json = Json
        fun run(): String {
            var state = newAiGame(5L, listOf(Difficulty.NORMAL, Difficulty.HARD))
            val ais = listOf(AiPlayer(Difficulty.NORMAL), AiPlayer(Difficulty.HARD))
            var rounds = 0
            while (state.phase is GamePhase.Playing && rounds < 30) {
                state = playTurn(state, ais)
                rounds++
            }
            return json.encodeToString(GameState.serializer(), state)
        }
        assertEquals(run(), run())
    }
}
