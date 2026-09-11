package com.msa.fightandconquer.ui

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

/** The seat the HUD calls "current" while an AI's beats are still playing ([presentedTurn]). */
class PresentedTurnTest {

    private val ai = PlayerKind.Ai(Difficulty.NORMAL)

    private fun state(kinds: List<PlayerKind>, currentPlayer: Int): GameState {
        val tiles = HashMap<Hex, Tile>()
        val players = kinds.mapIndexed { i, kind ->
            tiles[Hex.of(i, 0)] = Tile(owner = PlayerId(i))
            PlayerState(
                id = PlayerId(i),
                kind = kind,
                treasury = 10,
                capital = Hex.of(i, 0),
            )
        }
        return GameState(
            config = GameConfig(seed = 1L),
            tiles = tiles,
            units = emptyMap(),
            players = players,
            currentPlayer = PlayerId(currentPlayer),
            turnNumber = 0,
            rngState = 1L,
        )
    }

    @Test
    fun `nothing presented follows the engine's human seat`() {
        val s = state(listOf(PlayerKind.Human, ai), currentPlayer = 0)
        assertEquals(PresentedTurn(seat = 0, isHuman = true, aiActing = false), presentedTurn(s, null))
    }

    @Test
    fun `nothing presented follows the engine's AI seat without claiming it acts`() {
        val s = state(listOf(PlayerKind.Human, ai), currentPlayer = 1)
        assertEquals(PresentedTurn(seat = 1, isHuman = false, aiActing = false), presentedTurn(s, null))
    }

    @Test
    fun `a presented AI seat outranks the engine having moved on to the human`() {
        val s = state(listOf(PlayerKind.Human, ai), currentPlayer = 0)
        assertEquals(PresentedTurn(seat = 1, isHuman = false, aiActing = true), presentedTurn(s, 1))
    }

    @Test
    fun `a presented seat equal to the engine's AI seat still reads as acting`() {
        val s = state(listOf(PlayerKind.Human, ai), currentPlayer = 1)
        assertEquals(PresentedTurn(seat = 1, isHuman = false, aiActing = true), presentedTurn(s, 1))
    }
}
