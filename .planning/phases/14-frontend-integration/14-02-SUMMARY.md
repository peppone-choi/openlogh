---
phase: 14-frontend-integration
plan: 02
subsystem: api
tags: [rest, tactical, battle, merit, operation, kotlin, spring, jsonb]

# Dependency graph
requires:
  - phase: 12-operation-integration
    provides: "endBattle ×1.5 participant merit bonus, operationParticipantFleetIds snapshot, computeBaseMerit heuristic"
  - phase: 10-tactical-combat
    provides: "TacticalBattle entity with battleState JSONB column, TacticalBattleService lifecycle"
  - phase: 14-frontend-integration (14-01)
    provides: "BattleSummaryDto / BattleSummaryRow type stubs in TacticalBattleDtos.kt"
provides:
  - "GET /api/v1/battle/{sessionId}/{battleId}/summary REST endpoint"
  - "TacticalBattleService.buildBattleSummary() — reconstructs per-unit merit breakdown from persisted JSONB snapshot"
  - "endBattle() now persists unitSnapshots + operationParticipantFleetIds into TacticalBattle.battleState JSONB (no new column)"
  - "BattleSummaryDto response wire-format: rows[] with baseMerit / operationMultiplier / totalMerit / isOperationParticipant"
affects: [14-18, end-of-battle-modal, frontend-tactical-integration, ops-02-visual-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Existing JSONB column reuse (TacticalBattle.battleState) as append-only snapshot store — avoids Flyway migration for read-model projections"
    - "Controller exception→HTTP mapping: NoSuchElementException→404, IllegalArgumentException→404 (cross-session leak prevention), IllegalStateException→409"
    - "Lightweight Mockito-based REST controller test (mirrors GeneralControllerTest) — avoids SpringBootTest overhead for HTTP contract verification"

key-files:
  created:
    - "backend/game-app/src/test/kotlin/com/openlogh/controller/BattleSummaryEndpointTest.kt"
  modified:
    - "backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt (endBattle snapshot capture + buildBattleSummary method)"
    - "backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleRestController.kt (/summary endpoint)"

key-decisions:
  - "Reuse existing TacticalBattle.battleState JSONB column for unit snapshots — no V48 Flyway migration"
  - "buildBattleSummary re-derives baseMerit from snapshot rather than persisting the computed value — keeps Phase 12 formula single-sourced in computeBaseMerit"
  - "Cross-session access returns 404 (not 403) to mirror existing getBattleState/getBattleHistory semantics and avoid leaking cross-session battle existence"
  - "Test uses plain Mockito per Phase 12 D-17 (mockito-kotlin NOT on classpath) + constructor-injection controller style (mirrors GeneralControllerTest) rather than SpringBootTest"

patterns-established:
  - "Append-only JSONB snapshots for read-model projections: write in endBattle, derive rows at read-time, no schema migration"
  - "REST controller test at unit level: mock the service, assert HTTP status mapping only — faster than SpringBootTest for endpoint contract tests"

requirements-completed: [FE-01]

# Metrics
duration: ~25 min
completed: 2026-04-09
---

# Phase 14 Plan 14-02: Backend BattleSummaryDto + /summary REST endpoint Summary

**REST endpoint exposing per-unit merit breakdown (base + ×1.5 operation multiplier = total) from a persisted JSONB snapshot, so the end-of-battle modal can visually verify Phase 12 OPS-02.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-09T10:25Z (approximate)
- **Completed:** 2026-04-09T10:50Z
- **Tasks:** 1 (TDD — RED + GREEN, no REFACTOR)
- **Files modified:** 3 (DTO already committed by sibling 14-01; Service + Controller + new Test file by this plan)

## Accomplishments

- `GET /api/v1/battle/{sessionId}/{battleId}/summary` endpoint returns a per-unit merit breakdown so the end-of-battle modal (Phase 14 D-32..D-34) can render "기본 X + 작전 +Y = 총 Z" rows.
- Per-unit rows carry `baseMerit`, `operationMultiplier` (1.0 or 1.5), `totalMerit`, and `isOperationParticipant` separately — Phase 12 OPS-02 (×1.5 participant bonus) is now visually verifiable from the UI instead of requiring DB inspection.
- `TacticalBattleService.endBattle()` now captures per-unit snapshots (`fleetId`, `officerId`, `officerName`, `side`, `survivingShips`, `maxShips`) + `operationParticipantFleetIds` into the existing `TacticalBattle.battleState` JSONB column before the officer-update loop. No new DB column / Flyway migration.
- `TacticalBattleService.buildBattleSummary(sessionId, battleId)` reconstructs the merit breakdown from the persisted snapshot, mirroring `computeBaseMerit` and the `endBattle` multiplier logic exactly so what the UI shows equals what was credited to `Officer.meritPoints`.
- Error mapping: `NoSuchElementException`→404, `IllegalArgumentException` (session mismatch)→404, `IllegalStateException` (phase ≠ ENDED)→409.
- Regression: `OperationMeritBonusTest` 4/4 and `TacticalBattleServiceSyncTest` 2/2 still pass after the `endBattle` snapshot-capture addition (Phase 12 Blocker 4 guard is preserved).

## Task Commits

TDD cycle — one feature, two atomic commits (REFACTOR skipped — no cleanup warranted):

1. **Task 1 RED: failing BattleSummary endpoint test** — `7bb96d38` (test)
2. **Task 1 GREEN: BattleSummary REST endpoint with merit breakdown** — `41cd2268` (feat)

**Plan metadata:** `{plan-metadata-hash}` (docs: complete 14-02 plan)

_Note: The `BattleSummaryDto` / `BattleSummaryRow` data class definitions landed in `TacticalBattleDtos.kt` via sibling wave 1 agent commit `a7988a3d` (14-01), which extended the DTO file with Phase 14 types as part of the DTO frontloading work. 14-02's Edit on the DTO file produced no git diff because the class definitions were already present when the Edit ran — the Service and Controller changes are the net contribution of this plan._

## Files Created/Modified

- **Created:** `backend/game-app/src/test/kotlin/com/openlogh/controller/BattleSummaryEndpointTest.kt` — 3-test Mockito-based REST controller test (200 happy path, 404 not found, 409 not ended)
- **Modified:** `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt`
  - `endBattle` now writes `battle.battleState["unitSnapshots"]` + `battle.battleState["operationParticipantFleetIds"]` BEFORE the officer-update loop (so snapshot values mirror exactly what the merit loop sees)
  - New `buildBattleSummary(sessionId, battleId): BattleSummaryDto` method (throws `NoSuchElementException` for missing, `IllegalArgumentException` for session mismatch, `IllegalStateException` for non-ended phase)
- **Modified:** `backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleRestController.kt`
  - New `@GetMapping("/{sessionId}/{battleId}/summary")` endpoint with exception→HTTP mapping
  - Import `HttpStatus` + `BattleSummaryDto`

## Decisions Made

1. **Reuse existing `TacticalBattle.battleState` JSONB column for snapshots** (steered away from plan option (a) which proposed V48 migration).
   - **Rationale:** The column is already JSONB and currently mostly-empty; adding two new map keys is additive, non-breaking, requires no migration, and keeps the tactical-battle table schema stable. The plan explicitly allowed derivation from "an event-log table if one exists" — `battleState` is the de-facto inline event snapshot store.
2. **Re-derive base merit at read-time rather than persist it.** Keeps the Phase 12 merit formula (`computeBaseMerit` heuristic: `(100 * ships/maxShips).coerceAtLeast(10)` on winning side, 0 otherwise) single-sourced in `TacticalBattleService` so a future rule change updates both write and read paths via the same code.
3. **Cross-session access returns 404, not 403.** Mirrors existing `getBattleState` / `getBattleHistory` semantics and avoids leaking cross-session battle existence to clients.
4. **Test uses plain Mockito (not SpringBootTest).** Per Phase 12 D-17 (mockito-kotlin NOT on `:game-app` classpath) and mirroring the lightweight `GeneralControllerTest` pattern. The REST controller is a pure delegator, so mocking the service and asserting HTTP status + body structure covers the full contract without Spring context overhead.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] JAVA_HOME pointing to JDK 25 caused Gradle build failure**
- **Found during:** Task 1 RED verification (initial `./gradlew compileTestKotlin` failed with opaque `25.0.2` error)
- **Issue:** Default system JDK was Temurin 25.0.2, incompatible with Gradle 8.12 + Kotlin Gradle plugin (which expect JVM 17/21). Root error was masked by the toolchain.
- **Fix:** Set `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` for all subsequent Gradle invocations.
- **Files modified:** None (environment-only fix).
- **Verification:** Subsequent `:shared:compileKotlin` and `:game-app:compileKotlin` succeeded under JDK 21.
- **Committed in:** n/a (environment fix, not code)

