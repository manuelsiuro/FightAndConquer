# UI & HUD (`app/.../ui/`)

Single-ViewModel pattern: `GameViewModel` owns all UI state and is the only
**mutator** of `GameEngine` (`GameScreen` reads `engine.state` directly to wire
highlights, resync, camera jumps and fog, and registers its `BoardScene` as the
ViewModel's `BoardPlayback` via `attachBoard`/`detachBoard`, but never submits and
never collects `engine.events` for the board — the ViewModel feeds it);
`GameScreen` renders and wires the board; `MenuScreen` is the front door and
`SetupScreen` configures new games.
Colors: `UiColors` — a `@Composable` accessor for `LocalUiColors`, resolving to
the light or dark `UiColorScheme` per the system setting (`UiColors.kt`); the
Material scheme in `theme/Theme.kt` is derived from the same instance. Faction
pastels, `onFaction` and the board-overlay chips are fixed across themes because
they mirror the render palette; only the chrome tokens (paper, ink, surfaces,
toasts) flip. The legacy translucent `panel` / `toastWarning` tokens survive for
the guide, campaign/about/debrief and editor screens only — the in-game HUD and,
since it grew a 3D backdrop, the menu are all opaque `surface` (see the chrome
idiom below). No dynamic color — wallpaper-derived schemes clashed
with the fixed board palette. System bars are transparent edge-to-edge (`MainActivity` sets
`SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)` + disables nav-bar contrast
enforcement); the Game screen hides them entirely (immersive, edge-swipe reveals
transiently) via `ImmersiveDuringGame`.

## Typography

Two bundled faces, both SIL OFL 1.1 (`theme/Type.kt`):

- **Figtree** (`UiFontFamily`), everything by default. It is set on every Material
  `Typography` style, and `Text` inherits it through `LocalTextStyle` (`bodyLarge`), so call
  sites only pick size and weight. A `TextStyle` built from scratch (a `TextMeasurer` label,
  as in `TimelineChart`) skips that chain and must name `UiFontFamily` itself.
- **Fraunces** (`DisplayFontFamily`), screen and overlay titles at **22 sp and up** only: the
  menu title, turn banner, outcome and victory headlines, and the screen headers (setup,
  campaign, briefing, maps, guide, about). Bold or ExtraBold. Add `fontFamily =
  DisplayFontFamily` to a new title in that range. HUD surfaces never use it.

