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
3. **Art** — per-civ Blender sets (`art/blender/pieces/<civ>/`, 23 player-owned
   kinds each; neutral markers never fork), (civ, kind)-keyed `PieceMeshes` +
   `PieceIcons` with lazy per-civ preload and shared-instance Kingdom fallback.

Feature gate: `civBonusesEnabled` (default on; off = art only). Full spec in
[civilizations.md](civilizations.md).

## Shipped: research

The University tech tree — the game's first per-player progression system, and
the first system whose state lives on `PlayerState` rather than a tile or the
rules snapshot (`research: ResearchState`, defaulted; `LegacySaveTest` guards
the strip). Full spec in [game-rules.md](game-rules.md) "Research".

1. **Engine** — `Tech` (4 branches × 3 tiers, linear), `StartResearch`, the
   turn-start tick (+1 point per standing University), and a THIRD
   effective-rules layer: `base → civ → research` (`ResearchModifiers`, its own
   bounded identity-keyed cache — per-player variation would poison the civ
   layer's one-slot cache). Costs/durations live in `RuleConstants`, so save
   snapshots keep replay legality and campaigns can retune. Unlock gates bind
   purchase only, through the single shared `Rules.buildingAvailable` predicate.
2. **The audited exception** — Smithing/Armory retired "soldier strength ==
   tier"; every AI tier computation now solves through
   `Rules.buyStrength`/`buyDefense` (`ai/Tiers.kt`), provably the old
   arithmetic when research is off. The flip's gate reshuffle landed with ZERO
   bar edits — every chaotic gate survived, HARD's edge intact.
3. **AI** — `ai/ResearchPolicy.kt`, a threshold ladder between diplomacy and
   the naval steps (research state is constant inside a one-ply window, so no
   evaluator term could steer it): capital-threat veto, war reserves (zero when
   genuinely sea-locked — island flood-fill), per-difficulty priority lists
   (HARD offense-first with STONE last; EASY researches exactly NAVIGATION and
   only when sea-locked). The naval ladder's port steps wait for NAVIGATION.
4. **Content** — three new buildings in all four silhouette languages
   (University/Bank/Fortress ×4 civs), the research side panel (overflow entry
   + idle badge), locked purchase cards, Setup/editor toggles, glyphs `U N S`,
   the Chronicle's Breakthrough moment, and Academy mission 9 (Ink and Iron):
   castle gates at defense 3 under a tier-3 cap — only Smithing opens the pass.
   All 20 pre-research missions bake `researchEnabled=False` and play unchanged.

Follow-ups worth considering: per-civ research deltas (a `CivModifiers`-style
tech-cost table), a research objective in the editor's goal dialog (engine
support exists — `Objective.ResearchCount` is campaign-authored only today),
and surfacing opponents' completed techs in the diplomacy panel.

## Shipped: muster buildings

Military units now require a prerequisite building standing in the realm —
the game's first production chain (`militaryBuildingsRequired`, default on;
full spec in [game-rules.md](game-rules.md) "Muster buildings"). Barracks →
soldier tiers 2–3, Barracks + Fortress → the Knight, Archery range → Archer,
Siege workshop → Catapult; realm-wide, one working (non-starving) instance;
the gate binds every creation path the player has (buy, buy-merge, merge)
while authored spawns and disembarks stay exempt — the research doctrine.

1. **Engine** — one shared predicate family beside `buildingAvailable`
   (`Rules.requiredBuildingsFor` / `hasWorkingBuilding` / `missingUnitBuilding`
   / `unitAvailable`), consumed by Legality (pinned order:
   `SPECIAL_UNITS_DISABLED → INVALID_TIER → UNIT_NEEDS_BUILDING →
   CANNOT_AFFORD`), by `reachable`'s merge targets (chips, checkMerge and the
   AI filter as one), and by `buyableAt`'s muster-locked unit cards
   (`PurchaseOption.Unit.lockedByBuilding`, the `lockedByTech` twin probed via
   `recruitProbe`). The three halls are pure prerequisites: no income, no
   defense, no vision.
