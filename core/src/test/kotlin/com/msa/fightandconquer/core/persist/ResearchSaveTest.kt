package com.msa.fightandconquer.core.persist

import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.model.ActiveResearch
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.ResearchState
import com.msa.fightandconquer.core.model.Tech
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Research state persistence: byte-stable round-trips (determinism contract) and
 * the defaulted-decode path a pre-research save takes. The action-log round-trip
 * for StartResearch lives in [LegacySaveTest] once the action exists.
 */
class ResearchSaveTest {

    private fun withResearch(state: GameState, seat: Int, research: ResearchState): GameState =
        state.copy(players = state.players.map { if (it.id.value == seat) it.copy(research = research) else it })

    @Test
    fun `a state carrying completed and active research round-trips byte-identically`() {
        val research = ResearchState.of(listOf(Tech.COINAGE, Tech.SMITHING, Tech.BANKING))
            .copy(active = ActiveResearch(Tech.MASONRY, progress = 2))
        val state = withResearch(strip(9, 0..2, 6..8), seat = 0, research = research)

        val json = SaveCodec.json.encodeToString(GameState.serializer(), state)
        val decoded = SaveCodec.json.decodeFromString<GameState>(json)

        assertEquals(state, decoded)
        assertEquals(json, SaveCodec.json.encodeToString(GameState.serializer(), decoded))
    }

    @Test
    fun `canonical writers sort completed techs regardless of input order`() {
        val scrambled = listOf(Tech.NAVIGATION, Tech.SMITHING, Tech.COINAGE, Tech.BANKING)
        assertEquals(
            listOf(Tech.SMITHING, Tech.COINAGE, Tech.BANKING, Tech.NAVIGATION),
            ResearchState.of(scrambled).completed.toList(),
        )
        assertEquals(
            listOf(Tech.SMITHING, Tech.COINAGE, Tech.MASONRY),
            ResearchState.of(listOf(Tech.MASONRY, Tech.COINAGE)).completing(Tech.SMITHING).completed.toList(),
        )
    }

    @Test
    fun `completing clears the active slot`() {
        val state = ResearchState(active = ActiveResearch(Tech.COINAGE, progress = 3))
        val done = state.completing(Tech.COINAGE)
        assertNull(done.active)
        assertEquals(listOf(Tech.COINAGE), done.completed.toList())
    }

    @Test
    fun `a player without the research key decodes to the empty default`() {
        // The exact shape a pre-research save carries for a player entry.
        val legacy = """{"id":0,"kind":{"type":"human"},"treasury":12,"capital":null}"""
        val player = CompatJson.decodeFromString<com.msa.fightandconquer.core.model.PlayerState>(legacy)
        assertEquals(ResearchState(), player.research)
    }
}
