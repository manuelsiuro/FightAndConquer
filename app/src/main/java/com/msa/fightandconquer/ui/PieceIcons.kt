package com.msa.fightandconquer.ui

import androidx.annotation.DrawableRes
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.UnitType

/**
 * Pre-rendered thumbnails of the 3D piece models (baked by
 * tools/render_piece_icons.py into res/drawable-nodpi). FACTION parts are a
 * neutral warm gray — ownership is conveyed by the faction dot, not the icon.
 *
 * Keyed by (civilization, kind) mirroring the 3D asset contract: KINGDOM keeps the
 * flat `piece_<kind>` drawables; other civs get `piece_<civ>_<kind>` drawables that
 * land one entry at a time — until an icon ships, its table entry points at the
 * Kingdom drawable (the 2D twin of the .pmesh fallback). Neutral markers (tree,
 * gravestone, deposits) never fork.
 */
object PieceIcons {

    @DrawableRes
    fun unit(civ: Civilization, type: UnitType, tier: Int): Int = when (civ) {
        Civilization.KINGDOM -> kingdomUnit(type, tier)
        Civilization.VIKINGS -> when (type) {
            UnitType.ARCHER -> R.drawable.piece_vikings_archer
            UnitType.CATAPULT -> R.drawable.piece_vikings_catapult
            UnitType.TRANSPORT -> R.drawable.piece_vikings_boat
            UnitType.WARSHIP -> R.drawable.piece_vikings_warship
            UnitType.SOLDIER -> when (tier) {
                1 -> R.drawable.piece_vikings_unit_t1
                2 -> R.drawable.piece_vikings_unit_t2
                3 -> R.drawable.piece_vikings_unit_t3
                else -> R.drawable.piece_vikings_unit_t4
            }
        }
        // Replace an entry with its piece_sultanate_* drawable as each icon is delivered.
        Civilization.SULTANATE -> when (type) {
            UnitType.ARCHER -> R.drawable.piece_archer
            UnitType.CATAPULT -> R.drawable.piece_catapult
            UnitType.TRANSPORT -> R.drawable.piece_boat
            UnitType.WARSHIP -> R.drawable.piece_warship
            UnitType.SOLDIER -> kingdomSoldier(tier)
        }
        // Replace an entry with its piece_shogunate_* drawable as each icon is delivered.
        Civilization.SHOGUNATE -> when (type) {
            UnitType.ARCHER -> R.drawable.piece_archer
            UnitType.CATAPULT -> R.drawable.piece_catapult
            UnitType.TRANSPORT -> R.drawable.piece_boat
            UnitType.WARSHIP -> R.drawable.piece_warship
            UnitType.SOLDIER -> kingdomSoldier(tier)
        }
    }

