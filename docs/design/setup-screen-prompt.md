# Claude Design prompt — Setup Screen redesign

Paste everything below the rule into Claude Design.

---

# Fight & Conquer — app design context + a screen redesign task

## The game

*Fight & Conquer* is a Slay/Antiyoy-style hex-conquest strategy game for Android.
The centerpiece is a live 3D board rendered in-app: low-poly Blender-authored
pieces (soldiers, knights, castles, ships) on pastel hex tiles. Turn-based: capture
territory, manage a coin economy, fight up to 3 AI or local opponents. Four
civilizations with light rule asymmetry: **Kingdom** (the baseline), **Vikings**
(sea raiders), **Sultanate** (desert merchants), **Shogunate** (defensive
discipline).

## Visual language (applies to every screen)

- **Warm paper aesthetic.** Light theme: background `#F4F2EF`, ink `#3E3A36`,
  near-white panels `#FFFDFB`. Dark theme is the same aesthetic inverted: dark warm
  paper `#201E1B`, light warm ink. Theme follows the system setting; no Material
  dynamic color.
- **Six fixed faction pastels** identify players in every context: sage `#8FA89B`,
  coral `#DE9B8B`, ochre `#E6C594`, slate blue `#8FA3B5`, mauve `#B59BAD`, olive
  `#A8B58F`. They stay identical in both themes (they mirror the 3D board's
  palette). Text on a pastel is always dark ink `#3E3A36`, never white.
- Accent tokens: coin gold `#B8913D`, positive green `#41663F`, alert rust `#9C4636`.
- **Rendered 3D piece icons** are a house signature: baked renders of the actual
  game pieces sit on small "plinth" cards throughout the HUD (shop cards, info
  cards, menu decoration). Every civilization has its own icon set.
- Flat glyphs are tinted vector icons (coin, flag, shield, pact) — no emoji in
  persistent chrome.
- **No confirmation dialogs anywhere.** Destructive actions rely on Undo or a
  two-tap "armed" pattern with a warning toast.
- Edge-to-edge with transparent system bars; the in-game screen is fully immersive.
- Implementation is Jetpack Compose Material 3; portrait phone-first; all text
  localized; UI state survives rotation.

## Navigation

The main menu is the hub; every other screen returns straight to it (no deep back
stack). Menu → New Game (Setup), Continue, Campaign, Map Editor (library → editor),
Guide (overlay), Settings, About; Setup/Campaign/Library all lead into the Game
screen.

## The screens

**1. Main Menu** — a decorative tableau of rendered pieces (knight, castle, tower
on a plinth) under the game title, then a vertical button list: Continue Game (only
when an autosave exists), New Game, Campaign, Map Editor, Guide, Settings, About.
The first of Continue/New Game is a filled button, the rest outlined.

**2. Setup Screen — ⭐ the redesign target** — described in detail below.

**3. Campaign Screen** — a row of campaign chips over the selected campaign's
mission list: number badge, mission name, best-rounds or lock reason, and an
earned-stars row per mission.

**4. Briefing Screen** — the pre-mission card: story text, the mission's objectives
with live counters, defeat clauses, "new in this mission" chips that deep-link into
the Field Guide, and a Begin / Play-again button.

**5. Game Screen** — the 3D board fullscreen with a HUD floating over it. Top bar:
player chip in the seat's pastel, tappable coin/net income area, turn counter,
fresh-units badge, pending-proposal badge, overflow menu (Field Guide, Diplomacy,
Resign, Exit). Bottom bar: a context card (tapped-piece info with a 60 dp piece
render on a plinth, selected-unit hint, or a purchase tray of 92 dp cards with
piece renders — desaturated when unaffordable), Undo, and an End-Turn FAB that
morphs into "N unmoved · ✕ · End anyway" when fresh units remain. One side panel at
a time hangs from the top bar (264 dp): Economy (income/upkeep rows with 20 dp
piece icons, net + projection, bankruptcy warnings), Diplomacy (per-opponent rows
with faction dot, war/pact status pill, propose-pact and tribute chips), or
campaign Objectives (mission name, turn counter that turns alert-colored near the
limit, struck-through objective lines with have/need counters). Toasts top-center;
world-anchored "+N" coin popups; defense chips float over board hexes. A
full-screen privacy banner covers the board between pass-and-play turns; game-over
/ mission-outcome overlays (stars, debrief, Next/Retry/Leave) scrim everything.

**6. Field Guide** — a rules-reference overlay (not a screen) available from the
menu and in-game; entries can be deep-linked (a shop card's "?" opens the guide at
that piece).

**7. Map Library ("My maps")** — the player's authored scenarios: list with draft
badges (validation issues counter), Play/Edit/Share per map. Sharing exports a text
code, `.fcmap` file, QR code, or an image with the map hidden inside.

**8. Map Editor** — the same live 3D board in author mode: brush chips (terrain,
owner, buildings, units, capitals, objective hexes), per-seat treasuries and rules
toggles, undo, an issues counter; drag-to-paint behind a Paint toggle that parks
pan/zoom.

**9. About** — identity, version, credits, links, open-source licenses.

**10. Settings** — currently a "Coming soon" placeholder.

## The task: redesign the Setup Screen

**Current state:** one scrolling centered column of ~10 identical sections, each a
small gray label over a horizontally-scrolling row of Material filter chips. In
order: Map source (Generated / Custom — row only appears if custom maps exist;
choosing Custom replaces *all* rows below with a scrollable list of map-name chips,
since a custom scenario plays exactly as authored) · Opponents (1–3 enemies) · Mode
(vs AI / Pass & Play) · Difficulty (Easy/Normal/Hard, vs-AI only) · one
Civilization row **per seat** ("Yours"/"AI 1"… or "Player 1"… in Pass & Play;
duplicates allowed; civ is independent of seat color) · Map size
(Small/Medium/Large ≈ 120/250/450 hexes) · Map type
(Continent/Islands/Archipelago) · Fog of war (Off/On, default off) · Special units
(On/Off, default on) · Diplomacy (On/Off, default on) · full-width sage Start
button · Back link. While the map generates, the form is replaced by a spinner +
"Generating…".

**Why it fails:** no hierarchy — a first-timer's choice (how many enemies) looks
identical to an expert toggle (diplomacy off). Per-seat civ rows multiply to a wall
of repeated chips at 4 players. Horizontal scrolling hides options. On/Off chip
pairs are clumsy booleans. It reads as a settings form in a game whose other
screens are full of rendered pieces, pastels, and personality.

**Goals, in priority order:**

1. **One tap from defaults to playing.** Sensible defaults exist (1 enemy, vs AI,
   Normal, Kingdom, Medium Continent); a returning player should barely pause here.
   Consider progressive disclosure: essentials prominent, world-tuning and rule
   toggles behind an "advanced" affordance.
2. **Make it feel like the rest of the game.** Civ picking deserves the piece-icon
   treatment; seats can wear their faction pastels; map type/size could be visual.
3. **Scale per-seat civ selection gracefully** from 2 to 4 seats.
4. **Handle the modal states cleanly:** the custom-map collapse, the vs-AI-only
   difficulty, differing seat labels in Pass & Play, and the generating state.

**Deliverable:** explore 2–3 distinct directions (e.g. quick-start card with
progressive disclosure, a short stepped flow, a visual card-based single screen) as
portrait phone mockups in **both light and dark themes**, staying coherent with the
sibling screens described above. Then recommend one direction and detail its
component breakdown for a Compose Material 3 implementation.
