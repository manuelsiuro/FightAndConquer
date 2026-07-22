package com.msa.fightandconquer.core.persist

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.TestStates.strip
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Save-compatibility guard: every expansion field must be defaulted, so a save written
 * BEFORE the expansion (i.e. without those JSON keys) decodes and replays identically.
 * A legacy save is emulated by stripping exactly the expansion keys from a fresh save —
 * add each new serialized field name here as later phases introduce them.
 */
class LegacySaveTest {

    private val expansionKeys = setOf(
        // Tile (Phase A)
        "deposit",
        // RuleConstants (Phase A)
        "fertileHexBonus", "fertileFarmBonus",
        "goldVeinsPerPlayer", "goldVeinBandMin", "goldVeinBandMax",
        "goldVeinsNeutralPer150Hexes", "fertilePerPlayer", "fertileNeutralPercent",
        "mineCost", "mineIncome",
        "marketCost", "marketNeighborIncome", "marketNeighborCap",
        "lumberCampCost", "lumberCampTreeIncome", "lumberCampTreeCap",
        "watchtowerCost", "watchtowerVisionRadius",
    )

    private fun strip(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterKeys { it !in expansionKeys }.mapValues { strip(it.value) })
        is JsonArray -> JsonArray(element.map { strip(it) })
        else -> element
    }

    @Test
    fun `a pre-expansion save decodes and replays to the identical state`() {
        val engine = GameEngine(strip(9, 0..2, 6..8))
        check(engine.submit(GameAction.BuyUnit(1, hex(1))) is com.msa.fightandconquer.core.engine.LegalityResult.Ok)
        check(engine.submit(GameAction.MoveUnit(engine.state.value.tiles.getValue(hex(1)).unit!!, hex(3))) is com.msa.fightandconquer.core.engine.LegalityResult.Ok)
        val save = engine.toSave()

        val legacyJson = SaveCodec.json.encodeToString(
            JsonElement.serializer(),
            strip(SaveCodec.json.parseToJsonElement(SaveCodec.encode(save))),
        )
        val decoded = SaveCodec.decode(legacyJson)

        // Missing expansion keys must fall back to the exact defaults...
        assertEquals(save, decoded)
        // ...and the replayed live state must match the running engine bit for bit.
        assertEquals(
            SaveCodec.json.encodeToString(
                com.msa.fightandconquer.core.model.GameState.serializer(),
                engine.state.value,
            ),
            SaveCodec.json.encodeToString(
                com.msa.fightandconquer.core.model.GameState.serializer(),
                SaveCodec.restore(decoded),
            ),
        )
    }
}
