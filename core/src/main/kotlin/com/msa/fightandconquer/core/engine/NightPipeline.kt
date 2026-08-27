package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Monster
import com.msa.fightandconquer.core.model.MonsterKind
import com.msa.fightandconquer.core.model.Terrain

/**
 * The day-night cycle's world step (see docs/game-rules.md "Day-night cycle").
 * Runs exactly ONCE PER ROUND, at the seat-wrap point inside
 * [TurnPipeline.endTurn] — never per seat like the turn-start steps — inside
 * the wrapping player's EndTurn reduction (world events inside another seat's
 * turn boundary are precedented: tree spread, gravestone growth).
 *
 * The phase itself is [Rules.isNight] — a pure function of the round counter,
 * so this object only reacts to phase EDGES and night-interior rounds:
 * - day→night: [GameEvent.NightFell] + a deterministic spawn wave;
 * - night→night: the monster action phase (each monster moves/attacks);
 * - night→day: [GameEvent.DawnBroke] + despawn (survivors may drop a cache).
 *
 * Determinism: candidate sets are iterated `sortedBy { packed }` before any
 * roll ([StateBuilder.rollIndex] — the spreadTrees template); monsters never
 * enter [GameState.units] and never change tile ownership.
 */
internal object NightPipeline {

    /** Called right after `turnNumber++` at the round wrap; [StateBuilder.turnNumber] is the NEW round. */
    fun roundTick(b: StateBuilder) {
        val wasNight = Rules.isNight(b.turnNumber - 1, b.rules)
        val nowNight = Rules.isNight(b.turnNumber, b.rules)
        when {
            !wasNight && nowNight -> nightfall(b)
            wasNight && nowNight -> monsterPhase(b)
            wasNight && !nowNight -> dawn(b)
        }
    }

    private fun nightfall(b: StateBuilder) {
        b.events.add(GameEvent.NightFell(b.turnNumber))
        val rules = b.rules
        if (rules.monsterSpawnPer100Hexes <= 0) return
        val capitals = b.players.filter { !it.eliminated }.mapNotNull { it.capital }
        val candidates = b.tiles.entries
            .filter { (hex, tile) ->
                tile.terrain == Terrain.LAND &&
                    tile.unit == null && tile.building == null && tile.monster == null &&
                    capitals.all { HexMath.distance(hex, it) >= rules.monsterCapitalStandoff }
            }
            .map { it.key }
            .sortedBy { it.packed }
            .toMutableList()
        if (candidates.isEmpty()) return
        val landHexes = b.tiles.values.count { it.terrain == Terrain.LAND }
        val wave = minOf(landHexes * rules.monsterSpawnPer100Hexes / 100, rules.monsterSpawnCap)
            .coerceAtLeast(1)
            .coerceAtMost(candidates.size)
        val tier = Rules.monsterTierAt(b.turnNumber, rules)
        val kinds = MonsterKind.forTier(tier)
        repeat(wave) {
            // Sample without replacement so a wave never doubles up a hex.
            val hex = candidates.removeAt(b.rollIndex(candidates.size))
            val monster = Monster(kinds[b.rollIndex(kinds.size)], tier, b.turnNumber)
            b.updateTile(hex) { it.copy(monster = monster) }
            b.events.add(GameEvent.MonsterSpawned(hex, monster))
        }
    }

    /** How far a monster looks for territory to stalk when nothing is in striking range. */
    private const val PROWL_RADIUS = 6

    /**
     * Night-interior rounds: each monster acts once, in packed order (kills by
     * an earlier monster change what a later one sees — the order makes that
     * deterministic; the phase itself consumes no RNG). A monster either
     * strikes the best unit within [com.msa.fightandconquer.core.model.RuleConstants.monsterMoveRange]
     * whose hex it can out-attack — the FULL defense model applies
     * ([Rules.defenseFrom]), so towers, garrisons and auras protect at night
     * exactly as by day — or prowls one step toward the nearest owned or
     * garrisoned tile, or holds. Monsters never capture and never raze:
     * ownership, elimination and starvation stay untouched by the whole phase.
     */
    private fun monsterPhase(b: StateBuilder) {
        for (origin in monsterHexes(b)) {
            val monster = b.tiles[origin]?.monster ?: continue
            val attack = Rules.monsterAttackOf(monster)
            val reach = passableBfs(b, origin, b.rules.monsterMoveRange)

            // Strike: the nearest beatable unit hex bordering the reachable
            // path (the capture-as-final-step rule), ties broken by packed.
            val target = reach.entries.asSequence()
                .flatMap { (hex, depth) ->
                    HexMath.neighbors(hex).mapNotNull { n ->
                        val t = b.tiles[n]
                        val defender = t?.unit?.let { b.units[it] }
                        if (t != null && defender != null && !Rules.isNaval(defender.type) &&
                            t.terrain == Terrain.LAND && t.building == null &&
                            attack > defenseAt(b, n)
                        ) {
                            Triple(depth + 1, n.packed, n)
                        } else {
                            null
                        }
                    }
                }
                .filter { it.first <= b.rules.monsterMoveRange }
                .minWithOrNull(compareBy({ it.first }, { it.second }))
                ?.third
            if (target != null) {
                b.events.add(GameEvent.MonsterAttacked(origin, target))
                b.killUnit(b.tiles.getValue(target).unit!!, DeathCause.KILLED)
                moveMonster(b, monster, origin, target)
                continue
            }

            // Prowl: one step along a shortest passable path toward the nearest
            // hex bordering someone's territory or army. No RNG — (depth, packed)
            // ordering decides every tie.
            val step = prowlStep(b, origin)
            if (step != null) moveMonster(b, monster, origin, step)
        }
    }

