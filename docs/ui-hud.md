# UI & HUD (`app/.../ui/`)

Single-ViewModel pattern: `GameViewModel` owns all UI state and is the only
**mutator** of `GameEngine` (`GameScreen` reads `engine.state`/`engine.events`
directly to wire the renderer, but never submits); `GameScreen` renders and wires
the board; `MenuScreen` is the front door and `SetupScreen` configures new games.
Colors: `UiColors` — a `@Composable` accessor for `LocalUiColors`, resolving to
the light or dark `UiColorScheme` per the system setting (`UiColors.kt`); the
Material scheme in `theme/Theme.kt` is derived from the same instance. Faction
pastels, `onFaction` and the board-overlay chips are fixed across themes because
they mirror the render palette; only the chrome tokens (paper, ink, panels,
toasts) flip. No dynamic color — wallpaper-derived schemes clashed with the fixed
board palette. System bars are transparent edge-to-edge (`MainActivity` sets
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
| `popups` | `List<CoinPopup>` (1.2 s TTL) | World-anchored floating "+N 🪙" |
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
   pact-icon+`N` → diplomacy panel, "thinking…", ⋯ menu with Field Guide/Diplomacy/
   Resign/Exit)
   + `ProposalStrip` (persistent accept/decline rows for incoming pact offers —
   StateFlow-driven, only for the acting human, never behind the banner) +
   `BottomBar` (InfoCard with a 60 dp baked piece render on a plinth (`iconRes`
   from `PieceIcons`, null for abstract cards) / selected-unit hint (same card
   layout: 40 dp unit render on a plinth + name + "pick a highlighted hex" line,
   via `HudState.selectedUnitIconRes`) / `PurchaseCard`
   tray — 92 dp cards with 44 dp piece renders, desaturated+dimmed when
   unaffordable, coin-icon cost, upkeep & defense lines / Undo / End-Turn FAB that
   morphs in place into "N unmoved · ✕ · End anyway" for 3 s when fresh units
   remain). Flat glyphs are tinted vector drawables (`ic_coin/ic_flag/ic_shield/
   ic_pact`) — no emoji in persistent HUD chrome (toast/popup prose keeps 🪙).
4. One panel at a time in the slot under the TopBar. `ObjectivesPanel` (campaign only;
   mission name, turn counter that turns alert-coloured in the last three rounds, one
   struck-through-when-done line per objective with its `have / need` counter) shows
   whenever the two glanceable panels are closed — a mission's terms should not have to be
   gone looking for. `CoachCardView` sits above the `BottomBar` rather than over the board,
   so a hint never covers the hexes it points at; `HighlightSet.hintFocus` puts a pulsing
   ring on those hexes (`BoardScene.showHighlights` draws it first so a selection reads on
   top). `CampaignOutcomeOverlay` replaces `GameOverOverlay` for a mission: stars, rounds
   or the defeat reason, the debrief, and Next / Retry / Leave.
   `EconomyPanel` (under the TopBar, 264 dp; income rows — hexes, fertile bonus,
   one line per building type, each with a 20 dp piece icon — per-unit-type upkeep
   rows with unit icons, divider, emphasized net + projection, bankruptcy/upkeep-risk
   warning strips) / `DiplomacyPanel` (same slot, mutually exclusive: one row per
   opponent — faction dot, name, tinted status pill (war=alert, pact=positive with
   pact icon + turns left), Propose pact (pact icon) and Tribute buttons, coin-icon
   tribute chips 10/25/50 dimmed when unaffordable, divider-separated rows, and a
   footer stating pact duration + break penalty from `DiplomacyPanelState`).
   Capturing a pact partner's hex needs a second tap (warning toast arms the
   confirmation) — the no-dialog idiom throughout.
5. `ToastStack` (top-center).
6. `TurnBanner` (pass-and-play privacy scrim) / `GameOverOverlay` — topmost, they
   scrim everything below.

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

`SetupScreen` (behind New game): opponents 2–4 seats, mode vs-AI / pass-and-play,
difficulty (Easy/Normal/Hard), map type (Continent/Islands/Archipelago →
`GameSetup.shape` → `MapParams.shape`), map size, fog, and On/Off rows for
special units and diplomacy, wired through `GameSetup` into `RuleConstants`. Choices are
`rememberSaveable` so rotation doesn't reset them. Start game generates the map
off-main and shows the `generating` spinner here.

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
