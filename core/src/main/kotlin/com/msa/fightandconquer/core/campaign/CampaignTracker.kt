package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.engine.DeathCause
import com.msa.fightandconquer.core.engine.GameEvent
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import kotlinx.serialization.Serializable

/**
 * The small slice of campaign state that a [GameState] cannot tell you: how long a hex
 * has been held, which story beats already fired, and the running tallies some
 * objectives count over a whole level.
 *
 * It is advanced by a **pure fold** — [step] over one reducer transition — so it can
 * always be rebuilt by replaying a save's action log (see [CampaignSaveRef]). Nothing
 * here influences the reducer; it is scoreboard, not rules.
 *
 * Collections are kept canonically sorted on write so the serialized save stays
 * byte-stable, exactly like [com.msa.fightandconquer.core.model.DiplomacyState].
 */
@Serializable
data class CampaignTracker(
    /**
     * Objective index -> the round at which its hold streak started, for
     * [Objective.HoldHexes]. Absent while the hexes are not all held.
     * Keyed by index because a level's objective list is fixed at authoring time.
     */
    val holdSince: Map<Int, Int> = emptyMap(),
    /** Ids of [ScriptTrigger]s already fired — each beat plays exactly once. */
    val firedScripts: Set<String> = emptySet(),
    /** Enemy boats the campaign seat has sunk. */
    val boatsSunk: Int = 0,
    /** Units the campaign seat has lost to any cause (merges and embarks excluded). */
    val unitsLost: Int = 0,
    /** Trees the campaign seat has cleared. */
    val treesCleared: Int = 0,
    /**
     * How far the coach script has advanced. Monotonic. Steps gated on a
     * [HintCondition.UiSignal] are bumped by the UI rather than by [step], so a save
     * restored mid-turn may re-show at most the current hint — never an earlier one.
     */
    val hintIndex: Int = 0,
) {

    fun withScriptFired(id: String): CampaignTracker =
        copy(firedScripts = (firedScripts + id).toSortedSet())

    fun withHintIndex(index: Int): CampaignTracker =
        if (index <= hintIndex) this else copy(hintIndex = index)

    companion object {

        /**
         * Folds one reducer transition into the tracker. [before]/[after] bracket the
         * action so event facts can be attributed to an owner (an event carries only a
         * [com.msa.fightandconquer.core.model.UnitId]); [seat] is the campaign player.
         *
         * Pure and RNG-free: replaying the same transitions yields the same tracker.
         */
        fun step(
            prev: CampaignTracker,
            before: GameState,
            after: GameState,
            events: List<GameEvent>,
            seat: PlayerId,
            objectives: List<Objective>,
        ): CampaignTracker {
            var boatsSunk = prev.boatsSunk
            var unitsLost = prev.unitsLost
            var treesCleared = prev.treesCleared
            for (event in events) {
                when (event) {
                    is GameEvent.UnitDied -> {
                        val dead = before.units[event.unit] ?: continue
                        // A voluntary disband is not a loss the objective should count.
                        if (dead.owner == seat && event.cause != DeathCause.DISBANDED) {
                            unitsLost++
                        } else if (event.cause == DeathCause.SUNK && Rules.isNaval(dead.type)) {
                            boatsSunk++
                        }
                    }
                    // The bonus is paid to whoever moved onto the tree, which is the
                    // acting seat at the time of the action.
                    is GameEvent.TreeCleared -> if (before.currentPlayer == seat) treesCleared++
                    else -> Unit
                }
            }

            // Hold streaks: a round of holding is only credited while every named hex is
            // still owned; losing one resets the clock rather than pausing it.
            val holdSince = HashMap(prev.holdSince)
            objectives.forEachIndexed { index, objective ->
                if (objective !is Objective.HoldHexes) return@forEachIndexed
                val holding = objective.hexes.all { after.tiles[it]?.owner == seat }
                if (!holding) holdSince.remove(index) else holdSince.putIfAbsent(index, after.turnNumber)
            }

            return prev.copy(
                holdSince = holdSince.toSortedMap(),
                boatsSunk = boatsSunk,
                unitsLost = unitsLost,
                treesCleared = treesCleared,
            )
        }
    }
}
