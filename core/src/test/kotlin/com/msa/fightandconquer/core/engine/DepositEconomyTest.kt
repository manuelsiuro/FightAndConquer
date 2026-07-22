package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.custom
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withDeposit
import com.msa.fightandconquer.core.TestStates.withFlora
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Flora
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DepositIncomeTest {

    @Test
    fun `mine requires a gold vein and pays its income`() {
        val s = strip(9, 0..2, 6..8)
        val rejected = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.MINE, hex(1)))
        val rejection = rejected.events.filterIsInstance<GameEvent.ActionRejected>().single()
        assertEquals(RejectionReason.BUILDING_NEEDS_DEPOSIT, rejection.reason)

        val withVein = s.withDeposit(Deposit.GOLD_VEIN, at = hex(1))
        val (next, events) = Reducer.reduce(withVein, GameAction.BuyBuilding(BuildingType.MINE, hex(1)))
        assertTrue(events.any { it is GameEvent.BuildingBuilt })
        assertEquals(100 - s.config.rules.mineCost, next.player(PlayerId(0)).treasury)
        // 3 hexes + mine income; the bare vein itself adds nothing.
        assertEquals(3 + s.config.rules.mineIncome, Rules.incomeOf(next, PlayerId(0)))
        assertInvariants(next)
    }

    @Test
    fun `bare vein produces plain hex income`() {
        val s = strip(9, 0..2, 6..8).withDeposit(Deposit.GOLD_VEIN, at = hex(1))
        assertEquals(3, Rules.incomeOf(s, PlayerId(0)))
    }

    @Test
    fun `fertile boosts the hex and a farm on it`() {
        val s = strip(9, 0..2, 6..8).withDeposit(Deposit.FERTILE, at = hex(1))
        assertEquals(3 + 1, Rules.incomeOf(s, PlayerId(0)))

        val (withFarm, events) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.FARM, hex(1)))
        assertTrue(events.any { it is GameEvent.BuildingBuilt })
        // 3 hexes + fertile 1 + farm 4 + fertile farm 2.
        assertEquals(3 + 1 + 4 + 2, Rules.incomeOf(withFarm, PlayerId(0)))
    }

    @Test
    fun `flora blocks all income of a fertile hex`() {
        val s = strip(9, 0..2, 6..8)
            .withDeposit(Deposit.FERTILE, at = hex(1))
            .withFlora(Flora.Tree, at = hex(1))
        assertEquals(2, Rules.incomeOf(s, PlayerId(0)))
    }
}

class MarketAndLumberCampTest {

    /** P0 owns a full hex flower (center + ring); P1 has a far single-tile capital. */
    private fun flower(): Pair<com.msa.fightandconquer.core.model.GameState, Hex> {
        val center = Hex.of(0, 0)
        val owners = HashMap<Hex, Int?>()
        owners[center] = 0
        HexMath.neighbors(center).forEach { owners[it] = 0 }
        val enemyCapital = Hex.of(5, 0)
        owners[enemyCapital] = 1
        return custom(owners, capital0 = HexMath.neighbors(center).minByOrNull { it.packed }!!, capital1 = enemyCapital) to center
    }

