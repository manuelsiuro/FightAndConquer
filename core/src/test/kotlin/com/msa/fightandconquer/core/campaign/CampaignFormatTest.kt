package com.msa.fightandconquer.core.campaign

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.engine.Legality
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.map.MapValidator
import com.msa.fightandconquer.core.model.PlayerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural gate on every shipped mission. A campaign is content, but a *broken* level
 * is a crash on a player's device, so the whole catalogue is parsed, validated and
 * instantiated here on every `:core:test` run.
 */
class CampaignFormatTest {

    private val campaigns = CampaignAssets.campaigns()

    @Test
    fun `the catalogue is present and every file decodes`() {
        assertTrue("no campaigns found in ${CampaignAssets.directory}", campaigns.isNotEmpty())
        campaigns.forEach { assertTrue("campaign ${it.id} has no levels", it.levels.isNotEmpty()) }
    }

    @Test
    fun `campaign and level ids are unique across the catalogue`() {
        val campaignIds = campaigns.map { it.id }
        assertEquals("duplicate campaign ids", campaignIds.size, campaignIds.toSet().size)

        val levelIds = campaigns.flatMap { c -> c.levels.map { it.id } }
        assertEquals("duplicate level ids", levelIds.size, levelIds.toSet().size)

        val orders = campaigns.map { it.order }
        assertEquals("campaigns must have a total display order", orders.size, orders.toSet().size)
    }

    @Test
    fun `every authored map satisfies the engine's structural contract`() {
        forEachLevel { campaign, level ->
            val violations = MapValidator.validateAuthored(level.map)
            assertTrue("${campaign.id}/${level.id}: $violations", violations.isEmpty())
        }
    }

    @Test
    fun `every level instantiates into a consistent opening position`() {
        forEachLevel { campaign, level ->
            val state = LevelFactory.instantiate(level)
            assertInvariants(state)
            assertEquals(
                "${campaign.id}/${level.id}: seat count",
                level.seats.size,
                state.players.size,
            )
            assertEquals(
                "${campaign.id}/${level.id}: exactly one human seat",
                1,
                state.players.count { it.kind is PlayerKind.Human },
            )
            state.players.forEach { player ->
                assertNotNull("${campaign.id}/${level.id}: seat ${player.id} has no capital", player.capital)
                assertTrue(
                    "${campaign.id}/${level.id}: seat ${player.id} starts broke and cut off",
                    player.treasury >= 0,
                )
            }
            assertTrue(
                "${campaign.id}/${level.id}: nobody starts starving",
                state.tiles.values.none { it.starving },
            )
        }
    }

    @Test
    fun `every level states at least one objective and can be scored from turn zero`() {
        forEachLevel { campaign, level ->
            assertTrue("${campaign.id}/${level.id}: no objectives", level.objectives.isNotEmpty())
            val status = Objectives.evaluate(LevelFactory.instantiate(level), CampaignTracker(), level)
            assertEquals(
                "${campaign.id}/${level.id}: already decided before a move is made",
                Verdict.InProgress,
                status.verdict,
            )
            assertEquals(level.objectives.size, status.rows.size)
        }
    }

    @Test
    fun `objectives, failures, hints and scripts only name hexes the map actually has`() {
        forEachLevel { campaign, level ->
            val where = "${campaign.id}/${level.id}"
            val onMap = level.map.tiles.map { it.hex }.toSet()
            level.objectives.forEach { objective ->
                val named = when (objective) {
                    is Objective.CaptureHexes -> objective.hexes
                    is Objective.HoldHexes -> objective.hexes
                    else -> emptyList()
                }
                assertTrue("$where: objective names off-map hexes", onMap.containsAll(named))
            }
            level.failures.filterIsInstance<FailCondition.LoseHexes>().forEach {
                assertTrue("$where: protected hex is off-map", onMap.containsAll(it.hexes))
            }
            level.hints.forEach {
                assertTrue("$where: hint ${it.id} focuses an off-map hex", onMap.containsAll(it.focus))
            }
            level.scripts.forEach { trigger ->
                assertTrue(
                    "$where: script ${trigger.id} spawns off-map",
                    onMap.containsAll(trigger.action.spawns.map { it.hex }),
                )
            }
        }
    }

