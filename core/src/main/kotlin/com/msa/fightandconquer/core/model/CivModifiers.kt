package com.msa.fightandconquer.core.model

/**
 * The per-civilization rule-delta table: light asymmetry only. Civilizations modify
 * special-unit stats (archer/catapult/transport/warship cost, upkeep, strength, move
 * range), building costs, building/deposit income, and the vision-building cost —
 * NEVER the soldier ladder (tier 1–4 cost/upkeep/strength == tier/move ranges stays
 * universal; the AI's MoveGenerator/NavalPolicy hardcode those assumptions).
 *
 * Resolution happens through [effective]: the rest of the engine asks
 * `Rules.effectiveRules(state, player)` and reads plain [RuleConstants] fields, so no
 * rule site ever branches on a civilization.
 *
 * Flavor of each civ:
 * - [Civilization.KINGDOM] — the baseline. No deltas at all: `effective` returns the
 *   rules instance unchanged, which is what keeps pre-civilization saves (all-Kingdom)
 *   replaying bit-identically.
 * - [Civilization.VIKINGS] — sea raiders: fearsome warships (+1 strength) and a cheap
 *   ferry navy (transports and ports −5), paid for by clumsy farming (farm cost step
 *   +1) and unloved archery (archer +4).
 * - [Civilization.SULTANATE] — desert merchants: cheap markets (−4), richer mines
 *   (+1 income) and easy first farms (base −2), but shipwrights overcharge (warship
 *   +5) and timber is scarce (lumber-camp tree income −1).
 * - [Civilization.SHOGUNATE] — defensive discipline: cheap towers and watchtowers
 *   (−3 each), frugal archers (upkeep −1) and drilled siege crews (catapult range +1),
 *   while the isolationist coast makes ports and transports +5.
 */
object CivModifiers {

    /**
     * The rules [civ] actually plays with. Identity (the same instance) for
     * [Civilization.KINGDOM] and whenever [RuleConstants.civBonusesEnabled] is off.
     * Pure and deterministic; memoized per base-rules instance (a game holds one
     * immutable [RuleConstants], so the one-slot cache hits on every call).
     */
    fun effective(rules: RuleConstants, civ: Civilization): RuleConstants {
        if (civ == Civilization.KINGDOM || !rules.civBonusesEnabled) return rules
        val cached = cache
        val entry = if (cached != null && cached.base === rules) {
            cached
        } else {
            Entry(rules, Civilization.entries.map { modified(rules, it) }).also { cache = it }
        }
        return entry.byCiv[civ.ordinal]
    }

    /** One base-rules instance with all four civ variants, swapped atomically. */
    private class Entry(val base: RuleConstants, val byCiv: List<RuleConstants>)

    @Volatile
    private var cache: Entry? = null

    private fun modified(rules: RuleConstants, civ: Civilization): RuleConstants = when (civ) {
        Civilization.KINGDOM -> rules
        Civilization.VIKINGS -> rules.copy(
            warshipStrength = rules.warshipStrength + 1,
            transportCost = rules.transportCost - 5,
            portCost = rules.portCost - 5,
            farmCostStep = rules.farmCostStep + 1,
            archerCost = rules.archerCost + 4,
        )
        Civilization.SULTANATE -> rules.copy(
            marketCost = rules.marketCost - 4,
            mineIncome = rules.mineIncome + 1,
            farmCostBase = rules.farmCostBase - 2,
            warshipCost = rules.warshipCost + 5,
            lumberCampTreeIncome = rules.lumberCampTreeIncome - 1,
        )
        Civilization.SHOGUNATE -> rules.copy(
            towerCost = rules.towerCost - 3,
            watchtowerCost = rules.watchtowerCost - 3,
            archerUpkeep = rules.archerUpkeep - 1,
            catapultMoveRange = rules.catapultMoveRange + 1,
            portCost = rules.portCost + 5,
            transportCost = rules.transportCost + 5,
        )
    }.also { validate(it, civ) }

    /** Every civ-touchable cost/income must stay > 0; upkeep/strength/range >= 0. */
    private fun validate(r: RuleConstants, civ: Civilization) {
        fun positive(name: String, value: Int) =
            require(value > 0) { "$civ drives $name to $value (must stay > 0)" }
        fun nonNegative(name: String, value: Int) =
            require(value >= 0) { "$civ drives $name to $value (must stay >= 0)" }
        positive("farmCostBase", r.farmCostBase)
        positive("towerCost", r.towerCost)
        positive("strongTowerCost", r.strongTowerCost)
        positive("mineCost", r.mineCost)
        positive("marketCost", r.marketCost)
        positive("lumberCampCost", r.lumberCampCost)
        positive("watchtowerCost", r.watchtowerCost)
        positive("portCost", r.portCost)
        positive("fisheryCost", r.fisheryCost)
        positive("bridgeCost", r.bridgeCost)
        positive("archerCost", r.archerCost)
        positive("catapultCost", r.catapultCost)
        positive("transportCost", r.transportCost)
        positive("warshipCost", r.warshipCost)
        positive("farmIncome", r.farmIncome)
        positive("mineIncome", r.mineIncome)
        positive("marketNeighborIncome", r.marketNeighborIncome)
        positive("lumberCampTreeIncome", r.lumberCampTreeIncome)
        positive("portIncome", r.portIncome)
        positive("fisheryShoalIncome", r.fisheryShoalIncome)
        nonNegative("farmCostStep", r.farmCostStep)
        nonNegative("archerUpkeep", r.archerUpkeep)
        nonNegative("catapultUpkeep", r.catapultUpkeep)
        nonNegative("transportUpkeep", r.transportUpkeep)
        nonNegative("warshipUpkeep", r.warshipUpkeep)
        nonNegative("archerStrength", r.archerStrength)
        nonNegative("catapultStrength", r.catapultStrength)
        nonNegative("warshipStrength", r.warshipStrength)
        nonNegative("archerMoveRange", r.archerMoveRange)
        nonNegative("catapultMoveRange", r.catapultMoveRange)
        nonNegative("transportMoveRange", r.transportMoveRange)
        nonNegative("warshipMoveRange", r.warshipMoveRange)
    }
}
