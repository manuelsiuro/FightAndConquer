package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import kotlinx.serialization.Serializable

/**
 * One card of the coach script. The step is shown **until** [until] is satisfied, then
 * the script advances; a level's teaching is a queue, not a state machine, so it can
 * never dead-end.
 *
 * [id] resolves to prose in `strings.xml` on the app side — the engine module has no
 * resources and carries no text. [focus] hexes get a highlight ring, so
 * "move onto the marked hex" needs no coordinates in the prose.
 */
@Serializable
data class HintStep(
    val id: String,
    val until: LevelCondition,
    val focus: List<Hex> = emptyList(),
)

object Hints {

    /**
     * Advances [from] past every step the board already satisfies and returns the index
     * of the step to show (`steps.size` once the script is done).
     *
     * [LevelCondition.Acknowledged] steps never auto-advance — the UI bumps the index
     * itself when the player dismisses the card.
     */
    fun advance(
        steps: List<HintStep>,
        from: Int,
        state: GameState,
        seat: PlayerId,
        status: CampaignStatus,
        uiSignals: Set<String>,
    ): Int {
        var index = from
        while (index < steps.size &&
            Conditions.isSatisfied(steps[index].until, state, seat, status, uiSignals)
        ) {
            index++
        }
        return index
    }
}
