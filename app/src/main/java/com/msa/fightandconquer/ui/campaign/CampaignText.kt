package com.msa.fightandconquer.ui.campaign

import androidx.annotation.StringRes
import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.campaign.DefeatReason
import com.msa.fightandconquer.core.campaign.FailCondition
import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.ObjectiveRow
import com.msa.fightandconquer.ui.UiText
import com.msa.fightandconquer.ui.buildingNameRes
import com.msa.fightandconquer.ui.seatNameRes
import com.msa.fightandconquer.ui.unitNameRes

/**
 * The bridge between the engine's string **ids** and `strings.xml`.
 *
 * `:core` has no resources, so a campaign definition carries stable ids (`"academy"`,
 * `"academy_first_steps"`, `"select"`) and never prose — which is also what lets the
 * whole catalogue be translated by adding one `values-<lang>/strings.xml`.
 *
 * The lookup is an explicit table rather than `getIdentifier`: resource names resolved
 * by string are invisible to the shrinker and fail silently at runtime, whereas a
 * missing entry here is caught by `CampaignTextTest` against the shipped JSON.
 */
object CampaignText {

    data class CampaignCopy(@StringRes val name: Int, @StringRes val blurb: Int)
    data class LevelCopy(
        @StringRes val name: Int,
        @StringRes val briefing: Int,
        @StringRes val debrief: Int,
    )

    private val campaigns = mapOf(
        "academy" to CampaignCopy(R.string.campaign_academy_name, R.string.campaign_academy_blurb),
        "isles" to CampaignCopy(R.string.campaign_isles_name, R.string.campaign_isles_blurb),
        "crown" to CampaignCopy(R.string.campaign_crown_name, R.string.campaign_crown_blurb),
    )