    @Test
    fun `objectives and conditions only reference seats the level has`() {
        forEachLevel { campaign, level ->
            val where = "${campaign.id}/${level.id}"
            val seats = level.seats.indices
            level.objectives.filterIsInstance<Objective.EliminatePlayer>().forEach {
                assertTrue("$where: objective names seat ${it.seat.value}", it.seat.value in seats)
            }
            level.failures.filterIsInstance<FailCondition.AllyEliminated>().forEach {
                assertTrue("$where: failure names seat ${it.seat.value}", it.seat.value in seats)
            }
            level.scripts.forEach { trigger ->
                trigger.action.spawns.forEach {
                    assertTrue("$where: script spawns for seat ${it.owner.value}", it.owner.value in seats)
                }
                trigger.action.grants.forEach {
                    assertTrue("$where: script pays seat ${it.player.value}", it.player.value in seats)
                }
            }
        }
    }

    /**
     * The static half of solvability, and the half an AI playthrough cannot give us: a
     * hex the mission tells you to take must be *gettable*. Either it shares a landmass
     * with your capital, or the level gives you boats and the hex has a shore.
     */
    @Test
    fun `every hex an objective demands can actually be reached`() {
        forEachLevel { campaign, level ->
            val demanded = level.objectives.flatMap { objective ->
                when (objective) {
                    is Objective.CaptureHexes -> objective.hexes
                    is Objective.HoldHexes -> objective.hexes
                    else -> emptyList()
                }
            }
            if (demanded.isEmpty()) return@forEachLevel

            val where = "${campaign.id}/${level.id}"
            val tiles = level.map.tiles.associateBy { it.hex }
            val land = level.map.tiles
                .filter { it.terrain == com.msa.fightandconquer.core.model.Terrain.LAND }
                .map { it.hex }
                .toSet()
            val sea = level.map.tiles
                .filter { it.terrain == com.msa.fightandconquer.core.model.Terrain.SEA }
                .map { it.hex }
                .toSet()
            val capital = level.map.capitals[level.playerSeat.value]
            val home = com.msa.fightandconquer.core.hex.HexMath.floodFill(capital) { it in land }
            val canSail = level.rules.navalEnabled

            demanded.forEach { hex ->
                val tile = tiles[hex]
                assertTrue("$where: objective hex $hex is not land", tile?.hex in land)
                val walkable = hex in home
                val landable = canSail &&
                    com.msa.fightandconquer.core.hex.HexMath.neighbors(hex).any { it in sea }
                assertTrue(
                    "$where: objective hex $hex is on an unreachable landmass " +
                        "(no land route, and ${if (canSail) "no shore" else "boats are disabled"})",
                    walkable || landable,
                )
            }
        }
    }

    @Test
    fun `hint and script ids are unique within their level`() {
        forEachLevel { campaign, level ->
            val hintIds = level.hints.map { it.id }
            assertEquals("${campaign.id}/${level.id}: duplicate hint id", hintIds.size, hintIds.toSet().size)
            val scriptIds = level.scripts.map { it.id }
            assertEquals("${campaign.id}/${level.id}: duplicate script id", scriptIds.size, scriptIds.toSet().size)
        }
    }

    /**
     * A story beat that is illegal on the opening board is not necessarily broken — it may
     * be waiting for ground the player has yet to take — but one that names a hex nobody
     * can ever own is. This checks the cheap half: the payload is well-formed for the
     * rules the level ships with.
     */
    @Test
    fun `story beats are accepted by the rules their level enables`() {
        forEachLevel { campaign, level ->
            if (level.scripts.isEmpty()) return@forEachLevel
            val state = LevelFactory.instantiate(level)
            assertTrue(
                "${campaign.id}/${level.id}: scripts present but the gate is closed",
                state.config.rules.scriptedEventsEnabled,
            )
            level.scripts.forEach { trigger ->
                val rejected = Legality.check(state, trigger.action) as? LegalityResult.Rejected
                assertTrue(
                    "${campaign.id}/${level.id}: beat '${trigger.id}' can never fire (${rejected?.reason})",
                    rejected?.reason != com.msa.fightandconquer.core.engine.RejectionReason.SCRIPTED_EVENTS_DISABLED,
                )
            }
        }
    }

    private fun forEachLevel(body: (CampaignDef, LevelDef) -> Unit) {
        campaigns.forEach { campaign -> campaign.levels.forEach { body(campaign, it) } }
    }
}