The TTFs in `res/font/` are static instances baked by `tools/bake_fonts.py` from the upstream
variable fonts in `art/fonts/` (license texts beside them). Fraunces is pinned at SOFT 100,
WONK 0 and opsz 28. Edit the script and re-bake, never the TTFs. Glyphs the faces lack
(`⋮`, `✕`, arrows) fall back to the system font per character. Outside Compose, the
shared map image's caption (`MinimapRenderer`, a plain Canvas) gets Figtree SemiBold from
`MapShareManager` via `Resources.getFont`. The About screen credits
both faces.

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
| `menuWorld` | `GameState?` | The menu's decorative orbiting world; null while it generates (or if generation failed), renewed by `enterMenu` on every menu entry |
| `hud` | `HudState?` | TopBar/BottomBar (player, coins, net, turn, selection name + Atk/Def/upkeep/cargo-attack stats, purchases + `ShopInfo` + `purchaseCategory` (the open half of the Recruit / Build pair, null = pair only), canUndo, banner seat, winner, `freshUnitCount`). `currentPlayer` / `currentIsHuman` / `currentCiv` / `aiThinking` / `winner`(+`winnerIsHuman`) follow the **presented** seat, not the engine's: `presentedTurn(state, presentedAiSeat)` (`PresentedTurn.kt`) keeps an AI seat current while its beats still play, so the human's chrome — and an AI victory's Game Over overlay — appear only on a still board |
| `highlights` | `HighlightSet` | Board discs (selected/moves/captures/merges) |
| `overlayLabels` | `List<OverlayLabel(hex, value, CAPTURABLE\|BLOCKED\|ATTACKER, SHIELD\|SWORD, cd)>` | While a unit is selected: defense chips on frontier hexes (attacker-aware — a catapult's numbers ignore buildings; defense-0 capturable hexes omitted — the disc already says it; a land unit holding an enemy BRIDGE reads as ordinary hex defense, never a duel), sword chips on warship duels (green sinkable / red out-gunning hulls, showing ship strength), bombard-raid shield chips (green legal / red `DEFENSE_TOO_HIGH`), shield chips on a loaded transport's hostile landings (the hex's defense — the cargo's attack rides the badge), and — whenever any chip shows — a dark sword badge with the attacker's (or its cargo's) value on the selected hex (never on a fishing dory: a hull that cannot attack has nothing to compare, and the badge would occlude the parked-catch coin chip on its own hex). The naval discs and their chips come from one `navalExtras` scan so the two renderings cannot drift |
| `economy` | `EconomyBreakdown?` | Economy bottom sheet (null = closed; recomputed on every refresh while open) |
| `research` | `ResearchPanelState?` | Research bottom sheet (null = closed; recomputed on every refresh while open). Built by the pure `buildResearchPanel(state, seat)` — branch lanes of tech nodes (done / in-progress / available / busy / locked; BUSY = prerequisite met but the single slot is occupied, LOCKED = the tier below is missing), the working-University count and rate. `HudState` adds `researchAvailable` / `diplomacyAvailable` (rules flags — levels without a system must not grow a dead action-bar button) and `researchBadge` (a working University with no active research) |
| `objectivesOpen` | `StateFlow<Boolean>` | Mission objectives bottom sheet (campaign only, on-demand from its action-bar circle — objectives left the always-on side slot when the panels became sheets) |
| `stats` | `GameStatsState?` | War-report bottom sheet (null = closed; recomputed on every refresh while open). Built by the pure `buildGameStats(record, state, viewer)` over the live match recorder — the viewer's own series/totals/moments only, never an enemy's (fog- and hot-seat-safe by construction) |
| `toasts` | `List<HudToast>` (max 3, 2.5 s TTL) | Top-center notifications |
| `popups` | `List<CoinPopup>` (1.2 s TTL) | World-anchored floating "+N" coin pills |
| `infoCard` | `InfoCard?` | Bottom card for non-selectable taps (enemy/spent units, buildings, flora, deposits, bare enemy ground, cut-off tiles) — `UiText` + numbers from the tapped piece's owner-effective rules, never hardcoded. Units carry an Atk/Def pair (sword/shield `InfoStat.iconRes` glyphs); every enemy-owned hex adds "To capture — Atk N+" (`Rules.captureRequirement` on land, the defender's `unitDefenseOf` at sea) and, when outside cover raises the hex above the tapped piece itself, "Guarded by <Tower/Baron/…>" via `Rules.defenseSourceOf`. In day-night games an own unlit defense building offers `InfoCardAction.LightBeacon` (single-tap — in-turn Undo covers it, the StartResearch precedent; the card stays open so the "Beacon — Lit" stat is seen landing) |
| `cameraJumps` | `SharedFlow<Hex>` | One-shot camera glides |
| `resync` | `StateFlow<Int>` | Board must skip+reconcile (undo/load) |
| `campaignRun` | `CampaignRunState?` | Mission HUD: level name, objective lines, coach card, turn limit, outcome. **Null in a skirmish**, which is how every pre-existing HUD path stays untouched |
| `visibility` | `StateFlow<BoardVisibility?>` | Fog sets (`visible` + `explored`) for the viewing seat; null = fog off or game over (fog lifts). During AI turns the perspective stays on the last human seat that played (no pass-and-play leak) |

## Interaction model (`onHexTapped`)

