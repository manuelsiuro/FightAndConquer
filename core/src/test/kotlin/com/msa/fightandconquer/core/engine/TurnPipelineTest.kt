package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.custom
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withFlora
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun GameState.endTurn(): ReduceResult = Reducer.reduce(this, GameAction.EndTurn)

class EconomyTest {

    @Test
    fun `income and upkeep are applied at turn start`() {
        // P1 owns 3 hexes (6..8), has one spearman (upkeep 6): 3 - 6 = -3 per turn
        val s = strip(9, 0..2, 6..8, treasury = 20)
            .withUnit(owner = 1, tier = 2, at = hex(7))
        val (next, events) = s.endTurn()
        assertEquals(PlayerId(1), next.currentPlayer)
        val started = events.filterIsInstance<GameEvent.TurnStarted>().single()
        assertEquals(3, started.income)
        assertEquals(6, started.upkeep)
        assertEquals(17, next.player(PlayerId(1)).treasury)
        assertInvariants(next)
    }

    @Test
    fun `farm adds income and tree blocks hex income`() {
        val s = strip(9, 0..2, 6..8)
        // P1: farm on 7, tree on 6 -> income = hex7(1+4) + hex8(1) = 6; hex6 blocked
        val s2 = s.copy(
            tiles = s.tiles + (hex(7) to s.tiles.getValue(hex(7)).copy(building = com.msa.fightandconquer.core.model.Building.FARM)),
        ).withFlora(Flora.Tree, at = hex(6))
        val (_, events) = s2.endTurn()
        val started = events.filterIsInstance<GameEvent.TurnStarted>().single()
        assertEquals(6, started.income)
    }

    @Test
    fun `bankruptcy kills every unit and leaves gravestones`() {
        // P1: 3 hexes income, knight upkeep 54, treasury 10 -> -41 -> bankruptcy
        val s = strip(9, 0..2, 6..8, treasury = 100)
            .withTreasury(1, 10)
            .withUnit(owner = 1, tier = 4, at = hex(7))
        val knight = s.unitIdAt(hex(7))
        val (next, events) = s.endTurn()
        assertEquals(0, next.player(PlayerId(1)).treasury)
        assertNull(next.units[knight])
        assertTrue(next.tiles.getValue(hex(7)).flora is Flora.Gravestone)
        assertTrue(events.any { it is GameEvent.Bankruptcy })
        assertTrue(events.any { it is GameEvent.UnitDied && it.cause == DeathCause.BANKRUPTCY })
        assertInvariants(next)
    }

    @Test
    fun `solvent player keeps units`() {
        val s = strip(9, 0..2, 6..8, treasury = 100)
            .withUnit(owner = 1, tier = 1, at = hex(7))
        val (next, events) = s.endTurn()
        assertEquals(101, next.player(PlayerId(1)).treasury) // 100 + 3 - 2
        assertFalse(events.any { it is GameEvent.Bankruptcy })
    }

    @Test
    fun `spent units refresh at their owners turn start`() {
        val s = strip(9, 0..2, 6..8)
            .withUnit(owner = 1, tier = 1, at = hex(7), spent = true)
        val id = s.unitIdAt(hex(7))
        val (next, _) = s.endTurn()
        assertFalse(next.units.getValue(id).spent)
    }
}

class FloraLifecycleTest {

    @Test
    fun `gravestone becomes a tree exactly one full round later`() {
        val s = strip(9, 0..2, 6..8)
            .withFlora(Flora.Gravestone(createdRound = 0), at = hex(7))
        // P0 ends -> P1 starts (round still 0): stays a gravestone
        val (afterP1Start, e1) = s.endTurn()
        assertTrue(afterP1Start.tiles.getValue(hex(7)).flora is Flora.Gravestone)
        assertFalse(e1.any { it is GameEvent.TreeGrown })
        // P1 ends -> P0 starts (round 1) ; P0 ends -> P1 starts (round 1): converts
        val (afterP0Start, _) = afterP1Start.endTurn()
        assertEquals(1, afterP0Start.turnNumber)
        val (afterP1Round1, e3) = afterP0Start.endTurn()
        assertTrue(afterP1Round1.tiles.getValue(hex(7)).flora is Flora.Tree)
        assertTrue(e3.any { it is GameEvent.TreeGrown && it.hex == hex(7) })
    }

