package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.map.MapDefinition
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty

/** Starting points for the editor — small, valid, immediately playable. */
object MapTemplates {

    /**
     * A new map's opening state: a land disc with two opposed starts, so the canvas is
     * never a bewildering void and "New map" already passes the validator.
     */
    fun starter(id: String, name: String, createdAt: Long): CustomMapDef {
        val radius = 3
        val capitals = listOf(Hex.of(-radius + 1, 0), Hex.of(radius - 1, 0))
        val starts = capitals.mapIndexed { seat, capital ->
            HexMath.range(capital, 1).associateWith { seat }
        }
        val owners = HashMap<Hex, Int>().apply { starts.forEach { putAll(it) } }
        val tiles = HexMath.range(Hex.of(0, 0), radius).sortedBy { it.packed }.map { hex ->
            TileDef(
                hex = hex,
                owner = owners[hex],
                building = if (hex in capitals) Building.CAPITAL else null,
            )
        }
        return CustomMapDef(
            id = id,
            name = name,
            createdAt = createdAt,
            modifiedAt = createdAt,
            level = LevelDef(
                id = id,
                seed = createdAt,
                map = MapDefinition(name = name, tiles = tiles, capitals = capitals),
                seats = listOf(SeatDef.Player, SeatDef.Ai(Difficulty.NORMAL)),
            ),
        )
    }
}