```
banner shown / AI turn / game over → ignore (board taps also close the glanceable
                                     sheets). "AI turn" lasts until the AI's beats
                                     have played: the gate is the presented seat, so
                                     taps stay ignored through the board's tail
                                     (`BoardScene.tap` still fast-forwards the beats
                                     already fed before the tap reaches the ViewModel)
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
    own empty usable tile       → purchase selection: the Recruit / Build pair only
                                  (PurchaseMenu.shown — nothing buyable, nothing shown);
                                  a button tap (togglePurchaseCategory) opens that
                                  category's cards from engine.buyableAt, tapping the
                                  active button folds them again; a half with nothing to
                                  sell is dimmed and inert
    bare sea hex with buyables  → purchase selection too — the same pair on water
                                  (boats under Recruit, bridges under Build)
    own University, no unit     → research sheet (BuildingTap.targetOf →
                                  openResearchPanel; needs researchEnabled — a
                                  starving University still opens the sheet)
    own Capital, no unit        → economy sheet (BuildingTap.targetOf →
                                  openEconomyPanel)
    anything else               → InfoCard (unit > building > flora > deposit >
                                  sea > starving tile)
                                  own pieces carry action buttons: Rotate +
                                  Destroy on an own bridge, Destroy on any own
                                  non-capital building (performInfoAction;
                                  refunds shown inline)
                                  fog on: fogged hex → generic "unexplored" card if
                                  explored, nothing if never seen — stats never leak
tap off-board (picker miss)     → cancelSelection (via BoardScene.onTapMiss)
```

The two building taps are one pure rule, `BuildingTap.targetOf(state, hex, seat)` in
`ui/game/BuildingTap.kt` (JVM-tested in `BuildingTapTest`): own Capital → `ECONOMY`, own
University with research enabled → `RESEARCH`, `null` for a missing tile, another seat's
tile, a hex with a unit on it, or any other building. It is consulted inside `select()`,
so a held unit acts first — tapping your own Capital with a soldier in hand moves or
merges onto it when it is in reach, and only falls through to the economy sheet when it
is not.

In a campaign the same `select()` also raises the `unitSelected` teaching signal, which is
how a coach step can wait on "pick up a soldier" (see `ui.UiSignals`).

`focusNextFreshUnit()` cycles unmoved units (stable id order), selects via the
internal `select()` (never submits), and emits a camera jump.

Device fixtures for the purchase menu come from `PurchaseMenuFixturesTest`
(`FC_FIXTURES_OUT=<dir> ./gradlew :core:test --tests '*PurchaseMenuFixturesTest'` writes a
both / recruit-only / build-only autosave; push one as `files/autosave.json` with the app
force-stopped, then tap Continue). `PanelTapFixturesTest` writes the same way for the
building taps and the research sheet: `panels` (Universities and spent peasants around a
capital, one tech done, an idle research slot) and `panels_busy` (the same board with a
research in progress).

## Event feedback

The ViewModel's collector on `engine.events` (ViewModel scope, restarted per engine —
the only one left, the board is fed synchronously by `submit`) drives:
tree-clear and demolish/disband refund popups (human actor only), loot toasts (both
sides), "territory cut off" warning (diffed starving sets, debounced per round),
"AI took N of your hexes" (accumulated during AI turns, flushed at the human's
`TurnStarted`), bankruptcy alert, and `ActionRejected` reasons as info toasts.

The toasts raised on the human's `TurnStarted` ("AI took N of your hexes", "night
approaching") go through a local `announce()`: while an AI seat is still presented they
are parked in `handoffToasts` and flushed at the handoff (`onAiTurnsDone`), so the player
reads them when the board is still and the turn is actually his — not over the AI's tail.

The selected-unit strip additionally hosts a "Disband +N" button for the held
fresh unit (`HudState.selectedUnitDisbandRefund` → `disbandSelectedUnit()`); all
destroy paths rely on the ordinary Undo button rather than a confirm dialog.
Spent units cannot disband — the engine refuses `UNIT_ALREADY_ACTED` — so the
InfoCard carries no Disband action: tapping an own spent unit shows its name,
"Already moved this turn" and its stats, nothing to press.

## GameScreen layers (root Box, bottom → top)

