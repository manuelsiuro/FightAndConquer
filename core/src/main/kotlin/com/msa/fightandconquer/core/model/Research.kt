package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/** The four research branches. Grouping only — never serialized on its own. */
enum class TechBranch { WAR, COIN, STONE, SAIL }

/**
 * The twelve technologies of the skill tree: four branches, three tiers each,
 * strictly linear within a branch. Serialized by NAME — never rename a value,
 * saves carry them. Costs and durations deliberately live in [RuleConstants]
 * (per tier), not here: saves snapshot their rules, so replayed StartResearch
 * actions stay legal under later tuning, and campaigns can retune pacing.
 */
@Serializable
enum class Tech(val branch: TechBranch, val tier: Int) {
    SMITHING(TechBranch.WAR, 1),
    ARMORY(TechBranch.WAR, 2),
    SIEGECRAFT(TechBranch.WAR, 3),
    COINAGE(TechBranch.COIN, 1),
    BANKING(TechBranch.COIN, 2),
    TREASURY(TechBranch.COIN, 3),
    MASONRY(TechBranch.STONE, 1),
    ENGINEERING(TechBranch.STONE, 2),
    BASTIONS(TechBranch.STONE, 3),
    NAVIGATION(TechBranch.SAIL, 1),
    SHIPWRIGHTS(TechBranch.SAIL, 2),
    ADMIRALTY(TechBranch.SAIL, 3),
    ;

    /** The tier below in this branch, or null for tier 1 (linear prerequisites). */
    val prerequisite: Tech?
        get() = entries.firstOrNull { it.branch == branch && it.tier == tier - 1 }
}

/** One research in flight: which tech, and how many progress points banked. */
@Serializable
data class ActiveResearch(val tech: Tech, val progress: Int = 0)

/**
 * A player's research this match: completed techs plus the single active slot.
 * [completed] is kept sorted by [Tech.ordinal] so serialized state is
 * byte-stable (determinism tests); every writer goes through [completing] or
 * [of] — the `discovered`/`setDiplomacy` discipline.
 */
@Serializable
data class ResearchState(
    val completed: Set<Tech> = emptySet(),
    val active: ActiveResearch? = null,
) {
    fun has(tech: Tech): Boolean = tech in completed

    /** Completion: adds [tech], clears the active slot, keeps canonical order. */
    fun completing(tech: Tech): ResearchState =
        ResearchState(completed = sortedSet(completed + tech), active = null)

    companion object {
        /** Canonical constructor for authored sets (campaign startingTech). */
        fun of(techs: Collection<Tech>): ResearchState = ResearchState(completed = sortedSet(techs))

        private fun sortedSet(techs: Collection<Tech>): Set<Tech> =
            techs.sortedBy { it.ordinal }.toCollection(LinkedHashSet())
    }
}
