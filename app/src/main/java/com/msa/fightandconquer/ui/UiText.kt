package com.msa.fightandconquer.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
fun UiText.resolve(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
}

/** Non-composable resolution (e.g. for content descriptions built in helpers). */
fun UiText.resolve(context: android.content.Context): String = when (this) {
    is UiText.Res -> context.getString(id, *args.toTypedArray())
    is UiText.Plural -> context.resources.getQuantityString(id, count, *args.toTypedArray())
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
}
