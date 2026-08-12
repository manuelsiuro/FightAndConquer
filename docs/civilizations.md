# Civilizations

Each player seat picks a **civilization**: an identity that selects the piece art set
and a light per-civ rule-delta table. Four ship — **Kingdom** (the original set, the
baseline), **Vikings**, **Sultanate** and **Shogunate**. Civilization is orthogonal to
seat color: color stays derived from seat index everywhere, so any civilization
renders in any faction tint, and two seats may play the same civilization.

The design is *light asymmetry only*: civilizations nudge special-unit and building
economics, never the soldier ladder — a Peasant costs 10 and a Knight fights at 4 for
everyone, so the core capture math reads the same across the board.

## Choosing a civilization

- **Setup**: `SetupScreen` shows a per-seat civilization chip row (yours + one per AI
  seat), saved across rotation via `civListSaver`. The picks flow into
  `MapDefinition.newGame(…, civs = …)`, which stamps `PlayerState.civ` per seat.
- **Campaign / custom maps**: `LevelDef.civs` is an optional list parallel to `seats`
  (`null` = all Kingdom). `LevelFactory` requires matching arity; the map editor's
  validator mirrors that require as `MapViolation.CivsSizeMismatch` ("N civilizations
  for M seats"). Campaign sources set `"civs"` in `tools/campaign_src/`;
  `build_campaigns.py` validates names and arity at bake time.
- **Guide**: each civilization has a one-line description string
  (`guide_desc_civ_*`), names via `civNameRes` in `UiText.kt` — no hardcoded strings.

## Bonus table (defaults)

`core/.../model/CivModifiers.kt` is the whole table; deltas apply to the game's
`RuleConstants` snapshot. Effective values at the default rules:

| Civ | Flavor | Bonuses | Penalties |
|---|---|---|---|
| **Kingdom** | The baseline | none — the identity delta | none |
| **Vikings** | Sea raiders | Warship strength 2→**3**; Transport 15→**10**; Port 20→**15** | Farm cost step 2→**3**; Archer 14→**18** |
| **Sultanate** | Desert merchants | Market 25→**21**; Mine income 6→**7**; Farm base 12→**10** | Warship 25→**30**; Lumber-camp tree income 2→**1** |
| **Shogunate** | Defensive discipline | Tower 15→**12**; Watchtower 8→**5**; Archer upkeep 4→**3**; Catapult move range 2→**3** | Port 20→**25**; Transport 15→**20** |

`CivModifiers.validate` guards every civ-touchable field at construction: costs and
incomes must stay > 0, upkeep/strength/range ≥ 0 — a rules snapshot a delta would
drive to zero is rejected, not clamped (`CivModifiersTest`).

## Engine mechanics

**Where civ lives.** `PlayerState.civ: Civilization = KINGDOM` — defaulted, so a save
or map written before civilizations existed decodes as all-Kingdom and replays
bit-identically (`LegacySaveTest` allows the `civ` / `civBonusesEnabled` keys).

**Resolution.** No rule site branches on a civilization. The rest of the engine asks

```kotlin
Rules.effectiveRules(state, player)   // = CivModifiers.effective(state.config.rules, player.civ)
```

and reads plain `RuleConstants` fields. Every owner-dependent accessor in `Rules`
(`strengthOf`, `unitCostOf`, `unitUpkeepOf`, `buildingCost`, `farmCost`, `incomeOf`,
`upkeepOf`, …) resolves through it, so legality, the reducer, the AI and the UI all
price a piece at its **owner's** civ automatically. `CivModifiers.effective` is pure
and memoized per base-rules instance (a game holds one immutable `RuleConstants`, so
the one-slot cache hits on every call); Kingdom — and any civ with the gate off — is
the *same instance*, not an equal copy.

**Soldier ladder is universal by design.** Tier 1–4 cost/upkeep/strength/move ranges
and `maxTier` are never civ-modified: the AI's `MoveGenerator` and `NavalPolicy`
hardcode those assumptions (cheapest-breaker ladders, tier-vs-defense math). Raw
`state.config.rules.unitCost`/`soldierMoveRanges` reads stay valid everywhere. The AI
*does* read effective rules for affordability: `MoveGenerator` and `NavalPolicy`
resolve `Rules.effectiveRules(state, me)` up front, so a Viking AI sees its cheap
transports and a Shogunate AI its dear ports.

**Feature gate.** `RuleConstants.civBonusesEnabled` (default **true**) is the master
switch: off, every civilization plays at the base rules and the civ picks art only.
Like all rules it is snapshotted into `GameConfig`, so toggling the default never
alters an in-progress game.

**Compatibility discipline.** Same as every expansion: defaulted serialized fields
only. Pre-civ saves decode as all-Kingdom + gate-on, whose delta is the identity —
legacy replays are bit-identical either way. Shared custom maps without `civs` play
all-Kingdom. `CivilizationTest` covers the newGame/LevelFactory/validator arity
requires and the save round-trip; `CivRulesTest` covers the deltas flowing through
combat, pricing, income and upkeep; `AiSimulationTest` runs mixed-civ termination and
determinism suites (no winrate bands — deterministic gates reshuffle chaotically on
any change).

## Art sets

Full pipeline detail in [asset-pipeline.md](asset-pipeline.md); the contract:

- **Kingdom IS the flat set** — `assets/pieces/<kind>.pmesh`, `piece_<kind>` icons.
  Other civs bake into `assets/pieces/<civ>/<kind>.pmesh` from
  `art/blender/pieces/<civ>/*.py` scripts (`KIND = "<CIV>_<KIND>"`,
  `export_piece(PIECE, coll, subdir="<civ>")`), GLBs in `art/models/<civ>/`.
- **Only player-owned kinds fork** — the 19 in `PieceMeshes.CIV_FORKED_KINDS`
  (4 soldiers, archer, catapult, boat, warship, and the 11 buildings). Neutral board
  furniture (`NEUTRAL_KINDS`: tree, gravestone, gold vein, fertile, fish shoal)
  never forks and always renders Kingdom art.
- **Runtime**: `PieceMeshes` is keyed (civilization, kind). `BoardScene` preloads the
  art sets of exactly the civs present in the game; a civ-forked kind without a baked
  asset *shares* the Kingdom entry (same instance — loaded once, freed once), and
  `artCivFor` reports the civ actually rendering so the scene diffs by art identity,
  never recreating an identical-looking piece. `PieceMeshLoaderTest` re-validates
  every checked-in civ `.pmesh` and exercises the exact fallback/ownership logic on
  the JVM (`CivArtTable`).
- **Icons**: `PieceIcons` mirrors the 3D contract with (civ, kind) tables —
  `piece_<civ>_<kind>` drawables, baked by `render_piece_icons.py <civ>/<kind>`;
  a missing icon points at the Kingdom drawable.
- **Board readability contract**: unit tier heights (0.30 / 0.41 / 0.48 / 0.54) and
  the 1–4 pip rings are identical across civs — tier must read at a glance no matter
  whose army it is. Same converter budgets as always (≤ 600 tris, radius 0.45).

### Silhouette language

| Civ | Language (sampled: capital, tower, boats, units) |
|---|---|
| Kingdom | The original set — keep, crenellations, pennants |
| Vikings | Timber longhouse, curled prow/stern posts, round wall/gunwale shields, gold dragon finials |
| Sultanate | Domes and minarets, lateen-rigged dhows, gold crescents, turbans |
| Shogunate | Tiered eave roofs, torii, battened square sails, nobori banners, straw hats |

## Adding a fifth civilization

1. **Enum**: add the entry to `Civilization` (`:core`). Serialization is by name;
   no new keys, so `LegacySaveTest` needs nothing.
2. **Deltas**: add its arm in `CivModifiers.modified` (light asymmetry; never the
   soldier ladder) and its exact-fields case to `CivModifiersTest`.
3. **Strings**: `civ_<name>` + `guide_desc_civ_<name>` in `strings.xml`, mapped in
   `UiText.civNameRes` and the guide (exhaustive `when`s fail to compile until done).
4. **Icons tables**: the new `Civilization` branch in `PieceIcons.unit`/`building`
   (point at Kingdom drawables until icons ship — the tables are the fallback).
5. **Art**: 19 scripts under `art/blender/pieces/<name>/` (`KIND = "<NAME>_<KIND>"`,
   `export_piece(…, subdir="<name>")`), then
   `python3 tools/glb2pmesh.py --all art/models app/src/main/assets/pieces` and
   `python3 tools/render_piece_icons.py <name>/<kind>` per piece. The art can land
   incrementally — unshipped pieces fall back to Kingdom, 3D and 2D alike.
6. **Tests to extend**: `CivModifiersTest` (delta + validation), `CivRulesTest` if
   the deltas touch new accessors, `CampaignCodecTest` (civs round-trip list),
   `PieceMeshLoaderTest` (picks up `pieces/<name>/` automatically once assets
   exist), and an `AiSimulationTest` mixed-civ run via its `civs` parameter.
   Then `./gradlew :core:test :app:testDebugUnitTest` and a device screenshot.
