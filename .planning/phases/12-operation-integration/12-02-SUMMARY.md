---
phase: 12-operation-integration
plan: 02
subsystem: command-service
tags: [kotlin, spring-boot, jpa, transactional, command-pattern, junit5]

# Dependency graph
requires:
  - phase: 12-01
    provides: "OperationPlan entity, OperationStatus enum, OperationPlanRepository, MissionObjective.defaultForPersonality"
provides:
  - "OperationPlanService (@Service + @Transactional) — sole transactional boundary for OperationPlan CRUD; enforces D-04 1-fleet-1-operation atomically"
  - "CommandServices.operationPlanService (nullable) — dependency wire for command layer access"
  - "OperationPlanCommand rewrite — delegates to OperationPlanService.assignOperation, stub nation.meta[\"operationPlan\"] removed"
  - "OperationCancelCommand rewrite — delegates to OperationPlanService.cancelOperation, stub nation.meta.remove removed"
  - "WarpNavigationCommand Fleet.planetId fix — mirrors IntraSystemNavigationCommand.kt:44 so OPS-03 arrival detection works for cross-system operations"
affects: [12-03-engine-state, 12-04-lifecycle-service, 13-strategic-ai]

# Tech tracking
tech-stack:
  added: []  # Reuses existing spring-tx + spring-data-jpa
  patterns:
    - "Dedicated @Transactional service as atomicity boundary when calling layer (CommandExecutor) is not transactional"
    - "Optional nullable constructor param pattern for backward-compatible dependency injection in CommandServices/CommandExecutor"
    - "Atomic multi-entity invariant enforcement: read priors → mutate in place → saveAll priors → save new — all under one @Transactional scope"

key-files:
  created:
    - "backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/service/OperationPlanServiceTest.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommandTest.kt"
  modified:
    - "backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommand.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt"

key-decisions:
  - "OperationPlanService owns ALL OperationPlan persistence — commands never touch OperationPlanRepository directly. CommandExecutor has no @Transactional annotation (verified), so this service is the sole atomicity boundary."
  - "operationPlanService wired via OPTIONAL nullable field in CommandServices and CommandExecutor (same pattern as existing fleetRepository) so pre-existing InMemoryTurnHarness / CommandProposalServiceTest / 8 CommandServices(...) test construction sites continue to compile unchanged."
  - "issuedAtTick always 0L in Phase 12 (documented limitation per must_haves). No tick accessor exists at command dispatch time. OPS-03 semantics do not depend on this value."
  - "Sync channel wiring (TacticalBattleService.syncOperationToActiveBattles call) deferred to Plan 12-04 Task 2b — the target method is created by Plan 12-03 which runs in parallel to this plan."
  - "cancelOperation is idempotent-safe for already-CANCELLED status (early return) but rejects COMPLETED via check()."

requirements-completed: [OPS-03]

# Metrics
duration: 8min
completed: 2026-04-09
---

# Phase 12 Plan 02: Operation Command Rewrite + WarpNavigation Fix Summary

**OperationPlanService @Transactional boundary enforcing D-04 1-fleet-1-operation atomically, plus OperationPlan/OperationCancel command rewrites delegating to the service, plus one-line WarpNavigationCommand Fleet.planetId fix that unblocks OPS-03 arrival detection for cross-system operations**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-04-09T03:07:11Z
- **Completed:** 2026-04-09T03:14:47Z
- **Tasks:** 2 (both TDD RED → GREEN)
- **Files created:** 3 (1 service + 2 test classes)
- **Files modified:** 5 (WarpNavigationCommand, CommandServices, CommandExecutor, OperationPlanCommand, OperationCancelCommand)

## Accomplishments

