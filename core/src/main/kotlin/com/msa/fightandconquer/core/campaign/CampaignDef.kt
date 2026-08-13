package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.map.MapDefinition
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.UnitType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Who holds a seat. Index in [LevelDef.seats] == index in [MapDefinition.capitals]. */
@Serializable
sealed interface SeatDef {

    /** The player's own seat. Exactly one level seat may be [Player]. */
    @Serializable
    @SerialName("player")
    data object Player : SeatDef

    @Serializable
    @SerialName("ai")
    data class Ai(val difficulty: Difficulty) : SeatDef

    fun toKind(): PlayerKind = when (this) {
        Player -> PlayerKind.Human
        is Ai -> PlayerKind.Ai(difficulty)
    }
}

/** A unit the level starts with on the board. */
@Serializable
data class UnitPlacement(
    val seat: Int,
    val hex: Hex,
    @SerialName("unitType")
    val type: UnitType = UnitType.SOLDIER,
    val tier: Int = 1,
)

/**
 * One campaign mission.
 *
 * The board is an ordinary [MapDefinition] with `generatorParams = null` — exactly the
 * authored-map case the map format was built for — so a level needs no bespoke terrain
 * representation and is validated by [com.msa.fightandconquer.core.map.MapValidator].
 *
 * Difficulty comes from honest level design, never from cheating: per-seat AI strength
 * ([seats]), the authored starting position, [startingTreasury], and the rules a level
 * leaves switched on. [rules] is the teaching lever the Academy leans on — a level that
 * has not taught boats yet simply ships `navalEnabled = false`, and the purchase tray
 * narrows itself with no gating code anywhere.
 *
 * Every user-facing string is an **id**, never prose: the engine module has no
 * resources, and the app resolves ids against `strings.xml` so campaigns translate.
 */
@Serializable
data class LevelDef(
    val id: String,
    val seed: Long,
    val map: MapDefinition,
    val seats: List<SeatDef>,
    val rules: RuleConstants = RuleConstants(),
    /** Per-seat opening treasury; null keeps [RuleConstants.startingTreasury] for all. */
    val startingTreasury: List<Int>? = null,
    /** Per-seat civilizations; null keeps [Civilization.DEFAULT] for all. */
    val civs: List<Civilization>? = null,
    val startingUnits: List<UnitPlacement> = emptyList(),
    val objectives: List<Objective> = listOf(Objective.ConquerAll),
    val failures: List<FailCondition> = emptyList(),
    /** Rounds for a three-star finish; null means the level is not rated on speed. */
    val parRounds: Int? = null,
    val hints: List<HintStep> = emptyList(),
    val scripts: List<ScriptTrigger> = emptyList(),
    /**
     * Whether an AI driving the player's seat can be expected to *complete* this level.
     *
     * The AI is an opponent model: it plays for territory, and knows nothing about
     * objectives. So it finishes a mission whose goal happens to align with conquest
     * ("outlast", "out-earn", "wipe out that seat") and reliably ignores one that does
     * not ("land on those three beaches", "sink three raiders"). Flagging the latter
     * false is honest, not a loophole — `CampaignPlaythroughTest` still requires those
     * levels to terminate and to leave the player alive, and the static reachability
     * check in `CampaignFormatTest` still proves their targets can be gotten at.
     */
    val aiSolvable: Boolean = true,
) {
    /** The seat the player holds. */
    val playerSeat: PlayerId
        get() = PlayerId(seats.indexOfFirst { it is SeatDef.Player }.coerceAtLeast(0))

    /** Star rating for a win in [rounds]: 3 within par, 2 within 1.5x par, else 1. */
    fun starsFor(rounds: Int): Int {
        val par = parRounds ?: return 3
        return when {
            rounds <= par -> 3
            rounds <= par * 3 / 2 -> 2
            else -> 1
        }
    }
}

/**
 * An ordered series of missions. [id] identifies both the asset and the string ids the
 * app resolves for its name and blurb.
 */
@Serializable
data class CampaignDef(
    val version: Int = 1,
    val id: String,
    /** Display order in the campaign picker. */
    val order: Int = 0,
    val levels: List<LevelDef>,
)
