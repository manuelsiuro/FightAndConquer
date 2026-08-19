# Rules audit — August 2026

Deep review of `RuleConstants`, `Rules` and `CivModifiers` (branch
`feature/rules-audit`): are the game rules playable, and do any side effects or
corner cases remain? Every claim below was verified in code or by an executed
test, not asserted from reading. Fixes on this branch are strictly
behavior-neutral for all shipped values; nothing that could reshuffle the
chaotic AI balance gates or seed-deterministic map generation was touched.

## Verdict

**The rule set is playable and internally consistent.** The full `:core` suite
(AI-vs-AI termination and winrate gates, determinism, legacy saves, share
codec, campaign playthroughs) passes unchanged with the audit's additions, and
the new variant probes confirm the campaign gates (`navalEnabled = false`,
`disabledBuildings`, lowered `maxTier`) reject cleanly at every entry point.
No money pump exists under any civilization's effective prices. The findings
below are hardening and latent-risk items, not live defects.

## Fixed on this branch

| # | Finding | Evidence | Fix |
|---|---------|----------|-----|
| F1 | **Documented invariants were unenforced.** `visionRadiusOwned >= 2`, naval move ranges `<= visionRadiusUnit`, `fisheryRange <= visionRadiusOwned` and `demolishRefundPercent` "below 100" existed only as comments; `maxTier` above the 4-entry price lists crashed later as `IndexOutOfBounds` in `Rules.costIn`/`upkeepIn` (while `soldierMoveRanges` silently clamped via `getOrElse` — inconsistent). A hand-edited save or future campaign level could violate any of them silently. | `RuleConstants.kt` (field docs), `Rules.kt:122,140` | `init` block in `RuleConstants` + `RuleConstantsValidationTest` (14 tests). kotlinx runs `init` on decode, so bad configs fail at load. All shipped values, civ variants and campaign overrides pass (tutorial zeros stay legal). |
| F2 | **Dead code:** `Rules.region()` had zero callers in main, tests, and the app. | grep `\.region(` across repo | Deleted. |
| F3 | **Test gaps** around exactly the rule surface a campaign or UI depends on: gate flags never asserted (`NAVAL_DISABLED`, `BUILDING_NOT_AVAILABLE`), `maxTier` never driven as a changed rule, refund exploits never probed per-civ, fishing edges (dory disband, fishery demolition, embark-onto-dory, dory under bankruptcy) untested, 14 `RejectionReason` codes shown by the UI never asserted. | explorer survey of `core/src/test` | 34 new pinning tests in `RuleVariantGateTest`, `RefundExploitTest`, `RulesAuditCornerTest`, `RejectionReasonCoverageTest` — all passed on first run, confirming current behavior. |

## Verified sound (no change needed)

- **Rounding always favors the treasury sink.** Every `* percent / 100` site
  (`demolishRefund`, `disbandRefund`, capital loot at `StateBuilder.kt:164`,
  pact penalty at `:221`) floors; no refund can round up past its cost.
- **No money pump.** Refunds compute from the same effective (civ-priced)
  table the player paid (`Rules.demolishRefund`/`disbandRefund` resolve
  `effectiveRules(owner)`), the farm refund reads the *last* farm's price, and
  `RefundExploitTest` proves refund < cost for every building and unit across
  all four civilizations, cargo included.
- **BFS reach is closed under its own rules.** Land units can never capture
  open sea (only bridge hexes); warships can never storm a bridge (both
  `seaReachable` arms exclude building hexes); a bridge cannot be built under
  any boat (`Legality.kt:268`); buying or disembarking onto own forest clears
  the tree through the same `clearFloraAt` path as movement.
- **Turn pipeline order is exactly as documented** (diplomacy → gravestones →
  trees → atomic income/upkeep → bankruptcy → starvation with grace/sea-supply
  → refresh → elimination), and bankruptcy provably sinks boats too (pinned).
- **`reachable()`'s raw reads of `maxTier`/`navalEnabled`** are safe: neither
  field is in the civ-delta table, matching the documented "universal ladder"
  contract.
- **CivModifiers' one-slot cache** is correct under concurrency (`@Volatile`,
  immutable entries, worst case recompute).

## Report-only findings (deferred, with reasons)

