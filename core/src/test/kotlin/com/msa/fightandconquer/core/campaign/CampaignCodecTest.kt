package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.TestStates.hex
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.ScriptGrant
import com.msa.fightandconquer.core.engine.ScriptSpawn
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.UnitType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round-trips **every** variant of the authored vocabulary.
 *
 * The trap this guards against has bitten this codebase before: a property named `type`
 * on a `@Serializable` sealed subclass collides with the polymorphic discriminator, and
 * the failure is silent until something tries to decode it (see the comment on
 * `GameAction.BuyBuilding`). Levels are decoded from disk at runtime, so an unlisted
 * variant would fail on a player's device rather than here.
 */
class CampaignCodecTest {

    private val everyObjective = listOf(
        Objective.ConquerAll,
        Objective.CaptureHexes(listOf(hex(1), hex(2))),
        Objective.HoldHexes(listOf(hex(3)), rounds = 4),
        Objective.SurviveRounds(6),
        Objective.OwnHexCount(12),
        Objective.ReachTreasury(80),
        Objective.ReachIncome(20),
        Objective.EliminatePlayer(PlayerId(1)),
        Objective.BuildCount(BuildingType.PORT, 2),
        Objective.FieldUnits(UnitType.CATAPULT, 1),
        Objective.SinkBoats(3),
    )

    private val everyFailure = listOf(
        FailCondition.TurnLimit(20),
        FailCondition.LoseHexes(listOf(hex(0))),
        FailCondition.LoseAllUnits,
        FailCondition.AllyEliminated(PlayerId(1)),
    )

    private val everyCondition = listOf(
        LevelCondition.Acknowledged,
        LevelCondition.UiSignal("unitSelected"),
        LevelCondition.OwnHexCountAtLeast(5),
        LevelCondition.RoundAtLeast(3),
        LevelCondition.UnitCountAtLeast(UnitType.TRANSPORT, 1, tier = 1),
        LevelCondition.BuildingCountAtLeast(BuildingType.FARM, 2),
        LevelCondition.TreasuryAtLeast(50),
        LevelCondition.IncomeAtLeast(15),
        LevelCondition.ObjectiveDone(0),
        LevelCondition.OwnsHexes(listOf(hex(4))),
        LevelCondition.LostAnyHex(listOf(hex(5))),
        LevelCondition.PlayerEliminated(PlayerId(1)),
        LevelCondition.All(listOf(LevelCondition.RoundAtLeast(2), LevelCondition.TreasuryAtLeast(10))),
    )

    @Test
    fun `every authored variant survives a JSON round trip`() {
        val level = TestLevels.strip(
            objectives = everyObjective,
            failures = everyFailure,
            civs = listOf(
                com.msa.fightandconquer.core.model.Civilization.VIKINGS,
                com.msa.fightandconquer.core.model.Civilization.SHOGUNATE,
            ),
            hints = everyCondition.mapIndexed { i, c -> HintStep("h$i", c, listOf(hex(1))) },
            scripts = everyCondition.mapIndexed { i, c ->
                ScriptTrigger(
                    id = "s$i",
                    condition = c,
                    action = GameAction.RunScript(
                        tag = "s$i",
                        spawns = listOf(ScriptSpawn(PlayerId(0), hex(1), UnitType.ARCHER, tier = 1)),
                        grants = listOf(ScriptGrant(PlayerId(0), 10)),
                    ),
                )
            },
        )
        val campaign = CampaignDef(id = "roundtrip", levels = listOf(level))

        val decoded = CampaignCodec.decode(CampaignCodec.encode(campaign))

        assertEquals(campaign, decoded)
    }

    @Test
    fun `a level written with only its overrides decodes to the engine defaults`() {
        // How shipped assets are written: no rules block at all means default rules.
        val minimal = """
            {"id":"c","order":0,"levels":[{"id":"lvl","seed":7,
             "map":{"name":"m","tiles":[{"hex":0,"owner":0,"building":"CAPITAL"}],"capitals":[0]},
             "seats":[{"type":"player"}],
             "objectives":[{"type":"conquerAll"}]}]}
        """.trimIndent()

        val level = CampaignCodec.decode(minimal).levels.single()

        assertEquals(com.msa.fightandconquer.core.model.RuleConstants(), level.rules)
        assertEquals(listOf(Objective.ConquerAll), level.objectives)
        assertEquals(emptyList<FailCondition>(), level.failures)
        assertEquals(null, level.parRounds)
        assertEquals(true, level.aiSolvable)
    }
}
