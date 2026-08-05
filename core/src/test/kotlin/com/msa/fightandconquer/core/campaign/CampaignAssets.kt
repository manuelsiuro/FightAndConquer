package com.msa.fightandconquer.core.campaign

import java.io.File

/**
 * Locates the shipped campaign JSON from a host test.
 *
 * The levels live under `app/src/main/assets/` because that is where the game loads them
 * from, but they are *validated* here, in `:core`, where the format and the rules live —
 * so a malformed level fails `./gradlew :core:test` in milliseconds instead of on a
 * device. The directory is found by walking up from the module's working directory
 * rather than hardcoding a relative depth.
 */
object CampaignAssets {

    private const val RELATIVE = "app/src/main/assets/campaigns"

    val directory: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, RELATIVE) }
            .firstOrNull { it.isDirectory }
            ?: error("campaign assets not found: no ancestor of ${File("").absolutePath} holds $RELATIVE")
    }

    fun files(): List<File> = directory.listFiles { f: File -> f.extension == "json" }
        .orEmpty()
        .sortedBy { it.name }

    fun campaigns(): List<CampaignDef> = files().map { CampaignCodec.decode(it.readText()) }
}