1. Gesture Box + `FilamentHost`/`BoardScene` (tap → ViewModel; transform gestures →
   rig; wires: `attachBoard`/`detachBoard` (the ViewModel's `BoardPlayback` — beats
   arrive from `submit`, not from a collector), highlights,
   resync→`skipAnimations`+`apply`,
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
   (tinted vectors `ic_coin/ic_flag/ic_shield/ic_sword/ic_pact`, the twelve
   `ic_tech_*` research glyphs and `ic_end_turn` only).
   `TopBar` (full-width, content-sized: faction disc, seat label over "Civ · Turn N",
   display-only coin block, and one 48 dp controlFill circle — the ⋮ menu with Field
   Guide / two-tap-armed Resign / Exit; the circle flips to
   filled-ink while the menu is open; second row shows "thinking…" during AI turns)
   + `ActionBar` (`ui/game/ActionBar.kt` — up to six standalone floating 48 dp circles
   at the left gutter, 8 dp apart, each full `hudSurface` chrome: Diplomacy (coin-gold
   pending-proposal dot, hidden when the rules disable diplomacy —
   `HudState.diplomacyAvailable`) · Research (idle-research dot, hidden when
   `!researchAvailable`) · Economy · War report (`ic_chart`) · Objectives
   (`ic_target`, campaign only) · jump-to-fresh-unit
   (filled-ink count badge; 38 % disabled treatment at zero — slot-stable). Panel
   buttons flip to filled-ink while their sheet is open; the whole bar hides for AI
   turns, the privacy banner, and after a winner. It lives inside the measured top-chrome column, so panels and
   toasts re-anchor below it for free)
   + `ProposalStrip` (persistent accept/decline rows for incoming pact offers —
   StateFlow-driven, only for the acting human, never behind the banner; outlined
   Decline + filled-ink Accept) +
   `BottomBar` (selected-unit strip at plinth S — name over a 12 sp `inkMuted`
   sword-Atk · shield-Def · upkeep stats line (a loaded transport shows its cargo's
   attack, an empty one an em-dash) — with Disband / `InfoCard` at plinth M
   with a divider before its outlined-primary + controlFill-secondary action row /
   the **purchase menu** (`PurchaseMenuView`): two 48 dp radius-16 `hudSurface`
   buttons — Recruit (`ic_swords`) / Build (`ic_build`), filled ink while open,
   38 % + `inactiveGlyph` when that half has nothing to sell — over the filtered
   `PurchaseCard` row (cards only while a category is open; every rule in the pure
   `ui/game/PurchaseMenu.kt`, JVM-tested in `PurchaseMenuTest`) — fixed 128 dp cards
   (unit cards fold upkeep beside the cost and spend the third line on an 11 sp
   sword-Atk · shield-Def row from `PurchaseOption.Unit.strength/defense`, civ-correct
   at offer time; structures keep the income/defense micro-label),
   plinth-M render, 28 dp info glyph in a 48 dp target; unaffordable = still tappable
   (engine rejection toasts), render 38 % + grayscale, `inactiveGlyph` text, rust cost /
   44 dp outlined Undo / 56 dp radius-20 FAB (20 dp `ic_end_turn` glyph over "End",
   12 sp/800, both `onFaction`) in the current player's pastel.
   With fresh units the FAB arms instead of ending: a full-width armed surface appears
   below — micro-label "N UNITS UNMOVED" + "Tap again to end", 48 dp ✕, rust
   "End anyway" — and disarms after 3 s or on ✕; FAB-again or End-anyway commits).
4. One glanceable surface at a time, as a **bottom sheet** — `HudBottomSheet`
   (`ui/game/HudBottomSheet.kt`), deliberately in-composition rather than
   material3's `ModalBottomSheet`: that one opens its own window, which escapes
   `ImmersiveDuringGame` (system bars would pop back over the board) and stacks
   above every in-game overlay. Chrome: opaque `surface`, top-only 28 dp corners
   via the `hudSurface(Shape)` overload, `UiColors.sheetScrim` behind (hoisted
   from the Setup civ picker), a 34×4 dp hairline drag handle in a 48 dp zone.
   Dismissal: scrim tap, system Back, or dragging the handle past 30 % of the
   sheet height (drag lives on the handle only, so it never fights the content's
   scroll); every path funnels into `GameViewModel.closePanels()`, and board taps
   keep nulling the flows as belt-and-braces. Height caps at 60 % of the window
   (content scrolls inside; an optional pinned footer slot stays visible); width
   caps at 560 dp for landscape/tablets. Content survives the slide-out via
   `rememberRetained` — the ViewModel flows null on dismiss, but the sheet still
   needs something to draw while animating. The ViewModel keeps all five sheets
   mutually exclusive (each toggle closes the rest). Shared idioms: `PanelHeader`
   micro-label + divider headers and `seatLabel()` for the "Player N"/"AI N"
   wording (`ui/game/HudMetrics.kt` — the old 264 dp `HudSidePanel` is gone).
   `CoachCardView` — the HUD's only solid-pastel surface ("HINT" micro-label,
   sage fill) — sits above the `BottomBar` rather than over the board, so a hint
   never covers the hexes it points at; `HighlightSet.hintFocus` puts a pulsing
   ring on those hexes (`BoardScene.showHighlights` draws it first so a selection
   reads on top).
   The occupants:
   `EconomySheetContent` (income and upkeep as two side-by-side columns — the
   sheet's width is what freed them from stacking — rows with an 18 dp tinted icon
   slot: positive @30 % for income, rust @30 % for cost; the pinned footer is the
   controlFill emphasis block "Net per turn" + "Treasury next turn" and the
   radius-10 warning strips: coin-gold @30 % upkeep risk, rust @30 % bankruptcy).
   `DiplomacySheetContent` (one line per opponent — faction disc, name, status
   pill at exactly one 30 % tint: rust war · positive pact · coin-gold incoming ·
   controlFill sent — with 40 dp outlined Propose/Tribute inline on the same
   line; controlFill tribute chips 10/25/50 disabled at 38 % when unaffordable,
   and a footer stating pact duration + break penalty).
   `ObjectivesSheetContent` (campaign only, on-demand from its action-bar
   circle — `toggleObjectivesPanel()`; mission name, turn counter that
   turns alert-coloured in the last three rounds, 18 dp check circles with
   struck-through done lines and `have / need` counters).
   `ResearchSheetBody` (one lane per branch — War/Coin/Stone/Sail, Sail absent
   entirely when naval rules are off — of three 108 dp tech cards flowing left
   to right with 12×2 dp connectors: `positive` once the tier before is done,
   `divider` otherwise, so the linearity is the reading direction; each card
   carries name (2 lines), effect (2 lines), status glyph, and an always-visible
   cost + "NT" duration — dimmed `inactiveGlyph` on LOCKED and BUSY cards, since
   a locked tree must not scream about money; the in-progress card wears a
   faction border + 3 dp progress bar; lane headers count "n/3". Every card leads
   its name row with a 28 dp `TechTile` — the tech's own glyph
   (`techIconRes` in `UiText.kt` → one of twelve `ic_tech_*` vectors, drawn at 16 dp)
   on a rounded `size * 2 / 7` square whose wash reads the status: ink 12 % with
   an ink glyph when the tech is available, the same 12 % with an `inactiveGlyph`
   glyph when LOCKED or BUSY, the faction pastel at 30 % with an ink glyph while
   it is in progress, `positive` at 30 % with a `positive` glyph once it is done.
   A lane whose three techs are all done turns its header square `positive` @30 %
   too. The pinned `ResearchSheetFooter` carries the active research's progress
   track — its row led by that tech's tile — the per-turn rate, or the "build a
   University" nudge, which shows the civ-correct University render on a plinth S
   (`PiecePlinth` + `PlinthScale.S`, civ from `ResearchPanelState.civ`) beside its
   text). Locked, busy and
   unaffordable cards stay tappable: the engine's rejection toast explains
   itself, so the sheet carries no second rules implementation — the
   PurchaseCard contract. Starting research is single-tap; in-turn Undo covers a
   mis-tap, and the armed pattern stays reserved for irreversible acts. Its
   entry point is the action bar's Research circle (a third 48 dp circle *inside*
   the top bar was measured out: ~286 dp of fixed bar content on a 360 dp portrait
   screen would crush the seat-identity column — the floating row below the bar
   sidesteps that), with the idle-research badge dot on that circle. AI
   research stays private until the Chronicle; a human completion shows one
   toast, and research-gated structures ride the purchase menu's Build half as locked cards
   ("REQUIRES <TECH>", desaturated plinth, inactiveGlyph cost — a lock is
   structural, not poverty, so it never wears the alert color). Muster-locked
   units wear the identical treatment naming the missing hall
   (`PurchaseOption.Unit.lockedByBuilding` — "BARRACKS" where the upkeep
   micro-label would sit), including the tier-1 buy-merge card when merging
   would create a hall-less tier.
   `GameStatsSheet` (the war report, from the action bar's chart circle: the
   viewer's own chronicle graphed live off the match recorder — a lens-switchable
   `TimelineChart` (Territory filled / Income-vs-Upkeep / Treasury / Army, the
   Army lens hidden while a resumed pre-strength record has no samples), a NOW
   strip with this turn's live numbers (the chart only knows turn-start samples;
   no synthetic "current" point, which would re-trigger the draw-in on every
   buy), the running record totals, and the turning points the viewer acted in or
   suffered — suffered ones on a 12 % alert wash. Own faction only by design:
   enemy series stay hidden information under fog and hot-seat, and comparisons
   belong to the post-match debrief).
   Capturing a pact partner's hex needs a second tap (warning toast arms the
   confirmation) — the no-dialog idiom throughout. (A modal container is fine;
   the rule bans *confirmation* dialogs.)
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

