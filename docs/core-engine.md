# Core Engine (`:core`)

Pure Kotlin JVM module (`kotlin("jvm")` + `kotlinx.serialization`); **no Android
imports** — everything runs in millisecond host tests. Package root:
`com.msa.fightandconquer.core`.

## Hex math (`hex/`)

- `Hex` — axial coordinate packed into one `Int` (`q` high 16 bits, `r` low 16,
  signed; range ±32767). Cheap map key, serializes as a number. `s = -q - r`.
- `HexMath` — `DIRECTIONS` (6 axial offsets in ring order), `neighbors`,
  allocation-free `forEachNeighbor`, cube `distance`, `range`, `ring`,
  BFS `floodFill(start, canEnter)`, `connectedComponents`.
- Orientation is topology-only here; the renderer decides pointy-top and owns
  axial↔world conversion (`app/.../render/HexWorld.kt`).

## Model (`model/`)

`GameState` (immutable, `@Serializable`):
`config` (seed + `RuleConstants` snapshot), `tiles: Map<Hex, Tile>` (water = absent),
`units: Map<UnitId, GameUnit>`, `players: List<PlayerState>` (index = `PlayerId.value`,
seat order = turn order), `currentPlayer`, `turnNumber` (completed rounds),
`rngState: Long` (SplitMix64 — RNG lives IN the state), `phase`
(`Playing | Finished(winner)`), `nextUnitId`.

`Tile(owner?, unit?, building?, flora?, starving)` — `unit` mirrors `GameUnit.hex`
(dual index kept consistent by the reducer, checked by test invariants).
`Building`: CAPITAL, FARM, TOWER, STRONG_TOWER. `Flora`: `Gravestone(createdRound)`
| `Tree`. `PlayerKind`: `Human` | `Ai(difficulty)`.

## Actions, events, reducer (`engine/`)

`GameAction` (all implicitly by `currentPlayer`): `MoveUnit(unit, to)` (move OR
capture, one action), `BuyUnit(tier, at)` (place / buy-merge / buy-capture),
`BuyBuilding(type, at)`, `MergeUnits(a, b)` (a is the fresh mover), `EndTurn`,
`Surrender`.

`GameEvent` — ordered facts for animation/tests: `ActionRejected`, `UnitSpawned`,
`UnitMoved(unit, from, to)`, `HexCaptured(hex, newOwner, oldOwner)`,
`UnitDied(unit, hex, cause KILLED|STARVED|BANKRUPTCY)`, `UnitsMerged(into, consumed)`,
`BuildingBuilt/Destroyed`, `TreeGrown`, `TreeSpread(from, to)`, `TreeCleared(hex,
bonus)`, `GravestoneTrampled`, `TurnStarted(player, income, upkeep)`, `Bankruptcy`,
`CapitalMoved(player, from, to, loot)`, `PlayerEliminated`, `GameOver(winner)`.

Flow: `Reducer.reduce` → `Legality.check` (returns a `RejectionReason` code plus an
optional `amount` — the UI maps it to a localized toast) → apply on an internal mutable `StateBuilder`
(shared ops: `spawnUnit`, `killUnit`, `clearFloraAt`, `captureHex`,
`recomputeStarving`, `checkElimination`) → freeze to `ReduceResult(state, events)`.
`TurnPipeline` implements the turn-start ordering (see game-rules.md).

`Rules` — pure queries used by Legality, AI and UI: `region`, `defenseOf`,
`reachable(unitId) -> ReachResult(moveTargets, captureTargets, mergeTargets)`
(single region scan), `capitalConnected`, `incomeOf`, `upkeepOf`, `nextFarmCost`,
`buildingCost`.

## Determinism contract (`engine/Rng.kt`)

Local SplitMix64 (`advance`/`output`/`nextInt`) — **never** `kotlin.random.Random`
(algorithm not stable across versions), never wall clocks. Every random draw threads
`rngState` through the builder. Tested: same seed + same actions ⇒ identical JSON.
Consumers that need "random but replayable" behavior (tree spread, capital
relocation, map gen retries) all derive from this chain.

