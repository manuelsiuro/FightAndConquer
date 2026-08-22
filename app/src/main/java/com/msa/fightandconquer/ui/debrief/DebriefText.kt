package com.msa.fightandconquer.ui.debrief

import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.record.KeyMoment
import com.msa.fightandconquer.ui.UiText
import com.msa.fightandconquer.ui.seatNameRes

/**
 * One line of the chronicle's story feed. Exhaustive over the sealed [KeyMoment] —
 * a new moment type fails to compile until it has a narrative (the UiText.kt idiom).
 */
fun KeyMoment.label(): UiText = when (this) {
    is KeyMoment.CapitalLooted ->
        UiText.of(R.string.moment_capital_looted, seatName(by), seatName(victim), loot)
    is KeyMoment.PactBetrayed ->
        UiText.of(R.string.moment_pact_broken, seatName(breaker), seatName(victim), penalty)
    is KeyMoment.WentBankrupt -> UiText.of(R.string.moment_bankruptcy, seatName(seat))
    is KeyMoment.ShipSunk -> UiText.of(R.string.moment_ship_sunk, seatName(by), seatName(owner))
    is KeyMoment.Eliminated -> UiText.of(R.string.moment_eliminated, seatName(seat))
    is KeyMoment.Crowned -> UiText.of(R.string.moment_crowned, seatName(winner))
}

/** The seat whose colour dot fronts the feed row (whoever acted). */
val KeyMoment.actorSeat: Int
    get() = when (this) {
        is KeyMoment.CapitalLooted -> by
        is KeyMoment.PactBetrayed -> breaker
        is KeyMoment.WentBankrupt -> seat
        is KeyMoment.ShipSunk -> by
        is KeyMoment.Eliminated -> seat
        is KeyMoment.Crowned -> winner
    }

/** The seat the moment happened *to*; null when nobody suffered it. */
val KeyMoment.victimSeat: Int?
    get() = when (this) {
        is KeyMoment.CapitalLooted -> victim
        is KeyMoment.PactBetrayed -> victim
        is KeyMoment.WentBankrupt -> seat
        is KeyMoment.ShipSunk -> owner
        is KeyMoment.Eliminated -> seat
        is KeyMoment.Crowned -> null
    }

private fun seatName(seat: Int): UiText = UiText.of(seatNameRes(seat))
