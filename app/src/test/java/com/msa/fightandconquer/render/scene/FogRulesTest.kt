package com.msa.fightandconquer.render.scene

import com.msa.fightandconquer.core.hex.Hex
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FogRulesTest {

    private val a = Hex.of(0, 0)
    private val b = Hex.of(1, 0)
    private val deep = Hex.of(5, 0)

    @Test
    fun `fog off hides nothing`() {
        assertFalse(FogRules.hexHidden(null, deep))
        assertFalse(FogRules.segmentHidden(null, a, deep))
        assertFalse(FogRules.auraSourceHidden(null, deep))
    }

    @Test
    fun `a segment renders only when both ends are visible`() {
        val visible = setOf(a, b)
        assertFalse("fully visible step animates", FogRules.segmentHidden(visible, a, b))
        assertTrue("stepping into fog hides", FogRules.segmentHidden(visible, a, deep))
        assertTrue("stepping out of fog hides", FogRules.segmentHidden(visible, deep, b))
        assertTrue("deep fog march hides", FogRules.segmentHidden(visible, deep, Hex.of(6, 0)))
    }

    @Test
    fun `aura sources inside the fog are muted`() {
        val visible = setOf(a)
        assertFalse(FogRules.auraSourceHidden(visible, a))
        assertTrue("fogged tower/archer paints no ring", FogRules.auraSourceHidden(visible, deep))
    }
}