    private val levels = mapOf(
        "academy_first_steps" to LevelCopy(
            R.string.level_academy_first_steps_name,
            R.string.level_academy_first_steps_briefing,
            R.string.level_academy_first_steps_debrief,
        ),
        "academy_coin_and_crown" to LevelCopy(
            R.string.level_academy_coin_and_crown_name,
            R.string.level_academy_coin_and_crown_briefing,
            R.string.level_academy_coin_and_crown_debrief,
        ),
        "academy_shoulder" to LevelCopy(
            R.string.level_academy_shoulder_name,
            R.string.level_academy_shoulder_briefing,
            R.string.level_academy_shoulder_debrief,
        ),
        "academy_stone_and_timber" to LevelCopy(
            R.string.level_academy_stone_and_timber_name,
            R.string.level_academy_stone_and_timber_briefing,
            R.string.level_academy_stone_and_timber_debrief,
        ),
        "academy_cut_the_line" to LevelCopy(
            R.string.level_academy_cut_the_line_name,
            R.string.level_academy_cut_the_line_briefing,
            R.string.level_academy_cut_the_line_debrief,
        ),
        "academy_ranged_and_siege" to LevelCopy(
            R.string.level_academy_ranged_and_siege_name,
            R.string.level_academy_ranged_and_siege_briefing,
            R.string.level_academy_ranged_and_siege_debrief,
        ),
        "academy_salt_and_sail" to LevelCopy(
            R.string.level_academy_salt_and_sail_name,
            R.string.level_academy_salt_and_sail_briefing,
            R.string.level_academy_salt_and_sail_debrief,
        ),
        "academy_words_before_swords" to LevelCopy(
            R.string.level_academy_words_before_swords_name,
            R.string.level_academy_words_before_swords_briefing,
            R.string.level_academy_words_before_swords_debrief,
        ),
        "isles_landfall" to LevelCopy(
            R.string.level_isles_landfall_name,
            R.string.level_isles_landfall_briefing,
            R.string.level_isles_landfall_debrief,
        ),
        "isles_lighthouse" to LevelCopy(
            R.string.level_isles_lighthouse_name,
            R.string.level_isles_lighthouse_briefing,
            R.string.level_isles_lighthouse_debrief,
        ),
        "isles_wolves" to LevelCopy(
            R.string.level_isles_wolves_name,
            R.string.level_isles_wolves_briefing,
            R.string.level_isles_wolves_debrief,
        ),
        "isles_strait" to LevelCopy(
            R.string.level_isles_strait_name,
            R.string.level_isles_strait_briefing,
            R.string.level_isles_strait_debrief,
        ),
        "isles_admirals_grave" to LevelCopy(
            R.string.level_isles_admirals_grave_name,
            R.string.level_isles_admirals_grave_briefing,
            R.string.level_isles_admirals_grave_debrief,
        ),
        "isles_crown_of_salt" to LevelCopy(
            R.string.level_isles_crown_of_salt_name,
            R.string.level_isles_crown_of_salt_briefing,
            R.string.level_isles_crown_of_salt_debrief,
        ),
        "crown_granary" to LevelCopy(
            R.string.level_crown_granary_name,
            R.string.level_crown_granary_briefing,
            R.string.level_crown_granary_debrief,
        ),
        "crown_siege_of_ash" to LevelCopy(
            R.string.level_crown_siege_of_ash_name,
            R.string.level_crown_siege_of_ash_briefing,
            R.string.level_crown_siege_of_ash_debrief,
        ),
        "crown_iron_veins" to LevelCopy(
            R.string.level_crown_iron_veins_name,
            R.string.level_crown_iron_veins_briefing,
            R.string.level_crown_iron_veins_debrief,
        ),
        "crown_last_wall" to LevelCopy(
            R.string.level_crown_last_wall_name,
            R.string.level_crown_last_wall_briefing,
            R.string.level_crown_last_wall_debrief,
        ),
        "crown_breach" to LevelCopy(
            R.string.level_crown_breach_name,
            R.string.level_crown_breach_briefing,
            R.string.level_crown_breach_debrief,
        ),
        "crown_three_thrones" to LevelCopy(
            R.string.level_crown_three_thrones_name,
            R.string.level_crown_three_thrones_briefing,
            R.string.level_crown_three_thrones_debrief,
        ),
    )

