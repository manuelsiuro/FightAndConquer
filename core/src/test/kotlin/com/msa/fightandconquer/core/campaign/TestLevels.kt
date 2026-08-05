package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.map.MapDefinition
import com.msa.fightandconquer.core.map.TileDef
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.RuleConstants

/** Level scaffolding for campaign tests — the parts of a level the scorer actually reads. */
object TestLevels {

    /**
     * A 1-row strip level mirroring `TestStates.strip(9, 0..2, 6..8)`, so a test can pair
     * a hand-built micro-state with a level definition that scores it.
     */
    fun strip(
        objectives: List<Objective> = listOf(Objective.ConquerAll),
        failures: List<FailCondition> = emptyList(),
        hints: List<HintStep> = emptyList(),
        scripts: List<ScriptTrigger> = emptyList(),
        parRounds: Int? = null,
    ): LevelDef = LevelDef(
        id = "test_strip",
        seed = 42L,
        map = MapDefinition(
            name = "test_strip",
            tiles = (0 until 9).map { q ->
                TileDef(
                    hex = hex(q),
                    owner = when (q) {
                        in 0..2 -> 0
                        in 6..8 -> 1
                        else -> null
                    },
                    building = if (q == 0 || q == 8) Building.CAPITAL else null,
                )
            },
            capitals = listOf(hex(0), hex(8)),
        ),
        seats = listOf(SeatDef.Player, SeatDef.Ai(Difficulty.PASSIVE)),
        rules = RuleConstants(),
        objectives = objectives,
        failures = failures,
        hints = hints,
        scripts = scripts,
        parRounds = parRounds,
    )
}
