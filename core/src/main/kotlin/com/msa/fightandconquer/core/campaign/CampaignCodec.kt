package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.persist.CompatJson
import kotlinx.serialization.json.Json

/** JSON for shipped campaign definitions — [CompatJson] explains the configuration. */
object CampaignCodec {

    val json: Json = CompatJson

    fun decode(text: String): CampaignDef =
        json.decodeFromString(CampaignDef.serializer(), text)

    fun encode(campaign: CampaignDef): String =
        json.encodeToString(CampaignDef.serializer(), campaign)
}
