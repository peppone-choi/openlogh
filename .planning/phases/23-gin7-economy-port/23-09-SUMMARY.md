---
phase: 23-gin7-economy-port
plan: 23-09-Gin7-randomizePlanetTradeRate
milestone: v2.3
subsystem: engine/economy
tags: [economy, gin7-port, trade-rate, determinism, parallel-wave, wave-3]
requirements: [EC-09]
dependency_graph:
  requires: [22-03]  # Gin7EconomyService Phase 4 scaffold
  provides: [Gin7EconomyService.randomizePlanetTradeRate]
  affects: []
tech_stack:
  added: []
  patterns:
    - verbatim-legacy-port (probability table, value bounds, seed tag preserved exactly)
    - isolated-planet-skip (Rule 2 LOGH-convention additive)
    - deterministic-rng-seed (DeterministicRng.create keyed on session hidden seed + turn coords)
    - append-at-tail-parallel-safety (wave-3 sibling coordination)
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7RandomizePlanetTradeRateTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt
decisions:
  - "Verbatim port of legacy `EconomyService.randomizeCityTradeRate` (EconomyService.kt:648-672). Probability table (4→0.2, 5→0.4, 6→0.6, 7→0.8, 8→1.0), value bounds (rng.nextInt(95,106) → 95..105 inclusive), reset default 100, and deterministic seed tag `hiddenSeed|tradeRate|year|month` all preserved exactly. Legacy body becomes dead code once Plan 23-10 wires the stub through this method"
  - "Isolated-planet skip (Rule 2 LOGH-convention additive). Planets with supplyState != 1 are excluded from the randomisation pass, mirroring the sibling Gin7 convention documented in the class-level KDoc line 26 (processIncome, salary outlay, resource growth all skip isolated planets). The legacy body processed every city unconditionally; this is a correctness tweak — isolated planets have no usable trade route, so rerolling would be noise. Documented as a deviation in the method KDoc so Plan 23-10 pipeline wire-up is aware"
  - "Append-at-tail parallel-wave coordination. Wave 3 executed 23-07 (processYearlyStatistics), 23-08 (processDisasterOrBoom), and 23-09 concurrently against Gin7EconomyService.kt. All three plans appended after the last existing method with ~25-line scope guard (this plan), ~120-line scope (23-07), and ~256-line scope (23-08). Merge landed cleanly because each plan kept its diff strictly additive — no shared import reordering, no constructor changes, no shared companion mutations"
  - "No injected Random parameter (sibling 23-08 chose the opposite). Plan 23-09 uses the legacy `DeterministicRng.create` pattern directly inside the method body rather than accepting an optional `kotlin.random.Random` parameter (the approach 23-08's processDisasterOrBoom adopted for test stubbing). Rationale: the Plan 23-09 5-test suite already exercises determinism via the `seed-23-09` hidden-seed fixture — seed-tagged DeterministicRng is sufficient. Keeping the signature single-argument matches the legacy API surface exactly and minimises the TickEngine call-site diff in Plan 23-10"
  - "TDD RED+GREEN split across two commits (standard Wave 3 pattern). The 23-09 test file references `randomizePlanetTradeRate` directly — a method-not-found RED compile failure is a clean signal. Unlike Plan 23-06 (which had to compress RED+GREEN to avoid wedging shared :game-app:compileTestKotlin for siblings), Plan 23-09 ran LAST in wave-3 execution order and the shared compile was already green from siblings' merged tails, so the standard RED-commit + GREEN-commit cadence was safe"
metrics:
  duration: ~35 minutes  # includes ~10min parallel-wave recovery after sibling 23-08's concurrent git operation transiently clobbered WT
  completed_date: 2026-04-10
  tasks_completed: 2
  files_modified: 1
  files_created: 1
  tests_added: 5
  tests_passing: 5  # scoped to Gin7RandomizePlanetTradeRateTest
commits:
  - "736d406f: test(23-09): add failing tests for Gin7.randomizePlanetTradeRate"
  - "b15b0569: feat(23-09): port randomizePlanetTradeRate into Gin7EconomyService"
---

# Phase 23 Plan 09: Gin7EconomyService.randomizePlanetTradeRate Summary

## One-liner

Periodic per-planet trade-rate shuffle: level-scaled probability (4→20% .. 8→100%) re-rolls `tradeRoute` into `[95..105]` using seeded `DeterministicRng`; low-level and isolated planets reset to default `100`.

