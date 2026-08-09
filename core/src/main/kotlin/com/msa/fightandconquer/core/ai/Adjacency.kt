package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId

/** Neighbor queries shared by [Evaluator] and [MoveGenerator]. */
internal object Adjacency {

    /** Own tree hexes next to [hex] — lumber-camp feedstock. */
    fun adjacentOwnTrees(state: GameState, hex: Hex, me: PlayerId): Int {
        var count = 0
        HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && t.flora is Flora.Tree) count++
        }
        return count
    }

    /** Trees next to an own lumber camp are managed income, not rot to clear. */
    fun nextToOwnCamp(state: GameState, hex: Hex, me: PlayerId): Boolean {
        var found = false
        HexMath.forEachNeighbor(hex) { n ->
            val t = state.tiles[n]
            if (t != null && t.owner == me && t.building == Building.LUMBER_CAMP) found = true
        }
        return found
    }
}
