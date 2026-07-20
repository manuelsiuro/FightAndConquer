package com.msa.fightandconquer.core.engine

import kotlinx.serialization.Serializable

/**
 * Why an action was refused, as a stable code rather than prose.
 *
 * The engine has no access to Android resources, so it reports a reason code (plus
 * an optional [LegalityResult.Rejected.amount] such as a cost or defense value) and
 * the UI layer maps it to a localized string.
 */
@Serializable
enum class RejectionReason {
    GAME_FINISHED,
    NO_SUCH_UNIT,
    NOT_YOUR_UNIT,
    UNIT_ALREADY_ACTED,
    /** Destination holds a friendly unit — merge instead of moving. */
    DESTINATION_HAS_UNIT,
    DESTINATION_UNREACHABLE,
    INVALID_TIER,
    /** amount = required cost. */
    CANNOT_AFFORD,
    NO_SUCH_HEX,
    HEX_CUT_OFF,
    HEX_HAS_BUILDING,
    HEX_HAS_UNIT,
    /** Occupied by a unit that cannot absorb the purchase (different tier / max tier). */
    HEX_OCCUPIED_INCOMPATIBLE,
    NOT_ADJACENT_TO_TERRITORY,
    /** amount = the hex's defense rating. */
    DEFENSE_TOO_HIGH,
    NOT_YOUR_HEX,
    HEX_NEEDS_CLEARING,
    FARM_NEEDS_ADJACENCY,
    NOT_YOUR_UNITS,
    CANNOT_MERGE_WITH_SELF,
    TIER_MISMATCH,
    ALREADY_MAX_TIER,
    NOT_IN_SAME_REGION,
    /** No game is loaded (UI-level guard). */
    NO_GAME,
}