## Facade (`engine/GameEngine.kt`)

The only entry point `:app` uses:

- `state: StateFlow<GameState>`, `events: SharedFlow<GameEvent>`
  (buffer 1024, DROP_OLDEST — safe because the renderer reconciles from state).
- `submit(action): LegalityResult` — turn boundaries (`EndTurn`/`Surrender`) reset
  the undo stack and the per-turn action log.
- `undo()/canUndo()` — immutable-state stack, within the current seat's turn only.
- Queries: `reachableFor`, `buyableAt(hex): List<PurchaseOption>` (already filtered
  by legality *and* affordability), `incomeSummary(player)`.
- `toSave()` / `fromSave(save)`.

Thread-confined: call mutators from one (main) thread.

## Persistence (`persist/`)

`SaveGame(version, turnStartState, actionsThisTurn)` — restoring **replays** the
turn's actions through the reducer, which doubles as an integrity check
(`fromSave` rebuilds the undo stack via `submit`). JSON via `SaveCodec`
(`ignoreUnknownKeys` for forward compatibility). The app stores one autosave at
`filesDir/autosave.json`; finished games delete it.

## Map generation (`map/`)

`MapParams(seed, size SMALL/MEDIUM/LARGE ≈ 120/250/450, playerCount 2–6, shape
CONTINENT/ISLANDS/ARCHIPELAGO)` → `MapDefinition(name, generatorParams?, tiles,
capitals)`; `newGame(gameSeed, kinds, rules)` instantiates a `GameState`.
Authored campaign maps use the same format with `generatorParams = null`.

Algorithm: seeded random-walk blob growth (frontier weighted `(1+landNeighbors)²`);
ISLANDS/ARCHIPELAGO grow per-blob then connect with `hexLine` bridges (ring
topology). Capitals via farthest-point sampling seeded from the rim (viable = full
neighbor ring on land), fairness floor `max(5, 0.9·√(land/players))`. Start regions =
capital + ring. Tree count = 8 % of total land, placed on unprotected neutral hexes. `MapValidator` enforces:
single landmass, marked/owned capitals, spacing, equal regions, no flora in starts;
the generator retries derived seeds (≤32) until valid.

## AI (`ai/`)

Stateless greedy: `AiPlayer(difficulty).chooseAction(state)` = argmax over
`MoveGenerator.candidates` scored by simulating each with the real reducer
(`Evaluator.score` on the resulting state), or `EndTurn` when nothing beats the
baseline. Driven by the ViewModel loop (≤ `MAX_ACTIONS_PER_TURN = 500`).

Candidates: unit captures, tree-clear moves, merges only when the merged tier breaks
a current frontier defense, buy-capture with the cheapest sufficient tier, peasants
onto own trees, towers on threatened borders (top 3), farms with spare cash
(Easy skips structures until income > 15).

Evaluator: hexes dominate (`14/hex`, Easy `12`), income has diminishing returns
(`6·min(net,10) + 0.5·max(0, net−10)` — this is what prevents endgame stalemates
where upkeep-phobia refuses to break defended hexes), token treasury pull
(`0.15·min(t,200)`, Easy `0.25·min(t,100)`), `−6/own tree`, `−2/enemy hex`,
hard no-bankruptcy guard (soft −100 for Easy). Hard adds `+8/enemy starving tile`
(slicing) and `−1.5/exposed border hex` (retake awareness). Easy also considers only
~60 % of candidates (deterministic hash on `rngState + index`).

## Testing (`core/src/test/`)

- Rule unit tests per area (defense, reach, capture, merge, economy, flora,
  slicing, turn order) on hand-built micro-maps (`TestStates.strip/custom` +
  `assertInvariants` cross-index checker).
- Determinism: bit-identical serialization; roundtrip stability.
- Generator property test: 200 seeds × shapes × player counts must validate.
- Engine facade: undo semantics, save/replay equivalence mid-turn and across turns.
- AI simulations: full games terminate < 400 rounds with invariants; Hard ≥ 70 %
  vs Easy mirror games; Easy expands within 3 rounds; turns < 1 s on LARGE;
  AI games fully deterministic.
