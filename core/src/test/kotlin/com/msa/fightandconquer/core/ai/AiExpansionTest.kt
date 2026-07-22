package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withDeposit
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expansion-economy AI gates: the new buildings must actually get used — and only legally. */
class AiExpansionTest {

    private fun newAiGame(seed: Long, difficulties: List<Difficulty>, rules: RuleConstants = RuleConstants()): GameState {
        val params = MapParams(
            seed = seed,
            size = MapSize.SMALL,
            playerCount = difficulties.size,
            shape = MapShape.entries[(seed % 3).toInt()],
        )
        return MapGenerator.generate(params).newGame(
            gameSeed = seed * 31 + 7,
            kinds = difficulties.map { PlayerKind.Ai(it) },
            rules = rules,
        )
    }

    /** Plays full games and returns every Building type the AIs constructed. */
    private fun builtAcrossGames(seeds: LongRange, difficulty: Difficulty, rules: RuleConstants = RuleConstants()): Set<Building> {
        val built = HashSet<Building>()
        for (seed in seeds) {
            // Same seat-count scheme as the main termination gate: chokepoint-heavy
            // 2-player island duels can stalemate legitimately until siege units exist.
            val playerCount = 2 + (seed % 3).toInt()
            var state = newAiGame(seed, List(playerCount) { difficulty }, rules)
            val ais = List(playerCount) { AiPlayer(difficulty) }
            while (state.phase is GamePhase.Playing && state.turnNumber < 400) {
                val ai = ais[state.currentPlayer.value]
                var actions = 0
                while (true) {
                    val action = ai.chooseAction(state)
                    val result = Reducer.reduce(state, action)
                    result.events.filterIsInstance<GameEvent.BuildingBuilt>().forEach { built.add(it.building) }
                    state = result.state
                    actions++
                    if (action == GameAction.EndTurn || state.phase !is GamePhase.Playing) break
                    if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                        state = Reducer.reduce(state, GameAction.EndTurn).state
                        break
                    }
                }
            }
            assertTrue("seed $seed did not finish", state.phase is GamePhase.Finished)
        }
        return built
    }

    @Test
    fun `normal AIs build mines and lumber camps and never a fogless watchtower`() {
        val built = builtAcrossGames(1L..6L, Difficulty.NORMAL)
        assertTrue("no MINE built across 6 games", Building.MINE in built)
        assertTrue("no LUMBER_CAMP built across 6 games", Building.LUMBER_CAMP in built)
        assertTrue("WATCHTOWER built without fog of war", Building.WATCHTOWER !in built)
    }

    @Test
    fun `normal AI consolidates with a market when the front is quiet`() {
        // Markets lose the argmax to any capture — correctly, land beats garnish income.
        // On a board with nothing left to take, the AI must invest instead of hoarding:
        // P0's island is fully owned, P1 sits on a disconnected island far away.
        val owners = HashMap<com.msa.fightandconquer.core.hex.Hex, Int?>()
        val center = com.msa.fightandconquer.core.hex.Hex.of(0, 0)
        com.msa.fightandconquer.core.hex.HexMath.range(center, 2).forEach { owners[it] = 0 }
        val enemyCapital = com.msa.fightandconquer.core.hex.Hex.of(9, 0)
        owners[enemyCapital] = 1
        var state = com.msa.fightandconquer.core.TestStates.custom(
            owners,
            capital0 = center,
            capital1 = enemyCapital,
        )
        val ai = AiPlayer(Difficulty.NORMAL)
        var built = false
        repeat(AiPlayer.MAX_ACTIONS_PER_TURN) {
            val action = ai.chooseAction(state)
            if (action == GameAction.EndTurn) return@repeat
            state = Reducer.reduce(state, action).state
            if (state.tiles.values.any { it.building == Building.MARKET }) built = true
        }
        assertTrue("quiet-front AI never built a market", built)
    }

    @Test
    fun `hard AI builds watchtowers in fog games`() {
        val built = builtAcrossGames(1L..4L, Difficulty.HARD, RuleConstants(fogOfWar = true))
        assertTrue("no WATCHTOWER built across 4 fog games", Building.WATCHTOWER in built)
    }

    /** Drives one full turn for the current player; returns the state after EndTurn. */
    private fun playOneTurn(start: GameState, ai: AiPlayer): GameState {
        var state = start
        var actions = 0
        while (true) {
            val action = ai.chooseAction(state)
            state = Reducer.reduce(state, action).state
            actions++
            if (action == GameAction.EndTurn || state.phase !is GamePhase.Playing) return state
            if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                return Reducer.reduce(state, GameAction.EndTurn).state
            }
        }
    }

    @Test
    fun `hard AI cracks a strong tower with a catapult where soldiers cannot`() {
        // Strong tower guards hex 27 at defense 3; treasury 35 affords a catapult (30)
        // but not the tier-4 soldier (40) — only siege can take the hex. The 27-hex
        // economy keeps net income healthy so the catapult's upkeep is sustainable.
        val stuck = strip(30, 0..26, 27..29)
            .withTreasury(0, 35)
            .withBuilding(Building.STRONG_TOWER, at = hex(27))
        val cracked = playOneTurn(stuck, AiPlayer(Difficulty.HARD))
        assertEquals(
            com.msa.fightandconquer.core.model.PlayerId(0),
            cracked.tiles.getValue(hex(27)).owner,
        )
        assertTrue(cracked.units.values.any { it.type == com.msa.fightandconquer.core.model.UnitType.CATAPULT })

        // Same position with specials disabled: the hex is untakeable this turn.
        val disabledRules = RuleConstants(specialUnitsEnabled = false)
        val stuckClassic = strip(30, 0..26, 27..29, rules = disabledRules)
            .withTreasury(0, 35)
            .withBuilding(Building.STRONG_TOWER, at = hex(27))
        val stalled = playOneTurn(stuckClassic, AiPlayer(Difficulty.HARD))
        assertEquals(
            com.msa.fightandconquer.core.model.PlayerId(1),
            stalled.tiles.getValue(hex(27)).owner,
        )
    }

    @Test
    fun `hard AI hardens an exposed border on its first turn`() {
        // P0 owns a 7-hex flower; two P1 spearmen threaten three border hexes, all
        // coverable by one defensive purchase (archer or tower) at (1,0).
        val center = com.msa.fightandconquer.core.hex.Hex.of(0, 0)
        val owners = HashMap<com.msa.fightandconquer.core.hex.Hex, Int?>()
        owners[center] = 0
        com.msa.fightandconquer.core.hex.HexMath.neighbors(center).forEach { owners[it] = 0 }
        val e1 = com.msa.fightandconquer.core.hex.Hex.of(2, -1)
        val e2 = com.msa.fightandconquer.core.hex.Hex.of(1, 1)
        owners[e1] = 1
        owners[e2] = 1
        val start = com.msa.fightandconquer.core.TestStates.custom(
            owners,
            capital0 = com.msa.fightandconquer.core.hex.Hex.of(-1, 0),
            capital1 = e1,
            treasury = 20,
        )
            .withUnit(owner = 1, tier = 2, at = e1, spent = true)
            .withUnit(owner = 1, tier = 2, at = e2, spent = true)

        val watched = com.msa.fightandconquer.core.hex.Hex.of(1, 0)
        val after = playOneTurn(start, AiPlayer(Difficulty.HARD))
        assertTrue(
            "border still defends at ${Rules.defenseOf(after, watched)}",
            Rules.defenseOf(after, watched) >= 2,
        )
    }

    @Test
    fun `hard AI mines an owned vein within one turn`() {
        val s = strip(9, 0..5, 6..8)
            .withDeposit(Deposit.GOLD_VEIN, at = hex(3))
            .withTreasury(0, 40)
        val ai = AiPlayer(Difficulty.HARD)
        var state = s
        var mined = false
        repeat(AiPlayer.MAX_ACTIONS_PER_TURN) {
            val action = ai.chooseAction(state)
            if (action == GameAction.EndTurn) return@repeat
            state = Reducer.reduce(state, action).state
            if (state.tiles.getValue(hex(3)).building == Building.MINE) mined = true
        }
        assertTrue("hard AI left its gold vein unmined", mined)
        assertEquals(Building.MINE, state.tiles.getValue(hex(3)).building)
    }
}
