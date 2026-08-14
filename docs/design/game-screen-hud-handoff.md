# Handoff: Fight & Conquer — Game Screen HUD restyle

> Source: Claude Design project `2188e6bc-a627-4b6b-928a-9fe271ff4bef`
> (`design_handoff_game_screen_hud/README.md`, mockups in `Game Screen HUD.dc.html`).
> The prompt that produced it is `docs/design/game-screen-prompt.md`.

## Overview

*Fight & Conquer* is a Slay/Antiyoy-style hex-conquest game for Android (Jetpack Compose,
Material 3, portrait phone-first, all strings localized). The Setup Screen was previously
redesigned and shipped as direction **1a "Quick-start card"**, and that design is now the app's
visual authority.

This handoff restyles the **Game Screen (the in-game HUD)** into that same language. It is a
**visual unification, not an interaction redesign** — every existing behavior is preserved:
selection, purchase tray, undo, end-turn confirm, tap-to-dismiss banners, one side panel at a
time, world-anchored chips, immersive full-screen (no system bars), and 48 dp minimum touch
targets.

The HUD previously elevated everything with drop shadows on translucent panels, used zero
hairline borders and none of the shipped tokens, and had accumulated drift (two plinth scales,
nine ad-hoc ink alphas, four pill tint alphas, panels anchored at a hard-coded 68 dp under a
~80 dp bar, a 26 dp touch target, a 🪙 emoji, three button idioms inside one confirm row,
overlay text floating on a bare scrim). All of that is resolved here.

## About the Design Files

The files in this bundle are **design references created in HTML** — prototypes showing the
intended look and behavior, not production code to copy. The task is to **recreate these
designs in the app's existing Jetpack Compose Material 3 codebase**, using its established
composables, theme, and patterns. Do not port HTML/CSS structure; read the metrics below and
express them in Compose (`Modifier.border(1.dp, hairline, RoundedCornerShape(…))`, `Surface`,
`Card`, etc.).

Dimensions in the HTML are **CSS px at a 390 × 844 reference frame, and map 1:1 to dp**. Text
sizes map to **sp**. Everything scales with the system font setting: panels and cards grow
vertically; the 128 dp purchase card is the one fixed box and clamps its name to one line with
ellipsis.

## Fidelity

**High-fidelity.** Final colors, typography, spacing, radii, and states. Recreate pixel-for-pixel
using the app's existing theme tokens. The only placeholders are the **grey diagonally-striped
boxes with monospace captions**, which stand in for the live 3D board and every baked piece
render — they are not artwork.

---

## Design Tokens

Add these to the app theme alongside the tokens already shipped with Setup 1a. Names below are
the ones used throughout this document.

| Token | Light | Dark | Use in the HUD |
| --- | --- | --- | --- |
| `bg` | `#F4F2EF` | `#201E1B` | Overlay scrim base only (the board fills the screen) |
| `surface` | `#FFFDFB` | `#2A2724` | Every panel, card, toast, chip, overlay card |
| `ink` | `#3E3A36` | `#F2EEE9` | Primary text, outlined-button stroke |
| `inkMuted` | `#8A8279` | `#9A9188` | Micro-labels, secondary lines |
| `hairline` | `#E7E1D9` | `#37332E` | 1 dp border on every surface and plinth |
| `divider` | `#EDE7DF` | `#302C28` | Row rules inside panels |
| `controlFill` | `#EDE9E3` | `#332F2A` | Plinths, chips, ✕, secondary buttons, emphasis block |
| `track` | `#E4DFD8` | `#332F2A` | Unearned outcome stars |
| `inactiveGlyph` | `#D6D0C7` | `#4A4540` | Unaffordable card text, unchecked objective ring |
| `coinGold` | `#B8913D` | `#D9B168` | Coin vector, warning tint @30%, stars |
| `positive` | `#41663F` | `#7FA97C` | Net +N, coin popups, pact pill @30%, done check |
| `alertRust` | `#9C4636` | `#D2705C` | Unaffordable cost, war pill @30%, End anyway, last-3-turns counter, Resign |

**Elevation — `boardLift`**, the only shadow in the HUD:
`0 2 6 rgba(62,58,54,.10)` light · `0 2 8 rgba(0,0,0,.34)` dark.
In Compose: `Modifier.shadow(2.dp, shape, ambientColor/spotColor tuned to the above)` or an
equivalent single elevation constant. Every HUD surface is **opaque** — no translucency anywhere.