    @Test
    fun `market income counts owned producing neighbors up to the cap`() {
        val (s, center) = flower()
        val (next, _) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.MARKET, center))
        // 7 hexes + min(6 neighbors, cap 5) * 1.
        assertEquals(7 + 5, Rules.incomeOf(next, PlayerId(0)))
    }

    @Test
    fun `market ignores flora-blocked neighbors`() {
        val (s, center) = flower()
        val blocked = HexMath.neighbors(center).sortedBy { it.packed }.take(3)
        var state = s
        blocked.forEach { state = state.withFlora(Flora.Tree, at = it) }
        val (next, _) = Reducer.reduce(state, GameAction.BuyBuilding(BuildingType.MARKET, center))
        // 4 producing hexes (3 treed) + min(3, 5) market income.
        assertEquals(4 + 3, Rules.incomeOf(next, PlayerId(0)))
    }

    @Test
    fun `lumber camp turns adjacent own trees into income up to the cap`() {
        val (s, center) = flower()
        val treed = HexMath.neighbors(center).sortedBy { it.packed }.take(5)
        var state = s
        treed.forEach { state = state.withFlora(Flora.Tree, at = it) }
        val (next, _) = Reducer.reduce(state, GameAction.BuyBuilding(BuildingType.LUMBER_CAMP, center))
        // 2 producing hexes (5 treed, camp hex still produces) + 2 * min(5, cap 4).
        assertEquals(2 + 2 * 4, Rules.incomeOf(next, PlayerId(0)))
        assertInvariants(next)
    }

    @Test
    fun `trees next to a lumber camp never spread`() {
        // 100% spread chance: the P1 tree MUST spread unless a camp manages it.
        val rules = RuleConstants(treeSpreadPercent = 100)
        val s = strip(9, 0..2, 6..8, rules = rules).withFlora(Flora.Tree, at = hex(7))
        val (_, freeEvents) = Reducer.reduce(s, GameAction.EndTurn)
        assertTrue(freeEvents.any { it is GameEvent.TreeSpread })

        val managed = s.withBuilding(Building.LUMBER_CAMP, at = hex(6))
        val (_, campEvents) = Reducer.reduce(managed, GameAction.EndTurn)
        assertFalse(campEvents.any { it is GameEvent.TreeSpread })
    }
}

class WatchtowerTest {

    @Test
    fun `watchtower is illegal without fog of war`() {
        val s = strip(9, 0..2, 6..8)
        val (_, events) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.WATCHTOWER, hex(1)))
        val rejection = events.filterIsInstance<GameEvent.ActionRejected>().single()
        assertEquals(RejectionReason.REQUIRES_FOG_OF_WAR, rejection.reason)
    }

    @Test
    fun `watchtower extends vision to its radius under fog`() {
        val rules = RuleConstants(fogOfWar = true)
        val s = strip(9, 0..2, 6..8, rules = rules)
        // Owned-hex vision only reaches distance 2 from hex 2 -> hex 4.
        assertFalse(hex(5) in Rules.visibleHexes(s, PlayerId(0)))
        val (next, events) = Reducer.reduce(s, GameAction.BuyBuilding(BuildingType.WATCHTOWER, hex(1)))
        assertTrue(events.any { it is GameEvent.BuildingBuilt })
        val visible = Rules.visibleHexes(next, PlayerId(0))
        assertTrue(hex(1 + rules.watchtowerVisionRadius) in visible)
        assertFalse(hex(1 + rules.watchtowerVisionRadius + 1) in visible)
    }
}

class DepositCaptureTest {

    @Test
    fun `capture destroys the mine but keeps the vein`() {
        val s = strip(9, 0..5, 6..8)
            .withDeposit(Deposit.GOLD_VEIN, at = hex(6))
            .withBuilding(Building.MINE, at = hex(6))
            .withUnit(owner = 0, tier = 1, at = hex(5))
        val unit = s.unitIdAt(hex(5))
        val (next, events) = Reducer.reduce(s, GameAction.MoveUnit(unit, hex(6)))
        assertTrue(events.any { it is GameEvent.BuildingDestroyed && it.building == Building.MINE })
        val tile = next.tiles.getValue(hex(6))
        assertEquals(PlayerId(0), tile.owner)
        assertNull(tile.building)
        assertEquals(Deposit.GOLD_VEIN, tile.deposit)
        assertInvariants(next)
    }

    @Test
    fun `turn start income matches the pure income query`() {
        // Refactor guard: TurnPipeline and Rules.incomeOf must never drift apart.
        val s = strip(12, 0..2, 6..10)
            .withDeposit(Deposit.GOLD_VEIN, at = hex(6))
            .withBuilding(Building.MINE, at = hex(6))
            .withDeposit(Deposit.FERTILE, at = hex(7))
            .withBuilding(Building.FARM, at = hex(7))
            .withBuilding(Building.MARKET, at = hex(8))
            .withBuilding(Building.LUMBER_CAMP, at = hex(9))
        val expected = Rules.incomeOf(s, PlayerId(1))
        val (_, events) = Reducer.reduce(s, GameAction.EndTurn)
        val started = events.filterIsInstance<GameEvent.TurnStarted>().single()
        assertEquals(expected, started.income)
    }
}
