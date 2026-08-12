package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * A player's civilization: the identity that selects the piece art set and, in later
 * phases, a light stat-delta table resolved through [com.msa.fightandconquer.core.engine.Rules].
 *
 * Orthogonal to seat color — color stays derived from seat index everywhere, so any
 * civilization renders in any player's faction tint. [KINGDOM] is the pre-civilization
 * art set and the serialization default: a save or map written before civilizations
 * existed decodes as all-Kingdom and replays unchanged.
 */
@Serializable
enum class Civilization {
    KINGDOM,
    VIKINGS,
    SULTANATE,
    SHOGUNATE,
    ;

    companion object {
        val DEFAULT = KINGDOM
    }
}
