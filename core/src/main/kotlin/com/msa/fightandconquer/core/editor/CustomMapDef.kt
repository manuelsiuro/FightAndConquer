package com.msa.fightandconquer.core.editor

import com.msa.fightandconquer.core.campaign.LevelDef
import kotlinx.serialization.Serializable

/**
 * A user-authored map: a thin envelope around a real [LevelDef], so playing one is
 * exactly `LevelFactory.instantiate(level)` and forward compatibility rides on the
 * campaign format's own tolerance (`ignoreUnknownKeys` + defaulted fields).
 *
 * [id] doubles as the storage file name (`maps/<id>.json`) and is mirrored into
 * [LevelDef.id]; [name] is the user's title, mirrored into the map's `name`.
 * Timestamps are epoch millis stamped by the app layer — the engine never reads a
 * clock (determinism), it only carries the values.
 */
@Serializable
data class CustomMapDef(
    val version: Int = 1,
    val id: String,
    val name: String,
    val author: String? = null,
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val level: LevelDef,
)
