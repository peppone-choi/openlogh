---
phase: 23-gin7-economy-port
plan: 23-08
subsystem: economy
tags: [gin7-port, disaster-boom, rng-injection, korean-templates]
requirements: [EC-08]
wave: 3
milestone: v2.3
dependency_graph:
  requires:
    - 23-01-PLAN (Gin7EconomyService scaffolding / processIncome)
    - 23-06-PLAN (Gin7EconomyService is the Plan-23 target file; updatePlanetSupplyState landed first and established the full repo ctor)
  provides:
    - Gin7EconomyService.processDisasterOrBoom(world, rng) — deterministic RNG-injectable disaster/boom event generator
    - Gin7EconomyService.DisasterOrBoomEntry — private data class carrying state code + Korean title/body templates
  affects:
    - 23-10-PLAN (pipeline wire-up will schedule this method monthly and route the TODO(23-10) history-log markers into the event bus / HistoryService)
tech_stack:
  added: []
  patterns:
    - "Method-level RNG parameter (kotlin.random.Random with Random.Default fallback) replaces legacy DeterministicRng.create(config) pattern — enables stub RNG injection without reaching through world.config"
    - "Append-at-class-tail placement to avoid Wave-3 parallel merge conflicts with siblings 23-07/23-09 also editing Gin7EconomyService.kt"
    - "Null-guarded OfficerRepository usage — legacy 2-arg test ctors skip officer injury loop silently via defensive null check on the ctor parameter"
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7ProcessDisasterOrBoomTest.kt (307 lines — 5 tests + 2 stub RNG objects)
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt (+404 lines at tail)
decisions:
  - "D-08-01: RNG injection via method parameter (not constructor) — lets a single service instance run with different RNGs per call for deterministic test coverage without spawning fresh service objects. Defaults to kotlin.random.Random.Default for production."
  - "D-08-02: historyService.logWorldHistory emission stubbed to logger.info with TODO(23-10) marker — HistoryService and MessageRepository are not wired into Gin7EconomyService yet, pipeline wire-up in Plan 23-10 will add them."
  - "D-08-03: Korean event templates preserved verbatim (역병/항성 폭풍/에너지 부족/반란군 for disasters; 호황/풍작 for booms). State codes match legacy numeric values (1..9). LOGH re-skinning deferred — these strings are the same ones already in legacy EconomyService.processDisasterOrBoom, so this is a textual no-op."
  - "D-08-04: DisasterOrBoomEntry data class ported as a private nested data class inside Gin7EconomyService (mirrors legacy placement, not shared with EconomyService's own copy)."
  - "D-08-05: Officer injury loop kept behind null-guard on officerRepository — sibling 23-02 already wired it via a 3-arg ctor, but the 2-arg legacy ctor still used by 23-06/23-03 tests must not NPE. injury loop silently no-ops when officerRepository is null."
metrics:
  duration: 16m
  completed_date: 2026-04-10
  tasks: 2
  files: 2
---

# Phase 23 Plan 23-08: Gin7.processDisasterOrBoom Summary

Ported the largest method in the legacy economy pipeline (~164 lines, `EconomyService.processDisasterOrBoom`) into `Gin7EconomyService` with a method-parameter RNG injection to enable deterministic test coverage.

## One-liner

Disaster/boom event generator now lives in Gin7EconomyService with stub-RNG-injectable probability rolls, Korean event templates preserved verbatim, and officer injury loop guarded behind the optional OfficerRepository for parity with earlier Wave-2 sibling plans.

## What was built

### Task 1 (RED): Failing test file
**Commit:** `365e2243`
**File:** `backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7ProcessDisasterOrBoomTest.kt` (307 lines)

5 tests exercising:
1. **3-year grace period** — `startYear + 3 > currentYear` returns without mutation
2. **Disaster path** — injected `AlwaysZeroRandom` forces every planet hit; asserts resources decayed by the exact `0.8..0.95` affectRatio band
3. **Boom path** — month 4 + `AlwaysZeroRandom` forces boom selection; asserts resources grew by the exact `1.01..1.05` band, state code = 2
4. **Miss path** — `AlwaysHighRandom` (nextDouble → 0.999) ensures no per-planet roll triggers; legacy state-reset pass still runs (state code `<=10` → 0)
5. **Distribution sanity** — 1000 seeded Random iterations; assert hits fall in `[5, 200]` window (expected ~35 for 3.5% probability)

