package com.msa.fightandconquer.core.map

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tile
import kotlinx.serialization.Serializable

@Serializable
enum class MapSize(val targetHexes: Int) { SMALL(120), MEDIUM(250), LARGE(450) }

@Serializable
enum class MapShape { CONTINENT, ISLANDS, ARCHIPELAGO }

@Serializable
data class MapParams(
    val seed: Long,
    val size: MapSize = MapSize.MEDIUM,
    val playerCount: Int = 2,
    val shape: MapShape = MapShape.CONTINENT,
) {
    init {
        require(playerCount in 2..6) { "playerCount must be 2..6" }
    }
}

@Serializable
data class TileDef(
    val hex: Hex,
    /** Player slot index, null = neutral. */
    val owner: Int? = null,
    val building: Building? = null,
    val flora: Flora? = null,
)

/**
 * The shared map format: procedural skirmish maps carry their [generatorParams];
 * authored campaign maps set it to null. Player count = [capitals].size.
 */
@Serializable
data class MapDefinition(
    val version: Int = 1,
    val name: String,
    val generatorParams: MapParams? = null,
    val tiles: List<TileDef>,
    val capitals: List<Hex>,
) {
    /** Instantiate a fresh game on this map. [kinds].size must match [capitals].size. */
    fun newGame(
        gameSeed: Long,
        kinds: List<PlayerKind>,
        rules: RuleConstants = RuleConstants(),
    ): GameState {
        require(kinds.size == capitals.size) { "need ${capitals.size} player kinds" }
        val tileMap = tiles.associate { def ->
            def.hex to Tile(
                owner = def.owner?.let(::PlayerId),
                building = def.building,
                flora = def.flora,
            )
        }
        capitals.forEach { capital ->
            require(tileMap.getValue(capital).building == Building.CAPITAL) {
                "capital $capital missing its building"
            }
        }
        val players = kinds.mapIndexed { index, kind ->
            val player = PlayerState(PlayerId(index), kind, rules.startingTreasury, capitals[index])
            if (!rules.fogOfWar) player
            else player.copy(
                // Fog of war: seed explored memory with the starting vision.
                discovered = Rules.sortedDiscovered(
                    Rules.visibleHexesFrom(tileMap, emptyList(), rules, player.id),
                ),
            )
        }
        return GameState(
            config = GameConfig(seed = gameSeed, rules = rules),
            tiles = tileMap,
            units = emptyMap(),
            players = players,
            currentPlayer = PlayerId(0),
            rngState = gameSeed,
        )
    }
}
