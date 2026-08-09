# Campaign Mode

Three campaigns of authored missions on hand-built maps, with objectives beyond
last-colour-standing, a coach that teaches the rules one at a time, scripted story beats,
and per-mission star ratings.

| Campaign | Id | Missions | Theme |
|---|---|---|---|
| The Academy | `academy` | 8 | Tutorial — one new idea per mission |
| The Sundered Isles | `isles` | 6 | Naval story — logistics before combat |
| The Iron Crown | `crown` | 6 | Land/economy story — a succession war |

## Where the pieces live

```
core/campaign/                     Pure data + pure functions (host-testable)
  CampaignDef.kt      CampaignDef / LevelDef / SeatDef / UnitPlacement
  Objective.kt        Objective + FailCondition vocabularies
  Objectives.kt       evaluate(state, tracker, level) -> CampaignStatus
  Conditions.kt       LevelCondition — one predicate vocabulary for hints AND scripts
  Hints.kt            HintStep + Hints.advance
  Scripts.kt          ScriptTrigger + Scripts.next
  CampaignTracker.kt  The scoreboard GameState cannot imply (pure fold)
  LevelFactory.kt     LevelDef -> opening GameState
  CampaignProgress.kt Permanent career record + unlock rules
  CampaignCodec.kt    JSON for the shipped definitions
  CampaignSave.kt     CampaignSaveRef + tracker restore-by-replay

app/src/main/assets/campaigns/     academy.json, isles.json, crown.json
tools/campaign_src/*.py            The readable sources those are baked from
tools/build_campaigns.py           The bake step

app/.../ui/campaign/               CampaignScreen, BriefingScreen, repository,
                                   progress store, CampaignText (id -> @StringRes)
app/.../ui/game/CampaignHud.kt     Objectives panel, coach card, outcome overlay
```

## The load-bearing decision: scoring sits outside the reducer

`Objectives.evaluate` is a **pure function of `(GameState, CampaignTracker, LevelDef)`**
called from the ViewModel — it is not part of `Reducer`, and `GamePhase` keeps meaning
exactly what it meant before ("the conquest is over"). Consequences worth knowing:

- determinism, save replay and the AI are untouched by anything in this document;
- a mission's terms can change without touching the rules engine;
- **total conquest settles any mission.** With no rival left there is no pass to hold and
  no clock to beat, so `Finished(winner == you)` is a win whatever the objectives said.
  Without that rule a "hold this ridge" level the player simply wins outright would hang
  forever waiting for an opponent who no longer exists.
- **defeat outranks victory.** A turn limit that expires on the very turn the last
  objective completes is still a loss. That ordering is the mission's promise to the
  player, and it is pinned by a test.

## Authoring a mission

Sources are Python modules under `tools/campaign_src/`, each exporting `CAMPAIGN`. Bake
with:

```bash
python3 tools/build_campaigns.py           # all campaigns
python3 tools/build_campaigns.py academy   # just one
./gradlew :core:test                       # validates what you just baked
```

A map is ASCII art, because a map written as JSON tile records cannot be reviewed by eye.
Row `r`, column `c` maps to axial `q = c - r // 2`, so indenting odd rows lines the
printed grid up with the board.

```
~  ~  ~   ~  ~  ~          ~  ~  ~  ~
  ~  .  0   0  .  1K:keep_a 1  .  ~  ~
~  0  0C  0  .  1          1C 1  ~  ~
  ~  0  0   .  .  1K:keep_b 1  .  ~  ~
~  ~  .   .  .  .          .  ~  ~  ~
  ~  ~  ~   ~  ~  ~          ~  ~  ~  ~
```

| Token | Meaning |
|---|---|
| `-` | off-map void (no tile) |
| `~` / `~*` | open sea / sea with a fish shoal |
| `B0` | bridge on sea, owned by seat 0 |
| `.` | neutral land |
| `.t` `.g` | tree / gravestone |
| `.$` `.%` | gold vein / fertile ground |
| `0` | land owned by seat 0 (any digit is a seat) |
| `0C 0T 0K 0F 0M 0R 0L 0W 0P 0Y` | capital, tower, castle, farm, mine, market, lumber camp, watchtower, port, fishery |
| `:name` | declares an **anchor** on that hex |

Anchors are the reason this is maintainable: anywhere in the level dict, `"@keep_a"` is
replaced by that hex, so objectives, hints and story beats never contain a packed
integer. Capitals additionally get `@capital0`, `@capital1`, …

### The teaching lever

The Academy does not gate actions. It switches off what it has not taught yet, using
rules that already existed plus one new field:

| Lever | Effect |
|---|---|
| `maxTier` | caps the soldier ladder |
| `specialUnitsEnabled` / `navalEnabled` / `diplomacyEnabled` | whole systems off |
| `disabledBuildings` | per-building, so one structure can be taught at a time |
| `hexIncome: 0` + `unitUpkeep: [0,0,0,0]` + `treasury: 0` | no economy at all — and because `GameEngine.buyableAt` already filters by affordability, the purchase tray is simply **empty**. Mission 1 has nothing on screen but two soldiers and some ground. |