2. **AI** — `ai/MilitaryPolicy.kt`, a demand-driven threshold ladder between
   research and the naval steps (a pure prerequisite pays nothing the turn it
   stands — invisible to the one-ply argmax): the ungated `Tiers` probes read
   which hall a blocked plan waits on; every difficulty founds the Barracks
   under demand (a peasant-locked AI can never eliminate anyone —
   termination-load-bearing). `Tiers.maxRecruitable` caps every maxTier
   fallback; the naval ladder ships the best tier it is *allowed*; the
   Evaluator anchors the sunk halls (6/4/3, the UNIVERSITY convention).
3. **The reshuffle's structural finds** — two latent termination hazards the
   flip exposed, fixed at the root: DiplomacyPolicy never signs a pact that
   leaves it with zero living enemies (four NORMALs froze a game in a
   self-renewing all-pact clique), and the argmax now scores candidates
   against a visibility set frozen at the turn's start (an advance must never
   be penalized for the fog it lifts — every capture on the frozen fog seed
   scored negative for revealing the defender's interior). The flip itself
   landed with ZERO bar edits — every chaotic gate survived again.
4. **Content** — three new buildings in all four silhouette languages
   (Barracks/Archery range/Siege workshop ×4 civs, all below the defense
   height band), muster-locked unit cards, glyphs `H A E`, and all 21 missions
   retuned: Academy 3 became the Barracks-and-merge lesson, Academy 6 the
   war-schools lesson (build the workshop → field the catapult → crack the
   keeps), survive/naval missions pre-place halls per seat, both finales sell
   the whole trio, and every research-off mission caps `maxTier` at 3 (the
   Knight's Fortress would be a lock nothing opens).

Follow-ups worth considering: per-civ muster deltas (a Shogunate range
discount), a "musters at" line in the Chronicle, and an upgraded hall tier
(drill yard → war academy) if a second production chain ever lands.

## Shipped: the day-night cycle

The `feature/day-night-cycle` work (an optional mode: night falls every few
rounds, monsters spawn, act and vanish at dawn) landed in four save-compatible
milestones, same discipline as above (defaulted fields, `LegacySaveTest`-guarded;
full rules in [game-rules.md](game-rules.md) "Day-night cycle", the board look in
[rendering.md](rendering.md) "Day-night look"):

1. **Model + cycle** — `Tile.monster`/`Tile.cache` payloads (the flora
   precedent: never a `GameUnit`, no seats, no ownership), the phase as a pure
   function of the round counter (`Rules.isNight` — nothing serialized, replays
   can never desync), `NightPipeline` ticking once per ROUND at `endTurn`'s
   wrap, deterministic spawn waves, and combat through the two shared choke
   points (`defenseOf` + `reachable`) so Legality, the AI and the UI chips all
   price monsters for free. Slain monsters drop tier-scaled gold caches
   (collected by arrival, owner-agnostic) and rarely turn ground FERTILE.
2. **The action phase** — night-interior rounds: strike the nearest beatable
   unit hex (the FULL defense model applies — towers protect at night exactly
   as by day), else prowl one deterministic step, never capture or raze;
   squatted hexes earn nothing; warships shell coastal monsters.
3. **AI** — monster strikes ride `captureTargets` and the one-ply sim's
   treasury term for free; a walk-to-cache candidate, an Evaluator night-threat
   term (step under cover / merge before dusk fall out of the argmax), and
   monsters as raiders in HARD's exposed-border max. `NightAiTest` gates
   termination + determinism; existing balance gates untouched (flag off).
4. **App surface** — the Kotlin-side night look (no matc recompile), monster/
   cache rendering with the zero-corrections discipline, sun/moon countdown in
   the top bar, Setup + editor toggles, info cards, Field Guide; all 21
   missions bake the flag off explicitly.

Note: the 13 new rule keys pushed SMALL share codes past the 2000-byte QR
ceiling — the FCM1 envelope moved to **format version 2** with a re-baked
frozen dictionary (v1 codes still decode; pinned by test).

The Blender bestiary + chest landed in the same branch (five monster minis +
the reward chest, all `NEUTRAL_KINDS`, 216–328 tris, icons baked), as did the
`Objective.MonstersSlain` campaign objective (`CampaignTracker.monstersSlain`
counting `MonsterSlain` by seat; the baker passes `{"type": "slayMonsters"}`
through verbatim, so authoring one needs no tool change). Deferred follow-ups:
per-kind stat flavor (kind is already serialized — purely additive) and a
night-showcase campaign mission.

**Beacon upgrade — SHIPPED** (feature/beacon-lights): the game's first
building upgrade. `GameAction.UpgradeBuilding` lights a `Tile.beacon` flag on
an own standing defense building for `beaconCost` (12); `Rules.litHexes`
(radius via `beaconRadiusOf` — 1, Fortress 2) is derived, never stored, and
`NightPipeline` shuns it at all four points (spawn wave, passability, strikes,
prowl lures). The Tile-flag design deliberately avoided new `Building` values:
zero campaign-glyph/tray/editor churn, one save key (`beacon`, plus the
`beaconCost` rule key). Renderer: four `*_LIT` PieceKinds (16 Blender bakes,
per-civ brazier idioms) + the app's first point lights — shadowless, pooled,
intensity riding `nightFactor` — and the **lit-radius ground tint**: hexes in
a visible beacon's radius warm to `Palette.BEACON_TILE_MULT` at night, the
hex-accurate readable safe zone (rendering.md "Beacon point lights"). AI:
`BeaconPolicy` threshold policy + the Evaluator's night-threat term skipping
lit units. Deferred follow-ups: an `EMBER` ColorRole so flames brighten with
`nightFactor` (needs the three synced role lists + a full re-bake), lit-variant
UI icons, editor-authored pre-lit beacons, flame flicker on the ambience clock.

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
- **Enjoyment proposals (2026-08 design pass)** — evaluated alongside the
  post-match debrief ([debrief.md](debrief.md)), which was picked first:
  - *Decisive endgame* — AI concession when hopelessly behind, an
    "outcome inevitable — auto-resolve?" offer, or the economic victory above;
    attacks the genre's mop-up tail. Rule change → one deliberate
    balance-gate rebalance pass.
  - *AI personalities* — **SHIPPED (2026-08 AI overhaul)**: `AiProfile`
    presets (raider / turtle / admiral / schemer) derived per seat from the
    game seed (or authored on `SeatDef.Ai`), plus the strategic layer
    (`Strategy.assess` cuts/threats/fronts), counter-attack and army-value
    evaluator terms, coverage-ranked towers, ordinary bridges, rear-guard
    disbands, `RepositionPolicy` marching, and seeded argmax jitter — see
    core-engine.md §AI. Per-seat difficulty *UI plumbing* (above) remains.
  - *Seeded & daily challenges* — seed entry + "beat my map" share codes
    (the `FCM1` envelope machinery exists) and a daily fixed-seed map scored
    by rounds-to-win with local personal bests.
  - *Match history* — persist finished debriefs (`CustomMapStore`-style
    one-file-per-match store) behind a History screen. The in-flight
    chronicle's autosave piggyback now EXISTS (`SaveGame.record` +
    `MatchRecordSave.restore` — a resumed match keeps its debrief); only the
    keep-after-the-debrief store and its screen remain unbuilt.
