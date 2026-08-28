package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * A named play style for an AI seat. Personality never changes WHAT is legal or
 * visible — it only reweights the same evaluator terms and policy thresholds
 * every seat shares (see [com.msa.fightandconquer.core.ai.AiProfile]), so two
 * personalities on the same difficulty stay comparably strong but feel like
 * different someones.
 *
 * A seat with no explicit personality gets one derived from the immutable
 * [GameConfig.seed] and its [PlayerId] — stable for the whole game (saves and
 * replays included), different across games.
 */
@Serializable
enum class AiPersonality {
    /** Expansion and cuts over consolidation; thin defenses, early pressure. */
    RAIDER,

    /** Holds ground: towers, garrisons, economy behind walls; never betrays. */
    TURTLE,

    /** Sea-minded: ports, warships, amphibious flanks even with a land front. */
    ADMIRAL,

    /** Tech and diplomacy first; starves rivals with cuts rather than storms. */
    SCHEMER,
}
