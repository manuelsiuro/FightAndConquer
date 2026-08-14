# UI & HUD (`app/.../ui/`)

Single-ViewModel pattern: `GameViewModel` owns all UI state and is the only
**mutator** of `GameEngine` (`GameScreen` reads `engine.state`/`engine.events`
directly to wire the renderer, but never submits); `GameScreen` renders and wires
the board; `MenuScreen` is the front door and `SetupScreen` configures new games.
Colors: `UiColors` — a `@Composable` accessor for `LocalUiColors`, resolving to
the light or dark `UiColorScheme` per the system setting (`UiColors.kt`); the
Material scheme in `theme/Theme.kt` is derived from the same instance. Faction
pastels, `onFaction` and the board-overlay chips are fixed across themes because
they mirror the render palette; only the chrome tokens (paper, ink, surfaces,
toasts) flip. The legacy translucent `panel` / `toastWarning` tokens survive for
the menu, guide and editor screens only — the in-game HUD is all opaque `surface`
(see the chrome idiom below). No dynamic color — wallpaper-derived schemes clashed
with the fixed board palette. System bars are transparent edge-to-edge (`MainActivity` sets
`SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)` + disables nav-bar contrast
enforcement); the Game screen hides them entirely (immersive, edge-swipe reveals
transiently) via `ImmersiveDuringGame`.

## Strings

**Every user-facing string lives in `res/values/strings.xml`.** Composables use
`stringResource`/`pluralStringResource` directly. The ViewModel can't hold a
`Context`, so it emits **`UiText`** (`UiText.kt`) — a `@StringRes` id plus format
args — which composables resolve with `text.resolve()`. Engine rejections arrive as
`RejectionReason` codes and map to resources via `RejectionReason.toUiText(amount)`
(an exhaustive `when`, so a new code fails to compile until it has a string).
Unit/building names come from `unitNameRes(tier)`.

## GameViewModel — state surface

| Flow | Type | Drives |
|---|---|---|
| `screen` | `Menu(hasAutosave) \| Setup(generating) \| Campaign \| Briefing(campaignId, levelId) \| MapEditor \| Settings \| About \| Game` | Top-level navigation |
| `hud` | `HudState?` | TopBar/BottomBar (player, coins, net, turn, selection tier, purchases + `ShopInfo`, canUndo, banner seat, winner, `freshUnitCount`) |
| `highlights` | `HighlightSet` | Board discs (selected/moves/captures/merges) |
| `overlayLabels` | `List<OverlayLabel(hex, text, CAPTURABLE\|BLOCKED)>` | Defense chips on frontier hexes while a unit is selected (defense-0 capturable hexes omitted — the disc already says it) |
| `economy` | `EconomyBreakdown?` | Coin-tap panel (null = closed; recomputed on every refresh while open) |
| `toasts` | `List<HudToast>` (max 3, 2.5 s TTL) | Top-center notifications |
| `popups` | `List<CoinPopup>` (1.2 s TTL) | World-anchored floating "+N" coin pills |
| `infoCard` | `InfoCard?` | Bottom card for non-selectable taps (enemy/spent units, buildings, flora, cut-off tiles) — `UiText` + numbers from `RuleConstants`, never hardcoded |
| `cameraJumps` | `SharedFlow<Hex>` | One-shot camera glides |
| `resync` | `StateFlow<Int>` | Board must skip+reconcile (undo/load) |
| `campaignRun` | `CampaignRunState?` | Mission HUD: level name, objective lines, coach card, turn limit, outcome. **Null in a skirmish**, which is how every pre-existing HUD path stays untouched |
| `visibility` | `StateFlow<BoardVisibility?>` | Fog sets (`visible` + `explored`) for the viewing seat; null = fog off or game over (fog lifts). During AI turns the perspective stays on the last human seat that played (no pass-and-play leak) |

## Interaction model (`onHexTapped`)

