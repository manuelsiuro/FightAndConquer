package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Legality
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitType

/**
 * Deterministic invasion ladder, consulted before the greedy argmax (like
 * DiplomacyPolicy). A single-action greedy AI cannot see a multi-turn ferry
 * plan — embark, sail, land — because every intermediate step scores worse
 * than doing nothing. This policy makes island conquest a scripted certainty
 * instead of an evaluator-tuning hope, which is what keeps island games
 * terminating.
 *
 * Landing cargo is always worth doing; the expansion steps (sail/embark/
 * buy transport/found port) engage only in "overseas mode": living
 * non-partner enemies exist but none are reachable over land.
 *
 * Fog: the ladder navigates by TERRAIN only (island shapes are chart
 * knowledge, like the pre-discovered sea); it never reads unit or ownership
 * state of unseen hexes beyond "not mine", which the player always knows.
 */
internal object NavalPolicy {

    /** Keep the AI's ferry fleet small — this is a supply line, not an armada. */
    private const val MAX_TRANSPORTS = 3

    /** Savings can bankroll upkeep for this many turns when income can't carry it. */
    private const val SAVINGS_TURNS = 5

    /** A treasury this deep means the greedy loop has stalled — start force-spending. */
    private const val WAR_CHEST = 300

    fun action(
        state: GameState,
        difficulty: com.msa.fightandconquer.core.model.Difficulty = com.msa.fightandconquer.core.model.Difficulty.NORMAL,
    ): GameAction? {
        if (!state.config.rules.navalEnabled) return null
        val easy = difficulty == com.msa.fightandconquer.core.model.Difficulty.EASY
        val me = state.currentPlayer
        val rules = state.config.rules
        val partners: Set<com.msa.fightandconquer.core.model.PlayerId> =
            if (rules.diplomacyEnabled) {
                state.diplomacy.pacts.mapNotNull {
                    when (me) {
                        it.a -> it.b
                        it.b -> it.a
                        else -> null
                    }
                }.toSet()
            } else {
                emptySet()
            }

        val myUnits = state.units.values
            .filter { it.owner == me }
            .sortedBy { it.id.value }
        val transports = myUnits.filter { it.type == UnitType.TRANSPORT }

        // The capital's landmass. Ferries exist to LEAVE it: never disembark back
        // onto it, and empty boats steer back to it for the next passenger.
        val homeland: Set<Hex> = state.player(me).capital?.let { capital ->
            HexMath.floodFill(capital) { state.tiles[it]?.terrain == Terrain.LAND }
        } ?: emptySet()

        // Decapitation focus: landings and sailing aim at enemy capitals — their
        // 50% loot is the snowball that DECIDES island wars; aimless coastal
        // raiding just seesaws forever. Under fog only SCOUTED capitals count
        // (ever-discovered ground is honest intel — capitals never move on
        // their own). Easy raids aimlessly on purpose — it is the beatable one.
        val enemyCapitals: List<Hex> =
            if (easy) {
                emptyList()
            } else {
                state.players
                    .filter { !it.eliminated && it.id != me && it.id !in partners }
                    .mapNotNull { it.capital }
                    .filter { !rules.fogOfWar || it in state.player(me).discovered }
            }

        // 1. Land cargo on foreign shores whenever a transport can — beachheads
        //    before everything.
        for (boat in transports) {
            if (boat.spent || boat.cargo == null) continue
            bestDisembark(state, boat, homeland, enemyCapitals)?.let { return it }
        }

        // 2. Expedition port: a starving OVERSEAS colony gets a harbor NOW — it is
        //    the only thing that stops the beachhead from dying. Mainland slices
        //    (same landmass as the capital) are deliberately left to starve:
        //    port-rescuing every cut region would neutralize slicing entirely
        //    and stalemate land wars.
        if (state.player(me).treasury >= rules.portCost &&
            state.tiles.values.any { it.owner == me && it.starving }
        ) {
            portSpot(state, starvingOnly = true)?.let { spot ->
                if (spot !in homeland) {
                    return GameAction.BuyBuilding(com.msa.fightandconquer.core.model.BuildingType.PORT, spot)
                }
            }
        }

        // The strongest soldier tier the economy can carry — income first, deep
        // savings as a war chest (a defended beach is only ever cracked by a
        // STRONGER landing party, so the ladder must escalate past defenders).
        val net = Rules.incomeOf(state, me) - Rules.upkeepOf(state, me)
        val treasury = state.player(me).treasury
        fun sustainable(cost: Int, upkeep: Int): Boolean =
            treasury >= cost && (net >= upkeep || treasury >= cost + upkeep * SAVINGS_TURNS)
        val bestTier: Int? = (rules.maxTier downTo 1).firstOrNull { t ->
            sustainable(rules.unitCost[t - 1], rules.unitUpkeep[t - 1])
        }

        // 2b. War-chest assault: a swollen treasury means the greedy loop has
        //     refused every purchase for many turns (its diminishing-income curve
        //     vetoes high-upkeep units) while a defended front stands. Spend the
        //     savings on exactly the unit that cracks the weakest frontier hex —
        //     guaranteed forward progress in any stalled war, land or sea.
        //     Easy hoards instead — cracking walls is what it is bad at.
        if (!easy && treasury >= WAR_CHEST) {
            val frontier = ArrayList<Pair<Hex, Int>>()
            for ((hex, tile) in state.tiles) {
                if (tile.owner != me || tile.starving) continue
                HexMath.forEachNeighbor(hex) { n ->
                    val t = state.tiles[n]
                    if (t != null && t.terrain == Terrain.LAND && t.owner != me &&
                        t.owner != null && t.owner !in partners &&
                        frontier.none { it.first == n }
                    ) {
                        frontier.add(n to Rules.defenseOf(state, n))
                    }
                }
            }
            val target = frontier
                .filter { (_, defense) -> defense + 1 <= rules.maxTier }
                .minWithOrNull(compareBy({ it.second }, { it.first.packed }))
            if (target != null) {
                val action = GameAction.BuyUnit(target.second + 1, target.first)
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
            // Bridge shortcut: a single span from our shore to foreign land turns
            // the war chest into a permanent land route — cheaper than a ferry.
            val strait = state.tiles.entries
                .filter { (hex, tile) ->
                    tile.terrain == Terrain.SEA && tile.building == null && tile.unit == null &&
                        HexMath.neighbors(hex).any {
                            val t = state.tiles[it]
                            t?.owner == me && !t.starving && t.terrain == Terrain.LAND
                        } &&
                        HexMath.neighbors(hex).any {
                            val t = state.tiles[it]
                            t != null && t.terrain == Terrain.LAND && t.owner != me &&
                                t.owner !in partners
                        }
                }
                .minByOrNull { it.key.packed }?.key
            if (strait != null) {
                val action = GameAction.BuyBuilding(
                    com.msa.fightandconquer.core.model.BuildingType.BRIDGE,
                    strait,
                )
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
            // No enemy in reach? Reclaim the overgrown economy instead: the greedy
            // loop refuses peasants-on-trees whenever net income is negative, and
            // a fully forested kingdom is exactly how it ends up idle and rich.
            val overgrown = state.tiles.entries
                .filter { (_, tile) ->
                    tile.owner == me && !tile.starving &&
                        tile.flora is com.msa.fightandconquer.core.model.Flora.Tree &&
                        tile.unit == null && tile.building == null
                }
                .minByOrNull { it.key.packed }?.key
            if (overgrown != null) {
                val action = GameAction.BuyUnit(1, overgrown)
                if (Legality.check(state, action) is LegalityResult.Ok) return action
            }
        }

        // Where a fresh marine could be raised (flora is fine — the recruit
        // clears it; gravestones are left to become trees).
        val musterHexes = state.tiles.entries
            .filter { (_, tile) ->
                tile.owner == me && tile.terrain == Terrain.LAND && !tile.starving &&
                    tile.unit == null && tile.building == null &&
                    tile.flora !is com.msa.fightandconquer.core.model.Flora.Gravestone
            }
            .map { it.key }
        val musterSpot = musterHexes.minByOrNull { it.packed }

        // 3. Sail each loaded transport toward a beach ITS CARGO CAN ACTUALLY
        //    TAKE: enemy/neutral coast with defense below the cargo's strength,
        //    or an own overseas hex it may reinforce. Purely positional — no
        //    memory, no oscillation: the destination flips only when defenses
        //    genuinely change. Loaded convoys are COMMITTED: they sail
        //    regardless of overseas mode (freezing them mid-route whenever a
        //    land skirmish opens somewhere is how fleets rot at sea).
        //    Capital shores are preferred when beatable — their loot decides.
        //
        // 3b. A boat whose cargo can beat NO beach anywhere sails home and
        //     unloads: the weak marine turns garrison, the hull reloads with a
        //     stronger one (raised by 4b). Without this, fleets loaded in
        //     poorer times park at hopeless fronts forever and wars deadlock.
        fun beatable(hex: Hex, strength: Int): Boolean {
            val tile = state.tiles.getValue(hex)
            return if (tile.owner == me) {
                tile.unit == null && tile.building == null
            } else {
                strength > Rules.defenseOf(state, hex)
            }
        }
        val warCoast = state.tiles.entries
            .filter { (hex, tile) ->
                tile.terrain == Terrain.LAND && hex !in homeland && tile.owner !in partners
            }
            .map { it.key }
        for (boat in transports) {
            if (boat.spent) continue
            val cargo = boat.cargo ?: continue
            val strength = Rules.buyStrength(rules, cargo.tier, cargo.type)
            // Easy sails blunt: any coast looks good, hopeless fronts included,
            // and it never recycles a doomed marine — that blindness is the
            // difficulty gap at sea.
            val strikeable = if (easy) warCoast else warCoast.filter { beatable(it, strength) }
            if (strikeable.isNotEmpty()) {
                val nearCapital = if (enemyCapitals.isEmpty()) {
                    emptyList()
                } else {
                    strikeable.filter { s -> enemyCapitals.any { HexMath.distance(s, it) <= 2 } }
                }
                // Capital shores first — but they are a PREFERENCE, not a cage:
                // when their approach is blocked (often by the convoy's own
                // hulls), fall back to any beach the cargo can take, or the
                // fleet freezes at sea forever while the war stalemates.
                sailToward(state, boat, nearCapital)?.let { return it }
                sailToward(state, boat, strikeable)?.let { return it }
            } else {
                // 3b: nothing to strike — bring the marine home as garrison.
                val homeLanding = HexMath.neighbors(boat.hex)
                    .filter { it in homeland }
                    .map { GameAction.Disembark(boat.id, it) }
                    .firstOrNull { Legality.check(state, it) is LegalityResult.Ok }
                homeLanding?.let { return it }
                val landable = homeland.filter { h ->
                    val t = state.tiles[h]
                    t != null && t.owner == me && t.unit == null && t.building == null
                }
                sailToward(state, boat, landable)?.let { return it }
            }
        }

        // 2c. Sea control (HARD only): enemy ferries on the water are an
        //     invasion in progress — the greedy loop never buys the warship
        //     (upkeep repels its one-ply evaluator before any sink pays off),
        //     so interdiction is a threshold decision. One hunter, kept on the
        //     prey's wake; the greedy loop lands the actual kill (+4/boat term).
        //     Kept off NORMAL: symmetric interdiction wars drag mirrors past
        //     every termination bound — this is Hard's edge, not the default.
        if (difficulty == com.msa.fightandconquer.core.model.Difficulty.HARD) {
            val visibleNow = if (rules.fogOfWar) Rules.visibleHexes(state, me) else null
            val prey = state.units.values.filter {
                it.owner != me && it.owner !in partners && Rules.isNaval(it.type) &&
                    (visibleNow == null || it.hex in visibleNow)
            }
            if (prey.isNotEmpty()) {
                val myWarships = myUnits.filter { it.type == UnitType.WARSHIP }
                if (myWarships.isEmpty() && sustainable(rules.warshipCost, rules.warshipUpkeep)) {
                    launchSpot(state)?.let { return GameAction.BuyUnit(1, it, UnitType.WARSHIP) }
                }
                // Chase FERRIES only, and never pre-empt a kill the greedy loop
                // can already take this turn — sailing spends the ship, and a
                // hunter that parks spent beside an enemy warship just donates
                // itself (both AIs did, trading hulls forever).
                val ferries = prey.filter { it.type == UnitType.TRANSPORT }.map { it.hex }
                for (ship in myWarships) {
                    if (ship.spent) continue
                    if (Rules.reachable(state, ship.id).captureTargets.isNotEmpty()) continue
                    sailToward(state, ship, ferries)?.let { return it }
                }
            }
        }

        if (!overseasMode(state, partners)) return null

        // 3c. Empty boats steer to wherever the next passenger actually stands
        //     (a knight garrisoning a conquered island is fetched, not waited
        //     for), else home.
        val passengers = myUnits
            .filter {
                !it.spent && !Rules.isNaval(it.type) &&
                    (bestTier == null || Rules.strengthOf(it, rules) >= bestTier)
            }
            .map { it.hex }
        for (boat in transports) {
            if (boat.spent || boat.cargo != null) continue
            val goals = passengers.ifEmpty { homeland.toList() }
            sailToward(state, boat, goals)?.let { return it }
        }

        // 4. Board the strongest fresh land unit onto a waiting empty transport —
        //    never ship a soldier weaker than what we could raise instead, UNLESS
        //    a fully built-up island leaves no ground to muster on (then the
        //    marine we have beats the marine we can't recruit).
        val boarder = myUnits
            .filter { !it.spent && !Rules.isNaval(it.type) }
            .sortedWith(
                compareByDescending<GameUnit> { Rules.strengthOf(it, rules) }
                    .thenBy { it.id.value },
            )
            .firstNotNullOfOrNull { unit ->
                Rules.reachable(state, unit.id).embarkTargets.minByOrNull { it.packed }
                    ?.let { unit to it }
            }
        if (boarder != null) {
            val (unit, target) = boarder
            if (bestTier == null || musterSpot == null ||
                Rules.strengthOf(unit, rules) >= bestTier
            ) {
                return GameAction.MoveUnit(unit.id, target)
            }
        }

        // 4b. Raise the marine the beach actually needs. Guarded so a stranded
        //     strong unit (boat en route home) never triggers duplicate buys.
        val emptyBoatWaiting = transports.any { it.cargo == null }
        val hasStrongFresh = bestTier != null && myUnits.any {
            !it.spent && !Rules.isNaval(it.type) && Rules.strengthOf(it, rules) >= bestTier
        }
        if (emptyBoatWaiting && bestTier != null && !hasStrongFresh && musterSpot != null) {
            return GameAction.BuyUnit(bestTier, musterSpot)
        }

        // 5. Launch a fresh transport once every boat is loaded (stuck fleets off a
        //    defended coast must not block the next, stronger wave).
        if (transports.size < MAX_TRANSPORTS &&
            transports.none { it.cargo == null } &&
            sustainable(rules.transportCost, rules.transportUpkeep)
        ) {
            launchSpot(state)?.let { return GameAction.BuyUnit(1, it, UnitType.TRANSPORT) }
        }

        // 6. Found the first port on our best coastal hex.
        val hasPort = state.tiles.values.any { it.owner == me && it.building == Building.PORT }
        if (!hasPort && state.player(me).treasury >= rules.portCost) {
            portSpot(state, starvingOnly = false)?.let {
                return GameAction.BuyBuilding(com.msa.fightandconquer.core.model.BuildingType.PORT, it)
            }
        }
        return null
    }

    /**
     * The ferry war is on when living non-partner enemies exist and the land
     * route is either absent or hopeless: no frontier at all, or every frontier
     * hex is walled at defense >= maxTier (a knight wall no land assault can
     * ever crack — flanking by sea is then the only path to a decision).
     */
    private fun overseasMode(state: GameState, partners: Set<com.msa.fightandconquer.core.model.PlayerId>): Boolean {
        val me = state.currentPlayer
        val enemiesAlive = state.players.any {
            it.id != me && !it.eliminated && it.id !in partners
        }
        if (!enemiesAlive) return false
        var minDefense = Int.MAX_VALUE
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me || tile.starving) continue
            HexMath.forEachNeighbor(hex) { n ->
                val t = state.tiles[n]
                if (t != null && t.terrain == Terrain.LAND && t.owner != me && t.owner !in partners) {
                    val d = Rules.defenseOf(state, n)
                    if (d < minDefense) minDefense = d
                }
            }
        }
        return minDefense == Int.MAX_VALUE || minDefense >= state.config.rules.maxTier
    }

