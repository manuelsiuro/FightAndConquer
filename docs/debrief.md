# Post-match debrief — "The Chronicle"

A match currently ends with a bare verdict overlay and a single button; nothing of
the game's story survives the scrim. The debrief turns the finish into a ceremony,
a story, and the numbers: one scrollable screen showing how the war was won —
per-seat timelines, the turning points, and end-of-war honours. It exists only for
the match that just ended; nothing is written to disk.

## Why a live recorder

The engine keeps no full-game action log — `engine/GameEngine.kt` clears
`actionsThisTurn` at every turn boundary, and the autosave is turn-start state +
the current turn's actions. A debrief that wants timelines and key moments must
therefore **record facts as they happen**. The recorder is a pure fold beside the
engine, `campaign/CampaignTracker`'s cousin:

- `record/MatchRecorder.kt` — `start(initialState, meta)` /
  `step(prev, before, after, events)` / `finish(finalState)`. Pure, RNG-free,
  no reducer involvement — **the reducer stays untouched**, exactly like
  campaign objective scoring.
- It reads `GameEngine.lastEvents` (the non-lossy channel), never the
  drop-oldest events flow — the same rule `CampaignTracker` documents: fine for
  a renderer that reconciles from state, fatal for a tally of facts no later
  state reveals.
- **In-memory only.** The `GameViewModel` starts it in `newGame` / `startLevel`
  / `playCustomMap`, folds it in the shared scoreboard step on both the human
  and AI submit paths, and drops it at match teardown. `SaveGame` is unchanged;
  a match resumed from an autosave plays unrecorded and simply hides the
  debrief button. Persistence (match history) is a deliberate non-goal of this
  scope.

## What is recorded

| Piece | Contents | Source |
|---|---|---|
| `MatchMeta` | mode (skirmish/pass-and-play/campaign/custom), seed, land-hex count, fog flag, level name | captured at match start (`GameSetup` is not retained afterwards) |
| `SeatDescriptor` | human/AI, difficulty, civ — one per seat, index = `PlayerId.value` | `initial.players` |
| Series | per seat, one sample per round: hexes, income, upkeep, treasury, units — parallel arrays keyed by a `rounds` list; eliminated seats simply stop | sampled on `GameEvent.TurnStarted` (income/upkeep ride the event; the rest reads the after-state); round-0 baseline at start |
| `KeyMoment` | CapitalLooted, PactBetrayed, WentBankrupt, ShipSunk, Eliminated, Crowned — round-stamped, capped at 300 | folded from `lastEvents`; attribution uses the **before**-state (`before.units[event.unit]`, `before.currentPlayer`) |
| `SeatTotals` | kills, losses, boats sunk, hexes captured, pacts broken | running event tallies |

Superlatives ("Largest realm", "Admiral", …) are computed at **display time**
from series peaks + totals — the recorder stores facts, not judgments.

## Screen anatomy

`ui/debrief/DebriefScreen.kt`, reached from the finish overlays via
`Screen.Debrief` (entering it tears the match down; back goes to the menu).
BriefingScreen frame — `background`, safe-drawing padding, vertical scroll,
bottom outlined back button — and every surface on the shipped HUD system
(opaque `panel`, hairline borders, the 12/30/100 % tint ladder, faction pastels
with `onFaction`, no emoji, no dialogs).

1. **Verdict** — the ceremony. Winner's faction disc + the capital render on a
   `PlinthScale.L` plinth (continuous with the game-over overlay's hero),
   28 sp headline, micro-meta line (map · rounds). Defeat swaps the headline
   and desaturates the hero.
2. **The shape of the war** — the hero visual. One `TimelineChart` card:
   per-seat curves in faction pastels (2 dp lines, 12 % fill for the default
   lens), key moments as small markers on the curves, animated left-to-right
   draw-in. Segmented chips swap the lens — Territory (default) / Income /
   Treasury — one chart, three lenses, instead of a stack of charts.
   A legend row of faction dots + seat labels sits beneath.
3. **Turning points** — the story feed. Chronological rows: round pill,
   actor's faction dot, one-line narrative ("AI 2 betrayed you, paying
   14 gold"). Rows describing events against the human seat carry the alert
   tint.
4. **Honours** — a two-column grid of panel cards (label, faction dot, bold
   value): Largest realm, Richest hoard, Warmonger, Admiral, Butcher,
   Survivor. Values count up on entrance.

**Entry points**: the skirmish `GameOverOverlay` gains a primary filled
"View debrief" above a now-outlined "Back to menu"; `CampaignOutcomeOverlay`
gains an outlined "View debrief" between Retry and Menu (Next stays primary).
The button hides when no recorder exists (resumed match).

## Trade-offs

- **Pass-and-play reveals everything** — the debrief shows every seat's economy
  and fog trajectory. Acceptable: fog already lifts the moment the game
  finishes, and the match is over.
- **Process death loses the chronicle** — the recorder is not carried in the
  autosave at this scope. The resumed match still plays correctly; it just
  finishes without a debrief.
- Recorder cost is O(events) per action plus one sample per seat per round —
  no per-frame work, nothing on the render path.

## Tests & gates

- `:core` — `record/MatchRecorderTest`: one sample per living seat per round +
  round-0 baseline; sunk/loot/betrayal attribution via the before-state;
  eliminated-seat series truncation; fold determinism (fold twice → equal).
- `:app` — build + existing unit suites stay green; `DebriefText`'s exhaustive
  `when` over `KeyMoment` fails compilation on an unmapped moment type.
- On device — play a SMALL skirmish to the end: screenshot the finish overlay
  with the new button and the debrief in light **and** dark; one campaign
  finish for the outcome-overlay variant; zero "reconcile corrected" warnings
  (standing gate — the renderer is untouched).
