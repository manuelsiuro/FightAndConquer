package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.TestStates
import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.model.AiPersonality
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPersonalityTest {

    @Test
    fun `derived personality is deterministic and distinct across the four seats`() {
        for (seed in listOf(1L, 7L, 42L, 987654321L)) {
            val personalities = (0..3).map { AiProfile.derivedPersonality(seed, PlayerId(it)) }
            assertEquals(
                "seats must never share a preset (seed $seed)",
                AiPersonality.entries.size,
                personalities.toSet().size,
            )
            assertEquals(
                "same seed must derive the same personalities forever",
                personalities,
                (0..3).map { AiProfile.derivedPersonality(seed, PlayerId(it)) },
            )
        }
    }

    @Test
    fun `different seeds rotate the personality a seat receives`() {
        val seen = (1L..16L).map { AiProfile.derivedPersonality(it, PlayerId(0)) }.toSet()
        assertTrue("16 seeds landing on one preset means the derivation is broken", seen.size > 1)
    }

    @Test
    fun `an explicit personality on the seat overrides the seed derivation`() {
        val base = TestStates.strip(9, 0..2, 6..8)
        val state = base.copy(
            players = base.players.map {
                it.copy(kind = PlayerKind.Ai(Difficulty.HARD, AiPersonality.TURTLE))
            },
        )
        assertEquals(
            AiPersonality.TURTLE,
            AiProfile.resolve(state, PlayerId(0), Difficulty.HARD).personality,
        )
    }

    @Test
    fun `easy and passive stay on the neutral profile`() {
        val state = TestStates.strip(9, 0..2, 6..8)
        assertEquals(AiProfile.NEUTRAL, AiProfile.resolve(state, PlayerId(0), Difficulty.EASY))
        assertEquals(AiProfile.NEUTRAL, AiProfile.resolve(state, PlayerId(0), Difficulty.PASSIVE))
        assertNull(AiProfile.NEUTRAL.personality)
    }

    @Test
    fun `normal and hard resolve a personality for every AI seat`() {
        val base = TestStates.strip(9, 0..2, 6..8)
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.HARD)) {
            val state = base.copy(
                players = base.players.map { it.copy(kind = PlayerKind.Ai(difficulty)) },
            )
            val profile = AiProfile.resolve(state, PlayerId(1), difficulty)
            assertTrue("$difficulty must play a personality", profile.personality != null)
            assertEquals(
                "resolution must be a pure function of the state",
                profile,
                AiProfile.resolve(state, PlayerId(1), difficulty),
            )
        }
    }

    @Test
    fun `a human chair driven by an AI stand-in plays the neutral profile`() {
        // Campaign playthroughs and autoplay put an AiPlayer in the Human seat:
        // that is a stand-in for a competent player, never a someone — and it
        // keeps the campaign solvability gates out of the personality reshuffle.
        val state = TestStates.strip(9, 0..2, 6..8)
        assertEquals(AiProfile.NEUTRAL, AiProfile.resolve(state, PlayerId(0), Difficulty.NORMAL))
        assertEquals(AiProfile.NEUTRAL, AiProfile.resolve(state, PlayerId(0), Difficulty.HARD))
    }
}
