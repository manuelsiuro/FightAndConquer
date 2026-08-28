package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.CivModifiers
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.ResearchModifiers
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

    /** Boats: units that live on SEA hexes and move by sea BFS instead of region reach. */
    fun isNaval(type: UnitType): Boolean =
        type == UnitType.TRANSPORT || type == UnitType.WARSHIP || type == UnitType.FISHING_BOAT

    // --- Day-night cycle (see docs/game-rules.md "Day-night cycle") ---

    /**
     * Whether [round] is night. The phase is a pure function of the round
     * counter and the rules snapshot — deliberately NOT stored state, so a
     * replayed save can never desync from its phase.
     */
    fun isNight(round: Int, rules: RuleConstants): Boolean =
        rules.dayNightEnabled &&
            round % (rules.dayLengthRounds + rules.nightLengthRounds) >= rules.dayLengthRounds

    fun isNight(state: GameState): Boolean = isNight(state.turnNumber, state.config.rules)

    /** 0-based index of the day-night cycle [round] belongs to (drives the tier ramp). */
    fun nightIndex(round: Int, rules: RuleConstants): Int =
        round / (rules.dayLengthRounds + rules.nightLengthRounds)

    /** Rounds until the next nightfall from [round], or null when it is already night. */
    fun roundsUntilNight(round: Int, rules: RuleConstants): Int? {
        if (!rules.dayNightEnabled || isNight(round, rules)) return null
        return rules.dayLengthRounds - round % (rules.dayLengthRounds + rules.nightLengthRounds)
    }

    /** Rounds until dawn from night [round], or null when it is daytime. */
    fun roundsUntilDawn(round: Int, rules: RuleConstants): Int? {
        if (!isNight(round, rules)) return null
        val cycle = rules.dayLengthRounds + rules.nightLengthRounds
        return cycle - round % cycle
    }

    /** The tier a monster spawned at [round] gets: base + one per [RuleConstants.monsterTierRampNights] nights, capped. */
    fun monsterTierAt(round: Int, rules: RuleConstants): Int = minOf(
        rules.monsterBaseTier + nightIndex(round, rules) / rules.monsterTierRampNights,
        rules.monsterMaxTier,
    )

    /** Attack a monster strikes with (one above its own defense — a naked equal-tier unit falls). */
    fun monsterAttackOf(monster: com.msa.fightandconquer.core.model.Monster): Int = monster.tier + 1

    /** Defense of the monster's hex contribution: strictly-greater to slay, like any garrison. */
    fun monsterDefenseOf(monster: com.msa.fightandconquer.core.model.Monster): Int = monster.tier

    /**
     * Lit radius of a beacon on [building], or null when the building cannot
     * carry one (the null doubles as Legality's "supported" predicate). A
     * behavior table in code, not rule keys (the MonsterKind.forTier precedent):
     * radius 1 denies spawn-adjacency and blocks a one-hex chokepoint; radius 2
     * (19 hexes, = monsterCapitalStandoff) out-ranges a full round of
     * monsterMoveRange — reserved for the Fortress so the cheap Watchtower
     * never strictly dominates the night game.
     */
    fun beaconRadiusOf(building: Building): Int? = when (building) {
        Building.TOWER, Building.STRONG_TOWER, Building.WATCHTOWER -> 1
        Building.FORTRESS -> 2
        Building.CAPITAL, Building.FARM, Building.MINE, Building.MARKET,
        Building.LUMBER_CAMP, Building.PORT, Building.FISHERY, Building.BRIDGE,
        Building.UNIVERSITY, Building.BANK,
        Building.BARRACKS, Building.ARCHERY_RANGE, Building.SIEGE_WORKSHOP,
        -> null
    }

    /** Effective one-time cost to light a beacon for [player] (civ deltas apply). */
    fun beaconCost(state: GameState, player: PlayerId): Int =
        effectiveRules(state, player).beaconCost

    /**
     * Every hex lit by a standing beacon, any owner — monsters shun light no
     * matter whose it is. Lit hexes are monster-proof at night: excluded from
     * the spawn wave, impassable to monsters, and never struck. Always derived,
     * never stored (the [visibleHexes]/[isNight] doctrine — a replayed save can
     * never desync from it).
     */
    fun litHexes(state: GameState): Set<Hex> = litHexesFrom(state.tiles)

    /**
     * Map-shape-agnostic core of [litHexes], shared with the engine's
     * NightPipeline and the renderer (the [shoalHexesWithin] doctrine — the
     * safe-zone rule must never be re-derived outside this function).
     * [sourceFilter] lets a caller drop whole sources — the renderer's fog
     * gate — without touching the derivation itself.
     */
    fun litHexesFrom(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        sourceFilter: (Hex) -> Boolean = { true },
    ): Set<Hex> {
        var lit: HashSet<Hex>? = null
        for ((hex, tile) in tiles) {
            if (!tile.beacon) continue
            val radius = tile.building?.let { beaconRadiusOf(it) } ?: continue
            if (!sourceFilter(hex)) continue
            val set = lit ?: HashSet<Hex>().also { lit = it }
            for (h in HexMath.range(hex, radius)) if (h in tiles) set.add(h)
        }
        return lit ?: emptySet()
    }

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
     * Producing own neighbors of [hex] — the MARKET rule's countable: owned by
     * [player], non-starving, flora-free. Same sharing contract as [shoalsWithin]:
     * [incomeFrom], the AI's market valuation, and the app's income breakdown all
     * count through here — they must never drift apart.
     */
    fun marketNeighbors(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        hex: Hex,
        player: PlayerId,
    ): Int {
        var count = 0
        HexMath.forEachNeighbor(hex) { n ->
            val t = tiles[n]
            if (t != null && t.owner == player && !t.starving && t.flora == null) count++
        }
        return count
    }

    /** Own tree hexes next to [hex] — the LUMBER_CAMP rule's countable (same sharing contract as [marketNeighbors]). */
    fun adjacentOwnTrees(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        hex: Hex,
        player: PlayerId,
    ): Int {
        var count = 0
        HexMath.forEachNeighbor(hex) { n ->
            val t = tiles[n]
            if (t != null && t.owner == player && t.flora is com.msa.fightandconquer.core.model.Flora.Tree) count++
        }
        return count
    }

    /**
     * True when [unit] earns [RuleConstants.fishingBoatIncome]: a FISHING_BOAT
     * parked on a FISH_SHOAL sea hex. Ownership is the caller's concern —
     * [boatIncomeFrom] and the app's income breakdown share this predicate.
     */
    fun isEarningFishingBoat(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        unit: GameUnit,
    ): Boolean {
        if (unit.type != UnitType.FISHING_BOAT) return false
        val t = tiles[unit.hex] ?: return false
        return t.terrain == com.msa.fightandconquer.core.model.Terrain.SEA &&
            t.deposit == com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL
    }

    /**
     * The rules [player] actually plays with: the game's [RuleConstants] filtered
     * through their civilization's delta table ([CivModifiers.effective] — identity
     * for KINGDOM and when [RuleConstants.civBonusesEnabled] is off), then their
     * completed research ([ResearchModifiers.effective] — identity with research
     * off or nothing completed). Every owner-dependent accessor below resolves
     * through this. Raw `state.config.rules.unitCost`/`unitUpkeep`/
     * `soldierMoveRanges`/`maxTier` reads stay valid everywhere — but soldier
     * STRENGTH is `tier + unitAttackBonus` under research (the audited
     * exception), so strength must always be read through [strengthOf]/[buyStrength].
     */
    fun effectiveRules(state: GameState, player: PlayerId): RuleConstants {
        val p = state.player(player)
        return ResearchModifiers.effective(CivModifiers.effective(state.config.rules, p.civ), p.research)
    }

    /** Attack/capture power of a unit: tier for soldiers, per-type for specials — plus the owner's attack research. */
    fun strengthOf(state: GameState, unit: GameUnit): Int =
        strengthIn(effectiveRules(state, unit.owner), unit.tier, unit.type)

    /** [strengthOf] for a [player]'s unit that doesn't exist yet (buy-capture legality, cargo). */
    fun buyStrength(state: GameState, player: PlayerId, tier: Int, type: UnitType): Int =
        strengthIn(effectiveRules(state, player), tier, type)

    private fun strengthIn(rules: RuleConstants, tier: Int, type: UnitType): Int = when (type) {
        UnitType.SOLDIER -> tier + rules.unitAttackBonus
        UnitType.ARCHER -> rules.archerStrength + rules.unitAttackBonus
        UnitType.CATAPULT -> rules.catapultStrength + rules.unitAttackBonus
        // Working hulls have no attack to improve: giving them one would perturb
        // the attacker-wins tie rule at sea for boats that can never initiate.
        UnitType.TRANSPORT, UnitType.FISHING_BOAT -> 0
        UnitType.WARSHIP -> rules.warshipStrength + rules.unitAttackBonus
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

    // Explicit arms (no strengthIn fallthrough): the attack research bonus must
    // never leak into defense. ARMORY's unitDefenseBonus lifts the land garrison
    // kinds; the warship's sink threshold deliberately follows warshipStrength
    // alone (SIEGECRAFT/SHIPWRIGHTS raise it, ARMORY does not).
    private fun defenseIn(rules: RuleConstants, tier: Int, type: UnitType): Int = when (type) {
        UnitType.SOLDIER -> tier + rules.unitDefenseBonus
        UnitType.ARCHER -> rules.archerAuraDefense + rules.unitDefenseBonus
        UnitType.CATAPULT -> rules.catapultStrength + rules.unitDefenseBonus
        UnitType.TRANSPORT, UnitType.FISHING_BOAT -> 0
        UnitType.WARSHIP -> rules.warshipStrength
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
            UnitType.TRANSPORT -> rules.transportUpkeep
            UnitType.WARSHIP -> rules.warshipUpkeep
            UnitType.FISHING_BOAT -> rules.fishingBoatUpkeep
            else -> landUpkeep(unit.tier, unit.type, rules)
        }
        val cargo = unit.cargo?.let { landUpkeep(it.tier, it.type, rules) } ?: 0
        return own + cargo
    }

    /** Upkeep of a land unit — the only kinds that ride as cargo (boats never carry boats). */
    private fun landUpkeep(tier: Int, type: UnitType, rules: RuleConstants): Int = when (type) {
        UnitType.SOLDIER -> rules.unitUpkeep[tier - 1]
        UnitType.ARCHER -> rules.archerUpkeep
        UnitType.CATAPULT -> rules.catapultUpkeep
        UnitType.TRANSPORT, UnitType.WARSHIP, UnitType.FISHING_BOAT -> 0
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
     * and tower/capital coverage (self + adjacent). Neutral hexes defend at 0 —
     * unless a night [com.msa.fightandconquer.core.model.Monster] squats the hex:
     * it defends itself at its tier (a creature, not a building — never zeroed
     * by siege; no aura to neighbors).
     * A capture requires attacker strength STRICTLY greater than this.
     * A CATAPULT [attackerType] ignores building contributions entirely
     * (units still defend at full value).
     */
    fun defenseOf(state: GameState, hex: Hex, attackerType: UnitType? = null): Int =
        defenseFrom(state.tiles, state.units, hex, attackerType) { effectiveRules(state, it) }

    /**
     * State-shape-agnostic core of [defenseOf], shared with the engine's
     * StateBuilder (the [visibleHexesFrom] pattern): NightPipeline prices a
     * monster's targets against the SAME defense model players face —
     * towers, garrisons and auras protect at night exactly as by day.
     */
    internal fun defenseFrom(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        units: Map<UnitId, GameUnit>,
        hex: Hex,
        attackerType: UnitType? = null,
        effectiveRulesOf: (PlayerId) -> RuleConstants,
    ): Int {
        val tile = tiles[hex] ?: return 0
        fun unitAt(h: Hex): GameUnit? = tiles[h]?.unit?.let { units[it] }
        fun unitDefense(unit: GameUnit): Int =
            if (isNaval(unit.type)) 0 else defenseIn(effectiveRulesOf(unit.owner), unit.tier, unit.type)
        val monsterDefense = tile.monster?.let { monsterDefenseOf(it) } ?: 0
        val owner = tile.owner ?: return monsterDefense
        val siege = attackerType == UnitType.CATAPULT
        fun fortification(building: Building?): Int =
            if (siege || building == null) 0 else buildingDefenseIn(effectiveRulesOf(owner), building)
        var defense = maxOf(monsterDefense, fortification(tile.building))
        unitAt(hex)?.let { defense = maxOf(defense, unitDefense(it)) }
        HexMath.forEachNeighbor(hex) { n ->
            val neighborTile = tiles[n]
            if (neighborTile?.owner == owner) {
                unitAt(n)?.let { defense = maxOf(defense, unitDefense(it)) }
                defense = maxOf(defense, fortification(neighborTile.building))
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

        /** A night monster defending its own hex. */
        data class Monster(val monster: com.msa.fightandconquer.core.model.Monster) : DefenseSource
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
        // The squatting monster explains itself first (mirrors defenseOf's max).
        tile.monster?.let { consider(monsterDefenseOf(it)) { DefenseSource.Monster(it) } }
        val owner = tile.owner ?: return best
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
        return buildingDefenseIn(effectiveRules(state, owner), building)
    }

    /** [buildingDefense] against pre-resolved effective rules (shared with [defenseFrom]). */
    private fun buildingDefenseIn(rules: RuleConstants, building: Building): Int = when (building) {
        Building.TOWER -> rules.towerDefense
        Building.STRONG_TOWER -> rules.strongTowerDefense
        Building.CAPITAL -> rules.capitalDefense
        Building.FORTRESS -> rules.fortressDefense
        Building.FARM, Building.MINE, Building.MARKET,
        Building.LUMBER_CAMP, Building.WATCHTOWER, Building.PORT,
        Building.FISHERY, Building.BRIDGE, Building.UNIVERSITY, Building.BANK,
        Building.BARRACKS, Building.ARCHERY_RANGE, Building.SIEGE_WORKSHOP,
        -> 0
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
     *   specials never merge; the merged tier must be musterable —
     *   [unitAvailable] — so chips, checkMerge and the AI filter as one);
     * - embarkTargets: own empty transports on sea adjacent to the path.
     */
    fun reachable(state: GameState, unitId: UnitId): ReachResult {
        val unit = state.units[unitId] ?: return ReachResult.EMPTY
        if (unit.spent || state.phase !is com.msa.fightandconquer.core.model.GamePhase.Playing) return ReachResult.EMPTY
        if (isNaval(unit.type)) return seaReachable(state, unit)
        val rules = state.config.rules
        val strength = strengthOf(state, unit)
        val maxRange = moveRangeOf(state, unit)
        // Once per call, not per neighbor: identity while the muster flag is off.
        val mergeAllowed = unitAvailable(state, unit.owner, unit.tier + 1, UnitType.SOLDIER)
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
                            // A monster squatting an OWN hex blocks it like hostile
                            // ground: never traversed, attacked as the final step
                            // (strictly-greater, exactly the frontier rule). Only the
                            // monster defends here — own towers never shield it
                            // against its own landlord.
                            tile.owner == unit.owner && tile.monster != null -> {
                                if (n !in capture && n !in blocked) {
                                    if (strength > monsterDefenseOf(tile.monster)) capture.add(n)
                                    else blocked.add(n)
                                }
                            }
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
                                        occupant.tier == unit.tier && unit.tier < rules.maxTier &&
                                        mergeAllowed -> merge.add(n)
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
        val blocked = HashSet<Hex>() // enemy hulls that out-gun this ship (UI chips)
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
                        } else if (unit.type == UnitType.WARSHIP && n !in capture && n !in blocked) {
                            // Attack: an enemy boat blocks the water it sits on.
                            // Too strong a hull -> blockedTargets, exactly as the
                            // land BFS reports too-defended frontier hexes.
                            val defender = state.unitAt(n)
                            if (defender != null && defender.owner != unit.owner &&
                                isNaval(defender.type) &&
                                state.tiles[n]?.building == null
                            ) {
                                if (strength >= strengthOf(state, defender)) capture.add(n)
                                else blocked.add(n)
                            }
                        }
                    }
                }
            }
            frontier = next
            depth++
        }
        return ReachResult(move, capture, mergeTargets = emptySet(), blockedTargets = blocked)
    }

    /** Cost of the player's NEXT farm: base + step per farm already owned (civ-priced). */
    fun nextFarmCost(state: GameState, player: PlayerId): Int {
        val rules = effectiveRules(state, player)
        return rules.farmCostBase + rules.farmCostStep * state.farmCount(player)
    }

    /** What [player] pays for a fresh building, at their effective rules. */
    fun buildingCost(state: GameState, player: PlayerId, type: com.msa.fightandconquer.core.model.BuildingType): Int =
        structureCost(effectiveRules(state, player), type, state.farmCount(player))

    /**
     * The one price table for structures, shared by [buildingCost] and
     * [demolishRefund]. [farmCount] is the number of farms already standing that
     * the next farm is priced against — the buy path passes the current count,
     * the refund path the count minus the farm being torn down.
     */
    private fun structureCost(
        rules: RuleConstants,
        type: com.msa.fightandconquer.core.model.BuildingType,
        farmCount: Int,
    ): Int = when (type) {
        com.msa.fightandconquer.core.model.BuildingType.FARM ->
            rules.farmCostBase + rules.farmCostStep * farmCount
        com.msa.fightandconquer.core.model.BuildingType.TOWER -> rules.towerCost
        com.msa.fightandconquer.core.model.BuildingType.STRONG_TOWER -> rules.strongTowerCost
        com.msa.fightandconquer.core.model.BuildingType.MINE -> rules.mineCost
        com.msa.fightandconquer.core.model.BuildingType.MARKET -> rules.marketCost
        com.msa.fightandconquer.core.model.BuildingType.LUMBER_CAMP -> rules.lumberCampCost
        com.msa.fightandconquer.core.model.BuildingType.WATCHTOWER -> rules.watchtowerCost
        com.msa.fightandconquer.core.model.BuildingType.PORT -> rules.portCost
        com.msa.fightandconquer.core.model.BuildingType.FISHERY -> rules.fisheryCost
        com.msa.fightandconquer.core.model.BuildingType.BRIDGE -> rules.bridgeCost
        com.msa.fightandconquer.core.model.BuildingType.UNIVERSITY -> rules.universityCost
        com.msa.fightandconquer.core.model.BuildingType.BANK -> rules.bankCost
        com.msa.fightandconquer.core.model.BuildingType.FORTRESS -> rules.fortressCost
        com.msa.fightandconquer.core.model.BuildingType.BARRACKS -> rules.barracksCost
        com.msa.fightandconquer.core.model.BuildingType.ARCHERY_RANGE -> rules.archeryRangeCost
        com.msa.fightandconquer.core.model.BuildingType.SIEGE_WORKSHOP -> rules.siegeWorkshopCost
    }

    // --- Research (see docs/game-rules.md "Research") ---

    /**
     * The tech that unlocks purchasing [type], or null when it is never
     * research-gated. Consulted only in researchEnabled games, and only for
     * PURCHASE — captured or map-authored buildings keep working without it.
     */
    fun requiredTech(type: com.msa.fightandconquer.core.model.BuildingType): com.msa.fightandconquer.core.model.Tech? =
        when (type) {
            com.msa.fightandconquer.core.model.BuildingType.STRONG_TOWER -> com.msa.fightandconquer.core.model.Tech.MASONRY
            com.msa.fightandconquer.core.model.BuildingType.PORT -> com.msa.fightandconquer.core.model.Tech.NAVIGATION
            com.msa.fightandconquer.core.model.BuildingType.BANK -> com.msa.fightandconquer.core.model.Tech.BANKING
            com.msa.fightandconquer.core.model.BuildingType.FORTRESS -> com.msa.fightandconquer.core.model.Tech.ENGINEERING
            else -> null
        }

    /** The research-line buildings: only offered at all in researchEnabled games. */
    private fun isResearchLine(type: com.msa.fightandconquer.core.model.BuildingType): Boolean =
        type == com.msa.fightandconquer.core.model.BuildingType.UNIVERSITY ||
            type == com.msa.fightandconquer.core.model.BuildingType.BANK ||
            type == com.msa.fightandconquer.core.model.BuildingType.FORTRESS

    /**
     * Whether [player] may currently purchase [type] under the research rules —
     * the single availability predicate shared by Legality and the AI (they must
     * never drift). Flag off: research-line buildings are not offered and the
     * gated classics (Strong Tower, Port) sell ungated — the pre-research game.
     * Flag on: [requiredTech] must be completed. [RuleConstants.disabledBuildings]
     * is a separate, game-wide gate checked by Legality alongside this.
     */
    fun buildingAvailable(state: GameState, player: PlayerId, type: com.msa.fightandconquer.core.model.BuildingType): Boolean {
        if (!state.config.rules.researchEnabled) return !isResearchLine(type)
        val tech = requiredTech(type) ?: return true
        return state.player(player).research.has(tech)
    }

    /**
     * Standing, non-starving Universities owned by [player] — each adds one
     * progress point at the owner's turn start. Shared by Legality (starting
     * research requires one), TurnPipeline's tick, and the HUD's turns-left
     * projection (the marketNeighbors sharing contract).
     */
    fun workingUniversities(tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>, player: PlayerId): Int =
        tiles.values.count {
            it.owner == player && it.building == Building.UNIVERSITY && !it.starving
        }

    // --- Muster buildings (see docs/game-rules.md "Muster buildings") ---

    /** The realm-wide buildings that gate creating a [tier]/[type] unit — empty when ungated. */
    fun requiredBuildingsFor(tier: Int, type: UnitType): List<Building> = when (type) {
        UnitType.SOLDIER -> when {
            // The Peasant stays free: recruiting can never fully deadlock.
            tier <= 1 -> emptyList()
            tier <= 3 -> listOf(Building.BARRACKS)
            // The Knight additionally needs the heavy keep.
            else -> listOf(Building.BARRACKS, Building.FORTRESS)
        }
        UnitType.ARCHER -> listOf(Building.ARCHERY_RANGE)
        UnitType.CATAPULT -> listOf(Building.SIEGE_WORKSHOP)
        // Boats are placement-gated by an adjacent working Port already.
        UnitType.TRANSPORT, UnitType.WARSHIP, UnitType.FISHING_BOAT -> emptyList()
    }

    /**
     * At least one standing, non-starving [building] owned by [player] anywhere in
     * the realm — the [workingUniversities] predicate, realm-wide.
     */
    fun hasWorkingBuilding(
        tiles: Map<Hex, com.msa.fightandconquer.core.model.Tile>,
        player: PlayerId,
        building: Building,
    ): Boolean = tiles.values.any { it.owner == player && it.building == building && !it.starving }

    /**
     * The first missing prerequisite for creating a [tier]/[type] unit, or null
     * when creation is allowed. Binds CREATION by the player — direct buy,
     * buy-merge and MergeUnits alike — while scripted spawns, disembarks and
     * map-authored units stay exempt (the [requiredTech] doctrine: authored
     * content keeps working). The single predicate shared by Legality,
     * buyableAt's locked cards and the AI — they must never drift. The UI
     * derives the rejection's missing building through this (the
     * BUILDING_NEEDS_RESEARCH convention: the reason code carries no payload).
     * A required building sitting in [RuleConstants.disabledBuildings] still
     * gates: a campaign that disables the Barracks with the flag on has
     * deliberately removed tier 2+ from that mission.
     */
    fun missingUnitBuilding(state: GameState, player: PlayerId, tier: Int, type: UnitType): Building? {
        if (!state.config.rules.militaryBuildingsRequired) return null
        return requiredBuildingsFor(tier, type).firstOrNull { !hasWorkingBuilding(state.tiles, player, it) }
    }

    /** Whether [player] may currently create a [tier]/[type] unit under the muster rules. */
    fun unitAvailable(state: GameState, player: PlayerId, tier: Int, type: UnitType): Boolean =
        missingUnitBuilding(state, player, tier, type) == null

    /** Gold paid up front to start [tech], at [player]'s effective rules. */
    fun techCost(state: GameState, player: PlayerId, tech: com.msa.fightandconquer.core.model.Tech): Int =
        effectiveRules(state, player).techCostByTier[tech.tier - 1]

    /** Progress points [tech] needs to complete, at [player]'s effective rules. */
    fun techDuration(state: GameState, player: PlayerId, tech: com.msa.fightandconquer.core.model.Tech): Int =
        effectiveRules(state, player).techDurationByTier[tech.tier - 1]

    /**
     * Treasury credit for demolishing an own [building]:
     * [RuleConstants.demolishRefundPercent] of its cost, integer division.
     * A FARM refunds against the LAST farm's price (base + step × (count − 1)),
     * never [nextFarmCost] — otherwise build-then-demolish would turn a profit.
     * CAPITAL never reaches here (Legality forbids demolishing it).
     */
    fun demolishRefund(state: GameState, player: PlayerId, building: Building): Int {
        val type = com.msa.fightandconquer.core.model.BuildingType.entries
            .firstOrNull { it.building == building } ?: return 0 // CAPITAL
        val rules = effectiveRules(state, player)
        val cost = structureCost(rules, type, (state.farmCount(player) - 1).coerceAtLeast(0))
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

    /**
     * The treasury penalty [breaker] pays for aggression against a pact partner —
     * the exact charge `StateBuilder.breakPact` transfers, so UI confirmations
     * can promise the engine's own number.
     */
    fun pactBreakPenalty(state: GameState, breaker: PlayerId): Int =
        pactBreakPenaltyOf(state.player(breaker).treasury, state.config.rules)

    /** [pactBreakPenalty] against a raw treasury — the formula StateBuilder shares. */
    internal fun pactBreakPenaltyOf(treasury: Int, rules: RuleConstants): Int =
        treasury * rules.pactBreakPenaltyPercent / 100

    /** Income the player will collect at turn start: producing hexes, deposits, buildings, parked boats. */
    fun incomeOf(state: GameState, player: PlayerId): Int {
        val eff = effectiveRules(state, player)
        return scaleIncome(
            incomeFrom(state.tiles, eff, player) +
                boatIncomeFrom(state.tiles, state.units.values, eff, player),
            eff,
        )
    }

    /**
     * The one place [RuleConstants.incomePercent] applies (Coinage/Treasury):
     * once, to the TOTAL — shared by [incomeOf] and TurnPipeline's incomeIn,
     * which must never drift (the [incomeFrom] sharing contract).
     */
    internal fun scaleIncome(raw: Int, rules: RuleConstants): Int = raw * rules.incomePercent / 100

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
        if (u.owner == player && isEarningFishingBoat(tiles, u)) rules.fishingBoatIncome else 0
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
            // A night monster pillages what it squats: the hex earns nothing
            // until it is slain or dawn takes it.
            if (tile.monster != null) continue
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
                Building.MARKET ->
                    income += rules.marketNeighborIncome *
                        minOf(marketNeighbors(tiles, hex, player), rules.marketNeighborCap)
                Building.LUMBER_CAMP ->
                    income += rules.lumberCampTreeIncome *
                        minOf(adjacentOwnTrees(tiles, hex, player), rules.lumberCampTreeCap)
                Building.PORT -> income += rules.portIncome
                Building.FISHERY -> {
                    val shoals = shoalsWithin(tiles, hex, rules.fisheryRange)
                    income += rules.fisheryShoalIncome * minOf(shoals, rules.fisheryShoalCap)
                }
                Building.BANK -> income += rules.bankIncome
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
                Building.CAPITAL, Building.TOWER, Building.STRONG_TOWER, Building.FORTRESS ->
                    addRange(hex, rules.visionRadiusBuilding)
                Building.WATCHTOWER -> addRange(hex, rules.watchtowerVisionRadius)
                Building.FARM, Building.MINE, Building.MARKET, Building.LUMBER_CAMP,
                Building.PORT, Building.FISHERY, Building.BRIDGE,
                Building.UNIVERSITY, Building.BANK,
                Building.BARRACKS, Building.ARCHERY_RANGE, Building.SIEGE_WORKSHOP, null,
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
