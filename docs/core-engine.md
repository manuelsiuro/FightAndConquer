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
`config` (seed + `RuleConstants` snapshot), `tiles: Map<Hex, Tile>` (off-board =
absent; sea is a real tile with `terrain = SEA`),
`units: Map<UnitId, GameUnit>`, `players: List<PlayerState>` (index = `PlayerId.value`,
seat order = turn order), `currentPlayer`, `turnNumber` (completed rounds),
`rngState: Long` (SplitMix64 — RNG lives IN the state), `phase`
(`Playing | Finished(winner)`), `nextUnitId`.

`Tile(owner?, unit?, building?, flora?, starving, deposit?, terrain)` — `unit`
mirrors `GameUnit.hex` (dual index kept consistent by the reducer, checked by
test invariants); `deposit` (`GOLD_VEIN | FERTILE | FISH_SHOAL`) is permanent
terrain that survives capture. `terrain: Terrain = LAND` (defaulted → old saves
decode). Plain SEA is never owned, carries no flora/gravestones, and its only
legal deposit is FISH_SHOAL; the single exception is `Building.BRIDGE` — the
one building that stands ON a sea hex and owns it, making it walkable ground
that region flood-fills cross with zero changes to the region/starvation/
defense machinery. `Building`: CAPITAL, FARM, TOWER, STRONG_TOWER, MINE,
MARKET, LUMBER_CAMP, WATCHTOWER, PORT, FISHERY, BRIDGE.
`Flora`: `Gravestone(createdRound)` | `Tree`. `PlayerKind`: `Human` | `Ai(difficulty)`.

`GameUnit` carries `type: UnitType = SOLDIER` (serialized as `unitType`).
Soldiers keep strength == tier (1..4); ARCHER/CATAPULT are single-level
(tier fixed at 1) with per-type strength/upkeep from `RuleConstants` — every
strength/upkeep/cost read goes through `Rules.strengthOf/unitUpkeepOf/unitCostOf`.
TRANSPORT/WARSHIP are the naval types (`Rules.isNaval`); a transport's stowed
passenger lives in `GameUnit.cargo: CargoUnit?` (tier + type snapshot — the
embarked unit is *removed* from `units`, disembarking spawns a fresh `UnitId`;
cargo pays its normal upkeep via `unitUpkeepOf`).

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
`MergeUnits(a, b)` (a is the fresh mover), `RotateBuilding(at, orientation)`
(sets an own bridge's deck axis 0..2 on `Tile.bridgeOrientation` — cosmetic,
free, but engine state so saves and replays agree), `DemolishBuilding(at)` /
`DisbandUnit(unit)` (raze an own non-capital building / dismiss an own unit for
a `demolishRefundPercent` refund; a demolished bridge reverts to neutral sea,
and both recompute starvation where relevant), `Disembark(boat, to)`,
`Bombard(unit, target)`, `ProposePact(to, durationRounds)`,
`RespondPact(from, accept)`, `SendTribute(to, amount)`, `EndTurn`, `Surrender`, and
`RunScript(tag, spawns, grants)` — the campaign director's story beat (docs/campaign.md):
gated off by `RuleConstants.scriptedEventsEnabled` (default false), self-contained so the
reducer never needs the level, RNG-free, and undo-sealing (`GameEngine.submit` clears the
undo stack after one but keeps it in the action log).
Embarking is not a new action: `MoveUnit` onto an own empty adjacent transport
stows the mover. `MoveUnit` by a boat onto an enemy boat's hex is a naval
strike (sink + move in, no ownership change).
Serialization note: sealed-class JSON uses the `type` discriminator, so action
*properties* must not be serialized under that name (`BuyBuilding.type` →
`"building"`, unit types → `"unitType"`; a collision used to silently kill
mid-turn autosaves — regression-tested in `persist/LegacySaveTest`).

`GameEvent` — ordered facts for animation/tests: `ActionRejected`, `UnitSpawned`,
`UnitMoved(unit, from, to)`, `HexCaptured(hex, newOwner, oldOwner)`,
`UnitDied(unit, hex, cause KILLED|STARVED|BANKRUPTCY|SUNK|DISBANDED)` (DISBANDED
leaves no gravestone and is excluded from the campaign "units lost" tally),
`UnitsMerged(into, consumed)`,
`UnitEmbarked(unit, transport, from, at)`, `UnitDisembarked(transport, unit, to)`,
`Bombarded(by, target)`,
`ScriptFired(tag)` (campaign narration; the renderer needs no case for it),
`BuildingBuilt/Destroyed`, `BuildingRotated(hex, orientation)`,
`RefundPaid(player, hex, amount)` (HUD-only "+N" popup; the board renders
nothing), `TreeGrown`, `TreeSpread(from, to)`, `TreeCleared(hex,
bonus)`, `GravestoneTrampled`, `TurnStarted(player, income, upkeep)`, `Bankruptcy`,
`CapitalMoved(player, from, to, loot)`, `PlayerEliminated`, `GameOver(winner)`,
plus HUD-only diplomacy facts: `PactProposed/Accepted/Declined/Expired`,
`PactProposalExpired`, `PactBroken(breaker, victim, penalty)`, `TributeSent`.

