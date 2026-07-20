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
  (`HEX_RADIUS/HEX_HEIGHT/CAPTURE_RAISE`…).
- `PieceMeshes` — the 10 `PieceKind`s as lists of `Part(GpuMesh, ColorRole)`.
  **Loader-first**: baked `assets/pieces/<kind>.pmesh` wins; the procedural token
  set remains as per-kind fallback. `ColorRole`: FACTION (player tint), GOLD,
  TREE_FOLIAGE, TRUNK, STONE, PIP (ink).

## BoardScene (`render/scene/BoardScene.kt`) — the whole board

- **Tiles**: one entity per hex, shared prism buffers, per-tile `hexTile` instance.
  Owned tiles sit +0.1 (`CAPTURE_RAISE`); capture animates height + wave (0.3 s).
- **Pieces**: registry `unitPieces[UnitId]`, `buildingPieces[Hex]`, `floraPieces[Hex]`.
  A `Piece` = N part entities sharing one transform (`Transforms.trs`: translate +
  Y-rotation + uniform scale only), its roles + ownerIndex (for re-tinting) and
  `setDimmed()` (spent units ×0.72 on every part).
- **Event queue / director**: `apply(state, events)` enqueues; `onFrame` starts the
  next beat only when the `Animator` is idle, so beats play strictly in order.
  Handlers (spawn bounce easeOutBack + camera rumble, multi-hex path hops via
  region-BFS `ownedPath` at 0.16 s/hex capped 0.9 s, capture wave, merge
  converge→upgrade bounce, sink→gravestone, tree grow…) each map one `GameEvent`.
  `TurnStarted` refreshes all dim states. Tap during playback = `skipAnimations()`.
- **Reconcile**: after every queue drain (and on undo/load via the ViewModel's
  `resync` tick) the scene diffs against `GameState` and snaps tiles/pieces/dim —
  logs a warning if it had to correct anything.
- **Highlights & auras**: pooled `highlight`-material discs
  (`showHighlights(selected, moves, captures, merges)`; capture discs pulse via a
  per-frame alpha sine) and `hexAnnulus` rings on every tile covered by a
  tower/castle/capital (alpha 0.30 + 0.08·(defense−1)), refreshed in reconcile.
  Z-layering: tile top < aura (+0.006) < discs (+0.012).
- **Anchors for the HUD**: `setTrackedAnchors(Set<Hex>)` +
  `anchors: StateFlow<Map<Hex, Float2>>` — screen positions published from
  `onFrame` (quantized to ¼ px, change-detected ⇒ zero traffic when idle) at
  `tileTop + 0.8` so labels clear the tallest piece.

## Camera & picking (`render/CameraRig.kt`, `HexPicker.kt`, `HexWorld.kt`)

Orbit rig (target on the ground plane, min distance 5, pitch 35–70°, FOV 30°
near-ortho look). `fitCameraOnce` frames the whole board using the **viewport
aspect** (portrait makes horizontal FOV the constraint) and raises the max
distance per board (`max(40, fit×1.3)` — the constructor's 35 is only a default).
`jumpTo(hex)` glides on a **separate Animator** (the shared one gates the event
queue); user pan cancels glides. Picking is CPU ray-casting: `rayThrough(px)` →
two-plane test (raised top first, then base) → axial cube-rounding — and
`project(world)` is its exact inverse (unit-tested round-trip), which is what makes
HUD anchors line up with picking.

## Performance envelope

~450 tiles + ~40 baked pieces (66–362 tris each) + pooled overlays ≈ well under
100 k tris and ~700 draw calls incl. shadow pass — measured 119–120 fps on a
Galaxy S24. Per-frame CPU extras are a handful of uniform writes (pulse/dim) and
≤ ~20 `project()` calls while labels are visible.
