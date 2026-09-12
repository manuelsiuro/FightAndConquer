package com.msa.fightandconquer.ui.game

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId

/** The glanceable sheet an own building opens when tapped instead of an info card. */
enum class BuildingTapTarget { ECONOMY, RESEARCH }

/** Pure tap-routing rule for building hexes (docs/ui-hud.md "Interaction model"). */
object BuildingTap {
    /**
     * [BuildingTapTarget.ECONOMY] for [seat]'s own Capital, [BuildingTapTarget.RESEARCH] for
     * its own University while research is enabled; null otherwise — including whenever a
     * unit stands on the hex (units route first).
     */
    fun targetOf(state: GameState, hex: Hex, seat: PlayerId): BuildingTapTarget? {
        val tile = state.tiles[hex] ?: return null
        if (tile.owner != seat || tile.unit != null) return null
        return when (tile.building) {
            Building.CAPITAL -> BuildingTapTarget.ECONOMY
            Building.UNIVERSITY ->
                BuildingTapTarget.RESEARCH.takeIf { state.config.rules.researchEnabled }
            else -> null
        }
    }
}
