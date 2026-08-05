package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.engine.ScriptGrant
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import com.msa.fightandconquer.core.persist.SaveCodec
import com.msa.fightandconquer.core.persist.SaveGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tracker is the campaign's scoreboard and the only campaign state a [GameState]
 * cannot imply. It must therefore be reconstructible purely by replaying a save.
 */
class CampaignTrackerTest {

    private val objectives = listOf(Objective.SinkBoats(2))

    private fun fold(tracker: CampaignTracker, before: GameState, action: GameAction): Pair<CampaignTracker, GameState> {
        val result = Reducer.reduce(before, action)
        return CampaignTracker.step(
            tracker, before, result.state, result.events, PlayerId(0), objectives,
        ) to result.state
    }

    @Test
    fun `sinking an enemy boat counts, losing your own does not`() {
        // P0 warship at hex(3); P1 transport at hex(4), both afloat.
        val state = TestStates.strip(9, 0..2, 6..8, rules = RuleConstants())
            .withSea(listOf(hex(3), hex(4)))
            .withUnit(owner = 0, tier = 1, at = hex(3), type = UnitType.WARSHIP)
            .withUnit(owner = 1, tier = 1, at = hex(4), type = UnitType.TRANSPORT)

        val (tracker, after) = fold(
            CampaignTracker(),
            state,
            GameAction.MoveUnit(state.unitIdAt(hex(3)), hex(4)),
        )

        assertEquals("the transport went down", 1, tracker.boatsSunk)
        assertEquals("no friendly loss", 0, tracker.unitsLost)
        assertTrue(after.units.values.none { it.owner == PlayerId(1) })
    }

    @Test
    fun `losing a unit to bankruptcy counts as a loss, not a kill`() {
        val state = TestStates.strip(9, 0..2, 6..8, treasury = 0)
            .withUnit(owner = 0, tier = 4, at = hex(1)) // upkeep 54 against no purse
            .copy(currentPlayer = PlayerId(1))

        val (tracker, _) = fold(CampaignTracker(), state, GameAction.EndTurn)

        assertEquals(1, tracker.unitsLost)
        assertEquals(0, tracker.boatsSunk)
    }

    @Test
    fun `a story beat is remembered so it plays exactly once`() {
        val trigger = ScriptTrigger(
            id = "relief",
            condition = LevelCondition.RoundAtLeast(2),
            action = GameAction.RunScript("relief", grants = listOf(ScriptGrant(PlayerId(0), 30))),
        )
        val level = TestLevels.strip(scripts = listOf(trigger))
        val state = TestStates.strip(9, 0..2, 6..8, rules = RuleConstants(scriptedEventsEnabled = true))
            .copy(turnNumber = 2)
        val status = Objectives.evaluate(state, CampaignTracker(), level)

        val first = Scripts.next(level.scripts, state, PlayerId(0), status, CampaignTracker())
        assertEquals(trigger, first)

        val fired = CampaignTracker().withScriptFired(trigger.id)
        assertEquals(null, Scripts.next(level.scripts, state, PlayerId(0), status, fired))
    }

    @Test
    fun `a beat whose landing hex is blocked stays armed instead of being consumed`() {
        val trigger = ScriptTrigger(
            id = "landing",
            condition = LevelCondition.RoundAtLeast(0),
            action = GameAction.RunScript(
                "landing",
                spawns = listOf(com.msa.fightandconquer.core.engine.ScriptSpawn(PlayerId(0), hex(1))),
            ),
        )
        val level = TestLevels.strip(scripts = listOf(trigger))
        val blocked = TestStates.strip(9, 0..2, 6..8, rules = RuleConstants(scriptedEventsEnabled = true))
            .withUnit(owner = 0, tier = 1, at = hex(1))
        val status = Objectives.evaluate(blocked, CampaignTracker(), level)

        assertEquals(
            "illegal right now, so it does not fire",
            null,
            Scripts.next(level.scripts, blocked, PlayerId(0), status, CampaignTracker()),
        )

        val clear = TestStates.strip(9, 0..2, 6..8, rules = RuleConstants(scriptedEventsEnabled = true))
        val clearStatus = Objectives.evaluate(clear, CampaignTracker(), level)
        assertEquals(
            "still armed once the hex frees up",
            trigger,
            Scripts.next(level.scripts, clear, PlayerId(0), clearStatus, CampaignTracker()),
        )
    }

    @Test
    fun `a resumed save rebuilds the identical tracker`() {
        val level = TestLevels.strip(listOf(Objective.HoldHexes(listOf(hex(2)), rounds = 3)))
        val engine = GameEngine(TestStates.strip(9, 0..2, 6..8))

        // Play a turn's worth of actions, folding as the live director would.
        var live = CampaignTracker()
        fun play(action: GameAction) {
            val before = engine.state.value
            engine.submit(action)
            live = CampaignTracker.step(
                live, before, engine.state.value, engine.lastEvents, PlayerId(0), level.objectives,
            )
        }
        play(GameAction.BuyUnit(1, hex(1)))
        play(GameAction.MoveUnit(engine.state.value.unitIdAt(hex(1)), hex(3)))

        val save = SaveGame(
            turnStartState = engine.toSave().turnStartState,
            actionsThisTurn = engine.toSave().actionsThisTurn,
            campaign = CampaignSaveRef("test", level.id, CampaignTracker()),
        )
        val roundTripped = SaveCodec.decode(SaveCodec.encode(save))

        assertEquals(live, CampaignSave.restoreTracker(roundTripped, level))
    }
}
