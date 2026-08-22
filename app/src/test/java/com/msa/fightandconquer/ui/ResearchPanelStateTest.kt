package com.msa.fightandconquer.ui

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.ActiveResearch
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.ResearchState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tech
import com.msa.fightandconquer.core.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure state -> research-panel mapping ([buildResearchPanel]). */
class ResearchPanelStateTest {

    private fun state(
        rules: RuleConstants = RuleConstants(researchEnabled = true),
        research: ResearchState = ResearchState(),
        treasury: Int = 100,
        universities: Int = 1,
        starvingUniversities: Int = 0,
    ): GameState {
        val tiles = HashMap<Hex, Tile>()
        val me = PlayerId(0)
        tiles[Hex.of(0, 0)] = Tile(owner = me, building = Building.CAPITAL)
        var q = 1
        repeat(universities) {
            tiles[Hex.of(q++, 0)] = Tile(owner = me, building = Building.UNIVERSITY)
        }
        repeat(starvingUniversities) {
            tiles[Hex.of(q++, 0)] = Tile(owner = me, building = Building.UNIVERSITY, starving = true)
        }
        return GameState(
            config = GameConfig(seed = 1L, rules = rules),
            tiles = tiles,
            units = emptyMap(),
            players = listOf(
                PlayerState(me, PlayerKind.Human, treasury, Hex.of(0, 0), research = research),
            ),
            currentPlayer = me,
            rngState = 1L,
        )
    }

    private fun ResearchPanelState.node(tech: Tech): TechNodeUi =
        branches.flatMap { it.nodes }.single { it.tech == tech }

    @Test
    fun `research off yields no panel`() {
        assertNull(buildResearchPanel(state(rules = RuleConstants()), PlayerId(0)))
    }

    @Test
    fun `sail branch is absent without naval rules`() {
        val landlocked = buildResearchPanel(
            state(rules = RuleConstants(researchEnabled = true, navalEnabled = false)),
            PlayerId(0),
        )!!
        assertEquals(3, landlocked.branches.size)
        assertEquals(4, buildResearchPanel(state(), PlayerId(0))!!.branches.size)
    }

    @Test
    fun `fresh player - tier ones available, deeper tiers locked`() {
        val panel = buildResearchPanel(state(), PlayerId(0))!!
        assertEquals(TechUiStatus.AVAILABLE, panel.node(Tech.SMITHING).status)
        assertEquals(TechUiStatus.AVAILABLE, panel.node(Tech.COINAGE).status)
        assertEquals(TechUiStatus.LOCKED, panel.node(Tech.BANKING).status)
        assertEquals(TechUiStatus.LOCKED, panel.node(Tech.TREASURY).status)
        assertNull(panel.active)
    }

    @Test
    fun `an active research marks its node and locks the rest`() {
        val panel = buildResearchPanel(
            state(research = ResearchState(active = ActiveResearch(Tech.COINAGE, 2))),
            PlayerId(0),
        )!!
        assertEquals(TechUiStatus.IN_PROGRESS, panel.node(Tech.COINAGE).status)
        assertEquals(2, panel.node(Tech.COINAGE).progress)
        assertEquals(TechUiStatus.LOCKED, panel.node(Tech.SMITHING).status)
        assertEquals(Tech.COINAGE, panel.active?.tech)
    }

    @Test
    fun `completed techs show done and open the next tier`() {
        val panel = buildResearchPanel(
            state(research = ResearchState.of(listOf(Tech.COINAGE))),
            PlayerId(0),
        )!!
        assertEquals(TechUiStatus.DONE, panel.node(Tech.COINAGE).status)
        assertEquals(TechUiStatus.AVAILABLE, panel.node(Tech.BANKING).status)
    }

    @Test
    fun `affordability follows the treasury`() {
        val rules = RuleConstants(researchEnabled = true)
        val poor = buildResearchPanel(state(treasury = rules.techCostByTier[0] - 1), PlayerId(0))!!
        assertTrue(!poor.node(Tech.COINAGE).affordable)
        assertTrue(buildResearchPanel(state(), PlayerId(0))!!.node(Tech.COINAGE).affordable)
    }

    @Test
    fun `only working universities count toward the rate`() {
        val panel = buildResearchPanel(
            state(universities = 2, starvingUniversities = 1),
            PlayerId(0),
        )!!
        assertEquals(2, panel.universityCount)
        assertEquals(2, panel.ratePerTurn)
    }
}
