package com.msa.fightandconquer.ui.game

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.GameUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tile
import com.msa.fightandconquer.core.model.UnitId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure tap-routing rule for building hexes ([BuildingTap.targetOf]) — the sheet a
 * building opens instead of an info card.
 */
class BuildingTapTest {

    private val me = PlayerId(0)
    private val foe = PlayerId(1)

    private val capital = Hex.of(0, 0)
    private val university = Hex.of(1, 0)
    private val farm = Hex.of(2, 0)
    private val plain = Hex.of(3, 0)
    private val enemyCapital = Hex.of(5, 0)
    private val enemyUniversity = Hex.of(6, 0)
    private val offMap = Hex.of(9, 9)

    /**
     * A two-seat board (human + passive AI) with one of every tapped tile: own capital,
     * own university, own farm, own empty hex and the enemy's pair.
     */
    private fun state(
        rules: RuleConstants = RuleConstants(researchEnabled = true),
        universityStarving: Boolean = false,
        unitOnCapital: Boolean = false,
    ): GameState {
        val tiles = HashMap<Hex, Tile>()
        tiles[capital] = Tile(owner = me, building = Building.CAPITAL)
        tiles[university] =
            Tile(owner = me, building = Building.UNIVERSITY, starving = universityStarving)
        tiles[farm] = Tile(owner = me, building = Building.FARM)
        tiles[plain] = Tile(owner = me)
        tiles[enemyCapital] = Tile(owner = foe, building = Building.CAPITAL)
        tiles[enemyUniversity] = Tile(owner = foe, building = Building.UNIVERSITY)

        val units = HashMap<UnitId, GameUnit>()
        if (unitOnCapital) {
            val id = UnitId(1)
            units[id] = GameUnit(id = id, owner = me, tier = 1, hex = capital)
            tiles[capital] = tiles.getValue(capital).copy(unit = id)
        }

        return GameState(
            config = GameConfig(seed = 1L, rules = rules),
            tiles = tiles,
            units = units,
            players = listOf(
                PlayerState(me, PlayerKind.Human, 100, capital),
                PlayerState(foe, PlayerKind.Ai(Difficulty.PASSIVE), 100, enemyCapital),
            ),
            currentPlayer = me,
            rngState = 1L,
            nextUnitId = 2,
        )
    }

    @Test
    fun `an own capital opens the economy sheet`() {
        assertEquals(
            BuildingTapTarget.ECONOMY,
            BuildingTap.targetOf(state(), capital, me),
        )
    }

    @Test
    fun `an own university opens the research sheet`() {
        assertEquals(
            BuildingTapTarget.RESEARCH,
            BuildingTap.targetOf(state(), university, me),
        )
    }

    @Test
    fun `a university opens nothing when research is off`() {
        val off = state(rules = RuleConstants(researchEnabled = false))
        assertNull(BuildingTap.targetOf(off, university, me))
        // The capital's sheet does not depend on the research rule.
        assertEquals(BuildingTapTarget.ECONOMY, BuildingTap.targetOf(off, capital, me))
    }

    @Test
    fun `a starving university still opens the research sheet`() {
        assertEquals(
            BuildingTapTarget.RESEARCH,
            BuildingTap.targetOf(state(universityStarving = true), university, me),
        )
    }

    @Test
    fun `an enemy capital opens nothing`() {
        assertNull(BuildingTap.targetOf(state(), enemyCapital, me))
    }

    @Test
    fun `an enemy university opens nothing`() {
        assertNull(BuildingTap.targetOf(state(), enemyUniversity, me))
    }

    @Test
    fun `a unit standing on an own capital routes first`() {
        assertNull(BuildingTap.targetOf(state(unitOnCapital = true), capital, me))
    }

    @Test
    fun `an own farm opens nothing`() {
        assertNull(BuildingTap.targetOf(state(), farm, me))
    }

    @Test
    fun `an own empty tile opens nothing`() {
        assertNull(BuildingTap.targetOf(state(), plain, me))
    }

    @Test
    fun `a hex off the map opens nothing`() {
        assertNull(BuildingTap.targetOf(state(), offMap, me))
    }

    @Test
    fun `the seat decides - another seat's buildings open nothing`() {
        val s = state()
        assertNull(BuildingTap.targetOf(s, capital, foe))
        assertNull(BuildingTap.targetOf(s, university, foe))
        // ... and the enemy's own pair answers for its own seat.
        assertEquals(BuildingTapTarget.ECONOMY, BuildingTap.targetOf(s, enemyCapital, foe))
        assertEquals(BuildingTapTarget.RESEARCH, BuildingTap.targetOf(s, enemyUniversity, foe))
    }
}
