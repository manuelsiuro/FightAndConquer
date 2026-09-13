package com.msa.fightandconquer.core.persist

import com.msa.fightandconquer.core.TestStates.assertInvariants
import com.msa.fightandconquer.core.TestStates.withUnit
import com.msa.fightandconquer.core.engine.GameAction
import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.Legality
import com.msa.fightandconquer.core.engine.LegalityResult
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.engine.RejectionReason
import com.msa.fightandconquer.core.engine.Rules
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.ActiveResearch
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.ResearchState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tech
import com.msa.fightandconquer.core.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Device fixtures for the HUD panel taps and the fresh-only disband rule — and the standing
 * proof of their premise.
 *
 * Two autosaves over the same 61-hex disc, all of it the human's except the enemy capital,
 * so every runtime scenario of the HUD work sits one board tap away from the centre:
 *
 * - the **centre** `(0,0)` is the human's Capital — tapping it opens the economy sheet;
 * - **ring 2** (12 hexes, ~two hex widths out) is all own Universities — tapping one opens
 *   the research sheet;
 * - **ring 3** (18 hexes, ~three hex widths out) each hold an own **spent** peasant —
 *   tapping one shows the info card *without* a Disband action (a spent unit may not
 *   disband). End Turn refreshes them all, so the same taps then offer "Disband +N";
 * - ring 1 is own, empty and buildable — the purchase menu still opens there.
 *
 * The two fixtures differ only in the human's research:
 *
 * - `panels.json` — Smithing done, nothing active (Armory available at 35 > the 30-coin
 *   treasury = the unaffordable cue, Siegecraft locked, the other tier-1 techs available).
 * - `panels_busy.json` — the same plus Armory in progress 2/4, so every other reachable
 *   tech reads BUSY.
 *
 * [premisesHold] asserts all of that on every run (round-trip, invariants, the 12 working
 * Universities, the 18 spent ring-3 peasants and their rejected disbands, the purchase
 * probe, the research state), so the fixtures can never rot silently.
 * [writesFixturesWhenRequested] writes the JSON only when the environment variable
 * `FC_FIXTURES_OUT` names a directory — the ordinary suite is a silent no-op:
 *
 * ```
 * FC_FIXTURES_OUT=/tmp/fixtures ./gradlew :core:test --tests "*PanelTapFixturesTest"
 * ```
 *
 * Pushing one onto a device (force-stop first, or the live app rewrites the autosave from
 * `onStop`), then tap "Continue Game":
 *
 * ```
 * adb shell am force-stop com.msa.fightandconquer
 * adb push panels.json /sdcard/fixture.json
 * adb shell "cat /sdcard/fixture.json | run-as com.msa.fightandconquer sh -c 'cat > files/autosave.json'"
 * ```
 */
class PanelTapFixturesTest {

    /** The human's capital; the board's centre. */
    private val capital0 = Hex.of(0, 0)

    /** The passive AI's capital, the one hex the human does not own. */
    private val capital1 = Hex.of(4, 0)

    /** Own, empty, next to [capital0]: what a purchase tap lands on. */
    private val probe = Hex.of(1, 0)

    /** The 12 hexes exactly two steps out — all own Universities. */
    private val ring2 = HexMath.range(capital0, 2) - HexMath.range(capital0, 1).toSet()

    /** The 18 hexes exactly three steps out — all own spent peasants. */
    private val ring3 = HexMath.range(capital0, 3) - HexMath.range(capital0, 2).toSet()

    /**
     * A 61-hex land disc owned by seat 0 (Human, treasury 30, [research]) except [capital1],
     * held by a [Difficulty.PASSIVE] AI that only ends its turn; Universities on [ring2] and
     * a spent tier-1 soldier on every hex of [ring3].
     */
    private fun fixture(research: ResearchState): GameState {
        val tiles = HexMath.range(capital0, 4).associateWith { hex ->
            Tile(owner = if (hex == capital1) PlayerId(1) else PlayerId(0))
        }.toMutableMap()
        tiles[capital0] = tiles.getValue(capital0).copy(building = Building.CAPITAL)
        tiles[capital1] = tiles.getValue(capital1).copy(building = Building.CAPITAL)
        for (hex in ring2) tiles[hex] = tiles.getValue(hex).copy(building = Building.UNIVERSITY)
        val base = GameState(
            config = GameConfig(seed = SEED, rules = RuleConstants()),
            tiles = tiles,
            units = emptyMap(),
            players = listOf(
                PlayerState(
                    PlayerId(0),
                    PlayerKind.Human,
                    TREASURY,
                    capital0,
                    civ = Civilization.KINGDOM,
                    research = research,
                ),
                PlayerState(
                    PlayerId(1),
                    PlayerKind.Ai(Difficulty.PASSIVE),
                    TREASURY,
                    capital1,
                    civ = Civilization.KINGDOM,
                ),
            ),
            currentPlayer = PlayerId(0),
            rngState = SEED,
        )
        // withUnit keeps units, the tile back-pointer and nextUnitId consistent.
        return ring3.fold(base) { state, hex -> state.withUnit(owner = 0, tier = 1, at = hex, spent = true) }
    }