`disabledBuildings` is enforced in `Legality.checkBuyBuilding`, so the AI cannot build
what the player cannot either.

`Difficulty.PASSIVE` (shown as "Dormant") is a seat that only ever ends its turn —
a training dummy for missions whose lesson a live opponent would drown out. It is
excluded from `SetupScreen` via `Difficulty.selectable`. Give every dormant seat a large
purse: a bankruptcy would wipe the garrison the mission is built around.

## Objectives and defeat clauses

A mission is won when **every** objective is complete.

| Objective | Progress shown as |
|---|---|
| `ConquerAll` | last one standing |
| `CaptureHexes(hexes)` | owned / named |
| `HoldHexes(hexes, rounds)` | rounds held / rounds needed |
| `SurviveRounds(n)` | round / n |
| `OwnHexCount(n)`, `ReachTreasury(n)`, `ReachIncome(n)` | have / need |
| `EliminatePlayer(seat)` | yes/no |
| `BuildCount(building, n)`, `FieldUnits(type, n)` | have / need |
| `SinkBoats(n)` | tracked cumulatively |

Defeat: losing every hex is **always** a loss and needs no clause. The authored extras are
`TurnLimit(rounds)`, `LoseHexes(hexes)` ("protect this"), `LoseAllUnits`, and
`AllyEliminated(seat)`.

A hold streak **resets** when the grip breaks; it does not pause.

## Hints and story beats share one predicate vocabulary

`LevelCondition` is used by both, so a level reads the same whether it is coaching or
narrating: `RoundAtLeast`, `OwnHexCountAtLeast`, `OwnsHexes`, `LostAnyHex`,
`UnitCountAtLeast`, `BuildingCountAtLeast`, `TreasuryAtLeast`, `IncomeAtLeast`,
`ObjectiveDone`, `PlayerEliminated`, `All`, plus two UI-only cases:

- `Acknowledged` — the card waits for the player to tap "Got it".
- `UiSignal(name)` — a teaching moment no board state implies. The names live in
  `ui.UiSignals` (`unitSelected`, `economyOpened`, `diplomacyOpened`) and a test fails the
  build if a level waits on one the app never emits. **Meaningless for a story beat** (the
  director is headless there) — also test-enforced.

Hints are a **queue, not a state machine**: a step is shown until its condition holds, then
the script advances. It cannot dead-end.

`HintStep.focus` hexes get a pulsing ring on the board (`HighlightSet.hintFocus`), which is
why the prose never has to contain coordinates.

## Story beats: `GameAction.RunScript`

A beat is an ordinary, replayable action:

```kotlin
RunScript(tag, spawns: List<ScriptSpawn>, grants: List<ScriptGrant>)
```

Design constraints, all of them load-bearing:

- **Self-contained payload.** The reducer never needs the level definition, so a saved
  action log replays a beat exactly as it first fired.
- **No RNG.** `rngState` is untouched (tested).
- **Gated off by default.** `RuleConstants.scriptedEventsEnabled` is `false`, and
  `LevelFactory` derives it from whether the level actually has triggers — so a skirmish
  can never contain a beat, and an author cannot leave their own beats switched off.
- **Legality is the same shape as a purchase**: a spawn must land on an owned, empty,
  flora-free land hex of its own player (or open sea, for a boat).
- **Not undoable.** `GameEngine.submit` clears the undo stack after a `RunScript` but keeps
  it in the turn's action log — a story beat is not the player's move to take back.
- **Skipped, not consumed**, when currently illegal (its landing hex is occupied): the
  trigger stays armed for a later turn.
- Beats fire **only on the player's own turn**, one per pass, so each gets its own
  animation and lands in the right turn on replay.

The renderer needs no case for `GameEvent.ScriptFired` — the spawns arrive as ordinary
`UnitSpawned` events, and the tag becomes a toast.

## The tracker, and why saves need it

`CampaignTracker` holds the handful of facts a `GameState` cannot tell you: how long a hex
has been held, which beats already fired, and the cumulative tallies (`boatsSunk`,
`unitsLost`, `treesCleared`, `hintIndex`). It advances by a pure fold —
`CampaignTracker.step(prev, before, after, events, seat, objectives)`.

It reads `GameEngine.lastEvents`, **not** the `events` flow: that flow is drop-oldest by
design, which is fine for a renderer that reconciles from state and fatal for a tally of
facts no later state reveals.

`SaveGame.campaign: CampaignSaveRef?` (a defaulted field, so pre-campaign saves are
unaffected — `persist/LegacySaveTest`) carries the campaign id, the level id, the
**turn-start** tracker and the observed UI signals. Loading re-folds the tracker across the
replayed actions, so a resumed mission scores identically to one never interrupted. The
tracker stored is the pre-turn one on purpose: storing the live one would double-count the
turn's kills when the save's own actions replay on top.