**2. [Rule 3 - Blocking] Kotlin daemon mangles Hangul paths to `uXXXX` escapes**
- **Found during:** Task 1 RED verification (after fixing Java version, Kotlin daemon emitted `source file or directory not found: /Users/apple/Desktop/uAC1CuC778...` — the project path `/Users/apple/Desktop/개인프로젝트/openlogh/` gets mangled).
- **Issue:** Kotlin compiler daemon has a known encoding bug where non-ASCII file paths passed through `ProcessBuilder` become Java unicode escape sequences (`u` prefix) instead of literal Hangul. This is a pre-existing environment issue, not caused by 14-02's code.
- **Fix:** Use `-Dkotlin.compiler.execution.strategy=in-process` on every Gradle invocation — bypasses the daemon and runs the Kotlin compiler in the Gradle worker JVM where the encoding is honored.
- **Files modified:** None (flag added to build invocation only).
- **Verification:** With `in-process`, `:shared:compileKotlin` + `:game-app:compileKotlin` + `:game-app:compileTestKotlin` + `:game-app:test` all succeed.
- **Committed in:** n/a (environment fix, not code)

**3. [Rule 1 - Plan Drift] Plan's `battle.result` casing was wrong**
- **Found during:** Task 1 GREEN implementation (reading `endBattle` to mirror the merit formula)
- **Issue:** Plan's example code used uppercase strings `"ATTACKER_WIN"` / `"DEFENDER_WIN"` / `"DRAW"` for `battle.result`. Actual `endBattle` writes lowercase `"attacker_win"` / `"defender_win"` / `"draw"` (TacticalBattleService.kt:354).
- **Fix:** `buildBattleSummary` uses the lowercase casing in the `when` expression that maps `battle.result` back to `BattleSide`. Test fixture also uses lowercase `"attacker_win"`.
- **Files modified:** `TacticalBattleService.kt`, `BattleSummaryEndpointTest.kt`
- **Verification:** Test `returns 200 with merit breakdown for ended battle` asserts `body.winner == "attacker_win"` and passes.
- **Committed in:** `41cd2268`

