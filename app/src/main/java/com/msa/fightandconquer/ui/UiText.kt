package com.msa.fightandconquer.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.msa.fightandconquer.core.engine.RejectionReason
import com.msa.fightandconquer.R

/**
 * A string the ViewModel can produce without holding a Context: a resource id plus
 * its format arguments, resolved by the composable that renders it. Keeps every
 * user-facing string in strings.xml and the ViewModel unit-testable.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): Res = Res(id, args.toList())

        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): Plural =
            Plural(id, count, args.toList())
    }
}

@Composable
fun UiText.resolve(): String {
    val context = LocalContext.current
    return resolve(context)
}

/**
 * Non-composable resolution (e.g. for content descriptions built in helpers).
 *
 * A format argument may itself be a [UiText] — campaign objectives compose lines like
 * "Build 2 × Farm" out of a template plus a piece name, and both halves have to stay in
 * `strings.xml` to be translatable. Nested arguments are resolved first.
 */
fun UiText.resolve(context: android.content.Context): String {
    fun flatten(args: List<Any>): Array<Any> =
        args.map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
    return when (this) {
        is UiText.Res -> context.getString(id, *flatten(args))
        is UiText.Plural -> context.resources.getQuantityString(id, count, *flatten(args))
    }
}

/** Unit tier (1..4) -> display name resource (soldier ladder). */
@StringRes
fun unitNameRes(tier: Int): Int = when (tier) {
    1 -> R.string.unit_peasant
    2 -> R.string.unit_spearman
    3 -> R.string.unit_baron
    else -> R.string.unit_knight
}

/** Type-aware unit display name (specials ignore tier). */
@StringRes
fun unitNameRes(type: com.msa.fightandconquer.core.model.UnitType, tier: Int): Int = when (type) {
    com.msa.fightandconquer.core.model.UnitType.SOLDIER -> unitNameRes(tier)
    com.msa.fightandconquer.core.model.UnitType.ARCHER -> R.string.unit_archer
    com.msa.fightandconquer.core.model.UnitType.CATAPULT -> R.string.unit_catapult
    com.msa.fightandconquer.core.model.UnitType.TRANSPORT -> R.string.unit_transport
    com.msa.fightandconquer.core.model.UnitType.WARSHIP -> R.string.unit_warship
    com.msa.fightandconquer.core.model.UnitType.FISHING_BOAT -> R.string.unit_fishing_boat
}

/** Building -> display name resource. */
@StringRes
fun buildingNameRes(building: com.msa.fightandconquer.core.model.Building): Int = when (building) {
    com.msa.fightandconquer.core.model.Building.CAPITAL -> R.string.building_capital
    com.msa.fightandconquer.core.model.Building.FARM -> R.string.building_farm
    com.msa.fightandconquer.core.model.Building.TOWER -> R.string.building_tower
    com.msa.fightandconquer.core.model.Building.STRONG_TOWER -> R.string.building_castle
    com.msa.fightandconquer.core.model.Building.MINE -> R.string.building_mine
    com.msa.fightandconquer.core.model.Building.MARKET -> R.string.building_market
    com.msa.fightandconquer.core.model.Building.LUMBER_CAMP -> R.string.building_lumber_camp
    com.msa.fightandconquer.core.model.Building.WATCHTOWER -> R.string.building_watchtower
    com.msa.fightandconquer.core.model.Building.PORT -> R.string.building_port
    com.msa.fightandconquer.core.model.Building.FISHERY -> R.string.building_fishery
    com.msa.fightandconquer.core.model.Building.BRIDGE -> R.string.building_bridge
}

/** Purchasable building type -> display name resource. */
@StringRes
fun buildingNameRes(type: com.msa.fightandconquer.core.model.BuildingType): Int =
    buildingNameRes(type.building)

/**
 * A seat's colour name ("the Red", …), shared by objectives, the editor palette and
 * anywhere else a seat is named rather than numbered.
 */
