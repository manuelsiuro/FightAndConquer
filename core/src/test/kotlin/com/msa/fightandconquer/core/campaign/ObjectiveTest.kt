package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scoring is pure: every objective and defeat clause on a hand-built strip. */
class ObjectiveTest {

    private fun base(): GameState = TestStates.strip(9, 0..2, 6..8)

    private fun statusOf(
        state: GameState,
        objectives: List<Objective> = emptyList(),
        failures: List<FailCondition> = emptyList(),
        tracker: CampaignTracker = CampaignTracker(),
    ) = Objectives.evaluate(state, tracker, TestLevels.strip(objectives, failures))

    @Test
    fun `capture-hexes reports partial progress and completes on the last hex`() {
        val hexes = listOf(hex(3), hex(4))
        val partial = base().copy(
            tiles = base().tiles + (hex(3) to base().tiles.getValue(hex(3)).copy(owner = PlayerId(0))),
        )

        assertEquals(1, statusOf(partial, listOf(Objective.CaptureHexes(hexes))).rows.single().progress)
        assertFalse(statusOf(partial, listOf(Objective.CaptureHexes(hexes))).rows.single().done)

        val complete = partial.copy(
            tiles = partial.tiles + (hex(4) to partial.tiles.getValue(hex(4)).copy(owner = PlayerId(0))),
        )
        assertEquals(Verdict.Won, statusOf(complete, listOf(Objective.CaptureHexes(hexes))).verdict)
    }

    @Test
    fun `a level is only won when every objective is done`() {
        val state = base().withTreasury(0, 100)
        val objectives = listOf(Objective.ReachTreasury(50), Objective.OwnHexCount(9))

        val status = statusOf(state, objectives)

        assertTrue(status.rows[0].done)
        assertFalse(status.rows[1].done)
        assertEquals(Verdict.InProgress, status.verdict)
    }

    @Test
    fun `survive and turn-limit read the same clock from opposite ends`() {
        val late = base().copy(turnNumber = 10)

        assertEquals(
            Verdict.Won,
            statusOf(late, listOf(Objective.SurviveRounds(10))).verdict,
        )
        assertEquals(
            Verdict.Lost(DefeatReason.OUT_OF_TIME),
            statusOf(late, listOf(Objective.ConquerAll), listOf(FailCondition.TurnLimit(10))).verdict,
        )
    }

    @Test
    fun `defeat outranks victory on the turn both land`() {
        // Objectives complete on the very round the clock runs out: the level is lost.
        val state = base().withTreasury(0, 100).copy(turnNumber = 8)
        val status = statusOf(
            state,
            listOf(Objective.ReachTreasury(50)),
            listOf(FailCondition.TurnLimit(8)),
        )

        assertTrue("the objective did complete", status.rows.single().done)
        assertEquals(Verdict.Lost(DefeatReason.OUT_OF_TIME), status.verdict)
    }

    @Test
    fun `total conquest settles a mission whatever it asked for`() {
        // A "hold the ridge for 20 rounds" level the player simply wins outright must
        // not hang waiting for an opponent who no longer exists.
        val conquered = base()
            .copy(phase = com.msa.fightandconquer.core.model.GamePhase.Finished(PlayerId(0)))
            .copy(players = base().players.map { if (it.id.value == 1) it.copy(eliminated = true) else it })

        assertEquals(
            Verdict.Won,
            statusOf(
                conquered,
                listOf(Objective.HoldHexes(listOf(hex(4)), rounds = 20)),
                listOf(FailCondition.TurnLimit(1)),
            ).verdict,
        )
    }

    @Test
    fun `a rival conquering the board is a defeat`() {
        val overrun = base()
            .copy(phase = com.msa.fightandconquer.core.model.GamePhase.Finished(PlayerId(1)))

        assertEquals(
            Verdict.Lost(DefeatReason.RIVAL_VICTORY),
            statusOf(overrun, listOf(Objective.ReachTreasury(1))).verdict,
        )
    }

    @Test
    fun `losing a protected hex ends the level`() {
        val lost = base().copy(
            tiles = base().tiles + (hex(2) to base().tiles.getValue(hex(2)).copy(owner = PlayerId(1))),
        )

        assertEquals(
            Verdict.Lost(DefeatReason.LOST_PROTECTED_HEX),
            statusOf(lost, listOf(Objective.ConquerAll), listOf(FailCondition.LoseHexes(listOf(hex(2))))).verdict,
        )
    }

