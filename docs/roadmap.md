# Roadmap & Extension Points

## Designed-for, not yet built

### Campaign / authored maps
`MapDefinition` already supports authored maps (`generatorParams = null`) with the
same serialization as skirmish maps. To build: author JSON (or a small editor that
emits `MapDefinition`), ship under assets, add a campaign list to `MenuScreen`, pass
scripted `RuleConstants`/`PlayerKind`s per level. `MapValidator` should run on every
authored map in a unit test.

### Online multiplayer
The groundwork is deliberate: deterministic reducer, RNG inside `GameState`,
serializable `GameAction` log, replay-based saves. A lockstep model only needs:
action transport + seat authority + hash comparison of post-action states
(`Json.encodeToString(GameState.serializer(), s).hashCode()` is already stable).
Keep any new randomness inside the state RNG or determinism breaks silently —
the determinism tests in `:core` are the tripwire.

### Obvious next features
- Sound/haptics (hook `GameEvent`s in a ViewModel collector — same pattern as toasts).
- Map seed sharing / seed entry in `MenuScreen` (`GameSetup.seed` is already there).
- Multiple autosave slots (`SaveGame` is self-contained; only the repository file
  naming needs work).
- Difficulty per AI seat (plumb a list through `GameSetup` instead of one value).
- Tablet/landscape layout (HUD is the only portrait-specific part).
- Translations: the string *extraction* is done (every user-facing string is in
  `res/values/strings.xml`, with `UiText` carrying resource ids out of the
  ViewModel), so shipping a language is just adding `values-<lang>/strings.xml`.

## How-to recipes

**New building type**: add to `Building`/`BuildingType` (`:core` model), cost/defense
in `RuleConstants` + `Rules.buildingCost`/`defenseOf`, legality in
`Legality.checkBuyBuilding`, income/pipeline effects in `TurnPipeline`, tests; then
`PieceKind` + Blender script + bake (asset-pipeline.md), `BoardScene.buildingKind`,
purchase-card copy in `GameScreen`, info card in `GameViewModel.infoCardFor`.

**New unit tier**: extend `RuleConstants.unitCost/unitUpkeep/maxTier`, check every
`tier - 1` indexing site, AI `MoveGenerator` cheapest-breaker logic handles it
automatically; add `PieceKind.UNIT_T5` + model + `PieceMeshes.unitKind`; keep the
height progression strictly increasing and pips countable.

**New AI difficulty**: add to `Difficulty`, weight branch in `Evaluator.score`,
candidate filtering in `MoveGenerator`, seat wiring in `GameViewModel.newGame`,
and a winrate expectation in `AiSimulationTest`.

**Rule tuning**: change `RuleConstants` defaults → run `:core:test`. The AI
simulation suite is the balance tripwire (termination, winrates, Easy-expands).
Saves embed their full rules snapshot (`SaveCodec` uses `encodeDefaults = true`
precisely for this — tested), so tuning defaults never alters an in-progress game.

## Known gaps / accepted trade-offs

- **Tree-clear animation** is a generic sink, not the doc's "tip-over" (needs X/Z
  rotation support in `Transforms.trs`, which is translate+Y-rot+scale only).
- **Capital silhouette** is gold-roof dominant; faction color shows mainly on walls
  under the cornice. One-line tweak in `art/blender/pieces/capital.py` if desired.
- **Gravestone thumbnail** renders washed-out in Blender previews (near-white on
  near-white) — fine in-game where lighting differs.
- **Pass-and-play banner** shows at new-game start and between seats, but
  Continue-resume skips it (`showOpeningBanner = false` in `continueGame`) — a
  minor privacy gap for resumed hot-seat games. vs-AI has no banner.
- **Camera pose is not saved across Activity recreation**: rotating mid-game
  re-runs `fitCameraOnce` and re-frames the board (menu setup state *is* saved via
  `rememberSaveable`). Hoisting the rig pose into the ViewModel would fix it.
- **The 3D board is not screen-reader navigable** — it exposes a single summary
  content description (turn/player); individual hexes have no semantics.
- **Undo** is per-seat, in-turn only (cleared at `EndTurn`) — by design for
  pass-and-play fairness.
- **Release build** still has `optimization { enable = false }` (no R8) and no
  signing config — required before any store publishing, along with re-checking
  16 KB alignment on the release artifact.
- Menu scripted-test fragility: chip coordinates shift when Continue is visible
  (see ui-hud.md).

## Verification culture (keep it)

Every phase of this project shipped with its own gate: `:core` suites for rules/AI,
on-device screenshots for anything visual, FPS probe for performance, reconcile-
correction warnings for renderer integrity, and adversarial review of designs
before large implementations. When extending the game, define the gate first —
"how will I see this working?" — then build.
