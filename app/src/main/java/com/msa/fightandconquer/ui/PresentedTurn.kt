package com.msa.fightandconquer.ui

import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerKind

/**
 * The seat the HUD calls "current". An AI seat still being shown on the board wins over the
 * engine's current player, so the human's chrome (FAB, action bar, tray, taps) appears only
 * once the board is still — the turn-by-turn cycle as the player sees it.
 */
internal data class PresentedTurn(val seat: Int, val isHuman: Boolean, val aiActing: Boolean)

/**
 * [presentedAiSeat] is the AI seat the HUD presents while its beats play (null = follow the
 * engine); see `GameViewModel.presentedAiSeat` and docs/ui-hud.md "AI driving & autosave".
 */
internal fun presentedTurn(state: GameState, presentedAiSeat: Int?): PresentedTurn =
    if (presentedAiSeat != null) {
        PresentedTurn(seat = presentedAiSeat, isHuman = false, aiActing = true)
    } else {
        PresentedTurn(
            seat = state.currentPlayer.value,
            isHuman = state.player(state.currentPlayer).kind is PlayerKind.Human,
            aiActing = false,
        )
    }
