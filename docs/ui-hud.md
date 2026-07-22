# UI & HUD (`app/.../ui/`)

Single-ViewModel pattern: `GameViewModel` owns all UI state and is the only
**mutator** of `GameEngine` (`GameScreen` reads `engine.state`/`engine.events`
directly to wire the renderer, but never submits); `GameScreen` renders and wires
the board; `MenuScreen` configures new games. Colors: `UiColors` (sRGB mirror of
the render palette); the Material scheme in `theme/Theme.kt` is derived from it
(light-only, no dynamic color — wallpaper-derived schemes clashed with the fixed
board palette).

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
| `screen` | `Menu(hasAutosave, generating) \| Game` | Top-level navigation |
| `hud` | `HudState?` | TopBar/BottomBar (player, coins, net, turn, selection tier, purchases + `ShopInfo`, canUndo, banner seat, winner, `freshUnitCount`) |
| `highlights` | `HighlightSet` | Board discs (selected/moves/captures/merges) |
| `overlayLabels` | `List<OverlayLabel(hex, text, CAPTURABLE\|BLOCKED)>` | Defense chips on frontier hexes while a unit is selected (defense-0 capturable hexes omitted — the disc already says it) |
| `economy` | `EconomyBreakdown?` | Coin-tap panel (null = closed; recomputed on every refresh while open) |
| `toasts` | `List<HudToast>` (max 3, 2.5 s TTL) | Top-center notifications |
| `popups` | `List<CoinPopup>` (1.2 s TTL) | World-anchored floating "+N 🪙" |
| `infoCard` | `InfoCard?` | Bottom card for non-selectable taps (enemy/spent units, buildings, flora, cut-off tiles) — `UiText` + numbers from `RuleConstants`, never hardcoded |
| `cameraJumps` | `SharedFlow<Hex>` | One-shot camera glides |
| `resync` | `StateFlow<Int>` | Board must skip+reconcile (undo/load) |
| `visibility` | `StateFlow<BoardVisibility?>` | Fog sets (`visible` + `explored`) for the viewing seat; null = fog off or game over (fog lifts). During AI turns the perspective stays on the last human seat that played (no pass-and-play leak) |

## Interaction model (`onHexTapped`)

```
banner shown / AI turn / game over → ignore (board taps also close the economy panel)
unit already selected:
    tap on move/capture target  → submit MoveUnit, clear selection
    tap on merge target         → submit MergeUnits, clear selection
    otherwise                   → fall through to select(hex)
select(hex):
    own fresh unit              → select: highlights + defense overlay labels
    own empty usable tile       → purchase selection (tray from engine.buyableAt)
    anything else               → InfoCard (unit > building > flora > starving tile)
                                  fog on: fogged hex → generic "unexplored" card if
                                  explored, nothing if never seen — stats never leak
tap off-board (picker miss)     → cancelSelection (via BoardScene.onTapMiss)
```

`focusNextFreshUnit()` cycles unmoved units (stable id order), selects via the
internal `select()` (never submits), and emits a camera jump.

## Event feedback

A second collector on `engine.events` (ViewModel scope, restarted per engine) drives:
tree-clear popups (human actor only), loot toasts (both sides), "territory cut off"
warning (diffed starving sets, debounced per round), "AI took N of your hexes"
(accumulated during AI turns, flushed at the human's `TurnStarted`), bankruptcy
alert, and `ActionRejected` reasons as info toasts.

## GameScreen layers (root Box, bottom → top)

1. Gesture Box + `FilamentHost`/`BoardScene` (tap → ViewModel; transform gestures →
   rig; wires: events→`apply`, highlights, resync→`skipAnimations`+`apply`,
   cameraJumps→`jumpTo`, labels+popups→`setTrackedAnchors`, visibility→`setFog` —
   also applied at scene creation so fog covers the very first frame).
2. `AnchorOverlay` — **pixel-space, no safeDrawingPadding**: defense chips + coin
   popups positioned with `Modifier.offset` from `BoardScene.anchors`
   (`Float2` → `IntOffset`; placement-phase only).
3. HUD column (safeDrawingPadding): `TopBar` (player chip, clickable coin-icon/net
   area → economy panel, turn, fresh badge `N`+flag icon, pending-proposal badge
   pact-icon+`N` → diplomacy panel, "thinking…", ⋯ menu with Diplomacy/Resign/Exit)
   + `ProposalStrip` (persistent accept/decline rows for incoming pact offers —
   StateFlow-driven, only for the acting human, never behind the banner) +
   `BottomBar` (InfoCard with a 60 dp baked piece render on a plinth (`iconRes`
   from `PieceIcons`, null for abstract cards) / selected-unit hint / `PurchaseCard`
   tray — 92 dp cards with 44 dp piece renders, desaturated+dimmed when
   unaffordable, coin-icon cost, upkeep & defense lines / Undo / End-Turn FAB that
   morphs in place into "N unmoved · ✕ · End anyway" for 3 s when fresh units
   remain). Flat glyphs are tinted vector drawables (`ic_coin/ic_flag/ic_shield/
   ic_pact`) — no emoji in persistent HUD chrome (toast/popup prose keeps 🪙).
4. `EconomyPanel` (under the TopBar, 264 dp; income rows — hexes, fertile bonus,
   one line per building type, each with a 20 dp piece icon — per-unit-type upkeep
   rows with unit icons, divider, emphasized net + projection, bankruptcy/upkeep-risk
   warning strips) / `DiplomacyPanel` (same slot, mutually exclusive: one row per
   opponent with pact status, Propose pact, and Tribute chips 10/25/50). Capturing a
   pact partner's hex needs a second tap (warning toast arms the confirmation) —
   the no-dialog idiom throughout.
5. `ToastStack` (top-center).
6. `TurnBanner` (pass-and-play privacy scrim) / `GameOverOverlay` — topmost, they
   scrim everything below.

Compose children above the AndroidView naturally consume their own touches; only
unhandled ones reach the board — no interop hit-test code exists or should be added.

## Menu & modes

`MenuScreen`: a decorative piece tableau (knight/capital/tower renders on a panel
plinth) under the title, then opponents 2–4 seats, mode vs-AI / pass-and-play,
difficulty (Easy/Normal/Hard), map size, fog, and On/Off rows for special units
and diplomacy (wired through `GameSetup` into `RuleConstants`), Continue when an
autosave exists (New Game is a filled button only when Continue is absent). New
games generate maps off-main (`generating` spinner). Note the layout shifts when
Continue is visible — scripted UI tests must not hardcode chip coordinates.

## AI driving & autosave

`maybeRunAi()` loops while the current seat is AI: `chooseAction` on Default,
`submit` on Main, ~220 ms pacing, autosave at each AI turn end, capped by
`AiPlayer.MAX_ACTIONS_PER_TURN` (500).
Autosave also fires on human `EndTurn` and `Activity.onStop` (`persistNow`);
a finished game deletes the autosave.
