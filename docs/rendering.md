# Rendering (`:app` — Google Filament)

Dependency: `com.google.android.filament:filament-android` (see
`gradle/libs.versions.toml` for the pinned version) + `dev.romainguy:kotlin-math`.
Deliberately **no** gltfio / filament-utils / filamat-lite (size + 16 KB-alignment
surface; models are baked offline instead — see asset-pipeline.md).

## Host & lifecycle

- `render/RenderEngine.kt` — hand-rolled Engine/Renderer/Scene/View/Camera behind a
  `SurfaceView` + `UiHelper` + `DisplayHelper`; Choreographer frame loop
  (`onFrame(frameTimeNanos, dt)` hook → render). OpenGL backend forced
  (emulator-safe). Linear tone mapper (ACES crushes the pastels), manual exposure
  `setExposure(16, 1/125, 100)`, MSAA 4×, SSAO (radius 0.3, MEDIUM), pale clear
  color. Debug FPS probe logs `fps=` every ~5 s (tag `FightRender`).
- `render/FilamentHost.kt` — Compose host. **Ownership lives in a plain holder, not
  Compose state**: a state-keyed `DisposableEffect` once re-ran on the factory's
  state write and destroyed the live engine (black screen). Pause/resume follows the
  lifecycle; `onRelease` tears down scene-first, `Engine.destroy()` last.
- `render/SceneEnvironment.kt` — one directional sun `normalize(1,−1,0.4)` at
  100 k lux with PCF shadows (2048 map, shadowFar 60, normalBias 1) + single-SH-band
  flat ambient `IndirectLight` (25 k) — no IBL asset needed for matte materials.

## Materials (`app/src/main/materials/` → `assets/materials/*.filamat`)

Compiled offline with `matc` (`tools/compile-materials.sh`); **matc version must
match the filament-android runtime** — recompile on every Filament upgrade.

| Material | Params | Used for |
|---|---|---|
| `piece` | `baseColor` float3, `roughness` | Every piece part; faction tint + spent-dim are per-instance `baseColor` writes |
| `hexTile` | `colorFrom/colorTo/tileCenter/waveRadius/waveSoftness` | Tiles; the radial capture wave is pure uniform animation |
| `highlight` | `color` float4 (unlit, transparent) | Selection/move/capture discs + defense auras |
| `water` | `shallowColor/deepColor/time` | Sea tiles; slow sine interference bands from `getWorldPosition().xz`, one shared instance (plus fog-band variants), a single `time` write per frame |

## Procedural meshes (`render/mesh/`)

- `MeshBuilder` — flat-shaded triangle accumulator. **Winding is self-correcting**:
  every face passes an `expectedDir`; if the geometric normal opposes it the
  triangle is flipped, so lighting and culling can never disagree. Tangent-frame
  quaternions are derived per face (`TangentFrames`) because Filament's lit model
  wants TANGENTS, not raw normals.
- `Primitives` — hex prism (R 0.5, H 0.25, bevel 0.05, skirt −0.15 so raised tiles
  never show a gap), hexDisc/hexAnnulus, cylinder/cone/frustum/sphere, boxes,
  wedges, merlon rings, star profiles, pennants — plus `*Into(builder, …)` variants
  for multi-part single meshes. Board metrics constants live here
  (`HEX_RADIUS/HEX_HEIGHT/CAPTURE_RAISE`, and `SEA_SINK = 0.12` — sea tiles are
  ordinary prisms translated down, so the land skirt forms the cliff coastline).
- `PieceMeshes` — the 28 `PieceKind`s as lists of `Part(GpuMesh, ColorRole)`.
  **Loader-first**: baked `assets/pieces/<kind>.pmesh` wins; the procedural token
  set remains as per-kind fallback. `ColorRole`: FACTION (player tint), GOLD,
  TREE_FOLIAGE, TRUNK, STONE, PIP (ink).

## BoardScene (`render/scene/BoardScene.kt`) — the whole board

- **Tiles**: one entity per hex, shared prism buffers, per-tile `hexTile` instance.
  Owned tiles sit +0.1 (`CAPTURE_RAISE`); capture animates height + wave (0.3 s).
  Sea tiles share `water`-material instances instead (per fog band, swapped via
  `setMaterialInstanceAt` — no per-tile uniforms), never raise, and get no
  capture wave; `reconcile` computes height terrain-aware.
- **Pieces**: registry `unitPieces[UnitId]`, `buildingPieces[Hex]`, `floraPieces[Hex]`.
  A `Piece` = N part entities sharing one transform (`Transforms.trs`: translate +
  Y-rotation + uniform scale only), its roles + ownerIndex (for re-tinting),
  `setDimmed()` (spent units ×0.72 on every part), and a `yaw`. Bridge yaw is a
  pure function of state (`PieceHeadings.bridgeYaw`): the player-stored
  `Tile.bridgeOrientation` wins, else the chain's through-axis (both deck ends
  touching land/bridge), else any connected end — so create and reconcile
  always agree, and reconcile silently re-aims existing spans as their chain
  grows (never a correction, same class as fog). Unit yaw is **view-only**
  heading: motion segments turn the piece toward its travel direction over
  their first quarter (`PieceHeadings.headingYaw` + shortest-arc `lerpAngle`),
  the heading persists at rest, and reconcile deliberately ignores it.