- **`OperationPlanService`** created as the sole transactional boundary for OperationPlan CRUD. Two `@Transactional` methods:
  - `assignOperation(...)` enforces D-04 atomically: (a) validates `participantFleetIds` non-empty, (b) loads fleets via `fleetRepository.findAllById`, (c) rejects missing fleets or foreign-faction fleets, (d) reads every PENDING/ACTIVE op of the faction via `findBySessionIdAndFactionIdAndStatusIn`, (e) removes the new fleets from every prior op's `participantFleetIds`, (f) saves mutated priors via `saveAll`, (g) constructs + saves the new `OperationPlan`. All seven steps share one JTA tx — failure at any step rolls back the whole operation.
  - `cancelOperation(factionId, operationId)` looks up the op, checks faction ownership (`check(...)` → `IllegalStateException`), rejects COMPLETED status, early-returns on already-CANCELLED (idempotent), flips status + `updatedAt`, saves.
- **`OperationPlanCommand` rewrite** — delegates to `services.operationPlanService?.assignOperation(...)`. Parses 5 arg types (objective enum, targetStarSystemId, participantFleetIds list, scale, planName). Catches `IllegalArgumentException` and returns `CommandResult.fail(e.message)`. Stub `nation.meta["operationPlan"]` entirely removed.
- **`OperationCancelCommand` rewrite** — delegates to `cancelOperation(factionId, operationId)`. Catches both `NoSuchElementException` and `IllegalStateException`. Stub `nation.meta.remove("operationPlan")` entirely removed.
- **`CommandServices`** — added `operationPlanService: OperationPlanService? = null` as the 10th field. All 8+ existing test construction sites and the 2 CommandExecutor production construction sites continue to compile unchanged (positional-args omit the new trailing nullable field).
- **`CommandExecutor`** — added `OperationPlanService? = null` as the 12th primary constructor parameter AND threaded `operationPlanService = operationPlanService` through BOTH `CommandServices(...)` construction sites (officer command path at L110-121, faction command path at L218-230). Secondary constructor at L45-73 intentionally unchanged so `InMemoryTurnHarness.commandExecutor = CommandExecutor(...)` tests stay green.
- **`WarpNavigationCommand`** — single-line fix: added `troop?.planetId = destPlanetId` between `general.planetId = destPlanetId` and `pushLog(...)`, mirroring `IntraSystemNavigationCommand.kt:44` exactly. The bug was pre-existing (Phase 11 or earlier) and is what the 12-VALIDATION checker surfaced as "OPS-03 arrival detection broken for cross-system operations".
- **8 new tests, all passing:**
  - `WarpNavigationCommandTest` — 2 tests (0.131s + 0.002s): `updates_fleet_planet_id()` + `warp without fleet still updates officer()`.
  - `OperationPlanServiceTest` — 6 tests (0.929s + 0.046s + 0.013s + 0.024s + 0.025s + 0.017s): persistence, atomic D-04 fleet release, empty participant rejection, foreign faction rejection, cancel happy path, cross-faction cancel rejection.

## Task Commits

Each task was committed atomically with `--no-verify` (parallel-executor requirement):

1. **Task 1: WarpNavigationCommand Fleet.planetId fix + regression test** — `efe1d729` (fix)
2. **Task 2: OperationPlanService + command rewrites + wiring** — `deec74f1` (feat)

**Plan metadata commit:** pending (will include SUMMARY.md, STATE.md, ROADMAP.md, REQUIREMENTS.md)

## Files Created/Modified

### Created

