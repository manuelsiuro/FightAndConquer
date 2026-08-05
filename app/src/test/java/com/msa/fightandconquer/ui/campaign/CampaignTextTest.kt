package com.msa.fightandconquer.ui.campaign

import com.msa.fightandconquer.core.campaign.CampaignCodec
import com.msa.fightandconquer.core.campaign.CampaignDef
import com.msa.fightandconquer.core.campaign.LevelCondition
import com.msa.fightandconquer.ui.UiSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the copy tables and the shipped campaigns in lockstep.
 *
 * `:core:test` proves a level is *playable*; this proves it is *readable* — a mission
 * whose name or hint has no entry in [CampaignText] would render as a raw id, and a hint
 * waiting on a UI signal the app never emits would sit on screen forever. Both are
 * silent failures at runtime, which is exactly why they are asserted here.
 *
 * A plain JVM test: it reads the assets by path rather than through an `AssetManager`,
 * so no Robolectric and no device.
 */
class CampaignTextTest {

    private val campaigns: List<CampaignDef> = assetsDir()
        .listFiles { f: File -> f.extension == "json" }
        .orEmpty()
        .sortedBy { it.name }
        .map { CampaignCodec.decode(it.readText()) }

    private fun assetsDir(): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "app/src/main/assets/campaigns") }
        .firstOrNull { it.isDirectory }
        ?: error("campaign assets not found from ${File("").absolutePath}")

    @Test
    fun `the catalogue is not empty`() {
        assertTrue(campaigns.isNotEmpty())
    }

    @Test
    fun `every campaign and level in the catalogue has copy`() {
        val campaignIds = campaigns.map { it.id }.toSet()
        assertEquals("campaigns without copy", emptySet<String>(), campaignIds - CampaignText.campaignIds())
        assertEquals("copy for campaigns that no longer ship", emptySet<String>(), CampaignText.campaignIds() - campaignIds)

        val levelIds = campaigns.flatMap { c -> c.levels.map { it.id } }.toSet()
        assertEquals("levels without copy", emptySet<String>(), levelIds - CampaignText.levelIds())
        assertEquals("copy for levels that no longer ship", emptySet<String>(), CampaignText.levelIds() - levelIds)
    }

    @Test
    fun `every hint and story beat has copy`() {
        val hintKeys = campaigns.flatMap { c ->
            c.levels.flatMap { level -> level.hints.map { "${level.id}/${it.id}" } }
        }.toSet()
        assertEquals("hints without copy", emptySet<String>(), hintKeys - CampaignText.hintKeys())
        assertEquals("copy for hints that no longer ship", emptySet<String>(), CampaignText.hintKeys() - hintKeys)

        val scriptKeys = campaigns.flatMap { c ->
            c.levels.flatMap { level -> level.scripts.map { "${level.id}/${it.id}" } }
        }.toSet()
        assertEquals("story beats without copy", emptySet<String>(), scriptKeys - CampaignText.scriptKeys())
        assertEquals("copy for beats that no longer ship", emptySet<String>(), CampaignText.scriptKeys() - scriptKeys)
    }

    /**
     * A hint gated on a signal nothing emits never advances — the coach card would be
     * stuck on screen for the rest of the mission.
     */
    @Test
    fun `every ui signal a hint waits on is one the app actually emits`() {
        val waitedOn = campaigns.flatMap { c ->
            c.levels.flatMap { level ->
                level.hints.map { it.until }.filterIsInstance<LevelCondition.UiSignal>().map { it.name }
            }
        }.toSet()
        assertEquals("hints wait on signals the app never sends", emptySet<String>(), waitedOn - UiSignals.all)
    }

    /**
     * A story beat is a *board* event; a UI signal is meaningless to the headless
     * director and would silently never fire.
     */
    @Test
    fun `no story beat is gated on a ui signal`() {
        campaigns.forEach { campaign ->
            campaign.levels.forEach { level ->
                level.scripts.forEach { trigger ->
                    assertTrue(
                        "${campaign.id}/${level.id}: beat '${trigger.id}' waits on a UI signal",
                        trigger.condition !is LevelCondition.UiSignal,
                    )
                }
            }
        }
    }
}