**Faction pastels** (fixed, identical in both themes, always with `#3E3A36` ink on top — never
white, never light ink, including on the dark-theme FAB and coach card):
sage `#8FA89B` · coral `#DE9B8B` · ochre `#E6C594` · slate `#8FA3B5` · mauve `#B59BAD` · olive `#A8B58F`.

**Board-anchored chip colors** (fixed, theme-independent, white text — they mirror the board palette):
capturable `#3F6142` · blocked `#8E3E30`.

**Tint ladder** — only three steps exist. This replaces the nine ad-hoc ink alphas and the four
pill alphas:

| Step | Use |
| --- | --- |
| **12%** | Neutral glyph wash (untinted icons on a surface) |
| **30%** | Every status pill, badge, warning strip, and tinted icon |
| **100%** | Solid fills: coach card, FAB, filled-ink segment |

**Disabled** = container opacity **38%** + `inactiveGlyph` on text.

**Filled ink** (the fixed selection treatment carried over from Setup, identical in both themes):
fill `#3E3A36`, content `#F7F4F0` in light; fill `#F2EEE9`, content `#201E1B` in dark.

**Radii**: pill/circle 50% · chip 8 · strip/warning 10 · plinth S 11 · card 12–14 · plinth M 13 ·
panel/bar/card 16 · FAB, armed surface, plinth L, overlay card 20 · bottom sheets 28 (unchanged
from Setup).

**Spacing**: screen gutter 12 · between stacked HUD surfaces 8 · panel padding 14 · card padding
12 · bar padding 8 with a 12 leading inset · panel row vertical padding 9 · overlay card inset 24,
padding 20–24, internal rhythm 16.

**Typography** — the app's existing display face, weights 400/600/700/800:

| Role | Size / weight |
| --- | --- |
| Overlay title | 26–28 sp / 800 |
| Card title, mission name | 15 sp / 700 |
| Row name, seat label, button | 14 sp / 600–700 |
| Body, value, secondary button | 13 sp / 400–700 |
| Stats, footnote, counters | 11–12 sp / 400–700 |
| Micro-label | 10 sp / 700, `.12em` tracking, uppercase, `inkMuted` |

---

## Screens / Views

Frames in `Game Screen HUD.dc.html` are ordered 01–07, each in light and dark.
The 3D board renders identically in both themes — **only the HUD chrome flips**, so dark panels
float over a light board. That is intentional.

### Universal chrome rule

Every HUD surface: **opaque `surface` + 1 dp `hairline` + `boardLift`**. The hairline does the
separating; the shadow only lifts the surface off a moving board. (Setup keeps its shadowless
hairline — the shadow exists only because the board is live.) Press feedback everywhere:
**0.96 scale + ripple**, including plinth cards and the FAB.

### Plinth / card scale

One system: **plinth = render + 8 dp**, plinth radius = render radius + 4. Three steps only.
This replaces the old 64/60 and 44/40 pair.

| Step | Plinth / render | Radius | Used by |
| --- | --- | --- | --- |
| S | 40 / 32 | 11 / 7 | Selected-unit strip |
| M | 56 / 48 | 13 / 9 | Info card, purchase card |
| L | 96 / 80 | 20 / 14 | Overlays (capital render) |

Plinth = `controlFill` + 1 dp `hairline`.

---

### 1. Top bar (all frames)

**Purpose**: identity, treasury, turn, and the two persistent HUD entry points.

Full-bleed: 12 dp side gutters, 16 dp from the top of the immersive window, `surface` + radius 16
+ hairline + `boardLift`, padding **8** with a **12 dp leading inset**. Content-sized height
(≈72 dp one-row, ≈100 dp with the pill row).

Row, left to right:

1. **Faction disc** — 14 dp filled circle in the current player's pastel.
2. **Identity column** (`weight(1f)`, `min-width: 0`): seat label 14 sp/700 `ink` ("You" /
   "Player 2"), over `"<Civ name> · Turn N"` 11 sp `inkMuted`. **Both lines are single-line with
   ellipsis.** The turn counter lives on this line (it used to be a separate right-aligned label
   with a 1 dp × 22 divider) so the identity block keeps its width against the trailing group.
3. **Coin block** — 16 dp coin vector, treasury 15 sp/700 `ink`, net `+N`/`−N` 13 sp/700 in
   `positive` or `alertRust`. 6 dp internal gaps, and a 48 dp tap target achieved with
   `padding: 6 8; margin: -6 -8`. **Tapping it opens the Economy panel.**