Flow: `Reducer.reduce` → `Legality.check` (returns a `RejectionReason` code plus an
optional `amount` — the UI maps it to a localized toast) → apply on an internal mutable `StateBuilder`
(shared ops: `spawnUnit`, `killUnit`, `clearFloraAt`, `captureHex`,
`razeBuilding` (bombard + demolish; a bridge collapses to neutral open water),
`recomputeStarving`, `checkElimination`) → freeze to `ReduceResult(state, events)`.
`TurnPipeline` implements the turn-start ordering (see game-rules.md).

`Rules` — pure queries used by Legality, AI and UI: `region`,
`defenseOf(state, hex, attackerType?)` (unit contributions via
`defenseContribution` — the archer aura slots into the max model like tower
coverage; a CATAPULT attacker skips building contributions entirely),
`reachable(unitId) -> ReachResult(moveTargets, captureTargets, mergeTargets,
embarkTargets, blockedTargets, fullTransports)`
(single region scan; type-aware — specials never merge, catapults are
range-capped, land units may stand on/capture own-or-enemy BRIDGE hexes but
never plain sea; `fullTransports` are own loaded boats that would otherwise be
boardable, so Legality can refuse with the specific `TRANSPORT_FULL` instead of
a generic unreachable; naval units route through `seaReachable`, a
range-limited BFS over open sea that bridges, boats and sea buildings block),
`visibleHexes` (fog-of-war live vision,
RNG-free), `incomeOf` (delegating to `incomeFrom`, the single income source shared
with `TurnPipeline`: hex + deposit bonuses + farm/mine/market/lumber-camp),
`upkeepOf` (delegating to `upkeepFrom`, the single upkeep source shared with
`TurnPipeline`, mirroring income)/`unitUpkeepOf`,
`strengthOf`/`buyStrength`/`unitCostOf`, `nextFarmCost`, `buildingCost`,
`unitDefenseOf`/`buyDefense` (the per-unit display defense the HUD pairs with
`strengthOf`: garrison/aura value for land units, 0 for a transport, and — by
convention — a warship shows its strength, the naval sink threshold, even though
boats contribute 0 to land-hex defense), `captureRequirement` (`defenseOf + 1` —
the strictly-greater rule as a number the UI can print), and `defenseSourceOf`
(the strongest single contributor behind `defenseOf` — garrison, neighbor unit,
or tower/castle/capital — so the HUD can say "Guarded by Tower"; mirrors
`defenseOf`'s max with ties preferring the garrison, then fortifications).

Pact breaking has exactly one site: `StateBuilder.captureHex` checks for an active
pact with the victim before any mutation — covering move-capture and buy-capture —
transfers the penalty and emits `PactBroken`. Sinking a partner's boat and
bombarding a partner's coast hook the same machinery. `TurnPipeline` step 0
(`expireDiplomacy`) lapses ended pacts and stale proposals.

Naval specifics: warship strikes resolve at `warshipStrength >= defender`
(**ties to the attacker**, killing cargo too, `DeathCause.SUNK`, no gravestone
at sea); `Bombard` is a raid on an adjacent hex — strictly-greater vs
`defenseOf`, kills the unit, destroys any non-CAPITAL building (a bridge hex
reverts to neutral open sea), never captures ground. Overseas supply lives in
`StateBuilder.recomputeStarving` (Rule A: an own PORT feeds its region only on
a landmass *off* the capital's — mainland ports feed nothing, so slicing still
works) and the `TurnPipeline` starvation step (Rule B: units adjacent to an own
boat survive); `Legality` exempts PORT from the starving-purse check on
overseas colonies (Rule C) and gates naval purchases on an adjacent own
working port. `captureHex` preserves BRIDGE (`keepBridge`); surrender and
elimination neutralize bridges instead of removing them.

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

## Campaign (`campaign/`)

Pure data and pure functions for authored missions: `LevelDef`/`CampaignDef`,
`Objective`/`FailCondition`, `Objectives.evaluate` (a pure verdict, called from the
ViewModel — **never** from the reducer, so `GamePhase` keeps meaning "the conquest is
over"), the `CampaignTracker` fold for facts state cannot imply, `LevelCondition` shared by
coach hints and story beats, `LevelFactory`, and `CampaignProgress`. Full spec:
[campaign.md](campaign.md).

`Difficulty.PASSIVE` is a seat that only ends its turn (`AiPlayer` short-circuits) — a
training dummy for tutorial missions; excluded from skirmish setup via
`Difficulty.selectable`. `RuleConstants.disabledBuildings` turns individual structures off,
which is how the tutorial teaches one building at a time without any gating code.

## Persistence (`persist/`)

`SaveGame(version, turnStartState, actionsThisTurn, campaign?)` — restoring **replays** the
turn's actions through the reducer, which doubles as an integrity check
(`fromSave` rebuilds the undo stack via `submit`). JSON via `SaveCodec`,
which points at `persist/CompatJson` — the one configuration every persisted
artifact shares (saves, campaign definitions, custom maps, progress):
`ignoreUnknownKeys` for forward compatibility, `encodeDefaults` so artifacts
stay self-describing across rule tuning. The app stores one autosave at
`filesDir/autosave.json`; finished games delete it. `campaign: CampaignSaveRef?` (defaulted)
names the mission and carries the turn-start `CampaignTracker`, which is re-folded across
the replayed actions so a resumed mission scores identically to one never interrupted.
Campaign career progress is a separate permanent file (`CampaignProgress`).

## Map generation (`map/`)

`MapParams(seed, size SMALL/MEDIUM/LARGE ≈ 120/250/450, playerCount 2–6, shape
CONTINENT/ISLANDS/ARCHIPELAGO)` → `MapDefinition(name, generatorParams?, tiles,
capitals)`; `newGame(gameSeed, kinds, rules)` instantiates a `GameState`.
Authored campaign maps use the same format with `generatorParams = null`, and are checked
by `MapValidator.validateAuthored` — structural invariants only (sea contract, reachable
land, marked/owned capitals, each seat's start region connected), because an authored level
is asymmetric on purpose. Full campaign format in [campaign.md](campaign.md).

Algorithm: seeded random-walk blob growth (frontier weighted `(1+landNeighbors)²`).
CONTINENT is one landmass; ISLANDS/ARCHIPELAGO
grow per-blob with a keep-out predicate (no hex within distance 2 of another
blob → every channel is ≥ 2 wide and navigable). `seaSurface` wraps every
landmass in a size-scaled fringe (`seaFringe`: SMALL 3 / MEDIUM 4 / LARGE 5),
adds corridors between ring-adjacent island centers, pocket-fills any void the
map encloses (an island ring's basin becomes an inland sea; flood-fill from a
bounding rim finds what the outside can't reach), then keeps the largest sea
component — which is also what keeps growBlob's land-sealed interior holes
void instead of turning them into unreachable lakes (`targetHexes`
still counts *land*). Capitals via farthest-point sampling seeded from the rim
(viable = full neighbor ring on land), fairness floor
`max(5, 0.9·√(land/players))`, one capital per island where the count allows.
Start regions = capital + ring. Deposits (`placeDeposits`) are fair by construction: each capital's
gold vein(s) sit inside its Voronoi cell at a common per-attempt target distance
(nearest-vein spread ≤ 2 guaranteed), FERTILE in band 2–5 likewise; contested
neutral deposits stay outside every fair zone; on cramped maps with no room the
kind is skipped for everyone (zero-for-all is still fair); FISH_SHOAL uses the
same fair Voronoi banding on *sea* hexes (band 2–6 per capital + one
contested neutral per 150 land hexes); per-capital shoals walk the band for a
target every capital can serve with **fishery-workable** water (within
`fisheryRange` of land), falling back to unfiltered placement when no target
fits, while neutral shoals stay mid-ocean by design (fishing-boat territory). Tree count = 8 % of
total land, placed on unprotected neutral non-deposit hexes. `MapValidator` enforces:
shape-aware land connectivity (CONTINENT one component; ISLANDS/ARCHIPELAGO
capitals pairwise land-disconnected), sea single-component with every landmass
touching it, the sea contract (no owner/flora, FISH_SHOAL the only sea deposit),
marked/owned capitals, spacing, equal regions, no flora in starts,
no deposits in starts / under flora, and the deposit/shoal fairness bounds (as
tripwires); the generator retries derived seeds (≤32) until valid.

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
`state.tiles` for unseen hexes would leak the coastline), ports on coastal spots,
fisheries ranked by capped shoals-in-range (`Rules.shoalsWithin`, radius
`fisheryRange`), warship raids (`Bombard` where defense < warship
strength) and warship buys when enemy WAR boats are visible (a fishing dory is
prey — evaluator −1 — never a purchase trigger). Easy skips structures
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

Naval strategy likewise lives OUTSIDE the argmax in `ai/NavalPolicy.kt` — a
deterministic, stateless threshold ladder consulted before the greedy loop
(pattern copied from `DiplomacyPolicy`, and for the same reason: one-ply greedy
never *starts* a multi-turn plan — a transport's upkeep repels the evaluator
before any invasion pays off). A third ladder, `ai/FishingPolicy.kt`, follows
NavalPolicy (war wins treasury contention) and gates on `navalEnabled` alone —
dories are boats, not "specials", mirroring `checkBuyNaval`: buy a dory while
open shoals outnumber the hulls still *sailing* for one (parked hulls already
subtract themselves via occupancy — counting the whole fleet stranded a shoal
per parked boat under staggered buying; cap 3, income-funded only), sail each
onto the nearest open shoal (`ai/Sailing.kt`, the shared steering primitives,
with `ontoGoals` seeding the distance field AT sea goals), park forever, and
disband only a provably *surplus* hull — every shoal on the chart worked by an
own parked dory, a fog-free fact. A visibly squatted or ferry-transited shoal
is waited on, never written off: under fog the squatter fades from view when
the hull dies and a stateless policy re-buys forever (a disband/re-buy money
pump). Shoal positions are chart knowledge, occupancy is fog-honest. Overseas
mode (enemies exist but none reachable
by land): disembark → sail loaded transports toward *beatable* beaches
(sea-BFS distance fields, never straight-line — local minima cause shore-
hugging loops) → embark → buy transport → build port → war-chest fallback
(save for a stronger invader, or span a 1-hex strait with a bridge instead of
running a ferry line) → war economy (when even a peasant marine is
unsustainable OR the standing army holds net income at or below zero, and no
loaded transport is at sea, demobilize the costliest surplus soldier — never
the capital guard, never the strongest one, never while invaded — so refunds
and freed upkeep finance the fleet, and a standoff garrison is trimmed until
income runs the surplus that refills the war chest instead of bleeding into
bankruptcy). Every destination is a pure function of the state so consecutive
turns can't oscillate. Hard adds sea control: when enemy boats are
visible, buy one warship, shadow the ferries, and let the greedy loop land the
kill. Easy gets no ladder at all — aimless at sea by design.

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
- AI simulations: full games terminate < 400 rounds with invariants; Hard ≥ 60 %
  vs Easy over 30 mirror seeds (measures ~71 % since the 2026-08 turtle and
  war-economy fixes — the evaluator caps its retake penalty by force balance,
  and NavalPolicy demobilizes an idle army to fund the fleet; the remaining
  amphibious-fortress stall is a known gap in roadmap.md); Easy expands within
  3 rounds; turns < 1 s on LARGE; AI games fully deterministic; fog games
  terminate across seeds 1–10 (the historical seed dodge is retired).
- Expansion suites: `DepositEconomyTest` (per-building income rules, spread
  suppression, capture semantics), `DepositGenerationTest` (fairness property
  tests), `SpecialUnitTest` (aura/bypass/range/merge/upkeep), `DiplomacyTest`
  (pact lifecycle, auto-break penalty, pruning, replay), `AiExpansionTest`
  (usage tripwires + catapult-cracks-castle and border-hardening micro gates),
  `AiDiplomacyTest` (pact honoring, anti-oscillation, liveness), and
  `persist/LegacySaveTest` (pre-expansion saves decode + replay identically;
  every new serialized field must appear in its strip-list).
- Naval suites: `TerrainTest`/`SeaMapTest` (sea contract + shape-aware
  generation properties), `NavalMovementTest`, `EmbarkDisembarkTest`,
  `BombardTest`, `BridgeTest` (chain build, region-join un-starves, contest
  preserves, bombard destroys, surrender neutralizes), `SeaSupplyTest` (the
  full invasion loop), `SeaEconomyTest`, `FishShoalGenerationTest`, and
  `NavalAiTest` (two-island conquest within bounded rounds + random-island
  termination; the Hard-mirror winrate gate covers island maps too).
- Demolition & rotation suites: `RotateBridgeTest` (axis stored + event,
  rejections, undo, bombard clears the orientation) and `DemolishDisbandTest`
  (refund arithmetic incl. the last-farm price, capital/enemy/occupied-bridge
  rejections, bridge→neutral-sea + restarve, port demolition restarves the
  colony, no gravestone on disband, loaded-transport cargo refund, undo,
  campaign units-lost exclusion).
