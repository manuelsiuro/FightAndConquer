package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.withBeacon
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withMonster
import com.msa.fightandconquer.core.TestStates.withTreasury
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The beacon upgrade ([GameAction.UpgradeBuilding]): legality, the reducer's
 * debit-and-flag, the flag's death with its building, the lit-hex derivation,
 * and the night behavior — lit ground spawns nothing, admits no monster, and
 * shields the units standing in it.
 */
class BeaconTest {

    /** Day-night on, but a long day: no nightfall interferes with day-side tests. */
    private val dayRules = RuleConstants(dayNightEnabled = true)

    /**
     * Night from round 1 through 3; nightfall spawns nothing by default, so the
     * only monsters on the board are the hand-placed ones under test.
     */
    private val nightRules = RuleConstants(
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

    /** Rounds 1 (nightfall) then 2 (the first monster action phase). */
    private fun playToFirstActionPhase(engine: GameEngine): List<GameEvent> {
        playRound(engine)
        return playRound(engine)
    }

    private fun reasonOf(state: GameState, action: GameAction): RejectionReason {
        val (next, events) = Reducer.reduce(state, action)
        assertEquals("a rejected action must not change state", state, next)
        return events.filterIsInstance<GameEvent.ActionRejected>().single().reason
    }

    // --- Legality ---

    @Test
    fun `lighting an own tower debits the cost, sets the flag and emits BeaconLit`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules).withBuilding(Building.TOWER, hex(2))
        val engine = GameEngine(s)
        assertEquals(LegalityResult.Ok, engine.submit(GameAction.UpgradeBuilding(hex(2))))
        val after = engine.state.value
        assertEquals(100 - dayRules.beaconCost, after.player(PlayerId(0)).treasury)
        assertTrue(after.tiles.getValue(hex(2)).beacon)
        val lit = engine.lastEvents.filterIsInstance<GameEvent.BeaconLit>().single()
        assertEquals(hex(2), lit.hex)
        assertEquals(dayRules.beaconCost, lit.cost)
        assertInvariants(after)
    }

    @Test
    fun `each of the four defense buildings accepts a beacon`() {
        for (building in listOf(
            Building.TOWER, Building.STRONG_TOWER, Building.FORTRESS, Building.WATCHTOWER,
        )) {
            val s = strip(9, 0..3, 6..8, rules = dayRules).withBuilding(building, hex(2))
            assertEquals(
                "beacon on $building",
                LegalityResult.Ok,
                Legality.check(s, GameAction.UpgradeBuilding(hex(2))),
            )
        }
    }

    @Test
    fun `a non-defense building refuses the beacon`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules).withBuilding(Building.FARM, hex(2))
        assertEquals(
            RejectionReason.BEACON_NOT_SUPPORTED,
            reasonOf(s, GameAction.UpgradeBuilding(hex(2))),
        )
    }

    @Test
    fun `the capital refuses the beacon`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules)
        assertEquals(
            RejectionReason.BEACON_NOT_SUPPORTED,
            reasonOf(s, GameAction.UpgradeBuilding(hex(0))),
        )
    }

    @Test
    fun `a day-night-disabled game refuses the beacon`() {
        val s = strip(9, 0..3, 6..8).withBuilding(Building.TOWER, hex(2))
        assertEquals(
            RejectionReason.BEACON_NOT_SUPPORTED,
            reasonOf(s, GameAction.UpgradeBuilding(hex(2))),
        )
    }

    @Test
    fun `an already-lit beacon refuses a second lighting`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules)
            .withBuilding(Building.TOWER, hex(2)).withBeacon(hex(2))
        assertEquals(
            RejectionReason.BEACON_ALREADY_LIT,
            reasonOf(s, GameAction.UpgradeBuilding(hex(2))),
        )
    }

    @Test
    fun `an empty hex and an enemy tower are refused with the standard codes`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules).withBuilding(Building.TOWER, hex(7))
        assertEquals(RejectionReason.NO_BUILDING_THERE, reasonOf(s, GameAction.UpgradeBuilding(hex(1))))
        assertEquals(RejectionReason.NOT_YOUR_HEX, reasonOf(s, GameAction.UpgradeBuilding(hex(7))))
    }

    @Test
    fun `an empty treasury cannot afford the beacon`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules)
            .withBuilding(Building.TOWER, hex(2)).withTreasury(0, 5)
        val rejected = Legality.check(s, GameAction.UpgradeBuilding(hex(2)))
        assertEquals(
            LegalityResult.Rejected(RejectionReason.CANNOT_AFFORD, dayRules.beaconCost),
            rejected,
        )
    }

    // --- The flag dies with its building ---

    @Test
    fun `capturing a lit tower razes the tower and its beacon`() {
        val s = strip(9, 0..4, 5..8, rules = dayRules)
            .withBuilding(Building.TOWER, hex(4)).withBeacon(hex(4))
            .withUnit(owner = 1, tier = 3, at = hex(5))
        val engine = GameEngine(s)
        check(engine.submit(GameAction.EndTurn) is LegalityResult.Ok) // P0 passes
        assertEquals(
            LegalityResult.Ok,
            engine.submit(GameAction.MoveUnit(s.tiles.getValue(hex(5)).unit!!, hex(4))),
        )
        val tile = engine.state.value.tiles.getValue(hex(4))
        assertEquals(PlayerId(1), tile.owner)
        assertEquals(null, tile.building)
        assertEquals(false, tile.beacon)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `demolishing a lit tower clears the beacon and refunds the tower only`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules)
            .withBuilding(Building.TOWER, hex(2)).withBeacon(hex(2))
        val engine = GameEngine(s)
        assertEquals(LegalityResult.Ok, engine.submit(GameAction.DemolishBuilding(hex(2))))
        val after = engine.state.value
        assertEquals(false, after.tiles.getValue(hex(2)).beacon)
        // The beacon investment is sunk: the refund prices the tower alone.
        val expected = dayRules.towerCost * dayRules.demolishRefundPercent / 100
        assertEquals(100 + expected, after.player(PlayerId(0)).treasury)
        assertInvariants(after)
    }

    @Test
    fun `surrender razes the quitter's buildings and their beacons`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules)
            .withBuilding(Building.TOWER, hex(2)).withBeacon(hex(2))
        val engine = GameEngine(s)
        assertEquals(LegalityResult.Ok, engine.submit(GameAction.Surrender))
        val tile = engine.state.value.tiles.getValue(hex(2))
        assertEquals(null, tile.building)
        assertEquals(false, tile.beacon)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `capital relocation onto a lit tower razes the tower, its beacon and emits the destruction`() {
        // P1's only remaining region is the single lit-tower hex — the
        // relocation ladder's last-resort fallback must overwrite it cleanly.
        val s = TestStates.custom(
            owners = mapOf(
                hex(0) to 0, hex(1) to 0, hex(2) to 0, hex(3) to 0,
                hex(4) to 1, hex(5) to null, hex(6) to 1,
            ),
            capital0 = hex(0),
            capital1 = hex(4),
            rules = dayRules,
        )
            .withBuilding(Building.TOWER, hex(6)).withBeacon(hex(6))
            .withUnit(owner = 0, tier = 4, at = hex(3))
        val engine = GameEngine(s)
        assertEquals(
            LegalityResult.Ok,
            engine.submit(GameAction.MoveUnit(s.tiles.getValue(hex(3)).unit!!, hex(4))),
        )
        val after = engine.state.value
        assertEquals(hex(6), after.player(PlayerId(1)).capital)
        val tile = after.tiles.getValue(hex(6))
        assertEquals(Building.CAPITAL, tile.building)
        assertEquals(false, tile.beacon)
        assertTrue(
            "the overwritten tower's destruction is announced",
            engine.lastEvents.any {
                it is GameEvent.BuildingDestroyed && it.hex == hex(6) && it.building == Building.TOWER
            },
        )
        assertInvariants(after)
    }

    // --- Lit-hex derivation ---

    @Test
    fun `a tower beacon lights radius 1 and a fortress beacon radius 2`() {
        val tower = strip(9, 0..3, 6..8, rules = dayRules)
            .withBuilding(Building.TOWER, hex(2)).withBeacon(hex(2))
        assertEquals(setOf(hex(1), hex(2), hex(3)), Rules.litHexes(tower))
        val fortress = strip(9, 0..4, 6..8, rules = dayRules)
            .withBuilding(Building.FORTRESS, hex(4)).withBeacon(hex(4))
        assertEquals(setOf(hex(2), hex(3), hex(4), hex(5), hex(6)), Rules.litHexes(fortress))
    }

    @Test
    fun `an unlit tower lights nothing`() {
        val s = strip(9, 0..3, 6..8, rules = dayRules).withBuilding(Building.TOWER, hex(2))
        assertEquals(emptySet<com.msa.fightandconquer.core.hex.Hex>(), Rules.litHexes(s))
    }

    // --- Night behavior ---

    @Test
    fun `nightfall never spawns a monster on lit ground`() {
        val rules = nightRules.copy(monsterSpawnPer100Hexes = 100, monsterSpawnCap = 10)
        val s = strip(9, 0..3, 6..8, rules = rules)
            .withBuilding(Building.TOWER, hex(2)).withBeacon(hex(2))
        val engine = GameEngine(s)
        val events = playRound(engine) // nightfall
        assertTrue(events.any { it is GameEvent.NightFell })
        val spawned = events.filterIsInstance<GameEvent.MonsterSpawned>().map { it.hex }
        assertTrue("spawn wave lands somewhere", spawned.isNotEmpty())
        val lit = Rules.litHexes(engine.state.value)
        assertTrue("no spawn in the light: $spawned", spawned.none { it in lit })
        assertInvariants(engine.state.value)
    }

    @Test
    fun `a unit in lit ground survives a monster that out-attacks it`() {
        // Watchtower: defense 0, so only the beacon shields the peasant.
        val unshielded = strip(9, 0..3, 6..8, rules = nightRules)
            .withBuilding(Building.WATCHTOWER, hex(2))
            .withUnit(owner = 0, tier = 1, at = hex(3))
            .withMonster(hex(4), tier = 1)
        val control = GameEngine(unshielded)
        val controlEvents = playToFirstActionPhase(control)
        assertEquals(
            "control: the naked peasant falls",
            1,
            controlEvents.count { it is GameEvent.MonsterAttacked },
        )

        val shielded = GameEngine(unshielded.withBeacon(hex(2)))
        val events = playToFirstActionPhase(shielded)
        assertTrue("no strike into the light", events.none { it is GameEvent.MonsterAttacked })
        assertTrue(events.none { it is GameEvent.UnitDied })
        assertTrue(shielded.state.value.tiles.getValue(hex(3)).unit != null)
        assertInvariants(shielded.state.value)
    }

    @Test
    fun `lit territory does not lure the prowl`() {
        // Monster between two realms, equidistant: without light it stalks the
        // packed-smaller P0 fence; with P0's fence lit it turns toward P1.
        val s = strip(9, 0..2, 6..8, rules = nightRules).withMonster(hex(4), tier = 1)
        val control = GameEngine(s.withBuilding(Building.TOWER, hex(1)))
        val controlMove = playToFirstActionPhase(control)
            .filterIsInstance<GameEvent.MonsterMoved>().single()
        assertEquals(hex(3), controlMove.to)

        val litEngine = GameEngine(s.withBuilding(Building.TOWER, hex(1)).withBeacon(hex(1)))
        val move = playToFirstActionPhase(litEngine)
            .filterIsInstance<GameEvent.MonsterMoved>().single()
        assertEquals("the prowl turns away from the light", hex(5), move.to)
        assertInvariants(litEngine.state.value)
    }

    @Test
    fun `a monster caught in fresh light walks out and cannot re-enter`() {
        // Beacon at hex(2) lights 1..3; the monster stands at 3 when night deepens.
        val s = strip(9, 0..2, 6..8, rules = nightRules)
            .withBuilding(Building.WATCHTOWER, hex(2)).withBeacon(hex(2))
            .withMonster(hex(3), tier = 1)
        val engine = GameEngine(s)
        val move = playToFirstActionPhase(engine)
            .filterIsInstance<GameEvent.MonsterMoved>().single()
        assertEquals(hex(3), move.from)
        assertEquals("steps out of the light, away from it", hex(4), move.to)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `a monster with every exit lit is driven out entirely`() {
        // Fortress at hex(2) lights 0..4; the monster at 3 stands walled
        // between the fortress hex and lit ground — no strike, no exit — so
        // the light despawns it instead of freezing it on safe-painted ground.
        val s = strip(9, 0..2, 6..8, rules = nightRules)
            .withBuilding(Building.FORTRESS, hex(2)).withBeacon(hex(2))
            .withMonster(hex(3), tier = 1)
        val engine = GameEngine(s)
        val events = playToFirstActionPhase(engine)
        assertTrue(
            "the enclosed monster despawns",
            events.any { it is GameEvent.MonsterDespawned && it.hex == hex(3) },
        )
        assertEquals(null, engine.state.value.tiles.getValue(hex(3)).monster)
        assertInvariants(engine.state.value)
    }

    @Test
    fun `same seed with a mid-game beacon replays byte-identically`() {
        val rules = nightRules.copy(monsterSpawnPer100Hexes = 100, monsterSpawnCap = 4)
        fun play(): GameState {
            val s = strip(9, 0..3, 6..8, rules = rules).withBuilding(Building.TOWER, hex(2))
            val engine = GameEngine(s)
            check(engine.submit(GameAction.UpgradeBuilding(hex(2))) is LegalityResult.Ok)
            repeat(4) { playRound(engine) }
            return engine.state.value
        }
        assertEquals(play(), play())
    }
}
