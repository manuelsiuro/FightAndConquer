# Asset Pipeline — Blender → glTF → `.pmesh`

Piece models are authored in Blender (scripted, reproducible), exported as glTF, and
**baked offline** into a tiny binary format the game loads through its existing mesh
pipeline. No glTF runtime, no new native libraries; faction tinting, spent-dimming
and all animations work on baked models exactly as on procedural ones.

```
art/blender/pieces/<name>.py ──(Blender via MCP)──► art/models/<name>.glb
        ▲ _common.py helpers                              │ tools/glb2pmesh.py
        │                                                 ▼
tools/blender_run.py            app/src/main/assets/pieces/<name>.pmesh
                                                          │ PieceMeshLoader (runtime)
                                                          ▼
                                     MeshBuilder → GpuMesh → Part(mesh, ColorRole)
```

## Blender MCP setup

- Official **Blender Lab MCP server** (`https://projects.blender.org/lab/blender_mcp`),
  cloned at `~/blender_mcp`; project config in `.mcp.json`
  (`uv --directory ~/blender_mcp/mcp run blender-mcp`).
- Blender must be running with the Lab MCP **add-on connected** (listens on
  `127.0.0.1:9876`). Requires Blender 5.1+.
- `tools/blender_run.py` — standalone stdio JSON-RPC client (works without a Claude
  session): `exec <script.py>` (auto-prepends `art/blender/_common.py`),
  `code "<python>"`, `thumb <out.png>` / `viewport <out.png>` (note: the server
  writes renders into its own temp dir and returns the real path), `tools`.
- ⚠️ The MCP server executes arbitrary Python in Blender — keep valuable .blend
  files backed up.

## Authoring conventions (`art/blender/`)

- **Blender is Z-up**; pieces stand on `z = 0` (hex top) and grow along +Z; front
  faces −Y. GLB export uses `export_yup=True`, which maps to the game's Y-up.
- **One collection per piece** (`PIECE_<KIND>`), fully rebuilt by `reset_piece()` —
  every script is idempotent and re-runnable.
- **One final object per color role**, named `<piece>__<ROLE>` with ROLE ∈
  FACTION | GOLD | STONE | TRUNK | TREE_FOLIAGE | PIP. The converter maps the name
  suffix (`.001` copies tolerated) to `ColorRole`; multiple objects of one role are
  merged into a single part. Role materials exist only for viewport preview.
- **Budgets/fit** (converter-enforced): ≤ 600 tris/piece, |x|,|z| ≤ 0.45,
  height ≤ 0.75, nothing below y −0.01. Practical target: pieces inside radius
  ~0.30. **Unit heights must stay strictly increasing by tier** (currently
  0.30 / 0.41 / 0.48 / ~0.54) and each unit's plinth carries 1–4 ink PIP studs.
- Helpers in `_common.py`: `add_cyl` (cylinder/frustum/cone via `r_top`),
  `add_sphere`, `add_box`, `add_wedge`, `add_pennant` (solidified flag — vertical in
  mesh space because piece transforms only rotate about Y), `add_pips`,
  `join_roles` (join + rename + Triangulate modifier), `stage_for_render(KIND, …)`
  (solo-hides other piece collections — without this, thumbnails show every piece
  stacked at the origin), `export_piece`.

### Verify loop (do not skip)

```bash
python3 tools/blender_run.py exec art/blender/pieces/unit_t1.py   # builds + exports GLB
python3 tools/blender_run.py thumb /tmp/t1.png                    # returns temp path — copy & inspect
```
Iterate until the silhouette reads at ~40 px, then bake and check on device.

## `.pmesh` format (produced by `tools/glb2pmesh.py`)

Little-endian; deliberately dumb (role-tagged triangle soup):

```
"PMSH"                      4 bytes magic
u8  version = 1
u8  partCount               (loader accepts 1..16)
per part:
  u8  role                  0 FACTION, 1 GOLD, 2 TREE_FOLIAGE, 3 TRUNK, 4 STONE, 5 PIP
  u16 triCount              (loader accepts 1..2048)
  triCount × 9 × f32        ax ay az bx by bz cx cy cz  (world/piece space, Y-up)
```

The converter parses GLB (JSON + BIN chunks, zero deps), walks the node tree
applying TRS/matrix transforms, resolves indices/strides, groups triangles by role
(sorted by role id → byte-stable output), validates budgets/bounds and prints a
summary line per piece. `--all art/models app/src/main/assets/pieces` converts
everything.

## Runtime loading

`render/mesh/PieceMeshLoader.parse(bytes)` (pure, unit-tested) feeds each triangle
through `MeshBuilder.addTriangle` with the GLB winding's own normal as
`expectedDir` (degenerate tris skipped) — so flat shading + tangent quats come out
identical to procedural meshes. `PieceMeshes` tries
`assets/pieces/<kind_lowercase>.pmesh` first and falls back to the procedural
token set per kind, so a missing/broken asset can never crash the game.

Shipped model set (16): `unit_t1..t4`, `archer`, `catapult`, `capital`, `farm`,
`tower`, `strong_tower`, `mine`, `market`, `lumber_camp`, `watchtower`, `tree`,
`gravestone`, plus the terrain deposits `gold_vein` and `fertile` (low
edge-scatter rings — the hex center stays clear so units never clip them).
`PieceMeshLoaderTest` re-validates every checked-in `.pmesh` against the converter
budgets and fails if a shipped kind loses its bake.

## Adding or changing a piece — checklist

1. Edit/create `art/blender/pieces/<name>.py` (respect roles, budgets, heights).
2. `blender_run.py exec` it; `thumb` and inspect; iterate.
3. `python3 tools/glb2pmesh.py --all art/models app/src/main/assets/pieces`
   — the validation line must pass.
4. New piece *kind*? Add it to `PieceKind`, a procedural fallback branch in
   `PieceMeshes.proceduralFor`, and (if it's a building/flora) its handling in
   `BoardScene` (`buildingKind`, events) + `:core` model.
5. `./gradlew :app:testDebugUnitTest` (loader tests) → install → **zoomed
   screenshot on device** (tint, dim, pips, animations) → commit the `.py`, `.glb`
   and `.pmesh` together.
