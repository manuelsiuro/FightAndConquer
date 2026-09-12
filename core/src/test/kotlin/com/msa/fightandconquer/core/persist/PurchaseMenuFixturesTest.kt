package com.msa.fightandconquer.core.persist

import com.msa.fightandconquer.core.engine.GameEngine
import com.msa.fightandconquer.core.engine.PurchaseOption
import com.msa.fightandconquer.core.hex.Hex
import com.msa.fightandconquer.core.hex.HexMath
import com.msa.fightandconquer.core.model.Building
import com.msa.fightandconquer.core.model.BuildingType
import com.msa.fightandconquer.core.model.Civilization
import com.msa.fightandconquer.core.model.Difficulty
import com.msa.fightandconquer.core.model.GameConfig
import com.msa.fightandconquer.core.model.GameState
import com.msa.fightandconquer.core.model.PlayerId
import com.msa.fightandconquer.core.model.PlayerKind
import com.msa.fightandconquer.core.model.PlayerState
import com.msa.fightandconquer.core.model.RuleConstants
import com.msa.fightandconquer.core.model.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Device fixtures for the purchase menu (Recruit / Build pair) — and the standing proof
 * of their premise.
 *
 * Three autosaves over the same 61-hex disc, all of it the human's except the enemy
 * capital, so **any** board tap away from the centre selects an own empty hex:
 *
 * - `both.json` — treasury 100, default rules: the hex sells units *and* structures.
 * - `recruit_only.json` — treasury 100, every [BuildingType] disabled: units only.
 * - `build_only.json` — treasury 20 with a 30-gold peasant: structures only.
 *
 * [premisesHold] asserts exactly that through [GameEngine.buyableAt] on every run, so the
 * fixtures can never rot silently. [writesFixturesWhenRequested] writes the JSON only when
 * the environment variable `FC_FIXTURES_OUT` names a directory — the ordinary suite is a
 * silent no-op:
 *
 * ```
 * FC_FIXTURES_OUT=/tmp/fixtures ./gradlew :core:test --tests "*PurchaseMenuFixturesTest"
 * ```
 *
 * Pushing one onto a device (force-stop first, or the live app rewrites the autosave from
 * `onStop`), then tap "Continue Game":
 *
 * ```
 * adb shell am force-stop com.msa.fightandconquer
 * adb push both.json /sdcard/fixture.json
 * adb shell "cat /sdcard/fixture.json | run-as com.msa.fightandconquer sh -c 'cat > files/autosave.json'"
 * ```
 */
class PurchaseMenuFixturesTest {

    /** The human's capital; the board's centre. */
    private val capital0 = Hex.of(0, 0)

    /** The passive AI's capital, the one hex the human does not own. */
    private val capital1 = Hex.of(4, 0)

    /** Own, empty, next to [capital0]: what a device tap away from the centre lands on. */
    private val probe = Hex.of(1, 0)

    /**
     * A 61-hex land disc owned by seat 0 (Human) except [capital1], held by a
     * [Difficulty.PASSIVE] AI that only ends its turn.
     */
    private fun fixture(treasury: Int, rules: RuleConstants): GameState {
        val tiles = HexMath.range(capital0, 4).associateWith { hex ->
            Tile(owner = if (hex == capital1) PlayerId(1) else PlayerId(0))
        }.toMutableMap()
        tiles[capital0] = tiles.getValue(capital0).copy(building = Building.CAPITAL)
        tiles[capital1] = tiles.getValue(capital1).copy(building = Building.CAPITAL)
        return GameState(
            config = GameConfig(seed = SEED, rules = rules),
            tiles = tiles,
            units = emptyMap(),
            players = listOf(
                PlayerState(PlayerId(0), PlayerKind.Human, treasury, capital0, civ = Civilization.KINGDOM),
                PlayerState(
                    PlayerId(1),
                    PlayerKind.Ai(Difficulty.PASSIVE),
                    treasury,
                    capital1,
                    civ = Civilization.KINGDOM,
                ),
            ),
            currentPlayer = PlayerId(0),
            rngState = SEED,
        )
    }

    /** name -> state, in the order the tester loads them. */
    private fun fixtures(): Map<String, GameState> = linkedMapOf(
        "both" to fixture(treasury = 100, rules = RuleConstants()),
        "recruit_only" to fixture(
            treasury = 100,
            rules = RuleConstants(disabledBuildings = BuildingType.entries.toSet()),
        ),
        // A peasant costs 30 > 20, so no unit is affordable; a farm (12) and a
        // watchtower (8) still are. No specials, no boats: nothing else to recruit.
        "build_only" to fixture(
            treasury = 20,
            rules = RuleConstants(
                maxTier = 1,
                unitCost = listOf(30, 40, 50, 60),
                specialUnitsEnabled = false,
                navalEnabled = false,
            ),
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
            val at = engine.buyableAt(probe)
            val units = at.filterIsInstance<PurchaseOption.Unit>()
            val structures = at.filterIsInstance<PurchaseOption.Structure>()
            when (name) {
                "both" -> {
                    assertTrue("both sells a unit at $probe (got $at)", units.isNotEmpty())
                    assertTrue("both sells a structure at $probe (got $at)", structures.isNotEmpty())
                    assertTrue(
                        "both sells nothing on the capital $capital0 (got ${engine.buyableAt(capital0)})",
                        engine.buyableAt(capital0).isEmpty(),
                    )
                }
                "recruit_only" -> {
                    assertTrue("recruit_only sells a unit at $probe (got $at)", units.isNotEmpty())
                    assertEquals(
                        "recruit_only sells no structure at $probe",
                        emptyList<PurchaseOption.Structure>(),
                        structures,
                    )
                }
                "build_only" -> {
                    assertEquals(
                        "build_only sells no unit at $probe",
                        emptyList<PurchaseOption.Unit>(),
                        units,
                    )
                    assertTrue("build_only sells a structure at $probe (got $at)", structures.isNotEmpty())
                }
                else -> throw AssertionError("unknown fixture $name")
            }
        }
        assertEquals("three fixtures", setOf("both", "recruit_only", "build_only"), states.keys)
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
            println("purchase-menu fixture: ${file.absolutePath}")
        }
    }

    private companion object {
        const val SEED = 7L
        const val OUT_ENV = "FC_FIXTURES_OUT"
    }
}