`MenuScreen`: floating chrome over a slowly orbiting 3D world. The backdrop is a
`FilamentHost` filling the whole screen behind the chrome, running a `BoardScene` built
from `GameViewModel.menuWorld` — a throwaway `GameState` nobody plays, made by
`MenuWorld` (`ui/menu/MenuWorld.kt`: MEDIUM map, always a continent — the generator
leaves open ocean untiled, so island shapes read as a near-empty backdrop — 2–4 AI
seats with distinct civilizations, default rules) on `Dispatchers.Default`. Every entry
to the menu goes through the one funnel `GameViewModel.enterMenu(hasAutosave)` (init,
autosave-load failure, `backToMenu`, game exit): it nulls the world so the previous host
leaves composition, cancels the in-flight job, takes a fresh seed from
`MenuWorldSeeds.next(nowMillis, previous)` (the clock mixed through SplitMix64, never
equal to the previous one) and regenerates — a different world every single time the
menu is shown. Generation failure is swallowed: the world stays null and the chrome sits
on the plain `background`. The camera orbits one turn per minute
(`MENU_ORBIT_RAD_PER_SEC`) with `fitForOrbit` and a margin below 1, so the world
overflows the screen sides on purpose and fills the width, and `orbitTargetLiftFraction`
lifts its look-at target so the land mass rides in the free band between the title chip and
the tiles rather than behind them (see [rendering.md](rendering.md) "Frame pacing").
Decorative: no gesture modifiers, and the scene is never held in Compose state.

