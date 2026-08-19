# Game Rules — as implemented

The authoritative values live in `core/.../model/RuleConstants.kt` and are snapshotted
into every game's `GameConfig`, so saves are self-describing and rules are tunable
per game without breaking old saves.

## Constants (defaults)

| Constant | Value | Notes |
|---|---|---|
| Unit cost (T1–T4) | 10 / 20 / 30 / 40 | Any tier directly buyable |
| Unit upkeep (T1–T4) | 2 / 6 / 18 / 54 | Per turn; strength = tier |
| Max tier | 4 | Peasant, Spearman, Baron, Knight |
| Move ranges (T1–T4) | 3 / 4 / 5 / 6 | BFS steps through own territory; capture = final step; Archer 3 |
| Hex income | 1 /turn | Owned, non-starving, flora-free hexes |
| Farm | cost 12 + 2×(farms owned), +4 income | Must be adjacent to own Capital or Farm, or stand on fertile ground |
| Tower | cost 15, defense 2 | Covers self + 6 neighbors, no upkeep |
| Strong Tower ("Castle") | cost 35, defense 3 | Covers self + 6 neighbors |
| Capital | defense 1 (self + neighbors) | Loots 50 % of treasury when captured |
| Starting treasury | 12 | |
| Starting region | capital + 6 neighbors (7 hexes) | No starting units |
| Tree clear bonus | +3 coins | |
| Tree spread chance | 10 % per tree per owner-turn | |
| Initial trees | count = 8 % of **all** land hexes | Placed on neutral land, never in/adjacent to start regions |
| Fog of war | off by default | Optional per game; see [fog-of-war.md](fog-of-war.md) |
| Vision radii (fog on) | owned 2 / unit 3 / Capital+Tower+Castle 4 | `visionRadiusOwned` must stay ≥ 2 (Legality/AI invariant) |
| Gold vein (deposit) | 1 per player (band 3–6 from capital) + 1 per 150 land hexes in the middle | Permanent terrain; the only place a Mine can stand |
| Fertile ground (deposit) | +1 hex income; +2 extra for a Farm on it | 2 per player (band 2–5) + 3 % of neutral land; anchors a Farm without a chain; no Lumber camp allowed on it |
| Mine | cost 20, +6 income | Gold-vein hexes only; destroyed on capture (vein survives) |
| Market | cost 25, +1 per adjacent owned producing hex (cap 5) | Standard placement |
| Lumber camp | cost 15, +2 per adjacent own tree (cap 4) | Adjacent trees never spread ("managed forest"); never on fertile ground |
| Watchtower | cost 8, defense 0, vision radius 6 | Fog-of-war games only (hard legality gate) |
| Archer | cost 14, upkeep 4, strength 1 | Defense aura 2 over its hex + adjacent own hexes; never merges |
| Catapult | cost 30, upkeep 10, strength 2, move range 2 | Ignores building defense entirely; never merges |
| Transport boat | cost 15, upkeep 4, move range 3 | Carries 1 land unit (any type); bought at a Port onto adjacent sea |
| Warship | cost 25, upkeep 8, strength 2, move range 3 | Sinks boats (naval ties go to the **attacker**); bombards the coast |
| Fishing dory | cost 14, upkeep 3, strength 0, move range 3 | Earns +6/turn parked on a fish shoal (one boat per shoal); never attacks |
| Port | cost 20, +2 income | Own coastal land; sells boats; supplies overseas regions (see Naval rules) |
| Beachhead grace | 3 turns | Sea-captured hexes carry landing stores; the starving region skips unit deaths while stocked |
| Fishery | cost 18, +3 per fish shoal within 2 hexes (cap 3) | Own land with a shoal in operating range (`fisheryRange` 2) |
| Bridge | cost 15 per hex | Built on sea touching own land/bridge; walkable ground, blocks boats |
| Demolish refund | 50 % of cost | Razing an own building / disbanding an own unit returns half its price (`demolishRefundPercent`) |
| Fish shoal (deposit) | 1 per player (band 2–6, fishery-workable when the water allows) + 1 neutral per 150 land hexes mid-ocean | Sea-only deposit; worked by a Fishery in range and/or a parked Fishing dory |
| Pact duration | 2–10 rounds (proposals default to 6) | Unanswered proposals lapse after 1 full round |
| Pact proposal cooldown | 6 rounds per pair | Anti-spam, enforced by Legality |
| Pact break penalty | 25 % of the breaker's treasury, paid to the victim | Breaking = capturing a partner's hex (no explicit action) |
| Civilization bonuses | on by default | Per-civ rule deltas (`civBonusesEnabled`); see [civilizations.md](civilizations.md) |
| Scripted events | off by default | Campaign-only (`scriptedEventsEnabled`); see [campaign.md](campaign.md) |
| Disabled buildings | none by default | Campaign-only per-structure gate (`disabledBuildings`) |

