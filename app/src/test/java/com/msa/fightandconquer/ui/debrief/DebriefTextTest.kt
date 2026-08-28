package com.msa.fightandconquer.ui.debrief

import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.record.KeyMoment
import com.msa.fightandconquer.ui.UiText
import com.msa.fightandconquer.ui.seatNameRes
import org.junit.Assert.assertEquals
import org.junit.Test

/** [KeyMoment.label]'s naming strategy: colour names by default, caller-supplied otherwise. */
class DebriefTextTest {

    private val moment = KeyMoment.ShipSunk(round = 2, by = 0, owner = 1)

    @Test
    fun `the default keeps the debrief's colour names`() {
        assertEquals(
            UiText.of(
                R.string.moment_ship_sunk,
                UiText.of(seatNameRes(0)),
                UiText.of(seatNameRes(1)),
            ),
            moment.label(),
        )
    }

    @Test
    fun `a naming strategy renames every seat slot`() {
        assertEquals(
            UiText.of(
                R.string.moment_ship_sunk,
                UiText.of(R.string.hud_player, 1),
                UiText.of(R.string.hud_ai_player, 2),
            ),
            moment.label { seat ->
                UiText.of(if (seat == 0) R.string.hud_player else R.string.hud_ai_player, seat + 1)
            },
        )
    }
}