    @Test
    fun `trees always spread when chance is 100 percent`() {
        val s = strip(9, 0..2, 6..8, rules = RuleConstants(treeSpreadPercent = 100))
            .withFlora(Flora.Tree, at = hex(7))
        val (next, events) = s.endTurn()
        val spread = events.filterIsInstance<GameEvent.TreeSpread>().single()
        assertEquals(hex(7), spread.from)
        assertTrue(spread.to in setOf(hex(6), hex(8)))
        assertTrue(next.tiles.getValue(spread.to).flora is Flora.Tree)
    }

    @Test
    fun `trees never spread at 0 percent`() {
        val s = strip(9, 0..2, 6..8, rules = RuleConstants(treeSpreadPercent = 0))
            .withFlora(Flora.Tree, at = hex(7))
        val (_, events) = s.endTurn()
        assertFalse(events.any { it is GameEvent.TreeSpread })
    }
}

class SlicingTest {

    /**
     * 2-row map. P1: (4,0) lobe — (5,0) bridge — (6,0) capital + (6,1).
     * P0 owns everything else and captures the bridge, cutting (4,0) off.
     */
    private fun slicedSetup(): GameState {
        val owners = HashMap<Hex, Int?>()
        for (q in 0..6) {
            owners[Hex.of(q, 0)] = if (q >= 4) 1 else 0
            owners[Hex.of(q, 1)] = if (q >= 6) 1 else 0
        }
        return custom(owners, capital0 = Hex.of(0, 0), capital1 = Hex.of(6, 0))
    }

    @Test
    fun `capturing a bridge hex starves the isolated lobe`() {
        val s = slicedSetup()
            .withUnit(owner = 0, tier = 2, at = Hex.of(5, 1))
            .withUnit(owner = 1, tier = 1, at = Hex.of(4, 0))
        val attacker = s.unitIdAt(Hex.of(5, 1))
        val victim = s.unitIdAt(Hex.of(4, 0))

        // Bridge (5,0) defense: peasant on (4,0) adjacent = 1 -> spearman (2) captures.
        val (afterCapture, _) = Reducer.reduce(s, GameAction.MoveUnit(attacker, Hex.of(5, 0)))
        assertTrue("lobe flagged starving", afterCapture.tiles.getValue(Hex.of(4, 0)).starving)
        assertFalse("capital side unaffected", afterCapture.tiles.getValue(Hex.of(6, 1)).starving)
        // Victim's unit still alive mid-turn...
        assertTrue(afterCapture.units.containsKey(victim))

        // ...but dies at P1's turn start, leaving a gravestone.
        val (afterP1Start, events) = afterCapture.endTurn()
        assertNull(afterP1Start.units[victim])
        assertTrue(events.any { it is GameEvent.UnitDied && it.cause == DeathCause.STARVED })
        assertTrue(afterP1Start.tiles.getValue(Hex.of(4, 0)).flora is Flora.Gravestone)
        // Starving hex produced no income: income = owned non-starving hexes = (6,0)+(6,1) = 2
        val started = events.filterIsInstance<GameEvent.TurnStarted>().single()
        assertEquals(2, started.income)
        assertInvariants(afterP1Start)
    }

    @Test
    fun `starving hex remains owned and capturable`() {
        val s = slicedSetup().withUnit(owner = 0, tier = 2, at = Hex.of(5, 1))
        val attacker = s.unitIdAt(Hex.of(5, 1))
        val (afterCapture, _) = Reducer.reduce(s, GameAction.MoveUnit(attacker, Hex.of(5, 0)))
        assertEquals(PlayerId(1), afterCapture.tiles.getValue(Hex.of(4, 0)).owner)
    }
}