    /** name -> state, in the order the tester loads them. */
    private fun fixtures(): Map<String, GameState> = linkedMapOf(
        "panels" to fixture(ResearchState.of(listOf(Tech.SMITHING))),
        "panels_busy" to fixture(
            ResearchState.of(listOf(Tech.SMITHING)).copy(active = ActiveResearch(Tech.ARMORY, 2)),
        ),
    )

    private fun encode(state: GameState): String = SaveCodec.encode(SaveGame(turnStartState = state))

    @Test
    fun premisesHold() {
        val states = fixtures()
        for ((name, state) in states) {
            val json = encode(state)
            assertEquals(
                "$name round-trips through SaveCodec",
                state,
                SaveCodec.restore(SaveCodec.decode(json)),
            )
            val engine = GameEngine.fromSave(SaveCodec.decode(json))
            assertEquals("$name decodes to the fixture state", state, engine.state.value)
            assertInvariants(state)

            // Ring 2: a research sheet is one tap away from any of the 12 Universities.
            assertEquals("$name has 12 ring-2 hexes", 12, ring2.size)
            assertEquals(
                "$name has 12 working Universities for seat 0",
                12,
                Rules.workingUniversities(state.tiles, PlayerId(0)),
            )

            // Ring 3: 18 own, spent peasants, none of which may disband (T2's rule).
            assertEquals("$name has 18 ring-3 hexes", 18, ring3.size)
            assertEquals("$name holds exactly 18 units", 18, state.units.size)
            val ringHexes = ring3.toSet()
            for (unit in state.units.values) {
                assertEquals("$name unit ${unit.id} belongs to seat 0", PlayerId(0), unit.owner)
                assertTrue("$name unit ${unit.id} is spent", unit.spent)
                assertTrue("$name unit ${unit.id} stands on ring 3 (at ${unit.hex})", unit.hex in ringHexes)
                val result = Legality.check(state, GameAction.DisbandUnit(unit.id))
                assertEquals(
                    "$name spent unit ${unit.id} refuses to disband",
                    LegalityResult.Rejected(RejectionReason.UNIT_ALREADY_ACTED),
                    result,
                )
            }

            // Ring 1 stays buyable (the purchase menu path) while the capital sells nothing.
            assertTrue(
                "$name sells nothing on the capital $capital0 (got ${engine.buyableAt(capital0)})",
                engine.buyableAt(capital0).isEmpty(),
            )
            val at = engine.buyableAt(probe)
            val affordable = at.filterIsInstance<PurchaseOption.Unit>().filter { it.cost <= TREASURY }
            assertTrue("$name sells a unit within the $TREASURY-coin treasury at $probe (got $at)", affordable.isNotEmpty())

            // Research: the sheet's two states.
            val research = state.players[0].research
            assertTrue("$name has Smithing researched", research.has(Tech.SMITHING))
            when (name) {
                "panels" -> assertEquals("$name researches nothing", null, research.active)
                "panels_busy" -> assertEquals(
                    "$name researches Armory 2/4",
                    ActiveResearch(Tech.ARMORY, 2),
                    research.active,
                )
                else -> throw AssertionError("unknown fixture $name")
            }
        }
        assertEquals("two fixtures", setOf("panels", "panels_busy"), states.keys)
    }

    @Test
    fun writesFixturesWhenRequested() {
        val dir = System.getenv(OUT_ENV)?.takeIf { it.isNotBlank() } ?: return
        val out = File(dir)
        out.mkdirs()
        for ((name, state) in fixtures()) {
            val file = File(out, "$name.json")
            file.writeText(encode(state))
            assertTrue("$name.json written and non-empty", file.isFile && file.length() > 0L)
            println("panel-tap fixture: ${file.absolutePath}")
        }
    }

    private companion object {
        const val SEED = 7L
        const val OUT_ENV = "FC_FIXTURES_OUT"

        /** Coins for both seats: enough for a peasant, short of Armory's 35. */
        const val TREASURY = 30
    }
}