## Core mechanics

**Movement (per-tier ranges).** A fresh unit marches up to its **move range** in BFS
steps through its own connected territory (friendly units and buildings never block
the path — bridges carry it over water — but the destination must be stand-able and
empty), and the **final step may capture** one adjacent non-owned hex it can beat.
Ranges: Peasant 3 / Spearman 4 / Baron 5 / Knight 6 (`soldierMoveRanges`), Archer 3,
Catapult 2 — the same bounded-BFS model the ships always had, so a selected unit's
whole reach reads as one local highlight blob. Any move spends the unit for the turn.
Freshly bought units on owned hexes are unspent; buying directly onto a capturable hex
performs the capture and arrives spent (buy-placement is not movement and is
unchanged).

**Capture.** Attacker strength must be **strictly greater** than the hex defense:
`defense(hex) = max(unit on hex, owner's units on adjacent own hexes, tower/castle/capital coverage)`.
Neutral hexes defend at 0. Capturing kills the defender (combat kills leave **no**
gravestone — the attacker occupies the hex), destroys buildings (except the Capital,
which pays loot and relocates), clears trees for the bonus, and immediately
recomputes starvation for affected players.

**Merging.** A fresh unit may merge with a same-tier friendly unit **within its move
range** (same path rules as movement), producing one unit of tier+1 (max 4).
The moving unit is consumed; the result keeps the stationary unit's spent flag.
Buying a unit onto a same-tier own unit merges instantly ("buy-merge").

**Capital capture.** Attacker gains `loot = victim.treasury × 50 %`; the victim's
capital relocates to their largest remaining region (preferring empty tiles, chosen
deterministically via state RNG). A player with zero hexes is eliminated.

**Slicing / starvation.** After every ownership change, each player's owned hexes are
flood-filled from their capital. Disconnected hexes are flagged `starving`: they stay
owned and capturable but produce no income, can't fund purchases, and any units on
them die (→ gravestones) at that player's next turn start.

**Trees & gravestones.** A gravestone converts to a tree at its owner's first turn
start at least one full round after creation. Each tree on/adjacent to the current
player's territory rolls a 10 % spread onto a random adjacent empty land hex (state
RNG, deterministic seat order). A unit entering a tree hex clears it (+3, spends);
entering a gravestone hex tramples it silently. Trees/gravestones block hex income.

**Bankruptcy.** At turn start, `treasury += income − upkeep`; if the result is
negative, treasury is set to 0 and **all** of that player's units die (gravestones).

**Victory.** Last non-eliminated color wins. Surrender reverts territory to neutral,
kills the quitter's units, and passes the turn.

**Demolition & disbanding.** The current player may raze any own building except
the Capital (`DemolishBuilding`) and dismiss any own unit, fresh or spent
(`DisbandUnit`). Both refund `demolishRefundPercent` (50 %) of the piece's cost —
for a Farm, of the **last** farm's price (base + step × (farms − 1)), so
build-then-demolish always loses money; a loaded transport's refund includes its
cargo (which goes down with the boat). Demolishing a bridge reverts the hex to
open neutral water (refused while a unit stands on the span) and, like razing a
Port, immediately recomputes starvation — cutting your own supply line is legal
and instant. Disbanded units leave **no** gravestone and do not count as campaign
"units lost". Both actions are undoable within the turn like any other.

**Bridge rotation.** A bridge's deck is cosmetic but persistent: `RotateBuilding`
stores one of 3 axes on the tile (`Tile.bridgeOrientation`; the deck is
180°-symmetric), free of charge. Unrotated spans auto-align with their chain's
through-axis and re-aim as the chain grows; a stored axis is a sticky override.

**Terrain deposits.** Gold veins and fertile ground are permanent terrain placed at
map generation (fair by construction: each capital gets its own inside its Voronoi
cell; contested extras sit in the map middle, outside every fair zone). Deposits
survive capture, never stack with flora at generation, and — like everything else —
produce nothing while the hex is starving or overgrown. Under fog they behave as
terrain: remembered (dimmed) on explored hexes, hidden only where never seen.

