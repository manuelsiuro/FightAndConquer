package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Test

/** TEMPORARY diagnostic replay of academy_salt_and_sail — delete before merging. */
class DiagSaltAndSailTest {

    @Test
    fun `diag replay salt and sail`() {
        val level = CampaignAssets.campaigns()
            .first { it.id == "academy" }.levels.first { it.id == "academy_salt_and_sail" }
        val engine = GameEngine(LevelFactory.instantiate(level))
        val seat = level.playerSeat
        var tracker = CampaignTracker()

        fun submit(action: GameAction): Boolean {
            val before = engine.state.value
            if (engine.submit(action) !is LegalityResult.Ok) return false
            for (e in engine.lastEvents) {
                when (e) {
                    is GameEvent.TurnStarted ->
                        if (e.player == seat) {
                            val s = engine.state.value
                            val hexes = s.tiles.count { it.value.owner == seat }
                            val units = s.units.values.count { it.owner == seat }
                            println(
                                "r${s.turnNumber} p0: treasury=${s.player(seat).treasury} " +
                                    "income=${e.income} upkeep=${e.upkeep} hexes=$hexes units=$units",
                            )
                        }
                    is GameEvent.BuildingBuilt -> println("  built ${e.building} at ${e.hex}")
                    is GameEvent.UnitSpawned ->
                        println("  spawn o${e.unit.owner.value} ${e.unit.type} t${e.unit.tier} at ${e.unit.hex}")
                    is GameEvent.UnitDied -> println("  died at ${e.hex} (${e.cause})")
                    else -> {}
                }
            }
            tracker = CampaignTracker.step(
                tracker, before, engine.state.value, engine.lastEvents, seat, level.objectives,
            )
            return true
        }

        var status = Objectives.evaluate(engine.state.value, tracker, level, seat)
        var guard = 0
        while (status.verdict == Verdict.InProgress && engine.state.value.turnNumber < 70) {
            val state = engine.state.value
            if (state.phase !is GamePhase.Playing) break
            val beat = Scripts.next(level.scripts, state, seat, status, tracker)
            if (beat != null) {
                if (submit(beat.action)) tracker = tracker.withScriptFired(beat.id)
                status = Objectives.evaluate(engine.state.value, tracker, level, seat)
                continue
            }
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
            status = Objectives.evaluate(engine.state.value, tracker, level, seat)
        }
        println(
            "verdict=${status.verdict} rounds=${engine.state.value.turnNumber} " +
                status.rows.joinToString { "${it.progress}/${it.target}" },
        )
    }
}
