package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.persist.SaveGame
import kotlinx.serialization.Serializable

/**
 * The campaign half of an autosave: which mission is being played, plus the
 * [CampaignTracker] as it stood at the **turn start** the save snapshots.
 *
 * Attached to [SaveGame] as a defaulted field, so pre-campaign saves decode unchanged
 * (guarded by `persist/LegacySaveTest`).
 */
@Serializable
data class CampaignSaveRef(
    val campaignId: String,
    val levelId: String,
    val tracker: CampaignTracker = CampaignTracker(),
    /** UI teaching moments already observed — hint progress that no board state implies. */
    val uiSignals: Set<String> = emptySet(),
)

object CampaignSave {

    /**
     * Rebuilds the live tracker by replaying the save's turn exactly the way
     * [com.msa.fightandconquer.core.persist.SaveCodec.restore] rebuilds the state — the
     * fold is pure, so a resumed level scores identically to one never interrupted.
     */
    fun restoreTracker(save: SaveGame, level: LevelDef): CampaignTracker {
        val ref = save.campaign ?: return CampaignTracker()
        var state = save.turnStartState
        var tracker = ref.tracker
        for (action in save.actionsThisTurn) {
            val result = Reducer.reduce(state, action)
            tracker = CampaignTracker.step(
                prev = tracker,
                before = state,
                after = result.state,
                events = result.events,
                seat = level.playerSeat,
                objectives = level.objectives,
            )
            state = result.state
        }
        return tracker
    }
}