| # | Sev | Finding | Why deferred |
|---|-----|---------|--------------|
| R1 | High (latent) | **Raw-vs-effective rules readers.** `BoardScene.refreshAuras` (`app/.../render/scene/BoardScene.kt:1313-1336`) re-implements building defense + archer aura from raw `state.config.rules`, one value for all owners; `MoveGenerator.kt:178` (`towerDefense`) and `:418` (`archerAuraDefense`) contradict that file's own "raw reads are the universal soldier ladder only" comment; `Evaluator.kt:58-68` reads the market/lumber/fishery caps raw; `StateBuilder.clearFloraAt:109` pays `treeClearBonus` raw. **No live bug**: none of these fields is civ-modified today, so raw == effective everywhere. The moment a civ delta touches a defense value, aura rings render wrong and the AI mis-prices towers. | The AI sites are provably value-identical today but sit in gate-sensitive code; BoardScene needs a per-owner design decision. Fix in a follow-up branch with a one-time gate re-baseline, per project convention. Guard suggestion: extend `CivModifiers.validate`'s doc to name the fields the raw readers assume frozen. |
| R2 | Medium | **Income formula duplication.** `Rules.incomeFrom` is mirrored exactly in `GameViewModel.kt:1298-1338` (self-flagged; any new income building must be added twice or the economy panel stops summing). `Evaluator.kt:55-70` / `MoveGenerator.kt:332-339` are *intentional heuristics*, not mirrors — no parity test is applicable. `ShopInfo` defaults (`GameViewModel.kt:218-233`) hardcode a second copy of the rule-value table incl. precomputed products (overwritten at runtime, but a drift trap). | App-module refactor (expose a per-tile income breakdown from `Rules` so the panel derives rows instead of re-computing); out of a :core audit's blast radius. |
| R3 | Low | **Market counts an owned bridge hex as a neighbor** while the bridge itself produces nothing — `docs/game-rules.md` sells the market as "+1 per producing neighbor". Pinned as current behavior in `RulesAuditCornerTest`. | Either a one-word doc fix ("adjacent owned hex") or a rule change (exclude sea); a rule change alters income and could shift AI games. Recommend the doc fix. |
| R4 | Low | **MapGenerator magic numbers**: the `/150` divisor is baked into two constant *names* (`goldVeinsNeutralPer150Hexes`, `fishShoalsNeutralPer150Hexes`, `MapGenerator.kt:176,261`); the fertile band uses a literal `2` and `FERTILE_FAIR_RADIUS = 5` where gold veins and shoals get `*BandMin/Max` fields. | Any change alters generated maps → breaks seed determinism for existing shares/saves. Cosmetic only. |
| R5 | Low | **Legality runs twice per accepted action** (`GameEngine.submit:76` then `Reducer.reduce:13`), including the `reachable()` BFS. Perf only; micro-maps and the <1 s AI turn gate show it doesn't matter today. | Correct behavior; touch only if profiling ever flags it. |
| R6 | Info | `startRegionSize` is dead (generator hardcodes radius-1) but frozen into the ShareCodec v1 dictionary (`ShareCodec.kt:188`) — **must not be removed or renamed**. The dictionary also predates 7 newer fields; that only costs compression ratio, but never "regenerate" it. | Documented here so nobody cleans it up by accident. |
| R7 | Out of scope | HARD AI's ~60 % winrate vs the 55 % gate and the fog mutual-turtle stalemate (hand-picked seeds in `AiSimulationTest`) — root cause already diagnosed in `docs/roadmap.md` "Known gaps" (`Evaluator.exposedBorderHexes` veto). | Deliberately deferred per the balance-gate convention: fix structurally once, then rebalance all gates in one pass. |

## Untouchable list

Live rule defaults, every `CivModifiers` delta, MapGenerator tuning constants,
`startRegionSize`, and the frozen ShareCodec dictionary. All are load-bearing
for save/replay/share compatibility or gate stability.

## Verification

- `./gradlew :core:test` — full suite green with all changes (real execution,
  not cache), including `AiSimulationTest`, `LegacySaveTest`, `ShareCodecTest`,
  `CampaignPlaythroughTest`; repeated with `--rerun-tasks` to confirm the
  deterministic gates reproduce.
- New coverage: 48 tests across five files (34 behavior pins + 14 validation).
- Playability: gates green across default rules *and* the campaign variants
  (`navalEnabled=false`, `disabledBuildings`, `maxTier` 1–2, zeroed tutorial
  economy), which is the full set of rule shapes the game ships.
