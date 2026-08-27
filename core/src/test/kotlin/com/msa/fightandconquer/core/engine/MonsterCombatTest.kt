package com.msa.fightandconquer.core.engine

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.TestStates.unitIdAt
import com.msa.fightandconquer.core.TestStates.withBuilding
import com.msa.fightandconquer.core.TestStates.withCache
import com.msa.fightandconquer.core.TestStates.withDeposit
import com.msa.fightandconquer.core.TestStates.withMonster
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slaying monsters and scooping their caches: the strictly-greater rule against
 * monster defense through the shared choke points ([Rules.defenseOf],
 * [Rules.reachable]), the cache drop/collect lifecycle, and the placement
 * rejections a squatter causes.
 */
class MonsterCombatTest {

    /** Day-night on but far away (no cycle interference); no hoard RNG unless a test opts in. */
    private val rules = RuleConstants(
        dayNightEnabled = true,
        dayLengthRounds = 50,
        nightLengthRounds = 3,
        monsterHoardPercent = 0,
        monsterDawnCachePercent = 0,
    )

    private val base = strip(9, 0..3, 6..8, rules = rules)

    private fun rejected(result: LegalityResult): RejectionReason =
        (result as LegalityResult.Rejected).reason

    // ----- reach & defense -----

    @Test
    fun `a monster on an own hex blocks traversal and reads as a capture target when beatable`() {
        val s = base
            .withMonster(hex(2), tier = 1)
            .withUnit(owner = 0, tier = 2, at = hex(1))
        val reach = Rules.reachable(s, s.unitIdAt(hex(1)))
        assertTrue(hex(2) in reach.captureTargets)
        assertTrue(hex(2) !in reach.moveTargets)
        // The strip is one row: the monster at hex 2 walls off everything beyond.
        assertTrue(hex(3) !in reach.moveTargets)
    }

    @Test
    fun `an equal-tier monster blocks the attack`() {
        val s = base
            .withMonster(hex(2), tier = 1)
            .withUnit(owner = 0, tier = 1, at = hex(1))
        val reach = Rules.reachable(s, s.unitIdAt(hex(1)))
        assertTrue(hex(2) in reach.blockedTargets)
        assertTrue(hex(2) !in reach.captureTargets)
        assertEquals(
            RejectionReason.DESTINATION_UNREACHABLE,
            rejected(GameEngine(s).submit(GameAction.MoveUnit(s.unitIdAt(hex(1)), hex(2)))),
        )
    }

    @Test
    fun `a neutral monster hex defends at its tier`() {
        val s = base.withMonster(hex(4), tier = 2)
        assertEquals(2, Rules.defenseOf(s, hex(4)))
        assertEquals(3, Rules.captureRequirement(s, hex(4)))
        val source = Rules.defenseSourceOf(s, hex(4))
        assertTrue(source is Rules.DefenseSource.Monster)
    }

    @Test
    fun `siege never zeroes a monster - a creature is not a building`() {
        val s = base.withMonster(hex(4), tier = 2)
        assertEquals(2, Rules.defenseOf(s, hex(4), UnitType.CATAPULT))
    }

    @Test
    fun `enemy fortifications still cover a monster hex on enemy ground`() {
        // P1's tower at hex 7 covers hex 6; the monster on 6 defends at max(tower, tier).
        val s = strip(9, 0..3, 6..8, rules = rules)
            .withBuilding(Building.TOWER, hex(7))
            .withMonster(hex(6), tier = 1)
        assertEquals(rules.towerDefense, Rules.defenseOf(s, hex(6)))
    }

    @Test
    fun `own fortifications never shield a monster against its own landlord`() {
        // P0's own tower covers hex 2, but attacking the squatter there from own
        // ground is judged against the monster alone (reachable's own-tile arm).
        val s = base
            .withBuilding(Building.TOWER, hex(3))
            .withMonster(hex(2), tier = 1)
            .withUnit(owner = 0, tier = 2, at = hex(1))
        assertTrue(hex(2) in Rules.reachable(s, s.unitIdAt(hex(1))).captureTargets)
    }