    private fun moveMonster(b: StateBuilder, monster: Monster, from: Hex, to: Hex) {
        b.updateTile(from) { it.copy(monster = null) }
        b.updateTile(to) { it.copy(monster = monster) }
        b.events.add(GameEvent.MonsterMoved(from, to))
    }

    /** Hexes a monster can walk: LAND, no building, no unit, no other monster. */
    private fun passable(b: StateBuilder, hex: Hex): Boolean {
        val t = b.tiles[hex] ?: return false
        return t.terrain == Terrain.LAND && t.building == null && t.unit == null && t.monster == null
    }

    /** BFS through passable hexes up to [range]: reachable hex -> depth (origin at 0). */
    private fun passableBfs(b: StateBuilder, origin: Hex, range: Int): Map<Hex, Int> {
        val depths = LinkedHashMap<Hex, Int>()
        depths[origin] = 0
        var frontier = listOf(origin)
        var depth = 0
        while (depth < range && frontier.isNotEmpty()) {
            val next = ArrayList<Hex>()
            for (hex in frontier) {
                HexMath.forEachNeighbor(hex) { n ->
                    if (n !in depths && passable(b, n)) {
                        depths[n] = depth + 1
                        next.add(n)
                    }
                }
            }
            frontier = next
            depth++
        }
        return depths
    }

    private fun defenseAt(b: StateBuilder, hex: Hex): Int =
        Rules.defenseFrom(b.tiles, b.units, hex, attackerType = null) { b.effectiveRules(it) }

    /**
     * The first step of a shortest passable path toward the nearest lure — a
     * passable hex adjacent to an owned or garrisoned tile — within
     * [PROWL_RADIUS]. Null when nothing lures (the wilds stay still).
     */
    private fun prowlStep(b: StateBuilder, origin: Hex): Hex? {
        fun lures(hex: Hex): Boolean = HexMath.neighbors(hex).any { n ->
            val t = b.tiles[n]
            t != null && (t.owner != null || t.unit != null)
        }
        if (lures(origin)) return null // already stalking the fence line
        val depths = passableBfs(b, origin, PROWL_RADIUS)
        val goal = depths.entries.asSequence()
            .filter { it.value > 0 && lures(it.key) }
            .minWithOrNull(compareBy({ it.value }, { it.key.packed }))
            ?.key ?: return null
        // Walk the BFS gradient back from the goal to the step out of the origin,
        // preferring the packed-smallest predecessor at every hop (determinism).
        var cursor = goal
        while (depths.getValue(cursor) > 1) {
            cursor = HexMath.neighbors(cursor)
                .filter { depths[it] == depths.getValue(cursor) - 1 }
                .minBy { it.packed }
        }
        return cursor
    }

    private fun dawn(b: StateBuilder) {
        // DawnBroke leads so the renderer can vanish the survivors as one beat
        // (the per-monster events remain for headless asserts and fog handling).
        b.events.add(GameEvent.DawnBroke(b.turnNumber))
        val survivors = monsterHexes(b)
        for (hex in survivors) {
            val monster = b.tiles.getValue(hex).monster ?: continue
            b.updateTile(hex) { it.copy(monster = null) }
            b.events.add(GameEvent.MonsterDespawned(hex))
            // The original flavor: a vanishing survivor sometimes abandons a
            // half-value hoard where it stood.
            if (b.rollPercent() < b.rules.monsterDawnCachePercent) {
                val gold = (monster.tier * b.rules.monsterCachePerTier / 2).coerceAtLeast(1)
                b.updateTile(hex) { it.copy(cache = (it.cache ?: 0) + gold) }
                b.events.add(GameEvent.CacheDropped(hex, gold))
            }
        }
    }

    private fun monsterHexes(b: StateBuilder): List<Hex> =
        b.tiles.entries.filter { it.value.monster != null }.map { it.key }.sortedBy { it.packed }
}
