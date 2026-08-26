package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.Tile

/**
 * Shared spot chooser for threshold-policy structures (the University, the
 * muster halls): own, non-starving, clear land, deposits kept free for their
 * buildings; interior preferred (protect the engine the plan hangs on), lowest
 * packed tie-break. Never the last empty own land hex in a naval game — the
 * MoveGenerator muster-yard valve, replicated because policies do not go
 * through the candidate list. Extracted verbatim from ResearchPolicy's
 * universitySpot (behavior-identical, snapshot-safe).
 */
internal fun interiorBuildSpot(state: GameState, me: PlayerId): Hex? {
    val empty = state.tiles.entries.filter { (_, t) ->
        t.owner == me && !t.starving && t.terrain == Terrain.LAND &&
            t.building == null && t.unit == null && t.flora == null && t.deposit == null
    }
    if (state.config.rules.navalEnabled && empty.size <= 1) return null
    return empty
        .minWithOrNull(
            compareBy<Map.Entry<Hex, Tile>>(
                { (hex, _) -> if (HexMath.neighbors(hex).all { state.tiles[it]?.owner == me }) 0 else 1 },
                { (hex, _) -> hex.packed },
            ),
        )
        ?.key
}