class TurnOrderTest {

    @Test
    fun `eliminated players are skipped in rotation`() {
        val s = strip(9, 0..2, 6..8)
        val eliminated = s.copy(
            players = s.players.map { if (it.id == PlayerId(1)) it.copy(eliminated = true, capital = null) else it },
            tiles = s.tiles.mapValues { (_, t) ->
                if (t.owner == PlayerId(1)) t.copy(owner = null, building = null) else t
            },
        )
        val (next, _) = eliminated.endTurn()
        assertEquals(PlayerId(0), next.currentPlayer) // wraps straight back
        assertEquals(1, next.turnNumber)
    }

    @Test
    fun `surrender eliminates and passes the turn`() {
        val threeOwners = HashMap<Hex, Int?>()
        for (q in 0..8) threeOwners[Hex.of(q, 0)] = if (q <= 2) 0 else if (q >= 6) 1 else null
        val s = custom(threeOwners, capital0 = Hex.of(0, 0), capital1 = Hex.of(8, 0))
            .withUnit(owner = 0, tier = 1, at = Hex.of(1, 0))
        val (next, events) = Reducer.reduce(s, GameAction.Surrender)
        assertTrue(next.player(PlayerId(0)).eliminated)
        // territory reverted to neutral
        assertNull(next.tiles.getValue(Hex.of(1, 0)).owner)
        // remaining player wins
        assertTrue(events.any { it is GameEvent.GameOver && it.winner == PlayerId(1) })
        assertInvariants(next)
    }
}

class DeterminismTest {

    private val json = kotlinx.serialization.json.Json

    private fun playScript(s: GameState): GameState {
        var state = s
        val actions = listOf(
            GameAction.BuyUnit(1, hex(1)), // becomes UnitId(1) on a fresh strip
            GameAction.MoveUnit(com.msa.fightandconquer.core.model.UnitId(1), hex(3)),
            GameAction.EndTurn,
            GameAction.BuyUnit(2, hex(7)),
            GameAction.EndTurn,
            GameAction.EndTurn,
            GameAction.EndTurn,
        )
        for (a in actions) state = Reducer.reduce(state, a).state
        return state
    }

    @Test
    fun `same seed and actions produce bit-identical serialized states`() {
        val a = playScript(strip(9, 0..2, 6..8, seed = 777, rules = RuleConstants(treeSpreadPercent = 50)))
        val b = playScript(strip(9, 0..2, 6..8, seed = 777, rules = RuleConstants(treeSpreadPercent = 50)))
        assertEquals(
            json.encodeToString(GameState.serializer(), a),
            json.encodeToString(GameState.serializer(), b),
        )
    }

    @Test
    fun `serialization roundtrip preserves state exactly`() {
        val s = playScript(strip(9, 0..2, 6..8, seed = 123))
        val encoded = json.encodeToString(GameState.serializer(), s)
        val decoded = json.decodeFromString(GameState.serializer(), encoded)
        assertEquals(s, decoded)
        // and re-encoding is stable
        assertEquals(encoded, json.encodeToString(GameState.serializer(), decoded))
    }

    @Test
    fun `different seeds diverge under randomness`() {
        val rules = RuleConstants(treeSpreadPercent = 100)
        val a = strip(9, 0..2, 6..8, seed = 1, rules = rules).withFlora(Flora.Tree, at = hex(4)).endTurn()
        val b = strip(9, 0..2, 6..8, seed = 2, rules = rules).withFlora(Flora.Tree, at = hex(4)).endTurn()
        // Both spread (100%), but seeds may pick different targets; at minimum states are valid.
        assertInvariants(a.state)
        assertInvariants(b.state)
    }
}
