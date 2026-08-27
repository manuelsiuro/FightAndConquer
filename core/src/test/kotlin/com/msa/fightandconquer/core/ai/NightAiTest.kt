package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Day-night games under AI: termination, determinism, and proof the AI
 * actually engages the night (slays monsters) rather than bleeding to it.
 * No winrate bands (deterministic gates reshuffle chaotically — the flag-off
 * mirror gate in [AiSimulationTest] stays the balance baseline).
 */
class NightAiTest {

    private val nightRules = RuleConstants(dayNightEnabled = true)

    private fun newAiGame(seed: Long, difficulties: List<Difficulty>): GameState {
        val params = MapParams(
            seed = seed,
            size = MapSize.SMALL,
            playerCount = difficulties.size,
            shape = MapShape.entries[(seed % 3).toInt()],
        )
        return MapGenerator.generate(params).newGame(
            gameSeed = seed * 31 + 7,
            kinds = difficulties.map { PlayerKind.Ai(it) },
            rules = nightRules,
            civs = List(difficulties.size) { Civilization.DEFAULT },
        )
    }

    /** Drives one full AI turn, feeding every transition's events to [onEvents]. */
    private fun playTurn(
        state: GameState,
        ais: List<AiPlayer>,
        onEvents: (List<GameEvent>) -> Unit = {},
    ): GameState {
        var s = state
        val ai = ais[s.currentPlayer.value]
        var actions = 0
        while (true) {
            val action = ai.chooseAction(s)
            val result = Reducer.reduce(s, action)
            s = result.state
            onEvents(result.events)
            actions++
            if (action == GameAction.EndTurn || s.phase !is GamePhase.Playing) return s
            if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                val forced = Reducer.reduce(s, GameAction.EndTurn)
                onEvents(forced.events)
                return forced.state
            }
        }
    }

    @Test
    fun `night games terminate with a winner and the AI slays monsters`() {
        // Slaying is asserted across the whole batch, not per seed: a monster
        // hunt must beat a land capture in the same argmax to be chosen, and
        // some trajectories legitimately never present that trade.
        var slain = 0
        for (seed in 1L..4L) {
            var state = newAiGame(seed, listOf(Difficulty.NORMAL, Difficulty.HARD))
            val ais = listOf(AiPlayer(Difficulty.NORMAL), AiPlayer(Difficulty.HARD))
            var spawned = 0
            while (state.phase is GamePhase.Playing && state.turnNumber < 400) {
                state = playTurn(state, ais) { events ->
                    slain += events.count { it is GameEvent.MonsterSlain }
                    spawned += events.count { it is GameEvent.MonsterSpawned }
                }
                assertInvariants(state)
            }
            assertTrue(
                "night seed $seed did not finish (round ${state.turnNumber})",
                state.phase is GamePhase.Finished,
            )
            assertTrue("night seed $seed spawned no monsters", spawned > 0)
        }
        assertTrue("the AI never slew a single monster across all seeds", slain > 0)
    }

    @Test
    fun `night games are fully deterministic`() {
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
