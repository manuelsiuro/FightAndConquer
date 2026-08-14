# Claude Design prompt — Game Screen (in-game HUD) restyle

Paste everything below the rule into Claude Design (ideally into the same project
that holds the Setup Screen design, so it can reference direction 1a directly).

---

# Fight & Conquer — restyle the in-game HUD to the established design language

## Background

*Fight & Conquer* is a Slay/Antiyoy-style hex-conquest game for Android (Jetpack
Compose Material 3, portrait phone-first, all strings localized). You previously
designed its **Setup Screen — direction 1a "Quick-start card"** — which has been
implemented and shipped. That design's language is now the app's authority, and
this brief is about bringing the **Game Screen (the in-game HUD)** into it.

## The established language (from the shipped 1a design — follow it)

- **Warm paper**: light bg `#F4F2EF` / ink `#3E3A36`; dark bg `#201E1B` / light
  warm ink. Opaque surfaces `#FFFDFB` / `#2A2724`.
- **Tokens now implemented**: hairline `#E7E1D9`/`#37332E`, divider
  `#EDE7DF`/`#302C28`, control fill `#EDE9E3`/`#332F2A`, progress track
  `#E4DFD8`/`#332F2A`, inactive glyph `#D6D0C7`/`#4A4540`; coin gold `#B8913D`,
  positive `#41663F`, alert rust `#9C4636`; six fixed faction pastels (sage
  `#8FA89B`, coral `#DE9B8B`, ochre `#E6C594`, slate `#8FA3B5`, mauve `#B59BAD`,
  olive `#A8B58F`) with dark ink `#3E3A36` on any pastel, never white.
- **Card idiom**: opaque surface + **1 dp hairline border** (not drop shadows),
  radii 11–20 dp (sheets 28). Selection = pastel fill with dark ink, or the fixed
  "filled ink" `#3E3A36`/`#F7F4F0` segment treatment (same in both themes).
  Micro-labels: 10 sp / 700 / wide tracking / uppercase / muted. Piece renders on
  plinth boxes. 0.96 press scale + ripple. Bottom sheets: top radius 28, 42%-ink
  scrim, drag handle, tap-to-apply, no confirm buttons. No dialogs anywhere —
  destructive flows use Undo or a two-tap "armed" pattern.

## What the Game Screen is

The full-screen live 3D hex board with a HUD floating over it. **Hard
constraints that must survive the redesign:**

- The board is the content; chrome must stay compact and keep hexes visible and
  tappable — every unhandled touch falls through to the board.
- Fully immersive (no system bars).
- One side panel at a time (economy / diplomacy / campaign objectives).
- Small chips and coin popups are **world-anchored** — they track hexes as the
  camera moves.
- Minimum 48 dp touch targets.
- The 3D board keeps its light pastel look in **both** themes; only the HUD
  chrome flips, so dark-theme panels float over a light board.
- Keep the interaction model exactly as-is (selection, purchase tray, undo,
  end-turn confirm, tap-to-dismiss banners). This is a **visual unification**,
  not an interaction redesign.

## Current state, component by component (real implementation values)

- **Top bar**: rounded 16 dp translucent panel, 4 dp shadow, 12 dp gutter,
  content-sized (~80 dp from screen top in practice). Contains: 14 dp faction
  disc + seat label (14 sp) over civ name (11 sp); tappable coin area (16 dp coin
  icon, treasury 15 sp, net +N/−N colored); "Turn N" 13 sp; a fresh-units pill
  (pastel @ 30%, count + 13 dp flag icon); a pact-proposals pill (warning tint,
  pact icon + count); "thinking…" plain text during AI turns. Separate floating
  circle button (top-right) with a ⋮ overflow menu (Field Guide, Diplomacy,
  Resign in rust, Exit).
