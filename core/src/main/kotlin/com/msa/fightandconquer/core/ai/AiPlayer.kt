package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Legality
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.engine.Rng
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState

/**
 * Greedy utility AI. Stateless and pure: [chooseAction] is a function of the game state
 * only, so AI turns are as replayable as human ones. It submits ordinary [GameAction]s
 * through the same legality checks as a human player — it cannot cheat.
 *
 * The caller drives the loop: keep calling chooseAction/apply until it returns EndTurn
 * (bounded by [MAX_ACTIONS_PER_TURN] as a runaway backstop).
 */
class AiPlayer(private val difficulty: Difficulty) {

    fun chooseAction(state: GameState): GameAction {
        val me = state.currentPlayer

        // Diplomacy is a threshold policy, not an argmax candidate (see
        // DiplomacyPolicy). The legality guard keeps a policy/rules mismatch from
        // ever looping the caller on a rejected action.
        if (state.config.rules.diplomacyEnabled) {
            DiplomacyPolicy.mandatoryResponse(state, difficulty)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
            DiplomacyPolicy.initiative(state, difficulty)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }

        val baseline = Evaluator.score(state, me, difficulty)
        var best: GameAction = GameAction.EndTurn
        var bestScore = baseline

        val candidates = MoveGenerator.candidates(state, difficulty)
        for ((index, action) in candidates.withIndex()) {
            // Easy considers only ~60% of its options (deterministic per state+index).
            if (difficulty == Difficulty.EASY &&
                Math.floorMod(Rng.output(state.rngState + index * 31L), 100L) >= 60L
            ) {
                continue
            }
            val result = Reducer.reduce(state, action)
            if (result.events.firstOrNull() is GameEvent.ActionRejected) continue
            val score = Evaluator.score(result.state, me, difficulty)
            if (score > bestScore + EPSILON) {
                best = action
                bestScore = score
            }
        }
        return best
    }

    companion object {
        const val MAX_ACTIONS_PER_TURN = 500
        private const val EPSILON = 1e-6
    }
}