    /**
     * Best adjacent landing OFF the homeland: enemy ground first, then neutral,
     * then own colony; within a class, the beach nearest an enemy capital.
     */
    private fun bestDisembark(
        state: GameState,
        boat: GameUnit,
        homeland: Set<Hex>,
        enemyCapitals: List<Hex>,
    ): GameAction? {
        val candidates = HexMath.neighbors(boat.hex)
            .filter { state.tiles[it]?.terrain == Terrain.LAND && it !in homeland }
            .map { GameAction.Disembark(boat.id, it) }
            .filter { Legality.check(state, it) is LegalityResult.Ok }
        return candidates.sortedWith(
            compareByDescending<GameAction.Disembark> { action ->
                val owner = state.tiles.getValue(action.to).owner
                when {
                    owner != null && owner != boat.owner -> 2
                    owner == null -> 1
                    else -> 0
                }
            }
                .thenBy { action -> enemyCapitals.minOfOrNull { HexMath.distance(action.to, it) } ?: 0 }
                .thenBy { it.to.packed },
        ).firstOrNull()
    }

    /**
     * Move to the reachable sea hex strictly nearer to [goals] by SAILED distance
     * (multi-source BFS over water) — straight-line distance strands boats in
     * local minima behind their own island whenever the route bends around land.
     * Only moves that actually close distance are taken (no tide-pacing loops).
     */
    private fun sailToward(state: GameState, boat: GameUnit, goals: Collection<Hex>): GameAction? {
        if (goals.isEmpty()) return null
        val targets = Rules.reachable(state, boat.id).moveTargets
        if (targets.isEmpty()) return null

        fun sailable(hex: Hex): Boolean {
            val t = state.tiles[hex] ?: return false
            return t.terrain == Terrain.SEA && t.building == null
        }
        // Distance field seeded at every sea hex lapping a goal. Other boats are
        // ignored for the FIELD (they drift); actual moves come from moveTargets.
        val dist = HashMap<Hex, Int>()
        var frontier = ArrayList<Hex>()
        for (goal in goals) {
            HexMath.forEachNeighbor(goal) { n ->
                if (sailable(n) && dist.putIfAbsent(n, 0) == null) frontier.add(n)
            }
        }
        var d = 0
        while (frontier.isNotEmpty() && d < 128) {
            d++
            val next = ArrayList<Hex>()
            for (hex in frontier) {
                HexMath.forEachNeighbor(hex) { n ->
                    if (sailable(n) && dist.putIfAbsent(n, d) == null) next.add(n)
                }
            }
            frontier = next
        }
        val here = dist[boat.hex] ?: Int.MAX_VALUE
        val best = targets.minWith(
            compareBy({ dist[it] ?: Int.MAX_VALUE }, { it.packed }),
        )
        val bestDist = dist[best] ?: Int.MAX_VALUE
        return if (bestDist < here) GameAction.MoveUnit(boat.id, best) else null
    }

