# Roadmap & Extension Points

## Shipped: the economy & diplomacy expansion

The `feature/expansion` work landed in three save-compatible milestones (all
defaulted serialized fields; pre-expansion saves load and replay unchanged —
guarded by `persist/LegacySaveTest`):

1. **Terrain economy** — GOLD_VEIN/FERTILE deposits (`Tile.deposit`, fair-by-
   construction placement in `MapGenerator.placeDeposits`) + Mine, Market,
   Lumber Camp and the fog Watchtower (previously listed below as a follow-up).
2. **Special units** — `UnitType.ARCHER/CATAPULT` beside the soldier ladder
   (aura defense / building-defense bypass, range cap, no merging).
3. **Light diplomacy** — pacts + tribute as replayable actions
   (`GameState.diplomacy`), auto-break-on-attack with treasury penalty, and a
   deterministic RNG-free `ai/DiplomacyPolicy` (accept/propose/tribute/betray
   thresholds with hysteresis bands and state-side cooldowns).

Feature gates for A/B and classic play: `specialUnitsEnabled`,
`diplomacyEnabled`, and zeroed deposit counts in `RuleConstants`.

## Shipped: the naval expansion

The `feature/naval-expansion` work (tester-requested: islands, sea, boats,
bridges) landed in three save-compatible phases, same discipline as above
(defaulted fields, `LegacySaveTest`-guarded):

1. **Sea as first-class terrain** — `Tile.terrain` (LAND/SEA), map-type Setup
   option (Continent + coastal fringe / Islands / Archipelago with real
   water-separated islands and navigable channels), animated `water.mat`
   rendering, terrain-aware picking.
2. **Naval units & overseas play** — TRANSPORT (capacity-1 embark/disembark,
   amphibious assault) and WARSHIP (sink-with-ties-to-attacker, `Bombard`
   raids), PORT (boat vendor + overseas supply rules A/B/C), and a
   deterministic `ai/NavalPolicy` invasion ladder so island AI games terminate.
3. **Sea economy** — BRIDGE (the one building ON a sea hex; owned, walkable,
   blocks boats, bombardable), FISHERY + FISH_SHOAL deposits with the standard
   fairness machinery, AI economy terms, HARD-only warship interdiction.
