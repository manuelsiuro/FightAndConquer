package com.msa.fightandconquer.ui.guide

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.ui.PieceIcons

/**
 * One stat chip in a guide entry. [valueRes] is a format string ([arg] is the single
 * `%d` when present, otherwise the string is used verbatim). Labels/values reuse the
 * existing `info_stat_*` / `info_value_*` resources so the guide reads like the
 * tap-to-inspect InfoCard.
 */
data class GuideStat(
    @StringRes val labelRes: Int,
    @StringRes val valueRes: Int,
    val arg: Int? = null,
)

/**
 * A single browsable reference entry — one unit, building, resource, or basic concept.
 * Everything is a resource id so the whole catalog is static, context-free data that the
 * [FieldGuide] and the purchase tray both render from (no duplicated copy).
 *
 * Numeric [stats] use the shipped [com.msa.fightandconquer.core.model.RuleConstants]
 * defaults — the guide is educational, not a live readout of a modified config.
 */
data class GuideEntry(
    val id: String,
    @DrawableRes val iconRes: Int?,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    @StringRes val howToRes: Int?,
    @StringRes val requirementRes: Int? = null,
    val stats: List<GuideStat> = emptyList(),
)

data class GuideSection(@StringRes val titleRes: Int, val entries: List<GuideEntry>)

/**
 * The single source of truth for player-facing explanations of every unit, building and
 * resource. Grouped into [sections] for the [FieldGuide]; [forStructure] / [forUnit] let
 * the purchase cards resolve the same entry they render in the guide.
 */
object GuideCatalog {

    private val basics = GuideSection(
        R.string.guide_section_basics,
        listOf(
            GuideEntry(
                id = "basic_economy",
                iconRes = null,
                nameRes = R.string.guide_basic_economy_title,
                descRes = R.string.guide_basic_economy_desc,
                howToRes = R.string.guide_basic_economy_how,
            ),
            GuideEntry(
                id = "basic_capture",
                iconRes = null,
                nameRes = R.string.guide_basic_capture_title,
                descRes = R.string.guide_basic_capture_desc,
                howToRes = R.string.guide_basic_capture_how,
            ),
            GuideEntry(
                id = "basic_merge",
                iconRes = null,
                nameRes = R.string.guide_basic_merge_title,
                descRes = R.string.guide_basic_merge_desc,
                howToRes = R.string.guide_basic_merge_how,
            ),
            GuideEntry(
                id = "basic_territory",
                iconRes = null,
                nameRes = R.string.guide_basic_territory_title,
                descRes = R.string.guide_basic_territory_desc,
                howToRes = R.string.guide_basic_territory_how,
            ),
            GuideEntry(
                id = "basic_naval",
                iconRes = null,
                nameRes = R.string.guide_basic_naval_title,
                descRes = R.string.guide_basic_naval_desc,
                howToRes = R.string.guide_basic_naval_how,
            ),
        ),
    )

    /** Shared by every soldier tier — the ladder is one concept, not four cards. */
    private val soldiers = GuideEntry(
        id = "unit_soldiers",
        iconRes = PieceIcons.unit(UnitType.SOLDIER, tier = 2),
        nameRes = R.string.guide_unit_soldiers,
        descRes = R.string.guide_desc_soldiers,
        howToRes = R.string.guide_how_soldiers,
        stats = listOf(
            GuideStat(R.string.info_stat_attack, R.string.guide_value_soldier_tier),
            GuideStat(R.string.info_stat_defense, R.string.guide_value_soldier_tier),
            GuideStat(R.string.info_stat_upkeep, R.string.guide_value_soldier_upkeep),
        ),
    )

    private val archer = GuideEntry(
        id = "unit_archer",
        iconRes = PieceIcons.unit(UnitType.ARCHER, tier = 1),
        nameRes = R.string.unit_archer,
        descRes = R.string.info_archer,
        howToRes = R.string.guide_how_archer,
        requirementRes = R.string.guide_req_special,
        stats = listOf(
            GuideStat(R.string.info_stat_attack, R.string.info_value_plain, 1),
            GuideStat(R.string.info_stat_defense, R.string.info_value_defense_area, 2),
            GuideStat(R.string.info_stat_upkeep, R.string.info_value_per_turn, 4),
        ),
    )

    private val catapult = GuideEntry(
        id = "unit_catapult",
        iconRes = PieceIcons.unit(UnitType.CATAPULT, tier = 1),
        nameRes = R.string.unit_catapult,
        descRes = R.string.info_catapult,
        howToRes = R.string.guide_how_catapult,
        requirementRes = R.string.guide_req_special,
        stats = listOf(
            GuideStat(R.string.info_stat_attack, R.string.info_value_plain, 2),
            GuideStat(R.string.info_stat_defense, R.string.info_value_plain, 2),
            GuideStat(R.string.info_stat_range, R.string.info_value_plain, 2),
            GuideStat(R.string.info_stat_upkeep, R.string.info_value_per_turn, 10),
        ),
    )

