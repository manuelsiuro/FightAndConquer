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
| Hex income | 1 /turn | Owned, non-starving, flora-free hexes |
| Farm | cost 12 + 2×(farms owned), +4 income | Must be adjacent to own Capital or Farm |
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
| Fertile ground (deposit) | +1 hex income; +2 extra for a Farm on it | 2 per player (band 2–5) + 3 % of neutral land |
| Mine | cost 20, +6 income | Gold-vein hexes only; destroyed on capture (vein survives) |
| Market | cost 25, +1 per adjacent owned producing hex (cap 5) | Standard placement |
| Lumber camp | cost 15, +2 per adjacent own tree (cap 4) | Adjacent trees never spread ("managed forest") |
| Watchtower | cost 8, defense 0, vision radius 6 | Fog-of-war games only (hard legality gate) |
| Archer | cost 14, upkeep 4, strength 1 | Defense aura 2 over its hex + adjacent own hexes; never merges |
| Catapult | cost 30, upkeep 10, strength 2, move range 2 | Ignores building defense entirely; never merges |
| Pact duration | 2–10 rounds (proposals default to 6) | Unanswered proposals lapse after 1 full round |
| Pact proposal cooldown | 6 rounds per pair | Anti-spam, enforced by Legality |
| Pact break penalty | 25 % of the breaker's treasury, paid to the victim | Breaking = capturing a partner's hex (no explicit action) |

## Core mechanics

**Movement (Slay-style).** A fresh unit may, as ONE action, move anywhere within its
connected owned region and/or capture a single hex adjacent to that region. Any move
spends the unit for the turn. Freshly bought units on owned hexes are unspent; buying
directly onto a capturable hex performs the capture and arrives spent.

**Capture.** Attacker strength must be **strictly greater** than the hex defense:
`defense(hex) = max(unit on hex, owner's units on adjacent own hexes, tower/castle/capital coverage)`.
Neutral hexes defend at 0. Capturing kills the defender (combat kills leave **no**
gravestone — the attacker occupies the hex), destroys buildings (except the Capital,
which pays loot and relocates), clears trees for the bonus, and immediately
recomputes starvation for affected players.

**Merging.** A fresh unit may merge with any same-tier friendly unit **in its own
connected region** (no adjacency required), producing one unit of tier+1 (max 4).
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

**Fog of war (optional, off by default).** Classic fog: hexes outside a player's
live vision render near-black; once-seen hexes persist as dimmed terrain-only
"explored memory" (`PlayerState.discovered`, monotonic). Vision is derived —
never stored — from owned hexes, own units, and vision buildings. No action can
target an unseen hex (radius-2 guarantee), the AI honors fog symmetrically, and
the fog lifts when the game ends. Full spec: [fog-of-war.md](fog-of-war.md).

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
