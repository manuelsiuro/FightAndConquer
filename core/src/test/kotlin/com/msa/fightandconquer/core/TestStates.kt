package com.msa.fightandconquer.core

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.Tile
import com.msa.fightandconquer.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Hand-built micro-maps for rule tests. */
object TestStates {

    fun hex(q: Int, r: Int = 0): Hex = Hex.of(q, r)

    /**
     * A 1-row strip of [length] hexes at r=0. P0 owns [p0] (capital at its first hex),
     * P1 owns [p1] (capital at its last hex), everything else neutral.
     */
    /** Fully custom micro-map: hex -> owner index (null = neutral). */
    fun custom(
        owners: Map<Hex, Int?>,
        capital0: Hex,
        capital1: Hex,
        treasury: Int = 100,
        seed: Long = 42L,
        rules: com.msa.fightandconquer.core.model.RuleConstants = com.msa.fightandconquer.core.model.RuleConstants(),
    ): GameState {
        val tiles = owners.mapValues { (_, owner) -> Tile(owner = owner?.let(::PlayerId)) }.toMutableMap()
        tiles[capital0] = tiles.getValue(capital0).copy(building = Building.CAPITAL)
        tiles[capital1] = tiles.getValue(capital1).copy(building = Building.CAPITAL)
        return GameState(
            config = GameConfig(seed = seed, rules = rules),
            tiles = tiles,
            units = emptyMap(),
            players = listOf(
                PlayerState(PlayerId(0), PlayerKind.Human, treasury, capital0),
                PlayerState(PlayerId(1), PlayerKind.Human, treasury, capital1),
            ),
            currentPlayer = PlayerId(0),
            rngState = seed,
        )
    }

    fun strip(
        length: Int,
        p0: IntRange,
        p1: IntRange,
        treasury: Int = 100,
        seed: Long = 42L,
        rules: com.msa.fightandconquer.core.model.RuleConstants = com.msa.fightandconquer.core.model.RuleConstants(),
    ): GameState {
        val tiles = HashMap<Hex, Tile>()
        for (q in 0 until length) {
            val owner = when (q) {
                in p0 -> PlayerId(0)
                in p1 -> PlayerId(1)
                else -> null
            }
            tiles[hex(q)] = Tile(owner = owner)
        }
        val cap0 = hex(p0.first)
        val cap1 = hex(p1.last)
        tiles[cap0] = tiles.getValue(cap0).copy(building = Building.CAPITAL)
        tiles[cap1] = tiles.getValue(cap1).copy(building = Building.CAPITAL)
        return GameState(
            config = GameConfig(seed = seed, rules = rules),
            tiles = tiles,
            units = emptyMap(),
            players = listOf(
                PlayerState(PlayerId(0), PlayerKind.Human, treasury, cap0),
                PlayerState(PlayerId(1), PlayerKind.Human, treasury, cap1),
            ),
            currentPlayer = PlayerId(0),
            rngState = seed,
        )
    }

    fun GameState.withUnit(owner: Int, tier: Int, at: Hex, spent: Boolean = false): GameState {
        val unit = GameUnit(UnitId(nextUnitId), PlayerId(owner), tier, at, spent)
        return copy(
            units = units + (unit.id to unit),
            tiles = tiles + (at to tiles.getValue(at).copy(unit = unit.id)),
            nextUnitId = nextUnitId + 1,
        )
    }

    fun GameState.withBuilding(building: Building, at: Hex): GameState =
        copy(tiles = tiles + (at to tiles.getValue(at).copy(building = building)))

    fun GameState.withFlora(flora: Flora, at: Hex): GameState =
        copy(tiles = tiles + (at to tiles.getValue(at).copy(flora = flora)))

    fun GameState.withDeposit(deposit: com.msa.fightandconquer.core.model.Deposit, at: Hex): GameState =
        copy(tiles = tiles + (at to tiles.getValue(at).copy(deposit = deposit)))

    fun GameState.withTreasury(player: Int, amount: Int): GameState =
        copy(players = players.map { if (it.id.value == player) it.copy(treasury = amount) else it })

    fun GameState.unitIdAt(at: Hex): UnitId = tiles.getValue(at).unit!!

    /** Cross-index consistency: catches every dual-bookkeeping bug in the reducer. */
    fun assertInvariants(state: GameState) {
        for (unit in state.units.values) {
            assertEquals("tile back-pointer for $unit", unit.id, state.tiles[unit.hex]?.unit)
            assertEquals("unit stands on own tile: $unit", unit.owner, state.tiles[unit.hex]?.owner)
            assertTrue("tier in range: $unit", unit.tier in 1..state.config.rules.maxTier)
        }
        for ((hex, tile) in state.tiles) {
            tile.unit?.let { id ->
                assertEquals("units map entry for tile $hex", hex, state.units[id]?.hex)
            }
        }
        for (player in state.players) {
            player.capital?.let { cap ->
                assertEquals("capital building present", Building.CAPITAL, state.tiles[cap]?.building)
                assertEquals("capital on own tile", player.id, state.tiles[cap]?.owner)
            }
            assertTrue("treasury never negative after enforcement", player.treasury >= 0 || state.phase != com.msa.fightandconquer.core.model.GamePhase.Playing)
        }
    }
}
