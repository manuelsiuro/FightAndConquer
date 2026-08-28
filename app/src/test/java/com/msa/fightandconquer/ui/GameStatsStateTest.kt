package com.msa.fightandconquer.ui

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.Tile
import com.msa.fightandconquer.core.record.KeyMoment
import com.msa.fightandconquer.core.record.MatchKind
import com.msa.fightandconquer.core.record.MatchMeta
import com.msa.fightandconquer.core.record.MatchRecorderState
import com.msa.fightandconquer.core.record.SeatDescriptor
import com.msa.fightandconquer.core.record.SeatSeries
import com.msa.fightandconquer.core.record.SeatTotals
import com.msa.fightandconquer.core.model.Civilization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure record+state -> war-report mapping ([buildGameStats]). */
class GameStatsStateTest {

    private val me = PlayerId(0)
    private val foe = PlayerId(1)

    private fun state(myHexes: Int = 4, treasury: Int = 57, turnNumber: Int = 6): GameState {
        val tiles = HashMap<Hex, Tile>()
        tiles[Hex.of(0, 0)] = Tile(owner = me, building = Building.CAPITAL)
        repeat(myHexes - 1) { tiles[Hex.of(it + 1, 0)] = Tile(owner = me) }
        tiles[Hex.of(0, 1)] = Tile(owner = foe, building = Building.CAPITAL)
        return GameState(
            config = GameConfig(seed = 1L),
            tiles = tiles,
            units = emptyMap(),
            players = listOf(
                PlayerState(me, PlayerKind.Human, treasury, Hex.of(0, 0)),
                PlayerState(foe, PlayerKind.Human, 100, Hex.of(0, 1)),
            ),
            currentPlayer = me,
            turnNumber = turnNumber,
            rngState = 1L,
        )
    }

    private fun record(
        series: SeatSeries = SeatSeries(
            rounds = listOf(0, 1, 2),
            hexes = listOf(3, 4, 4),
            income = listOf(3, 4, 4),
            upkeep = listOf(0, 0, 2),
            treasury = listOf(100, 103, 105),
            units = listOf(0, 1, 1),
            strength = listOf(0, 1, 1),
        ),
        moments: List<KeyMoment> = emptyList(),
        secondSeatHuman: Boolean = true,
    ) = MatchRecorderState(
        meta = MatchMeta(kind = MatchKind.SKIRMISH_VS_AI, seed = 1L, landHexes = 20, fogOfWar = false),
        seats = listOf(
            SeatDescriptor(isHuman = true, civ = Civilization.KINGDOM),
            SeatDescriptor(isHuman = secondSeatHuman, civ = Civilization.VIKINGS),
        ),
        series = listOf(series, SeatSeries(rounds = listOf(0), hexes = listOf(1))),
        moments = moments,
        totals = listOf(SeatTotals(unitsKilled = 2), SeatTotals()),
    )

    @Test
    fun `the report carries only the viewer's series and totals`() {
        val stats = buildGameStats(record(), state(), me)
        assertEquals(0, stats.viewerSeat)
        assertEquals(listOf(0, 1, 2), stats.rounds)
        assertEquals(listOf(3, 4, 4), stats.territory)
        assertEquals(listOf(100, 103, 105), stats.treasury)
        assertEquals(2, stats.totals.unitsKilled)
        assertEquals(20, stats.landHexes)
    }

    @Test
    fun `now reflects the live board, not the last sample`() {
        val stats = buildGameStats(record(), state(myHexes = 4, treasury = 57), me)
        assertEquals(4, stats.now.hexes)
        assertEquals(20, stats.now.territoryPercent) // 4 of 20 land hexes
        assertEquals(57, stats.now.treasury)
        assertEquals(0, stats.now.units)
    }

    @Test
    fun `a pre-strength chronicle tail-aligns and hides nothing else`() {
        val legacy = record(
            series = SeatSeries(
                rounds = listOf(0, 1, 2),
                hexes = listOf(3, 4, 4),
                income = listOf(3, 4, 4),
                upkeep = listOf(0, 0, 2),
                treasury = listOf(100, 103, 105),
                units = listOf(0, 1, 1),
                strength = listOf(7), // resumed save: sampling began after the field landed
            ),
        )
        val stats = buildGameStats(legacy, state(), me)
        assertEquals(listOf(2), stats.strengthRounds)
        assertEquals(listOf(7), stats.strength)
        assertEquals(listOf(0, 1, 2), stats.rounds)
    }

    @Test
    fun `seat kinds ride along for the sheet's naming`() {
        val stats = buildGameStats(record(secondSeatHuman = false), state(), me)
        assertEquals(listOf(true, false), stats.seatIsHuman)
    }

    @Test
    fun `moments are the viewer's own, newest first, capped at six`() {
        val moments = buildList {
            add(KeyMoment.ShipSunk(round = 0, owner = 1, by = 0)) // viewer acted
            add(KeyMoment.WentBankrupt(round = 1, seat = 1)) // not the viewer's story
            add(KeyMoment.CapitalLooted(round = 2, by = 1, victim = 0, loot = 12)) // suffered
            repeat(6) { add(KeyMoment.Breakthrough(round = 3 + it, seat = 0, tech = com.msa.fightandconquer.core.model.Tech.COINAGE)) }
        }
        val stats = buildGameStats(record(moments = moments), state(), me)
        assertEquals(6, stats.moments.size)
        assertTrue(stats.moments.none { it is KeyMoment.WentBankrupt })
        // Newest first: the last breakthrough leads, the ship sinking fell off the cap.
        assertEquals(8, stats.moments.first().round)
        assertTrue(stats.moments.none { it is KeyMoment.ShipSunk })
    }
}
