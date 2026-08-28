package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapShape
import com.msa.fightandconquer.core.map.MapSize
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.Tile
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full-toolbox tripwires: the generator must offer what the old AI never touched. */
class ToolboxAiTest {

    @Test
    fun `a one-hex strait is a bridge candidate outside the war chest`() {
        // P0 shore (0..2,0), sea at (3,0), enemy shore (4..5,0).
        var state = TestStates.custom(
            owners = mapOf(
                hex(0, 0) to 0, hex(1, 0) to 0, hex(2, 0) to 0,
                hex(3, 0) to null,
                hex(4, 0) to 1, hex(5, 0) to 1,
            ),
            capital0 = hex(0, 0),
            capital1 = hex(5, 0),
            rules = RuleConstants(navalEnabled = true),
        )
        state = state.copy(
            tiles = state.tiles + (hex(3, 0) to Tile(owner = null, terrain = Terrain.SEA)),
        )
        val candidates = MoveGenerator.candidates(state, Difficulty.NORMAL)
        assertTrue(
            "the strait span must be an ordinary candidate, not a 300-coin escape hatch",
            candidates.any {
                it is GameAction.BuyBuilding && it.type == BuildingType.BRIDGE && it.at == hex(3, 0)
            },
        )
    }

    @Test
    fun `towers cover contested ground and interior spots never qualify`() {
        // P0 flower around (0,0); enemy hexes press the eastern petals.
        val owners = HashMap<Hex, Int?>()
        HexMath.range(Hex.of(0, 0), 1).forEach { owners[it] = 0 }
        owners[Hex.of(2, -1)] = 1
        owners[Hex.of(2, 0)] = 1
        val state = TestStates.custom(
            owners,
            capital0 = Hex.of(-1, 0),
            capital1 = Hex.of(2, -1),
            treasury = 40,
        )
        val towers = MoveGenerator.candidates(state, Difficulty.NORMAL)
            .filterIsInstance<GameAction.BuyBuilding>()
            .filter { it.type == BuildingType.TOWER }
            .map { it.at }
        assertTrue("no tower proposed on a pressed border", towers.isNotEmpty())
        for (spot in towers) {
            assertTrue(
                "tower at $spot hardens no contested hex — coverage ranking is broken",
                HexMath.range(spot, 1).any { n ->
                    owners[n] == 0 && HexMath.neighbors(n).any { owners[it] == 1 }
                },
            )
        }
    }

    @Test
    fun `a broke rear guard gets pensioned off instead of bankrupting the realm`() {
        // 9 hexes of income vs a tier-3 idling far behind a quiet border: net -9.
        val state = strip(12, 0..8, 9..11)
            .withUnit(owner = 0, tier = 3, at = hex(1))
            .withTreasury(0, 5)
        val action = AiPlayer(Difficulty.NORMAL).chooseAction(state)
        assertTrue(
            "expected the rear tier-3 to be disbanded, got $action",
            action is GameAction.DisbandUnit,
        )
    }

    @Test
    fun `normal AIs field tier-2 or better soldiers, not just peasant swarms`() {
        var sawRealSoldier = false
        for (seed in 1L..4L) {
            val playerCount = 2 + (seed % 2).toInt()
            val params = MapParams(
                seed = seed,
                size = MapSize.SMALL,
                playerCount = playerCount,
                shape = MapShape.entries[(seed % 3).toInt()],
            )
            var state = MapGenerator.generate(params).newGame(
                gameSeed = seed * 31 + 7,
                kinds = List(playerCount) { PlayerKind.Ai(Difficulty.NORMAL) },
                rules = RuleConstants(),
            )
            val ais = List(playerCount) { AiPlayer(Difficulty.NORMAL) }
            while (state.phase is GamePhase.Playing && state.turnNumber < 400) {
                val ai = ais[state.currentPlayer.value]
                var actions = 0
                while (true) {
                    val action = ai.chooseAction(state)
                    val result = Reducer.reduce(state, action)
                    result.events.filterIsInstance<GameEvent.UnitSpawned>().forEach {
                        if (it.unit.type == com.msa.fightandconquer.core.model.UnitType.SOLDIER &&
                            it.unit.tier >= 2
                        ) {
                            sawRealSoldier = true
                        }
                    }
                    result.events.filterIsInstance<GameEvent.UnitsMerged>().forEach { sawRealSoldier = true }
                    state = result.state
                    actions++
                    if (action == GameAction.EndTurn || state.phase !is GamePhase.Playing) break
                    if (actions >= AiPlayer.MAX_ACTIONS_PER_TURN) {
                        state = Reducer.reduce(state, GameAction.EndTurn).state
                        break
                    }
                }
            }
            if (sawRealSoldier) break
        }
        assertTrue(
            "four full NORMAL games and never a tier-2+ soldier or merge — peasant spam is back",
            sawRealSoldier,
        )
    }
}
