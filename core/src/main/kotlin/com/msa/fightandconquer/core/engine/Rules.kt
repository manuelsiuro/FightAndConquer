package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.CivModifiers
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
    /** Own empty transports this land unit can board (moves onto the boat's hex). */
    val embarkTargets: Set<Hex> = emptySet(),
    /** Frontier hexes within range whose defense currently beats this unit (UI chips). */
    val blockedTargets: Set<Hex> = emptySet(),
    /** Own transports that would be boardable but already carry cargo (TRANSPORT_FULL). */
    val fullTransports: Set<Hex> = emptySet(),
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

    /** Boats: units that live on SEA hexes and move by sea BFS instead of region reach. */
    fun isNaval(type: UnitType): Boolean =
        type == UnitType.TRANSPORT || type == UnitType.WARSHIP || type == UnitType.FISHING_BOAT

    /**
     * FISH_SHOAL sea hexes within [radius] of [hex] (center included — moot for
     * the land-hex callers). The single shoal query shared by Legality's fishery
     * placement, [incomeFrom]'s fishery arm, the AI's fishery valuation, and the
     * app's range indicator/income breakdown — they must never drift apart.
     */
    fun shoalHexesWithin(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        hex: Hex,
        radius: Int,
    ): List<Hex> = HexMath.range(hex, radius).filter { n ->
        val t = tiles[n]
        t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
            t.deposit == com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL
    }

    /** Count form of [shoalHexesWithin]. */
    fun shoalsWithin(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        hex: Hex,
        radius: Int,
    ): Int = shoalHexesWithin(tiles, hex, radius).size

    /**
     * The rules [player] actually plays with: the game's [RuleConstants] filtered
     * through their civilization's delta table ([CivModifiers.effective] — identity
     * for KINGDOM and when [RuleConstants.civBonusesEnabled] is off). Every
     * owner-dependent accessor below resolves through this; the soldier ladder is
     * universal by design, so raw `state.config.rules.unitCost`/`unitUpkeep`/
     * `soldierMoveRanges`/`maxTier` reads stay valid everywhere.
     */
    fun effectiveRules(state: GameState, player: PlayerId): RuleConstants =
        CivModifiers.effective(state.config.rules, state.player(player).civ)

    /** Attack/capture power of a unit: tier for soldiers, per-type (and per-civ) for specials. */
    fun strengthOf(state: GameState, unit: GameUnit): Int =
        strengthIn(effectiveRules(state, unit.owner), unit.tier, unit.type)

    /** [strengthOf] for a [player]'s unit that doesn't exist yet (buy-capture legality, cargo). */
    fun buyStrength(state: GameState, player: PlayerId, tier: Int, type: UnitType): Int =
        strengthIn(effectiveRules(state, player), tier, type)

    private fun strengthIn(rules: RuleConstants, tier: Int, type: UnitType): Int = when (type) {
        UnitType.SOLDIER -> tier
        UnitType.ARCHER -> rules.archerStrength
        UnitType.CATAPULT -> rules.catapultStrength
        UnitType.TRANSPORT, UnitType.FISHING_BOAT -> 0
        UnitType.WARSHIP -> rules.warshipStrength
    }

    /**
     * The defense number the UI pairs with a unit's attack ([strengthOf]): what an
     * enemy is up against when going for the unit itself. Land units show their
     * garrison/aura contribution to hex defense; a TRANSPORT shows 0 (anything
     * sinks it); a WARSHIP shows its strength — the naval sink threshold (an enemy
     * sinks it at strength >= this; ties go to the ATTACKER). Boats still
     * contribute 0 to land-hex defense ([defenseContribution]): this is a per-unit
     * display value, not a garrison. Not to be confused with [defenseOf], the
     * hex-level max the capture rule tests.
     */
    fun unitDefenseOf(state: GameState, unit: GameUnit): Int =
        defenseIn(effectiveRules(state, unit.owner), unit.tier, unit.type)

    /** [unitDefenseOf] for a [player]'s unit that doesn't exist yet (recruit cards). */
    fun buyDefense(state: GameState, player: PlayerId, tier: Int, type: UnitType): Int =
        defenseIn(effectiveRules(state, player), tier, type)

    private fun defenseIn(rules: RuleConstants, tier: Int, type: UnitType): Int = when (type) {
        UnitType.ARCHER -> rules.archerAuraDefense
        UnitType.TRANSPORT, UnitType.FISHING_BOAT -> 0
        UnitType.WARSHIP -> rules.warshipStrength
        else -> strengthIn(rules, tier, type)
    }

    /** What [player] pays for a fresh unit (civ-priced for specials; soldiers universal). */
    fun unitCostOf(state: GameState, player: PlayerId, tier: Int, type: UnitType): Int =
        costIn(effectiveRules(state, player), tier, type)

    private fun costIn(rules: RuleConstants, tier: Int, type: UnitType): Int = when (type) {
        UnitType.SOLDIER -> rules.unitCost[tier - 1]
        UnitType.ARCHER -> rules.archerCost
        UnitType.CATAPULT -> rules.catapultCost
        UnitType.TRANSPORT -> rules.transportCost
        UnitType.WARSHIP -> rules.warshipCost
        UnitType.FISHING_BOAT -> rules.fishingBoatCost
    }

    /**
     * Per-turn upkeep of a unit, at its owner's effective rules.
     * A transport also pays its cargo's upkeep: no free army parking at sea.
     */
    fun unitUpkeepOf(state: GameState, unit: GameUnit): Int =
        upkeepIn(unit, effectiveRules(state, unit.owner))

    /** [unitUpkeepOf] against pre-resolved effective [rules] — the single source shared with [upkeepFrom]. */
    private fun upkeepIn(unit: GameUnit, rules: RuleConstants): Int {
        val own = when (unit.type) {
            UnitType.SOLDIER -> rules.unitUpkeep[unit.tier - 1]
            UnitType.ARCHER -> rules.archerUpkeep
            UnitType.CATAPULT -> rules.catapultUpkeep
            UnitType.TRANSPORT -> rules.transportUpkeep
            UnitType.WARSHIP -> rules.warshipUpkeep
            UnitType.FISHING_BOAT -> rules.fishingBoatUpkeep
        }
        val cargo = unit.cargo?.let { cargoUpkeep(it, rules) } ?: 0
        return own + cargo
    }

    private fun cargoUpkeep(cargo: com.msa.fightandconquer.core.model.CargoUnit, rules: RuleConstants): Int =
        when (cargo.type) {
            UnitType.SOLDIER -> rules.unitUpkeep[cargo.tier - 1]
            UnitType.ARCHER -> rules.archerUpkeep
            UnitType.CATAPULT -> rules.catapultUpkeep
            UnitType.TRANSPORT, UnitType.WARSHIP, UnitType.FISHING_BOAT -> 0 // boats never carry boats
        }

    /**
     * What a unit contributes to the defense of its hex and adjacent own hexes,
     * at its OWNER's effective rules. The archer's aura slots into the existing
     * max-based model exactly like tower coverage — no additive special case.
     * Boats are ships, not garrisons: they defend nothing (and being at sea,
     * never neighbor an OWN hex anyway).
     */
    internal fun defenseContribution(state: GameState, unit: GameUnit): Int =
        if (isNaval(unit.type)) 0 else unitDefenseOf(state, unit)

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
        val siege = attackerType == UnitType.CATAPULT
        var defense = if (siege) 0 else buildingDefense(state, owner, tile.building)
        state.unitAt(hex)?.let { defense = maxOf(defense, defenseContribution(state, it)) }
        HexMath.forEachNeighbor(hex) { n ->
            val neighborTile = state.tiles[n]
            if (neighborTile?.owner == owner) {
                state.unitAt(n)?.let { defense = maxOf(defense, defenseContribution(state, it)) }
                if (!siege) defense = maxOf(defense, buildingDefense(state, owner, neighborTile.building))
            }
        }
        return defense
    }

    /**
     * The minimum attack that takes [hex] by land: capture requires STRICTLY more
     * than [defenseOf], so the threshold is defense + 1. (The naval sink threshold
     * is the defender's [unitDefenseOf] — ties go to the attacker at sea.)
     */
    fun captureRequirement(state: GameState, hex: Hex): Int = defenseOf(state, hex) + 1

    /** The strongest single contributor to [defenseOf] — what a capture must out-attack. */
    sealed interface DefenseSource {
        /** A defending unit: the garrison on the hex itself, or one on an adjacent own hex. */
        data class Unit(val unit: GameUnit) : DefenseSource

        /** A tower/castle/capital covering the hex from [at] (the hex itself or a neighbor). */
        data class Fortification(val building: Building, val at: Hex) : DefenseSource
    }

    /**
     * Which piece produces [defenseOf] at [hex] — for the UI to explain why a hex
     * defends above the tapped unit's own value ("Guarded by Tower"). Mirrors
     * [defenseOf]'s max exactly (same [attackerType] siege gating); ties prefer the
     * hex's own garrison (it explains itself), then fortifications over neighbor
     * units (naming the permanent piece reads clearer). Null when the hex defends at 0.
     */
    fun defenseSourceOf(state: GameState, hex: Hex, attackerType: UnitType? = null): DefenseSource? {
        val tile = state.tiles[hex] ?: return null
        val owner = tile.owner ?: return null
        val siege = attackerType == UnitType.CATAPULT
        var best: DefenseSource? = null
        var bestValue = 0
        // Consideration order = tie priority: a later candidate must strictly beat the best.
        fun consider(value: Int, source: () -> DefenseSource) {
            if (value > bestValue) {
                bestValue = value
                best = source()
            }
        }
        state.unitAt(hex)?.let { consider(defenseContribution(state, it)) { DefenseSource.Unit(it) } }
        if (!siege) {
            tile.building?.let {
                consider(buildingDefense(state, owner, it)) { DefenseSource.Fortification(it, hex) }
            }
        }
        HexMath.forEachNeighbor(hex) { n ->
            val neighborTile = state.tiles[n]
            if (neighborTile?.owner == owner) {
                if (!siege) {
                    neighborTile.building?.let {
                        consider(buildingDefense(state, owner, it)) { DefenseSource.Fortification(it, n) }
                    }
                }
                state.unitAt(n)?.let { consider(defenseContribution(state, it)) { DefenseSource.Unit(it) } }
            }
        }
        return best
    }

    /** Defensive value of the DEFENDER's building, at the defender's effective rules. */
    private fun buildingDefense(state: GameState, owner: PlayerId, building: Building?): Int {
        if (building == null) return 0
        val rules = effectiveRules(state, owner)
        return when (building) {
            Building.TOWER -> rules.towerDefense
            Building.STRONG_TOWER -> rules.strongTowerDefense
            Building.CAPITAL -> rules.capitalDefense
            Building.FARM, Building.MINE, Building.MARKET,
            Building.LUMBER_CAMP, Building.WATCHTOWER, Building.PORT,
            Building.FISHERY, Building.BRIDGE,
            -> 0
        }
    }

    /** Movement range of a unit per action, at its owner's effective rules. */
    fun moveRangeOf(state: GameState, unit: GameUnit): Int {
        val rules = effectiveRules(state, unit.owner)
        return when (unit.type) {
            UnitType.CATAPULT -> rules.catapultMoveRange
            UnitType.ARCHER -> rules.archerMoveRange
            UnitType.TRANSPORT -> rules.transportMoveRange
            UnitType.WARSHIP -> rules.warshipMoveRange
            UnitType.FISHING_BOAT -> rules.fishingBoatMoveRange
            UnitType.SOLDIER ->
                rules.soldierMoveRanges.getOrElse(unit.tier - 1) { rules.soldierMoveRanges.last() }
        }
    }

    /**
     * Reachability for a fresh land unit: BFS from its hex through its own
     * connected territory (any own tile is traversable — friendly units and
     * buildings never block the path, bridges carry it over water), up to
     * [moveRangeOf] steps. Mirrors the ships' bounded BFS so every unit's whole
     * reach reads as one local blob:
     * - moveTargets: own unoccupied, stand-able hexes (building-free or bridge;
     *   flora is fine — moving onto a tree clears it) within range;
     * - captureTargets: non-owned hexes with defense < strength, adjacent to
     *   the path — the capture is the final step, at path distance <= range;
     * - mergeTargets: same-tier friendly SOLDIERs within range (tier < max;
     *   specials never merge);
     * - embarkTargets: own empty transports on sea adjacent to the path.
     */
    fun reachable(state: GameState, unitId: UnitId): ReachResult {
        val unit = state.units[unitId] ?: return ReachResult.EMPTY
        if (unit.spent || state.phase !is com.msa.fightandconquer.core.model.GamePhase.Playing) return ReachResult.EMPTY
        if (isNaval(unit.type)) return seaReachable(state, unit)
        val rules = state.config.rules
        val strength = strengthOf(state, unit)
        val maxRange = moveRangeOf(state, unit)
        val move = HashSet<Hex>()
        val capture = HashSet<Hex>()
        val merge = HashSet<Hex>()
        val embark = HashSet<Hex>()
        val fullBoats = HashSet<Hex>()
        val visited = HashSet<Hex>().apply { add(unit.hex) }
        val blocked = HashSet<Hex>() // non-owned hexes already found too defended
        var frontier = listOf(unit.hex)
        var depth = 0
        while (depth < maxRange && frontier.isNotEmpty()) {
            val next = ArrayList<Hex>()
            for (hex in frontier) {
                HexMath.forEachNeighbor(hex) { n ->
                    if (n !in visited) {
                        val tile = state.tiles[n]
                        when {
                            tile == null -> {}
                            tile.owner == unit.owner -> {
                                visited.add(n)
                                next.add(n)
                                // A bridge is the one stand-able building:
                                // troops walk and hold it.
                                val standable = tile.building == null || tile.building == Building.BRIDGE
                                val occupant = state.unitAt(n)
                                when {
                                    occupant == null -> if (standable) move.add(n)
                                    unit.type == UnitType.SOLDIER && occupant.type == UnitType.SOLDIER &&
                                        occupant.tier == unit.tier && unit.tier < rules.maxTier -> merge.add(n)
                                }
                            }
                            // Open sea is never capturable by land units — but an
                            // own empty transport floating there can be boarded,
                            // and an enemy/neutral BRIDGE hex is dry ground to storm.
                            tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
                                tile.building != Building.BRIDGE -> {
                                if (n !in embark && n !in fullBoats && rules.navalEnabled) {
                                    val boat = state.unitAt(n)
                                    if (boat != null && boat.owner == unit.owner &&
                                        boat.type == UnitType.TRANSPORT
                                    ) {
                                        if (boat.cargo == null) embark.add(n) else fullBoats.add(n)
                                    }
                                }
                            }
                            n !in capture && n !in blocked -> {
                                if (strength > defenseOf(state, n, unit.type)) {
                                    capture.add(n)
                                } else {
                                    blocked.add(n)
                                }
                            }
                        }
                    }
                }
            }
            frontier = next
            depth++
        }
        return ReachResult(move, capture, merge, embark, blocked, fullBoats)
    }

    /**
     * Naval reachability: BFS over open sea (empty of units and buildings) up to
     * the boat's move range. A WARSHIP additionally targets enemy boats on sea
     * hexes adjacent to its reachable water when its strength is >= the
     * defender's — naval ties go to the ATTACKER, or equal warships could never
     * sink each other and island games would stalemate. Boats never merge.
     */
    private fun seaReachable(state: GameState, unit: GameUnit): ReachResult {
        if (!state.config.rules.navalEnabled) return ReachResult.EMPTY
        val maxRange = moveRangeOf(state, unit)
        fun openSea(hex: Hex): Boolean {
            val t = state.tiles[hex] ?: return false
            return t.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
                t.building == null && t.unit == null
        }

        val move = HashSet<Hex>()
        val capture = HashSet<Hex>()
        val visited = HashSet<Hex>().apply { add(unit.hex) }
        var frontier = listOf(unit.hex)
        var depth = 0
        val strength = strengthOf(state, unit)
        while (depth < maxRange && frontier.isNotEmpty()) {
            val next = ArrayList<Hex>()
            for (hex in frontier) {
                HexMath.forEachNeighbor(hex) { n ->
                    if (n !in visited) {
                        if (openSea(n)) {
                            visited.add(n)
                            move.add(n)
                            next.add(n)
                        } else if (unit.type == UnitType.WARSHIP && n !in capture) {
                            // Attack: an enemy boat blocks the water it sits on.
                            val defender = state.unitAt(n)
                            if (defender != null && defender.owner != unit.owner &&
                                isNaval(defender.type) &&
                                state.tiles[n]?.building == null &&
                                strength >= strengthOf(state, defender)
                            ) {
                                capture.add(n)
                            }
                        }
                    }
                }
            }
            frontier = next
            depth++
        }
        return ReachResult(move, capture, mergeTargets = emptySet())
    }

    /** Cost of the player's NEXT farm: base + step per farm already owned (civ-priced). */
    fun nextFarmCost(state: GameState, player: PlayerId): Int {
        val rules = effectiveRules(state, player)
        return rules.farmCostBase + rules.farmCostStep * state.farmCount(player)
    }

    /** What [player] pays for a fresh building, at their effective rules. */
    fun buildingCost(state: GameState, player: PlayerId, type: com.msa.fightandconquer.core.model.BuildingType): Int {
        val rules = effectiveRules(state, player)
        return when (type) {
            com.msa.fightandconquer.core.model.BuildingType.FARM -> nextFarmCost(state, player)
            com.msa.fightandconquer.core.model.BuildingType.TOWER -> rules.towerCost
            com.msa.fightandconquer.core.model.BuildingType.STRONG_TOWER -> rules.strongTowerCost
            com.msa.fightandconquer.core.model.BuildingType.MINE -> rules.mineCost
            com.msa.fightandconquer.core.model.BuildingType.MARKET -> rules.marketCost
            com.msa.fightandconquer.core.model.BuildingType.LUMBER_CAMP -> rules.lumberCampCost
            com.msa.fightandconquer.core.model.BuildingType.WATCHTOWER -> rules.watchtowerCost
            com.msa.fightandconquer.core.model.BuildingType.PORT -> rules.portCost
            com.msa.fightandconquer.core.model.BuildingType.FISHERY -> rules.fisheryCost
            com.msa.fightandconquer.core.model.BuildingType.BRIDGE -> rules.bridgeCost
        }
    }

    /**
     * Treasury credit for demolishing an own [building]:
     * [RuleConstants.demolishRefundPercent] of its cost, integer division.
     * A FARM refunds against the LAST farm's price (base + step × (count − 1)),
     * never [nextFarmCost] — otherwise build-then-demolish would turn a profit.
     * CAPITAL never reaches here (Legality forbids demolishing it).
     */
    fun demolishRefund(state: GameState, player: PlayerId, building: Building): Int {
        val rules = effectiveRules(state, player)
        val cost = when (building) {
            Building.CAPITAL -> return 0
            Building.FARM ->
                rules.farmCostBase +
                    rules.farmCostStep * (state.farmCount(player) - 1).coerceAtLeast(0)
            Building.TOWER -> rules.towerCost
            Building.STRONG_TOWER -> rules.strongTowerCost
            Building.MINE -> rules.mineCost
            Building.MARKET -> rules.marketCost
            Building.LUMBER_CAMP -> rules.lumberCampCost
            Building.WATCHTOWER -> rules.watchtowerCost
            Building.PORT -> rules.portCost
            Building.FISHERY -> rules.fisheryCost
            Building.BRIDGE -> rules.bridgeCost
        }
        return cost * rules.demolishRefundPercent / 100
    }

    /**
     * Treasury credit for disbanding [unit]: [RuleConstants.demolishRefundPercent]
     * of its owner's cost for it, cargo included (the cargo goes down with the
     * transport).
     */
    fun disbandRefund(state: GameState, unit: GameUnit): Int {
        val rules = effectiveRules(state, unit.owner)
        val own = costIn(rules, unit.tier, unit.type)
        val cargo = unit.cargo?.let { costIn(rules, it.tier, it.type) } ?: 0
        return (own + cargo) * rules.demolishRefundPercent / 100
    }

    /** Income the player will collect at turn start: producing hexes, deposits, buildings, parked boats. */
    fun incomeOf(state: GameState, player: PlayerId): Int {
        val eff = effectiveRules(state, player)
        return incomeFrom(state.tiles, eff, player) +
            boatIncomeFrom(state.tiles, state.units.values, eff, player)
    }

    /**
     * Per-turn earnings of [player]'s fishing boats parked on FISH_SHOAL sea
     * hexes — the unit half of the income sum (see [incomeFrom] for the tile
     * half; TurnPipeline adds both, exactly like [incomeOf]). Standing on the
     * shoal at turn start is the whole rule: no spent/fog/starving coupling
     * (open sea is never owned and never starves).
     */
    internal fun boatIncomeFrom(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        units: Collection<GameUnit>,
        rules: RuleConstants,
        player: PlayerId,
    ): Int = units.sumOf { u ->
        val t = tiles[u.hex]
        if (u.owner == player && u.type == UnitType.FISHING_BOAT &&
            t != null && t.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
            t.deposit == com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL
        ) {
            rules.fishingBoatIncome
        } else {
            0
        }
    }

    /**
     * Single source of truth for TILE income, shared with TurnPipeline. A tile produces
     * only when owned, non-starving and flora-free; deposit bonuses and building income
     * stack on top of [RuleConstants.hexIncome]. [rules] must be [player]'s EFFECTIVE
     * rules (both callers resolve them; only [player]'s own tiles are read).
     * NOT the whole income: every caller must also add [boatIncomeFrom].
     */
    internal fun incomeFrom(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        rules: com.msa.fightandconquer.core.model.RuleConstants,
        player: PlayerId,
    ): Int {
        var income = 0
        for ((hex, tile) in tiles) {
            if (tile.owner != player || tile.starving || tile.flora != null) continue
            // Sea produces nothing, owned or not (a bridge hex is owned but incomeless).
            if (tile.terrain == com.msa.fightandconquer.core.model.Terrain.SEA) continue
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
                Building.PORT -> income += rules.portIncome
                Building.FISHERY -> {
                    val shoals = shoalsWithin(tiles, hex, rules.fisheryRange)
                    income += rules.fisheryShoalIncome * minOf(shoals, rules.fisheryShoalCap)
                }
                else -> {}
            }
        }
        return income
    }

    fun upkeepOf(state: GameState, player: PlayerId): Int =
        upkeepFrom(state.units.values, effectiveRules(state, player), player)

    /**
     * Single source of truth for upkeep, shared with TurnPipeline (mirrors [incomeFrom]).
     * [rules] must be [player]'s EFFECTIVE rules (only [player]'s own units are summed).
     */
    internal fun upkeepFrom(
        units: Collection<GameUnit>,
        rules: RuleConstants,
        player: PlayerId,
    ): Int = units.sumOf { if (it.owner == player) upkeepIn(it, rules) else 0 }

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
                Building.FARM, Building.MINE, Building.MARKET, Building.LUMBER_CAMP,
                Building.PORT, Building.FISHERY, Building.BRIDGE, null,
                -> {}
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