## Progression

`CampaignProgress` (in `:core`, like `SaveGame`) keyed by level id, persisted by
`CampaignProgressStore` at `filesDir/campaign_progress.json`. Separate from the autosave,
which is one game in flight and is deleted when it ends.

- Missions unlock in order; the first one of a campaign opens the campaign.
- The Academy is open from the start; the story campaigns open after `academy_shoulder`
  (mission 3) — early enough not to be a wall, late enough that the rules are not a
  surprise.
- Stars: 3 within `parRounds`, 2 within 1.5×, otherwise 1. Best result only.

## Copy

`:core` has no resources, so definitions carry **ids** and never prose;
`ui/campaign/CampaignText.kt` maps them to `@StringRes`. The table is explicit rather than
`getIdentifier` — names resolved by string are invisible to the shrinker and fail silently
at runtime, whereas a missing entry fails `CampaignTextTest`.

Objective lines are **templated** (`objective_build` = "Build %1$d × %2$s"), so ~15 strings
cover all twenty missions. `UiText` arguments may themselves be `UiText`, which is what
lets a template nest a piece name that also has to stay translatable.

## Verification

```bash
./gradlew :core:test              # format, scoring, tracker, scripting, playthroughs
./gradlew :app:testDebugUnitTest  # copy coverage against the shipped JSON
```

| Suite | Gate |
|---|---|
| `CampaignFormatTest` | every level decodes, passes `MapValidator.validateAuthored`, instantiates with invariants intact, names only on-map hexes and existing seats, is not already decided at turn zero, and every objective hex is **reachable** (shares the capital's landmass, or has a shore and the level has boats) |
| `CampaignCodecTest` | every objective / fail-condition / condition variant round-trips — the guard against the `type`-vs-discriminator collision that has bitten this codebase before |
| `ObjectiveTest` | each objective and defeat clause on hand-built micro-states; hold-streak reset; conquest-settles-any-mission; defeat-outranks-victory |
| `CampaignTrackerTest` | sink/loss attribution, beats firing once, a blocked beat staying armed, resumed-save tracker equality |
| `ScriptedActionTest` | the gate is off by default, invalid targets rejected, no RNG touched, undo sealed, save replays bit-identically |
| `CampaignPlaythroughTest` | every mission driven by an AI in the player's chair: terminates within its cap (3× par when `aiSolvable`, 6× par otherwise — non-solvable missions only prove they can't deadlock, and a 3-way AI war can honestly grind past 3× par), invariants hold each turn, `aiSolvable` ones are won, and **every** mission's opening survives at least 8 rounds |
| `CampaignTextTest` (`:app`) | every campaign/level/hint/beat has copy and vice versa; hints only wait on signals the app emits; no beat waits on a UI signal |

### `aiSolvable`, honestly

The AI is an **opponent model**: it plays for territory and knows nothing about
objectives. It therefore finishes missions whose goal aligns with conquest (outlast,
out-earn, wipe out that seat) and reliably ignores one that does not (land on those three
beaches, sink three raiders). Those are flagged `aiSolvable = false`, and the suite still
requires them to terminate (within the looser 6× par cap), to keep their invariants, and
to leave the player standing after the opening — while the static reachability check
proves their targets can be gotten at. What it does *not* prove is that a human can win them; that is what device play is for.

### On device

Screenshots of the campaign list (locked / unlocked / starred), a briefing, the objectives
panel, the coach card with its ring, and both outcome overlays; a kill-and-resume
mid-mission; a story beat firing. Plus the standing gates: `fps=` at target, and **zero
"reconcile corrected" warnings**.

States that take many turns to reach by hand — a mission win, a beat firing — are quicker
to verify from a **fixture autosave**: build the `SaveGame` you want in a throwaway
`:core` test (`SaveGame(turnStartState = …, campaign = CampaignSaveRef(campaignId,
levelId, tracker))`), write it out, then

```bash
adb shell am force-stop com.msa.fightandconquer   # BEFORE writing, or onStop overwrites it
adb push fixture.json /sdcard/fixture.json
adb shell "cat /sdcard/fixture.json | run-as com.msa.fightandconquer sh -c 'cat > files/autosave.json'"
```

and resume through **Continue Game**. Force-stopping first is not optional: a live process
rewrites the autosave from `onStop`, and you will silently test the old state instead.

## Adding a mission

1. Add a level dict to the campaign's `tools/campaign_src/*.py`.
2. `python3 tools/build_campaigns.py <id>`.
3. Add its name/briefing/debrief and every hint and beat string to `strings.xml`, and the
   ids to `CampaignText`.
4. `./gradlew :core:test :app:testDebugUnitTest` — the suites above will tell you if the
   map is broken, the objective is unreachable, the clock is too tight, or the copy is
   missing.