Over it, HUD chrome rather than translucent panels, pinned to the screen's two edges so the
middle band stays world: the title + subtitle `hudSurface` chip sits `TopCenter` at the
HUD's own `TopBarTopInset` (16 dp under the safe area) and the action block `BottomCenter`,
12 dp above it. The block is six 88 dp **square icon tiles**, three per row with 8 dp gaps
(280 dp per row) — New game · Campaign · Map Editor / Guide · Settings · About, always
those six in that order — each an opaque `surface` + hairline + the single `boardLift` at
the menu's shared 16 dp radius, `scaleClickable` for the HUD's press feedback, showing a
24 dp `inkMuted` glyph over a 12 sp/600 label. Only when an autosave exists a 280 × 48 dp
pastel **Continue bar** (20 dp glyph beside a 14 sp label) sits above the grid; the grid
itself never changes shape. Exactly one surface is the pastel primary — `faction(0)` fill,
the end-turn FAB's darker hairline, `onFaction` content, never `ink` on a pastel — the
Continue bar when it exists, otherwise the New game tile. The glyphs are the tintable
`ic_play` / `ic_swords` / `ic_banner` / `ic_hex_pencil` / `ic_gear` / `ic_info` vectors,
with Guide reusing the open book `ic_research`. Guide opens the `FieldGuide` overlay in
place rather than navigating.

