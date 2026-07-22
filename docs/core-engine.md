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

`Tile(owner?, unit?, building?, flora?, starving, deposit?)` — `unit` mirrors
`GameUnit.hex` (dual index kept consistent by the reducer, checked by test
invariants); `deposit` (`GOLD_VEIN | FERTILE`) is permanent terrain that survives
capture. `Building`: CAPITAL, FARM, TOWER, STRONG_TOWER, MINE, MARKET,
LUMBER_CAMP, WATCHTOWER. `Flora`: `Gravestone(createdRound)` | `Tree`.
`PlayerKind`: `Human` | `Ai(difficulty)`.

`GameUnit` carries `type: UnitType = SOLDIER` (serialized as `unitType`).
Soldiers keep strength == tier (1..4); ARCHER/CATAPULT are single-level
(tier fixed at 1) with per-type strength/upkeep from `RuleConstants` — every
strength/upkeep/cost read goes through `Rules.strengthOf/unitUpkeepOf/unitCostOf`.

`GameState.diplomacy: DiplomacyState` holds pacts, pending proposals, per-pair
proposal/tribute cooldown rounds and lifetime `pactBreaks` — every list kept
canonically sorted on write (byte-stable JSON; the determinism tests pin it).

`PlayerState` also carries `discovered: Set<Hex> = emptySet()` — fog-of-war
explored memory, packed-sorted for byte-stable serialization, monotonic, always
empty when fog is off (defaulted → old saves load unchanged). Updated by a single
post-action hook in `Reducer.reduce`; live vision is always derived via
`Rules.visibleHexes` (see docs/fog-of-war.md).

## Actions, events, reducer (`engine/`)

`GameAction` (all implicitly by `currentPlayer`): `MoveUnit(unit, to)` (move OR
capture, one action), `BuyUnit(tier, at, type)` (place / buy-merge / buy-capture;
`type` defaults to SOLDIER so old logs replay unchanged), `BuyBuilding(type, at)`,
`MergeUnits(a, b)` (a is the fresh mover), `ProposePact(to, durationRounds)`,
`RespondPact(from, accept)`, `SendTribute(to, amount)`, `EndTurn`, `Surrender`.
Serialization note: sealed-class JSON uses the `type` discriminator, so action
*properties* must not be serialized under that name (`BuyBuilding.type` →
`"building"`, unit types → `"unitType"`; a collision used to silently kill
mid-turn autosaves — regression-tested in `persist/LegacySaveTest`).

`GameEvent` — ordered facts for animation/tests: `ActionRejected`, `UnitSpawned`,
`UnitMoved(unit, from, to)`, `HexCaptured(hex, newOwner, oldOwner)`,
`UnitDied(unit, hex, cause KILLED|STARVED|BANKRUPTCY)`, `UnitsMerged(into, consumed)`,
`BuildingBuilt/Destroyed`, `TreeGrown`, `TreeSpread(from, to)`, `TreeCleared(hex,
bonus)`, `GravestoneTrampled`, `TurnStarted(player, income, upkeep)`, `Bankruptcy`,
`CapitalMoved(player, from, to, loot)`, `PlayerEliminated`, `GameOver(winner)`,
plus HUD-only diplomacy facts: `PactProposed/Accepted/Declined/Expired`,
`PactProposalExpired`, `PactBroken(breaker, victim, penalty)`, `TributeSent`.

Flow: `Reducer.reduce` → `Legality.check` (returns a `RejectionReason` code plus an
optional `amount` — the UI maps it to a localized toast) → apply on an internal mutable `StateBuilder`
(shared ops: `spawnUnit`, `killUnit`, `clearFloraAt`, `captureHex`,
`recomputeStarving`, `checkElimination`) → freeze to `ReduceResult(state, events)`.
`TurnPipeline` implements the turn-start ordering (see game-rules.md).

`Rules` — pure queries used by Legality, AI and UI: `region`,
`defenseOf(state, hex, attackerType?)` (unit contributions via
`defenseContribution` — the archer aura slots into the max model like tower
coverage; a CATAPULT attacker skips building contributions entirely),
`reachable(unitId) -> ReachResult(moveTargets, captureTargets, mergeTargets)`
(single region scan; type-aware — specials never merge, catapults are
range-capped), `capitalConnected`, `visibleHexes` (fog-of-war live vision,
RNG-free), `incomeOf` (delegating to `incomeFrom`, the single income source shared
with `TurnPipeline`: hex + deposit bonuses + farm/mine/market/lumber-camp),
`upkeepOf`/`unitUpkeepOf`, `strengthOf`/`buyStrength`/`unitCostOf`,
`nextFarmCost`, `buildingCost`.

