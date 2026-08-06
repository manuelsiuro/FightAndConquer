package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.campaign.FailCondition
import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.TestLevels
import com.msa.fightandconquer.core.campaign.UnitPlacement
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trip for the stored custom-map artifact, following `CampaignCodecTest`: the
 * polymorphic vocabulary inside ([Objective], `SeatDef`, `UnitPlacement`) is the
 * campaign's own and already discriminator-safe; this pins the envelope around it.
 */
class MapCodecTest {

    private val def = CustomMapDef(
        id = "a1b2c3",
        name = "Twin Passes",
        author = "someone",
        createdAt = 1_700_000_000_000,
        modifiedAt = 1_700_000_001_000,
        level = TestLevels.strip(
            objectives = listOf(
                Objective.CaptureHexes(listOf(hex(3), hex(4))),
                Objective.BuildCount(BuildingType.FARM, 2),
                Objective.EliminatePlayer(PlayerId(1)),
            ),
            failures = listOf(FailCondition.TurnLimit(30)),
        ).copy(
            startingTreasury = listOf(100, 250),
            startingUnits = listOf(UnitPlacement(seat = 0, hex = hex(1), type = UnitType.ARCHER, tier = 1)),
        ),
    )

    @Test
    fun `round trips exactly`() {
        assertEquals(def, MapCodec.decode(MapCodec.encode(def)))
    }

    @Test
    fun `unknown keys from a newer build are tolerated`() {
        val withExtra = MapCodec.encode(def).removeSuffix("}") +
            ""","futureField":{"nested":[1,2,3]}}"""
        assertEquals(def, MapCodec.decode(withExtra))
    }

    @Test
    fun `defaults are written out so tomorrow's rules cannot leak in`() {
        val text = MapCodec.encode(def)
        // encodeDefaults: the embedded RuleConstants snapshot must be self-describing.
        assert(text.contains("\"maxTier\""))
        assert(text.contains("\"version\":1"))
    }
}
