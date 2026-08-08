package com.msa.fightandconquer.core.persist

import kotlinx.serialization.json.Json

/**
 * The one JSON configuration shared by every persisted artifact — saves
 * ([SaveCodec]), campaign definitions, custom maps, campaign progress:
 *
 * - `ignoreUnknownKeys` so an artifact written by a newer format still loads the
 *   parts this build understands (forward-compatible loading);
 * - `encodeDefaults` because written artifacts MUST be fully self-describing —
 *   the `RuleConstants` snapshot is the whole point; with encodeDefaults=false an
 *   artifact relying on today's defaults would silently adopt tomorrow's after
 *   rule tuning.
 */
val CompatJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