4. **Trailing group** — two 48 dp circles, 4 dp apart, `controlFill`:
   - **Diplomacy** (20 dp pact glyph at 55–60% ink). **Promoted out of the ⋮ menu** — this is the
     one structural change to the interaction surface. When proposals are pending it carries a
     **9 dp `coinGold` badge** at top-right with a 1 dp `surface`-colored ring.
   - **Overflow ⋮** (19 sp glyph).
   Either circle flips to the **filled ink** treatment while its surface is open. Frame 05 shows
   Diplomacy active; frame 06 shows the menu active.

**Optional second row**, 8 dp under the identity row: the **fresh-units pill** — padding 4/10,
radius 50, pastel @30%, 13 dp glyph + 13 sp/700 `ink` ("3 fresh"). Dark theme raises pills to
**solid pastel** for legibility. The old pact-proposals pill is retired — the Diplomacy badge
covers it. During AI turns the pill row is replaced by "thinking…" 13 sp `inkMuted`.

**Overflow menu**: anchored under the trailing group. `surface`, radius 16, hairline, `boardLift`,
48 dp rows at 14 sp — Field Guide, Objectives, Resign (`alertRust`), Exit. Resign uses the
two-tap armed pattern. **No dialogs anywhere in the HUD.**

> **Anchoring rule**: panels, toasts, and the proposal strip anchor to the top bar's
> **measured bottom + 8 dp**, never a constant. The old hard-coded 68 dp let them slide under a
> ~80 dp bar. In the reference frames this resolves to `top: 110` (or 174 when a proposal strip
> is present).

---

### 2. Idle board (frame 01)

Top bar (with pill row) + End-turn FAB. Nothing else. The board is the content.

**End-turn FAB**: 56 dp, radius 20, filled with the **current player's pastel**, 1 dp
`rgba(62,58,54,.14)` hairline, `boardLift`, bottom-right at 12 dp. Content: "End" 15 sp/800 `ink`
over a 9 sp micro-label "TURN" at 62% ink.

---

### 3. Unit selected (frame 02)

**Purpose**: act on the selected unit and inspect the hex under the cursor.

Bottom cluster, 12 dp gutter, 8 dp between surfaces, bottom-up:
FAB row → info card → selected-unit strip.

- **Selected-unit strip** (scale S): radius 14, padding 8/12. Plinth S 40/32 · title 14 sp/700 ·
  stats 12 sp `inkMuted` ("Str 2 · moves left 1 · upkeep 6") · 32 dp outlined "Deselect"
  (radius 16, 1 dp `ink` stroke, 13 sp/700).
- **Info card** (scale M): radius 16, padding 12, 12 dp internal gap. Plinth M 56/48 ·
  title 15 sp/700 + 10 dp faction dot · stats 12 sp `inkMuted` · 1 dp `divider` · action row:
  44 dp buttons, radius 14, 14 sp/700 — primary **outlined** (1 dp `ink`), secondary
  **`controlFill`** with `inkMuted` label.
- **Undo**: 44 dp, radius 14, `surface` + 1 dp **`ink`** stroke + `boardLift`, 14 sp/700.

**World-anchored elements** (they track their hex as the camera moves):

- **Defence chip**: radius 50, padding 4/10, 12 dp shield + 12 sp/700 **white** on fixed
  `#3F6142` (capturable) or `#8E3E30` (blocked), `boardLift`, **no hairline**, identical in both
  themes.
- **Coin popup**: radius 50, padding 3/9, `surface` + hairline + `boardLift`, 13 sp/700
  `positive`. Rises 24 dp over 700 ms and fades.

---

### 4. Purchase tray + armed end-turn (frame 03)

**Purchase card**: 128 × 128 dp, radius 16, padding 10, 6 dp internal gaps, horizontally
scrolling row with 8 dp gaps. Plinth M 56/48 · name 13 sp/700 (one line, ellipsis) · 14 dp coin +
cost 13 sp/700 · upkeep as a 10 sp micro-label. **Info button**: 28 dp glyph circle in
`controlFill` at top-right, extending over the card padding into a **48 dp touch target**
(replaces the old bare 26 dp target).

**Unaffordable state**: container keeps full opacity and stays tappable (tapping surfaces a
"not enough coin" toast); render drops to **38% + grayscale**; name and upkeep use
`inactiveGlyph`; cost turns `alertRust`; the info glyph goes `inactiveGlyph`.

