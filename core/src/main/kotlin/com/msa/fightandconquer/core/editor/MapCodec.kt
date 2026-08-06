package com.msa.fightandconquer.core.editor

import kotlinx.serialization.json.Json

/**
 * JSON for stored custom maps, mirroring
 * [com.msa.fightandconquer.core.campaign.CampaignCodec]:
 *
 * - `ignoreUnknownKeys` so a map authored against a newer format still loads the parts
 *   this build understands;
 * - `encodeDefaults` so a written map is fully self-describing — one that relies on
 *   today's `RuleConstants` defaults must not silently adopt tomorrow's.
 */
object MapCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun decode(text: String): CustomMapDef =
        json.decodeFromString(CustomMapDef.serializer(), text)

    fun encode(def: CustomMapDef): String =
        json.encodeToString(CustomMapDef.serializer(), def)
}
