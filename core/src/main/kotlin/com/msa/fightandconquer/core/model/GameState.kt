package com.msa.fightandconquer.core.model

import com.msa.fightandconquer.core.hex.Hex
import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val config: GameConfig,
    val tiles: Map<Hex, Tile>,
    val units: Map<UnitId, GameUnit>,
    /** Seat order = turn order; [PlayerId.value] indexes this list. */
    val players: List<PlayerState>,
    val currentPlayer: PlayerId,
    /** Completed full rounds (increments when the turn wraps back to the first living seat). */
    val turnNumber: Int = 0,
    /** SplitMix64 state — the RNG lives in the state so reduce() stays pure and replayable. */
    val rngState: Long,
    val phase: GamePhase = GamePhase.Playing,
    val nextUnitId: Int = 1,
    /** Pacts, proposals and diplomacy bookkeeping (defaulted — save-compatible). */
    val diplomacy: DiplomacyState = DiplomacyState(),
) {
    init {
        players.forEachIndexed { index, player ->
            require(player.id.value == index) { "players must be indexed by PlayerId" }
        }
    }

    fun tile(hex: Hex): Tile? = tiles[hex]

    fun unitAt(hex: Hex): GameUnit? = tiles[hex]?.unit?.let { units[it] }

    fun player(id: PlayerId): PlayerState = players[id.value]

    fun ownedHexes(id: PlayerId): Set<Hex> =
        tiles.filterValues { it.owner == id }.keys

    fun ownedHexCount(id: PlayerId): Int =
        tiles.values.count { it.owner == id }

    fun unitsOf(id: PlayerId): List<GameUnit> =
        units.values.filter { it.owner == id }

    fun farmCount(id: PlayerId): Int =
        tiles.values.count { it.owner == id && it.building == Building.FARM }
}
