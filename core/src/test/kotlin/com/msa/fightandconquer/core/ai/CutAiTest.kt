package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slicing must be EXECUTED, not merely detected: offered a cheap end-nibble and
 * a pricier isthmus capture that starves a tile, the argmax takes the cut.
 */
class CutAiTest {

    @Test
    fun `the AI funds the isthmus cut over the cheap end-nibble`() {
        // P0: a 19-hex blob (income high enough that upkeep deltas sit past the
        // income curve's knee — a small realm would rightly pinch pennies).
        // P1: the chain (3,-1)cap - (3,0) - (3,1) along P0's eastern border.
        // (3,0) defends at 1 (capital next door): a tier-2 cut that starves
        // (3,1). (3,1) defends at 0: a tier-1 nibble. The cut must win.
        val owners = HashMap<Hex, Int?>()
        HexMath.range(Hex.of(0, 0), 2).forEach { owners[it] = 0 }
        owners[hex(3, -1)] = 1
        owners[hex(3, 0)] = 1
        owners[hex(3, 1)] = 1
        // A standing barracks keeps this about the cut, not about founding halls.
        val state = TestStates.custom(
            owners,
            capital0 = Hex.of(-2, 0),
            capital1 = hex(3, -1),
            treasury = 40,
        ).let {
            TestStates.run { it.withBuilding(com.msa.fightandconquer.core.model.Building.BARRACKS, Hex.of(0, 0)) }
        }
        val action = AiPlayer(Difficulty.NORMAL).chooseAction(state)
        assertTrue("expected a soldier purchase, got $action", action is GameAction.BuyUnit)
        assertEquals("the cut hex must outbid the nibble", hex(3, 0), (action as GameAction.BuyUnit).at)
        assertTrue("a cut needs a breaker, not a peasant", action.tier >= 2)
    }
}
