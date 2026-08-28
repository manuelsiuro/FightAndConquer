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

/** The seat whose fog and gold the HUD shows ([hudViewer]). */
class HudViewerTest {

    private val ai = PlayerKind.Ai(Difficulty.NORMAL)

    private fun state(
        kinds: List<PlayerKind>,
        currentPlayer: Int,
        eliminated: Set<Int> = emptySet(),
    ): GameState {
        val tiles = HashMap<Hex, Tile>()
        val players = kinds.mapIndexed { i, kind ->
            tiles[Hex.of(i, 0)] = Tile(owner = PlayerId(i))
            PlayerState(
                id = PlayerId(i),
                kind = kind,
                treasury = 10,
                capital = Hex.of(i, 0),
                eliminated = i in eliminated,
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
    fun `a human's own turn is their own perspective`() {
        val s = state(listOf(PlayerKind.Human, ai), currentPlayer = 0)
        assertEquals(PlayerId(0), hudViewer(s, lastHumanSeat = null))
    }

    @Test
    fun `an AI turn stays on the human who most recently played`() {
        val s = state(listOf(ai, PlayerKind.Human, PlayerKind.Human), currentPlayer = 0)
        assertEquals(PlayerId(2), hudViewer(s, lastHumanSeat = 2))
    }

    @Test
    fun `an eliminated last human yields to the first living human`() {
        val s = state(
            listOf(ai, PlayerKind.Human, PlayerKind.Human),
            currentPlayer = 0,
            eliminated = setOf(2),
        )
        assertEquals(PlayerId(1), hudViewer(s, lastHumanSeat = 2))
    }

    @Test
    fun `an all-AI match is watched from the current seat`() {
        val s = state(listOf(ai, ai), currentPlayer = 1)
        assertEquals(PlayerId(1), hudViewer(s, lastHumanSeat = null))
    }
}