    /** Keyed "levelId/hintId" — hint ids are only unique within their level. */
    private val hints = mapOf(
        "academy_first_steps/select" to R.string.hint_academy_first_steps_select,
        "academy_first_steps/capture" to R.string.hint_academy_first_steps_capture,
        "academy_first_steps/end_turn" to R.string.hint_academy_first_steps_end_turn,
        "academy_first_steps/finish" to R.string.hint_academy_first_steps_finish,
        "academy_coin_and_crown/coins" to R.string.hint_academy_coin_and_crown_coins,
        "academy_coin_and_crown/farm" to R.string.hint_academy_coin_and_crown_farm,
        "academy_coin_and_crown/expand" to R.string.hint_academy_coin_and_crown_expand,
        "academy_coin_and_crown/upkeep" to R.string.hint_academy_coin_and_crown_upkeep,
        "academy_shoulder/defense" to R.string.hint_academy_shoulder_defense,
        "academy_shoulder/merge" to R.string.hint_academy_shoulder_merge,
        "academy_shoulder/storm" to R.string.hint_academy_shoulder_storm,
        "academy_stone_and_timber/trees" to R.string.hint_academy_stone_and_timber_trees,
        "academy_stone_and_timber/tower" to R.string.hint_academy_stone_and_timber_tower,
        "academy_stone_and_timber/hold" to R.string.hint_academy_stone_and_timber_hold,
        "academy_cut_the_line/slicing" to R.string.hint_academy_cut_the_line_slicing,
        "academy_cut_the_line/loot" to R.string.hint_academy_cut_the_line_loot,
        "academy_ranged_and_siege/castle" to R.string.hint_academy_ranged_and_siege_castle,
        "academy_ranged_and_siege/catapult" to R.string.hint_academy_ranged_and_siege_catapult,
        "academy_ranged_and_siege/archer" to R.string.hint_academy_ranged_and_siege_archer,
        "academy_salt_and_sail/port" to R.string.hint_academy_salt_and_sail_port,
        "academy_salt_and_sail/transport" to R.string.hint_academy_salt_and_sail_transport,
        "academy_salt_and_sail/landing" to R.string.hint_academy_salt_and_sail_landing,
        "academy_salt_and_sail/conquer" to R.string.hint_academy_salt_and_sail_conquer,
        "academy_words_before_swords/outnumbered" to R.string.hint_academy_words_before_swords_outnumbered,
        "academy_words_before_swords/pact" to R.string.hint_academy_words_before_swords_pact,
        "academy_words_before_swords/betray" to R.string.hint_academy_words_before_swords_betray,
        "isles_landfall/embark" to R.string.hint_isles_landfall_embark,
        "isles_landfall/sail" to R.string.hint_isles_landfall_sail,
        "isles_landfall/supply" to R.string.hint_isles_landfall_supply,
        "isles_lighthouse/fog" to R.string.hint_isles_lighthouse_fog,
        "isles_lighthouse/beacon" to R.string.hint_isles_lighthouse_beacon,
        "isles_lighthouse/endure" to R.string.hint_isles_lighthouse_endure,
        "isles_wolves/warship" to R.string.hint_isles_wolves_warship,
        "isles_wolves/ties" to R.string.hint_isles_wolves_ties,
        "isles_strait/bridge" to R.string.hint_isles_strait_bridge,
        "isles_strait/march" to R.string.hint_isles_strait_march,
        "isles_admirals_grave/two_fronts" to R.string.hint_isles_admirals_grave_two_fronts,
        "isles_admirals_grave/admiral" to R.string.hint_isles_admirals_grave_admiral,
        "isles_crown_of_salt/finale" to R.string.hint_isles_crown_of_salt_finale,
        "crown_granary/loam" to R.string.hint_crown_granary_loam,
        "crown_granary/market" to R.string.hint_crown_granary_market,
        "crown_granary/harvest" to R.string.hint_crown_granary_harvest,
        "crown_siege_of_ash/outnumbered" to R.string.hint_crown_siege_of_ash_outnumbered,
        "crown_siege_of_ash/levy" to R.string.hint_crown_siege_of_ash_levy,
        "crown_siege_of_ash/hold_on" to R.string.hint_crown_siege_of_ash_hold_on,
        "crown_iron_veins/veins" to R.string.hint_crown_iron_veins_veins,
        "crown_iron_veins/mine" to R.string.hint_crown_iron_veins_mine,
        "crown_iron_veins/bank" to R.string.hint_crown_iron_veins_bank,
        "crown_last_wall/bastion" to R.string.hint_crown_last_wall_bastion,
        "crown_last_wall/pact" to R.string.hint_crown_last_wall_pact,
        "crown_last_wall/endure" to R.string.hint_crown_last_wall_endure,
        "crown_breach/wall" to R.string.hint_crown_breach_wall,
        "crown_breach/siege" to R.string.hint_crown_breach_siege,
        "crown_breach/through" to R.string.hint_crown_breach_through,
        "crown_three_thrones/succession" to R.string.hint_crown_three_thrones_succession,
    )

    /** Keyed "levelId/scriptId" — the narration for a story beat. */
    private val scripts = mapOf(
        "isles_landfall/relief_squadron" to R.string.script_isles_landfall_relief_squadron,
        "isles_lighthouse/night_raid" to R.string.script_isles_lighthouse_night_raid,
        "isles_wolves/wolves_first" to R.string.script_isles_wolves_wolves_first,
        "isles_wolves/wolves_second" to R.string.script_isles_wolves_wolves_second,
        "isles_wolves/wolves_third" to R.string.script_isles_wolves_wolves_third,
        "isles_wolves/wolves_fourth" to R.string.script_isles_wolves_wolves_fourth,
        "crown_siege_of_ash/the_levy" to R.string.script_crown_siege_of_ash_the_levy,
        "crown_last_wall/sworn_swords" to R.string.script_crown_last_wall_sworn_swords,
    )

