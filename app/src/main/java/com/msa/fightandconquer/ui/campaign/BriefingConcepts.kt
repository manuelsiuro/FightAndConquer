package com.msa.fightandconquer.ui.campaign

import androidx.annotation.StringRes
import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.ui.guide.GuideCatalog

/** A briefing chip: a label plus the [GuideCatalog] entry it opens. */
data class BriefingConcept(@StringRes val labelRes: Int, val guideEntryId: String)

/**
 * The "new in this mission" chips, derived from what the level's own rules switch on
 * rather than from a hand-kept list per level.
 *
 * Deriving it means the chips can never contradict the mission: a level that disables
 * boats cannot advertise them, and adding a level needs no extra bookkeeping. The order
 * runs from the most specialised idea to the most familiar and is capped at three, so
 * the late missions — where everything is enabled — show the interesting end of the list
 * instead of a wall of chips.
 */
object BriefingConcepts {

    private val advanced = listOf(
        BuildingType.BRIDGE,
        BuildingType.FISHERY,
        BuildingType.PORT,
        BuildingType.WATCHTOWER,
        BuildingType.MINE,
        BuildingType.MARKET,
        BuildingType.LUMBER_CAMP,
        BuildingType.STRONG_TOWER,
        BuildingType.TOWER,
        BuildingType.FARM,
    )

    fun forLevel(level: LevelDef, limit: Int = 3): List<BriefingConcept> {
        val rules = level.rules
        val concepts = ArrayList<BriefingConcept>()
        var extra = 0

        // Map-derived on purpose: a shoal-less naval mission must not advertise
        // the dory it cannot use. Naval-gated only (dories are boats, not
        // "specials" — checkBuyNaval sells them on navalEnabled alone). The
        // dory chip widens the cap by one so the fishing lesson never evicts a
        // concept the mission showed before it existed (the FISHERY chip loses
        // the take() race otherwise).
        if (rules.navalEnabled &&
            level.map.tiles.any { it.deposit == com.msa.fightandconquer.core.model.Deposit.FISH_SHOAL }
        ) {
            concepts += BriefingConcept(
                com.msa.fightandconquer.R.string.unit_fishing_boat,
                GuideCatalog.forUnit(UnitType.FISHING_BOAT).id,
            )
            extra = 1
        }
        if (rules.navalEnabled) {
            concepts += BriefingConcept(
                com.msa.fightandconquer.R.string.unit_transport,
                GuideCatalog.forUnit(UnitType.TRANSPORT).id,
            )
        }
        if (rules.specialUnitsEnabled) {
            concepts += BriefingConcept(
                com.msa.fightandconquer.R.string.unit_catapult,
                GuideCatalog.forUnit(UnitType.CATAPULT).id,
            )
        }
        for (type in advanced) {
            if (type in rules.disabledBuildings) continue
            if (!rules.navalEnabled && type in NAVAL_BUILDINGS) continue
            if (!rules.fogOfWar && type == BuildingType.WATCHTOWER) continue
            val entry = GuideCatalog.forStructure(type)
            concepts += BriefingConcept(entry.nameRes, entry.id)
        }
        return concepts.take(limit + extra)
    }

    private val NAVAL_BUILDINGS = setOf(BuildingType.PORT, BuildingType.FISHERY, BuildingType.BRIDGE)
}
