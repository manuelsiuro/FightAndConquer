package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.share.ShareError
import com.msa.fightandconquer.ui.UiText

/**
 * Copy for the editor's engine-side vocabulary, following `RejectionReason.toUiText`:
 * exhaustive `when`s, so a new violation or share error fails to compile until it has
 * a string. Seats are shown 1-based ("Player 3"), hexes as their axial coordinates.
 */
private fun coords(hex: Hex): String = "(${hex.q}, ${hex.r})"

fun MapViolation.toUiText(): UiText = when (this) {
    MapViolation.NoLand -> UiText.of(R.string.editor_v_no_land)
    MapViolation.DuplicateTiles -> UiText.of(R.string.editor_v_duplicate_tiles)
    is MapViolation.SeaTileOwned -> UiText.of(R.string.editor_v_sea_owned, coords(hex))
    is MapViolation.SeaTileHasBuilding -> UiText.of(R.string.editor_v_sea_building, coords(hex))
    is MapViolation.SeaTileHasFlora -> UiText.of(R.string.editor_v_sea_flora, coords(hex))
    is MapViolation.SeaTileLandDeposit -> UiText.of(R.string.editor_v_sea_land_deposit, coords(hex))
    MapViolation.FishShoalOnLand -> UiText.of(R.string.editor_v_fish_on_land)
    is MapViolation.SeaSplit -> UiText.of(R.string.editor_v_sea_split, components)
    MapViolation.SurfaceDisconnected -> UiText.of(R.string.editor_v_surface_disconnected)
    is MapViolation.LandmassUnreachable -> UiText.of(R.string.editor_v_landmass_unreachable)
    MapViolation.NoCapitals -> UiText.of(R.string.editor_v_no_capitals)
    MapViolation.DuplicateCapitals -> UiText.of(R.string.editor_v_duplicate_capitals)
    is MapViolation.CapitalOffMap -> UiText.of(R.string.editor_v_capital_off_map, seat + 1)
    is MapViolation.CapitalUnmarked -> UiText.of(R.string.editor_v_capital_unmarked, seat + 1)
    is MapViolation.CapitalWrongOwner -> UiText.of(R.string.editor_v_capital_wrong_owner, seat + 1)
    is MapViolation.SeatOwnsNothing -> UiText.of(R.string.editor_v_seat_owns_nothing, seat + 1)
    is MapViolation.SeatCutOffTiles -> UiText.of(R.string.editor_v_seat_cut_off, seat + 1, count)
    is MapViolation.OrphanOwner -> UiText.of(R.string.editor_v_orphan_owner, seat + 1)
    is MapViolation.SeatCountMismatch -> UiText.of(R.string.editor_v_seat_count, seats, capitals)
    MapViolation.NoPlayerSeat -> UiText.of(R.string.editor_v_no_player_seat)
    MapViolation.MultiplePlayerSeats -> UiText.of(R.string.editor_v_multiple_player_seats)
    is MapViolation.TreasurySizeMismatch -> UiText.of(R.string.editor_v_treasury_size)
    is MapViolation.CivsSizeMismatch -> UiText.of(R.string.editor_v_civs_size)
    is MapViolation.UnitOffMap -> UiText.of(R.string.editor_v_unit_off_map, coords(hex))
    is MapViolation.UnitStacked -> UiText.of(R.string.editor_v_unit_stacked, coords(hex))
    is MapViolation.UnitNotOnOwnedGround ->
        UiText.of(R.string.editor_v_unit_ground, seat + 1, coords(hex))
    is MapViolation.UnitSeatMissing -> UiText.of(R.string.editor_v_unit_seat, coords(hex))
    MapViolation.NoObjectives -> UiText.of(R.string.editor_v_no_objectives)
    is MapViolation.ObjectiveHexOffMap -> UiText.of(R.string.editor_v_objective_off_map, coords(hex))
    is MapViolation.ObjectiveHexUnreachable ->
        UiText.of(R.string.editor_v_objective_unreachable, coords(hex))
    is MapViolation.ObjectiveSeatMissing -> UiText.of(R.string.editor_v_objective_seat, seat + 1)
    is MapViolation.ObjectiveAlreadyDecided -> UiText.of(R.string.editor_v_objective_decided)
    MapViolation.FailureAtStart -> UiText.of(R.string.editor_v_failure_at_start)
}

fun ShareError.toUiText(): UiText = when (this) {
    ShareError.NOT_A_MAP_CODE -> UiText.of(R.string.share_error_not_a_code)
    ShareError.UNSUPPORTED_VERSION -> UiText.of(R.string.share_error_unsupported_version)
    ShareError.CORRUPTED -> UiText.of(R.string.share_error_corrupted)
    ShareError.MALFORMED -> UiText.of(R.string.share_error_malformed)
    ShareError.INVALID_MAP -> UiText.of(R.string.share_error_invalid_map)
}