**Tray header**: "RECRUIT" micro-label. A section header sitting directly over the board never
runs bare — it takes a **surface chip** (`surface` + hairline, radius 8, padding 3/8, `inkMuted`)
in both themes. The divider variant is only for headers inside a panel.

**Armed end-turn** — replaces the old three-idiom morph row. One surface spanning the full
gutter width on its own row **below** Undo: radius 20, padding 8, `surface` + hairline +
`boardLift`, containing
`[ micro-label "3 UNITS UNMOVED" over "Tap again to end" 13 sp/600 ]` ·
`[ 48 dp ✕, radius 16, controlFill + hairline ]` ·
`[ 48 dp "End anyway", radius 16, alertRust fill, 14 sp/800 ]`.
All three text runs are `nowrap`. Disarms after **3 s**, or on ✕, or on a board tap. Two taps to
end, exactly as before.

---

### 5. Economy panel (frame 04)

**Side panel** geometry, shared by all three panels: **264 dp** wide, right-aligned at the 12 dp
gutter, top = top-bar bottom + 8. `surface`, radius 16, hairline, `boardLift`, padding 14, 10 dp
between blocks. **One panel at a time** — opening one closes the other.

**Panel header idiom** (consistent with Setup micro-labels): 10 sp/700/`.12em`/uppercase/
`inkMuted`, optional right-aligned second micro-label for context ("TURN 7"), then a 1 dp
`divider` with an 8 dp gap.

**Rows**: 9 dp vertical padding, 18 dp tinted icon square @30% (`positive` for income,
`alertRust` for cost), label 13 sp, value 13 sp/700, 1 dp `divider` between rows.

**Emphasis block**: `controlFill`, radius 12, padding 10/12 — "Net per turn" 13 sp/700 with the
value 15 sp/800 in `positive`/`alertRust`; "Treasury next turn" 12 sp `inkMuted` with 14 dp coin +
13 sp/700 value.

**Warning strip**: radius 10, padding 9/11, `coinGold` @30% (upkeep risk) or `alertRust` @30%
(bankruptcy), 14 dp glyph + 12 sp text in `ink`.

---

### 6. Diplomacy panel + proposal strip (frame 05)

**Proposal strip**: full width at the 12 dp gutter, directly under the top bar, radius 16,
padding 10/12, 10 dp gaps — 14 dp faction disc · 18 dp pact glyph @12% · 13 sp text ·
36 dp outlined "Decline" (radius 12) · 36 dp **filled-ink** "Accept". The panel below it shifts
down accordingly (top 174 in the reference frame).

**Opponent row**: 14 dp disc · name 14 sp/600 · **status pill** — radius 50, padding 3/9,
10 sp micro-label in `ink`, exactly one 30% tint: `alertRust` war · `positive` pact ·
`coinGold` incoming offer · `controlFill` sent. (This replaces four different ad-hoc alphas.)
1 dp `divider` between rows.

**Expanded row** adds: 40 dp outlined "Propose" / "Tribute" (radius 12, 13 sp/700), then
32 dp tribute chips 10/25/50 — radius 50, `controlFill`, selected = **filled ink**.
Footer: 11 sp `inkMuted`.

---

### 7. Campaign: objectives + coach card + toasts (frame 06)

**Objectives panel**: standard panel geometry. Mission name 15 sp/700 with the turn counter
13 sp/700 beside it (**`alertRust` in the last 3 rounds**). Rows: 18 dp check circle — filled
`positive` when done, with the label struck through and `inkMuted`; otherwise a 1 dp
`inactiveGlyph` ring — label 13 sp, have/need counter 12 sp/700.

**Coach card** (campaign hints): the **only solid-pastel surface in the HUD** — sage fill,
radius 16, 1 dp `rgba(62,58,54,.14)` hairline, `boardLift`, padding 12/14. "HINT" micro-label at
70% ink, body 14 sp `ink`, text-button dismiss right-aligned with a 48 dp target. Sits above the
bottom cluster.

**Toasts**: top-center, 56 dp side insets, **max 3**, 6 dp apart, radius 12, padding 9/12,
centered 13 sp. Info = `surface`; warning = `coinGold` @30% over surface; alert = `alertRust`
@30% over surface. **All three use `ink` text** — the old white-on-rust 15 sp variant is retired
so there is one text color everywhere. Tap to dismiss.

---

### 8. Full-screen overlays (frames 06b, 07)

