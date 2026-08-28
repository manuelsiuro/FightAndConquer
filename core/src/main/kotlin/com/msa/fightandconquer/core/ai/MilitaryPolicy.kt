package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType

/**
 * Muster-hall decisions as a threshold policy — the single owner of "build the
 * Barracks / Siege Workshop / Archery Range / tier-4 Fortress" (never argmax
 * candidates: a pure prerequisite earns nothing the turn it stands, so its
 * payoff is turns away and invisible to a one-ply comparison — the
 * ResearchPolicy structural argument, verbatim; an Evaluator term could only
 * distort cross-position comparisons). Stateless, deterministic, RNG-free;
 * consulted after research and before the naval ladder, whose marine steps
 * wait on the Barracks this policy funds.
 *
 * Demand-driven: each hall is bought only when a concrete blocked plan is
 * waiting on it — a frontier hex the allowed tiers cannot crack (the ungated
 * Tiers probes read the demand), a threatened throne the allowed garrison
 * cannot hold, or an overseas war whose marines outgrow the peasant. EVERY
 * difficulty builds the Barracks under demand — cracking any defended hex
 * needs tier 2+, so a peasant-locked AI can never eliminate anyone
 * (termination-load-bearing); EASY just demands harder evidence and keeps a
 * deeper reserve.
 */
internal object MilitaryPolicy {

