package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Assert.assertTrue
import org.junit.Test

/** The naval invasion ladder must make water-separated AI games terminate. */
class NavalAiTest {

    /**
     * Two 8-hex islands with a 3-wide channel. Both seats are AI; without boats
     * neither can ever reach the other, so termination proves the invasion works.
     */
    private fun twoIslandState(difficulty: Difficulty): GameState {
        val islandA = listOf(hex(0), hex(1), hex(2), hex(0, 1), hex(1, 1), hex(2, 1), hex(1, -1), hex(2, -1))
        val islandB = listOf(hex(6), hex(7), hex(8), hex(6, 1), hex(7, 1), hex(8, 1), hex(7, -1), hex(8, -1))
        val owners = HashMap<Hex, Int?>()
        islandA.forEach { owners[it] = 0 }
        islandB.forEach { owners[it] = 1 }
        // Like generated maps, every island is ringed by open water (2-hex fringe).
        val land = islandA + islandB
        val sea = buildSet {
            for (hex in land) {
                for (n in com.msa.fightandconquer.core.hex.HexMath.range(hex, 2)) {
                    if (n !in land) add(n)
                }
            }
        }
        val base = com.msa.fightandconquer.core.TestStates.custom(
            owners = owners,
            capital0 = hex(1),
            capital1 = hex(7),
            treasury = 60,
            seed = 7L,
        ).withSea(sea.toList())
        return base.copy(
            players = base.players.map { it.copy(kind = PlayerKind.Ai(difficulty)) },
        )
    }

    @Test
    fun `AI crosses the water and finishes a two-island game`() {
        var state = twoIslandState(Difficulty.NORMAL)
        val ai = AiPlayer(Difficulty.NORMAL)
        var rounds = 0
        while (state.phase is GamePhase.Playing && rounds < 2000) {
            val action = ai.chooseAction(state)
            state = Reducer.reduce(state, action).state
            assertInvariants(state)
            rounds++
        }
        assertTrue(
            "two-island AI game must end in conquest (stopped at turn ${state.turnNumber})",
            state.phase is GamePhase.Finished,
        )
    }

    @Test
    fun `the invasion ladder acts even for the easy AI`() {
        // Easy mirrors may seesaw forever (as on land — no Easy-vs-Easy
        // termination gate exists); what matters is that Easy still runs the
        // ladder: builds a port, ferries a marine and actually lands it.
        var state = twoIslandState(Difficulty.EASY)
        val ai = AiPlayer(Difficulty.EASY)
        var landed = false
        var steps = 0
        while (state.phase is GamePhase.Playing && steps < 400 && !landed) {
            val action = ai.chooseAction(state)
            if (action is GameAction.Disembark) landed = true
            state = Reducer.reduce(state, action).state
            steps++
        }
        assertTrue("easy AI never landed an invasion (turn ${state.turnNumber})", landed)
    }
}
