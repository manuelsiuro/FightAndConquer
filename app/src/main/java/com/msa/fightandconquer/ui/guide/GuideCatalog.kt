package com.msa.fightandconquer.ui.guide

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
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
        ),
    )

    /** Shared by every soldier tier — the ladder is one concept, not four cards. */
    private val soldiers = GuideEntry(
        id = "unit_soldiers",
        iconRes = PieceIcons.unit(UnitType.SOLDIER, tier = 2),
        nameRes = R.string.guide_unit_soldiers,
        descRes = R.string.guide_desc_soldiers,
        howToRes = R.string.guide_how_soldiers,
    )

    private val archer = GuideEntry(
        id = "unit_archer",
        iconRes = PieceIcons.unit(UnitType.ARCHER, tier = 1),
        nameRes = R.string.unit_archer,
        descRes = R.string.info_archer,
        howToRes = R.string.guide_how_archer,
        requirementRes = R.string.guide_req_special,
        stats = listOf(
            GuideStat(R.string.info_stat_strength, R.string.info_value_plain, 1),
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
            GuideStat(R.string.info_stat_strength, R.string.info_value_plain, 2),
            GuideStat(R.string.info_stat_range, R.string.info_value_plain, 2),
            GuideStat(R.string.info_stat_upkeep, R.string.info_value_per_turn, 10),
        ),
    )

    private val units = GuideSection(
        R.string.guide_section_units,
        listOf(soldiers, archer, catapult),
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
        listOf(capital, farm, tower, castle, mine, market, lumberCamp, watchtower),
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

    val sections: List<GuideSection> = listOf(basics, units, buildings, resources)

    /** The guide entry for a purchasable building, so a purchase card can link to it. */
    fun forStructure(type: BuildingType): GuideEntry = when (type) {
        BuildingType.FARM -> farm
        BuildingType.TOWER -> tower
        BuildingType.STRONG_TOWER -> castle
        BuildingType.MINE -> mine
        BuildingType.MARKET -> market
        BuildingType.LUMBER_CAMP -> lumberCamp
        BuildingType.WATCHTOWER -> watchtower
    }

    /** The guide entry for a purchasable unit (all soldier tiers share one entry). */
    fun forUnit(type: UnitType): GuideEntry = when (type) {
        UnitType.SOLDIER -> soldiers
        UnitType.ARCHER -> archer
        UnitType.CATAPULT -> catapult
    }
}
