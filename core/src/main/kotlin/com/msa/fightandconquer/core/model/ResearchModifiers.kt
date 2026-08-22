package com.msa.fightandconquer.core.model

/**
 * The research rule-delta layer: the third stage of the effective-rules
 * pipeline, applied strictly AFTER [CivModifiers.effective] returns —
 * `base → civ → research` — and never inside the civ layer's one-slot cache
 * path, which is keyed per base-rules instance and would be poisoned by
 * per-player variation.
 *
 * Unlike [CivModifiers.validate]'s load-time `require`s, this layer COERCES
 * instead of validating (see SHIPWRIGHTS): its deltas apply at runtime over
 * arbitrary campaign bases, where a crash mid-game is worse than a floor.
 *
 * Delta table (unlock techs — BANKING, MASONRY, ENGINEERING, NAVIGATION —
 * carry no rule delta; they gate purchases via `Rules.requiredTech`):
 * - SMITHING: every fighting unit +1 attack ([RuleConstants.unitAttackBonus]).
 *   This is the audited exception to the "soldier ladder is universal" rule —
 *   the AI's tier arithmetic solves strength through effective rules.
 * - ARMORY: land units +1 garrison defense ([RuleConstants.unitDefenseBonus]).
 * - SIEGECRAFT: catapult +1, warship +1 strength.
 * - COINAGE: income ×110% — superseded by TREASURY's ×120% (not stacked).
 * - BASTIONS: towers, fortress and capital +1 defense (still zeroed by siege).
 * - SHIPWRIGHTS: warship +1 strength, transports −5 cost (floor 1).
 * - ADMIRALTY: port +1 income, fishery +1 per shoal.
 */
object ResearchModifiers {

    /**
     * The rules a player with [research] actually plays with, layered over
     * their civ-effective [civRules]. Identity when research is disabled or
     * nothing is completed. Memoized on ([civRules] identity, [ResearchState.completed]
     * identity): both are instance-stable — CivModifiers caches its variants per
     * base, and the completed set is only rebuilt on a completion — so the AI's
     * hot loops (defenseOf per neighbor per candidate) hit the cache every call.
     */
    fun effective(civRules: RuleConstants, research: ResearchState): RuleConstants {
        if (!civRules.researchEnabled || research.completed.isEmpty()) return civRules
        cache.forEach { if (it.civRules === civRules && it.completed === research.completed) return it.result }
        val result = modified(civRules, research.completed)
        // Copy-on-write, drop-oldest bound: ≤ seats × civ variants live at once;
        // the bound only guards long test processes that churn many games.
        cache = (listOf(Entry(civRules, research.completed, result)) + cache).take(MAX_ENTRIES)
        return result
    }

    private class Entry(val civRules: RuleConstants, val completed: Set<Tech>, val result: RuleConstants)

    private const val MAX_ENTRIES = 16

    @Volatile
    private var cache: List<Entry> = emptyList()

    private fun modified(rules: RuleConstants, completed: Set<Tech>): RuleConstants {
        var r = rules
        if (Tech.SMITHING in completed) r = r.copy(unitAttackBonus = r.unitAttackBonus + 1)
        if (Tech.ARMORY in completed) r = r.copy(unitDefenseBonus = r.unitDefenseBonus + 1)
        if (Tech.SIEGECRAFT in completed) {
            r = r.copy(catapultStrength = r.catapultStrength + 1, warshipStrength = r.warshipStrength + 1)
        }
        when {
            Tech.TREASURY in completed -> r = r.copy(incomePercent = r.incomePercent + 20)
            Tech.COINAGE in completed -> r = r.copy(incomePercent = r.incomePercent + 10)
        }
        if (Tech.BASTIONS in completed) {
            r = r.copy(
                towerDefense = r.towerDefense + 1,
                strongTowerDefense = r.strongTowerDefense + 1,
                fortressDefense = r.fortressDefense + 1,
                capitalDefense = r.capitalDefense + 1,
            )
        }
        if (Tech.SHIPWRIGHTS in completed) {
            r = r.copy(
                warshipStrength = r.warshipStrength + 1,
                transportCost = (r.transportCost - 5).coerceAtLeast(1),
            )
        }
        if (Tech.ADMIRALTY in completed) {
            r = r.copy(portIncome = r.portIncome + 1, fisheryShoalIncome = r.fisheryShoalIncome + 1)
        }
        return r
    }
}