    @DrawableRes
    fun building(civ: Civilization, building: Building): Int = when (civ) {
        Civilization.KINGDOM -> kingdomBuilding(building)
        Civilization.VIKINGS -> when (building) {
            Building.CAPITAL -> R.drawable.piece_vikings_capital
            Building.FARM -> R.drawable.piece_vikings_farm
            Building.TOWER -> R.drawable.piece_vikings_tower
            Building.STRONG_TOWER -> R.drawable.piece_vikings_strong_tower
            Building.MINE -> R.drawable.piece_vikings_mine
            Building.MARKET -> R.drawable.piece_vikings_market
            Building.LUMBER_CAMP -> R.drawable.piece_vikings_lumber_camp
            Building.WATCHTOWER -> R.drawable.piece_vikings_watchtower
            Building.PORT -> R.drawable.piece_vikings_port
            Building.FISHERY -> R.drawable.piece_vikings_fishery
            Building.BRIDGE -> R.drawable.piece_vikings_bridge
        }
        // Replace an entry with its piece_sultanate_* drawable as each icon is delivered.
        Civilization.SULTANATE -> when (building) {
            Building.CAPITAL -> R.drawable.piece_capital
            Building.FARM -> R.drawable.piece_farm
            Building.TOWER -> R.drawable.piece_tower
            Building.STRONG_TOWER -> R.drawable.piece_strong_tower
            Building.MINE -> R.drawable.piece_mine
            Building.MARKET -> R.drawable.piece_market
            Building.LUMBER_CAMP -> R.drawable.piece_lumber_camp
            Building.WATCHTOWER -> R.drawable.piece_watchtower
            Building.PORT -> R.drawable.piece_port
            Building.FISHERY -> R.drawable.piece_fishery
            Building.BRIDGE -> R.drawable.piece_bridge
        }
        // Replace an entry with its piece_shogunate_* drawable as each icon is delivered.
        Civilization.SHOGUNATE -> when (building) {
            Building.CAPITAL -> R.drawable.piece_capital
            Building.FARM -> R.drawable.piece_farm
            Building.TOWER -> R.drawable.piece_tower
            Building.STRONG_TOWER -> R.drawable.piece_strong_tower
            Building.MINE -> R.drawable.piece_mine
            Building.MARKET -> R.drawable.piece_market
            Building.LUMBER_CAMP -> R.drawable.piece_lumber_camp
            Building.WATCHTOWER -> R.drawable.piece_watchtower
            Building.PORT -> R.drawable.piece_port
            Building.FISHERY -> R.drawable.piece_fishery
            Building.BRIDGE -> R.drawable.piece_bridge
        }
    }

    @Deprecated(
        "Kingdom-only; pass the owner's civilization",
        ReplaceWith("unit(Civilization.KINGDOM, type, tier)"),
    )
    @DrawableRes
    fun unit(type: UnitType, tier: Int): Int = unit(Civilization.KINGDOM, type, tier)

    @Deprecated(
        "Kingdom-only; pass the owner's civilization",
        ReplaceWith("building(Civilization.KINGDOM, building)"),
    )
    @DrawableRes
    fun building(building: Building): Int = building(Civilization.KINGDOM, building)

    @DrawableRes
    private fun kingdomUnit(type: UnitType, tier: Int): Int = when (type) {
        UnitType.ARCHER -> R.drawable.piece_archer
        UnitType.CATAPULT -> R.drawable.piece_catapult
        UnitType.TRANSPORT -> R.drawable.piece_boat
        UnitType.WARSHIP -> R.drawable.piece_warship
        UnitType.SOLDIER -> kingdomSoldier(tier)
    }

    @DrawableRes
    private fun kingdomSoldier(tier: Int): Int = when (tier) {
        1 -> R.drawable.piece_unit_t1
        2 -> R.drawable.piece_unit_t2
        3 -> R.drawable.piece_unit_t3
        else -> R.drawable.piece_unit_t4
    }

    @DrawableRes
    private fun kingdomBuilding(building: Building): Int = when (building) {
        Building.CAPITAL -> R.drawable.piece_capital
        Building.FARM -> R.drawable.piece_farm
        Building.TOWER -> R.drawable.piece_tower
        Building.STRONG_TOWER -> R.drawable.piece_strong_tower
        Building.MINE -> R.drawable.piece_mine
        Building.MARKET -> R.drawable.piece_market
        Building.LUMBER_CAMP -> R.drawable.piece_lumber_camp
        Building.WATCHTOWER -> R.drawable.piece_watchtower
        Building.PORT -> R.drawable.piece_port
        Building.FISHERY -> R.drawable.piece_fishery
        Building.BRIDGE -> R.drawable.piece_bridge
    }

    // Neutral markers: never fork per civ.
    val tree = R.drawable.piece_tree
    val gravestone = R.drawable.piece_gravestone
    val goldVein = R.drawable.piece_gold_vein
    val fertile = R.drawable.piece_fertile
    val fishShoal = R.drawable.piece_fish_shoal
}