```
banner shown / AI turn / game over → ignore (board taps also close the economy panel)
unit already selected:
    tap on move/capture target  → submit MoveUnit, clear selection
    tap on merge target         → submit MergeUnits, clear selection
    otherwise                   → fall through to select(hex)
unit selected is a transport:
    tap adjacent land           → submit Disembark (engine-checked)
unit selected is a warship:
    tap raid target             → submit Bombard
select(hex):
    own fresh unit              → select: highlights + defense overlay labels
                                  (own units are selectable even on unowned sea —
                                  boats sit on hexes they don't own; embark targets
                                  highlight like moves)
    own empty usable tile       → purchase selection (tray from engine.buyableAt)
    bare sea hex with buyables  → purchase selection too (bridge/boat tray on water)
    anything else               → InfoCard (unit > building > flora > deposit >
                                  sea > starving tile)
                                  own pieces carry action buttons: Rotate +
                                  Destroy on an own bridge, Destroy on any own
                                  non-capital building, Disband on an own spent
                                  unit (performInfoAction; refunds shown inline)
                                  fog on: fogged hex → generic "unexplored" card if
                                  explored, nothing if never seen — stats never leak
tap off-board (picker miss)     → cancelSelection (via BoardScene.onTapMiss)
```

In a campaign the same `select()` also raises the `unitSelected` teaching signal, which is
how a coach step can wait on "pick up a soldier" (see `ui.UiSignals`).

`focusNextFreshUnit()` cycles unmoved units (stable id order), selects via the
internal `select()` (never submits), and emits a camera jump.

## Event feedback

A second collector on `engine.events` (ViewModel scope, restarted per engine) drives:
tree-clear and demolish/disband refund popups (human actor only), loot toasts (both
sides), "territory cut off" warning (diffed starving sets, debounced per round),
"AI took N of your hexes" (accumulated during AI turns, flushed at the human's
`TurnStarted`), bankruptcy alert, and `ActionRejected` reasons as info toasts.

The selected-unit strip additionally hosts a "Disband +N" button for the held
fresh unit (`HudState.selectedUnitDisbandRefund` → `disbandSelectedUnit()`); all
destroy paths rely on the ordinary Undo button rather than a confirm dialog.

## GameScreen layers (root Box, bottom → top)

1. Gesture Box + `FilamentHost`/`BoardScene` (tap → ViewModel; transform gestures →
   rig; wires: events→`apply`, highlights, resync→`skipAnimations`+`apply`,
   cameraJumps→`jumpTo`, labels+popups→`setTrackedAnchors`, visibility→`setFog` —
   also applied at scene creation so fog covers the very first frame).
2. `AnchorOverlay` — **pixel-space, no safeDrawingPadding**: defense chips + coin
   popups positioned with `Modifier.offset` from `BoardScene.anchors`
   (`Float2` → `IntOffset`; placement-phase only).
3. HUD column (safeDrawingPadding). **Chrome idiom** (the Game-Screen restyle of the
   Setup 1a language — full spec in
   [design/game-screen-hud-handoff.md](design/game-screen-hud-handoff.md)): every
   surface is opaque `UiColors.surface` + 1 dp `hairline` + the single `boardLift`
   shadow (`Modifier.hudSurface` in `ui/game/HudMetrics.kt`); one three-step tint
   ladder (12 % glyph wash / 30 % pills, badges, warning strips / 100 % solid fills);
   one plinth scale (`PlinthScale` S 40/32 · M 56/48 · L 96/80 = controlFill box +
   hairline behind every baked render); press feedback is 0.96 scale + ripple
   (`scaleClickable`). No translucent panels, no ad-hoc ink alphas, no emoji anywhere
   (tinted vectors `ic_coin/ic_flag/ic_shield/ic_pact` only).
   `TopBar` (full-width, content-sized: faction disc, seat label over "Civ · Turn N",
   coin block → economy panel, then two 48 dp controlFill circles — Diplomacy with a
   coin-gold pending-proposal badge, and the ⋮ menu with Field Guide / Objectives
   (campaign) / two-tap-armed Resign / Exit; either circle flips to filled-ink while
   its surface is open; second row hosts the fresh-units pill — pastel @30 % in light,
   solid pastel in dark — or "thinking…")
   + `ProposalStrip` (persistent accept/decline rows for incoming pact offers —
   StateFlow-driven, only for the acting human, never behind the banner; outlined
   Decline + filled-ink Accept) +
   `BottomBar` (selected-unit strip at plinth S with Disband / `InfoCard` at plinth M
   with a divider before its outlined-primary + controlFill-secondary action row /
   "RECRUIT" surface-chip header over the `PurchaseCard` tray — fixed 128 dp cards,
   plinth-M render, 28 dp info glyph in a 48 dp target; unaffordable = still tappable
   (engine rejection toasts), render 38 % + grayscale, `inactiveGlyph` text, rust cost /
   44 dp outlined Undo / 56 dp radius-20 "End·TURN" FAB in the current player's pastel.
   With fresh units the FAB arms instead of ending: a full-width armed surface appears
   below — micro-label "N UNITS UNMOVED" + "Tap again to end", 48 dp ✕, rust
   "End anyway" — and disarms after 3 s or on ✕; FAB-again or End-anyway commits).
