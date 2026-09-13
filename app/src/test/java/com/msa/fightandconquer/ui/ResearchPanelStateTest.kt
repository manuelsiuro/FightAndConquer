package com.msa.fightandconquer.ui

import com.msa.fightandconquer.R
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.ActiveResearch
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Civilization
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
        civ: Civilization = Civilization.KINGDOM,
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
                PlayerState(
                    me,
                    PlayerKind.Human,
                    treasury,
                    Hex.of(0, 0),
                    civ = civ,
                    research = research,
                ),
            ),
            currentPlayer = me,
            rngState = 1L,
        )
    }

    private fun ResearchPanelState.node(tech: Tech): TechNodeUi =
        branches.flatMap { it.nodes }.single { it.tech == tech }

    @Test
    fun `research off yields no panel`() {
        assertNull(
            buildResearchPanel(state(rules = RuleConstants(researchEnabled = false)), PlayerId(0)),
        )
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
    fun `an active research marks its node and stalls the rest`() {
        val panel = buildResearchPanel(
            state(research = ResearchState(active = ActiveResearch(Tech.COINAGE, 2))),
            PlayerId(0),
        )!!
        assertEquals(TechUiStatus.IN_PROGRESS, panel.node(Tech.COINAGE).status)
        assertEquals(2, panel.node(Tech.COINAGE).progress)
        // Reachable-but-slot-taken is BUSY; a missing prerequisite stays LOCKED —
        // the sheet dims the two differently.
        assertEquals(TechUiStatus.BUSY, panel.node(Tech.SMITHING).status)
        assertEquals(TechUiStatus.LOCKED, panel.node(Tech.BANKING).status)
        assertEquals(Tech.COINAGE, panel.active?.tech)
    }

    @Test
    fun `busy never outranks a missing prerequisite`() {
        val panel = buildResearchPanel(
            state(research = ResearchState(active = ActiveResearch(Tech.SMITHING, 1))),
            PlayerId(0),
        )!!
        assertEquals(TechUiStatus.IN_PROGRESS, panel.node(Tech.SMITHING).status)
        assertEquals(TechUiStatus.BUSY, panel.node(Tech.COINAGE).status)
        assertEquals(TechUiStatus.LOCKED, panel.node(Tech.ARMORY).status)
        assertEquals(TechUiStatus.LOCKED, panel.node(Tech.BANKING).status)
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

    @Test
    fun `every technology carries its own glyph`() {
        val panel = buildResearchPanel(state(), PlayerId(0))!!
        val nodes = panel.branches.flatMap { it.nodes }
        assertEquals(Tech.entries.size, nodes.size)
        assertTrue(nodes.none { it.iconRes == 0 })
        assertEquals(nodes.size, nodes.map { it.iconRes }.toSet().size)
        for (tech in Tech.entries) {
            assertEquals(techIconRes(tech), panel.node(tech).iconRes)
        }
    }

    @Test
    fun `techIconRes maps each technology to its ic_tech drawable`() {
        assertEquals(R.drawable.ic_tech_smithing, techIconRes(Tech.SMITHING))
        assertEquals(R.drawable.ic_tech_armory, techIconRes(Tech.ARMORY))
        assertEquals(R.drawable.ic_tech_siegecraft, techIconRes(Tech.SIEGECRAFT))
        assertEquals(R.drawable.ic_tech_coinage, techIconRes(Tech.COINAGE))
        assertEquals(R.drawable.ic_tech_banking, techIconRes(Tech.BANKING))
        assertEquals(R.drawable.ic_tech_treasury, techIconRes(Tech.TREASURY))
        assertEquals(R.drawable.ic_tech_masonry, techIconRes(Tech.MASONRY))
        assertEquals(R.drawable.ic_tech_engineering, techIconRes(Tech.ENGINEERING))
        assertEquals(R.drawable.ic_tech_bastions, techIconRes(Tech.BASTIONS))
        assertEquals(R.drawable.ic_tech_navigation, techIconRes(Tech.NAVIGATION))
        assertEquals(R.drawable.ic_tech_shipwrights, techIconRes(Tech.SHIPWRIGHTS))
        assertEquals(R.drawable.ic_tech_admiralty, techIconRes(Tech.ADMIRALTY))
    }

    @Test
    fun `the active research carries the glyph`() {
        val panel = buildResearchPanel(
            state(research = ResearchState(active = ActiveResearch(Tech.ARMORY, 1))),
            PlayerId(0),
        )!!
        assertEquals(R.drawable.ic_tech_armory, panel.active!!.iconRes)
    }

    @Test
    fun `the panel carries the seat's civilization`() {
        assertEquals(Civilization.KINGDOM, buildResearchPanel(state(), PlayerId(0))!!.civ)
        assertEquals(
            Civilization.SHOGUNATE,
            buildResearchPanel(state(civ = Civilization.SHOGUNATE), PlayerId(0))!!.civ,
        )
    }
}
