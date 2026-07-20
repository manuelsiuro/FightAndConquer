# Architecture

## Module map

```
┌─────────────────────────────────────────────────────────────────┐
│ :app (Android, Compose + Filament)                              │
│                                                                 │
│  MainActivity ── GameViewModel ─────────────┐                   │
│                    │      │                 │                   │
│              HudState  highlights/labels  cameraJumps           │
│                    │      │  toasts/popups │                    │
│  GameScreen ───────┴──────┴───────┬────────┘                    │
│    ├─ FilamentHost (SurfaceView)  │                             │
│    │    └─ BoardScene ◄───────────┘  (tap/pan/zoom, apply)      │
│    │         ├─ RenderEngine (Engine/Renderer/View/Camera)      │
│    │         ├─ PieceMeshes ← PieceMeshLoader ← assets/pieces   │
│    │         ├─ MaterialStore ← assets/materials                │
│    │         ├─ Animator + event queue                          │
│    │         └─ CameraRig + HexPicker                           │
│    └─ HUD composables (TopBar, BottomBar, overlays, toasts)     │
└───────────────┬─────────────────────────────────────────────────┘
                │ implementation(project(":core"))
┌───────────────▼─────────────────────────────────────────────────┐
│ :core (pure Kotlin JVM — zero Android imports)                  │
│                                                                 │
│  GameEngine (facade)                                            │
│    ├─ state: StateFlow<GameState>     events: SharedFlow<Event> │
│    ├─ submit(action) → Legality.check → Reducer.reduce          │
│    └─ undo / queries (reachableFor, buyableAt, incomeSummary)   │
│  Reducer + StateBuilder + TurnPipeline   (the rules)            │
│  Rules (pure queries: region, defenseOf, reachable, income)     │
│  MapGenerator + MapValidator + MapDefinition                    │
│  AiPlayer + MoveGenerator + Evaluator                           │
│  SaveGame + SaveCodec (kotlinx.serialization JSON)              │
│  hex/ (packed axial Hex, HexMath), engine/Rng (SplitMix64)      │
└─────────────────────────────────────────────────────────────────┘
```

## Data flow (one player action)

1. Compose gesture → `BoardScene.tap()` → CPU ray-pick → `GameViewModel.onHexTapped(hex)`.
2. ViewModel decides (selection state machine) and calls `engine.submit(GameAction)`.
3. `GameEngine`: `Legality.check` → `Reducer.reduce(state, action)` → new immutable
   `GameState` + ordered `List<GameEvent>` → publishes to `state` / `events` flows.
4. `GameScreen` collector feeds each event to `BoardScene.apply(state, [event])`;
   the scene queues events and plays one animation beat at a time; when the queue
   drains it runs `reconcile(state)` (self-healing snap to truth).
5. ViewModel refreshes `HudState` (+ economy panel, overlay labels, toasts) from the
   same state; Compose recomposes.

AI turns run the same path: a coroutine picks `AiPlayer.chooseAction(state)` on
`Dispatchers.Default` and submits on the main thread with ~220 ms pacing so board
animations keep up. The AI uses only public actions — it cannot cheat.

## Design principles

- **Immutable state + pure reducer.** `reduce(state, action) -> ReduceResult(state, events)`
  never throws; illegal actions return the unchanged state + `ActionRejected`.
- **Determinism as a hard contract.** SplitMix64 RNG state is a field of `GameState`;
  identical seed + action list ⇒ bit-identical serialized states (tested). This makes
  saves replayable and keeps the door open for lockstep online play.
- **Event-driven rendering with a reconcile safety net.** Events drive animation;
  `reconcile()` guarantees correctness. Debug builds log a warning whenever reconcile
  had to correct anything (target: zero).
- **One narrow seam between game and presentation:** the `GameEngine` facade
  (`core/engine/GameEngine.kt`). Nothing in `:app` touches the reducer directly.
- **Assets are baked, not parsed at runtime.** Blender models are converted offline
  to `.pmesh` triangle soups that flow through the same `MeshBuilder` as procedural
  geometry — no glTF runtime, no new native libs (16 KB page-size surface unchanged).

## Threading model

| Thread | Work |
|---|---|
| Main | Everything stateful: Compose, `GameEngine.submit/undo`, `BoardScene` (Choreographer frame loop, animations, Filament calls), autosave scheduling |
| `Dispatchers.Default` | AI `chooseAction` (pure), map generation for new games |
| `Dispatchers.IO` | Routine autosave writes, save loads (`persistNow` in `onStop` deliberately writes synchronously on main — the process may die before a dispatch runs) |
| Filament internal threads | Driver/backend work owned by Filament (`Engine.Backend.OPENGL`) |

`GameEngine` is deliberately thread-confined (call submit/undo from main only).
The only cross-thread objects are `StateFlow`/`SharedFlow`.

## Lifecycles

- `FilamentHost` owns creation/destruction: ownership lives in a plain holder
  (NOT Compose state — a state-keyed effect once destroyed the live engine; see the
  comment in `FilamentHost.kt`), pause/resume follows the lifecycle, teardown is
  scene-content first, `Engine.destroy()` last.
- Autosave: after every `EndTurn` (+ AI turn ends) and in `Activity.onStop`
  (`persistNow`) so a process kill resumes mid-turn. Finished games delete the save.

## Key invariants (tested in `:core`)

- `tiles[unit.hex].unit == unit.id` and units always stand on tiles they own.
- Treasury never negative after turn-start enforcement.
- Capital tile always carries `Building.CAPITAL` while its player is alive.
- Full AI-vs-AI games terminate; Hard beats Easy ≥ 70 % on mirror seeds;
  Easy expands beyond its start region within 3 rounds (anti-catatonia regression).
