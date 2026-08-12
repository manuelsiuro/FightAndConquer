package com.msa.fightandconquer.core.model

import com.msa.fightandconquer.core.campaign.LevelFactory
import com.msa.fightandconquer.core.campaign.TestLevels
import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.CustomMapValidator
import com.msa.fightandconquer.core.map.MapGenerator
import com.msa.fightandconquer.core.map.MapParams
import com.msa.fightandconquer.core.map.MapViolation
import com.msa.fightandconquer.core.persist.SaveCodec
import com.msa.fightandconquer.core.persist.SaveGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 civilization plumbing: identity only — a civ rides on [PlayerState], flows in
 * through every instantiation path, and survives persistence. No rule reads it yet.
 */
class CivilizationTest {

    private val map = MapGenerator.generate(MapParams(seed = 7L, playerCount = 3))
    private val kinds = List(3) { PlayerKind.Human }

    @Test
    fun `newGame assigns the requested civilization to each seat`() {
        val civs = listOf(Civilization.VIKINGS, Civilization.SULTANATE, Civilization.SHOGUNATE)
        val state = map.newGame(gameSeed = 1L, kinds = kinds, civs = civs)
        assertEquals(civs, state.players.map { it.civ })
    }

    @Test
    fun `newGame defaults every seat to Kingdom`() {
        val state = map.newGame(gameSeed = 1L, kinds = kinds)
        assertTrue(state.players.all { it.civ == Civilization.KINGDOM })
    }

    @Test
    fun `newGame rejects a civ list of the wrong arity`() {
        assertThrows(IllegalArgumentException::class.java) {
            map.newGame(gameSeed = 1L, kinds = kinds, civs = listOf(Civilization.VIKINGS))
        }
    }

    @Test
    fun `LevelFactory applies authored civs and defaults without them`() {
        val authored = listOf(Civilization.SULTANATE, Civilization.VIKINGS)
        val withCivs = LevelFactory.instantiate(TestLevels.strip(civs = authored))
        assertEquals(authored, withCivs.players.map { it.civ })

        val withoutCivs = LevelFactory.instantiate(TestLevels.strip())
        assertTrue(withoutCivs.players.all { it.civ == Civilization.KINGDOM })
    }

    @Test
    fun `LevelFactory rejects a civ list of the wrong arity`() {
        assertThrows(IllegalArgumentException::class.java) {
            LevelFactory.instantiate(TestLevels.strip(civs = listOf(Civilization.VIKINGS)))
        }
    }

    @Test
    fun `the validator mirrors the civ arity require`() {
        val def = CustomMapDef(
            id = "civ_arity",
            name = "civ_arity",
            createdAt = 0L,
            modifiedAt = 0L,
            level = TestLevels.strip(civs = listOf(Civilization.VIKINGS)),
        )
        assertTrue(
            CustomMapValidator.validate(def).any { it is MapViolation.CivsSizeMismatch },
        )
    }

    @Test
    fun `a save carries each player's civilization through a round trip`() {
        val civs = listOf(Civilization.SHOGUNATE, Civilization.KINGDOM, Civilization.VIKINGS)
        val state = map.newGame(gameSeed = 1L, kinds = kinds, civs = civs)
        val save = SaveGame(turnStartState = state, actionsThisTurn = emptyList())
        val decoded = SaveCodec.decode(SaveCodec.encode(save))
        assertEquals(civs, SaveCodec.restore(decoded).players.map { it.civ })
    }
}
