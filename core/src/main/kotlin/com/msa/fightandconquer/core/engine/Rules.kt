package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitId

data class ReachResult(
    val moveTargets: Set<Hex>,
    val captureTargets: Set<Hex>,
    val mergeTargets: Set<Hex>,
) {
    companion object {
        val EMPTY = ReachResult(emptySet(), emptySet(), emptySet())
    }
}

/** Pure rule queries shared by Legality, the Reducer, the AI, and the UI. */
object Rules {

    /** The connected same-owner region containing [start]; empty if the hex is neutral/absent. */
    fun region(state: GameState, start: Hex): Set<Hex> {
        val owner = state.tiles[start]?.owner ?: return emptySet()
        return HexMath.floodFill(start) { state.tiles[it]?.owner == owner }
    }

    /**
     * Defense rating of [hex] from an attacker's perspective:
     * max of the defending unit on it, the owner's units on adjacent own hexes,
     * and tower/capital coverage (self + adjacent). Neutral hexes defend at 0.
     * A capture requires attacker strength STRICTLY greater than this.
     */
    fun defenseOf(state: GameState, hex: Hex): Int {
        val tile = state.tiles[hex] ?: return 0
        val owner = tile.owner ?: return 0
        var defense = buildingDefense(state, tile.building)
        state.unitAt(hex)?.let { defense = maxOf(defense, it.tier) }
        HexMath.forEachNeighbor(hex) { n ->
            val neighborTile = state.tiles[n]
            if (neighborTile?.owner == owner) {
                state.unitAt(n)?.let { defense = maxOf(defense, it.tier) }
                defense = maxOf(defense, buildingDefense(state, neighborTile.building))
            }
        }
        return defense
    }

    private fun buildingDefense(state: GameState, building: Building?): Int = when (building) {
        Building.TOWER -> state.config.rules.towerDefense
        Building.STRONG_TOWER -> state.config.rules.strongTowerDefense
        Building.CAPITAL -> state.config.rules.capitalDefense
        Building.FARM, null -> 0
    }

    /**
     * Slay-style reachability for a fresh unit, in one region scan:
     * - moveTargets: unoccupied, building-free hexes of its region (flora is fine — moving
     *   onto a tree clears it);
     * - captureTargets: non-owned hexes adjacent to the region with defense < strength;
     * - mergeTargets: hexes in the region holding a same-tier friendly unit (tier < max).
     */
    fun reachable(state: GameState, unitId: UnitId): ReachResult {
        val unit = state.units[unitId] ?: return ReachResult.EMPTY
        if (unit.spent || state.phase !is com.msa.fightandconquer.core.model.GamePhase.Playing) return ReachResult.EMPTY
        val region = region(state, unit.hex)
        val move = HashSet<Hex>()
        val capture = HashSet<Hex>()
        val merge = HashSet<Hex>()
        for (hex in region) {
            val tile = state.tiles.getValue(hex)
            if (hex != unit.hex && tile.building == null) {
                val occupant = state.unitAt(hex)
                when {
                    occupant == null -> move.add(hex)
                    occupant.tier == unit.tier && unit.tier < state.config.rules.maxTier -> merge.add(hex)
                }
            }
            HexMath.forEachNeighbor(hex) { n ->
                if (n !in capture) {
                    val neighborTile = state.tiles[n]
                    if (neighborTile != null && neighborTile.owner != unit.owner &&
                        unit.tier > defenseOf(state, n)
                    ) {
                        capture.add(n)
                    }
                }
            }
        }
        return ReachResult(move, capture, merge)
    }

    /** Owned hexes connected to the player's capital (the funded "main" territory). */
    fun capitalConnected(state: GameState, player: PlayerId): Set<Hex> {
        val capital = state.player(player).capital ?: return emptySet()
        if (state.tiles[capital]?.owner != player) return emptySet()
        return HexMath.floodFill(capital) { state.tiles[it]?.owner == player }
    }

    /** Cost of the player's NEXT farm: base + step per farm already owned. */
    fun nextFarmCost(state: GameState, player: PlayerId): Int =
        state.config.rules.farmCostBase + state.config.rules.farmCostStep * state.farmCount(player)

    fun buildingCost(state: GameState, player: PlayerId, type: com.msa.fightandconquer.core.model.BuildingType): Int =
        when (type) {
            com.msa.fightandconquer.core.model.BuildingType.FARM -> nextFarmCost(state, player)
            com.msa.fightandconquer.core.model.BuildingType.TOWER -> state.config.rules.towerCost
            com.msa.fightandconquer.core.model.BuildingType.STRONG_TOWER -> state.config.rules.strongTowerCost
        }

    /** Income the player will collect at turn start: producing hexes + farms. */
    fun incomeOf(state: GameState, player: PlayerId): Int {
        val rules = state.config.rules
        var income = 0
        for (tile in state.tiles.values) {
            if (tile.owner == player && !tile.starving && tile.flora == null) {
                income += rules.hexIncome
                if (tile.building == Building.FARM) income += rules.farmIncome
            }
        }
        return income
    }

    fun upkeepOf(state: GameState, player: PlayerId): Int {
        val rules = state.config.rules
        return state.units.values.sumOf { if (it.owner == player) rules.unitUpkeep[it.tier - 1] else 0 }
    }

    /**
     * Fog-of-war live vision: union of radius ranges around the player's owned hexes,
     * units, and vision buildings (capital/towers), clipped to the map. Pure and
     * RNG-free — vision is always derived, never stored (only [PlayerState.discovered]
     * persists). See docs/fog-of-war.md, including the visionRadiusOwned >= 2 invariant.
     */
    fun visibleHexes(state: GameState, player: PlayerId): Set<Hex> =
        visibleHexesFrom(state.tiles, state.units.values, state.config.rules, player)

    /** Map-shape-agnostic core of [visibleHexes], shared with the engine's StateBuilder. */
    internal fun visibleHexesFrom(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        units: Collection<com.msa.fightandconquer.core.model.GameUnit>,
        rules: com.msa.fightandconquer.core.model.RuleConstants,
        player: PlayerId,
    ): Set<Hex> {
        val visible = HashSet<Hex>()
        fun addRange(center: Hex, radius: Int) {
            for (h in HexMath.range(center, radius)) if (h in tiles) visible.add(h)
        }
        for ((hex, tile) in tiles) {
            if (tile.owner != player) continue
            addRange(hex, rules.visionRadiusOwned)
            when (tile.building) {
                Building.CAPITAL, Building.TOWER, Building.STRONG_TOWER ->
                    addRange(hex, rules.visionRadiusBuilding)
                Building.FARM, null -> {}
            }
        }
        for (unit in units) {
            if (unit.owner == player) addRange(unit.hex, rules.visionRadiusUnit)
        }
        return visible
    }

    /** Canonical packed-sorted storage form for [PlayerState.discovered] (byte-stable JSON). */
    internal fun sortedDiscovered(hexes: Set<Hex>): Set<Hex> =
        hexes.sortedBy { it.packed }.toCollection(LinkedHashSet())
}
