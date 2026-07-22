package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitId
import com.msa.fightandconquer.core.persist.SaveCodec
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun `submit applies legal actions and rejects illegal ones without state change`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        assertTrue(engine.submit(GameAction.BuyUnit(1, hex(1))) is LegalityResult.Ok)
        assertEquals(90, engine.state.value.player(PlayerId(0)).treasury)

        val rejected = engine.submit(GameAction.BuyUnit(1, hex(7))) // enemy territory, not adjacent
        assertTrue(rejected is LegalityResult.Rejected)
        assertEquals(90, engine.state.value.player(PlayerId(0)).treasury)
    }

    @Test
    fun `undo reverts within a turn and stops at turn boundaries`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        assertFalse(engine.canUndo())
        engine.submit(GameAction.BuyUnit(1, hex(1)))
        assertTrue(engine.canUndo())
        assertTrue(engine.undo())
        assertEquals(100, engine.state.value.player(PlayerId(0)).treasury)
        assertFalse(engine.canUndo())

        engine.submit(GameAction.BuyUnit(1, hex(1)))
        engine.submit(GameAction.EndTurn)
        assertFalse("undo cleared across turns", engine.canUndo())
        assertFalse(engine.undo())
    }

    @Test
    fun `save and restore mid-turn reproduces the exact state`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        engine.submit(GameAction.BuyUnit(1, hex(1)))
        engine.submit(GameAction.MoveUnit(UnitId(1), hex(3)))

        val encoded = SaveCodec.encode(engine.toSave())
        val restored = GameEngine.fromSave(SaveCodec.decode(encoded))

        assertEquals(engine.state.value, restored.state.value)
        // Restored engine can continue playing and undo the replayed actions.
        assertTrue(restored.canUndo())
        assertTrue(restored.submit(GameAction.EndTurn) is LegalityResult.Ok)
    }

    @Test
    fun `saves embed the full rules snapshot so later tuning cannot corrupt them`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        val encoded = SaveCodec.encode(engine.toSave())
        // Default-valued rules must still be serialized (encodeDefaults = true).
        assertTrue("rules snapshot missing from save", encoded.contains("unitUpkeep"))
        assertTrue(encoded.contains("treeSpreadPercent"))
    }

    @Test
    fun `save-restore across a turn boundary`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        engine.submit(GameAction.BuyUnit(1, hex(1)))
        engine.submit(GameAction.EndTurn)
        engine.submit(GameAction.BuyUnit(1, hex(7)))

        val restored = GameEngine.fromSave(SaveCodec.decode(SaveCodec.encode(engine.toSave())))
        assertEquals(engine.state.value, restored.state.value)
        assertEquals(PlayerId(1), restored.state.value.currentPlayer)
    }

    @Test
    fun `buyableAt lists exactly the affordable legal options`() {
        val s = strip(9, 0..2, 6..8).withUnit(owner = 0, tier = 1, at = hex(1))
        val engine = GameEngine(s)
        // Empty own hex 2: all four unit tiers affordable at 100, archer + catapult,
        // tower + strong tower, market + lumber camp; farm rejected (hex 2 not adjacent
        // to capital at 0), mine rejected (no gold vein), watchtower rejected (fog off).
        val options = engine.buyableAt(hex(2))
        assertEquals(
            setOf(
                PurchaseOption.Unit(1, 10), PurchaseOption.Unit(2, 20),
                PurchaseOption.Unit(3, 30), PurchaseOption.Unit(4, 40),
                PurchaseOption.Unit(1, 14, com.msa.fightandconquer.core.model.UnitType.ARCHER),
                PurchaseOption.Unit(1, 30, com.msa.fightandconquer.core.model.UnitType.CATAPULT),
                PurchaseOption.Structure(com.msa.fightandconquer.core.model.BuildingType.TOWER, 15),
                PurchaseOption.Structure(com.msa.fightandconquer.core.model.BuildingType.STRONG_TOWER, 35),
                PurchaseOption.Structure(com.msa.fightandconquer.core.model.BuildingType.MARKET, 25),
                PurchaseOption.Structure(com.msa.fightandconquer.core.model.BuildingType.LUMBER_CAMP, 15),
            ),
            options.toSet(),
        )
        // On the hex with the tier-1 unit: only a tier-1 buy-merge is offered for units.
        val mergeOptions = engine.buyableAt(hex(1)).filterIsInstance<PurchaseOption.Unit>()
        assertEquals(listOf(PurchaseOption.Unit(1, 10)), mergeOptions)
    }

    @Test
    fun `income summary matches rules`() {
        val engine = GameEngine(strip(9, 0..2, 6..8).withUnit(owner = 0, tier = 2, at = hex(1)))
        val summary = engine.incomeSummary(PlayerId(0))
        assertEquals(3, summary.income)
        assertEquals(6, summary.upkeep)
        assertEquals(100, summary.treasury)
        assertEquals(-3, summary.net)
        assertEquals(97, summary.projected)
    }

    @Test
    fun `events are emitted in order for subscribers`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        val received = ArrayList<GameEvent>()
        kotlinx.coroutines.test.runTest {
            val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                engine.events.collect { received.add(it) }
            }
            engine.submit(GameAction.BuyUnit(1, hex(1)))
            engine.submit(GameAction.MoveUnit(UnitId(1), hex(3)))
            kotlinx.coroutines.yield()
            job.cancel()
        }
        assertTrue(received.any { it is GameEvent.UnitSpawned })
        assertTrue(received.any { it is GameEvent.HexCaptured })
        // spawn precedes capture
        assertTrue(
            received.indexOfFirst { it is GameEvent.UnitSpawned } <
                received.indexOfFirst { it is GameEvent.HexCaptured },
        )
    }
}
