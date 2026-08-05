package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.engine.RejectionReason
import com.msa.fightandconquer.core.engine.ScriptGrant
import com.msa.fightandconquer.core.engine.ScriptSpawn
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.core.persist.SaveCodec
import com.msa.fightandconquer.core.persist.SaveGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scripted-event action is the one way a campaign can put something on the board that
 * no player bought. These tests pin the two properties that makes that safe: it is gated
 * off everywhere except a campaign level, and it replays exactly like any other action.
 */
class ScriptedActionTest {

    private fun scripted(): GameState =
        TestStates.strip(9, 0..2, 6..8, rules = RuleConstants(scriptedEventsEnabled = true))

    private fun reinforcements(hex: com.msa.fightandconquer.core.hex.Hex = hex(1)) =
        GameAction.RunScript(
            tag = "relief_column",
            spawns = listOf(ScriptSpawn(PlayerId(0), hex, UnitType.SOLDIER, tier = 2)),
            grants = listOf(ScriptGrant(PlayerId(0), 25)),
        )

    @Test
    fun `a skirmish game refuses scripted events`() {
        val state = TestStates.strip(9, 0..2, 6..8) // default rules: gate off
        val result = Reducer.reduce(state, reinforcements())

        val rejected = result.events.single() as GameEvent.ActionRejected
        assertEquals(RejectionReason.SCRIPTED_EVENTS_DISABLED, rejected.reason)
        assertEquals(state, result.state)
    }

    @Test
    fun `a campaign level spawns the reinforcements and pays the gold`() {
        val state = scripted().let { it.copy(players = it.players.map { p -> p.copy(treasury = 5) }) }

        val result = Reducer.reduce(state, reinforcements())

        assertInvariants(result.state)
        val unit = result.state.units.values.single()
        assertEquals(PlayerId(0), unit.owner)
        assertEquals(2, unit.tier)
        assertFalse("reinforcements arrive ready to fight", unit.spent)
        assertEquals(30, result.state.player(PlayerId(0)).treasury)
        assertTrue(result.events.first() is GameEvent.ScriptFired)
        assertTrue(result.events.any { it is GameEvent.UnitSpawned })
    }

    @Test
    fun `a scripted spawn cannot land on an occupied hex, enemy ground or the sea`() {
        val state = scripted().withUnit(owner = 0, tier = 1, at = hex(1))

        fun reasonFor(spawn: ScriptSpawn): RejectionReason? {
            val events = Reducer.reduce(state, GameAction.RunScript("t", listOf(spawn))).events
            return (events.singleOrNull() as? GameEvent.ActionRejected)?.reason
        }

        assertEquals(
            "occupied hex",
            RejectionReason.INVALID_SCRIPT_TARGET,
            reasonFor(ScriptSpawn(PlayerId(0), hex(1))),
        )
        assertEquals(
            "enemy ground",
            RejectionReason.INVALID_SCRIPT_TARGET,
            reasonFor(ScriptSpawn(PlayerId(0), hex(7))),
        )
        assertEquals(
            "off-map hex",
            RejectionReason.INVALID_SCRIPT_TARGET,
            reasonFor(ScriptSpawn(PlayerId(0), hex(99))),
        )
        assertEquals(
            "a boat needs water",
            RejectionReason.INVALID_SCRIPT_TARGET,
            reasonFor(ScriptSpawn(PlayerId(0), hex(2), UnitType.TRANSPORT)),
        )
        assertNull("its own empty ground is fine", reasonFor(ScriptSpawn(PlayerId(0), hex(2))))
    }

    @Test
    fun `two spawns cannot share a hex`() {
        val action = GameAction.RunScript(
            "double",
            listOf(ScriptSpawn(PlayerId(0), hex(1)), ScriptSpawn(PlayerId(0), hex(1))),
        )
        val rejected = Reducer.reduce(scripted(), action).events.single() as GameEvent.ActionRejected
        assertEquals(RejectionReason.INVALID_SCRIPT_TARGET, rejected.reason)
    }

    @Test
    fun `a story beat cannot be undone but stays in the replay log`() {
        val engine = GameEngine(scripted())
        engine.submit(GameAction.BuyUnit(1, hex(2)))
        assertTrue("an ordinary purchase is undoable", engine.canUndo())

        assertTrue(engine.submit(reinforcements()) is LegalityResult.Ok)

        assertFalse("the beat seals the history behind it", engine.canUndo())
        assertEquals(2, engine.toSave().actionsThisTurn.size)
    }

    @Test
    fun `a save containing a story beat replays bit-identically`() {
        val engine = GameEngine(scripted())
        engine.submit(GameAction.BuyUnit(1, hex(2)))
        engine.submit(reinforcements())
        val save = engine.toSave()

        val text = SaveCodec.encode(save)
        val restored = SaveCodec.restore(SaveCodec.decode(text))

        assertEquals(
            SaveCodec.json.encodeToString(GameState.serializer(), engine.state.value),
            SaveCodec.json.encodeToString(GameState.serializer(), restored),
        )
        assertEquals(text, SaveCodec.encode(SaveGame(turnStartState = save.turnStartState, actionsThisTurn = save.actionsThisTurn)))
    }

    @Test
    fun `a story beat draws no randomness`() {
        val state = scripted()
        val after = Reducer.reduce(state, reinforcements()).state
        assertEquals("rngState must be untouched", state.rngState, after.rngState)
    }
}