**4. [Rule 1 - Plan Drift] Plan's `@RequestMapping` prefix was wrong**
- **Found during:** Task 1 GREEN implementation (reading `TacticalBattleRestController`)
- **Issue:** Plan's example said `/api/{sessionId}/battles/{battleId}/summary`. Existing controller uses `@RequestMapping("/api/v1/battle")` (singular, v1 prefix) with per-method `/{sessionId}/{battleId}/history`.
- **Fix:** Match existing convention — new endpoint is `@GetMapping("/{sessionId}/{battleId}/summary")` under the same class-level `/api/v1/battle` prefix. Final URL: `GET /api/v1/battle/{sessionId}/{battleId}/summary`.
- **Files modified:** `TacticalBattleRestController.kt`
- **Verification:** Test invokes `controller.getBattleSummary(sessionId, battleId)` directly (mocking the path doesn't require URL parsing).
- **Committed in:** `41cd2268`

---

**Total deviations:** 4 auto-fixed (2 blocking environment, 2 plan drift). **Impact on plan:** None — all four were required for the plan to execute at all, and none changed the intended behavior. The plan's algorithmic intent (reconstruct per-unit merit breakdown from persisted data, expose via REST, return 200/404/409) was preserved exactly; only URL casing, result-string casing, and build tooling flags differed from the plan example code.

## Issues Encountered

- **Gradle + Kotlin toolchain compatibility**: The project path contains Hangul characters (`개인프로젝트`) which triggers a Kotlin compiler daemon encoding bug, and the default system JDK (Temurin 25) is incompatible with Gradle 8.12. Both were resolved by setting `JAVA_HOME` to Temurin 21 and passing `-Dkotlin.compiler.execution.strategy=in-process`. These are pre-existing environment issues unrelated to 14-02's scope and should be documented once for future plan executors in the project's onboarding notes.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- The backend contract (`BattleSummaryDto` + `/summary` endpoint) is ready for Plan 14-18 (end-of-battle modal) to call.
- Frontend will need to add `BattleSummary` types to `frontend/src/types/tactical.ts` and a fetch helper; this is explicitly scoped to 14-18 per phase plan ordering.
- **Blocker / concern:** `buildBattleSummary` only works for battles ENDED AFTER the `endBattle` snapshot capture was added. Any pre-existing ENDED battle in the database will have `battleState["unitSnapshots"]` absent and `buildBattleSummary` will return an empty `rows[]`. In practice this is a non-issue for Phase 14 demo scenarios (tests run against fresh battles), but production backfill of historical battles is out of scope.

## Self-Check

Below verifications were performed before submitting this summary:

- ✓ `grep -n "data class BattleSummaryDto" backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt` → line 256
- ✓ `grep -n "data class BattleSummaryRow" backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt` → line 237
- ✓ `grep -n "fun buildBattleSummary" backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` → line 800
- ✓ `grep -n "getBattleSummary" backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleRestController.kt` → line 77
- ✓ `grep -n '"/{sessionId}/{battleId}/summary"' backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleRestController.kt` → line 76
- ✓ `./gradlew :game-app:test --tests "com.openlogh.controller.BattleSummaryEndpointTest"` → `tests="3" skipped="0" failures="0" errors="0"`
- ✓ Regression: `./gradlew :game-app:test --tests "OperationMeritBonusTest" "TacticalBattleServiceSyncTest"` → 6/6 tests pass
- ✓ Commits exist: `git log --oneline | grep 14-02` shows `7bb96d38` (test) + `41cd2268` (feat)

## Self-Check: PASSED

---

*Phase: 14-frontend-integration*
*Completed: 2026-04-09*