**Special units.** Archers and Catapults live beside the soldier ladder
(`GameUnit.type`, tier fixed at 1). The Archer projects tower-like defense
(aura 2 over its hex + adjacent own hexes) through the ordinary max-based defense
formula but attacks at strength 1. The Catapult attacks at strength 2 and ignores
building defense entirely — the designed answer to castle stalemates — but moves at
most 2 hexes per action, so units can intercept it. Specials never merge (any path)
and pay per-type upkeep into the normal bankruptcy math.

**Diplomacy (light).** Players may propose non-aggression pacts
(accept/decline on the target's turn) and gift tribute. All of it flows through
ordinary actions in the save's action log — fully replayable, zero RNG. There is no
explicit "break" action: capturing a partner's hex breaks the pact automatically and
transfers 25 % of the breaker's treasury to the victim (plus a lifetime break
counter the AI reads as reputation). Pacts expire after their agreed duration;
elimination prunes a player's pacts and proposals. Victory stays conquest-only —
pacts are temporary tools, not alliances.

**Sea & naval play.** Sea is first-class terrain (`Tile.terrain = SEA`): never
owned, no flora, no gravestones, no income; its only deposit is the fish shoal.
(The "no income" rule has exactly one exception, and it rides a unit, not the
tile: a **fishing dory parked on a shoal** pays its owner at turn start — see
Fishing below.) The one exception on the terrain side is a **bridge** — a sea hex carrying `Building.BRIDGE` *is*
owned, walkable ground: region flood-fills join across it, land units stand on
and storm it (capturing a bridge hex **preserves** the span), and boats cannot
pass under it. Chains grow hex by hex from your land or an existing bridge.
Warship bombardment collapses a bridge back into open neutral water; surrender
and elimination leave bridges standing as neutral spans.

**Fishing.** Fish shoals are worked two ways, stacking freely. A **Fishery**
(own land, shoal within `fisheryRange` = 2) earns +3 per shoal in range, cap 3 —
rival fisheries may share a shoal. A **fishing dory** (bought at a Port like any
boat — it rides `navalEnabled` alone, not `specialUnitsEnabled`; strength 0,
never attacks) earns +6/turn whenever it *starts the turn*
parked on a shoal hex — the game's only income-producing unit. Hex occupancy
makes shoals exclusive for boats: one dory per shoal, and an enemy hull parked
there blocks yours. Anywhere else the dory is pure upkeep; anything sinks it,
so guard the fleet or fish behind your own coast. Neutral mid-ocean shoals are
deliberately out of every fishery's reach — they are the dory's hunting ground
and the warship's bait.

**Boats.** Transports and warships are bought at a Port onto adjacent sea and
move by BFS over open sea (range 3, blocked by bridges, other boats and sea
buildings). They occupy sea hexes without owning them. **Embark:** moving a
fresh land unit onto an adjacent own empty transport stows it (capacity 1; the
boat can still sail this turn; tapping a *loaded* transport refuses with the
specific "transport is full" message rather than a generic unreachable). **Disembark** onto adjacent land — onto an own
empty hex, or an amphibious assault onto any hex the cargo's strength could
capture normally; boat and landed unit end spent. A sunk boat drowns its cargo
(no gravestone at sea). Cargo pays its normal upkeep while at sea.

**Warships.** Strength 2 at sea; sinking an enemy boat needs strength ≥ defender
(**ties to the attacker** — no naval stalemates) and moves in without capturing
the hex. **Bombard** is a raid on an adjacent coastal hex: if warship strength
**exceeds** the hex defense it kills the unit and destroys the building — but
never captures ground, and Capitals are immune. Towers (defense 2) fully block
it. Warships cannot be attacked from land.

**Overseas supply.** A region disconnected from the capital normally starves;
four rules make island conquest viable: (A) an own **Port** feeds its region —
but only on a landmass *other than* the capital's, so slicing on the mainland
still works; (B) units adjacent to an own boat never starve (fleet lifeline);
(C) a Port may be built *on* a starving overseas region, which then un-starves
it; (D) **beachhead grace** — a hex captured from the sea (disembark) carries
landing stores (`Tile.graceTurns` = `beachheadGraceTurns`, default 3). While a
starving region holds at least one stocked tile, its units skip starvation at
turn start and every stocked tile burns one turn of stores; captures made from
a stocked tile inherit its remaining stores, so the invasion can expand without
resetting the clock. Grace suspends only the deaths — a graced region still
earns nothing and cannot fund purchases — and ends for good the moment the
region is fed normally (reconnection or a Port). Slicing captures on the
mainland never stamp stores, so classic slicing kills on schedule.