- Translations: the string *extraction* is done (every user-facing string is in
  `res/values/strings.xml`, with `UiText` carrying resource ids out of the
  ViewModel), so shipping a language is just adding `values-<lang>/strings.xml`.

## How-to recipes

**New building type**: add to `Building`/`BuildingType` (`:core` model), cost/defense
in `RuleConstants` + `Rules.buildingCost`/`defenseOf` (+ `visibleHexesFrom` if it
sees, `StateBuilder.captureHex` destroyed-vs-kept, `Rules.requiredTech` if
research-gated), legality in `Legality.checkBuyBuilding`, income/pipeline effects
in `TurnPipeline`, AI: a `MoveGenerator` candidate + an `Evaluator` asset term
(else the greedy loop never buys it), tests; then `PieceKind` + procedural
fallback arm + Blender scripts ×4 civs + bake (asset-pipeline.md),
`BoardScene.buildingKind` (+ `refreshAuras` if it defends — that `when` has an
`else` and fails SILENTLY), `PieceIcons` ×4 civ arms, `UiText.buildingNameRes`,
purchase-card detail label in `BottomBar`, `GameViewModel.infoCardFor` +
economy-panel row if it earns + `ShopInfo` field, `GuideCatalog` entry +
`forStructure`, `EDITOR_BUILDINGS` in the map editor, `BriefingConcepts.advanced`,
a campaign glyph in `tools/build_campaigns.py`, and `ALL_BUILDINGS` in ALL THREE
campaign sources (narrow teaching trays silently widen otherwise). If the
building gates unit creation, extend `Rules.requiredBuildingsFor` and give
`MilitaryPolicy` a demand trigger for it instead of an Evaluator steering term.

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

