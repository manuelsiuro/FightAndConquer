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
 * Naval types live on SEA hexes only, are bought at sea next to an own PORT,
 * move by range-limited sea BFS (never region reach), and keep [GameUnit.tier]
 * fixed at 1:
 *
 * - [TRANSPORT]: carries one land unit as [GameUnit.cargo] (embark by moving
 *   the unit onto it, [com.msa.fightandconquer.core.engine.GameAction.Disembark]
 *   to land it, amphibious capture included). Strength 0 — warship bait.
 * - [WARSHIP]: sinks enemy boats by moving onto them (ties go to the attacker)
 *   and can Bombard an adjacent land hex — a raid that kills the unit and
 *   destroys a non-capital building, but never captures ground. Towers with
 *   defense >= its strength block bombardment entirely.
 * - [FISHING_BOAT]: the working hull — strength 0, never attacks. Parked on a
 *   FISH_SHOAL sea hex it earns [RuleConstants.fishingBoatIncome] at its
 *   owner's turn start (the game's only income-producing unit); anywhere else
 *   it is pure upkeep. One boat per shoal falls out of hex occupancy.
 *
 * Specials and naval units never merge (not with each other, not with soldiers).
 */
@Serializable
enum class UnitType { SOLDIER, ARCHER, CATAPULT, TRANSPORT, WARSHIP, FISHING_BOAT }
