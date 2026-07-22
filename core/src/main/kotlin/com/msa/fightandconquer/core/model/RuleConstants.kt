package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * All tunable rule values, snapshotted into each game's [GameConfig] so saves are
 * self-describing. Values follow docs/game-idea.md where specified, and
 * Slay/Antiyoy conventions where the doc is silent.
 */
@Serializable
data class RuleConstants(
    /** Purchase cost by tier (index = tier - 1). Any tier is directly buyable. */
    val unitCost: List<Int> = listOf(10, 20, 30, 40),
    /** Per-turn upkeep by tier (index = tier - 1). Locked by the design doc. */
    val unitUpkeep: List<Int> = listOf(2, 6, 18, 54),
    val maxTier: Int = 4,

    val hexIncome: Int = 1,
    val farmCostBase: Int = 12,
    /** Each additional farm costs this much more than the previous one. */
    val farmCostStep: Int = 2,
    val farmIncome: Int = 4,

    val towerCost: Int = 15,
    val towerDefense: Int = 2,
    val strongTowerCost: Int = 35,
    val strongTowerDefense: Int = 3,
    val capitalDefense: Int = 1,
    /** Percent of the victim's treasury looted when their capital is captured. */
    val capitalLootPercent: Int = 50,

    val startingTreasury: Int = 12,
    val startRegionSize: Int = 7,

    val treeClearBonus: Int = 3,
    /** Chance (percent) for each tree to spread at the affected player's turn start. */
    val treeSpreadPercent: Int = 10,
    /** Percent of neutral land hexes seeded with trees at map generation. */
    val initialTreePercent: Int = 8,

    /** Classic fog of war: unseen hexes hidden, seen-once hexes remembered as terrain. */
    val fogOfWar: Boolean = false,
    /**
     * Vision radius around every owned hex. MUST stay >= 2: every hex an action can
     * target — and every input to defenseOf on those targets — lies within distance 2
     * of owned territory, which is why Legality, reachable() and MoveGenerator need
     * no fog checks (see docs/fog-of-war.md).
     */
    val visionRadiusOwned: Int = 2,
    /** Vision radius around each of the player's units. */
    val visionRadiusUnit: Int = 3,
    /** Vision radius around the player's Capital, Tower and Strong Tower. */
    val visionRadiusBuilding: Int = 4,
)
