package com.msa.fightandconquer.render.mesh

import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.Building
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Ties the render-side lit-variant table to the engine's beacon-capable set:
 * extending `Rules.beaconRadiusOf` to a new building without shipping its lit
 * art variant must fail here, not silently render the unlit piece.
 */
class PieceKindMappingTest {

    @Test
    fun `every beacon-capable building has a lit art variant and no other does`() {
        for (building in Building.entries) {
            val unlit = PieceMeshes.buildingKind(building)
            val lit = PieceMeshes.buildingKind(building, lit = true)
            if (Rules.beaconRadiusOf(building) != null) {
                assertNotEquals("lit variant missing for $building", unlit, lit)
                assertEquals(
                    "litVariantOf and buildingKind agree for $building",
                    lit,
                    PieceMeshes.litVariantOf(unlit),
                )
            } else {
                assertEquals("phantom lit variant for $building", unlit, lit)
            }
        }
    }
}