    // ----- slaying -----

    @Test
    fun `slaying on an own hex pays the cache and keeps ownership semantics clean`() {
        val s = base
            .withMonster(hex(2), tier = 1)
            .withUnit(owner = 0, tier = 2, at = hex(1))
        val engine = GameEngine(s)
        val before = s.player(PlayerId(0)).treasury
        check(engine.submit(GameAction.MoveUnit(s.unitIdAt(hex(1)), hex(2))) is LegalityResult.Ok)
        val after = engine.state.value
        val events = engine.lastEvents
        assertEquals(null, after.tiles.getValue(hex(2)).monster)
        assertEquals(null, after.tiles.getValue(hex(2)).cache)
        assertEquals(before + rules.monsterCachePerTier, after.player(PlayerId(0)).treasury)
        assertEquals(1, events.count { it is GameEvent.MonsterSlain })
        assertEquals(1, events.count { it is GameEvent.CacheDropped })
        assertEquals(1, events.count { it is GameEvent.CacheCollected })
        // An own-tile strike is not a conquest.
        assertTrue(events.none { it is GameEvent.HexCaptured })
        assertInvariants(after)
    }

    @Test
    fun `storming a neutral monster hex is a capture plus a slay`() {
        val s = base
            .withMonster(hex(4), tier = 1)
            .withUnit(owner = 0, tier = 2, at = hex(3))
        val engine = GameEngine(s)
        val before = s.player(PlayerId(0)).treasury
        check(engine.submit(GameAction.MoveUnit(s.unitIdAt(hex(3)), hex(4))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(PlayerId(0), after.tiles.getValue(hex(4)).owner)
        assertEquals(null, after.tiles.getValue(hex(4)).monster)
        assertEquals(before + rules.monsterCachePerTier, after.player(PlayerId(0)).treasury)
        assertEquals(1, engine.lastEvents.count { it is GameEvent.HexCaptured })
        assertEquals(1, engine.lastEvents.count { it is GameEvent.MonsterSlain })
        assertInvariants(after)
    }

    @Test
    fun `buy-capture onto a monster hex slays and scoops in one purchase`() {
        // The hall satisfies the muster gate so the tier-2 purchase is the story.
        val s = base
            .withBuilding(Building.BARRACKS, hex(1))
            .withMonster(hex(4), tier = 1)
        val engine = GameEngine(s)
        val before = s.player(PlayerId(0)).treasury
        check(engine.submit(GameAction.BuyUnit(2, hex(4))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(null, after.tiles.getValue(hex(4)).monster)
        assertEquals(
            before - rules.unitCost[1] + rules.monsterCachePerTier,
            after.player(PlayerId(0)).treasury,
        )
        assertInvariants(after)
    }

    @Test
    fun `a tier-scaled cache pays per tier`() {
        val s = base
            .withMonster(hex(4), tier = 3)
            .withUnit(owner = 0, tier = 4, at = hex(3))
        val engine = GameEngine(s)
        check(engine.submit(GameAction.MoveUnit(s.unitIdAt(hex(3)), hex(4))) is LegalityResult.Ok)
        val drop = engine.lastEvents.filterIsInstance<GameEvent.CacheDropped>().single()
        assertEquals(3 * rules.monsterCachePerTier, drop.gold)
    }

    @Test
    fun `the hoard can turn the ground fertile but never overwrites a deposit`() {
        val hoard = rules.copy(monsterHoardPercent = 100)
        val s = strip(9, 0..3, 6..8, rules = hoard)
            .withMonster(hex(2), tier = 1)
            .withUnit(owner = 0, tier = 2, at = hex(1))
        val engine = GameEngine(s)
        check(engine.submit(GameAction.MoveUnit(s.unitIdAt(hex(1)), hex(2))) is LegalityResult.Ok)
        assertEquals(Deposit.FERTILE, engine.state.value.tiles.getValue(hex(2)).deposit)
        assertEquals(1, engine.lastEvents.count { it is GameEvent.HoardUncovered })

        val vein = strip(9, 0..3, 6..8, rules = hoard)
            .withDeposit(Deposit.GOLD_VEIN, hex(2))
            .withMonster(hex(2), tier = 1)
            .withUnit(owner = 0, tier = 2, at = hex(1))
        val engine2 = GameEngine(vein)
        check(engine2.submit(GameAction.MoveUnit(vein.unitIdAt(hex(1)), hex(2))) is LegalityResult.Ok)
        assertEquals(Deposit.GOLD_VEIN, engine2.state.value.tiles.getValue(hex(2)).deposit)
        assertTrue(engine2.lastEvents.none { it is GameEvent.HoardUncovered })
    }

    // ----- caches -----

    @Test
    fun `walking onto a waiting cache collects it`() {
        val s = base
            .withCache(hex(2), 10)
            .withUnit(owner = 0, tier = 1, at = hex(1))
        val engine = GameEngine(s)
        val before = s.player(PlayerId(0)).treasury
        check(engine.submit(GameAction.MoveUnit(s.unitIdAt(hex(1)), hex(2))) is LegalityResult.Ok)
        val after = engine.state.value
        assertEquals(null, after.tiles.getValue(hex(2)).cache)
        assertEquals(before + 10, after.player(PlayerId(0)).treasury)
        val collected = engine.lastEvents.filterIsInstance<GameEvent.CacheCollected>().single()
        assertEquals(PlayerId(0), collected.by)
        assertEquals(10, collected.gold)
        assertInvariants(after)
    }

    @Test
    fun `buying a unit onto a cache scoops it too`() {
        val s = base.withCache(hex(2), 10)
        val engine = GameEngine(s)
        val before = s.player(PlayerId(0)).treasury
        check(engine.submit(GameAction.BuyUnit(1, hex(2))) is LegalityResult.Ok)
        assertEquals(
            before - rules.unitCost[0] + 10,
            engine.state.value.player(PlayerId(0)).treasury,
        )
        assertEquals(null, engine.state.value.tiles.getValue(hex(2)).cache)
    }

    @Test
    fun `a cache is owner-agnostic - the enemy can scoop it from your land`() {
        // P1's soldier storms P0's undefended border hex 4, which holds a cache.
        val s = strip(9, 0..4, 5..8, rules = rules)
            .withCache(hex(4), 10)
            .withUnit(owner = 1, tier = 2, at = hex(5))
        val engine = GameEngine(s.copy(currentPlayer = PlayerId(1)))
        val before = s.player(PlayerId(1)).treasury
        check(engine.submit(GameAction.MoveUnit(s.unitIdAt(hex(5)), hex(4))) is LegalityResult.Ok)
        assertEquals(before + 10, engine.state.value.player(PlayerId(1)).treasury)
        assertEquals(null, engine.state.value.tiles.getValue(hex(4)).cache)
        assertInvariants(engine.state.value)
    }

    // ----- placement rejections -----

    @Test
    fun `buying a unit onto an own squatted hex is rejected`() {
        val s = base.withMonster(hex(2), tier = 1)
        assertEquals(
            RejectionReason.MONSTER_ON_HEX,
            rejected(GameEngine(s).submit(GameAction.BuyUnit(1, hex(2)))),
        )
    }

    @Test
    fun `building onto an own squatted hex is rejected`() {
        val s = base.withMonster(hex(2), tier = 1)
        assertEquals(
            RejectionReason.MONSTER_ON_HEX,
            rejected(GameEngine(s).submit(GameAction.BuyBuilding(BuildingType.TOWER, hex(2)))),
        )
    }
}
