package com.msa.fightandconquer.core.campaign

import kotlinx.serialization.json.Json

/**
 * JSON for shipped campaign definitions, mirroring
 * [com.msa.fightandconquer.core.persist.SaveCodec]:
 *
 * - `ignoreUnknownKeys` so a level authored against a newer format still loads the parts
 *   this build understands instead of failing the whole campaign;
 * - `encodeDefaults` so a written level is fully self-describing — a level that relies on
 *   today's `RuleConstants` defaults must not silently adopt tomorrow's.
 */
object CampaignCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun decode(text: String): CampaignDef =
        json.decodeFromString(CampaignDef.serializer(), text)

    fun encode(campaign: CampaignDef): String =
        json.encodeToString(CampaignDef.serializer(), campaign)
}
