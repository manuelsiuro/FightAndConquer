package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.custom
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.map.MapDefinition
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.persist.SaveCodec
import com.msa.fightandconquer.core.persist.SaveGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val FOG = RuleConstants(fogOfWar = true)

private fun hexes(range: IntRange): Set<com.msa.fightandconquer.core.hex.Hex> =
    range.map { hex(it) }.toSet()

class FogOfWarTest {

    // Strip maps are one row, so distance == |q1 - q2| and off-row range hexes
    // exercise the clip-to-map intersection.

    @Test
    fun `visible hexes cover owned, unit and building radii clipped to the map`() {
        val s = strip(12, 0..2, 9..11, rules = FOG)
        // P0: owned {0,1,2} radius 2 and capital at 0 radius 4 -> 0..4.
        assertEquals(hexes(0..4), Rules.visibleHexes(s, PlayerId(0)))
        // P1 mirrored: owned {9,10,11} radius 2, capital at 11 radius 4 -> 7..11.
        assertEquals(hexes(7..11), Rules.visibleHexes(s, PlayerId(1)))

        // A unit on the frontier hex extends vision by its own radius (3).
        val withUnit = s.withUnit(owner = 0, tier = 1, at = hex(2))
        assertEquals(hexes(0..5), Rules.visibleHexes(withUnit, PlayerId(0)))

        // A tower grants building vision (4); a farm grants none.
        val withTower = s.withBuilding(Building.TOWER, at = hex(2))
        assertEquals(hexes(0..6), Rules.visibleHexes(withTower, PlayerId(0)))
        val withFarm = s.withBuilding(Building.FARM, at = hex(2))
        assertEquals(hexes(0..4), Rules.visibleHexes(withFarm, PlayerId(0)))
    }

    @Test
    fun `player with no hexes and no units sees nothing`() {
        val owners = (0..5).associate { hex(it) to if (it <= 2) 0 else null }
        val s = custom(owners, capital0 = hex(0), capital1 = hex(5), rules = FOG)
        assertTrue(Rules.visibleHexes(s, PlayerId(1)).isEmpty())
    }

    @Test
    fun `newGame seeds discovered with starting vision only when fog is on`() {
        val map = MapDefinition(
            name = "fog-test",
            tiles = (0..11).map {
                TileDef(
                    hex = hex(it),
                    owner = if (it <= 2) 0 else if (it >= 9) 1 else null,
                    building = if (it == 0 || it == 11) Building.CAPITAL else null,
                )
            },
            capitals = listOf(hex(0), hex(11)),
        )
        val kinds = listOf(PlayerKind.Human, PlayerKind.Human)

        val fogged = map.newGame(gameSeed = 1L, kinds = kinds, rules = FOG)
        for (player in fogged.players) {
            assertTrue(player.discovered.isNotEmpty())
            assertEquals(Rules.visibleHexes(fogged, player.id), player.discovered)
        }

        val plain = map.newGame(gameSeed = 1L, kinds = kinds)
        for (player in plain.players) assertTrue(player.discovered.isEmpty())
    }

    @Test
    fun `discovered grows monotonically and covers the actor's vision after every action`() {
        var s = strip(12, 0..2, 9..11, rules = FOG)
        var previous: Set<com.msa.fightandconquer.core.hex.Hex> = emptySet()

        fun apply(action: GameAction) {
            s = Reducer.reduce(s, action).state
        }
        fun checkActor(id: Int) {
            val discovered = s.player(PlayerId(id)).discovered
            assertTrue("discovered must never shrink", discovered.containsAll(previous))
            assertTrue(
                "discovered must cover live vision",
                discovered.containsAll(Rules.visibleHexes(s, PlayerId(id))),
            )
            previous = discovered
        }

        apply(GameAction.BuyUnit(tier = 1, at = hex(3))) // capture placement on the frontier
        checkActor(0)
        apply(GameAction.EndTurn)

        // The hook ran for the incoming player too (turn-start discovery).
        val p1 = s.player(PlayerId(1)).discovered
        assertTrue(p1.containsAll(Rules.visibleHexes(s, PlayerId(1))))

        previous = s.player(PlayerId(0)).discovered
        apply(GameAction.EndTurn) // back to P0
        checkActor(0)
    }

    @Test
    fun `fog off leaves discovered empty through play`() {
        var s = strip(12, 0..2, 9..11)
        s = Reducer.reduce(s, GameAction.BuyUnit(tier = 1, at = hex(3))).state
        s = Reducer.reduce(s, GameAction.EndTurn).state
        for (player in s.players) assertTrue(player.discovered.isEmpty())
    }

    @Test
    fun `save replay reproduces discovered exactly and serialization is byte stable`() {
        val start = strip(12, 0..2, 9..11, rules = FOG)
        val actions = listOf<GameAction>(
            GameAction.BuyUnit(tier = 1, at = hex(3)),
            GameAction.BuyUnit(tier = 1, at = hex(1)),
        )
        var live = start
        for (action in actions) live = Reducer.reduce(live, action).state

        val save = SaveGame(turnStartState = start, actionsThisTurn = actions)
        val restored = SaveCodec.restore(SaveCodec.decode(SaveCodec.encode(save)))
        assertEquals(live, restored)

        // Byte stability: encode -> decode -> encode must be identical (sorted
        // discovered order survives the round trip), and both runs agree.
        val json = SaveCodec.json.encodeToString(GameState.serializer(), live)
        val rejson = SaveCodec.json.encodeToString(
            GameState.serializer(),
            SaveCodec.json.decodeFromString(GameState.serializer(), json),
        )
        assertEquals(json, rejson)
        assertEquals(json, SaveCodec.json.encodeToString(GameState.serializer(), restored))
    }
}
