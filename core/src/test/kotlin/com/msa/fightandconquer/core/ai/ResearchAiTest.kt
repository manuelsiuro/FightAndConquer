package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ResearchPolicy micro-fixtures: when the AI founds a University, what it
 * researches first, and when it refuses to spend. Full-game usage tripwires
 * ride the flag-flip change with the re-baseline.
 */
class ResearchAiTest {

    private val rules = RuleConstants(researchEnabled = true)

    /** A 30-strip where P0 holds 13 hexes — past every difficulty's size gate. */
    private fun wide(): GameState = strip(30, 0..12, 27..29, rules = rules)

    @Test
    fun `a quiet prosperous front founds a university`() {
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.HARD)) {
            val action = ResearchPolicy.action(wide(), difficulty)
            assertTrue(
                "$difficulty founds",
                action is GameAction.BuyBuilding && action.type == BuildingType.UNIVERSITY,
            )
        }
    }

    @Test
    fun `the first tech is offense for hard and economy for normal`() {
        val ready = wide().withBuilding(Building.UNIVERSITY, hex(1))
        assertEquals(
            GameAction.StartResearch(Tech.SMITHING),
            ResearchPolicy.action(ready, Difficulty.HARD),
        )
        assertEquals(
            GameAction.StartResearch(Tech.COINAGE),
            ResearchPolicy.action(ready, Difficulty.NORMAL),
        )
    }

    @Test
    fun `a threatened capital vetoes all research spending`() {
        // A tier-4 raider within striking range of the throne: scholarship waits.
        val threatened = wide().withUnit(1, 4, hex(4))
        assertNull(ResearchPolicy.action(threatened, Difficulty.HARD))
        assertNull(ResearchPolicy.action(threatened, Difficulty.NORMAL))
    }

    @Test
    fun `war reserves keep research from starving the front`() {
        // P0 and P1 share a border: NORMAL holds 40 coins back.
        val frontline = strip(9, 0..3, 4..8, rules = rules)
            .withBuilding(Building.UNIVERSITY, hex(1))
        val cost = rules.techCostByTier[0]
        assertNull(
            ResearchPolicy.action(frontline.withTreasury(0, cost + 39), Difficulty.NORMAL),
        )
        assertEquals(
            GameAction.StartResearch(Tech.COINAGE),
            ResearchPolicy.action(frontline.withTreasury(0, cost + 40), Difficulty.NORMAL),
        )
    }

    @Test
    fun `easy never researches on a land route`() {
        assertNull(ResearchPolicy.action(wide(), Difficulty.EASY))
        assertNull(
            ResearchPolicy.action(wide().withBuilding(Building.UNIVERSITY, hex(1)), Difficulty.EASY),
        )
    }

    @Test
    fun `easy researches exactly navigation when sea-locked`() {
        // Two islands: P0's landmass holds no enemy — the sea is the only road.
        val islands = strip(9, 0..2, 6..8, rules = rules)
            .withSeaGap()
        val founds = ResearchPolicy.action(islands, Difficulty.EASY)
        assertTrue(founds is GameAction.BuyBuilding && founds.type == BuildingType.UNIVERSITY)
        assertEquals(
            GameAction.StartResearch(Tech.NAVIGATION),
            ResearchPolicy.action(islands.withBuilding(Building.UNIVERSITY, hex(1)), Difficulty.EASY),
        )
    }

    @Test
    fun `sea-locked hard puts navigation before everything`() {
        val islands = strip(9, 0..2, 6..8, rules = rules)
            .withSeaGap()
            .withBuilding(Building.UNIVERSITY, hex(1))
        assertEquals(
            GameAction.StartResearch(Tech.NAVIGATION),
            ResearchPolicy.action(islands, Difficulty.HARD),
        )
    }

    @Test
    fun `research off returns nothing`() {
        val off = strip(30, 0..12, 27..29, rules = RuleConstants(researchEnabled = false))
            .withBuilding(Building.UNIVERSITY, hex(1))
        assertNull(ResearchPolicy.action(off, Difficulty.HARD))
    }

    private fun GameState.withSeaGap(): GameState = withSea(listOf(hex(3), hex(4), hex(5)))

    // ----- full-game tripwires (the AiExpansionTest usage idiom, research edition) -----

    private fun playOut(
        start: GameState,
        difficulty: Difficulty,
        maxRounds: Int,
        onEvent: (com.msa.fightandconquer.core.engine.GameEvent) -> Unit,
    ): GameState {
        var state = start
        val ais = state.players.map { AiPlayer(difficulty) }
        while (state.phase is com.msa.fightandconquer.core.model.GamePhase.Playing &&
            state.turnNumber < maxRounds
        ) {
            val ai = ais[state.currentPlayer.value]
            var actions = 0
            while (true) {
                val action = ai.chooseAction(state)
                val result = com.msa.fightandconquer.core.engine.Reducer.reduce(state, action)
                result.events.forEach(onEvent)
                state = result.state
                actions++
                if (action == GameAction.EndTurn ||
                    state.phase !is com.msa.fightandconquer.core.model.GamePhase.Playing
                ) {
                    break
                }
                if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                    state = com.msa.fightandconquer.core.engine.Reducer.reduce(state, GameAction.EndTurn).state
                    break
                }
            }
        }
        return state
    }

    @Test
    fun `across full games normal AIs found universities and complete research`() {
        val built = HashSet<Building>()
        val completed = HashSet<Tech>()
        for (seed in 1L..6L) {
            val playerCount = 2 + (seed % 3).toInt()
            val params = com.msa.fightandconquer.core.map.MapParams(
                seed = seed,
                size = com.msa.fightandconquer.core.map.MapSize.SMALL,
                playerCount = playerCount,
                shape = com.msa.fightandconquer.core.map.MapShape.entries[(seed % 3).toInt()],
            )
            val start = com.msa.fightandconquer.core.map.MapGenerator.generate(params).newGame(
                gameSeed = seed * 31 + 7,
                kinds = List(playerCount) { com.msa.fightandconquer.core.model.PlayerKind.Ai(Difficulty.NORMAL) },
                rules = RuleConstants(),
            )
            playOut(start, Difficulty.NORMAL, maxRounds = 400) { event ->
                if (event is com.msa.fightandconquer.core.engine.GameEvent.BuildingBuilt) built.add(event.building)
                if (event is com.msa.fightandconquer.core.engine.GameEvent.ResearchCompleted) completed.add(event.tech)
            }
        }
        assertTrue("no UNIVERSITY built across 6 games", Building.UNIVERSITY in built)
        assertTrue("no research completed across 6 games", completed.isNotEmpty())
        assertTrue("no tier-1 tech completed", completed.any { it.tier == 1 })
    }

    @Test
    fun `sea-locked normal AIs research navigation before founding a port`() {
        // Two five-hex islands: the ladder must run University -> NAVIGATION ->
        // Port, in that order, and actually get there.
        val start = strip(13, 0..4, 8..12, rules = rules)
            .withSea(listOf(hex(5), hex(6), hex(7)))
        val events = ArrayList<com.msa.fightandconquer.core.engine.GameEvent>()
        playOut(start, Difficulty.NORMAL, maxRounds = 200) { events.add(it) }
        val navigationAt = events.indexOfFirst {
            it is com.msa.fightandconquer.core.engine.GameEvent.ResearchCompleted && it.tech == Tech.NAVIGATION
        }
        val portAt = events.indexOfFirst {
            it is com.msa.fightandconquer.core.engine.GameEvent.BuildingBuilt && it.building == Building.PORT
        }
        assertTrue("NAVIGATION never completed", navigationAt >= 0)
        assertTrue("no port founded after NAVIGATION", portAt >= 0)
        assertTrue("port before NAVIGATION", navigationAt < portAt)
    }
}