- **Event queue / director**: `apply(state, events)` enqueues; `onFrame` starts the
  next beat only when the `Animator` is idle, so beats play strictly in order.
  Handlers (spawn bounce easeOutBack + camera rumble, multi-hex path hops via
  region-BFS `ownedPath` at 0.16 s/hex capped 0.9 s, capture wave, merge
  converge→upgrade bounce, sink→gravestone, tree grow…) each map one `GameEvent`.
  Naval beats: boats **glide** along a sea-BFS path (no hopping), embark hops the
  passenger onto the boat then removes its piece, disembark spawns and hops off,
  bombard flashes the target, and a death at sea sinks deeper with **no
  gravestone**. Idle boats bob (±0.008 `yOffset` in `onFrame`, per-unit phase —
  reconcile ignores `yOffset`, keeping the zero-warning gate safe).
  `TurnStarted` refreshes all dim states. Tap during playback = `skipAnimations()`.
- **Reconcile**: after every queue drain (and on undo/load via the ViewModel's
  `resync` tick) the scene diffs against `GameState` and snaps tiles/pieces/dim —
  logs a warning if it had to correct anything.
- **Highlights & auras**: pooled `highlight`-material discs
  (`showHighlights(selected, moves, captures, merges)`; capture discs pulse via a
  per-frame alpha sine) and `hexAnnulus` rings on every tile covered by a
  tower/castle/capital (alpha 0.30 + 0.08·(defense−1)), refreshed in reconcile.
  Z-layering: tile top < aura (+0.006) < discs (+0.012).
- **Fog of war** (`setFog(visible, explored)`): view-only, synced **silently** —
  never counted as a reconcile correction (same pattern as `spent`→dim). Tiles keep
  their logical faction color in `TileEntity.color`; `applyTileColor` renders it
  only when visible (explored = neutral × 0.45, hidden = × 0.12 — pure Kotlin
  uniform scaling, no matc recompile). Pieces on fogged hexes leave the scene via
  `Piece.setHidden` (applied inside `createPiece` too — no one-frame flash). Events
  are never filtered; only juice is suppressed in fog (rumble, capture wave, aura
  rings). `setFog` re-derives auras so no ring survives inside fog.
- **Anchors for the HUD**: `setTrackedAnchors(Set<Hex>)` +
  `anchors: StateFlow<Map<Hex, Float2>>` — screen positions published from
  `onFrame` (quantized to ¼ px, change-detected ⇒ zero traffic when idle) at
  `tileTop + 0.8` so labels clear the tallest piece.

## Day-night look (`SceneEnvironment.setNight`, `BoardScene.applyNightFactor`)

The optional day-night mode's board look is Kotlin-side end to end — **no matc
recompile** (the fog factors' pattern: pure uniform scaling at the existing
write sites). One `nightFactor` (0 = day, 1 = night) drives five knobs:

- **Sun**: color lerps to moon-blue (0.62, 0.70, 1.0), intensity 100k → 12k lux
  (`LightManager.setColor/-Intensity`; direction untouched so shadows stay
  coherent through the transition).
- **Ambient**: two PREBUILT `IndirectLight`s (an SH irradiance color is
  immutable after build) — warm day / cool night — swapped at factor 0.5,
  invisible under the moving sun lerp; intensity lerps 25k → 8k.
- **Clear color**: `Palette.BACKGROUND` → `NIGHT_BACKGROUND` (deep slate) via
  `RenderEngine.setClearColor`.
- **Tiles/water/pieces**: linear-space multipliers (`Palette.NIGHT_*_MULT`)
  composed onto the FINAL fog-banded colors — tiles in `applyTileColor`, the
  two shared water instances re-baked, every piece through `Piece.refreshTint`
  (the one tint write site: dim × night). Highlights, auras and HUD chips stay
  untinted for readability.

`NightFell`/`DawnBroke` play as one-shot ~1.2 s tweens on the **shared**
animator: `isBusy` is true only for the beat, and the beat gates the event
queue — darkness lands before the first `MonsterSpawned` plays; dawn sinks all
surviving monster pieces as one beat, then brightens. Reconcile snaps the
factor from `Rules.isNight(state)` **silently** (a view annotation like fog),
which is what restores a mid-night save with no events. Monsters idle with a
boat-bob-style breathing ripple (`yOffset` sine on the ambience clock — never
`isBusy`), so an idle night still renders at ~20 fps.

### Beacon point lights (`BoardScene.refreshBeaconLights`)

