package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Full-game diplomacy gates: pacts are honored, proposals can't oscillate, the
 * feature actually gets used, and pacted games still end in conquest.
 */
class AiDiplomacyTest {

    private fun newAiGame(seed: Long, playerCount: Int, difficulty: Difficulty): GameState {
        val params = MapParams(
            seed = seed,
            size = MapSize.SMALL,
            playerCount = playerCount,
            shape = MapShape.entries[(seed % 3).toInt()],
        )
        return MapGenerator.generate(params).newGame(
            gameSeed = seed * 31 + 7,
            kinds = List(playerCount) { PlayerKind.Ai(difficulty) },
            rules = RuleConstants(),
        )
    }

    @Test
    fun `normal AIs honor pacts and proposals never spam across full games`() {
        var proposals = 0
        var accepted = 0
        for (seed in 1L..6L) {
            val playerCount = 3 + (seed % 2).toInt() // 3..4
            var state = newAiGame(seed, playerCount, Difficulty.NORMAL)
            val ais = List(playerCount) { AiPlayer(Difficulty.NORMAL) }
            // Unordered pair -> round of their last observed proposal.
            val lastProposal = HashMap<Pair<Int, Int>, Int>()
            val cooldown = state.config.rules.pactProposalCooldownRounds

            while (state.phase is GamePhase.Playing && state.turnNumber < 400) {
                val actor = state.currentPlayer
                var actions = 0
                while (true) {
                    val action = ais[actor.value].chooseAction(state)

                    // NORMAL never betrays: no capture may target an active partner.
                    val target = when (action) {
                        is GameAction.MoveUnit -> state.units[action.unit]?.let { u ->
                            state.tiles[action.to]?.owner?.takeIf { it != u.owner }
                        }
                        is GameAction.BuyUnit -> state.tiles[action.at]?.owner?.takeIf { it != actor }
                        else -> null
                    }
                    if (target != null) {
                        assertTrue(
                            "seed $seed: $actor attacked pact partner $target on round ${state.turnNumber}",
                            state.diplomacy.pactBetween(actor, target) == null,
                        )
                    }

                    if (action is GameAction.ProposePact) {
                        val pair = minOf(actor.value, action.to.value) to maxOf(actor.value, action.to.value)
                        lastProposal[pair]?.let { last ->
                            assertTrue(
                                "seed $seed: pair $pair proposed again after ${state.turnNumber - last} rounds",
                                state.turnNumber - last >= cooldown,
                            )
                        }
                        lastProposal[pair] = state.turnNumber
                        proposals++
                    }

                    val result = Reducer.reduce(state, action)
                    if (result.events.any { it is GameEvent.PactAccepted }) accepted++
                    state = result.state
                    actions++
                    if (action == GameAction.EndTurn || state.phase !is GamePhase.Playing) break
                    if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                        state = Reducer.reduce(state, GameAction.EndTurn).state
                        break
                    }
                }
            }
            assertTrue("seed $seed did not finish (pacts must not deadlock conquest)", state.phase is GamePhase.Finished)
        }
        assertTrue("diplomacy never exercised: 0 proposals across 6 games", proposals > 0)
        assertTrue("no pact ever accepted across 6 games", accepted > 0)
    }

    @Test
    fun `hard duel endgames stay live despite pacts`() {
        // Two HARD players: any signed pact must not stall the game to the round cap
        // (Hard declines duels and betrays dominated partners).
        for (seed in 1L..3L) {
            var state = newAiGame(seed, 2, Difficulty.HARD)
            val ais = List(2) { AiPlayer(Difficulty.HARD) }
            while (state.phase is GamePhase.Playing && state.turnNumber < 400) {
                val ai = ais[state.currentPlayer.value]
                var actions = 0
                while (true) {
                    val action = ai.chooseAction(state)
                    state = Reducer.reduce(state, action).state
                    actions++
                    if (action == GameAction.EndTurn || state.phase !is GamePhase.Playing) break
                    if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                        state = Reducer.reduce(state, GameAction.EndTurn).state
                        break
                    }
                }
            }
            assertTrue("hard duel seed $seed stalled", state.phase is GamePhase.Finished)
        }
    }
}
