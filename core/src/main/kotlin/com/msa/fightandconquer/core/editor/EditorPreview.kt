package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.campaign.UnitPlacement
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.Tile
import com.msa.fightandconquer.core.model.UnitId

/**
 * Builds a throwaway [GameState] from a half-built map so the 3D board can render it.
 *
 * `MapDefinition.newGame` refuses anything without well-formed capitals — correct for
 * play, useless for previewing a draft. This path has no opinions: tiles are taken
 * verbatim (duplicates last-wins), any seat count ≥ 1 works, units that would be
 * illegal in play are simply skipped rather than thrown on. Fog stays off and the
 * state is never handed to the reducer — it exists purely for `BoardScene`.
 */
object EditorPreview {

    fun state(
        tiles: List<TileDef>,
        seatCount: Int,
        units: List<UnitPlacement> = emptyList(),
    ): GameState {
        val tileMap = HashMap<com.msa.fightandconquer.core.hex.Hex, Tile>(tiles.size)
        tiles.forEach { def ->
            tileMap[def.hex] = Tile(
                owner = def.owner?.let(::PlayerId),
                building = def.building,
                flora = def.flora,
                deposit = def.deposit,
                terrain = def.terrain,
            )
        }

        val seats = maxOf(1, seatCount)
        val players = (0 until seats).map { index ->
            PlayerState(PlayerId(index), PlayerKind.Human, 0, null)
        }

        val placed = HashMap<UnitId, GameUnit>()
        var nextUnitId = 1
        units.forEach { placement ->
            val tile = tileMap[placement.hex] ?: return@forEach
            if (tile.unit != null) return@forEach
            if (placement.seat !in 0 until seats) return@forEach
            val naval = Rules.isNaval(placement.type)
            if (naval != (tile.terrain == Terrain.SEA)) return@forEach
            val unit = GameUnit(
                id = UnitId(nextUnitId++),
                owner = PlayerId(placement.seat),
                tier = placement.tier,
                hex = placement.hex,
                spent = false,
                type = placement.type,
            )
            placed[unit.id] = unit
            tileMap[placement.hex] = tile.copy(unit = unit.id)
        }

        return GameState(
            config = GameConfig(seed = 0L, rules = RuleConstants()),
            tiles = tileMap,
            units = placed,
            players = players,
            currentPlayer = PlayerId(0),
            rngState = 0L,
            nextUnitId = nextUnitId,
        )
    }
}
