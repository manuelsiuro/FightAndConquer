package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * Permanent terrain resource on a hex, placed at map generation and never created
 * or destroyed by the reducer. Survives capture ([Tile.copy] preserves it).
 *
 * - [GOLD_VEIN]: the only place a Mine can be built.
 * - [FERTILE]: boosts the hex's base income and any Farm built on it.
 * - [FISH_SHOAL]: the sea analog of the gold vein — the only deposit allowed on
 *   SEA hexes; harvested by an adjacent coastal FISHERY.
 *
 * Flora on the hex still blocks all income, deposit bonuses included.
 */
@Serializable
enum class Deposit { GOLD_VEIN, FERTILE, FISH_SHOAL }
