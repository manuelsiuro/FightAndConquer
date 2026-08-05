package com.msa.fightandconquer.ui.editor

import com.msa.fightandconquer.core.editor.CustomMapDef
import com.msa.fightandconquer.core.editor.MapCodec
import java.io.File

/**
 * Reads and writes user maps, one JSON file per map at `filesDir/maps/<id>.json`.
 *
 * The shape lives in `:core` ([CustomMapDef]); this is only the files. Writes are
 * small and rare — one per explicit save — so they are synchronous, following
 * [com.msa.fightandconquer.ui.campaign.CampaignProgressStore]: losing an authored map
 * to a coroutine that never ran would be worse than a millisecond on the save button.
 *
 * A file that no longer decodes (hand-edited, or authored by a newer build than this
 * one understands) is skipped by [list] rather than sinking the whole library.
 */
class CustomMapStore(private val dir: File) {

    private var cached: MutableMap<String, CustomMapDef>? = null

    /** Every readable map, newest change first. */
    fun list(): List<CustomMapDef> =
        maps().values.sortedWith(compareByDescending<CustomMapDef> { it.modifiedAt }.thenBy { it.id })

    fun load(id: String): CustomMapDef? = maps()[id]

    fun save(def: CustomMapDef) {
        dir.mkdirs()
        runCatching { File(dir, "${def.id}.json").writeText(MapCodec.encode(def)) }
        maps()[def.id] = def
    }

    fun delete(id: String) {
        runCatching { File(dir, "$id.json").delete() }
        maps().remove(id)
    }

    private fun maps(): MutableMap<String, CustomMapDef> =
        cached ?: read().also { cached = it }

    private fun read(): MutableMap<String, CustomMapDef> {
        val out = LinkedHashMap<String, CustomMapDef>()
        val files = dir.listFiles { file -> file.extension == "json" } ?: return out
        files.forEach { file ->
            runCatching { MapCodec.decode(file.readText()) }
                .onSuccess { def -> out[def.id] = def }
        }
        return out
    }
}
