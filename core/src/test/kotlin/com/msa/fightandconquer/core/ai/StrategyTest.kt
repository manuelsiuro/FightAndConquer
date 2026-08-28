package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.PlayerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyTest {

    /**
     * P0 row at r=-1 (capital far left), P1 chain at r=0 with its capital on the
     * left end: (3,0) is the isthmus — taking it starves (4,0).
     *
     *   P0:  (0,-1) (1,-1) (2,-1)cap (3,-1)
     *   P1:          (2,0)cap (3,0) (4,0)
     */
    private fun isthmus() = TestStates.custom(
        owners = mapOf(
            hex(0, -1) to 0, hex(1, -1) to 0, hex(2, -1) to 0, hex(3, -1) to 0,
            hex(2, 0) to 1, hex(3, 0) to 1, hex(4, 0) to 1,
        ),
        capital0 = hex(0, -1),
        capital1 = hex(2, 0),
    )

    private fun assess(state: com.msa.fightandconquer.core.model.GameState, visible: Set<Hex>? = null) =
        Strategy.assess(state, PlayerId(0), AiProfile.NEUTRAL, visible)

    @Test
    fun `the isthmus hex carries its cut value and the capital is never a cut target`() {
        val context = assess(isthmus())
        assertEquals(
            "capturing (3,0) starves exactly (4,0); (2,0) is the capital, priced elsewhere",
            mapOf(hex(3, 0) to 1),
            context.cutValues,
        )
    }

    @Test
    fun `front hexes and the distance field march inward from the enemy border`() {
        val context = assess(isthmus())
        assertEquals(setOf(hex(2, -1), hex(3, -1)), context.frontHexes)
        assertEquals(0, context.distanceToFront[hex(3, -1)])
        assertEquals(1, context.distanceToFront[hex(1, -1)])
        assertEquals(2, context.distanceToFront[hex(0, -1)])
    }

    @Test
    fun `a visible enemy soldier at the fence is a threat and opens defense gaps`() {
        val state = isthmus().withUnit(owner = 1, tier = 2, at = hex(3, 0))
        val context = assess(state)
        assertEquals(1, context.threatUnits.size)
        // (3,-1) is a plain border hex: tier-2 strength 2 beats its defense.
        assertTrue(hex(3, -1) in context.defenseGaps)
    }

    @Test
    fun `fog without the enemy capital in sight yields no cut analysis`() {
        val state = isthmus()
        // Everything near my land except the enemy capital (2,0).
        val visible = state.tiles.keys.filterNot { it == hex(2, 0) }.toSet()
        assertTrue(assess(state, visible).cutValues.isEmpty())
        assertEquals(mapOf(hex(3, 0) to 1), assess(state, null).cutValues)
    }

    @Test
    fun `the stronger opponent is the focus`() {
        // P1 fields a big garrison — power comes from land + units.
        val state = isthmus().withUnit(owner = 1, tier = 3, at = hex(4, 0))
        assertEquals(PlayerId(1), assess(state).focusEnemy)
    }
}
