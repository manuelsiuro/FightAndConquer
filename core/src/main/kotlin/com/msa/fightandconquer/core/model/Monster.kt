package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * The night bestiary. A kind is identity and art; combat stats come from
 * [Monster.tier] alone (attack = tier + 1, defense = tier — see
 * `Rules.monsterAttackOf`/`monsterDefenseOf`), so adding per-kind flavor later
 * is purely additive. Name-serialized: no save keys beyond the enum name.
 */
@Serializable
enum class MonsterKind {
    WOLF, SPIDER, OGRE, TROLL, WYRM;

    companion object {
        /** The kinds a night of [tier]-strength monsters draws from (spawn variety). */
        fun forTier(tier: Int): List<MonsterKind> = when {
            tier <= 1 -> listOf(WOLF, SPIDER)
            tier == 2 -> listOf(OGRE, TROLL)
            else -> listOf(WYRM)
        }
    }
}

/**
 * A neutral creature squatting a land hex for the night (see docs/game-rules.md
 * "Day-night cycle"). Never a [GameUnit]: monsters live on the tile like flora,
 * own nothing, and vanish at dawn. While one squats an owned hex, that hex
 * produces no income; slaying it drops a gold cache ([Tile.cache]) on the spot.
 */
@Serializable
data class Monster(
    val kind: MonsterKind,
    /** 1..[RuleConstants.monsterMaxTier]; ramps up across the game's nights. */
    val tier: Int,
    /** The round ([GameState.turnNumber]) this monster's night began. */
    val spawnedRound: Int,
)
