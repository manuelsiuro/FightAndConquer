package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitId
import com.msa.fightandconquer.core.model.UnitType

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

    /** Attack/capture power of a unit: tier for soldiers, per-type for specials. */
    fun strengthOf(unit: GameUnit, rules: RuleConstants): Int =
        buyStrength(rules, unit.tier, unit.type)

    /** [strengthOf] for a unit that doesn't exist yet (buy-capture legality). */
    fun buyStrength(rules: RuleConstants, tier: Int, type: UnitType): Int = when (type) {
        UnitType.SOLDIER -> tier
        UnitType.ARCHER -> rules.archerStrength
        UnitType.CATAPULT -> rules.catapultStrength
    }

    fun unitCostOf(rules: RuleConstants, tier: Int, type: UnitType): Int = when (type) {
        UnitType.SOLDIER -> rules.unitCost[tier - 1]
        UnitType.ARCHER -> rules.archerCost
        UnitType.CATAPULT -> rules.catapultCost
    }

    /** Per-turn upkeep of a unit — the single source shared with TurnPipeline. */
    fun unitUpkeepOf(unit: GameUnit, rules: RuleConstants): Int = when (unit.type) {
        UnitType.SOLDIER -> rules.unitUpkeep[unit.tier - 1]
        UnitType.ARCHER -> rules.archerUpkeep
        UnitType.CATAPULT -> rules.catapultUpkeep
    }

    /**
     * What a unit contributes to the defense of its hex and adjacent own hexes.
     * The archer's aura slots into the existing max-based model exactly like tower
     * coverage — no additive special case.
     */
    internal fun defenseContribution(unit: GameUnit, rules: RuleConstants): Int = when (unit.type) {
        UnitType.ARCHER -> rules.archerAuraDefense
        else -> strengthOf(unit, rules)
    }

    /**
     * Defense rating of [hex] from an attacker's perspective:
     * max of the defending unit on it, the owner's units on adjacent own hexes,
     * and tower/capital coverage (self + adjacent). Neutral hexes defend at 0.
     * A capture requires attacker strength STRICTLY greater than this.
     * A CATAPULT [attackerType] ignores building contributions entirely
     * (units still defend at full value).
     */
    fun defenseOf(state: GameState, hex: Hex, attackerType: UnitType? = null): Int {
        val tile = state.tiles[hex] ?: return 0
        val owner = tile.owner ?: return 0
        val rules = state.config.rules
        val siege = attackerType == UnitType.CATAPULT
        var defense = if (siege) 0 else buildingDefense(state, tile.building)
        state.unitAt(hex)?.let { defense = maxOf(defense, defenseContribution(it, rules)) }
        HexMath.forEachNeighbor(hex) { n ->
            val neighborTile = state.tiles[n]
            if (neighborTile?.owner == owner) {
                state.unitAt(n)?.let { defense = maxOf(defense, defenseContribution(it, rules)) }
                if (!siege) defense = maxOf(defense, buildingDefense(state, neighborTile.building))
            }
        }
        return defense
    }

    private fun buildingDefense(state: GameState, building: Building?): Int = when (building) {
        Building.TOWER -> state.config.rules.towerDefense
        Building.STRONG_TOWER -> state.config.rules.strongTowerDefense
        Building.CAPITAL -> state.config.rules.capitalDefense
        Building.FARM, Building.MINE, Building.MARKET,
        Building.LUMBER_CAMP, Building.WATCHTOWER, null,
        -> 0
    }

    /**
     * Slay-style reachability for a fresh unit, in one region scan:
     * - moveTargets: unoccupied, building-free hexes of its region (flora is fine — moving
     *   onto a tree clears it);
     * - captureTargets: non-owned hexes adjacent to the region with defense < strength;
     * - mergeTargets: hexes in the region holding a same-tier friendly SOLDIER (tier < max;
     *   specials never merge).
     * A CATAPULT is range-capped: every target must lie within
     * [RuleConstants.catapultMoveRange] of its current hex.
     */
    fun reachable(state: GameState, unitId: UnitId): ReachResult {
        val unit = state.units[unitId] ?: return ReachResult.EMPTY
        if (unit.spent || state.phase !is com.msa.fightandconquer.core.model.GamePhase.Playing) return ReachResult.EMPTY
        val rules = state.config.rules
        val strength = strengthOf(unit, rules)
        val maxRange = if (unit.type == UnitType.CATAPULT) rules.catapultMoveRange else Int.MAX_VALUE
        fun inRange(hex: Hex) = HexMath.distance(unit.hex, hex) <= maxRange
        val region = region(state, unit.hex)
        val move = HashSet<Hex>()
        val capture = HashSet<Hex>()
        val merge = HashSet<Hex>()
        for (hex in region) {
            val tile = state.tiles.getValue(hex)
            if (hex != unit.hex && tile.building == null && inRange(hex)) {
                val occupant = state.unitAt(hex)
                when {
                    occupant == null -> move.add(hex)
                    unit.type == UnitType.SOLDIER && occupant.type == UnitType.SOLDIER &&
                        occupant.tier == unit.tier && unit.tier < rules.maxTier -> merge.add(hex)
                }
            }
            HexMath.forEachNeighbor(hex) { n ->
                if (n !in capture && inRange(n)) {
                    val neighborTile = state.tiles[n]
                    if (neighborTile != null && neighborTile.owner != unit.owner &&
                        strength > defenseOf(state, n, unit.type)
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
            com.msa.fightandconquer.core.model.BuildingType.MINE -> state.config.rules.mineCost
            com.msa.fightandconquer.core.model.BuildingType.MARKET -> state.config.rules.marketCost
            com.msa.fightandconquer.core.model.BuildingType.LUMBER_CAMP -> state.config.rules.lumberCampCost
            com.msa.fightandconquer.core.model.BuildingType.WATCHTOWER -> state.config.rules.watchtowerCost
        }

    /** Income the player will collect at turn start: producing hexes, deposits, buildings. */
    fun incomeOf(state: GameState, player: PlayerId): Int =
        incomeFrom(state.tiles, state.config.rules, player)

    /**
     * Single source of truth for income, shared with TurnPipeline. A tile produces only
     * when owned, non-starving and flora-free; deposit bonuses and building income stack
     * on top of [RuleConstants.hexIncome].
     */
    internal fun incomeFrom(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        rules: com.msa.fightandconquer.core.model.RuleConstants,
        player: PlayerId,
    ): Int {
        var income = 0
        for ((hex, tile) in tiles) {
            if (tile.owner != player || tile.starving || tile.flora != null) continue
            income += rules.hexIncome
            if (tile.deposit == com.msa.fightandconquer.core.model.Deposit.FERTILE) income += rules.fertileHexBonus
            when (tile.building) {
                Building.FARM -> {
                    income += rules.farmIncome
                    if (tile.deposit == com.msa.fightandconquer.core.model.Deposit.FERTILE) income += rules.fertileFarmBonus
                }
                Building.MINE -> income += rules.mineIncome
                Building.MARKET -> {
                    var neighbors = 0
                    HexMath.forEachNeighbor(hex) { n ->
                        val t = tiles[n]
                        if (t != null && t.owner == player && !t.starving && t.flora == null) neighbors++
                    }
                    income += rules.marketNeighborIncome * minOf(neighbors, rules.marketNeighborCap)
                }
                Building.LUMBER_CAMP -> {
                    var trees = 0
                    HexMath.forEachNeighbor(hex) { n ->
                        val t = tiles[n]
                        if (t != null && t.owner == player && t.flora is com.msa.fightandconquer.core.model.Flora.Tree) trees++
                    }
                    income += rules.lumberCampTreeIncome * minOf(trees, rules.lumberCampTreeCap)
                }
                else -> {}
            }
        }
        return income
    }

    fun upkeepOf(state: GameState, player: PlayerId): Int {
        val rules = state.config.rules
        return state.units.values.sumOf { if (it.owner == player) unitUpkeepOf(it, rules) else 0 }
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
                Building.WATCHTOWER -> addRange(hex, rules.watchtowerVisionRadius)
                Building.FARM, Building.MINE, Building.MARKET, Building.LUMBER_CAMP, null -> {}
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
