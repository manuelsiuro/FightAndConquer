# Fight & Conquer

Slay/Antiyoy-style hex-conquest game. Pure-Kotlin engine (`:core`) + Filament 3D
renderer in Compose (`:app`) + Blender-authored piece models baked to binary assets.

**Read `docs/README.md` first** — it indexes the full documentation
(architecture, rules, engine, rendering, asset pipeline, UI, roadmap).

## Commands

```bash
./gradlew :core:test                 # engine tests — run after ANY :core change
./gradlew :app:testDebugUnitTest     # app unit tests (mesh loader, projection)
./gradlew :app:assembleDebug         # build APK
python3 tools/glb2pmesh.py --all art/models app/src/main/assets/pieces   # re-bake models
python3 tools/blender_run.py exec art/blender/pieces/<p>.py              # rebuild a model (Blender must run w/ MCP add-on)
```

## Hard rules

- `:core` must never import `android.*`; the reducer stays pure; all randomness goes
  through the SplitMix64 `rngState` inside `GameState` (never `kotlin.random`,
  never clocks) — determinism is load-bearing (saves + future online play).
- The renderer treats `GameEvent`s as animation hints; `BoardScene.reconcile()`
  is the source of truth. Zero "reconcile corrected" warnings in logcat is a gate.
- Piece art changes go through `art/blender/pieces/*.py` → GLB → `.pmesh`
  (docs/asset-pipeline.md); object names carry `__<ROLE>` suffixes; ≤600 tris;
  unit heights strictly increase by tier.
- Verify on a device, not by assumption: screenshots for visuals
  (`adb exec-out screencap -p`), the `fps=` logcat probe for perf (target 120),
  AI-vs-AI simulation tests for balance.
- Filament materials (`.mat`) require recompiling with the version-matched `matc`
  (`tools/compile-materials.sh`); `.filamat` files are checked in.

## Gotchas

- `FilamentHost` ownership must stay in a plain holder — putting the engine in
  Compose state re-runs the effect and destroys the live engine (black screen).
- `Transforms.trs` supports translate + Y-rotation + scale (uniform XZ, separate
  `scaleY`) — **no X/Z rotation** (why the Blender pennant is modeled pre-rotated).
- Camera fit must use viewport aspect (portrait clips horizontally otherwise).
- Menu layout shifts when an autosave's Continue button is visible — don't
  hardcode tap coordinates in scripted UI checks.
- Blender MCP renders (`thumb`) may land in the server's temp dir instead of the
  requested path (sandboxing) — always read the `filepath` in the returned JSON,
  never assume your requested path was honored.