    @Test
    fun `losing every unit is only judged once the level is under way`() {
        val failures = listOf(FailCondition.LoseAllUnits)

        assertEquals(
            "round 0 opens on an empty board by design",
            Verdict.InProgress,
            statusOf(base(), listOf(Objective.ConquerAll), failures).verdict,
        )
        assertEquals(
            Verdict.Lost(DefeatReason.NO_UNITS_LEFT),
            statusOf(base().copy(turnNumber = 3), listOf(Objective.ConquerAll), failures).verdict,
        )
    }

    @Test
    fun `an eliminated player loses whatever the objectives say`() {
        val dead = base().copy(
            players = base().players.map { if (it.id.value == 0) it.copy(eliminated = true) else it },
        ).withTreasury(0, 999)

        assertEquals(
            Verdict.Lost(DefeatReason.ELIMINATED),
            statusOf(dead, listOf(Objective.ReachTreasury(1))).verdict,
        )
    }

    @Test
    fun `building and unit objectives count what the player actually fields`() {
        val state = base()
            .withBuilding(Building.FARM, hex(1))
            .withUnit(owner = 0, tier = 1, at = hex(2), type = UnitType.ARCHER)

        assertTrue(statusOf(state, listOf(Objective.BuildCount(BuildingType.FARM, 1))).rows.single().done)
        assertTrue(statusOf(state, listOf(Objective.FieldUnits(UnitType.ARCHER, 1))).rows.single().done)
        assertFalse(statusOf(state, listOf(Objective.FieldUnits(UnitType.CATAPULT, 1))).rows.single().done)
    }

    @Test
    fun `a soldier stowed aboard a transport still counts as fielded`() {
        val state = TestStates.run {
            base().withSea(hex(3)).withUnit(0, 1, hex(3), type = UnitType.TRANSPORT).withCargo(hex(3), tier = 2)
        }

        assertEquals(
            1,
            statusOf(state, listOf(Objective.FieldUnits(UnitType.SOLDIER, 1))).rows.single().progress,
        )
    }

    @Test
    fun `hold-hexes credits rounds only while the grip is unbroken`() {
        val objectives = listOf(Objective.HoldHexes(listOf(hex(2)), rounds = 3))
        val level = TestLevels.strip(objectives)
        var tracker = CampaignTracker()

        // Round 1: the hold starts.
        val r1 = base().copy(turnNumber = 1)
        tracker = CampaignTracker.step(tracker, r1, r1, emptyList(), PlayerId(0), objectives)
        assertEquals(0, Objectives.evaluate(r1, tracker, level).rows.single().progress)

        // Round 3: two rounds banked.
        val r3 = base().copy(turnNumber = 3)
        tracker = CampaignTracker.step(tracker, r3, r3, emptyList(), PlayerId(0), objectives)
        assertEquals(2, Objectives.evaluate(r3, tracker, level).rows.single().progress)

        // The hex is taken at round 4: the clock resets, it does not pause.
        val lost = base().copy(
            turnNumber = 4,
            tiles = base().tiles + (hex(2) to base().tiles.getValue(hex(2)).copy(owner = PlayerId(1))),
        )
        tracker = CampaignTracker.step(tracker, lost, lost, emptyList(), PlayerId(0), objectives)
        assertEquals(0, Objectives.evaluate(lost, tracker, level).rows.single().progress)

        // Retaken at round 5: counting starts over from zero.
        val retaken = base().copy(turnNumber = 5)
        tracker = CampaignTracker.step(tracker, retaken, retaken, emptyList(), PlayerId(0), objectives)
        val later = base().copy(turnNumber = 6)
        tracker = CampaignTracker.step(tracker, later, later, emptyList(), PlayerId(0), objectives)
        assertEquals(1, Objectives.evaluate(later, tracker, level).rows.single().progress)
    }

    @Test
    fun `star rating rewards speed and never drops below one`() {
        val level = TestLevels.strip(parRounds = 10)

        assertEquals(3, level.starsFor(rounds = 10))
        assertEquals(2, level.starsFor(rounds = 15))
        assertEquals(1, level.starsFor(rounds = 40))
        assertEquals("an unrated level always awards three", 3, TestLevels.strip().starsFor(999))
    }
}
