package com.msa.fightandconquer.core.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unlock math and best-result folding for the permanent campaign record. */
class CampaignProgressTest {

    private fun campaign(id: String, vararg levelIds: String) = CampaignDef(
        id = id,
        order = 0,
        levels = levelIds.map { TestLevels.strip().copy(id = it) },
    )

    @Test
    fun `wins keep the best stars and the fastest finish`() {
        var p = CampaignProgress()
        p = p.withWin("m1", stars = 2, rounds = 20)
        p = p.withWin("m1", stars = 1, rounds = 12) // worse stars, better time
        assertEquals(LevelResult(stars = 2, bestRounds = 12), p.resultFor("m1"))
        p = p.withWin("m1", stars = 3, rounds = 30) // better stars, worse time
        assertEquals(LevelResult(stars = 3, bestRounds = 12), p.resultFor("m1"))
    }

    @Test
    fun `missions unlock strictly in order`() {
        val academy = campaign(CampaignProgress.TUTORIAL_ID, "a1", "a2", "a3")
        var p = CampaignProgress()
        assertTrue(p.isUnlocked(academy, 0))
        assertFalse(p.isUnlocked(academy, 1))
        p = p.withWin("a1", 3, 5)
        assertTrue(p.isUnlocked(academy, 1))
        assertFalse(p.isUnlocked(academy, 2))
    }

    @Test
    fun `story campaigns open after the tutorial gate mission`() {
        val isles = campaign("isles", "i1", "i2")
        var p = CampaignProgress()
        assertFalse(p.isCampaignUnlocked(isles))
        assertFalse("locked campaign locks its first mission", p.isUnlocked(isles, 0))
        p = p.withWin(CampaignProgress.TUTORIAL_GATE_LEVEL, 1, 9)
        assertTrue(p.isCampaignUnlocked(isles))
        assertTrue(p.isUnlocked(isles, 0))
    }

    @Test
    fun `progress round-trips through its codec`() {
        val p = CampaignProgress().withWin("m1", 2, 14).withWin("m2", 3, 8)
        assertEquals(p, CampaignProgressCodec.decode(CampaignProgressCodec.encode(p)))
    }
}
