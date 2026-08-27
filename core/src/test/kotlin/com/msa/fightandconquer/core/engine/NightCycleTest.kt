package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day-night cycle: the phase formula, the once-per-ROUND tick at the seat
 * wrap (never per seat), the deterministic spawn wave, and the dawn despawn.
 */
class NightCycleTest {

    /** Fast cadence for tests: night on every odd round. */
    private val fastNight = RuleConstants(
        dayNightEnabled = true,
        dayLengthRounds = 1,
        nightLengthRounds = 1,
        monsterCapitalStandoff = 2,
        monsterDawnCachePercent = 0,
        monsterHoardPercent = 0,
    )

    private fun nightEvents(events: List<GameEvent>) = events.filter {
        it is GameEvent.NightFell || it is GameEvent.DawnBroke ||
            it is GameEvent.MonsterSpawned || it is GameEvent.MonsterDespawned ||
            it is GameEvent.CacheDropped
    }

    /** Runs one full round (every living seat ends its turn); returns the wrap transition's events. */
    private fun playRound(engine: GameEngine): List<GameEvent> {
        val seats = engine.state.value.players.count { !it.eliminated }
        repeat(seats - 1) { check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) }
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        return engine.lastEvents
    }

    // ----- the phase formula -----

    @Test
    fun `isNight follows the configured cadence`() {
        val rules = RuleConstants(dayNightEnabled = true, dayLengthRounds = 8, nightLengthRounds = 3)
        for (round in 0..7) assertTrue("round $round is day", !Rules.isNight(round, rules))
        for (round in 8..10) assertTrue("round $round is night", Rules.isNight(round, rules))
        for (round in 11..18) assertTrue("round $round is day", !Rules.isNight(round, rules))
        for (round in 19..21) assertTrue("round $round is night", Rules.isNight(round, rules))
    }

    @Test
    fun `flag off means never night`() {
        val rules = RuleConstants(dayNightEnabled = false, dayLengthRounds = 1, nightLengthRounds = 1)
        for (round in 0..10) assertTrue(!Rules.isNight(round, rules))
        assertEquals(null, Rules.roundsUntilNight(0, rules))
    }

    @Test
    fun `countdowns bracket the nightfall`() {
        val rules = RuleConstants(dayNightEnabled = true, dayLengthRounds = 8, nightLengthRounds = 3)
        assertEquals(8, Rules.roundsUntilNight(0, rules))
        assertEquals(1, Rules.roundsUntilNight(7, rules))
        assertEquals(null, Rules.roundsUntilNight(8, rules))
        assertEquals(3, Rules.roundsUntilDawn(8, rules))
        assertEquals(1, Rules.roundsUntilDawn(10, rules))
        assertEquals(null, Rules.roundsUntilDawn(11, rules))
        assertEquals(8, Rules.roundsUntilNight(11, rules))
    }

    @Test
    fun `monster tier ramps by nights and caps`() {
        val rules = RuleConstants(
            dayNightEnabled = true, dayLengthRounds = 8, nightLengthRounds = 3,
            monsterBaseTier = 1, monsterTierRampNights = 2, monsterMaxTier = 3,
        )
        assertEquals(1, Rules.monsterTierAt(8, rules)) // night 0
        assertEquals(1, Rules.monsterTierAt(19, rules)) // night 1
        assertEquals(2, Rules.monsterTierAt(30, rules)) // night 2
        assertEquals(2, Rules.monsterTierAt(41, rules)) // night 3
        assertEquals(3, Rules.monsterTierAt(52, rules)) // night 4
        assertEquals(3, Rules.monsterTierAt(96, rules)) // capped forever after
    }

    // ----- the pipeline tick -----

    @Test
    fun `night events fire exactly at the wrap and only there`() {
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = fastNight))
        // P0 ends: no wrap yet, still round 0 — no night events.
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        assertTrue(nightEvents(engine.lastEvents).isEmpty())
        // P1 ends: wrap to round 1 = night.
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
        val events = engine.lastEvents
        assertEquals(1, events.count { it is GameEvent.NightFell })
        assertEquals(1, events.count { it is GameEvent.MonsterSpawned })
        assertEquals(1, engine.state.value.tiles.values.count { it.monster != null })
        assertInvariants(engine.state.value)
    }

    @Test
    fun `flag off emits no night events ever`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        repeat(6) {
            check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok)
            assertTrue(nightEvents(engine.lastEvents).isEmpty())
        }
        assertTrue(engine.state.value.tiles.values.all { it.monster == null && it.cache == null })
    }

    @Test
    fun `a three player round ticks the cycle once, at the last seat`() {
        val tiles = HashMap<com.msa.fightandconquer.core.hex.Hex, Tile>()
        for (q in 0 until 12) {
            val owner = when (q) {
                in 0..2 -> PlayerId(0)
                in 5..6 -> PlayerId(1)
                in 9..11 -> PlayerId(2)
                else -> null
            }
            tiles[hex(q)] = Tile(owner = owner)
        }
        for ((seat, cap) in listOf(0 to hex(0), 1 to hex(5), 2 to hex(11))) {
            tiles[cap] = tiles.getValue(cap).copy(building = com.msa.fightandconquer.core.model.Building.CAPITAL)
        }
        val state = GameState(
            config = GameConfig(seed = 7L, rules = fastNight),
            tiles = tiles,
            units = emptyMap(),
            players = listOf(
                PlayerState(PlayerId(0), PlayerKind.Human, 100, hex(0)),
                PlayerState(PlayerId(1), PlayerKind.Human, 100, hex(5)),
                PlayerState(PlayerId(2), PlayerKind.Human, 100, hex(11)),
            ),
            currentPlayer = PlayerId(0),
            rngState = 7L,
        )
        val engine = GameEngine(state)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) // P0 -> P1
        assertTrue(nightEvents(engine.lastEvents).isEmpty())
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) // P1 -> P2
        assertTrue(nightEvents(engine.lastEvents).isEmpty())
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) // P2 -> P0, wrap
        assertEquals(1, engine.lastEvents.count { it is GameEvent.NightFell })
        assertInvariants(engine.state.value)
    }

    @Test
    fun `dawn removes every monster`() {
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = fastNight))
        playRound(engine) // wrap to round 1: night, spawn
        assertEquals(1, engine.state.value.tiles.values.count { it.monster != null })
        val dawnEvents = playRound(engine) // wrap to round 2: dawn
        assertEquals(1, dawnEvents.count { it is GameEvent.DawnBroke })
        assertEquals(1, dawnEvents.count { it is GameEvent.MonsterDespawned })
        assertTrue(engine.state.value.tiles.values.all { it.monster == null })
        assertInvariants(engine.state.value)
    }

    @Test
    fun `a dawn survivor can leave a half-value cache`() {
        val always = fastNight.copy(monsterDawnCachePercent = 100)
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = always))
        playRound(engine)
        val monsterHex = engine.state.value.tiles.entries.single { it.value.monster != null }
        val tier = monsterHex.value.monster!!.tier
        val dawnEvents = playRound(engine)
        val drop = dawnEvents.filterIsInstance<GameEvent.CacheDropped>().single()
        assertEquals(monsterHex.key, drop.hex)
        assertEquals((tier * always.monsterCachePerTier / 2).coerceAtLeast(1), drop.gold)
        assertEquals(drop.gold, engine.state.value.tiles.getValue(monsterHex.key).cache)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `zero dawn chance leaves nothing behind`() {
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = fastNight))
        playRound(engine)
        val dawnEvents = playRound(engine)
        assertTrue(dawnEvents.none { it is GameEvent.CacheDropped })
        assertTrue(engine.state.value.tiles.values.all { it.cache == null })
    }

    // ----- spawn placement -----

    @Test
    fun `spawns respect the capital standoff`() {
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = fastNight))
        playRound(engine)
        val monsterHex = engine.state.value.tiles.entries.single { it.value.monster != null }.key
        val caps = engine.state.value.players.mapNotNull { it.capital }
        assertTrue(
            "spawn $monsterHex too close to a capital",
            caps.all { com.msa.fightandconquer.core.hex.HexMath.distance(monsterHex, it) >= 2 },
        )
    }

    @Test
    fun `an impossible standoff spawns nothing but the night still falls`() {
        val walled = fastNight.copy(monsterCapitalStandoff = 10)
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = walled))
        val events = playRound(engine)
        assertEquals(1, events.count { it is GameEvent.NightFell })
        assertTrue(events.none { it is GameEvent.MonsterSpawned })
        assertTrue(engine.state.value.tiles.values.all { it.monster == null })
    }

    @Test
    fun `zero spawn rate disables the wave entirely`() {
        val quiet = fastNight.copy(monsterSpawnPer100Hexes = 0)
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = quiet))
        val events = playRound(engine)
        assertEquals(1, events.count { it is GameEvent.NightFell })
        assertTrue(events.none { it is GameEvent.MonsterSpawned })
    }

    @Test
    fun `spawned kinds match the wave's tier band`() {
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = fastNight))
        playRound(engine)
        val monster = engine.state.value.tiles.values.mapNotNull { it.monster }.single()
        assertTrue(monster.kind in com.msa.fightandconquer.core.model.MonsterKind.forTier(monster.tier))
        assertEquals(Rules.monsterTierAt(1, fastNight), monster.tier)
        assertEquals(1, monster.spawnedRound)
    }

    // ----- determinism -----

    @Test
    fun `the same seed spawns the same wave`() {
        fun playTwoRounds(): GameState {
            val engine = GameEngine(strip(9, 0..2, 6..8, rules = fastNight, seed = 99L))
            playRound(engine)
            return engine.state.value
        }
        assertEquals(playTwoRounds(), playTwoRounds())
    }

    @Test
    fun `a mid-night save round-trips through JSON and replays to the identical state`() {
        val engine = GameEngine(strip(9, 0..2, 6..8, rules = fastNight))
        playRound(engine) // night, monster on the board
        val codec = com.msa.fightandconquer.core.persist.SaveCodec
        val decoded = codec.decode(codec.encode(engine.toSave()))
        val restored = codec.restore(decoded)
        assertEquals(engine.state.value, restored)
        assertTrue(restored.tiles.values.any { it.monster != null })
    }
}
