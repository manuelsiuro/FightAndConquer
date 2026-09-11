package com.msa.fightandconquer.ui

import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Plays every consecutive AI seat, one action per board beat, and reports completion only
 * once the board has finished showing the AI's last move.
 *
 * Ordering contract (the whole point — see docs/ui-hud.md "AI driving & autosave"):
 *  - an action is submitted only after the host reports the board settled for the previous
 *    one, then [beatGapMs] so consecutive moves read as distinct;
 *  - the next action is COMPUTED while the previous beat plays (state is already final, only
 *    the board lags), and recomputed if the state changed underneath (a script, a director);
 *  - after an AI EndTurn the driver still waits for the board before presenting the next
 *    seat or calling [Host.onAiTurnsDone];
 *  - [AiPlayer.MAX_ACTIONS_PER_TURN] forces an EndTurn, and a rejected action (an AI bug —
 *    it simulates legality) ends the turn too instead of spinning.
 * Every Host call marked "main" is made on [main]; the AI thinks on [compute].
 */
internal class AiTurnDriver(
    private val host: Host,
    private val compute: CoroutineDispatcher,
    private val main: CoroutineDispatcher,
    private val chooser: (GameState, PlayerKind.Ai) -> GameAction =
        { s, k -> AiPlayer(k.difficulty).chooseAction(s) },
    private val beatGapMs: Long = BEAT_GAP_MS,
) {

    /** Everything the driver needs from the ViewModel — no renderer, no Android, no engine. */
    interface Host {
        /** The engine's current state (a StateFlow value — readable from any thread). */
        val state: GameState

        /** main. Submit + feed the board + scoreboards + HUD. False = the engine rejected it. */
        fun submitAi(action: GameAction): Boolean

        /** main. An AI seat's turn just ended (the autosave point). */
        fun onAiSeatTurnEnded()

        /** main. The HUD presents [seat] as the acting AI seat from now on. */
        fun presentAiSeat(seat: Int)

        /** Suspends until the board has shown everything fed so far (no board → at once; the host bounds it). */
        suspend fun awaitBoardSettled()

        /** main. Normal completion: the board is still and the current seat is not an AI, or the game is over. */
        fun onAiTurnsDone()
    }

    /**
     * Runs until the current seat is human again or the game is over. Cancellation propagates
     * out as usual, so [Host.onAiTurnsDone] only ever runs on normal completion — the caller's
     * `finally` owns the cancelled case.
     */
    suspend fun run() {
        withContext(compute) {
            coroutineScope {
                var guard = 0
                var presented: Int? = null
                var thought: Pair<GameState, Deferred<GameAction>>? = null
                while (true) {
                    val current = host.state
                    if (current.phase !is GamePhase.Playing) break
                    val kind = current.player(current.currentPlayer).kind as? PlayerKind.Ai ?: break
                    val seat = current.currentPlayer.value
                    if (presented != seat) {
                        presented = seat
                        withContext(main) { host.presentAiSeat(seat) }
                    }
                    // The think-ahead is only valid for the exact state it was computed from.
                    val ahead = thought?.takeIf { it.first === current }?.second
                    if (ahead == null) thought?.second?.cancel()
                    val chosen = ahead?.await() ?: chooser(current, kind)
                    thought = null
                    guard++
                    val action =
                        if (guard >= AiPlayer.MAX_ACTIONS_PER_TURN) GameAction.EndTurn else chosen
                    val accepted = withContext(main) { host.submitAi(action) }
                    if (!accepted && action != GameAction.EndTurn) {
                        // Re-reading the unchanged state next round with a maxed guard ends the
                        // turn: one extra loop instead of a spin on the illegal action.
                        guard = AiPlayer.MAX_ACTIONS_PER_TURN - 1
                        continue
                    }
                    if (action == GameAction.EndTurn) {
                        guard = 0
                        withContext(main) { host.onAiSeatTurnEnded() }
                    }
                    val after = host.state
                    val nextKind = (after.player(after.currentPlayer).kind as? PlayerKind.Ai)
                        ?.takeIf { after.phase is GamePhase.Playing }
                    // Think while the board plays the beat we just fed it.
                    if (nextKind != null) thought = after to async { chooser(after, nextKind) }
                    host.awaitBoardSettled()
                    if (nextKind == null) break
                    delay(beatGapMs)
                }
                thought?.second?.cancel()
            }
        }
        withContext(main) { host.onAiTurnsDone() }
    }

    companion object {
        /** Pause between two AI beats, so consecutive moves read as separate actions. */
        const val BEAT_GAP_MS = 100L
    }
}
