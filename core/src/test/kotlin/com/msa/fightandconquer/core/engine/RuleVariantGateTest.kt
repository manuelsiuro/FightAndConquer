package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule-variant gates campaign levels play with: [RuleConstants.navalEnabled],
 * [RuleConstants.disabledBuildings] and a lowered [RuleConstants.maxTier]. Skirmish
 * never flips these, so their rejection paths ride only on campaign playthroughs
 * unless pinned here.
 */
class RuleVariantGateTest {

    private fun rejected(result: LegalityResult): RejectionReason =
        (result as LegalityResult.Rejected).reason

    // ----- navalEnabled = false -----

    private fun landlocked() = strip(
        9, 0..2, 6..8,
        rules = RuleConstants(navalEnabled = false),
    ).withSea(listOf(hex(3), hex(4), hex(5)))

    @Test
    fun `naval disabled rejects every boat purchase`() {
        // A pre-existing port (authored map) must not re-open the shipyard.
        val engine = GameEngine(landlocked().withBuilding(Building.PORT, hex(2)))
        for (type in listOf(UnitType.TRANSPORT, UnitType.WARSHIP, UnitType.FISHING_BOAT)) {
            assertEquals(
                RejectionReason.NAVAL_DISABLED,
                rejected(engine.submit(GameAction.BuyUnit(1, hex(3), type))),
            )
        }
    }

    @Test
    fun `naval disabled rejects port fishery and bridge builds`() {
        val engine = GameEngine(landlocked())
        assertEquals(
            RejectionReason.NAVAL_DISABLED,
            rejected(engine.submit(GameAction.BuyBuilding(BuildingType.PORT, hex(2)))),
        )
        assertEquals(
            RejectionReason.NAVAL_DISABLED,
            rejected(engine.submit(GameAction.BuyBuilding(BuildingType.FISHERY, hex(2)))),
        )
        assertEquals(
            RejectionReason.NAVAL_DISABLED,
            rejected(engine.submit(GameAction.BuyBuilding(BuildingType.BRIDGE, hex(3)))),
        )
    }

    @Test
    fun `naval disabled leaves an authored boat without any reach`() {
        // A boat can only exist here if the map authored one; it must not sail.
        val s = landlocked().withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
        assertEquals(ReachResult.EMPTY, Rules.reachable(s, s.unitIdAt(hex(3))))
    }

    @Test
    fun `naval disabled offers no embark targets to land units`() {
        val s = landlocked()
            .withUnit(owner = 0, tier = 1, at = hex(2))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.TRANSPORT)
        assertTrue(Rules.reachable(s, s.unitIdAt(hex(2))).embarkTargets.isEmpty())
    }

    // ----- disabledBuildings -----

    @Test
    fun `disabled building rejects the build but not demolition of an authored one`() {
        val rules = RuleConstants(disabledBuildings = setOf(BuildingType.TOWER))
        val engine = GameEngine(
            strip(9, 0..2, 6..8, rules = rules).withBuilding(Building.TOWER, hex(2)),
        )
        assertEquals(
            RejectionReason.BUILDING_NOT_AVAILABLE,
            rejected(engine.submit(GameAction.BuyBuilding(BuildingType.TOWER, hex(1)))),
        )
        // The authored tower is still the player's to raze, at the normal refund.
        val before = engine.state.value.player(engine.state.value.currentPlayer).treasury
        assertTrue(engine.submit(GameAction.DemolishBuilding(hex(2))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(null, after.tiles.getValue(hex(2)).building)
        assertEquals(
            before + rules.towerCost * rules.demolishRefundPercent / 100,
            after.player(after.currentPlayer).treasury,
        )
        assertInvariants(after)
    }

    // ----- lowered maxTier -----

    private val capped = strip(9, 0..3, 6..8, rules = RuleConstants(maxTier = 2))

    @Test
    fun `lowered maxTier rejects buying above the cap`() {
        assertEquals(
            RejectionReason.INVALID_TIER,
            rejected(GameEngine(capped).submit(GameAction.BuyUnit(3, hex(1)))),
        )
    }

    @Test
    fun `lowered maxTier rejects merging at the cap`() {
        val s = capped
            .withUnit(owner = 0, tier = 2, at = hex(1))
            .withUnit(owner = 0, tier = 2, at = hex(2))
        assertEquals(
            RejectionReason.ALREADY_MAX_TIER,
            rejected(GameEngine(s).submit(GameAction.MergeUnits(s.unitIdAt(hex(1)), s.unitIdAt(hex(2))))),
        )
    }

    @Test
    fun `lowered maxTier removes cap-tier merge targets from reach`() {
        val s = capped
            .withUnit(owner = 0, tier = 2, at = hex(1))
            .withUnit(owner = 0, tier = 2, at = hex(2))
        assertTrue(Rules.reachable(s, s.unitIdAt(hex(1))).mergeTargets.isEmpty())
    }

    @Test
    fun `lowered maxTier rejects buy-merge onto a cap-tier occupant`() {
        val s = capped.withUnit(owner = 0, tier = 2, at = hex(1))
        assertEquals(
            RejectionReason.HEX_OCCUPIED_INCOMPATIBLE,
            rejected(GameEngine(s).submit(GameAction.BuyUnit(2, hex(1)))),
        )
    }
}