4. One panel at a time in the slot hanging off the TopBar's **measured** bottom + 8 dp
   (published from `onGloballyPositioned` in `GameScreen` — never a height constant)
   and right-aligned at the 12 dp gutter. `ObjectivesPanel` (campaign only; mission
   name, turn counter that turns alert-coloured in the last three rounds, 18 dp check
   circles — filled positive when done, `inactiveGlyph` ring while pending — with
   struck-through done lines and `have / need` counters) shows whenever the two
   glanceable panels are closed — a mission's terms should not have to be gone looking
   for; the ⋮ menu's Objectives entry just closes the other panels
   (`showObjectivesPanel()`). `CoachCardView` — the HUD's only solid-pastel surface
   ("HINT" micro-label, sage fill) — sits above the `BottomBar` rather than over the
   board, so a hint never covers the hexes it points at; `HighlightSet.hintFocus` puts
   a pulsing ring on those hexes (`BoardScene.showHighlights` draws it first so a
   selection reads on top).
   All three occupants of the slot share one chrome — `HudSidePanel` (264 dp) with
   `PanelHeader` micro-label + divider headers and `seatLabel()` for the
   "Player N"/"AI N" wording (`ui/game/HudMetrics.kt`).
   `EconomyPanel` (income/upkeep rows with an 18 dp tinted icon slot — positive @30 %
   for income, rust @30 % for cost — then a controlFill emphasis block: "Net per turn"
   + "Treasury next turn" projection, and radius-10 warning strips: coin-gold @30 %
   upkeep risk, rust @30 % bankruptcy) / `DiplomacyPanel` (one row per opponent —
   faction disc, name, status pill at exactly one 30 % tint: rust war · positive pact ·
   coin-gold incoming · controlFill sent — 40 dp outlined Propose/Tribute, controlFill
   tribute chips 10/25/50 disabled at 38 % when unaffordable, and a footer stating pact
   duration + break penalty from `DiplomacyPanelState`).
   Capturing a pact partner's hex needs a second tap (warning toast arms the
   confirmation) — the no-dialog idiom throughout.
5. `ToastStack` (top-center, anchored below the measured top chrome): one 13 sp ink
   text style for all kinds; warning/alert differ only by a 30 % coin-gold/rust wash.
6. Full-screen overlays — topmost, `bg` @92 % scrim with a single centered radius-20
   card (`OverlayScrim` in `Banners.kt`), 72 dp hero discs, plinth-L renders, 52 dp
   `OverlayButton`s: `TurnBanner` (pass-and-play privacy; whole screen is the tap
   target) / `GameOverOverlay` / `CampaignOutcomeOverlay` (mission micro-label, stars
   in coin gold vs `progressTrack`, debrief, stacked Next / Retry / Leave).

Compose children above the AndroidView naturally consume their own touches; only
unhandled ones reach the board — no interop hit-test code exists or should be added.

