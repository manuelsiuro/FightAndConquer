package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.persist.CompatJson
import kotlinx.serialization.json.Json

/** JSON for stored custom maps — [CompatJson] explains the configuration. */
object MapCodec {

    val json: Json = CompatJson

    fun decode(text: String): CustomMapDef =
        json.decodeFromString(CustomMapDef.serializer(), text)

    fun encode(def: CustomMapDef): String =
        json.encodeToString(CustomMapDef.serializer(), def)
}
