package com.msa.fightandconquer.ui.menu

import com.msa.fightandconquer.core.engine.Rng
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants

/**
 * The decorative world behind the main menu: a freshly generated skirmish map nobody
 * ever plays. It exists purely as scenery — the camera orbits it, no action is ever
 * submitted to the state it returns.
 *
 * Every choice (seat count, civilizations) is derived from the one [Long] seed through
 * the engine's SplitMix64 [Rng] — never the Kotlin stdlib random, never a clock — so a
 * seed always yields the very same world (the rule that keeps saves replayable). The
 * seed itself is picked by the caller.
 *
 * The shape is *not* rolled: menu worlds are always [MapShape.CONTINENT]. The generator
 * tiles no open ocean (only land plus a thin coastal sea fringe), so under the menu's
 * close framing the island shapes read as a near-empty backdrop — a thin chain of tiles
 * on void — which defeats the point of having scenery at all.
 */
object MenuWorld {

    /** Seats on a menu world: enough banners to look busy, few enough to stay readable. */
    private val PLAYER_RANGE = 2..4

    /** Keeps the civ shuffle from correlating with the seat-count roll. */
    private const val CIV_SALT = 0x5EEDC1F5L

    /** Advancing SplitMix64 chain, same idiom as `MapGenerator`. */
    private class Chain(var state: Long) {
        fun roll(bound: Int): Int {
            state = Rng.advance(state)
            return Rng.nextInt(state, bound)
        }
    }

    /**
     * Seed-derived generation parameters: MEDIUM size, 2..4 seats from the seed, and
     * always [MapShape.CONTINENT] — the island shapes leave the backdrop nearly empty
     * (see the class KDoc), so the shape is fixed rather than rolled.
     */
    fun params(seed: Long): MapParams {
        val rng = Chain(seed)
        val playerCount = PLAYER_RANGE.first + rng.roll(PLAYER_RANGE.last - PLAYER_RANGE.first + 1)
        return MapParams(
            seed = seed,
            size = MapSize.MEDIUM,
            playerCount = playerCount,
            shape = MapShape.CONTINENT,
        )
    }

    /**
     * Seed-derived seats: [playerCount] distinct civilizations in seed order, so the
     * capitals on screen never repeat their art. Requires [playerCount] to fit the
     * roster (four civilizations, four seats at most).
     */
    fun civs(seed: Long, playerCount: Int): List<Civilization> {
        require(playerCount in 1..Civilization.entries.size) {
            "playerCount must be 1..${Civilization.entries.size}"
        }
        val rng = Chain(Rng.output(seed xor CIV_SALT))
        val pool = Civilization.entries.toMutableList()
        // Fisher-Yates, seeded: a shuffle keeps every civ equally likely per seat.
        for (i in pool.indices.reversed()) {
            val j = rng.roll(i + 1)
            val swap = pool[i]
            pool[i] = pool[j]
            pool[j] = swap
        }
        return pool.take(playerCount)
    }

    /**
     * The full showcase state. Blocking (map generation retries until the validator is
     * happy) — call it off the main thread, as `GameViewModel.newGame` does.
     */
    fun generate(seed: Long): GameState {
        val params = params(seed)
        return MapGenerator.generate(params).newGame(
            gameSeed = seed * 31 + 17,
            kinds = List(params.playerCount) { PlayerKind.Ai(Difficulty.NORMAL) },
            rules = RuleConstants(),
            civs = civs(seed, params.playerCount),
        )
    }
}