- `backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt` (143 lines) — `@Service` class with `assignOperation` + `cancelOperation` both `@Transactional`. Injects `OperationPlanRepository` + `FleetRepository`. Uses `findBySessionIdAndFactionIdAndStatusIn` for D-04 prior-op lookup and `fleetRepository.findAllById` for faction ownership validation.
- `backend/game-app/src/test/kotlin/com/openlogh/service/OperationPlanServiceTest.kt` (152 lines) — `@SpringBootTest(classes = [OpenloghApplication::class])` + `@ActiveProfiles("test")` + `@Transactional` (H2 auto-rollback). 6 tests exercising all assignOperation and cancelOperation paths. Mirrors the 12-01 `OperationPlanRepositoryTest` Spring context pattern to avoid the duplicate `@SpringBootConfiguration` issue with `OpenloghApplicationTests$TestConfig`.
- `backend/game-app/src/test/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommandTest.kt` (63 lines) — pure unit test (no Spring), uses `runBlocking` for the suspend run(), constructs `CommandEnv(year=800, month=1, startYear=790, sessionId=1L)` per the verified CommandEnv signature (no `day` field).

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommand.kt` — added 5 lines (1 code + 4 comment) between `general.planetId = destPlanetId` and `pushLog(...)`: `troop?.planetId = destPlanetId`. No other changes.
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt` — added `import com.openlogh.service.OperationPlanService` and `val operationPlanService: OperationPlanService? = null` as the 10th field.
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` — added `import com.openlogh.service.OperationPlanService`, added `private val operationPlanService: OperationPlanService? = null,` as the 12th primary-constructor parameter, and added `operationPlanService = operationPlanService` to both `CommandServices(...)` construction sites (officer command + faction command paths). The secondary constructor is intentionally unchanged — it already threads all `null` defaults to the primary via `this(...)`.
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` — full rewrite. Adds imports for `MissionObjective`. Parses 5 arg types, delegates to `opService.assignOperation(...)`, catches `IllegalArgumentException` → `CommandResult.fail`. Stub `nation.meta["operationPlan"]` entirely removed.
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt` — full rewrite. Parses `operationId`, delegates to `opService.cancelOperation(...)`, catches both `NoSuchElementException` and `IllegalStateException`. Stub `nation.meta.remove("operationPlan")` entirely removed.

## Decisions Made

- **Service owns ALL persistence:** Commands never import `OperationPlanRepository`. This is the only way to guarantee D-04 atomicity because `CommandExecutor` is not `@Transactional` — if commands mutated the repo directly, concurrent invocations would race and a failed save after the new insert would leave dual-membership orphans. The service pattern forces every mutation through one `@Transactional` funnel.
- **Nullable-default field on CommandServices:** Chosen over making the field required because 8+ pre-existing `CommandServices(...)` test construction sites (NationCommandTest, NationResourceCommandTest, CommandParityTest, NationResearchSpecialCommandTest, GeneralMilitaryCommandTest, GeneralPoliticalCommandTest, NationDiplomacyStrategicCommandTest) use named+positional args and would ALL need touched if the new field were required. The nullable default keeps Task 2 scope minimal and mirrors the existing `fleetRepository: FleetRepository? = null` precedent.
- **Nullable-default param on CommandExecutor primary constructor:** Same rationale — `InMemoryTurnHarness` and `SuccessCommandExecutor` (CommandProposalServiceTest) both construct CommandExecutor via positional args and would break. The secondary constructor at L45-73 is intentionally NOT updated — it bridges to the primary with explicit field-by-field passing, so the new nullable field defaults to `null` when called from Java/test harnesses.
- **`issuedAtTick = 0L` in Phase 12:** Documented in must_haves per the checker's Blocker 2. `CommandEnv` has no `tickCount` or `day` field (verified), and `TickEngine`'s tick counter isn't plumbed through CommandExecutor → OfficerCommand. Plan 12-04 / 12-05 may retrofit the tick accessor; for now OPS-03 semantics do not read this field.
- **Sync channel NOT wired here:** Per Plan 12-02 objective note + checker Blocker 3, the sync channel wiring belongs in Plan 12-04 Task 2b because `TacticalBattleService.syncOperationToActiveBattles(...)` is created by the parallel Plan 12-03 agent and is not yet on disk from 12-02's perspective. A comment is left in both commands pointing at 12-04 Task 2b.
- **No REFACTOR phase needed:** GREEN code is already minimal and mirrors the plan's `<action>` spec exactly. Skipping refactor to avoid touching working code.

## Deviations from Plan

### Auto-fixed Issues

None. Plan 12-01 already surfaced the three environment blockers (Java 25 → 17, `-x ktlintCheck` removed, `@SpringBootTest classes = [OpenloghApplication::class]`), and this plan applied them preemptively from the start. Task 1 RED and Task 2 RED both failed for the **exact** reason predicted by the plan (Fleet.planetId assertion for Task 1; `Unresolved reference: OperationPlanService` for Task 2). GREEN phase for both tasks succeeded on first try with the source snippets from the plan's `<action>` blocks.

### Out-of-scope issues logged (not fixed)

**1. Sibling Plan 12-03 test files blocking `compileTestKotlin` for full-suite runs**
- **Found during:** Task 2 final verification (attempted full-suite rerun)
- **Issue:** The parallel Plan 12-03 executor agent wrote two untracked test files during my execution window:
  - `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/BattleTriggerOperationInjectionTest.kt` — references `operationPlanRepository` as a constructor param of `BattleTriggerService` (a parameter that doesn't exist yet because 12-03's source-side changes aren't committed yet)
  - `backend/game-app/src/test/kotlin/com/openlogh/service/TacticalBattleServiceSyncTest.kt` — references `TacticalBattleService.syncOperationToActiveBattles` (a method that doesn't exist yet)
- **Why out-of-scope:** Per `<parallel_execution>` and `<phase_context>`, Plan 12-02 "does NOT depend on 12-03's work" and "DO NOT touch tactical engine files or TacticalBattleService in this plan; that's 12-03's scope". The `<parallel_execution>` block explicitly states the orchestrator validates hooks once after the wave completes — not each agent individually.
- **Evidence my code is clean:** `./gradlew :game-app:compileKotlin` (main sources only) returns BUILD SUCCESSFUL after all Task 2 changes. The targeted `./gradlew :game-app:test --tests "com.openlogh.service.OperationPlanServiceTest" --tests "com.openlogh.command.gin7.operations.WarpNavigationCommandTest"` run earlier (before 12-03's test files landed on disk at 12:12-12:13) also returned BUILD SUCCESSFUL with 8/8 tests green. See XML reports:
  - `TEST-com.openlogh.service.OperationPlanServiceTest.xml` — `tests="6" skipped="0" failures="0" errors="0"`
  - `TEST-com.openlogh.command.gin7.operations.WarpNavigationCommandTest.xml` — `tests="2" skipped="0" failures="0" errors="0"`
- **Action:** None. Out of scope per parallel-execution contract. The wave-level validation after 12-03 commits its source-side changes will compile everything together.

**2. Pre-existing 207-test legacy Three Kingdoms city-name drift**
- Documented in `.planning/phases/12-operation-integration/deferred-items.md` by Plan 12-01. Not touched by this plan.

---

**Total deviations:** 0 auto-fixed issues in my code; 2 out-of-scope items logged (sibling-agent + pre-existing legacy).

**Impact on plan:** None. All plan tasks executed exactly as written.

## Authentication Gates

None encountered. No external services, no API keys needed.

## Issues Encountered

See "Out-of-scope issues logged" above. Both items are pre-existing or sibling-owned; neither was caused by this plan's changes.

## Known Stubs

None in my code. The PLAN explicitly calls out that `TacticalBattleService.syncOperationToActiveBattles(plan)` is NOT wired here — two comments in OperationPlanCommand and OperationCancelCommand document this as a Plan 12-04 Task 2b deliverable, not a stub. The method doesn't exist yet (Plan 12-03 creates it), so there is no callable target to wire. This is documented, intentional, and has a named resolver (Plan 12-04).

## Next Phase Readiness

Plan 12-02 unblocks:

- **Plan 12-04 (Lifecycle Service):** Can inject `OperationPlanService` directly to avoid going through the command layer for system-triggered state changes (e.g., PENDING → ACTIVE on arrival, ACTIVE → COMPLETED on victory). The service's `cancelOperation` pattern can be mirrored for a future `activateOperation` / `completeOperation`. Plan 12-04 Task 2b will also wire the sync channel: import `TacticalBattleService` (from 12-03) into both commands and call `syncOperationToActiveBattles(plan)` after the service delegate returns success.
- **Plan 12-03 (Engine State, running in parallel):** Has no dependency on 12-02 — 12-03 operates on the tactical battle state and will use `OperationPlanRepository.findBySessionIdAndStatus` (from 12-01) directly.
- **OPS-03 arrival detection:** WarpNavigationCommand now updates `Fleet.planetId` atomically with `Officer.planetId`, so when Plan 12-04 checks `fleet.planetId == operation.targetStarSystemId`, it will receive fresh data. Without this fix, ~90% of operations (all cross-system) would silently never trigger PENDING→ACTIVE.

**Cross-references for downstream agents:**

- `services.operationPlanService` is nullable — always check `?:` before calling. Production `CommandExecutor` always passes the Spring-managed bean, but unit tests with hand-constructed `CommandServices(...)` get `null`. Both `OperationPlanCommand` and `OperationCancelCommand` return `CommandResult.fail("OperationPlanService unavailable")` in that case.
- `OperationPlanService.assignOperation` throws `IllegalArgumentException` (on validation) which commands convert to `CommandResult.fail`. Downstream services calling the service directly should catch this.
- `OperationPlanService.cancelOperation` throws BOTH `NoSuchElementException` (missing op) AND `IllegalStateException` (wrong faction / already COMPLETED). Idempotent on already-CANCELLED.
- The `issuedAtTick = 0L` limitation is per-Phase-12, not a permanent design. If a later phase adds tick plumbing (e.g., injecting `TickEngine` into `CommandEnv`), that phase can update `OperationPlanService.assignOperation` signature to accept `issuedAtTick: Long` explicitly — all existing call sites pass `0L` today.

## Self-Check: PASSED

**Files verified to exist:**
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt`
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/service/OperationPlanServiceTest.kt`
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommandTest.kt`
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommand.kt` (modified, contains `troop?.planetId = destPlanetId`)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt` (modified, contains `operationPlanService: OperationPlanService`)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` (modified, contains `operationPlanService: OperationPlanService? = null` and two `operationPlanService = operationPlanService` pass-throughs)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` (rewritten, contains `services.operationPlanService` and `opService.assignOperation(`)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt` (rewritten, contains `opService.cancelOperation(`)

**Commits verified:**
- FOUND: `efe1d729` — fix(12-02): update Fleet.planetId after warp navigation
- FOUND: `deec74f1` — feat(12-02): add OperationPlanService transactional boundary (D-04)

**Test results verified (from isolated run before sibling-agent race):**
- FOUND: `build/test-results/test/TEST-com.openlogh.service.OperationPlanServiceTest.xml` — `tests="6" skipped="0" failures="0" errors="0"`
- FOUND: `build/test-results/test/TEST-com.openlogh.command.gin7.operations.WarpNavigationCommandTest.xml` — `tests="2" skipped="0" failures="0" errors="0"`

**Acceptance criteria verified (grep-confirmed on disk):**
- `@Service` + `@Transactional` (×2) in OperationPlanService.kt
- `findBySessionIdAndFactionIdAndStatusIn` in assignOperation
- `participantFleetIds.toSet()` in assignOperation
- `issuedAtTick = 0L` in assignOperation
- `services.operationPlanService` + `opService.assignOperation(` in OperationPlanCommand.kt
- `opService.cancelOperation(` in OperationCancelCommand.kt
- No `nation.meta["operationPlan"]` nor `nation.meta.remove` anywhere in the command files
- `operationPlanService: OperationPlanService? = null` in CommandExecutor.kt primary constructor
- `operationPlanService = operationPlanService` appears twice in CommandExecutor.kt (both CommandServices construction sites)

**Main-source compile verified:**
- `./gradlew :game-app:compileKotlin` — BUILD SUCCESSFUL after all Task 2 changes.

---
*Phase: 12-operation-integration*
*Plan: 02*
*Completed: 2026-04-09*