    /** Lowest-packed legal launch hex: open sea beside an own working port. */
    private fun launchSpot(state: GameState): Hex? {
        val me = state.currentPlayer
        val spots = HashSet<Hex>()
        for ((hex, tile) in state.tiles) {
            if (tile.owner != me || tile.building != Building.PORT || tile.starving) continue
            HexMath.forEachNeighbor(hex) { n ->
                val t = state.tiles[n]
                if (t != null && t.terrain == Terrain.SEA && t.unit == null && t.building == null) {
                    spots.add(n)
                }
            }
        }
        return spots.minByOrNull { it.packed }
    }

    /** Own buildable coastal hex with the most adjacent sea (ties: lowest packed). */
    private fun portSpot(state: GameState, starvingOnly: Boolean): Hex? {
        val me = state.currentPlayer
        return state.tiles.entries
            .filter { (hex, tile) ->
                tile.owner == me && tile.terrain == Terrain.LAND && tile.building == null &&
                    tile.unit == null && tile.flora == null && tile.deposit == null &&
                    (!starvingOnly || tile.starving) &&
                    HexMath.neighbors(hex).any { state.tiles[it]?.terrain == Terrain.SEA }
            }
            .maxWithOrNull(
                compareBy(
                    { (hex, _) -> HexMath.neighbors(hex).count { state.tiles[it]?.terrain == Terrain.SEA } },
                    { (hex, _) -> -(hex.packed) },
                ),
            )?.key
    }
}
