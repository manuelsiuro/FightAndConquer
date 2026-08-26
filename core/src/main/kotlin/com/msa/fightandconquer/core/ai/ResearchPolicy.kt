package com.msa.fightandconquer.core.ai

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Deposit
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.Tech
import com.msa.fightandconquer.core.model.TechBranch
import com.msa.fightandconquer.core.model.Terrain
import com.msa.fightandconquer.core.model.Tile

/**
 * Research decisions as a threshold policy — the single owner of "build a
 * University" and "start a tech" (never argmax candidates: research completes
 * at turn start, so its state is CONSTANT within any one-ply comparison and no
 * Evaluator term could ever steer the choice; the NavalPolicy structural
 * argument, verbatim). Stateless, deterministic, RNG-free; consulted after
 * diplomacy and before the naval ladder, whose port steps stay dormant until
 * this policy has funded NAVIGATION.
 *
 * Difficulty identities: HARD researches broadly and early (offense-first —
 * SMITHING before ARMORY, STONE last: the anti-turtle ordering), NORMAL runs a
 * short economy-leaning list, EASY researches exactly NAVIGATION and only when
 * sea-locked (the beatable rookie keeps its identity, but the two-island
 * invasion gate must stay reachable).
 */
internal object ResearchPolicy {

    fun action(state: GameState, difficulty: Difficulty): GameAction? {
        if (!state.config.rules.researchEnabled) return null
        val me = state.currentPlayer

        // Yield to defense: never fund scholarship while the throne is threatened.
        if (Tiers.capitalThreat(state, me) > 0) return null

        val partners: Set<PlayerId> =
            if (state.config.rules.diplomacyEnabled) state.diplomacy.partnersOf(me) else emptySet()
        // Sea-locked: a genuine island (no enemy shares my capital's landmass —
        // deliberately NOT NavalPolicy.overseasMode, whose adjacency-only test
        // reads every early land map with neutral buffers as "no land route")
        // and no NAVIGATION yet: research IS the war effort, reserves drop to 0.
        val seaLocked = state.config.rules.navalEnabled &&
            !state.player(me).research.has(Tech.NAVIGATION) &&
            isIsland(state, me, partners)

        val eff = Rules.effectiveRules(state, me)
        val treasury = state.player(me).treasury
        val net = Rules.incomeOf(state, me) - Rules.upkeepOf(state, me)
        val atWar = DiplomacyPolicy.adjacentEnemies(state, me).isNotEmpty()
        val reserve = when {
            seaLocked -> 0
            difficulty == Difficulty.HARD -> if (atWar) 30 else 10
            else -> if (atWar) 40 else 20
        }

        val universities = Rules.workingUniversities(state.tiles, me)
        if (universities == 0) {
            val wants = when {
                seaLocked -> treasury >= eff.universityCost
                difficulty == Difficulty.EASY -> false
                difficulty == Difficulty.HARD ->
                    state.ownedHexCount(me) >= 10 && net >= 6 && treasury >= eff.universityCost + reserve
                else ->
                    state.ownedHexCount(me) >= 12 && net >= 8 && treasury >= eff.universityCost + reserve
            }
            if (wants) {
                universitySpot(state, me)?.let {
                    return GameAction.BuyBuilding(BuildingType.UNIVERSITY, it)
                }
                // Entombed island: no clear hex anywhere for the school the whole
                // escape hangs on — reclaim ground (the naval ladder's 6b idiom).
                if (seaLocked && treasury >= eff.universityCost * 2) {
                    NavalPolicy.demolishForRoom(state, coastal = false)?.let { return it }
                }
            }
            return null
        }

        val research = state.player(me).research
        if (research.active == null) {
            val next = priorityList(state, difficulty, seaLocked).firstOrNull { tech ->
                tech !in research.completed &&
                    tech.prerequisite.let { it == null || it in research.completed }
            }
            if (next != null && treasury >= Rules.techCost(state, me, next) + reserve) {
                return GameAction.StartResearch(next)
            }
        } else if (difficulty == Difficulty.HARD && universities < 2) {
            // Second University: plenty of research left and a healthy economy —
            // the extra point per turn pays for itself over the remaining tree.
            val remaining =
                Rules.techDuration(state, me, research.active.tech) - research.active.progress
            if (remaining >= 4 && net >= 12 && treasury >= eff.universityCost + 40) {
                universitySpot(state, me)?.let {
                    return GameAction.BuyBuilding(BuildingType.UNIVERSITY, it)
                }
            }
        }
        return null
    }