Every menu decision — which entries, their order, which one is primary, how they chunk into
rows, the dp geometry, when to anchor and how far to lift the world — lives in the pure
`ui/menu/MenuLayout.kt` (`enum class MenuEntry(labelRes, iconRes)` plus `object MenuLayout`:
`continueBar`, `tiles`, `primary`, `rows`, `rowWidthDp`, `blockHeightDp`, `anchored`,
`liftFraction`), JVM-tested in `MenuLayoutTest` because `:app` has no Robolectric; the
composable is a dumb mapping over it. One un-padded `BoxWithConstraints` measures the usable
height (`maxHeight` minus the safe-drawing insets) and feeds both `MenuLayout.anchored` and
the world's `liftFraction`; below `ANCHORED_MIN_HEIGHT_DP` (420 dp — landscape phones) the
anchored layout would collide, so the same chip and block fall back to a vertically
scrolling centered column and the lift drops to 0. **Note the layout shifts when Continue is
visible — scripted UI tests must not hardcode coordinates; derive them from
`uiautomator dump`, and read a tile's label off its child text node: merged semantics still
leave the text on the child while the 88 dp bounds sit on the clickable parent.**

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

`DebriefScreen` (`ui/debrief/`, `Screen.Debrief`): the post-match chronicle —
verdict hero, one lens-switchable `TimelineChart`, turning-point feed, honours
grid — shown from the finish overlays' "View debrief" and fed by the in-memory
match recorder. Full spec in [debrief.md](debrief.md).

`PlaceholderScreen`: shared "Coming soon" screen, currently backing Settings
only. Replacing it means swapping a single `when` branch in
`MainActivity` — the `Screen` case and its `openX()` method already exist.

`FieldGuide` (`ui/guide/`) is **not** a `Screen` — it is a self-contained overlay
driven by `GuideCatalog`, and hosts just hoist a boolean and render it on top. Both
`MenuScreen` (Guide tile) and `GameScreen` (⋯ menu, and purchase cards passing
`focusEntryId` to scroll straight to one entry) do exactly that. It owns its own
`BackHandler`, so system back closes the guide without touching host navigation.

## AI driving & autosave

`maybeRunAi()` launches **`AiTurnDriver`** (`ui/AiTurnDriver.kt`) for the run of
consecutive AI seats: it thinks on `Dispatchers.Default` and calls back on
`Dispatchers.Main.immediate` through its `Host`, which the ViewModel implements as a private
inner `AiHost` scoped to one engine + one job (every main-thread callback first runs
`checkLive()`, so a teardown or a new match cancels the run instead of replaying actions
into another engine):

| `Host` member | ViewModel |
|---|---|
| `state` | `engine.state.value` (read from the compute thread) |
| `submitAi(action)` | `submit(action) is LegalityResult.Ok` — the human path: engine, board feed, scoreboards, HUD |
| `onAiSeatTurnEnded()` | `autosave()` at each AI seat's turn end |
| `presentAiSeat(seat)` | `presentedAiSeat = seat` + `refreshHud()` |
| `awaitBoardSettled()` | `board.playbackIdle.first { it }` under `withTimeoutOrNull(5 s)` — no board attached (screen not composed) returns at once; a timeout logs `Log.w(TurnFlow, …)` and proceeds, so a backgrounded app crawls instead of deadlocking |
| `onAiTurnsDone()` | clear `presentedAiSeat`, raise the pass-and-play banner if the next seat is another human, flush `handoffToasts`, finalize + autosave if the AI won mid-turn, `refreshHud()` |

The ordering contract: one action per **settled beat** — submit, wait for the board,
`AiTurnDriver.BEAT_GAP_MS` (100 ms) so consecutive moves read as distinct, submit the next;
the next action is *computed while the current beat plays* (and recomputed if the state moved
underneath), and an AI `EndTurn` also waits for the board before the next seat is presented or
the turn is handed to the human. `AiPlayer.MAX_ACTIONS_PER_TURN` (500) still caps a turn, and
a rejected action ends it rather than spinning. The driver is JVM-tested with a fake host and
virtual time (`AiTurnDriverTest`); the ViewModel's `finally` clears the presented seat on
cancellation, so the human's chrome can never stay hidden.

Probe: `adb logcat -s TurnFlow` prints one
`handoff from AI seat=N to seat=M after X ms of board playback` per handoff (X > 0 whenever
the AI actually moved), and `board playback timeout` if the 5 s bound ever fires.

Autosave also fires on human `EndTurn` and `Activity.onStop` (`persistNow`);
a finished game deletes the autosave.