    private val transport = GuideEntry(
        id = "unit_transport",
        iconRes = PieceIcons.unit(UnitType.TRANSPORT, tier = 1),
        nameRes = R.string.unit_transport,
        descRes = R.string.info_transport,
        howToRes = R.string.guide_how_transport,
        requirementRes = R.string.guide_req_port,
        stats = listOf(
            GuideStat(R.string.info_stat_attack, R.string.info_value_none),
            GuideStat(R.string.info_stat_defense, R.string.info_value_plain, 0),
            GuideStat(R.string.info_stat_range, R.string.info_value_plain, 3),
            GuideStat(R.string.info_stat_cargo, R.string.info_value_plain, 1),
            GuideStat(R.string.info_stat_upkeep, R.string.info_value_per_turn, 4),
        ),
    )

    private val warship = GuideEntry(
        id = "unit_warship",
        iconRes = PieceIcons.unit(UnitType.WARSHIP, tier = 1),
        nameRes = R.string.unit_warship,
        descRes = R.string.info_warship,
        howToRes = R.string.guide_how_warship,
        requirementRes = R.string.guide_req_port,
        stats = listOf(
            GuideStat(R.string.info_stat_attack, R.string.info_value_plain, 2),
            GuideStat(R.string.info_stat_defense, R.string.info_value_plain, 2),
            GuideStat(R.string.info_stat_range, R.string.info_value_plain, 3),
            GuideStat(R.string.info_stat_upkeep, R.string.info_value_per_turn, 8),
        ),
    )

    private val units = GuideSection(
        R.string.guide_section_units,
        listOf(soldiers, archer, catapult, transport, warship),
    )

