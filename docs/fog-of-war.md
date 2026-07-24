# Fog of War

Classic fog of war, optional per game (off by default). Unseen hexes render
near-black; hexes seen at least once persist as dimmed, terrain-only "explored
memory". Enemy pieces, faction colors, and defense auras are only rendered
inside the viewer's live vision. Both human players and the AI honor fog — the
AI reads no enemy information outside its own vision set.

## As-implemented specification

### Vision sources and radii

Vision is **derived, never stored** — `Rules.visibleHexes(state, player)` is a
pure, RNG-free function of the current state. The radii live in
`RuleConstants` (snapshotted into each game's `GameConfig` like every rule):

| Source | Constant | Default |
|---|---|---|
| Every owned hex | `visionRadiusOwned` | 2 |
| Every own unit | `visionRadiusUnit` | 3 |
| Own Capital / Tower / Strong Tower | `visionRadiusBuilding` | 4 |

The visible set is the union of `HexMath.range(source, radius)` over all
sources, intersected with the map's land hexes. There is no line-of-sight
blocking — vision is pure radius (trees/mountains do not occlude).

**`visionRadiusOwned >= 2` is a load-bearing invariant.** Every hex a player
action can target — region moves, frontier captures, adjacent buy-placements,
own-territory builds — lies within distance 2 of owned territory, and so does
every input to `Rules.defenseOf` on those targets (the hex plus its
neighbors). Consequences:

- `Legality` needs **zero** fog checks: no action can ever target an unseen
  hex, so there is no `FOG` rejection reason.
- `Rules.reachable` and the AI `MoveGenerator` already operate entirely inside
  the vision set — they required no changes.
- The defense chips shown on capture targets never leak fogged information.

If you ever lower `visionRadiusOwned` below 2, all three guarantees break.

### Explored memory (`discovered`)

`PlayerState.discovered: Set<Hex>` (defaulted to empty — old saves load
unchanged, `SaveGame.version` stays 1). It grows monotonically and is stored
sorted by `Hex.packed` so serialized state is byte-stable for the determinism
tests.

A single hook in `Reducer.reduce` — after the action is applied, before
`build()` — refreshes the **current player's** set when fog is enabled.
Refreshing only the actor is provably sufficient: vision sources are
exclusively *own* assets, so an opponent's action can only **shrink** your
visible set (capture your hex, kill your unit), and `discovered` never
shrinks. `EndTurn`/`Surrender` advance the seat inside the reducer, so the
same hook covers turn-start discovery for the incoming player. Initial
discovery is seeded per player in `MapDefinition.newGame`.

Explored memory is **terrain-only**: fogged tiles render as dark neutral
terrain with no faction color and no pieces. There is no "last-known enemy
snapshot" state to store or invalidate. Known accepted simplification: tile
raise-height is live, so a captured plateau at the fog rim can silhouette
slightly; cosmetic, and invisible in the near-black unexplored zone.

### Determinism contract

- Vision is a pure function; `discovered` updates are pure and sorted; the
  reducer hook touches no RNG. `rngState` is unaffected by fog.
- Undo restores the exact prior state, so `discovered` can shrink within a
  turn — intentional: it matches what save-replay reproduces bit-for-bit.
- Save restore replays `actionsThisTurn` through the reducer and reproduces
  identical `discovered` sets; two identical runs serialize byte-equal.
- Multiplayer lockstep (roadmap): `discovered` is part of `GameState`, so
  post-action state hashes stay comparable across peers.

### Toggle plumbing

`RuleConstants.fogOfWar: Boolean = false` → FilterChip in `SetupScreen` →
`GameSetup.fogOfWar` → `GameViewModel.newGame` passes
`RuleConstants(fogOfWar = …)` to `MapDefinition.newGame`. Because the rules
are snapshotted per game, fog cannot be toggled mid-game and saves are
self-describing.

### Presentation rules (`:app`)

- `GameViewModel` exposes `visibility: StateFlow<BoardVisibility?>`
  (`visible` + `explored` sets; `null` = fog off **or game over** — fog lifts
  on the victory screen).
- The view perspective is the current human seat; during AI turns it stays on
  the **last human seat that played** so pass-and-play never leaks the next
  player's map (the privacy `TurnBanner` covers the human→human hand-off).
- `BoardScene.setFog` drives three tile levels — VISIBLE (true faction
  color), EXPLORED (dark neutral), HIDDEN (near-black) — by scaling the
  existing tile color uniforms in Kotlin; no material recompile. Pieces in fog
  are removed from the scene (same mechanism as highlights), synced silently
  in `reconcile` exactly like the `spent → dimmed` annotation, so the
  "zero reconcile corrected warnings" gate still holds.
- Events are **never filtered** (filtering would trip the reconcile gate);
  animations simply play on hidden pieces. Only juice is suppressed in fog:
  camera rumble, the capture wave, and defense auras on fogged hexes.
  Compose-space anchors (coin popups, defense chips) are only tracked for
  non-fogged hexes.
- Info cards: an explored-but-fogged hex shows an "unexplored territory"
  card; a never-seen hex shows nothing. Unit/building stats never leak.
- Timing nuance (accepted): the fog set derives from the newest engine state
  while the scene may still be animating older beats, so a revealed piece can
  un-hide a beat early; the settling `reconcile` makes it exact.

### AI under fog

`Evaluator.score` computes the AI's own `visibleHexes` once per evaluation
and counts `enemyHexes` / `enemyStarving` / exposed-border pressure only
inside it. Unknown territory is simply ignored (optimistic). Both AIs honor
fog symmetrically, so mirror games stay fair. `MoveGenerator` is unchanged
(see the radius-2 invariant above).

Balance notes: HARD's slicing signal (`enemyStarving`) degrades under fog —
it can only reward cuts it can see. The fog-off winrate gate (HARD ≥ 70% vs
EASY) remains the balance baseline; fog-on games are gated on termination,
determinism, and per-turn time, not winrate.

## Gameplay impact analysis

- **Economy pacing.** Own income/upkeep are fully visible (your economy is
  never fogged), but the *enemy's* farm build-up and treasury are invisible.
  Bankruptcy — the game's signature catastrophe — becomes a genuine surprise
  in both directions: you can no longer time an attack to an opponent's
  visible over-extension, and your own collapse can hand a hidden neighbor a
  free window. This raises the value of steady farm chains over greedy unit
  rushes.
- **Towers gain a scouting dimension.** `visionRadiusBuilding = 4` makes
  towers dual-purpose (defense + watchpost). A tower at a choke point now
  buys information, which softens its pure-gold inefficiency vs farms.
- **Vision expansion ≈ territory expansion.** Movement is region-bound
  (Slay-style), so there are no roaming scouts: the only way to see more is
  to own more or to push units to the frontier (`visionRadiusUnit = 3`
  rewards forward unit placement as a scouting posture with real upkeep
  cost).
- **Pass-and-play synergy.** Fog pairs naturally with the existing privacy
  `TurnBanner`; hot-seat games finally have real hidden information.
- **Trees/gravestones** are visible as terrain in explored memory, so the
  tree-spread nuisance stays plannable; only *live* enemy activity is hidden.

## Extension proposals (evaluated, not implemented)

Effort ratings assume the recipes in [roadmap.md](roadmap.md).

### Buildings

| Proposal | Effect | Effort | Impact | Notes |
|---|---|---|---|---|
| **Watchtower** — ✅ SHIPPED with the expansion | No defense, cost 8, vision radius 6 | **Low** | High with fog on | Implemented exactly as sketched, plus a hard legality gate (`REQUIRES_FOG_OF_WAR`) instead of a UI-only filter, and a Hard-AI candidate family scored by never-seen positions. See game-rules.md. |
| **Market** | Flat +N coins/turn, no hex income multiplier, expensive (~25) | Medium | Medium | Alternative economy scaling to farm chains; touches `incomeOf`/`incomeIn` + AI `Evaluator` economy weighting. Watch the farm-adjacency niche — markets should NOT chain. |
| **Wall** | Defense 1 on its hex only (no aura), very cheap (~6) | Low/Medium | Medium | Cheap frontier stiffener below Tower; touches `defenseOf` coverage logic (self-only branch) and AI tower-placement heuristics. |

### Units

The unit model is **tier-keyed everywhere** (`GameUnit.tier` drives cost,
upkeep, strength, merge rules, buy action, and mesh selection via
`PieceMeshes.unitKind(tier)`). A new unit *type* therefore touches:
`GameUnit` (+type field, save-compat via default), `GameAction.BuyUnit`,
`Legality` (merge/buy rules), `Rules.reachable` (if movement differs),
`MoveGenerator`/`Evaluator`, and the piece pipeline. **High effort** — do it
once, deliberately, not per-unit.

| Proposal | Effect | Effort | Impact | Notes |
|---|---|---|---|---|
| **Scout** | Strength 0–1, cheap upkeep, `visionRadiusUnit + 3`, cannot capture | **High** (first typed unit) | High with fog | Only worthwhile after fog ships and proves fun. Consider instead: a rules-only variant "peasants see +1" to test the appetite before paying the type-system cost. |
| **Pikeman** | Tier-2 cost, defends as tier 3 when stationary | High | Medium | Defensive niche; breaks the clean strength==tier mental model — weigh against Wall which delivers similar value as a building (cheaper to build AND implement). |
| **Tier 5 (Lord)** | Straight extension | **Low** | Low/Medium | The roadmap recipe covers it (`maxTier`, cost/upkeep lists, `PieceKind.UNIT_T5`, height progression). Upkeep math (54→~162) makes it near-unusable — mostly a vanity cap. |

### Resources

| Proposal | Effect | Effort | Impact | Notes |
|---|---|---|---|---|
| **Wood** (from tree-clearing) | Clearing a tree yields 1 wood; buildings cost gold + wood | Medium | High | Turns the tree nuisance into a resource loop and gives clearing a second purpose. `PlayerState.wood: Int = 0` (save-compatible), `treeClearBonus` → wood, `Legality`/`Reducer` building costs, HUD chip, AI evaluator term. Biggest bang-for-buck of the resource ideas. |
| **Terrain types** (hills/plains) | Hills: +1 vision, +1 defense; generated at map creation | Medium | Medium | Needs `Tile.terrain` (defaulted), map-generator changes, renderer tile-height/material variation, and vision-source adjustment. Pairs beautifully with fog but is a broad, shallow touch. |
| **Second currency (mana/favor)** | Parallel income for special actions | High | Low | No current sink justifies it; revisit only if spells/abilities ever enter the design. Not recommended. |

**Recommended order** if extending after fog: Watchtower → Wood → Wall →
Market → terrain types → typed units.

## Polish follow-ups (deliberately out of MVP)

- Pooled translucent fog discs (reuse `highlight.mat`) for a softer fog edge
  instead of pure tile darkening.
- A dedicated `fog` shader uniform in `hexTile.mat` (requires the
  version-locked `matc` recompile — see asset-pipeline.md).
- Last-known-snapshot memory (ghost pieces at their last seen position).
- Fog-aware HARD AI heuristics (value information, probe unseen frontiers).
