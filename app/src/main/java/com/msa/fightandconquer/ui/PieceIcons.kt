package com.msa.fightandconquer.ui

import androidx.annotation.DrawableRes
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.UnitType

/**
 * Pre-rendered thumbnails of the 3D piece models (baked by
 * tools/render_piece_icons.py into res/drawable-nodpi). FACTION parts are a
 * neutral warm gray — ownership is conveyed by the faction dot, not the icon.
 */
object PieceIcons {
    @DrawableRes
    fun unit(type: UnitType, tier: Int): Int = when (type) {
        UnitType.ARCHER -> R.drawable.piece_archer
        UnitType.CATAPULT -> R.drawable.piece_catapult
        UnitType.SOLDIER -> when (tier) {
            1 -> R.drawable.piece_unit_t1
            2 -> R.drawable.piece_unit_t2
            3 -> R.drawable.piece_unit_t3
            else -> R.drawable.piece_unit_t4
        }
    }

    @DrawableRes
    fun building(building: Building): Int = when (building) {
        Building.CAPITAL -> R.drawable.piece_capital
        Building.FARM -> R.drawable.piece_farm
        Building.TOWER -> R.drawable.piece_tower
        Building.STRONG_TOWER -> R.drawable.piece_strong_tower
        Building.MINE -> R.drawable.piece_mine
        Building.MARKET -> R.drawable.piece_market
        Building.LUMBER_CAMP -> R.drawable.piece_lumber_camp
        Building.WATCHTOWER -> R.drawable.piece_watchtower
    }

    val tree = R.drawable.piece_tree
    val gravestone = R.drawable.piece_gravestone
    val goldVein = R.drawable.piece_gold_vein
    val fertile = R.drawable.piece_fertile
}