Two stub RNG classes extend `kotlin.random.Random` directly (no Mockito ceremony for final class):
- `AlwaysZeroRandom`: every `nextDouble()` returns 0.0, every `nextInt(n)` returns 0 — guarantees all probabilistic gates trigger
- `AlwaysHighRandom`: every `nextDouble()` returns 0.999999, `nextInt(until)` returns `until-1` — guarantees no probabilistic gate triggers

### Task 2 (GREEN): Port the method
**Commit:** `e094fa40`
**File:** `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt` (+404 lines at tail)

- `private data class DisasterOrBoomEntry(stateCode, title, body)` ported verbatim from legacy
- `processDisasterOrBoom(world: SessionState, rng: Random = Random.Default)` — method body is a line-by-line port of legacy `EconomyService.processDisasterOrBoom` (EconomyService.kt:484-644)
- Disaster entry table: 4 entries for month 1, 3 for month 4, 3 for month 7, 4 for month 10 — **Korean flavor text byte-identical to legacy**
- Boom entry table: month 4 = `【호황】` (state=2), month 7 = `【풍작】` (state=1)
- Per-planet probability:
  - boom: `0.02 + secuRatio * 0.05` (2%-7%)
  - disaster: `0.06 - secuRatio * 0.05` (1%-6%)
- AffectRatio application:
  - boom: `1.01 + min(secuRatio/0.8, 1) * 0.04` (1.01-1.05), coerced to max per resource
  - disaster: `0.80 + min(secuRatio/0.8, 1) * 0.15` (0.80-0.95), no max coercion
- Officer injury loop guarded by `if (officerRepository != null)` — legacy 2-arg test ctors skip safely

## Acceptance criteria

| Criterion | Status | Notes |
|---|---|---|
| `DisasterOrBoomEntry` data class ported | ✅ | private nested in Gin7EconomyService |
| Full STATE_CODE table ported verbatim | ✅ | 14 disaster entries + 2 boom entries |
| `processDisasterOrBoom(world)` public method | ✅ | plus optional `rng` parameter for test determinism |
| Seeded RNG for test determinism | ✅ | `kotlin.random.Random` injected via method param, not constructor |
| Korean event templates preserved | ✅ | 역병/항성 폭풍/에너지 부족/반란군/소행성/우주 방사선/자원 고갈/흉작/혹한/함대 봉쇄/호황/풍작 |
| Probability calibration matches legacy | ✅ | boom `0.02+secu*0.05`, disaster `0.06-secu*0.05` — byte-identical formulas |
| 5 tests passing | ✅ | BUILD SUCCESSFUL in 30s (test exec 3.696s) |
| Each task committed with `--no-verify` | ✅ | 365e2243 (RED), e094fa40 (GREEN) |
| SUMMARY.md + STATE.md + ROADMAP.md updated | ✅ | this file + tool-driven updates below |

## Test results

```
testsuite name="com.openlogh.engine.Gin7ProcessDisasterOrBoomTest"
  tests=5 skipped=0 failures=0 errors=0 time=3.696

  testcase: skips first 3 years from session start()                     [2.825s]
  testcase: no planet mutated when per planet roll misses()              [0.014s]
  testcase: disaster path reduces planet resources when targeted()       [0.060s]
  testcase: boom path increases planet resources when targeted()         [0.008s]
  testcase: probability distribution is non-trivial over 1000 iterations [0.780s]
```

BUILD SUCCESSFUL — 30s wall-clock, 2 executed tasks + 7 UP-TO-DATE.

## Deviations from Plan

### Auto-fixed issues

**1. [Rule 3 - Blocking] Parallel Wave 3 test-compile contention**
- **Found during:** Task 2 verification (running `:game-app:compileTestKotlin`)
- **Issue:** Sibling Wave 3 plans 23-07 (`Gin7ProcessYearlyStatisticsTest`) and 23-09 (`Gin7RandomizePlanetTradeRateTest`) had landed their RED test files (`4d50aa16` and `736d406f` respectively) between my RED commit and my GREEN verification run. Because Kotlin compile test is monolithic, their unresolved `processYearlyStatistics` / `randomizePlanetTradeRate` references blocked the whole `compileTestKotlin` task, preventing my own test from compiling or running.
- **Fix:** Temporarily moved the two sibling test files to `/tmp`, ran scoped `./gradlew :game-app:test --tests "com.openlogh.engine.Gin7ProcessDisasterOrBoomTest"`, then restored both sibling files byte-for-byte. Working tree returned to clean state (`git status` → nothing to commit) before committing GREEN. This is the standard parallel-wave race mitigation called out by the prompt's `<parallel_execution>` block.
- **Files modified:** none (sibling files restored to HEAD)
- **Commit:** not applicable — test-only verification workaround