@StringRes
fun seatNameRes(index: Int): Int = when (index) {
    0 -> R.string.seat_name_1
    1 -> R.string.seat_name_2
    2 -> R.string.seat_name_3
    3 -> R.string.seat_name_4
    4 -> R.string.seat_name_5
    else -> R.string.seat_name_6
}

/** Civilization -> display name resource. */
@StringRes
fun civNameRes(civ: com.msa.fightandconquer.core.model.Civilization): Int = when (civ) {
    com.msa.fightandconquer.core.model.Civilization.KINGDOM -> R.string.civ_kingdom
    com.msa.fightandconquer.core.model.Civilization.VIKINGS -> R.string.civ_vikings
    com.msa.fightandconquer.core.model.Civilization.SULTANATE -> R.string.civ_sultanate
    com.msa.fightandconquer.core.model.Civilization.SHOGUNATE -> R.string.civ_shogunate
}

/** AI difficulty -> label resource. */
@StringRes
fun difficultyLabelRes(difficulty: com.msa.fightandconquer.core.model.Difficulty): Int =
    when (difficulty) {
        com.msa.fightandconquer.core.model.Difficulty.EASY -> R.string.difficulty_easy
        com.msa.fightandconquer.core.model.Difficulty.NORMAL -> R.string.difficulty_normal
        com.msa.fightandconquer.core.model.Difficulty.HARD -> R.string.difficulty_hard
        com.msa.fightandconquer.core.model.Difficulty.PASSIVE -> R.string.difficulty_passive
    }