Pact breaking has exactly one site: `StateBuilder.captureHex` checks for an active
pact with the victim before any mutation — covering move-capture and buy-capture —
transfers the penalty and emits `PactBroken`. `TurnPipeline` step 0
(`expireDiplomacy`) lapses ended pacts and stale proposals.

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
capital + ring. Deposits (`placeDeposits`) are fair by construction: each capital's
gold vein(s) sit inside its Voronoi cell at a common per-attempt target distance
(nearest-vein spread ≤ 2 guaranteed), FERTILE in band 2–5 likewise; contested
neutral deposits stay outside every fair zone; on cramped maps with no room the
kind is skipped for everyone (zero-for-all is still fair). Tree count = 8 % of
total land, placed on unprotected neutral non-deposit hexes. `MapValidator` enforces:
single landmass, marked/owned capitals, spacing, equal regions, no flora in starts,
no deposits in starts / under flora, and the deposit fairness bounds (as tripwires);
the generator retries derived seeds (≤32) until valid.

## AI (`ai/`)

Stateless greedy: `AiPlayer(difficulty).chooseAction(state)` = argmax over
`MoveGenerator.candidates` scored by simulating each with the real reducer
(`Evaluator.score` on the resulting state), or `EndTurn` when nothing beats the
baseline. Driven by the ViewModel loop (≤ `MAX_ACTIONS_PER_TURN = 500`).

Candidates: unit captures (never onto active pact partners — except Hard's marked
betrayal targets), tree-clear moves (camp-managed trees excluded), merges only when
the merged tier breaks a current frontier defense, buy-capture with the cheapest
sufficient tier, peasants onto own trees, towers on threatened borders (top 3),
farms with spare cash, mines on every owned vein, markets on interior hexes
(≤ 3 owned — uncapped market spam was an observed turtling stalemate mode),
lumber camps at ≥ 2 adjacent own trees (top 2 each, Normal/Hard), catapults where
building defense is the blocker (top 4), archers ranked by aura gain (top 3),
Hard-only fog watchtowers scored by never-seen positions (pure geometry — probing
`state.tiles` for unseen hexes would leak the coastline). Easy skips structures
until income > 15 and never touches diplomacy or specials.

Evaluator: hexes dominate (`14/hex`, Easy `12`), income has diminishing returns
(`6·min(net,10) + 0.5·max(0, net−10)` — this is what prevents endgame stalemates
where upkeep-phobia refuses to break defended hexes), token treasury pull
(`0.15·min(t,200)`, Easy `0.25·min(t,100)`), `−6/own tree` (zeroed next to an own
lumber camp), `−2/enemy hex`, hard no-bankruptcy guard (soft −100 for Easy).
Deposits/buildings carry explicit ASSET terms (vein +10, vein-with-mine +18,
fertile +6, market +4+adjacency, camp +3+trees, fog watchtower +6; Easy gets none)
because past net +10 the income curve alone would stop all economy building.
Normal/Hard add pact value (+10/+14 per pact with a ≥1.2× stronger partner, +4
otherwise) so simulated betrayals lose the term and pay the penalty. Hard adds
`+8/enemy starving tile` (slicing), `−1.5/exposed border hex` (retake awareness,
strength-aware) and an idle-catapult upkeep nudge. Easy also considers only
~60 % of candidates (deterministic hash on `rngState + index`).

Diplomacy decisions live OUTSIDE the argmax in `DiplomacyPolicy` (RNG-free
thresholds; see the file's KDoc): answer proposals first (Easy always accepts;
Normal accepts ≥ 0.9× power or when fighting on two fronts; Hard also declines
prey and 1-v-1 pacts), then at most one initiative (propose to a ≥ 1.1× stronger
neighbor under multi-front pressure — state-side cooldowns make oscillation
structurally impossible — or tribute a ≥ 1.5× bully when the pact route is on
cooldown). Hard betrays a pacted partner only at ≥ 2× dominance when that partner
is the last obstacle, which keeps pacted duels from deadlocking.

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
- Expansion suites: `DepositEconomyTest` (per-building income rules, spread
  suppression, capture semantics), `DepositGenerationTest` (fairness property
  tests), `SpecialUnitTest` (aura/bypass/range/merge/upkeep), `DiplomacyTest`
  (pact lifecycle, auto-break penalty, pruning, replay), `AiExpansionTest`
  (usage tripwires + catapult-cracks-castle and border-hardening micro gates),
  `AiDiplomacyTest` (pact honoring, anti-oscillation, liveness), and
  `persist/LegacySaveTest` (pre-expansion saves decode + replay identically;
  every new serialized field must appear in its strip-list).
