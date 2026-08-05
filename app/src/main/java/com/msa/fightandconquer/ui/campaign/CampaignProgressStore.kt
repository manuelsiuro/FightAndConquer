package com.msa.fightandconquer.ui.campaign

import com.msa.fightandconquer.core.campaign.CampaignDef
import com.msa.fightandconquer.core.campaign.CampaignProgress
import com.msa.fightandconquer.core.campaign.CampaignProgressCodec
import com.msa.fightandconquer.core.campaign.LevelResult
import java.io.File

/**
 * Reads and writes campaign progress at `filesDir/campaign_progress.json`.
 *
 * The shape lives in `:core` ([CampaignProgress]); this is only the file. Writes are
 * small and rare — one per completed mission — so they are synchronous: losing a
 * hard-won third star to a coroutine that never ran would be worse than a millisecond on
 * the completion screen.
 */
class CampaignProgressStore(private val file: File) {

    private var cached: CampaignProgress? = null

    fun progress(): CampaignProgress = cached ?: read().also { cached = it }

    fun resultFor(levelId: String): LevelResult? = progress().resultFor(levelId)

    fun isComplete(levelId: String): Boolean = progress().isComplete(levelId)

    fun isUnlocked(campaign: CampaignDef, levelIndex: Int): Boolean =
        progress().isUnlocked(campaign, levelIndex)

    fun isCampaignUnlocked(campaign: CampaignDef): Boolean = progress().isCampaignUnlocked(campaign)

    fun completedCount(campaign: CampaignDef): Int = progress().completedCount(campaign)

    fun starCount(campaign: CampaignDef): Int = progress().starCount(campaign)

    /** Records a win, keeping the best stars and the fastest finish. */
    fun record(levelId: String, stars: Int, rounds: Int) {
        val updated = progress().withWin(levelId, stars, rounds)
        if (updated == cached) return
        cached = updated
        runCatching { file.writeText(CampaignProgressCodec.encode(updated)) }
    }

    private fun read(): CampaignProgress =
        runCatching { CampaignProgressCodec.decode(file.readText()) }.getOrElse { CampaignProgress() }
}