Overlay text no longer floats on a bare scrim. Scrim = `bg` @ **92%**; a single centered card
carries everything: 24 dp inset, radius 20, `surface` + hairline + `boardLift`, 20–24 dp padding,
16 dp internal rhythm.

Consistent overlay scale: hero disc **72 dp** · hero plinth **L 96/80** · title **26–28 sp/800** ·
a micro-label above the title · stars **22 dp** (`coinGold` earned, `track` unearned) ·
buttons **52 dp**, radius 16 — pastel primary (15 sp/800 `ink`), outlined secondary,
`controlFill` tertiary.

- **Pass-and-play turn banner** (frame 07): 72 dp faction disc · micro-label "TURN N · FACTION" ·
  "Player N" 28 sp/800 · 1 dp `divider` · "Tap anywhere to start" 14 sp `inkMuted`. The whole
  screen is the tap target.
- **Game over**: 72 dp disc + plinth L capital render · winner 26 sp/800 · one 52 dp pastel button.
- **Campaign outcome** (frame 06b): plinth L · mission micro-label · title 26 sp/800 · 22 dp star
  row · debrief 14 sp centered (`line-height: 1.5`) · stacked 52 dp Next / Retry / Leave.

---

## Interactions & Behavior

Unchanged from the current implementation unless noted:

- Tap a hex to select, tap again to deselect. The purchase tray opens on a friendly capital.
  Undo reverts the last action. Banners dismiss on tap.
- **One side panel at a time**: Economy (from the coin block), Diplomacy (from the new top-bar
  button), Objectives (from the overflow menu). Opening one closes the other.
- **Every unhandled touch falls through to the board.** HUD surfaces are opaque and therefore
  consume touches — the bottom cluster and the 264 dp side panel are sized to leave the board's
  center free.
- **Armed patterns, never dialogs**: end-turn-with-unmoved-units and Resign both arm on the
  first tap and commit on the second; 3 s or an explicit ✕ disarms. Destructive results use Undo.
- **Press feedback**: 0.96 scale + ripple on every tappable surface.
- **Disabled** controls stay in the layout at 38% container opacity and are not tappable.
  Unaffordable purchase cards are the exception: they remain tappable and surface a toast.
- **World-anchored** elements (defence chips, coin popups) re-project every frame as the camera
  moves.
- **Theme flips HUD chrome only.** The 3D board and its anchored chips render identically in
  both themes.
- **48 dp minimum** holds everywhere, including the purchase-card info button (28 dp glyph,
  48 dp target), the coach dismiss, and the coin block.

## State Management

No new state is introduced by this restyle. The HUD reads:

- `currentPlayer` (seat index, faction pastel, civ name), `turnNumber`
- `treasury`, `netPerTurn`, income/upkeep breakdown, treasury projection
- `freshUnitCount`, `pendingProposalCount` (now drives the Diplomacy button badge, not a pill)
- `selection`: none | unit | hex — drives which bottom surface is shown
- `openPanel`: none | economy | diplomacy | objectives (mutually exclusive)
- `purchaseTrayOpen` + per-item affordability
- `endTurnArmed: Boolean` with a 3 s timeout, plus `unmovedUnitCount`
- `toasts: List<Toast>` capped at 3, `coachHint`, `incomingProposal`
- `overlay`: none | turnBanner | gameOver | campaignOutcome
- Campaign: mission name, turns remaining, objective list with have/need

One new piece of derived layout state: the **measured height of the top bar**, published so
panels, toasts, and the proposal strip can anchor to its bottom + 8 dp
(`onGloballyPositioned` or a `SubcomposeLayout`).

## Assets

- **Vectors** (single-color, tintable, drawn at 24 dp, rendered at 12/14/16/18/20 dp): coin,
  shield, flag, pact/handshake, warning, check-circle, info, close, overflow, income, upkeep,
  tribute. **The 🪙 emoji is removed** — the coin vector is used everywhere, including the
  tribute button.
- **Baked piece renders**: transparent PNG at 32, 48, and 80 dp (× density buckets), one per unit
  and building, plus a capital render for the game-over overlay. Rendered against the light board
  palette so they read on the `controlFill` plinth in both themes.
- **Faction discs** are drawn, not assets: a filled circle in the faction pastel at 10 / 14 / 48 /
  72 dp.
- **Type**: the app's existing display face at weights 400 / 600 / 700 / 800.
- The grey diagonally-striped boxes with monospace captions in the mockups are **placeholders**
  for the 3D board and every baked render.
