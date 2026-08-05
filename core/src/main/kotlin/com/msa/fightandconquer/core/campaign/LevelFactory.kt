package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.UnitId

/** Turns a [LevelDef] into the opening [GameState] of a mission. */
object LevelFactory {

    /**
     * Builds the level's opening position: the authored map instantiated through the
     * ordinary [com.msa.fightandconquer.core.map.MapDefinition.newGame], then the
     * per-seat treasury and the starting garrison laid on top.
     *
     * [LevelDef.rules] is taken as written except for
     * [com.msa.fightandconquer.core.model.RuleConstants.scriptedEventsEnabled], which is
     * derived from whether the level actually has triggers — an author cannot leave a
     * level's story beats switched off, and a level without beats cannot accept one.
     */
    fun instantiate(level: LevelDef): GameState {
        require(level.seats.size == level.map.capitals.size) {
            "level ${level.id}: ${level.seats.size} seats for ${level.map.capitals.size} capitals"
        }
        val rules = level.rules.copy(scriptedEventsEnabled = level.scripts.isNotEmpty())
        var state = level.map.newGame(
            gameSeed = level.seed,
            kinds = level.seats.map { it.toKind() },
            rules = rules,
        )

        level.startingTreasury?.let { purses ->
            require(purses.size == level.seats.size) {
                "level ${level.id}: ${purses.size} purses for ${level.seats.size} seats"
            }
            state = state.copy(
                players = state.players.mapIndexed { index, p -> p.copy(treasury = purses[index]) },
            )
        }

        if (level.startingUnits.isNotEmpty()) state = placeGarrison(level, state)

        // Fog: the garrison sees further than the bare capitals did, so re-seed explored
        // memory after placing it (newGame ran before the units existed).
        if (rules.fogOfWar) {
            state = state.copy(
                players = state.players.map { player ->
                    val sea = state.tiles.filterValues { it.terrain == Terrain.SEA }.keys
                    player.copy(
                        discovered = Rules.sortedDiscovered(
                            player.discovered + Rules.visibleHexes(state, player.id) + sea,
                        ),
                    )
                },
            )
        }
        return state
    }

    private fun placeGarrison(level: LevelDef, base: GameState): GameState {
        val tiles = HashMap(base.tiles)
        val units = HashMap(base.units)
        var nextUnitId = base.nextUnitId
        for (placement in level.startingUnits) {
            val tile = requireNotNull(tiles[placement.hex]) {
                "level ${level.id}: starting unit on off-map hex ${placement.hex}"
            }
            require(tile.unit == null) {
                "level ${level.id}: two starting units on ${placement.hex}"
            }
            val owner = PlayerId(placement.seat)
            val naval = Rules.isNaval(placement.type)
            require(if (naval) tile.terrain == Terrain.SEA else tile.owner == owner) {
                "level ${level.id}: ${placement.type} at ${placement.hex} is not on its owner's ground"
            }
            val unit = GameUnit(
                id = UnitId(nextUnitId++),
                owner = owner,
                tier = placement.tier,
                hex = placement.hex,
                spent = false,
                type = placement.type,
            )
            units[unit.id] = unit
            tiles[placement.hex] = tile.copy(unit = unit.id)
        }
        return base.copy(tiles = tiles, units = units, nextUnitId = nextUnitId)
    }
}
