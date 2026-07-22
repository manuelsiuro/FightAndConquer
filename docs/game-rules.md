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

**Fog of war (optional, off by default).** Classic fog: hexes outside a player's
live vision render near-black; once-seen hexes persist as dimmed terrain-only
"explored memory" (`PlayerState.discovered`, monotonic). Vision is derived —
never stored — from owned hexes, own units, and vision buildings. No action can
target an unseen hex (radius-2 guarantee), the AI honors fog symmetrically, and
the fog lifts when the game ends. Full spec: [fog-of-war.md](fog-of-war.md).

## Turn-start pipeline (exact order — `TurnPipeline.kt`)

On `EndTurn`, the seat advances to the next living player (round counter increments
on wrap), then for the new player, in order:

1. Their gravestones ≥ 1 round old become trees.
2. Tree spread rolls (theirs + adjacent).
3. Income + upkeep applied atomically.
4. Bankruptcy check (negative → 0, all units die).
5. Starvation: units on their sliced-off hexes die.
6. All their units refresh (`spent = false`).
7. Elimination / victory check.

## Turn order & modes

Fixed seat order, eliminated seats skipped. Modes: single human vs 1–5 AI
(Easy/Normal/Hard) or all-human pass-and-play (privacy banner between seats;
per-seat undo stack cleared at turn boundaries). Maps: procedural
Small ≈ 120 / Medium ≈ 250 / Large ≈ 450 land hexes, 2–6 players.
