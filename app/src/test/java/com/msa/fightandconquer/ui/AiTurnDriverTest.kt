package com.msa.fightandconquer.ui

import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.Tile
import com.msa.fightandconquer.core.model.UnitId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ordering contract of [AiTurnDriver]: one action per settled board beat, a think-ahead
 * that overlaps the beat, and a handoff that only happens once the board is still.
 *
 * Virtual time: the fake host's [FakeHost.awaitBoardSettled] is the only real suspension
 * point, so releasing a settle is the test's way of saying "the board finished this beat".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiTurnDriverTest {

    private val passive = PlayerKind.Ai(Difficulty.PASSIVE)

    private val moveX = GameAction.MoveUnit(UnitId(1), Hex.of(0, 0))
    private val moveY = GameAction.MoveUnit(UnitId(2), Hex.of(1, 0))

    /** Same shape as `HudViewerTest.state`: one owned tile and one [PlayerState] per seat. */
    private fun state(kinds: List<PlayerKind>, currentPlayer: Int): GameState {
        val tiles = HashMap<Hex, Tile>()
        val players = kinds.mapIndexed { i, kind ->
            tiles[Hex.of(i, 0)] = Tile(owner = PlayerId(i))
            PlayerState(
                id = PlayerId(i),
                kind = kind,
                treasury = 10,
                capital = Hex.of(i, 0),
            )
        }
        return GameState(
            config = GameConfig(seed = 1L),
            tiles = tiles,
            units = emptyMap(),
            players = players,
            currentPlayer = PlayerId(currentPlayer),
            turnNumber = 0,
            rngState = 1L,
        )
    }

    /**
     * Records every host call and only settles when the test says so. It never validates an
     * action: legality is the engine's job, this fake only models the ordering the driver sees.
     */
    private class FakeHost(
        override var state: GameState,
        private val autoSettle: Boolean = false,
    ) : AiTurnDriver.Host {

        val submits = mutableListOf<GameAction>()
        val presented = mutableListOf<Int>()
        var turnEnded = 0
        var done = false
        val settles = mutableListOf<CompletableDeferred<Unit>>()

        /** Scripted per-seat answers; an exhausted queue yields [GameAction.EndTurn]. */
        private val script = HashMap<Int, ArrayDeque<GameAction>>()
        var chooserCalls = 0

        /** Actions the engine refuses (an AI bug in production). */
        var rejected: GameAction? = null

        /** The action after which the match is over. */
        var finishOn: GameAction? = null

        /** Runs at the start of every [awaitBoardSettled] — used to move the state underneath. */
        var onAwait: (() -> Unit)? = null

        fun script(seat: Int, vararg actions: GameAction) {
            script[seat] = ArrayDeque(actions.toList())
        }

        fun choose(state: GameState, kind: PlayerKind.Ai): GameAction {
            chooserCalls++
            return script[state.currentPlayer.value]?.removeFirstOrNull() ?: GameAction.EndTurn
        }

        override fun submitAi(action: GameAction): Boolean {
            submits += action
            if (action == rejected) return false
            when (action) {
                GameAction.EndTurn ->
                    state = state.copy(
                        currentPlayer = PlayerId((state.currentPlayer.value + 1) % state.players.size),
                    )
                finishOn -> state = state.copy(phase = GamePhase.Finished(PlayerId(1)))
                else -> Unit
            }
            return true
        }

        override fun onAiSeatTurnEnded() {
            turnEnded++
        }

        override fun presentAiSeat(seat: Int) {
            presented += seat
        }

        override suspend fun awaitBoardSettled() {
            onAwait?.invoke()
            if (autoSettle) return
            val settle = CompletableDeferred<Unit>()
            settles += settle
            settle.await()
        }

        override fun onAiTurnsDone() {
            done = true
        }
    }

    @Test
    fun `a passive AI hands over only after the board settles`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(state(listOf(PlayerKind.Human, passive), currentPlayer = 1))
        // The real AiPlayer: PASSIVE ends its turn straight away.
        val job = launch { AiTurnDriver(host, dispatcher, dispatcher).run() }
        advanceUntilIdle()

        assertEquals(listOf<GameAction>(GameAction.EndTurn), host.submits)
        assertEquals(listOf(1), host.presented)
        assertEquals(1, host.turnEnded)
        assertFalse("handed over before the board settled", host.done)
        assertEquals(1, host.settles.size)

        host.settles[0].complete(Unit)
        advanceUntilIdle()

        assertTrue(host.done)
        assertEquals(listOf<GameAction>(GameAction.EndTurn), host.submits)
        job.cancel()
    }

    @Test
    fun `one action per settled beat`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(state(listOf(PlayerKind.Human, passive), currentPlayer = 1))
        host.script(1, moveX, moveY, GameAction.EndTurn)
        val job = launch { AiTurnDriver(host, dispatcher, dispatcher, host::choose).run() }
        advanceUntilIdle()

        assertEquals(listOf<GameAction>(moveX), host.submits)

        host.settles[0].complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf<GameAction>(moveX, moveY), host.submits)

        host.settles[1].complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf(moveX, moveY, GameAction.EndTurn), host.submits)
        assertFalse(host.done)

        host.settles[2].complete(Unit)
        advanceUntilIdle()
        assertTrue(host.done)
        job.cancel()
    }

    @Test
    fun `thinks ahead while the beat plays`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(state(listOf(PlayerKind.Human, passive), currentPlayer = 1))
        host.script(1, moveX, moveY, GameAction.EndTurn)
        val job = launch { AiTurnDriver(host, dispatcher, dispatcher, host::choose).run() }
        advanceUntilIdle()

        assertEquals(1, host.submits.size)
        assertEquals("the next action must be computed during the beat", 2, host.chooserCalls)
        job.cancel()
    }

    @Test
    fun `a stale think-ahead is recomputed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(state(listOf(PlayerKind.Human, passive), currentPlayer = 1))
        host.script(1, moveX, moveY, GameAction.EndTurn)
        // Something else (a script, the campaign director) replaced the state during the beat.
        host.onAwait = { host.state = host.state.copy() }
        val job = launch { AiTurnDriver(host, dispatcher, dispatcher, host::choose).run() }
        advanceUntilIdle()

        assertEquals(listOf<GameAction>(moveX), host.submits)
        assertEquals(2, host.chooserCalls)

        host.settles[0].complete(Unit)
        advanceUntilIdle()

        // The stale think-ahead (moveY) was dropped; the third call decided on the new state.
        assertEquals(3, host.chooserCalls)
        assertEquals(listOf(moveX, GameAction.EndTurn), host.submits)
        job.cancel()
    }

    @Test
    fun `consecutive AI seats play in one run`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(state(listOf(passive, passive, PlayerKind.Human), currentPlayer = 0))
        val job = launch { AiTurnDriver(host, dispatcher, dispatcher).run() }
        advanceUntilIdle()

        assertEquals(listOf(0), host.presented)
        assertEquals(1, host.turnEnded)

        host.settles[0].complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(0, 1), host.presented)
        assertEquals(2, host.turnEnded)
        assertFalse(host.done)

        host.settles[1].complete(Unit)
        advanceUntilIdle()

        assertTrue(host.done)
        assertEquals(listOf(GameAction.EndTurn, GameAction.EndTurn), host.submits)
        job.cancel()
    }

    @Test
    fun `a game that ends mid-turn stops without a turn end`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(state(listOf(PlayerKind.Human, passive), currentPlayer = 1))
        host.script(1, moveX, moveY)
        host.finishOn = moveX
        val job = launch { AiTurnDriver(host, dispatcher, dispatcher, host::choose).run() }
        advanceUntilIdle()

        assertFalse(host.done)
        host.settles[0].complete(Unit)
        advanceUntilIdle()

        assertTrue(host.done)
        assertEquals(0, host.turnEnded)
        assertEquals(listOf<GameAction>(moveX), host.submits)
        job.cancel()
    }

    @Test
    fun `the action cap forces an EndTurn`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(
            state(listOf(passive, PlayerKind.Human), currentPlayer = 0),
            autoSettle = true,
        )
        val driver = AiTurnDriver(
            host,
            dispatcher,
            dispatcher,
            chooser = { _, _ -> host.chooserCalls++; moveX },
            beatGapMs = 0L,
        )
        val job = launch { driver.run() }
        advanceUntilIdle()

        assertEquals(AiPlayer.MAX_ACTIONS_PER_TURN, host.submits.size)
        assertEquals(GameAction.EndTurn, host.submits.last())
        assertEquals(1, host.turnEnded)
        assertTrue(host.done)
        job.cancel()
    }

    @Test
    fun `a rejected action ends the turn`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(
            state(listOf(PlayerKind.Human, passive), currentPlayer = 1),
            autoSettle = true,
        )
        host.rejected = moveX
        val driver = AiTurnDriver(
            host,
            dispatcher,
            dispatcher,
            chooser = { _, _ -> host.chooserCalls++; moveX },
            beatGapMs = 0L,
        )
        val job = launch { driver.run() }
        advanceUntilIdle()

        assertEquals(listOf(moveX, GameAction.EndTurn), host.submits)
        assertEquals(1, host.turnEnded)
        assertEquals(2, host.submits.size)
        assertTrue(host.done)
        job.cancel()
    }

    @Test
    fun `cancellation while waiting reports nothing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val host = FakeHost(state(listOf(PlayerKind.Human, passive), currentPlayer = 1))
        host.script(1, moveX, moveY, GameAction.EndTurn)
        val job = launch { AiTurnDriver(host, dispatcher, dispatcher, host::choose).run() }
        advanceUntilIdle()

        assertEquals(1, host.submits.size)
        assertEquals(1, host.settles.size)

        job.cancel()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertFalse("a cancelled driver must not hand over", host.done)
        assertEquals(1, host.submits.size)
    }
}
