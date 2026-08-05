# Map editor & sharing

The in-app editor lets players author full scenarios — terrain, ownership,
buildings, deposits, flora, starting units, per-seat treasuries, seat kinds
(including `PASSIVE`), rules toggles and win/lose conditions — and share them as a
text code, a `.fcmap` file, a QR code, or an image with the map hidden inside it.

## The artifact

One shape end to end (`core/editor/CustomMapDef.kt`):

```
CustomMapDef(version, id (UUID = file name), name, author?, createdAt, modifiedAt,
             level: LevelDef)
```

The envelope wraps a **real campaign `LevelDef`**, so playing a custom map is
literally `LevelFactory.instantiate(def.level)` and forward compatibility rides on
the campaign format's own tolerance (`ignoreUnknownKeys` + defaulted fields —
`MapCodec` mirrors `CampaignCodec`). Storage is one JSON per map at
`filesDir/maps/<id>.json` (`ui/editor/CustomMapStore.kt`, the
`CampaignProgressStore` idiom).

## Validation: drafts, never blocks

`core/map/MapViolation.kt` is the typed vocabulary (the old prose API
`MapValidator.validateAuthored` is now literally `codes.map { describe() }`, so its
wording never drifted). `core/editor/CustomMapValidator.kt` adds the scenario
checks — a mirror of every `require` in `LevelFactory` plus the static objective
checks `CampaignFormatTest` runs on shipped missions. Its contract: **a scenario
with zero violations can never throw at instantiation.**

Saving never blocks on violations — a violating map is a *draft* (badge in the
library, issues counter in the editor, translated via `MapViolation.toUiText`,
exhaustive `when` like `RejectionReason`). Play and share both require a clean map.

`core/editor/EditorPreview.kt` builds the render-only `GameState` for half-built
maps (no capital requirements, illegal units skipped); `MapDefinition.newGame`
stays strict for play.

## Editor session & canvas

`ui/editor/EditorSession.kt` — a plain holder owned by `GameViewModel` (the
`CampaignRepository` precedent): one immutable `CustomMapDef` evolved by brushes,
bounded undo (50 strokes, no-ops excluded), re-validated per stroke. Brushes that
need a player read `Ui.activeSeat`. Notable rules baked into the brushes:

- **Sea/land contract by construction** — painting sea strips owner/building/
  flora/land deposits; terrain edits sweep stranded units.
- **Capitals are atomic** — the capital tool sets owner + `CAPITAL` + the
  `capitals[seat]` entry together; painting the pending seat's capital *creates*
  the seat (up to 6). No bridge brush (authored bridges violate the sea contract);
  no buildings on neutral land (the campaign ASCII rule).
- Objective hexes for capture/hold goals are painted on the board via
  `Brush.ObjectiveHexes`, shown through the hint-highlight channel.

`ui/editor/MapEditorScreen.kt` drives the same `FilamentHost`/`BoardScene` stack
as play, in editor mode: `BoardScene.applyEditorState` diffs the *tile set*
(create/destroy/terrain-flip — the one thing `reconcile` deliberately never does),
`pickVoid` ray-picks the flat land plane so the map can grow onto the ghost ring
(`setGhosts`). Drag-to-paint hides behind the Paint chip, which parks pan/zoom.

## Playing a custom map

Both entries call `GameViewModel.playCustomMap`: the library's Play button and
Setup's "Map source: My maps" picker (which supersedes the generation options —
custom maps play **strictly as authored**). The run goes through the ordinary
campaign director under the sentinel campaign id **`@custom`**
(`GameViewModel.CUSTOM_CAMPAIGN`): objectives, turn limits and the tracker all
work; the repository can't resolve the sentinel, so next-mission, unlocks and
star recording naturally don't apply. Autosaves carry `CampaignSaveRef("@custom",
mapId)` — resume reloads the def from the store; if the map was deleted, the save
degrades to a plain skirmish resume.

## Sharing

Every channel carries the same envelope (`core/share/ShareCodec.kt`):

```
body = [fmtVer 1B][CRC32 4B][deflate(MapCodec JSON)]
file = "FCM1" + body        (.fcmap)
text = "FCM1:" + base64url(body)
```

Deflate runs against a **frozen preset dictionary** (a canonical scenario's JSON
baked into `ShareCodec`) — it collapses the ~1.3 KB self-describing boilerplate,
taking a 9-tile scenario from ~1 375 to ~200 chars and a SMALL generated map to
~1 540, under the QR guard. **Never regenerate the dictionary**: v1 codes are
compressed against exactly those bytes; envelope changes bump the version byte.
Decode checks outermost-first (magic → version → CRC → capped inflate → JSON →
`CustomMapValidator`), each failure mapping to one `ShareError` → translated
string. Imports are adopted under a fresh id.

Channels (`ui/share/MapShareManager.kt`, all zero-permission):

| Channel | Out | In |
|---|---|---|
| Text code | clipboard | paste |
| `.fcmap` file | FileProvider + `ACTION_SEND` (`cacheDir/shared_maps`) | document picker, 1 MB cap |
| QR | zxing:core encode (EC L) in a dialog; disabled over 2 000 chars (measured in `ShareCodecTest`) | pick a screenshot/photo of a QR |
| Stego image | `MinimapRenderer` (2D canvas minimap) + `core/share/LsbStego` (RGB LSBs, alpha untouched) → PNG | image picker: stego payload first, then QR fallback |

The stego share dialog warns that photo-compressing apps destroy the payload; a
recompressed image fails cleanly ("no map data found") because both the stego
magic and the envelope CRC must survive.

## Tests & gates

- `:core` — `MapViolationTest` (wording parity), `CustomMapValidatorTest`
  (violation ↔ `LevelFactory` mirror), `EditorPreviewTest`, `MapCodecTest`,
  `ShareCodecTest` (round-trips, corruption matrix, **measured sizes** pinning the
  QR threshold), `LsbStegoTest`, `CustomMapPlaythroughTest` (AI-vs-AI on authored
  scenarios terminates).
- `:app` — `CustomMapStoreTest`, `EditorSessionTest` (every brush rule),
  `QrRoundTripTest` (encode→rasterize→decode at guard size).
- Device gates: author/save/reopen a map; a full game on a custom map with
  background/restore; zero `reconcile corrected` logcat lines in ordinary play
  (the editor never logs corrections by design); the share/import loop of all
  three channels, including the JPEG-recompression negative test.