The app's only punctual lights: one **shadowless** warm POINT light per
visible lit beacon (`Tile.beacon`), pooled in `beaconLights` (hex → entity)
beside the aura pool. `refreshBeaconLights(state)` diffs the pool — called
from reconcile (after `refreshAuras`), `setFog`, and the `BeaconLit`/
`BuildingDestroyed` beats; a source hidden by fog contributes **no** light
(the aura-source rule — spill at the rim would betray the hidden building).
Intensity is `BEACON_LIGHT_LUMENS × nightFactor`, written inside
`applyNightFactor`: the glow fades in with the existing dusk tweens, is zero
by day, and costs nothing per frame between (never `isBusy`). Shadows stay
OFF — a cube shadow map per light would wreck the heat budget the pacing
system protects. The falloff (~1.8) spills onto the six neighbors only; the
LIGHT is presentation — `Rules.litHexes` is the protection truth. The lit
building itself swaps to a `*_LIT` PieceKind (`buildingKind(building, lit)`),
so the brazier flame is baked geometry, and the `BeaconLit` beat performs the
swap so reconcile never counts it as a correction.

The READABLE safe zone is the **lit-ground tint**: `litTint` (derived beside
the light pool from the same fog-checked sources, expanded by
`Rules.beaconRadiusOf`) switches those hexes' night multiplier from the cool
`NIGHT_TILE_MULT` to the warm `Palette.BEACON_TILE_MULT` inside `nightTile` —
hex-accurate against the protection rules, fading in/out with the dusk tweens
for free (`applyNightFactor` already repaints every tile), invisible by day
(`nightMix` is identity at factor 0), and applied to the visible fog band only
so it reveals nothing the fog hides. This tints the GROUND, not an overlay —
the "highlights, auras and HUD chips stay untinted" rule is untouched. A
mid-night lighting (or a fallen beacon) repaints exactly the flipped hexes.

## Camera & picking (`render/CameraRig.kt`, `HexPicker.kt`, `HexWorld.kt`)

Orbit rig (target on the ground plane, min distance 5, fixed 55° pitch — no
rotate/pitch gesture exists, so the rig exposes none — FOV 30° near-ortho
look). `fitCameraOnce` frames the whole board using the **viewport
aspect** (portrait makes horizontal FOV the constraint) and raises the max
distance per board (`max(40, fit×1.3)` — the constructor's 35 is only a default;
the far plane sits at 800 because island maps fit the camera ~330 units out). With
`BoardScene.fitForOrbit` set it fits the circumscribed circle instead, via `OrbitMath`
(see "Frame pacing" — the menu backdrop is the only caller).
`jumpTo(hex)` glides on a **separate Animator** (the shared one gates the event
queue); user pan cancels glides. Picking is CPU ray-casting: `rayThrough(px)` →
plane tests against each possible tile top — raised land, land, sunken sea —
accepting the first whose hex really has that height (`topYOf`) → axial
cube-rounding — and
`project(world)` is its exact inverse (unit-tested round-trip), which is what makes
HUD anchors line up with picking.

## Performance envelope

~450 land tiles + a size-scaled ocean (fringe 3/4/5 + filled inland basins —
roughly 2 000+ tiles total on a LARGE archipelago) + ~40 baked pieces (66–362
tris each) + pooled overlays — measured 119.4–120 fps on a Galaxy S24 (sea
tiles share per-fog-band material instances, so the water adds no per-tile
uniform traffic). Per-frame CPU extras are a handful of uniform writes (pulse/dim) and
≤ ~20 `project()` calls while labels are visible.

**Frame pacing (heat budget).** The loop renders every vsync only while the
scene is *busy* — `SceneController.isBusy()`: animations or queued beats,
camera glides, rumble, pulsing highlights, or the ~10 frames after any input
(`BoardScene.wake()`). A still board drops to a ~20 fps ambience rate
(`RenderEngine.IDLE_FRAME_INTERVAL_NANOS`): water shimmer and boat bob advance
by accumulated dt, so they stay smooth-slow rather than fast-choppy. A
turn-based game is idle most of its life, and rendering a static board at the
display rate is what used to cook the phone. Consequence for the `fps=` probe:
the target applies **while animating** — an idle board legitimately logs
~20 fps, so read drops only during action. Shadow map is 1024 (soft toy
shadows at tabletop zoom) and SSAO runs LOW — both retuned for heat with no
visible change.

**The one exception: the menu's attract orbit.** `BoardScene.autoOrbitRadPerSec`
(non-zero only on the menu backdrop, `ui/MenuScreen.kt`) advances `CameraRig.yaw` every
frame through `OrbitMath.advanceYaw` and makes `isBusy()` true for as long as it is set,
so the menu is the single place the board renders at the display rate while nothing
gameplay-related moves. That is deliberate: an idle-throttled orbit is a ~20 fps stutter,
and the menu is a short-lived screen, not the heat-relevant steady state (measured
118–120 fps on the SM-S921B). The companion knobs frame it: `fitForOrbit` makes
`fitCameraOnce` fit the board's true circumscribed circle — `OrbitMath.circumscribedRadius`
over the tile centers, then `OrbitMath.orbitFitDistanceForCircle` — so no yaw can clip the
board, and `orbitFitMargin` scales that distance (the menu passes a margin *below* 1 on
purpose, letting the sea rim overflow the screen sides). Both default to off, so the game
path is untouched.
