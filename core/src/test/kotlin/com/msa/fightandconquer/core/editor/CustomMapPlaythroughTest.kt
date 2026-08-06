package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.campaign.CampaignTracker
import com.msa.fightandconquer.core.campaign.FailCondition
import com.msa.fightandconquer.core.campaign.LevelDef
import com.msa.fightandconquer.core.campaign.LevelFactory
import com.msa.fightandconquer.core.campaign.Objective
import com.msa.fightandconquer.core.campaign.Objectives
import com.msa.fightandconquer.core.campaign.SeatDef
import com.msa.fightandconquer.core.campaign.Verdict
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.map.MapDefinition
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The editor's play-path promise, exercised headlessly the way
 * `CampaignPlaythroughTest` exercises shipped missions: a validated custom scenario
 * instantiates, an AI-vs-AI game on it terminates with invariants intact, and its
 * objectives/turn-limit score through the same tracker fold the ViewModel runs.
 */
class CustomMapPlaythroughTest {

    /** Mirrors the app's starter template: a land disc, two opposed 7-hex starts. */
    private fun starterLevel(
        objectives: List<Objective> = listOf(Objective.ConquerAll),
        failures: List<FailCondition> = emptyList(),
    ): LevelDef {
        val radius = 3
        val capitals = listOf(Hex.of(-radius + 1, 0), Hex.of(radius - 1, 0))
        val owners = HashMap<Hex, Int>()
        capitals.forEachIndexed { seat, capital ->
            HexMath.range(capital, 1).forEach { owners[it] = seat }
        }
        val tiles = HexMath.range(Hex.of(0, 0), radius).sortedBy { it.packed }.map { h ->
            TileDef(
                hex = h,
                owner = owners[h],
                building = if (h in capitals) Building.CAPITAL else null,
            )
        }
        return LevelDef(
            id = "custom_test",
            seed = 99L,
            map = MapDefinition(name = "custom_test", tiles = tiles, capitals = capitals),
            seats = listOf(SeatDef.Player, SeatDef.Ai(Difficulty.NORMAL)),
            objectives = objectives,
            failures = failures,
        )
    }

    @Test
    fun `a validated conquer-all scenario terminates`() {
        val def = CustomMapDef(id = "c1", name = "c1", level = starterLevel())
        assertTrue(CustomMapValidator.validate(def).isEmpty())
        val verdict = play(def.level)
        assertTrue("game never decided: $verdict", verdict != Verdict.InProgress)
    }

    @Test
    fun `an objective scenario scores through the tracker`() {
        val target = Hex.of(2, -2) // neutral corner of the disc
        val def = CustomMapDef(
            id = "c2",
            name = "c2",
            level = starterLevel(
                objectives = listOf(Objective.CaptureHexes(listOf(target))),
                failures = listOf(FailCondition.TurnLimit(40)),
            ),
        )
        assertTrue(CustomMapValidator.validate(def).isEmpty())
        val verdict = play(def.level)
        // Won (captured) or out of time — both prove the director decided the run.
        assertTrue("run never decided", verdict != Verdict.InProgress)
    }

    private fun play(level: LevelDef): Verdict {
        val engine = GameEngine(LevelFactory.instantiate(level))
        val seat = level.playerSeat
        var tracker = CampaignTracker()
        val cap = 90

        fun submit(action: GameAction): Boolean {
            val before = engine.state.value
            if (engine.submit(action) !is LegalityResult.Ok) return false
            tracker = CampaignTracker.step(
                tracker, before, engine.state.value, engine.lastEvents, seat, level.objectives,
            )
            return true
        }

        var status = Objectives.evaluate(engine.state.value, tracker, level, seat)
        var guard = 0
        while (status.verdict == Verdict.InProgress && engine.state.value.turnNumber < cap) {
            val state = engine.state.value
            if (state.phase !is GamePhase.Playing) break
            val difficulty = when (val kind = state.player(state.currentPlayer).kind) {
                is PlayerKind.Human -> Difficulty.NORMAL
                is PlayerKind.Ai -> kind.difficulty
            }
            val action = AiPlayer(difficulty).chooseAction(state)
            if (!submit(action)) submit(GameAction.EndTurn)
            if (++guard > AiPlayer.MAX_ACTIONS_PER_TURN) {
                submit(GameAction.EndTurn)
                guard = 0
            }
            if (action == GameAction.EndTurn) guard = 0
            assertInvariants(engine.state.value)
            status = Objectives.evaluate(engine.state.value, tracker, level, seat)
        }
        return status.verdict
    }
}
