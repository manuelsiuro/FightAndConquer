package com.msa.fightandconquer.core.campaign

import kotlinx.serialization.Serializable

/** What the player has achieved on one mission. Best result only — never a regression. */
@Serializable
data class LevelResult(val stars: Int, val bestRounds: Int)

/**
 * The permanent record of a player's campaign career, keyed by level id.
 *
 * Deliberately separate from [com.msa.fightandconquer.core.persist.SaveGame]: an
 * autosave is one game in flight and is *deleted* when it finishes, while this survives
 * everything. It lives in `:core` for the same reason the save format does — the app
 * owns the file, the engine module owns the shape of what is written into it.
 */
@Serializable
data class CampaignProgress(val results: Map<String, LevelResult> = emptyMap()) {

    fun resultFor(levelId: String): LevelResult? = results[levelId]

    fun isComplete(levelId: String): Boolean = levelId in results

    /** Folds in a win, keeping the best star count and the fastest finish. */
    fun withWin(levelId: String, stars: Int, rounds: Int): CampaignProgress {
        val previous = results[levelId]
        val merged = LevelResult(
            stars = maxOf(stars, previous?.stars ?: 0),
            bestRounds = minOf(rounds, previous?.bestRounds ?: Int.MAX_VALUE),
        )
        return if (merged == previous) this else CampaignProgress(results + (levelId to merged))
    }

    fun completedCount(campaign: CampaignDef): Int = campaign.levels.count { isComplete(it.id) }

    fun starCount(campaign: CampaignDef): Int = campaign.levels.sumOf { resultFor(it.id)?.stars ?: 0 }

    /**
     * Missions unlock in order: the first is open, the rest need the one before them.
     * Progression is a reading order, not a difficulty gate.
     */
    fun isUnlocked(campaign: CampaignDef, levelIndex: Int): Boolean {
        if (!isCampaignUnlocked(campaign)) return false
        if (levelIndex == 0) return true
        val previous = campaign.levels.getOrNull(levelIndex - 1) ?: return false
        return isComplete(previous.id)
    }

    /**
     * The tutorial is open from the start; the story campaigns want a player who already
     * knows how a hex is taken, so they open after its third mission — early enough not
     * to be a wall, late enough that the rules are not a surprise.
     */
    fun isCampaignUnlocked(campaign: CampaignDef): Boolean =
        campaign.id == TUTORIAL_ID || isComplete(TUTORIAL_GATE_LEVEL)

    companion object {
        const val TUTORIAL_ID = "academy"
        const val TUTORIAL_GATE_LEVEL = "academy_shoulder"
    }
}

object CampaignProgressCodec {

    private val json = com.msa.fightandconquer.core.persist.CompatJson

    fun encode(progress: CampaignProgress): String =
        json.encodeToString(CampaignProgress.serializer(), progress)

    fun decode(text: String): CampaignProgress =
        json.decodeFromString(CampaignProgress.serializer(), text)
}
