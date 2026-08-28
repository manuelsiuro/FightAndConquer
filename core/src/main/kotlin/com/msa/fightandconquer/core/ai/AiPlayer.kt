package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Legality
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.engine.Rng
import com.msa.fightandconquer.core.engine.Rules
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
        // A training dummy: holds what it has and yields. Short-circuiting here keeps
        // PASSIVE out of the evaluator and move generator entirely.
        if (difficulty == Difficulty.PASSIVE) return GameAction.EndTurn

        val me = state.currentPlayer
        // One profile for the whole decision: personality (explicit or
        // seed-derived) folded with difficulty into the weight/threshold set
        // every stage below reads (see AiProfile).
        val profile = AiProfile.resolve(state, me, difficulty)

        // Diplomacy is a threshold policy, not an argmax candidate (see
        // DiplomacyPolicy). The legality guard keeps a policy/rules mismatch from
        // ever looping the caller on a rejected action.
        if (state.config.rules.diplomacyEnabled) {
            DiplomacyPolicy.mandatoryResponse(state, difficulty)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
            DiplomacyPolicy.initiative(state, difficulty, profile)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }

        // Research is a threshold policy for the same structural reason: its
        // payoff is turns away, invisible to a one-ply argmax (see ResearchPolicy).
        // Before the naval ladder — it funds the NAVIGATION that ladder waits on.
        if (state.config.rules.researchEnabled) {
            ResearchPolicy.action(state, difficulty, profile)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }

        // The muster halls are threshold policies for the same structural reason:
        // a pure prerequisite pays nothing the turn it stands (see MilitaryPolicy).
        // Before the naval ladder — it funds the Barracks the marine tiers wait on.
        if (state.config.rules.militaryBuildingsRequired) {
            MilitaryPolicy.action(state, difficulty)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }

        // Beacons are a threshold policy for the same structural reason: lit
        // ground pays nothing until the monsters walk (see BeaconPolicy).
        if (state.config.rules.dayNightEnabled) {
            BeaconPolicy.action(state, difficulty)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }

        // Naval invasion is a threshold policy too: a single-action greedy search
        // can never justify the intermediate ferry steps (see NavalPolicy).
        // Fishing follows for the same structural reason, and AFTER invasion —
        // termination-load-bearing war wins any treasury contention.
        if (state.config.rules.navalEnabled) {
            NavalPolicy.action(state, difficulty, profile)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
            FishingPolicy.action(state, difficulty)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }

        // One frozen visibility set for the whole comparison: candidates are
        // judged on what is known NOW, never penalized for what they reveal
        // (see Evaluator.score's visibleOverride).
        val frozenVisible = if (state.config.rules.fogOfWar) Rules.visibleHexes(state, me) else null
        // The strategic read is frozen alongside visibility: candidates are
        // GENERATED from what is true now, then judged by simulation.
        val context = Strategy.assess(state, me, profile, frozenVisible)
        val baseline = Evaluator.score(state, me, difficulty, frozenVisible, profile)
        var best: GameAction = GameAction.EndTurn
        var bestScore = baseline

        val candidates = MoveGenerator.candidates(state, difficulty, profile, context)
        for ((index, action) in candidates.withIndex()) {
            // Easy considers only ~60% of its options (deterministic per state+index).
            if (difficulty == Difficulty.EASY &&
                Math.floorMod(Rng.output(state.rngState + index * 31L), 100L) >= 60L
            ) {
                continue
            }
            val result = Reducer.reduce(state, action)
            if (result.events.firstOrNull() is GameEvent.ActionRejected) continue
            // Seeded jitter: a hash of the immutable game seed, the seat, the
            // round, and the candidate's content — NEVER rngState, which
            // advances mid-turn. Sized to reorder near-equals (frontier
            // captures that once tie-broke purely on hex packing), always far
            // below the hex weight, so the AI varies without ever picking a
            // strictly worse move class. Replays stay byte-identical: the same
            // save produces the same hash forever.
            val noise = if (profile.jitterAmplitude == 0.0) {
                0.0
            } else {
                val key = state.config.seed * 31L + me.value * 7919L +
                    state.turnNumber * 131L + fingerprint(action)
                profile.jitterAmplitude *
                    (Math.floorMod(Rng.output(key), 1024L) / 1024.0 - 0.5)
            }
            val score = Evaluator.score(result.state, me, difficulty, frozenVisible, profile) + noise
            if (score > bestScore + EPSILON) {
                best = action
                bestScore = score
            }
        }
        // Nothing to capture, buy, or defend? March a rear laggard toward the
        // front (the Antiyoy relocate phase) before yielding the turn.
        if (best == GameAction.EndTurn && difficulty != Difficulty.EASY) {
            RepositionPolicy.action(state, context)?.let { action ->
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }
        return best
    }

    companion object {
        const val MAX_ACTIONS_PER_TURN = 500
        private const val EPSILON = 1e-6

        /**
         * Content hash for the jitter key. Deliberately hand-rolled: enum and
         * data-class hashCodes are identity-derived on some members and NOT
         * stable across JVM runs, which would break replay determinism.
         */
        private fun fingerprint(action: GameAction): Long = when (action) {
            is GameAction.MoveUnit ->
                1_000_003L + action.unit.value * 131L + action.to.packed
            is GameAction.BuyUnit ->
                2_000_003L + action.tier * 977L + action.at.packed * 31L + action.type.ordinal
            is GameAction.BuyBuilding ->
                3_000_003L + action.type.ordinal * 977L + action.at.packed
            is GameAction.MergeUnits ->
                4_000_003L + action.a.value * 131L + action.b.value
            is GameAction.DisbandUnit ->
                5_000_003L + action.unit.value
            is GameAction.Bombard ->
                6_000_003L + action.unit.value * 131L + action.target.packed
            else -> 7_000_003L
        }
    }
}
