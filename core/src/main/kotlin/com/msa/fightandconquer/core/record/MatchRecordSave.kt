package com.msa.fightandconquer.core.record

import com.msa.fightandconquer.core.engine.Reducer
import com.msa.fightandconquer.core.persist.SaveGame

/**
 * Rebuilds the live match chronicle from a save: the persisted turn-start
 * snapshot plus a re-fold of the replayed turn's actions — the exact
 * [com.msa.fightandconquer.core.campaign.CampaignSave.restoreTracker] pattern,
 * minus the level (the recorder's fold needs none). A resumed level therefore
 * chronicles exactly as one that was never interrupted.
 */
object MatchRecordSave {

    /**
     * [turnStart] defaults to the save's own snapshot; the app overrides it to
     * seed a partial record for a save written before [SaveGame.record] existed.
     * Null in, null out — a record-less legacy save with no seed stays unrecorded.
     */
    fun restore(save: SaveGame, turnStart: MatchRecorderState? = save.record): MatchRecorderState? {
        var record = turnStart ?: return null
        var state = save.turnStartState
        for (action in save.actionsThisTurn) {
            val result = Reducer.reduce(state, action)
            record = MatchRecorderState.step(record, state, result.state, result.events)
            state = result.state
        }
        return record
    }
}
