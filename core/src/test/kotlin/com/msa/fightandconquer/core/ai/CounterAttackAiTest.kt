package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CounterAttackAiTest {

    /**
     * P0 row at r=-1 (capital far left), P1 on the right end with a tier-2
     * raider at the shared border, neutral ground dangling below:
     *
     *   r=-1:  P0 (0..4)cap@0 | P1 (5,6)cap@6, tier-2 at (5,-1)
     *   r=0:   neutral (2,0) (3,0)
     */
    private fun raidedBorder() = TestStates.custom(
        owners = mapOf(
            hex(0, -1) to 0, hex(1, -1) to 0, hex(2, -1) to 0, hex(3, -1) to 0, hex(4, -1) to 0,
            hex(5, -1) to 1, hex(6, -1) to 1,
            hex(2, 0) to null, hex(3, 0) to null,
        ),
        capital0 = hex(0, -1),
        capital1 = hex(6, -1),
    ).withUnit(owner = 1, tier = 2, at = hex(5, -1))

    @Test
    fun `a raider at the fence lowers the position score`() {
        val threatened = raidedBorder()
        val quiet = threatened.copy(
            units = emptyMap(),
            tiles = threatened.tiles.mapValues { (_, t) -> t.copy(unit = null) },
        )
        val visible = null
        val with = Evaluator.score(threatened, PlayerId(0), Difficulty.NORMAL, visible)
        val without = Evaluator.score(quiet, PlayerId(0), Difficulty.NORMAL, visible)
        assertTrue(
            "an enemy soldier beside my land must read as pressure (with=$with without=$without)",
            without > with,
        )
    }

    @Test
    fun `killing the raider beats expanding politely into neutral ground`() {
        // A tier-3 by the border can retake the raider's hex or grab a neutral one.
        val state = raidedBorder()
            .withUnit(owner = 0, tier = 3, at = hex(4, -1))
            .let { s -> s.copy(players = s.players.map { it.copy(treasury = 0) }) }
        val action = AiPlayer(Difficulty.NORMAL).chooseAction(state)
        assertTrue("expected a unit move, got $action", action is GameAction.MoveUnit)
        assertEquals(
            "the counter-attack must out-score the quiet neutral capture",
            hex(5, -1),
            (action as GameAction.MoveUnit).to,
        )
    }
}