    fun campaign(id: String): CampaignCopy? = campaigns[id]

    fun level(id: String): LevelCopy? = levels[id]

    @StringRes
    fun hint(levelId: String, hintId: String): Int? = hints["$levelId/$hintId"]

    @StringRes
    fun script(levelId: String, scriptId: String): Int? = scripts["$levelId/$scriptId"]

    // Exposed for CampaignTextTest, which cross-checks these against the shipped JSON.
    internal fun campaignIds(): Set<String> = campaigns.keys
    internal fun levelIds(): Set<String> = levels.keys
    internal fun hintKeys(): Set<String> = hints.keys
    internal fun scriptKeys(): Set<String> = scripts.keys
}

/**
 * One objective as a line of prose plus its `have / need` counter. Both are [UiText] so
 * the ViewModel can build them without a Context, exactly like every other HUD string.
 */
fun ObjectiveRow.label(): UiText = when (val o = objective) {
    Objective.ConquerAll -> UiText.of(R.string.objective_conquer_all)
    is Objective.CaptureHexes -> UiText.of(R.string.objective_capture_hexes)
    is Objective.HoldHexes -> UiText.of(R.string.objective_hold_hexes, o.rounds)
    is Objective.SurviveRounds -> UiText.of(R.string.objective_survive, o.rounds)
    is Objective.OwnHexCount -> UiText.of(R.string.objective_own_hexes, o.count)
    is Objective.ReachTreasury -> UiText.of(R.string.objective_treasury, o.coins)
    is Objective.ReachIncome -> UiText.of(R.string.objective_income, o.coins)
    is Objective.EliminatePlayer -> UiText.of(R.string.objective_eliminate, UiText.of(seatNameRes(o.seat.value)))
    is Objective.BuildCount ->
        UiText.of(R.string.objective_build, o.count, UiText.of(buildingNameRes(o.building)))
    is Objective.FieldUnits ->
        UiText.of(R.string.objective_field, o.count, UiText.of(unitNameRes(o.type, tier = 1)))
    is Objective.SinkBoats -> UiText.of(R.string.objective_sink, o.count)
}

/** The counter beside an objective; null when it is a plain yes/no. */
fun ObjectiveRow.counter(): UiText? =
    if (target <= 1) null else UiText.of(R.string.objective_progress, progress.coerceAtMost(target), target)

fun FailCondition.label(): UiText = when (this) {
    is FailCondition.TurnLimit -> UiText.of(R.string.fail_turn_limit, rounds)
    is FailCondition.LoseHexes -> UiText.of(R.string.fail_lose_hexes)
    FailCondition.LoseAllUnits -> UiText.of(R.string.fail_lose_all_units)
    is FailCondition.AllyEliminated -> UiText.of(R.string.fail_ally_lost, UiText.of(seatNameRes(seat.value)))
}

fun DefeatReason.label(): UiText = when (this) {
    DefeatReason.ELIMINATED -> UiText.of(R.string.defeat_eliminated)
    DefeatReason.OUT_OF_TIME -> UiText.of(R.string.defeat_out_of_time)
    DefeatReason.LOST_PROTECTED_HEX -> UiText.of(R.string.defeat_lost_protected)
    DefeatReason.NO_UNITS_LEFT -> UiText.of(R.string.defeat_no_units)
    DefeatReason.ALLY_LOST -> UiText.of(R.string.defeat_ally_lost)
    DefeatReason.RIVAL_VICTORY -> UiText.of(R.string.defeat_rival_victory)
}

