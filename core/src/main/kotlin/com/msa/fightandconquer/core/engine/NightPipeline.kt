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

    /** Night-interior rounds: each monster takes its action. Lands with milestone M2. */
    private fun monsterPhase(b: StateBuilder) {
        // M2: movement BFS + attacks against Rules.defenseOf.
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
