package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.Rng
import com.msa.fightandconquer.core.model.AiPersonality
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind

/**
 * Flavor of the research priority ordering (see ResearchPolicy.priorityList).
 * BALANCED keeps the historical per-difficulty lists; the rest are personality
 * permutations of the same twelve techs — pacing changes, never new powers.
 */
enum class ResearchOrder { BALANCED, OFFENSE, ECONOMY, NAVAL, SCHOLARLY, BULWARK }

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
    /** Propose a pact to neighbors at >= this/10 of my power (historical: 11). */
    val pactProposalRatioTenths: Int = 11,
    /** Fund a second University once one is working (historically HARD-only). */
    val secondUniversity: Boolean = false,
    /** Which flavor of tech ordering the research policy runs. */
    val researchOrder: ResearchOrder = ResearchOrder.BALANCED,
    /** Open the naval invasion ladder on mixed maps when the sea flank is softer. */
    val amphibious: Boolean = false,
    /**
     * Minimum contested own hexes a tower must actually harden to be worth
     * proposing. 1 matches the historical eagerness (the old rule accepted any
     * uncovered border spot — the tower hex itself counts); raiders demand more.
     */
    val towerGainThreshold: Int = 1,
    /** Minimum aura gain before an archer is proposed (historical: 2). */
    val archerGainThreshold: Int = 2,
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
            // Personalities belong to AI SEATS. A Human chair driven by an
            // AiPlayer (the campaign playthrough stand-in, autoplay) is a
            // stand-in for a competent player, not a someone — it plays the
            // neutral profile, which also keeps the campaign catalogue's
            // solvability gates out of the personality reshuffle.
            val kind = state.player(me).kind as? PlayerKind.Ai ?: return NEUTRAL
            return of(difficulty, kind.personality ?: derivedPersonality(state.config.seed, me))
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

        /**
         * Preset lookup. HARD plays the full preset; NORMAL blends its scalar
         * weights halfway back to neutral (flavor stays, edge softens) and
         * jitters harder — a NORMAL opponent should feel varied more than sharp.
         */
        fun of(difficulty: Difficulty, personality: AiPersonality): AiProfile {
            val full = when (personality) {
                AiPersonality.RAIDER -> AiProfile(
                    personality = personality,
                    hexWeight = 1.15, cutWeight = 1.6, counterAttackWeight = 1.3,
                    assetWeight = 0.8, defenseWeight = 0.7,
                    jitterAmplitude = 1.0,
                    universityGateOffset = 4, warChestTarget = 220,
                    towerGainThreshold = 3,
                    researchOrder = ResearchOrder.OFFENSE,
                )
                AiPersonality.TURTLE -> AiProfile(
                    personality = personality,
                    hexWeight = 0.9, counterAttackWeight = 1.2,
                    assetWeight = 1.2, defenseWeight = 1.5,
                    jitterAmplitude = 1.0,
                    archerGainThreshold = 1, maxFortresses = 3,
                    betrayalDominance = 99.0,
                    researchOrder = ResearchOrder.BULWARK,
                )
                AiPersonality.ADMIRAL -> AiProfile(
                    personality = personality,
                    navalWeight = 1.6,
                    jitterAmplitude = 1.0,
                    proactiveWarships = true, amphibious = true,
                    researchOrder = ResearchOrder.NAVAL,
                )
                AiPersonality.SCHEMER -> AiProfile(
                    personality = personality,
                    cutWeight = 1.4, assetWeight = 1.2,
                    jitterAmplitude = 1.0,
                    universityGateOffset = -2, secondUniversity = true,
                    betrayalDominance = 1.5, pactProposalRatioTenths = 9,
                    researchOrder = ResearchOrder.SCHOLARLY,
                )
            }
            return if (difficulty == Difficulty.HARD) full else blendTowardNeutral(full)
        }

        private fun blendTowardNeutral(full: AiProfile): AiProfile {
            fun mid(x: Double): Double = (x + 1.0) / 2
            return full.copy(
                hexWeight = mid(full.hexWeight),
                cutWeight = mid(full.cutWeight),
                counterAttackWeight = mid(full.counterAttackWeight),
                assetWeight = mid(full.assetWeight),
                defenseWeight = mid(full.defenseWeight),
                navalWeight = mid(full.navalWeight),
                jitterAmplitude = 1.5,
                universityGateOffset = full.universityGateOffset / 2,
                warChestTarget = (full.warChestTarget + NEUTRAL.warChestTarget) / 2,
                towerGainThreshold = (full.towerGainThreshold + NEUTRAL.towerGainThreshold) / 2,
                maxFortresses = (full.maxFortresses + NEUTRAL.maxFortresses) / 2,
                betrayalDominance = (full.betrayalDominance + NEUTRAL.betrayalDominance) / 2,
            )
        }
    }
}
