package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withMonster
import com.msa.fightandconquer.core.TestStates.withSea
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monster action phase (night-interior rounds): strikes priced by the full
 * defense model, the deterministic prowl, pillage income suppression, and the
 * phase's no-capture/no-raze guarantee.
 */
class MonsterPhaseTest {

    /**
     * Night from round 1 through 3; nightfall spawns nothing, so the only
     * monsters on the board are the hand-placed ones under test.
     */
    private val rules = RuleConstants(
        dayNightEnabled = true,
        dayLengthRounds = 1,
        nightLengthRounds = 3,
        monsterSpawnPer100Hexes = 0,
        monsterDawnCachePercent = 0,
        monsterHoardPercent = 0,
    )

    /** Every living seat ends its turn; returns the wrap transition's events. */
    private fun playRound(engine: GameEngine): List<GameEvent> {
        repeat(engine.state.value.players.count { !it.eliminated }) {
            check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        }
        return engine.lastEvents
    }

    /** Rounds 1 (nightfall, no spawns) then 2 (the first action phase). */
    private fun playToFirstActionPhase(engine: GameEngine): List<GameEvent> {
        playRound(engine)
        return playRound(engine)
    }

    @Test
    fun `a naked peasant within range is struck and the hex stays its owner's`() {
        val s = strip(9, 0..3, 6..8, rules = rules)
            .withUnit(owner = 0, tier = 1, at = hex(3))
            .withMonster(hex(4), tier = 1)
        val engine = GameEngine(s)
        val events = playToFirstActionPhase(engine)
        assertEquals(1, events.count { it is GameEvent.MonsterAttacked })
        val died = events.filterIsInstance<GameEvent.UnitDied>().single()
        assertEquals(hex(3), died.hex)
        assertEquals(DeathCause.KILLED, died.cause)
        val moved = events.filterIsInstance<GameEvent.MonsterMoved>().single()
        assertEquals(hex(4), moved.from)
        assertEquals(hex(3), moved.to)
        val after = engine.state.value
        // The strike is a raid, not a conquest: ownership never changes.
        assertEquals(PlayerId(0), after.tiles.getValue(hex(3)).owner)
        assertTrue(after.tiles.getValue(hex(3)).monster != null)
        assertEquals(null, after.tiles.getValue(hex(3)).unit)
        assertTrue(events.none { it is GameEvent.HexCaptured })
        assertInvariants(after)
    }

    @Test
    fun `a tower-covered peasant survives the night`() {
        val s = strip(9, 0..3, 6..8, rules = rules)
            .withBuilding(Building.TOWER, hex(2))
            .withUnit(owner = 0, tier = 1, at = hex(3))
            .withMonster(hex(4), tier = 1)
        val engine = GameEngine(s)
        val events = playToFirstActionPhase(engine)
        assertTrue(events.none { it is GameEvent.MonsterAttacked })
        assertTrue(events.none { it is GameEvent.UnitDied })
        // The monster is already stalking the fence line: it holds.
        assertTrue(events.none { it is GameEvent.MonsterMoved })
        assertTrue(engine.state.value.tiles.getValue(hex(3)).unit != null)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `the strike range is the monster move range`() {
        // The peasant stands 3 passable steps away — beyond monsterMoveRange 2 —
        // with nothing adjacent to lure a prowl closer... except the unit itself,
        // so the monster prowls one step instead of striking.
        val far = strip(15, 0..2, 12..14, rules = rules)
            .withUnit(owner = 0, tier = 1, at = hex(2))
            .withMonster(hex(6), tier = 1)
        val engine = GameEngine(far)
        val events = playToFirstActionPhase(engine)
        assertTrue(events.none { it is GameEvent.MonsterAttacked })
        val moved = events.filterIsInstance<GameEvent.MonsterMoved>().single()
        assertEquals(hex(6), moved.from)
        assertEquals(hex(5), moved.to)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `a monster far from everything prowls toward the nearest territory`() {
        val wilds = strip(15, 0..2, 12..14, rules = rules).withMonster(hex(7), tier = 1)
        val engine = GameEngine(wilds)
        val events = playToFirstActionPhase(engine)
        val moved = events.filterIsInstance<GameEvent.MonsterMoved>().single()
        assertEquals(hex(7), moved.from)
        // Both borders lure at equal distance; the packed tie-break walks it left.
        assertEquals(hex(6), moved.to)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `a squatted hex earns nothing until the monster is gone`() {
        val quiet = strip(9, 0..3, 6..8, rules = rules)
        val squatted = quiet.withMonster(hex(2), tier = 1)
        assertEquals(
            Rules.incomeOf(quiet, PlayerId(0)) - rules.hexIncome,
            Rules.incomeOf(squatted, PlayerId(0)),
        )
    }

    @Test
    fun `a warship shells a coastal monster and its cache waits ashore`() {
        val coast = strip(9, 0..2, 6..8, rules = rules)
            .withSea(hex(4))
            .withUnit(owner = 0, tier = 1, at = hex(4), type = UnitType.WARSHIP)
            .withMonster(hex(3), tier = 1)
        val engine = GameEngine(coast)
        check(engine.submit(GameAction.Bombard(coast.unitIdAt(hex(4)), hex(3))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(null, after.tiles.getValue(hex(3)).monster)
        assertEquals(rules.monsterCachePerTier, after.tiles.getValue(hex(3)).cache)
        assertEquals(1, engine.lastEvents.count { it is GameEvent.MonsterSlain })
        assertTrue(engine.lastEvents.none { it is GameEvent.CacheCollected })
        assertInvariants(after)
    }

    @Test
    fun `a strong monster hex refuses the outgunned warship`() {
        val coast = strip(9, 0..2, 6..8, rules = rules)
            .withSea(hex(4))
            .withUnit(owner = 0, tier = 1, at = hex(4), type = UnitType.WARSHIP)
            .withMonster(hex(3), tier = 2)
        val result = GameEngine(coast).submit(GameAction.Bombard(coast.unitIdAt(hex(4)), hex(3)))
        assertEquals(RejectionReason.DEFENSE_TOO_HIGH, (result as LegalityResult.Rejected).reason)
    }

    @Test
    fun `the night phase seals behind the end turn - no undo into it`() {
        val engine = GameEngine(
            strip(9, 0..3, 6..8, rules = rules)
                .withUnit(owner = 0, tier = 1, at = hex(3))
                .withMonster(hex(4), tier = 1),
        )
        playToFirstActionPhase(engine)
        assertTrue(!engine.canUndo())
        assertTrue(!engine.undo())
    }

    @Test
    fun `a full night is deterministic and survives a save round-trip`() {
        val night = rules.copy(monsterSpawnPer100Hexes = 30, monsterDawnCachePercent = 25)
        fun play(): GameState {
            val engine = GameEngine(strip(15, 0..3, 11..14, rules = night, seed = 5L))
            repeat(5) { playRound(engine) } // nightfall, two action phases, dawn, day
            return engine.state.value
        }
        assertEquals(play(), play())
        val engine = GameEngine(strip(15, 0..3, 11..14, rules = night, seed = 5L))
        repeat(3) { playRound(engine) } // mid-night, monsters afoot
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        val codec = com.msa.fightandconquer.core.persist.SaveCodec
        assertEquals(engine.state.value, codec.restore(codec.decode(codec.encode(engine.toSave()))))
        assertInvariants(engine.state.value)
    }
}