## What was built

A single public method on `Gin7EconomyService`:

```kotlin
@Transactional
fun randomizePlanetTradeRate(world: SessionState)
```

Located at the tail of `Gin7EconomyService.kt` (lines 1176-1267 after sibling wave-3 appends). The method:

1. Loads every planet for the session via `planetRepository.findBySessionId(sessionId)`.
2. Early-returns with a debug log if no planets exist.
3. Derives a replay-safe RNG from `hiddenSeed | "tradeRate" | currentYear | currentMonth` via `DeterministicRng.create(...)`.
4. Walks each planet. Planets with `supplyState != 1` are **skipped** (LOGH addition — the legacy body processed every city unconditionally).
5. Looks up the level probability in `probByLevel = {4→0.2, 5→0.4, 6→0.6, 7→0.8, 8→1.0}`. Levels outside this range get `prob=0.0`.
6. Rolls `rng.nextDouble()`. If it falls under `prob`, assigns `rng.nextInt(95, 106)` (95..105 inclusive). Otherwise resets `tradeRoute = 100`.
7. Persists all touched planets via `planetRepository.saveAll(planets)` (single batch write — same pattern as sibling Gin7 methods).
8. Emits a structured INFO log with `mutated / reset / skipped` counters.

### Legacy reference

`EconomyService.randomizeCityTradeRate` (EconomyService.kt:648-672, ~25 lines). The legacy body was already a direct 1:1 Kotlin transcription from upstream opensamguk `EconomyService.randomizeCityTradeRate`. This plan's port preserved:

- **Probability table**: `4→0.2, 5→0.4, 6→0.6, 7→0.8, 8→1.0` — verbatim.
- **Value bounds**: `rng.nextInt(95, 106)` → 95..105 inclusive — verbatim.
- **Reset default**: `tradeRoute = 100` on prob-miss — verbatim.
- **Seed tag**: `"tradeRate"` — verbatim (replay consistency with any pre-existing scenario JSON).
- **Seed fallback**: `hiddenSeed ?: "${world.id}"` — verbatim.

### LOGH-only additions (documented deviations)

1. **Isolated-planet skip** (Rule 2 additive correctness). Planets with `supplyState != 1` are excluded. Mirrors the class-level KDoc convention on line 26: "고립 행성(supplyState=0)은 세금 징수에서 제외되며 자원 성장도 없다." Plan 23-09 extends this invariant to trade-rate randomisation.

2. **Structured logging**. A single `logger.info` line emits `year / month / mutated / reset / skipped` counters. The legacy body was silent; the LOGH port adds observability for the 24-tick drain invariant test that Plan 23-10 will activate.

### What was NOT changed