/** Engine rejection code -> player-facing explanation. */
fun RejectionReason.toUiText(amount: Int?): UiText = when (this) {
    RejectionReason.GAME_FINISHED -> UiText.of(R.string.reject_game_finished)
    RejectionReason.NO_SUCH_UNIT -> UiText.of(R.string.reject_no_such_unit)
    RejectionReason.NOT_YOUR_UNIT -> UiText.of(R.string.reject_not_your_unit)
    RejectionReason.UNIT_ALREADY_ACTED -> UiText.of(R.string.reject_unit_already_acted)
    RejectionReason.DESTINATION_HAS_UNIT -> UiText.of(R.string.reject_destination_has_unit)
    RejectionReason.DESTINATION_UNREACHABLE -> UiText.of(R.string.reject_destination_unreachable)
    RejectionReason.INVALID_TIER -> UiText.of(R.string.reject_invalid_tier)
    RejectionReason.CANNOT_AFFORD -> UiText.of(R.string.reject_cannot_afford, amount ?: 0)
    RejectionReason.NO_SUCH_HEX -> UiText.of(R.string.reject_no_such_hex)
    RejectionReason.HEX_CUT_OFF -> UiText.of(R.string.reject_hex_cut_off)
    RejectionReason.HEX_HAS_BUILDING -> UiText.of(R.string.reject_hex_has_building)
    RejectionReason.HEX_HAS_UNIT -> UiText.of(R.string.reject_hex_has_unit)
    RejectionReason.HEX_OCCUPIED_INCOMPATIBLE -> UiText.of(R.string.reject_hex_occupied_incompatible)
    RejectionReason.NOT_ADJACENT_TO_TERRITORY -> UiText.of(R.string.reject_not_adjacent)
    RejectionReason.DEFENSE_TOO_HIGH -> UiText.of(R.string.reject_defense_too_high, amount ?: 0)
    RejectionReason.NOT_YOUR_HEX -> UiText.of(R.string.reject_not_your_hex)
    RejectionReason.HEX_NEEDS_CLEARING -> UiText.of(R.string.reject_hex_needs_clearing)
    RejectionReason.FARM_NEEDS_ADJACENCY -> UiText.of(R.string.reject_farm_needs_adjacency)
    RejectionReason.NOT_YOUR_UNITS -> UiText.of(R.string.reject_not_your_units)
    RejectionReason.CANNOT_MERGE_WITH_SELF -> UiText.of(R.string.reject_cannot_merge_self)
    RejectionReason.TIER_MISMATCH -> UiText.of(R.string.reject_tier_mismatch)
    RejectionReason.ALREADY_MAX_TIER -> UiText.of(R.string.reject_already_max_tier)
    RejectionReason.NOT_IN_SAME_REGION -> UiText.of(R.string.reject_not_same_region)
    RejectionReason.CANNOT_MERGE_SPECIAL -> UiText.of(R.string.reject_cannot_merge_special)
    RejectionReason.SPECIAL_UNITS_DISABLED -> UiText.of(R.string.reject_specials_disabled)
    RejectionReason.BUILDING_NEEDS_DEPOSIT -> UiText.of(R.string.reject_building_needs_deposit)
    RejectionReason.FISHERY_NEEDS_SHOAL -> UiText.of(R.string.reject_fishery_needs_shoal, amount ?: 0)
    RejectionReason.FERTILE_RESERVED_FOR_FARM -> UiText.of(R.string.reject_fertile_reserved_for_farm)
    RejectionReason.REQUIRES_FOG_OF_WAR -> UiText.of(R.string.reject_requires_fog)
    RejectionReason.DIPLOMACY_DISABLED -> UiText.of(R.string.reject_diplomacy_disabled)
    RejectionReason.INVALID_PLAYER -> UiText.of(R.string.reject_invalid_player)
    RejectionReason.INVALID_PACT_DURATION -> UiText.of(R.string.reject_invalid_pact_duration)
    RejectionReason.PACT_ALREADY_ACTIVE -> UiText.of(R.string.reject_pact_active)
    RejectionReason.PROPOSAL_PENDING -> UiText.of(R.string.reject_proposal_pending)
    RejectionReason.PROPOSAL_COOLDOWN -> UiText.of(R.string.reject_proposal_cooldown, amount ?: 0)
    RejectionReason.NO_SUCH_PROPOSAL -> UiText.of(R.string.reject_no_such_proposal)
    RejectionReason.INVALID_TRIBUTE_AMOUNT -> UiText.of(R.string.reject_invalid_tribute)
    RejectionReason.NO_GAME -> UiText.of(R.string.reject_no_game)
    RejectionReason.SEA_IMPASSABLE -> UiText.of(R.string.reject_sea_impassable)
    RejectionReason.NAVAL_DISABLED -> UiText.of(R.string.reject_naval_disabled)
    RejectionReason.REQUIRES_COAST -> UiText.of(R.string.reject_requires_coast)
    RejectionReason.REQUIRES_SEA -> UiText.of(R.string.reject_requires_sea)
    RejectionReason.NO_ADJACENT_PORT -> UiText.of(R.string.reject_no_adjacent_port)
    RejectionReason.NOT_A_TRANSPORT -> UiText.of(R.string.reject_not_a_transport)
    RejectionReason.TRANSPORT_FULL -> UiText.of(R.string.reject_transport_full)
    RejectionReason.TRANSPORT_EMPTY -> UiText.of(R.string.reject_transport_empty)
    RejectionReason.NOT_A_WARSHIP -> UiText.of(R.string.reject_not_a_warship)
    RejectionReason.INVALID_BOMBARD_TARGET -> UiText.of(R.string.reject_invalid_bombard)
    RejectionReason.BUILDING_NOT_AVAILABLE -> UiText.of(R.string.reject_building_not_available)
    // Campaign director faults: a player can never provoke these, but an authored
    // level with a stale trigger can — surfacing them beats a silent no-op.
    RejectionReason.SCRIPTED_EVENTS_DISABLED -> UiText.of(R.string.reject_scripted_events_disabled)
    RejectionReason.INVALID_SCRIPT_TARGET -> UiText.of(R.string.reject_invalid_script_target)
    RejectionReason.NOT_A_BRIDGE -> UiText.of(R.string.reject_not_a_bridge)
    RejectionReason.INVALID_ORIENTATION -> UiText.of(R.string.reject_invalid_orientation)
    RejectionReason.NO_BUILDING_THERE -> UiText.of(R.string.reject_no_building_there)
    RejectionReason.CANNOT_DEMOLISH_CAPITAL -> UiText.of(R.string.reject_cannot_demolish_capital)
}
