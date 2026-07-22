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
    /** Special units (archer, catapult) never merge — with each other or soldiers. */
    CANNOT_MERGE_SPECIAL,
    /** Special units are disabled by this game's rules snapshot. */
    SPECIAL_UNITS_DISABLED,
    /** The building requires a specific terrain deposit (Mine on a gold vein). */
    BUILDING_NEEDS_DEPOSIT,
    /** The building only functions with fog of war enabled (Watchtower). */
    REQUIRES_FOG_OF_WAR,
    /** Diplomacy is disabled by this game's rules snapshot. */
    DIPLOMACY_DISABLED,
    /** Target seat is invalid: self, out of range, or eliminated. */
    INVALID_PLAYER,
    /** Pact duration outside [RuleConstants.pactMinDurationRounds..pactMaxDurationRounds]. */
    INVALID_PACT_DURATION,
    /** These two players already have an active pact. */
    PACT_ALREADY_ACTIVE,
    /** A proposal between these two players is already pending. */
    PROPOSAL_PENDING,
    /** amount = rounds until this pair may exchange another proposal. */
    PROPOSAL_COOLDOWN,
    /** No pending proposal from that player to respond to. */
    NO_SUCH_PROPOSAL,
    /** Tribute must be at least one coin. */
    INVALID_TRIBUTE_AMOUNT,
    /** No game is loaded (UI-level guard). */
    NO_GAME,
}
