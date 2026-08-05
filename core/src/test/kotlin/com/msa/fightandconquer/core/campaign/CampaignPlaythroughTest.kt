package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.ai.AiPlayer
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GamePhase
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plays every shipped mission headlessly with an AI in the player's chair, running the
 * same director loop the ViewModel runs: fold the tracker, score the objectives, fire the
 * story beats.
 *
 * Two different promises are checked. Every level must **terminate** with its invariants
 * intact — that is the crash gate, and it applies to all of them. Levels flagged
 * [LevelDef.aiSolvable] must additionally be **winnable**, which is the balance gate: it
 * catches an objective that no amount of play can satisfy or a turn limit set too tight.
 * Levels whose lesson needs human intent (sign a pact, then break it) are honestly
 * flagged false and verified on a device instead.
 */
class CampaignPlaythroughTest {

    /** Enough rope for a greedy AI to blunder through a level a human would finish faster. */
    private fun roundCap(level: LevelDef): Int = (level.parRounds ?: 30) * 3

    /** How long every level's opening must stand up, whoever is driving. */
    private val OPENING_ROUNDS = 8

    @Test
    fun `every level terminates with its invariants intact, and solvable ones are won`() {
        // Play everything first, then judge: one failing level must not hide the state of
        // the other nineteen when a rules change ripples through the catalogue.
        val outcomes = CampaignAssets.campaigns().flatMap { campaign ->
            campaign.levels.map { level -> Triple(campaign, level, play(level)) }
        }
        outcomes.forEach { (campaign, level, outcome) ->
            println(
                "${campaign.id}/${level.id}: ${outcome.verdict} after ${outcome.rounds} rounds " +
                    "(cap ${roundCap(level)}, ${outcome.progress})",
            )
        }

        outcomes.forEach { (campaign, level, outcome) ->
            assertTrue(
                "${campaign.id}/${level.id} never ended within ${roundCap(level)} rounds",
                outcome.verdict != Verdict.InProgress,
            )
            if (level.aiSolvable) {
                assertEquals(
                    "${campaign.id}/${level.id} is flagged solvable but the AI got ${outcome.verdict}",
                    Verdict.Won,
                    outcome.verdict,
                )
            }
            // Regardless of the flag: the opening position must be viable. Whether the
            // stand-in eventually wins is a statement about a one-ply greedy AI, but
            // being wiped out in the first handful of rounds is a statement about the
            // level — and a mission you lose before its own story beats have fired is
            // simply mistuned.
            val minimumViableRounds = minOf(OPENING_ROUNDS, level.parRounds ?: OPENING_ROUNDS)
            assertTrue(
                "${campaign.id}/${level.id} wiped the player out by round ${outcome.rounds} " +
                    "(opening must survive $minimumViableRounds)",
                outcome.verdict == Verdict.Won || outcome.rounds >= minimumViableRounds,
            )
        }
    }

    private data class Outcome(val verdict: Verdict, val rounds: Int, val progress: String)

    private fun play(level: LevelDef): Outcome {
        val engine = GameEngine(LevelFactory.instantiate(level))
        val seat = level.playerSeat
        var tracker = CampaignTracker()
        val cap = roundCap(level)

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

            // The director gets the first word each pass, exactly as in the ViewModel.
            val beat = Scripts.next(level.scripts, state, seat, status, tracker)
            if (beat != null) {
                if (submit(beat.action)) tracker = tracker.withScriptFired(beat.id)
                status = Objectives.evaluate(engine.state.value, tracker, level, seat)
                continue
            }

            val difficulty = when (val kind = state.player(state.currentPlayer).kind) {
                // The player's own chair: a competent stand-in, never a cheat.
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
        val hexes = engine.state.value.tiles.count { it.value.owner == seat }
        return Outcome(
            status.verdict,
            engine.state.value.turnNumber,
            status.rows.joinToString { "${it.progress}/${it.target}" } + ", $hexes hexes held",
        )
    }
}