**Map types.** Setup offers Continent (one landmass in open water), Islands and
Archipelago (real water-separated islands, ≥ 2-hex-wide navigable channels, one
capital per island where possible). Every landmass is wrapped in a coastal sea
band that scales with map size (Small 3 / Medium 4 / Large 5 hexes), and any
basin enclosed by the land — the middle of an island ring — fills in as a
sailable inland sea. Map size counts *land* hexes.

**Fog of war (optional, off by default).** Classic fog: hexes outside a player's
live vision render near-black; once-seen hexes persist as dimmed terrain-only
"explored memory" (`PlayerState.discovered`, monotonic). Vision is derived —
never stored — from owned hexes, own units, and vision buildings. Every sea hex
starts pre-discovered, so the ocean's shape reads as explored terrain from turn
one — but live vision stays pure radius, so enemy boats appear only when seen.
No action can target an unseen hex (radius-2 guarantee on land; at sea every
naval move range is ≤ `visionRadiusUnit`), the AI honors fog symmetrically, and
the fog lifts when the game ends. Full spec: [fog-of-war.md](fog-of-war.md).

**Civilizations.** Each seat plays a civilization (`PlayerState.civ`; Setup picks it,
campaign/custom maps may author it via `LevelDef.civs`). Kingdom is the baseline; the
others apply a light delta table (`CivModifiers`) to the game's rules snapshot,
resolved through `Rules.effectiveRules(state, player)` — so every price, income,
upkeep and special-unit stat above is read at the **owner's** effective rules. The
soldier ladder (tier cost/upkeep/strength/move ranges) is universal by design.

| Civ | Deltas from the table above |
|---|---|
| Kingdom | none (identity) |
| Vikings | Warship strength 3; Transport 10; Port 15 — Farm step 3; Archer 18 |
| Sultanate | Market 21; Mine +7; Farm base 10 — Warship 30; Lumber camp +1/tree |
| Shogunate | Tower 12; Watchtower 5; Archer upkeep 3; Catapult range 3 — Port 25; Transport 20 |

`civBonusesEnabled` (default on) gates the whole table: off, civs pick art only.
Pre-civilization saves decode as all-Kingdom and replay bit-identically. Full spec
(engine resolution, art sets, extension recipe): [civilizations.md](civilizations.md).

**Campaign missions.** Authored levels reuse every rule above, and add nothing to them.
They restrict: a mission's `RuleConstants` snapshot can cap `maxTier`, switch whole systems
off (`specialUnitsEnabled` / `navalEnabled` / `diplomacyEnabled`), disable individual
buildings (`disabledBuildings`), or zero out income and upkeep entirely — which is how the
tutorial teaches one idea at a time. Two additions exist only for missions:
`Difficulty.PASSIVE`, a seat that does nothing but end its turn, and
`GameAction.RunScript`, a replayable story beat that places authored reinforcements and
gold (gated by `scriptedEventsEnabled`, RNG-free, never undoable). Victory conditions
beyond conquest are scored *outside* the reducer and change no rule here — full spec in
[campaign.md](campaign.md).

## Turn-start pipeline (exact order — `TurnPipeline.kt`)

On `EndTurn`, the seat advances to the next living player (round counter increments
on wrap), then for the new player, in order:

0. Diplomacy expiry: ended pacts and stale proposals lapse (sorted event order).
1. Their gravestones ≥ 1 round old become trees.
2. Tree spread rolls (theirs + adjacent; lumber-camp-managed trees never spread).
3. Income + upkeep applied atomically (deposits + economy buildings included).
4. Bankruptcy check (negative → 0, all units die).
5. Starvation: units on their sliced-off hexes die.
6. All their units refresh (`spent = false`).
7. Elimination / victory check.

## Turn order & modes

Fixed seat order, eliminated seats skipped. Modes: single human vs 1–5 AI
(Easy/Normal/Hard) or all-human pass-and-play (privacy banner between seats;
per-seat undo stack cleared at turn boundaries). Maps: procedural
Small ≈ 120 / Medium ≈ 250 / Large ≈ 450 land hexes, 2–6 players.
