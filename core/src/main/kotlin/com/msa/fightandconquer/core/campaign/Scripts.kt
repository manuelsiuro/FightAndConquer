package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.Legality
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import kotlinx.serialization.Serializable

/**
 * A story beat: when [condition] first holds, the director submits [action] through the
 * ordinary engine, so the beat lands in the save's action log and replays exactly.
 *
 * [id] identifies the trigger (recorded in [CampaignTracker.firedScripts] so it plays
 * once) and resolves to the narration string on the app side. It is also carried by the
 * action as [GameAction.RunScript.tag], which is what makes a replayed beat announce
 * itself again without the director having to re-derive anything.
 */
@Serializable
data class ScriptTrigger(
    val id: String,
    val condition: LevelCondition,
    val action: GameAction.RunScript,
)

object Scripts {

    /**
     * The next beat to fire, or null. At most one per call — beats play one at a time so
     * each gets its own animation and narration, and the director simply calls again.
     *
     * A trigger whose action is currently illegal (its landing hex is occupied, say) is
     * **skipped, not consumed**: it stays armed for a later turn rather than being
     * silently swallowed. That is why the legality check happens here and not at
     * authoring time.
     */
    fun next(
        triggers: List<ScriptTrigger>,
        state: GameState,
        seat: PlayerId,
        status: CampaignStatus,
        tracker: CampaignTracker,
    ): ScriptTrigger? = triggers.firstOrNull { trigger ->
        trigger.id !in tracker.firedScripts &&
            Conditions.isSatisfied(trigger.condition, state, seat, status) &&
            Legality.check(state, trigger.action) is LegalityResult.Ok
    }
}