- **Bottom area** (bare column, 12 dp padding, free-floating surfaces): an
  **info card** (12 dp radius panel, 3 dp shadow; 64 dp plinth with 60 dp piece
  render; title 15 sp + optional faction dot; stats line; outlined action
  buttons) / a **selected-unit strip** (same idiom at 44/40 dp plinth — a second
  scale of the same component) / a **purchase tray** of horizontally scrolling
  **128×128 dp cards** (44 dp render, name 13 sp, coin+cost, upkeep line;
  unaffordable = half-alpha container + desaturated render + rust cost; a tiny
  26 dp info button top-right) — plus an outlined **Undo** button and the
  **End-turn FAB** (56 dp, current player's pastel). With unmoved units, the FAB
  morphs for 3 s into a row of [13 sp text · outlined "✕" button · rust "End
  anyway" FAB].
- **Side panel** (264 dp, 16 dp radius, translucent panel, 6 dp shadow, hangs
  below the top bar): **Economy** — income/upkeep rows (13 sp label/value, mixed
  16 dp tinted and 20 dp untinted icons, 1 px dividers at ink@12%), emphasized
  net + treasury projection, warning strips (8 dp radius, bankruptcy = rust,
  upkeep risk = warning tint). **Diplomacy** — per-opponent rows (14 dp disc,
  name, status pill at radius-50 with four different tint alphas for
  war/pact/sent/received), outlined Propose/Tribute buttons, 10/25/50 tribute
  chips, footer 11 sp. **Campaign objectives** — mission name 15 sp, turn
  counter (alert-colored in last 3 rounds), check-circle rows with strikethrough
  + have/need counters.
- **Proposal strip** (under the top bar): 14 dp radius panel row — disc, pact
  icon, 13 sp text, outlined Decline + filled Accept.
- **Coach card** (campaign hints, above the bottom bar): solid sage fill, 14 dp
  radius, dark-ink text, TextButton dismiss — the only solid-pastel surface in
  the HUD.
- **Toasts** (top-center, max 3): 12 dp radius, 4 dp shadow; info = panel/13 sp,
  warning = warm tint/13 sp, alert = rust/white/15 sp.
- **World-anchored**: defense chips (pill, 2 dp shadow, shield icon + number
  12 sp; fixed green `#3F6142` capturable / red `#8E3E30` blocked, white text,
  theme-independent) and rising "+N" coin popups (pill, panel, 13 sp bold
  positive).
- **Full-screen overlays**: pass-and-play **turn banner** (~90% paper scrim,
  48 dp faction disc, "Player N" 28 sp, "tap to start"); **game-over** (72 dp
  disc + 96 dp capital render, winner 26 sp, one pastel button); campaign
  **outcome** (title 26 sp, 3 stars in coin gold, debrief paragraph, stacked
  Next/Retry/Leave buttons) — all with text sitting directly on the scrim, no
  card.

## Why it needs your pass

The HUD predates the 1a design and now clashes with it: it elevates everything
with **drop shadows on translucent panels** and uses **zero hairline borders and
none of the new tokens**. Beyond the language clash, an audit found accumulated
drift: two plinth scales for one card idiom (64/60 vs 44/40 dp); nine ad-hoc ink
alphas (.06–.85) alongside the three defined ink tokens; four different pill
tint alphas in one component; the side panel and toasts anchored at 68 dp while
the real bar is ~80 dp tall, so they slide under it; a 26 dp touch target beside
a 48 dp rule; a 🪙 emoji in one button while everywhere else uses the coin
vector; three different button idioms inside the end-turn confirm row; and
full-screen overlays whose text floats on the scrim with no surface.

## The task

Restyle the entire HUD into the 1a language as **one coherent direction** (not
multiple alternatives), keeping every behavior above. Where the language
underdetermines the answer, decide and specify:

1. **The elevation rule for chrome floating over a live 3D board.** Setup's flat
   1 dp hairline may not separate enough from a busy board — define the unified
   treatment (opaque vs slightly translucent surface, hairline and/or minimal
   shadow) and apply it everywhere.
2. **One plinth/card scale system** for info card, unit strip, and purchase
   cards.
3. **A unified end-turn confirm** replacing the three-idiom morph row (the
   two-tap "armed" pattern must survive).
4. **Panel/section header idiom** consistent with the setup micro-labels.
5. **Overlay treatment**: whether banners/outcome screens gain a card surface,
   and a consistent scale for their discs/titles/star rows.
6. A rationalized **tint/alpha system** for pills, badges, and disabled states
   using the existing tokens.

Board-anchored chips keep their fixed, theme-independent colors (they mirror the
board palette), and faction pastels stay identical in both themes.

**Deliverables**: portrait phone mockups (390×844 reference) in **both themes**
of these states — (1) idle board with top bar + end-turn FAB; (2) unit selected:
info/unit strip + defense chips over the board; (3) purchase tray open with an
unaffordable card; (4) economy panel open; (5) diplomacy panel open with an
incoming proposal strip; (6) campaign: objectives panel + coach card + the
outcome overlay; (7) pass-and-play turn banner. Use grey diagonally-striped
placeholder boxes with monospace captions for the 3D board and all baked piece
renders (as in the setup handoff). Finish with a handoff README in the same
format as the Setup Screen one: final tokens, per-component metrics (sizes,
radii, type, spacing), state behavior notes, and required assets.
