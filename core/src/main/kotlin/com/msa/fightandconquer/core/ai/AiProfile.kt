package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.Rng
import com.msa.fightandconquer.core.model.AiPersonality
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind

/**
 * The single tuning surface the whole AI reads: every personality- or
 * difficulty-dependent weight, threshold, and cap lives here, so play styles
 * are data, not scattered branches. [NEUTRAL] reproduces the historical
 * constants exactly — a seat on the neutral profile plays the pre-personality
 * game move for move.
 *
 * Resolution ([resolve]) is pure and derives everything from the immutable
 * [com.msa.fightandconquer.core.model.GameConfig.seed], never from
 * [GameState.rngState] (which advances mid-turn): the same save replays the
 * same personalities forever, different seeds meet different opponents.
 */
data class AiProfile(
    /** Resolved play style; null on the neutral profile (EASY, PASSIVE). */
    val personality: AiPersonality? = null,
    // --- Evaluator term multipliers (1.0 == historical behavior) ---
    val hexWeight: Double = 1.0,
    val cutWeight: Double = 1.0,
    val counterAttackWeight: Double = 1.0,
    val assetWeight: Double = 1.0,
    val defenseWeight: Double = 1.0,
    val navalWeight: Double = 1.0,
    /** Argmax score jitter amplitude in evaluator points (0 = fully greedy). */
    val jitterAmplitude: Double = 0.0,
    // --- Policy thresholds ---
    /** Added to ResearchPolicy's university founding gate (negative = earlier). */
    val universityGateOffset: Int = 0,
    /** NavalPolicy war-chest assault threshold. */
    val warChestTarget: Int = 300,
    /** Buy warships against beatable coastal targets, not just enemy boats. */
    val proactiveWarships: Boolean = false,
    /** Power ratio over a pact partner at which betrayal opens (HARD). */
    val betrayalDominance: Double = 2.0,
    /** Minimum own hexes a tower must actually harden to be worth proposing. */
    val towerGainThreshold: Int = 2,
    // --- Structure caps: economy buildings are a garnish, not a wall-to-wall
    // strategy, and uncapped fortresses are the turtle-stalemate risk ---
    val maxMarkets: Int = 3,
    val maxBanks: Int = 2,
    val maxFortresses: Int = 2,
) {
    companion object {
        /** The historical, personality-free behavior. */
        val NEUTRAL = AiProfile()

        /**
         * The profile a seat plays this game. EASY and PASSIVE stay neutral —
         * the beatable rookie and the training dummy keep their identities
         * (and every campaign teaching level tuned against them).
         */
        fun resolve(state: GameState, me: PlayerId, difficulty: Difficulty): AiProfile {
            if (difficulty == Difficulty.EASY || difficulty == Difficulty.PASSIVE) return NEUTRAL
            val personality = (state.player(me).kind as? PlayerKind.Ai)?.personality
                ?: derivedPersonality(state.config.seed, me)
            return of(difficulty, personality)
        }

        /**
         * Seed-derived personality: one SplitMix64 output of the immutable game
         * seed, rotated by seat — stable all game, distinct across the up-to-4
         * AI seats, reshuffled by every new seed.
         */
        fun derivedPersonality(seed: Long, me: PlayerId): AiPersonality =
            AiPersonality.entries[
                (Rng.output(seed) + me.value).mod(AiPersonality.entries.size.toLong()).toInt(),
            ]

        /** Preset lookup. Phase 1: every preset still plays neutral. */
        fun of(difficulty: Difficulty, personality: AiPersonality): AiProfile =
            NEUTRAL.copy(personality = personality)
    }
}