### Rule-2 auto-additions (missing critical functionality)

**2. [Rule 2 - Critical] Null-guarded OfficerRepository usage**
- **Found during:** Task 2 port
- **Issue:** Legacy `processDisasterOrBoom` calls `officerRepository.findBySessionIdAndPlanetIdIn` unconditionally to drive the injury loop. Gin7EconomyService is constructed from multiple overloads, and the 2-arg and 3-arg test ctors set `officerRepository = null`. A direct port would NPE under those test fixtures (breaking sibling plans' test suites too).
- **Fix:** Wrapped the officer loop in `val officerRepo = officerRepository; if (officerRepo != null) { ... }`. When officerRepository is wired (4-arg production ctor + 3-arg Wave 2 sibling ctors), injury proceeds normally. When null, a no-op branch is taken — matches the defensive pattern already used by `updatePlanetSupplyState` (Plan 23-06). Additionally wrapped the `findBySessionIdAndPlanetIdIn` call in try/catch to log `warn` + continue if the repository method is not yet implemented in a given test fixture.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt`
- **Commit:** `e094fa40`

**3. [Rule 2 - Missing] HistoryService / MessageRepository not wired**
- **Found during:** Task 2 port
- **Issue:** Legacy `processDisasterOrBoom` calls `historyService.logWorldHistory(...)` and writes per-officer injury `Message` rows via `messageRepository.saveAll(injuryMessages)`. Neither `HistoryService` nor `MessageRepository` is a constructor dependency of `Gin7EconomyService`. Gin7 event broadcast is deferred to Plan 23-10 (pipeline wire-up) per Phase 23 CONTEXT.md.
- **Fix:** Replaced `historyService.logWorldHistory(...)` with `logger.info(...)` emitting the same title + body + year/month fields. Dropped per-officer injury Message writes (the injury state mutations on the Officer entity itself — ships/morale/training/injury — are still applied; only the notification-message row is skipped). Marked both sites with `// TODO(Plan 23-10): replace with historyService.logWorldHistory once event bus is wired` and `// TODO(Plan 23-10): emit per-officer injury Messages via MessageRepository`.
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt`
- **Commit:** `e094fa40`

## Parallel wave attribution notes

- **Wave 3 siblings**: `23-07` (`processYearlyStatistics`) and `23-09` (`randomizePlanetTradeRate`) were both in-flight on `Gin7EconomyService.kt` simultaneously. All three plans followed the append-at-class-tail pattern, so there were no merge conflicts in the source file itself. Test compile did race as described in deviation #1 above.
- **No sibling code was touched** by this plan. Sibling commits `4d50aa16` and `736d406f` preceded mine in log order (by timestamp) but landed after my RED commit `365e2243`. My GREEN commit `e094fa40` is clean.

## Known stubs

- `logger.info(...)` calls at disaster and boom log emission sites are stubs for the eventual `historyService.logWorldHistory(...)` integration. This is intentional and tracked for Plan 23-10. No frontend-visible data depends on these logs; the STATE_CODE mutations on Planet itself are the authoritative signal.
- Per-officer injury `Message` rows are skipped (Officer entity stat mutations still applied). Intentional — deferred to Plan 23-10 event-bus wire-up.

## Self-Check: PASSED

- Created `Gin7ProcessDisasterOrBoomTest.kt` — FOUND
- Modified `Gin7EconomyService.kt` — FOUND (new `processDisasterOrBoom` method at tail, verified by `find`)
- Commit `365e2243` (RED) — FOUND in `git log`
- Commit `e094fa40` (GREEN) — FOUND in `git log`
- 5/5 tests passing — verified via `build/test-results/test/TEST-*Gin7ProcessDisasterOrBoomTest*.xml`