## Screens & navigation

Navigation is a hand-rolled sealed `Screen` on `GameViewModel` switched in a `when`
in `MainActivity` — no Navigation Compose, no back stack. `backToMenu()` is the
single "back" target for every non-game screen; it recomputes `hasAutosave` and
cancels any in-flight map generation, so backing out mid-generation returns to the
menu instead of racing into the game.

`MenuScreen`: a decorative piece tableau (knight/capital/tower renders on a panel
plinth) under the title, then a button list — Continue Game (only when an autosave
exists), New game, Campaign, Map Editor, Guide, Settings, About. Whichever of
Continue/New game comes first is the filled button; the rest are outlined. Guide
opens the `FieldGuide` overlay in place rather than navigating. **Note the layout
shifts when Continue is visible — scripted UI tests must not hardcode coordinates;
derive them from `uiautomator dump`.**

`SetupScreen` (behind New game, `ui/setup/` — the quick-start card design): a
tableau card summarizing the match (human civ's piece trio + one-line summary +
seat dots), enemy-count cards, mode/difficulty columns (difficulty collapses in
pass-and-play), per-seat civilization cards with pastel caps that open a
`ModalBottomSheet` picker (tap-to-apply, "?" deep-links the Field Guide civ
entry), and one "World & rules" disclosure folding map size/type (Canvas hex
clusters), fog, special units and diplomacy behind a live summary — wired through
`GameSetup` into `RuleConstants`. A Generated / My-maps toggle (only when authored
maps exist) swaps the form for the custom-map list: `MinimapRenderer` thumbnails,
seats·hexes meta, drafts dimmed with their violation count and never startable.
Start sits in a sticky bottom bar under a scrim; in custom mode it reads
"Play {map}". All choices are `rememberSaveable` (hoisted above the
`AnimatedContent` that cross-fades form ↔ generating pane) so rotation and the
generating round-trip reset nothing; the generating pane's Cancel calls
`GameViewModel.cancelGeneration()`, which abandons the job and restores the form
as-is.

`AboutScreen`: static content — identity and version (`BuildConfig.VERSION_NAME` /
`VERSION_CODE`, which is why `buildFeatures { buildConfig = true }` is on), what the
game is, credits, links, and bundled open-source licenses. Links go through
`LocalUriHandler` wrapped in `runCatching` (it rethrows `ActivityNotFoundException`
as `IllegalArgumentException`) and fall back to a toast.

`CampaignScreen` (behind Campaign): a row of campaign chips over the selected campaign's
mission list — number badge, name, best-rounds or lock reason, star row. `BriefingScreen`
(`Screen.Briefing`) is the pre-mission card: story, objectives read from the level's own
opening position through the same `Objectives.evaluate` the HUD uses (so it cannot drift),
defeat clauses, "new in this mission" chips that open the `FieldGuide` at the right entry,
and Begin/Play-again. Both are described in [campaign.md](campaign.md).

`PlaceholderScreen`: shared "Coming soon" screen, currently backing Map
Editor and Settings. Replacing one means swapping a single `when` branch in
`MainActivity` — the `Screen` case and its `openX()` method already exist.

`FieldGuide` (`ui/guide/`) is **not** a `Screen` — it is a self-contained overlay
driven by `GuideCatalog`, and hosts just hoist a boolean and render it on top. Both
`MenuScreen` (Guide button) and `GameScreen` (⋯ menu, and purchase cards passing
`focusEntryId` to scroll straight to one entry) do exactly that. It owns its own
`BackHandler`, so system back closes the guide without touching host navigation.

## AI driving & autosave

`maybeRunAi()` loops while the current seat is AI: `chooseAction` on Default,
`submit` on Main, ~220 ms pacing, autosave at each AI turn end, capped by
`AiPlayer.MAX_ACTIONS_PER_TURN` (500).
Autosave also fires on human `EndTurn` and `Activity.onStop` (`persistNow`);
a finished game deletes the autosave.