- `Planet.tradeRoute` entity field — already present at `Planet.kt:62` with default `100`. No migration required.
- `EconomyService.randomizeCityTradeRate` legacy body — left in place as dead-for-production code. Plan 23-10 pipeline wire-up will route stub callers through `Gin7EconomyService.randomizePlanetTradeRate` and retire the legacy body at that point.
- `Random` injection signature — plan considered accepting an optional `kotlin.random.Random` parameter (sibling 23-08's pattern) but rejected in favour of keeping the legacy single-arg API surface. The 5-test suite achieves determinism via the `seed-23-09` hidden-seed fixture directly.
- Call sites — none wired yet. TickEngine routing is Plan 23-10 scope.

## Tests

`backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7RandomizePlanetTradeRateTest.kt` — 5 tests, all passing:

| # | Test                                                    | Scenario                                                                 |
| - | ------------------------------------------------------- | ------------------------------------------------------------------------ |
| 1 | level 8 planets receive trade rate in 95 to 105 range   | 10 level-8 planets, all rolled into legacy bounds                        |
| 2 | isolated planets are not touched                        | supplyState=0 planet keeps its prior `tradeRoute=77`; supplied is rolled |
| 3 | low level planets reset to default trade route 100      | level-1 and level-3 planets both reset to 100 from pre-drift values      |
| 4 | empty world runs cleanly                                | no planets → no NPE, no writes                                           |
| 5 | two calls with same seed produce identical trade rates  | Fresh service instances with identical hidden seed produce identical rates; sanity probe asserts at least one rate diverges from default 100 |

Test log output verifies the counter semantics end-to-end:

```
randomizePlanetTradeRate: year=800, month=3, mutated=10, reset=0, skipped=0   # test 1
randomizePlanetTradeRate: year=800, month=3, mutated=1,  reset=0, skipped=1   # test 2
randomizePlanetTradeRate: year=800, month=3, mutated=0,  reset=2, skipped=0   # test 3
randomizePlanetTradeRate: year=800, month=3, mutated=5,  reset=0, skipped=0   # test 5 (first call)
randomizePlanetTradeRate: year=800, month=3, mutated=5,  reset=0, skipped=0   # test 5 (second call, same output)
```

## Deviations from Plan

### Auto-fixed issues

**1. [Rule 3 — Parallel-wave WT recovery] Sibling 23-08's concurrent git operation transiently clobbered my Gin7RandomizePlanetTradeRateTest.kt**

- **Found during:** Task 1 (RED) — first commit attempt
- **Issue:** `Write` tool landed my test file successfully (gradle compile surfaced it with proper "Unresolved reference: randomizePlanetTradeRate" errors), but by the time I returned to stage the file, it had disappeared from the working tree. Git status showed an unrelated `Gin7ProcessDisasterOrBoomTest.kt` as "deleted" and `Gin7ProcessYearlyStatisticsTest.kt` as untracked. The 23-08 executor's commit step had inadvertently reset files that were not its own.
- **Fix:** Re-created the test file with `Write` once confirmed the local file was missing, then staged it with an explicit `git add <path>` (avoiding `git add -A` which would have pulled in sibling noise).
- **Files modified:** none beyond the plan scope
- **Commit:** part of `736d406f` (RED)

**2. [Rule 2 — Additive correctness] Isolated-planet skip not explicitly in plan**

- **Found during:** Task 2 (GREEN) initial design
- **Issue:** The plan referenced the legacy body verbatim, which processed every city unconditionally. But every other Gin7 method (processIncome, salary outlay, resource growth) excludes `supplyState != 1` planets — this is a Phase 23 convention documented on line 26 of the class KDoc. Processing isolated planets would be inconsistent with the sibling methods and would introduce noise into the TickEngine's monthly pipeline once Plan 23-10 wires the stub.
- **Fix:** Added an explicit `if (planet.supplyState.toInt() != 1) { skipped++; continue }` guard. Test 2 (`isolated planets are not touched`) was added to the test suite to pin this invariant.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt`
- **Commit:** `b15b0569`

### No auth gates or checkpoints

Plan 23-09 is fully autonomous (wave-3 parallel). No human verification required.

## Parallel-wave notes

- Wave 3 siblings (23-07 `processYearlyStatistics`, 23-08 `processDisasterOrBoom`, 23-09 `randomizePlanetTradeRate`) all appended to the tail of `Gin7EconomyService.kt` concurrently. Final merged file grew from 771 lines (pre-wave-3) to 1267 lines after all three plans landed.
- Append order: 23-08 first (`e094fa40`, +404 lines), 23-07 second (`feefe0f8`, +~120 lines), 23-09 last (`b15b0569`, +92 lines). All three diffs were strictly additive; no shared import reordering, no constructor mutations, no companion changes.
- 23-09 ran on-top-of 23-07+23-08 GREEN state, so `./gradlew :game-app:test --tests Gin7RandomizePlanetTradeRateTest` succeeded on the first GREEN compile without gradle-init isolation.

## Next plan

**23-10** — Pipeline wire-up + 24-tick regression + legacy test audit. This final Wave 4 plan will:
- Route `EconomyService.randomizeCityTradeRate` stub → `Gin7EconomyService.randomizePlanetTradeRate`
- Schedule trade-rate shuffle as a periodic event in TickEngine's monthly pipeline (legacy cadence: every month per `ProcessIncome.php` path; LOGH plan may subsample)
- Activate the 24-tick empty-NPC-world drain invariant test
- Categorise the 205 pre-existing legacy parity test failures by which Gin7 method now covers them

## Self-Check: PASSED

- `backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7RandomizePlanetTradeRateTest.kt` — FOUND
- `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt` — FOUND (1267 lines, `randomizePlanetTradeRate` at tail)
- Commit `736d406f` (RED) — FOUND in git log
- Commit `b15b0569` (GREEN) — FOUND in git log
- 5/5 tests passing per `TEST-com.openlogh.engine.Gin7RandomizePlanetTradeRateTest.xml` (`tests="5" failures="0" errors="0"`)