    private val farm = GuideEntry(
        id = "building_farm",
        iconRes = PieceIcons.building(Building.FARM),
        nameRes = R.string.building_farm,
        descRes = R.string.info_farm,
        howToRes = R.string.guide_how_farm,
        requirementRes = R.string.guide_req_farm,
        stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income, 4)),
    )
    private val tower = GuideEntry(
        id = "building_tower",
        iconRes = PieceIcons.building(Building.TOWER),
        nameRes = R.string.building_tower,
        descRes = R.string.info_tower,
        howToRes = R.string.guide_how_tower,
        stats = listOf(GuideStat(R.string.info_stat_defense, R.string.info_value_defense_area, 2)),
    )
    private val castle = GuideEntry(
        id = "building_castle",
        iconRes = PieceIcons.building(Building.STRONG_TOWER),
        nameRes = R.string.building_castle,
        descRes = R.string.info_castle,
        howToRes = R.string.guide_how_castle,
        stats = listOf(GuideStat(R.string.info_stat_defense, R.string.info_value_defense_area, 3)),
    )
    private val mine = GuideEntry(
        id = "building_mine",
        iconRes = PieceIcons.building(Building.MINE),
        nameRes = R.string.building_mine,
        descRes = R.string.info_mine,
        howToRes = R.string.guide_how_mine,
        requirementRes = R.string.guide_req_mine,
        stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income, 6)),
    )
    private val market = GuideEntry(
        id = "building_market",
        iconRes = PieceIcons.building(Building.MARKET),
        nameRes = R.string.building_market,
        descRes = R.string.info_market,
        howToRes = R.string.guide_how_market,
        stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income_max, 5)),
    )
    private val lumberCamp = GuideEntry(
        id = "building_lumber_camp",
        iconRes = PieceIcons.building(Building.LUMBER_CAMP),
        nameRes = R.string.building_lumber_camp,
        descRes = R.string.info_lumber_camp,
        howToRes = R.string.guide_how_lumber,
        requirementRes = R.string.guide_req_lumber,
        stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income_max, 8)),
    )
    private val watchtower = GuideEntry(
        id = "building_watchtower",
        iconRes = PieceIcons.building(Building.WATCHTOWER),
        nameRes = R.string.building_watchtower,
        descRes = R.string.info_watchtower,
        howToRes = R.string.guide_how_watchtower,
        requirementRes = R.string.guide_req_watchtower,
        stats = listOf(GuideStat(R.string.info_stat_vision, R.string.info_value_plain, 6)),
    )
    private val port = GuideEntry(
        id = "building_port",
        iconRes = PieceIcons.building(Building.PORT),
        nameRes = R.string.building_port,
        descRes = R.string.info_port,
        howToRes = R.string.guide_how_port,
        requirementRes = R.string.guide_req_port_coast,
        stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income, 2)),
    )
    private val fishery = GuideEntry(
        id = "building_fishery",
        iconRes = PieceIcons.building(Building.FISHERY),
        nameRes = R.string.building_fishery,
        descRes = R.string.info_fishery,
        howToRes = R.string.guide_how_fishery,
        requirementRes = R.string.guide_req_fishery,
        stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income_max, 9)),
    )
    private val bridge = GuideEntry(
        id = "building_bridge",
        iconRes = PieceIcons.building(Building.BRIDGE),
        nameRes = R.string.building_bridge,
        descRes = R.string.info_bridge,
        howToRes = R.string.guide_how_bridge,
        requirementRes = R.string.guide_req_bridge,
    )
    private val capital = GuideEntry(
        id = "building_capital",
        iconRes = PieceIcons.building(Building.CAPITAL),
        nameRes = R.string.building_capital,
        descRes = R.string.guide_desc_capital,
        howToRes = R.string.guide_how_capital,
        stats = listOf(GuideStat(R.string.info_stat_defense, R.string.info_value_defense_area, 1)),
    )

    private val buildings = GuideSection(
        R.string.guide_section_buildings,
        listOf(capital, farm, tower, castle, mine, market, lumberCamp, watchtower, port, fishery, bridge),
    )

    private val resources = GuideSection(
        R.string.guide_section_resources,
        listOf(
            GuideEntry(
                id = "res_gold_vein",
                iconRes = PieceIcons.goldVein,
                nameRes = R.string.piece_gold_vein,
                descRes = R.string.info_gold_vein,
                howToRes = R.string.guide_how_gold_vein,
            ),
            GuideEntry(
                id = "res_fertile",
                iconRes = PieceIcons.fertile,
                nameRes = R.string.piece_fertile,
                descRes = R.string.info_fertile,
                howToRes = R.string.guide_how_fertile,
                stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income, 1)),
            ),
            GuideEntry(
                id = "res_tree",
                iconRes = PieceIcons.tree,
                nameRes = R.string.piece_tree,
                descRes = R.string.info_tree,
                howToRes = R.string.guide_how_tree,
                stats = listOf(GuideStat(R.string.info_stat_clear_bonus, R.string.info_value_coins, 3)),
            ),
            GuideEntry(
                id = "res_fish_shoal",
                iconRes = PieceIcons.fishShoal,
                nameRes = R.string.piece_fish_shoal,
                descRes = R.string.info_fish_shoal,
                howToRes = R.string.guide_how_fish_shoal,
                stats = listOf(GuideStat(R.string.info_stat_income, R.string.info_value_income, 3)),
            ),
            GuideEntry(
                id = "res_gravestone",
                iconRes = PieceIcons.gravestone,
                nameRes = R.string.piece_gravestone,
                descRes = R.string.info_gravestone,
                howToRes = R.string.guide_how_gravestone,
            ),
            GuideEntry(
                id = "res_cut_off",
                iconRes = null,
                nameRes = R.string.tile_cut_off,
                descRes = R.string.info_cut_off,
                howToRes = R.string.guide_how_cut_off,
            ),
        ),
    )

    private val civs = GuideSection(
        R.string.guide_section_civs,
        listOf(
            GuideEntry(
                id = "civ_kingdom",
                iconRes = R.drawable.piece_capital,
                nameRes = R.string.civ_kingdom,
                descRes = R.string.guide_desc_civ_kingdom,
                howToRes = R.string.guide_how_civ,
            ),
            GuideEntry(
                id = "civ_vikings",
                iconRes = R.drawable.piece_vikings_capital,
                nameRes = R.string.civ_vikings,
                descRes = R.string.guide_desc_civ_vikings,
                howToRes = null,
            ),
            GuideEntry(
                id = "civ_sultanate",
                iconRes = R.drawable.piece_sultanate_capital,
                nameRes = R.string.civ_sultanate,
                descRes = R.string.guide_desc_civ_sultanate,
                howToRes = null,
            ),
            GuideEntry(
                id = "civ_shogunate",
                iconRes = R.drawable.piece_shogunate_capital,
                nameRes = R.string.civ_shogunate,
                descRes = R.string.guide_desc_civ_shogunate,
                howToRes = null,
            ),
        ),
    )

    val sections: List<GuideSection> = listOf(basics, civs, units, buildings, resources)

    /** The guide entry for a purchasable building, so a purchase card can link to it. */
    fun forStructure(type: BuildingType): GuideEntry = when (type) {
        BuildingType.FARM -> farm
        BuildingType.TOWER -> tower
        BuildingType.STRONG_TOWER -> castle
        BuildingType.MINE -> mine
        BuildingType.MARKET -> market
        BuildingType.LUMBER_CAMP -> lumberCamp
        BuildingType.WATCHTOWER -> watchtower
        BuildingType.PORT -> port
        BuildingType.FISHERY -> fishery
        BuildingType.BRIDGE -> bridge
    }

    /** The guide entry for a purchasable unit (all soldier tiers share one entry). */
    fun forUnit(type: UnitType): GuideEntry = when (type) {
        UnitType.SOLDIER -> soldiers
        UnitType.ARCHER -> archer
        UnitType.CATAPULT -> catapult
        UnitType.TRANSPORT -> transport
        UnitType.WARSHIP -> warship
    }

    /** The guide entry id for a civilization, so a civ picker can deep-link it. */
    fun civEntryId(civ: Civilization): String = when (civ) {
        Civilization.KINGDOM -> "civ_kingdom"
        Civilization.VIKINGS -> "civ_vikings"
        Civilization.SULTANATE -> "civ_sultanate"
        Civilization.SHOGUNATE -> "civ_shogunate"
    }
}