    fun action(state: GameState, difficulty: Difficulty): GameAction? {
        val rules = state.config.rules
        if (!rules.militaryBuildingsRequired) return null
        val me = state.currentPlayer
        val eff = Rules.effectiveRules(state, me)
        val treasury = state.player(me).treasury
        val reserve = when (difficulty) {
            Difficulty.HARD -> 10
            Difficulty.NORMAL -> 20
            else -> 30
        }

        val partners: Set<PlayerId> =
            if (rules.diplomacyEnabled) state.diplomacy.partnersOf(me) else emptySet()

        // The MoveGenerator frontier read: non-owned land next to funded
        // territory, with its defense (fog-legal — everything lies within the
        // visionRadiusOwned >= 2 guarantee).
        val frontier = HashMap<com.msa.fightandconquer.core.hex.Hex, Int>()
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me || tile.starving) continue
            HexMath.forEachNeighbor(hex) { n ->
                if (n !in frontier) {
                    val t = state.tiles[n]
                    if (t != null && t.terrain == Terrain.LAND && t.owner != me && t.owner !in partners) {
                        frontier[n] = Rules.defenseOf(state, n)
                    }
                }
            }
        }
        // The tier the land war is waiting on: the ungated solve for the softest
        // and the hardest crackable frontier hexes (0 = no frontier).
        val landDemandTier = frontier.values.maxOfOrNull { d ->
            Tiers.cheapestBreaker(state, me, d, gated = false) ?: 0
        } ?: 0
        val capitalThreat = Tiers.capitalThreat(state, me)
        val guardDemandTier = if (capitalThreat == 0) {
            0
        } else {
            Tiers.cheapestGarrison(state, me, capitalThreat, gated = false) ?: rules.maxTier
        }
        // Overseas war: no land frontier but a live non-partner enemy — the
        // marine ladder (which saves for tier 2+) is what the hall unlocks.
        val seaDemand = rules.navalEnabled && frontier.isEmpty() &&
            state.players.any { !it.eliminated && it.id != me && it.id !in partners }
        // The tier the SEA war waits on: the weakest fog-visible enemy coastal
        // hex, solved ungated (0 = no naval siege). A coast sealed wall-to-wall
        // with strong towers demands tier 4 — and unlike a land wall there is
        // no catapult path (siege demand never fires without a land frontier),
        // so the heavy keep is the ONLY crack. A tier-3-locked island pair
        // stalemates forever otherwise (the peasant-locked lesson, at sea).
        val seaDemandTier = if (!seaDemand) {
            0
        } else {
            val visible = if (rules.fogOfWar) Rules.visibleHexes(state, me) else null
            var minCoast = Int.MAX_VALUE
            for ((hex, tile) in state.tiles) {
                if (tile.terrain != Terrain.LAND) continue
                val owner = tile.owner ?: continue
                if (owner == me || owner in partners) continue
                if (visible != null && hex !in visible) continue
                if (HexMath.neighbors(hex).none { state.tiles[it]?.terrain == Terrain.SEA }) continue
                val d = Rules.defenseOf(state, hex)
                if (d < minCoast) minCoast = d
            }
            if (minCoast == Int.MAX_VALUE) {
                0
            } else {
                Tiers.cheapestBreaker(state, me, minCoast, gated = false) ?: 0
            }
        }

        fun wantsHall(type: BuildingType, building: Building): Boolean =
            type !in rules.disabledBuildings && !Rules.hasWorkingBuilding(state.tiles, me, building)

        fun buyAt(type: BuildingType, cost: Int): GameAction? {
            interiorBuildSpot(state, me)?.let { return GameAction.BuyBuilding(type, it) }
            // Fully built-up realm and the war hangs on this hall: reclaim ground
            // (the ResearchPolicy sea-locked idiom — termination-load-bearing).
            if (treasury >= cost * 2) {
                NavalPolicy.demolishForRoom(state, coastal = false)?.let { return it }
            }
            return null
        }

        // 1. The Barracks: the base school every tier-2+ plan waits on.
        if (wantsHall(BuildingType.BARRACKS, Building.BARRACKS)) {
            val demand = when (difficulty) {
                // EASY waits for hard evidence: a real fortification line
                // (demand past tier 2) or a threatened throne.
                Difficulty.EASY -> landDemandTier > 2 || guardDemandTier >= 2
                else -> landDemandTier >= 2 || guardDemandTier >= 2 || seaDemand
            }
            if (demand && treasury >= eff.barracksCost + reserve) {
                buyAt(BuildingType.BARRACKS, eff.barracksCost)?.let { return it }
            }
        }
        if (difficulty == Difficulty.EASY) return null

        // 2. The Siege Workshop: building defense is the blocker and a catapult
        //    cracks it (the MoveGenerator catapult demand signal).
        if (rules.specialUnitsEnabled && wantsHall(BuildingType.SIEGE_WORKSHOP, Building.SIEGE_WORKSHOP)) {
            val siegeDemand = frontier.any { (hex, defense) ->
                val siegeDefense = Rules.defenseOf(state, hex, UnitType.CATAPULT)
                defense > siegeDefense && siegeDefense < eff.catapultStrength
            }
            if (siegeDemand && treasury >= eff.siegeWorkshopCost + eff.catapultCost + reserve) {
                buyAt(BuildingType.SIEGE_WORKSHOP, eff.siegeWorkshopCost)?.let { return it }
            }
        }

        // 3. The Archery Range: a defensive garnish — only at war, only wealthy,
        //    lowest priority.
        if (rules.specialUnitsEnabled && wantsHall(BuildingType.ARCHERY_RANGE, Building.ARCHERY_RANGE)) {
            val atWar = frontier.isNotEmpty() || capitalThreat > 0
            if (atWar && treasury >= eff.archeryRangeCost + eff.archerCost + reserve * 2) {
                buyAt(BuildingType.ARCHERY_RANGE, eff.archeryRangeCost)?.let { return it }
            }
        }

        // 4. The tier-4 Fortress: the ungated demand solves to the Knight and
        //    only the heavy keep unlocks it. On LAND this stays HARD-only and
        //    non-hazard when locked: tier 3 + catapult still cracks any
        //    fortification (catapults zero building defense). At SEA the
        //    catapult escape does not exist, so NORMAL also founds it when the
        //    weakest enemy beach demands tier 4 — termination-load-bearing.
        if ((difficulty == Difficulty.HARD || (difficulty == Difficulty.NORMAL && seaDemandTier >= 4)) &&
            Rules.hasWorkingBuilding(state.tiles, me, Building.BARRACKS) &&
            maxOf(landDemandTier, guardDemandTier, seaDemandTier) >= 4 &&
            !Rules.hasWorkingBuilding(state.tiles, me, Building.FORTRESS) &&
            BuildingType.FORTRESS !in rules.disabledBuildings &&
            Rules.buildingAvailable(state, me, BuildingType.FORTRESS) &&
            treasury >= eff.fortressCost + reserve
        ) {
            buyAt(BuildingType.FORTRESS, eff.fortressCost)?.let { return it }
        }
        return null
    }
}
