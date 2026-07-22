package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * What kind of unit a [GameUnit] is. SOLDIER is the classic tier ladder
 * (strength == tier). Specials are single-level ([GameUnit.tier] fixed at 1),
 * with per-type strength/upkeep in [RuleConstants]:
 *
 * - [ARCHER]: weak in attack but projects a tower-like defense aura over its own
 *   hex and adjacent own hexes ([RuleConstants.archerAuraDefense]) — a mobile
 *   tower that costs upkeep instead of a one-time price.
 * - [CATAPULT]: the castle-breaker — ignores building defense entirely when
 *   attacking, but moves at most [RuleConstants.catapultMoveRange] hexes per
 *   action, so it can be intercepted. Loses to defense from enemy units.
 *
 * Specials never merge (not with each other, not with soldiers).
 */
@Serializable
enum class UnitType { SOLDIER, ARCHER, CATAPULT }
