package com.msa.fightandconquer.core.model

import kotlinx.serialization.Serializable

/**
 * All tunable rule values, snapshotted into each game's [GameConfig] so saves are
 * self-describing. Values follow docs/game-idea.md where specified, and
 * Slay/Antiyoy conventions where the doc is silent.
 */
@Serializable
data class RuleConstants(
    /** Purchase cost by tier (index = tier - 1). Any tier is directly buyable. */
    val unitCost: List<Int> = listOf(10, 20, 30, 40),
    /** Per-turn upkeep by tier (index = tier - 1). Locked by the design doc. */
    val unitUpkeep: List<Int> = listOf(2, 6, 18, 54),
    val maxTier: Int = 4,

    /**
     * Movement range by soldier tier (index = tier - 1): max BFS steps through
     * the unit's own connected territory per action, the final step of which
     * may capture a frontier hex. Higher tiers march farther. Kept small so a
     * unit's whole reach is readable at a glance (the ships set the pattern:
     * bounded BFS, range shown as a highlight blob).
     */
    val soldierMoveRanges: List<Int> = listOf(3, 4, 5, 6),
    /** Movement range of the Archer (same path rules as [soldierMoveRanges]). */
    val archerMoveRange: Int = 3,

    val hexIncome: Int = 1,
    val farmCostBase: Int = 12,
    /** Each additional farm costs this much more than the previous one. */
    val farmCostStep: Int = 2,
    val farmIncome: Int = 4,

    val towerCost: Int = 15,
    val towerDefense: Int = 2,
    val strongTowerCost: Int = 35,
    val strongTowerDefense: Int = 3,
    val capitalDefense: Int = 1,
    /** Percent of the victim's treasury looted when their capital is captured. */
    val capitalLootPercent: Int = 50,

    val startingTreasury: Int = 12,
    /**
     * NOT wired up: MapGenerator hardcodes the start region as radius-1
     * (= 7 hexes) and never reads this. Kept because the field is frozen into
     * the share-format v1 dictionary (ShareCodec); changing it does nothing.
     */
    val startRegionSize: Int = 7,

    val treeClearBonus: Int = 3,
    /** Chance (percent) for each tree to spread at the affected player's turn start. */
    val treeSpreadPercent: Int = 10,
    /** Percent of neutral land hexes seeded with trees at map generation. */
    val initialTreePercent: Int = 8,

    /** Classic fog of war: unseen hexes hidden, seen-once hexes remembered as terrain. */
    val fogOfWar: Boolean = false,
    /**
     * Vision radius around every owned hex. MUST stay >= 2: every hex an action can
     * target — and every input to defenseOf on those targets — lies within distance 2
     * of owned territory, which is why Legality, reachable() and MoveGenerator need
     * no fog checks (see docs/fog-of-war.md).
     */
    val visionRadiusOwned: Int = 2,
    /** Vision radius around each of the player's units. */
    val visionRadiusUnit: Int = 3,
    /** Vision radius around the player's Capital, Tower and Strong Tower. */
    val visionRadiusBuilding: Int = 4,

    // --- Terrain deposits (expansion) ---
    /** Extra income of a FERTILE hex on top of [hexIncome]. */
    val fertileHexBonus: Int = 1,
    /** Extra income of a FARM standing on a FERTILE hex. */
    val fertileFarmBonus: Int = 2,
    /** Gold veins placed near each capital at generation (0 disables). */
    val goldVeinsPerPlayer: Int = 1,
    /** Distance band (from the capital) for each player's fair gold vein. */
    val goldVeinBandMin: Int = 3,
    val goldVeinBandMax: Int = 6,
    /** Contested extra veins in the map middle, per 150 land hexes. */
    val goldVeinsNeutralPer150Hexes: Int = 1,
    /** Fair FERTILE hexes near each capital (band 2..5; 0 disables). */
    val fertilePerPlayer: Int = 2,
    /** Percent of unprotected neutral land seeded with FERTILE. */
    val fertileNeutralPercent: Int = 3,

    // --- Economy buildings (expansion) ---
    val mineCost: Int = 20,
    val mineIncome: Int = 6,
    val marketCost: Int = 25,
    /** Market income per adjacent owned, non-starving, flora-free hex. */
    val marketNeighborIncome: Int = 1,
    val marketNeighborCap: Int = 5,
    val lumberCampCost: Int = 15,
    /** Lumber camp income per adjacent own tree hex; those trees never spread. */
    val lumberCampTreeIncome: Int = 2,
    val lumberCampTreeCap: Int = 4,
    val watchtowerCost: Int = 8,
    /** Watchtower vision radius (fog games only; it has zero defense). */
    val watchtowerVisionRadius: Int = 6,

    // --- Special units (expansion) ---
    val specialUnitsEnabled: Boolean = true,
    val archerCost: Int = 14,
    val archerUpkeep: Int = 4,
    val archerStrength: Int = 1,
    /** Tower-like coverage of the archer's own hex + adjacent own hexes. */
    val archerAuraDefense: Int = 2,
    val catapultCost: Int = 30,
    val catapultUpkeep: Int = 10,
    /** Catapult attack strength vs units; building defense is ignored entirely. */
    val catapultStrength: Int = 2,
    /** Max hex distance a catapult covers per action (interceptable, slow). */
    val catapultMoveRange: Int = 2,

    // --- Naval (expansion) ---
    val navalEnabled: Boolean = true,
    val transportCost: Int = 15,
    val transportUpkeep: Int = 4,
    /**
     * Max sea-BFS distance per boat action. Every naval move range MUST stay
     * <= [visionRadiusUnit]: boats self-illuminate their whole action radius, which
     * is what keeps Legality/MoveGenerator fog-check-free at sea (the land-side
     * guarantee is [visionRadiusOwned] >= 2; see docs/fog-of-war.md).
     */
    val transportMoveRange: Int = 3,
    val warshipCost: Int = 25,
    val warshipUpkeep: Int = 8,
    /** Warship strength for sinking boats and bombarding; naval ties go to the ATTACKER. */
    val warshipStrength: Int = 2,
    val warshipMoveRange: Int = 3,
    /** The working hull: just under the transport — an economy boat, not a weapon. */
    val fishingBoatCost: Int = 14,
    /** Below the transport's 4: a working boat must not out-cost the ferry it undercuts. */
    val fishingBoatUpkeep: Int = 3,
    /**
     * Earned at turn start while parked on a FISH_SHOAL sea hex — net
     * +3/turn at defaults, deliberately below farm efficiency: the boat's
     * edge is reaching the mid-ocean shoals no fishery can work.
     */
    val fishingBoatIncome: Int = 6,
    /** Same "MUST stay <= visionRadiusUnit" contract as [transportMoveRange]. */
    val fishingBoatMoveRange: Int = 3,
    val portCost: Int = 20,
    val portIncome: Int = 2,
    /**
     * Overseas supply rule D: turns a sea-captured beachhead region survives on
     * its landing stores with no supply line. Each grace tile feeds its whole
     * starving region at the owner's turn start and burns one turn of stores;
     * 0 restores the pre-grace insta-starve. See docs/game-rules.md.
     */
    val beachheadGraceTurns: Int = 3,
    val fisheryCost: Int = 18,
    /** Fishery income per FISH_SHOAL sea hex within [fisheryRange]. */
    val fisheryShoalIncome: Int = 3,
    val fisheryShoalCap: Int = 3,
    /**
     * Operating radius of a fishery: it places against and earns from shoals up
     * to this many hexes away. MUST stay <= [visionRadiusOwned]: the placement
     * check reads sea hexes at this distance from an owned land hex, and staying
     * inside guaranteed own-hex vision is what keeps Legality fog-check-free.
     */
    val fisheryRange: Int = 2,
    /** Flat cost per bridge hex (chains grow hex by hex; no income, no upkeep). */
    val bridgeCost: Int = 15,
    /**
     * Percent of a piece's cost refunded when its owner demolishes a building or
     * disbands a unit (integer division). Below 100 so build-then-demolish always
     * loses money; a demolished FARM refunds against the LAST farm's price.
     */
    val demolishRefundPercent: Int = 50,
    /** Fair fish shoals near each capital's coast (0 disables). */
    val fishShoalsPerPlayer: Int = 1,
    val fishShoalBandMin: Int = 2,
    val fishShoalBandMax: Int = 6,
    /** Contested neutral shoals in open water, per 150 land hexes. */
    val fishShoalsNeutralPer150Hexes: Int = 1,

    // --- Diplomacy (expansion) ---
    val diplomacyEnabled: Boolean = true,
    val pactMinDurationRounds: Int = 2,
    val pactMaxDurationRounds: Int = 10,
    /** Unanswered proposals lapse after the target had this many full rounds. */
    val pactProposalTtlRounds: Int = 1,
    /** Rounds before the same pair may exchange another proposal (anti-spam). */
    val pactProposalCooldownRounds: Int = 6,
    /** Percent of the breaker's treasury paid to the victim on a pact break. */
    val pactBreakPenaltyPercent: Int = 25,

    // --- Civilizations (expansion) ---
    /**
     * Master gate for per-civilization rule deltas (see [CivModifiers]). Off, every
     * civilization plays with these base rules (the civ picks art only). Defaulted on —
     * pre-civilization saves decode as all-[Civilization.KINGDOM], whose delta is the
     * identity, so legacy replays stay bit-identical either way.
     */
    val civBonusesEnabled: Boolean = true,

    // --- Campaign ---
    /**
     * Buildings this game does not offer at all. Empty in skirmish; a campaign level
     * uses it to teach one structure at a time, so the purchase tray narrows itself
     * with no gating code anywhere else. Enforced in `Legality.checkBuyBuilding`, so
     * the AI cannot build them either.
     */
    val disabledBuildings: Set<BuildingType> = emptySet(),
    /**
     * Allows [com.msa.fightandconquer.core.engine.GameAction.RunScript] — the campaign
     * director's story beats. **Off by default**: a skirmish game can never contain a
     * scripted event, so nothing outside a campaign level can spawn free units.
     */
    val scriptedEventsEnabled: Boolean = false,
) {
    /**
     * Structural invariants every rules instance must satisfy — the documented
     * "MUST stay" contracts, enforced. kotlinx deserialization runs this block,
     * so a hand-edited save or campaign level that would crash the engine later
     * (or silently break the fog contract) fails loudly at load instead.
     * Deliberately weaker than [CivModifiers]' per-delta validation: zeros are
     * legal here (campaign tutorials ship `hexIncome = 0`, `unitUpkeep = [0,0,0,0]`).
     */
    init {
        require(maxTier in 1..minOf(unitCost.size, unitUpkeep.size)) {
            "maxTier $maxTier needs a cost and upkeep entry per tier " +
                "(unitCost has ${unitCost.size}, unitUpkeep has ${unitUpkeep.size})"
        }
        require(unitCost.all { it > 0 }) { "unitCost must stay > 0: $unitCost" }
        require(unitUpkeep.all { it >= 0 }) { "unitUpkeep must stay >= 0: $unitUpkeep" }
        require(soldierMoveRanges.isNotEmpty() && soldierMoveRanges.all { it >= 1 }) {
            "soldierMoveRanges must hold at least one range >= 1: $soldierMoveRanges"
        }
        require(hexIncome >= 0) { "hexIncome must stay >= 0: $hexIncome" }
        // Fog contracts (docs/fog-of-war.md): these are what keep Legality,
        // reachable() and MoveGenerator free of fog checks.
        require(visionRadiusOwned >= 2) { "visionRadiusOwned must stay >= 2: $visionRadiusOwned" }
        require(
            transportMoveRange <= visionRadiusUnit &&
                warshipMoveRange <= visionRadiusUnit &&
                fishingBoatMoveRange <= visionRadiusUnit,
        ) {
            "naval move ranges (transport $transportMoveRange, warship $warshipMoveRange, " +
                "fishing boat $fishingBoatMoveRange) must stay <= visionRadiusUnit $visionRadiusUnit"
        }
        require(fisheryRange <= visionRadiusOwned) {
            "fisheryRange $fisheryRange must stay <= visionRadiusOwned $visionRadiusOwned"
        }
        // Below 100 so build-then-demolish always loses money (see the field doc).
        require(demolishRefundPercent in 0..99) {
            "demolishRefundPercent must stay in 0..99: $demolishRefundPercent"
        }
        require(capitalLootPercent in 0..100) { "capitalLootPercent must stay in 0..100: $capitalLootPercent" }
        require(pactBreakPenaltyPercent in 0..100) {
            "pactBreakPenaltyPercent must stay in 0..100: $pactBreakPenaltyPercent"
        }
        require(treeSpreadPercent in 0..100) { "treeSpreadPercent must stay in 0..100: $treeSpreadPercent" }
        require(goldVeinBandMin <= goldVeinBandMax) {
            "gold vein band inverted: $goldVeinBandMin..$goldVeinBandMax"
        }
        require(fishShoalBandMin <= fishShoalBandMax) {
            "fish shoal band inverted: $fishShoalBandMin..$fishShoalBandMax"
        }
        require(pactMinDurationRounds <= pactMaxDurationRounds) {
            "pact duration band inverted: $pactMinDurationRounds..$pactMaxDurationRounds"
        }
    }
}