    /**
     * Fixed priority orders per difficulty and situation. Linear-per-branch means
     * the list only decides WHICH branch advances next. SAIL is filtered out of
     * naval-off games; NAVIGATION is absolute-first when sea-locked (all
     * difficulties) and bumps to second on shoal-rich land maps for HARD (the
     * dory economy pays for the tech — shoal positions are chart knowledge,
     * fog-legal by the FishingPolicy convention).
     */
    private fun priorityList(state: GameState, difficulty: Difficulty, seaLocked: Boolean): List<Tech> {
        val naval = state.config.rules.navalEnabled
        val list = when {
            difficulty == Difficulty.EASY ->
                if (seaLocked) listOf(Tech.NAVIGATION) else emptyList()
            seaLocked && difficulty == Difficulty.HARD -> listOf(
                Tech.NAVIGATION, Tech.SMITHING, Tech.SHIPWRIGHTS, Tech.COINAGE, Tech.ARMORY,
                Tech.BANKING, Tech.SIEGECRAFT, Tech.ADMIRALTY, Tech.TREASURY,
                Tech.MASONRY, Tech.ENGINEERING, Tech.BASTIONS,
            )
            seaLocked -> listOf(
                Tech.NAVIGATION, Tech.COINAGE, Tech.SMITHING, Tech.SHIPWRIGHTS,
                Tech.BANKING, Tech.ARMORY, Tech.TREASURY, Tech.ADMIRALTY,
            )
            difficulty == Difficulty.HARD -> {
                val base = listOf(
                    Tech.SMITHING, Tech.COINAGE, Tech.ARMORY, Tech.BANKING, Tech.NAVIGATION,
                    Tech.SIEGECRAFT, Tech.TREASURY, Tech.SHIPWRIGHTS,
                    Tech.MASONRY, Tech.ENGINEERING, Tech.ADMIRALTY, Tech.BASTIONS,
                )
                if (naval && shoalsOnChart(state) >= 2) {
                    listOf(Tech.SMITHING, Tech.NAVIGATION) +
                        base.filterNot { it == Tech.SMITHING || it == Tech.NAVIGATION }
                } else {
                    base
                }
            }
            else -> listOf(
                Tech.COINAGE, Tech.SMITHING, Tech.BANKING, Tech.NAVIGATION,
                Tech.ARMORY, Tech.TREASURY,
            )
        }
        return if (naval) list else list.filterNot { it.branch == TechBranch.SAIL }
    }

    private fun shoalsOnChart(state: GameState): Int =
        state.tiles.values.count { it.deposit == Deposit.FISH_SHOAL }

    /** No living enemy shares my capital's landmass: only the sea leads to war. */
    private fun isIsland(state: GameState, me: PlayerId, partners: Set<PlayerId>): Boolean {
        val capital = state.player(me).capital ?: return false
        if (state.players.none { !it.eliminated && it.id != me && it.id !in partners }) return false
        val landmass = HexMath.floodFill(capital) { state.tiles[it]?.terrain == Terrain.LAND }
        return landmass.none { hex ->
            state.tiles[hex]?.owner?.let { o -> o != me && o !in partners } == true
        }
    }

    /** The shared interior spot chooser (see [interiorBuildSpot]) — pure delegation. */
    private fun universitySpot(state: GameState, me: PlayerId): Hex? = interiorBuildSpot(state, me)
}