4. **Fishing overhaul** — FISHERY works shoals at range (`fisheryRange` 2, the
   shared `Rules.shoalsWithin` query), FISHING_BOAT (the dory: park-on-shoal
   income, the game's only earning unit, `ai/FishingPolicy` ladder), workable
   per-capital shoal placement, mid-ocean neutral shoals as boat territory.

Feature gate: `navalEnabled` plus zeroed shoal counts in `RuleConstants`.
Full rules in [game-rules.md](game-rules.md); engine details in
[core-engine.md](core-engine.md).

## Shipped: campaign mode

The `feature/campaign` work landed three campaigns of authored missions — **The Academy**
(8-mission tutorial), **The Sundered Isles** (6 naval chapters) and **The Iron Crown**
(6 land/economy chapters) — on the authored-map path `MapDefinition` was always built for
(`generatorParams = null`). Save-compatible by the same discipline as the earlier
expansions (`SaveGame.campaign` and the two new `RuleConstants` fields are defaulted;
`persist/LegacySaveTest` guards it).

The load-bearing decision: **objective scoring is a pure function beside the reducer**, not
inside it, so `GamePhase` still means "the conquest is over" and determinism, save replay
and the AI are untouched. Levels are ASCII-art sources baked to JSON assets by
`tools/build_campaigns.py`; story beats are a new replayable `GameAction.RunScript`
(gated off outside campaigns); the tutorial teaches by *switching rules off*
(`disabledBuildings`, `maxTier`, the feature flags) rather than by gating actions.
Full spec: [campaign.md](campaign.md).

## Shipped: civilizations

The `feature/civilizations` work (per-seat identity: art set + light rule deltas)
landed in three save-compatible phases, same discipline as above (defaulted fields —
pre-civ saves decode as all-Kingdom; `LegacySaveTest`-guarded):

1. **Identity** — the `Civilization` enum (KINGDOM baseline / VIKINGS / SULTANATE /
   SHOGUNATE) on `PlayerState.civ`, per-seat picker in `SetupScreen`,
   `LevelDef.civs` for campaign/custom maps (`MapViolation.CivsSizeMismatch`).
2. **Modifiers** — the `CivModifiers` delta table resolved through
   `Rules.effectiveRules(state, player)` so no rule site branches on a civ; the
   soldier ladder stays universal (AI `MoveGenerator`/`NavalPolicy` assumptions);
   AI affordability reads effective rules.
3. **Art** — per-civ Blender sets (`art/blender/pieces/<civ>/`, 19 player-owned
   kinds each; neutral markers never fork), (civ, kind)-keyed `PieceMeshes` +
   `PieceIcons` with lazy per-civ preload and shared-instance Kingdom fallback.

Feature gate: `civBonusesEnabled` (default on; off = art only). Full spec in
[civilizations.md](civilizations.md).

## Designed-for, not yet built

### Map editor — SHIPPED
Landed as designed (see [map-editor.md](map-editor.md)): the editor emits a
`CustomMapDef` wrapping a real `LevelDef`, validated by typed `MapViolation` codes
(the old `validateAuthored` prose is now `codes.map { describe() }`), stored at
`filesDir/maps/`, played through `LevelFactory` under the `@custom` sentinel, and
shared as text code / `.fcmap` file / QR / steganographic image over one `FCM1`
envelope. Deferred follow-ups: live camera QR scanning (zxing-android-embedded +
the app's first runtime permission), `.fcmap` ACTION_VIEW registration, authored
hints/scripts for custom maps, seat labels floating over capitals.

### Online multiplayer
The groundwork is deliberate: deterministic reducer, RNG inside `GameState`,
serializable `GameAction` log, replay-based saves. A lockstep model only needs:
action transport + seat authority + hash comparison of post-action states
(`Json.encodeToString(GameState.serializer(), s).hashCode()` is already stable).
Keep any new randomness inside the state RNG or determinism breaks silently —
the determinism tests in `:core` are the tripwire.

### Obvious next features
- Settings screen — the menu entry and `Screen.Settings` exist but render
  `PlaceholderScreen`. Nothing is persisted yet; a preferences store would be the
  first piece (sound, haptics, default setup choices).
- Sound/haptics (hook `GameEvent`s in a ViewModel collector — same pattern as toasts).
- Map seed sharing / seed entry in `SetupScreen` (`GameSetup.seed` is already there).
- Multiple autosave slots (`SaveGame` is self-contained; only the repository file
  naming needs work).
- Difficulty per AI seat (plumb a list through `GameSetup` instead of one value).
- Fog-of-war follow-ups — softer fog visuals, last-known-piece memory: evaluated
  with effort/impact ratings in
  [fog-of-war.md](fog-of-war.md#extension-proposals-evaluated-not-implemented).
  (The Watchtower from that list shipped with the expansion.)
- Diplomacy follow-ups — full alliances (shared vision, passage, joint victory)
  and an economic victory condition. (The menu toggle rows for
  `specialUnitsEnabled`/`diplomacyEnabled` shipped with the UI-polish pass.)
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

**New civilization**: add the `Civilization` entry (name-serialized — no new save
keys), its delta arm in `CivModifiers.modified` (never the soldier ladder), strings +
`civNameRes`/guide mapping, the `PieceIcons` branch (Kingdom drawables until icons
ship), then 19 Blender scripts under `art/blender/pieces/<name>/` + `glb2pmesh.py
--all` + `render_piece_icons.py <name>/<kind>` — art lands incrementally over the
Kingdom fallback. Tests: `CivModifiersTest`, `CampaignCodecTest` round-trip,
`PieceMeshLoaderTest`, an `AiSimulationTest` mixed-civ run (`civs` param). Full
recipe in [civilizations.md](civilizations.md#adding-a-fifth-civilization).

**New AI difficulty**: add to `Difficulty` (and to `Difficulty.selectable` if players may
pick it), weight branch in `Evaluator.score`, candidate filtering in `MoveGenerator`, seat
wiring in `GameViewModel.newGame`, and a winrate expectation in `AiSimulationTest`.

**New campaign mission**: add a level dict to `tools/campaign_src/<campaign>.py`, bake with
`python3 tools/build_campaigns.py <campaign>`, add its strings and `CampaignText` ids, then
run `:core:test` + `:app:testDebugUnitTest` — the campaign suites check the map, the
objective's reachability, the clocks and the copy. Recipe in [campaign.md](campaign.md).

**New objective or defeat clause**: add the variant to `Objective`/`FailCondition`, a
branch in `Objectives.row`/`verdict`, a label in `ui/campaign/CampaignText.kt` (exhaustive
`when`, so it fails to compile until it has a string), and an entry in
`CampaignCodecTest`'s round-trip list.

**Rule tuning**: change `RuleConstants` defaults → run `:core:test`. The AI
simulation suite is the balance tripwire (termination, winrates, Easy-expands).
Saves embed their full rules snapshot (`SaveCodec` uses `encodeDefaults = true`
precisely for this — tested), so tuning defaults never alters an in-progress game.

## Known gaps / accepted trade-offs

- **HARD's winrate vs EASY sits at ~60%** (measured over 30 seeds / 60 mirror
  games) since the evaluator's day-one market bug was fixed (it credited a 6th
  market neighbor that never pays income; `AiSimulationTest` documents the
  recalibrated 55% bar). Diagnosed root cause for the follow-up: HARD's
  retake-awareness penalty (`Evaluator`, `exposedBorderHexes`) vetoes expansion
  whenever fresh borders would be threatened, so against an EASY swarm on open
  maps HARD literally passes turns while being eaten — and a few HARD-vs-EASY
  island games stall to the 400-round cap. Fixing the timidity (e.g. capping
  the penalty relative to local force advantage) should restore the historical
  ≥70% edge; rebalance all gates once when doing it.

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
  re-runs `fitCameraOnce` and re-frames the board (`SetupScreen` choices *are* saved
  via `rememberSaveable`). Hoisting the rig pose into the ViewModel would fix it.
- **`SetupScreen` choices reset when you back out to the menu** — they survive
  rotation, but not leaving the screen, since the composable leaves composition.
  Hoisting the last-used setup into the ViewModel (or preferences) would fix it.
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
