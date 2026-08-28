package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.PlayerId
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositionAiTest {

    /** A long realm, a walled border, and a rear tier-2 with nothing to capture. */
    private fun laggardRealm() = strip(16, 0..11, 12..15)
        .withUnit(owner = 0, tier = 2, at = hex(1))
        // The enemy border hex out-defends everything affordable: a tower at the
        // enemy line and an empty purse leave the argmax with no capture and no buy.
        .withTreasury(0, 0)
        .withTreasury(1, 0)
        .let { s ->
            s.copy(
                tiles = s.tiles + (
                    hex(12) to s.tiles.getValue(hex(12))
                        .copy(building = com.msa.fightandconquer.core.model.Building.TOWER)
                    ),
            )
        }

    @Test
    fun `an idle rear soldier marches toward the front instead of standing forever`() {
        val state = laggardRealm()
        val action = AiPlayer(Difficulty.NORMAL).chooseAction(state)
        assertTrue("expected a march, got $action", action is GameAction.MoveUnit)
        val move = action as GameAction.MoveUnit
        val context = Strategy.assess(state, PlayerId(0), AiProfile.NEUTRAL, null)
        val before = context.distanceToFront.getValue(hex(1))
        val after = context.distanceToFront.getValue(move.to)
        assertTrue("march must strictly close on the front ($before -> $after)", after < before)
    }

    @Test
    fun `a repositioning turn still terminates`() {
        var state = laggardRealm()
        val ai = AiPlayer(Difficulty.NORMAL)
        var actions = 0
        while (true) {
            val action = ai.chooseAction(state)
            state = Reducer.reduce(state, action).state
            actions++
            if (action == GameAction.EndTurn || state.phase !is GamePhase.Playing) break
            assertTrue("turn ran past the action cap", actions < AiPlayer.MAX_ACTIONS_PER_TURN)
        }
        assertTrue("the turn must end on its own", actions < AiPlayer.MAX_ACTIONS_PER_TURN)
    }
}
