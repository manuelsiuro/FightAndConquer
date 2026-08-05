package com.msa.fightandconquer.ui.campaign

import android.content.Context
import com.msa.fightandconquer.core.campaign.CampaignCodec
import com.msa.fightandconquer.core.campaign.CampaignDef
import com.msa.fightandconquer.core.campaign.LevelDef

/**
 * Loads the shipped campaigns from `assets/campaigns/`.
 *
 * Decoding all three costs a few milliseconds and the result is immutable, so it is read
 * once and cached for the process rather than threaded through a loading state — a
 * campaign list that has to show a spinner would be an odd thing to build.
 *
 * The assets themselves are validated in `:core:test` (`CampaignFormatTest`), which is
 * why nothing here tries to repair a malformed file: a level that reaches a device has
 * already been parsed, validated and played through headlessly.
 */
class CampaignRepository(private val context: Context) {

    private var cached: List<CampaignDef>? = null

    /** Every campaign, in the display order their definitions declare. */
    fun campaigns(): List<CampaignDef> = cached ?: load().also { cached = it }

    fun campaign(id: String): CampaignDef? = campaigns().firstOrNull { it.id == id }

    fun level(campaignId: String, levelId: String): LevelDef? =
        campaign(campaignId)?.levels?.firstOrNull { it.id == levelId }

    /** The mission after [levelId] in the same campaign, or null at the end. */
    fun nextLevel(campaignId: String, levelId: String): LevelDef? {
        val levels = campaign(campaignId)?.levels ?: return null
        val index = levels.indexOfFirst { it.id == levelId }
        return levels.getOrNull(index + 1)
    }

    private fun load(): List<CampaignDef> {
        val names = runCatching { context.assets.list(DIRECTORY)?.toList() }
            .getOrNull()
            .orEmpty()
            .filter { it.endsWith(".json") }
        return names
            .mapNotNull { name ->
                runCatching {
                    CampaignCodec.decode(context.assets.open("$DIRECTORY/$name").bufferedReader().readText())
                }.getOrNull()
            }
            .sortedWith(compareBy({ it.order }, { it.id }))
    }

    private companion object {
        const val DIRECTORY = "campaigns"
    }
}