- **HARD's winrate vs EASY is restored to ~71%** (43/60 mirror games, 2 stalls;
  `AiSimulationTest`'s bar raised back to 60%). Two structural fixes (2026-08):
  the retake-penalty turtle — `Evaluator` caps the `exposedBorderHexes` penalty
  by the balance of fielded force (removing the penalty entirely measured
  WORSE, 63%; the cap, not deletion, is right) — and the island
  invasion-funding stall — `NavalPolicy` 2d demobilizes an idle land army
  (never the capital guard, never the designated marine, never while a loaded
  transport is at sea or invaders stand on the homeland) so the refunds and
  freed upkeep finance the fleet that the income knee + bankruptcy guard used
  to make unaffordable. Fog termination went from a hand-picked-seed dodge to
  **all of seeds 1–10 terminating**; that dodge is retired.

- **The standoff bleed-out is fixed too** — every termination dodge is now
  retired (`AiSimulationTest` runs fog seeds 1–10 and mixed-civ seeds 1–4;
  1–6 measured clean). The last stall (mixed-civ seed 2) was diagnosed by
  probe, and it was NOT an amphibious fortress: the sea stayed one navigable
  body with dozens of open beaches. It was a capital-pinned bridgehead
  standoff — each side's knights could capture defense-0 farms but the move
  dropped their own capital's cover, so the evaluator's −30 capital-guard
  term vetoed every advance; meanwhile the frozen 30-unit garrison held net
  income at −1, draining the treasury for ~250 rounds into a bankruptcy wipe,
  rebuild, repeat — and the WAR_CHEST force-attack (which DID fire once at
  treasury 308) could never refill. The fix extends NavalPolicy's war-economy
  step: in overseas mode a net-non-positive army is trimmed until income runs
  a surplus, so the surplus refills the war chest and 2b's force-attack breaks
  the standoff instead of the bank. Residual known behavior: single-marine
  landings are still a meat grinder against a defended island (each wave
  ships one soldier); a coordinated multi-boat wave or warship shore support
  would make island wars decisive faster — quality-of-play, not liveness.

  **Landed** (2026-08, second attempt — the first was reverted for regressing
  the dodge-free suite): the decisive-marine ladder. `NavalPolicy` now derives
  a **marine floor** — the tier a landing needs to take AND hold a beach:
  max(enemy's best visible soldier, weakest visible enemy coastal defense + 1),
  falling back to the wealth ladder when fog has shown nothing (a blind
  tier-1 default froze both fog AIs forever). Below the floor it SAVES rather
  than dribbling doomed waves; above it, it ships the best the economy
  sustains. Supporting steps found by replay probes: the launcher funds the
  whole kit (hull + marine) or waits; the trim can scuttle an idle hull whose
  upkeep out-eats a small island (but never the hull a shipping-grade marine
  is waiting for); a rich blockaded AI demolishes an income building to make
  room for the port or the muster hex (entombed-island freezes); a marine
  beyond one action from the dock is marched coastward (the ladder's last
  mile); and a delivered marine on an island still holding enemy ground is
  the land war's — re-boarding it shuttled beachheads forever. Measured:
  HARD-vs-EASY **81% (49/60) with zero 400-round stalls** (from ~60% and
  regular stalls), island finish tails max ~104 rounds (was 400-cap), fog
  10/10, mixed-civ all ≤ 108, every campaign level green.

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
