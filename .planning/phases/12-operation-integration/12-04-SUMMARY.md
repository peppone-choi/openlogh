---
phase: 12-operation-integration
plan: 04
subsystem: service-engine
tags: [kotlin, spring-boot, transactional, tick-engine, tactical-battle, mockito, tdd]

# Dependency graph
requires:
  - phase: 12-01
    provides: "OperationPlan entity, OperationStatus enum, OperationPlanRepository, MissionObjective.defaultForPersonality"
  - phase: 12-02
    provides: "OperationPlanService @Transactional boundary, OperationPlanCommand + OperationCancelCommand rewrites, WarpNavigationCommand Fleet.planetId fix, operationPlanService wired into CommandServices + CommandExecutor"
  - phase: 12-03
    provides: "TacticalBattleState.missionObjectiveByFleetId (ConcurrentHashMap), TacticalBattleState.operationParticipantFleetIds (ConcurrentHashMap-backed Set), TacticalBattleEngine Step 0.6 read-through, BattleTriggerService.buildInitialState operation population, TacticalBattleService.syncOperationToActiveBattles direct-call sync channel"
provides:
  - "OperationLifecycleService (@Service + @Transactional) — D-15 activation (PENDING→ACTIVE on arrival) + D-16/D-17/D-18 completion evaluation (CONQUEST/DEFENSE/SWEEP)"
  - "DEFENSE_STABILITY_TICKS = 60 const (D-18 stability window)"
  - "TickEngine Step 5.5 — operationLifecycleService.processTick(sessionId, tickCount) runs BEFORE tacticalBattleService.processSessionBattles, wrapped in try/catch for isolation"
  - "TacticalBattleService.endBattle participantSnapshot race guard (Blocker 4) — `val participantSnapshot: Set<Long> = state.operationParticipantFleetIds.toSet()` captured BEFORE the unit loop"
  - "TacticalBattleService.endBattle inline ×1.5 merit bonus for operation participants — FIRST `officer.meritPoints +=` accumulation path in the codebase (RankLadderService.kt:124/143 are RESETS only)"
  - "TacticalBattleService.computeBaseMerit private helper — winning-side survival-ratio formula (baseMerit=100 on full survival, 0 on loss/draw)"
  - "CommandServices.tacticalBattleService: TacticalBattleService? = null — nullable dependency wire alongside operationPlanService"
  - "CommandExecutor constructor 13th parameter — tacticalBattleService threaded into both executeOfficerCommand and executeFactionCommand CommandServices construction sites"
  - "OperationPlanCommand sync channel call — services.tacticalBattleService?.syncOperationToActiveBattles(plan) after assignOperation success"
  - "OperationCancelCommand sync channel call — services.tacticalBattleService?.syncOperationToActiveBattles(cancelled) after cancelOperation success"
affects: [13-strategic-ai]

# Tech tracking
tech-stack:
  added: []  # No new dependencies — reuses existing spring-tx + Mockito + JUnit 5 + H2 + AopTestUtils
  patterns:
    - "Lifecycle-service owned state transitions: @Service + @Transactional wrapping activate + evaluate in one tick call so DB mutations roll back together on failure"
    - "Snapshot-before-iterate race guard: `val snapshot: Set<Long> = concurrentSet.toSet()` captured at the start of a long-running loop to prevent mid-loop mutation from concurrent writer threads"
    - "DEFENSE stability counter persisted even when threshold not reached — state mutation crosses tick boundaries"
    - "runCatching around sync-channel calls in lifecycle service — sync failures log but do not roll back the status change (next tick re-propagates via Step 0.6 read-through)"
    - "AopTestUtils.getTargetObject(proxy) to unwrap Spring CGLIB proxies for reflection on private fields in @SpringBootTest integration tests (activeBattles field on TacticalBattleService)"
    - "@SpringBootTest(classes = [OpenloghApplication::class]) + @ActiveProfiles(\"test\") + @Transactional integration-test fixture with TacticalBattleRepository.saveAndFlush to pre-persist TacticalBattle before endBattle's internal save"

key-files:
  created:
    - "backend/game-app/src/main/kotlin/com/openlogh/service/OperationLifecycleService.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/service/OperationMeritBonusTest.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineOrderingTest.kt"
  modified:
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineTest.kt"
    - ".planning/phases/12-operation-integration/deferred-items.md"

key-decisions:
  - "OperationLifecycleService owns ALL lifecycle persistence — NOT TickEngine. @Transactional wraps both activate + evaluate in one call so DB rolls back together on failure"
  - "DEFENSE stability counter mutation MUST persist when threshold is not yet reached (via explicit save in evaluateCompletion else-branch). Without this, the counter would reset to 0 every tick and DEFENSE would never complete"
  - "Snapshot-before-iterate pattern for operationParticipantFleetIds in endBattle is the ONLY correct race guard — live set iteration would produce non-deterministic bonus assignment on concurrent CANCELLED sync from command threads"
  - "Merit += path uses `officer.meritPoints += awarded` — this is the FIRST accumulation path. RankLadderService.kt:124/143 are RESETS (`= MERIT_AFTER_PROMOTION/DEMOTION`) not accumulations. Any future merit sources should follow this += pattern"
  - "computeBaseMerit uses winning-side survival ratio: baseMerit = (100 * ships/maxShips).toInt().coerceAtLeast(10). Full survival + winning side = 100 exactly (OperationMeritBonusTest fixtures depend on this)"
  - "CommandServices.tacticalBattleService is nullable default-null for backward compat with the 8+ pre-existing CommandServices test construction sites that use positional args — mirrors the 12-02 operationPlanService pattern"
  - "TickEngine Step 5.5 uses try/catch (not rethrow) so a lifecycle failure does not block tactical battle processing or downstream tick steps"
  - "OperationMeritBonusTest must pre-persist both the TacticalBattle (for endBattle's save) AND the Fleet (for endBattle's findById-or-continue loop body). Seeded via saveAndFlush with auto-generated IDs"
  - "OperationMeritBonusTest uses AopTestUtils.getTargetObject to unwrap the Spring CGLIB proxy of TacticalBattleService before reflecting on the private activeBattles field — proxies hold null for inherited instance fields"

patterns-established:
  - "Lifecycle service at Step 5.5: any future 'between politics and battle' system should follow OperationLifecycleService's @Service + @Transactional + tick-loop pattern"
  - "Participant snapshot race guard: any concurrent set that flows into a long-running @Transactional loop must be snapshotted to `.toSet()` at the loop header"
  - "Merit accumulation via += (not =): the codebase now has its first canonical accumulation path for Officer.meritPoints. RankLadderService resets remain as-is but any new merit sources should use +="
  - "AopTestUtils for private-field reflection in @SpringBootTest: Spring proxies subclasses hold null for inherited instance fields, so reflection must target the unwrapped target object"

requirements-completed: [OPS-01, OPS-02, OPS-03]

# Metrics
duration: 10min
completed: 2026-04-09
---

# Phase 12 Plan 04: Operation Lifecycle + Merit Bonus + Sync Wiring Summary

**OperationLifecycleService (PENDING→ACTIVE on arrival + CONQUEST/DEFENSE/SWEEP completion), TickEngine Step 5.5, inline ×1.5 merit bonus in TacticalBattleService.endBattle with participantSnapshot race guard (first += merit accumulation in the codebase), and OperationPlan/OperationCancel command sync-channel wiring — closes OPS-01, OPS-02, OPS-03 and completes Phase 12 end-to-end.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-04-09T03:33:00Z
- **Completed:** 2026-04-09T03:45:00Z
- **Tasks:** 3 (all tdd="true")
- **Commits:** 3 task commits + 1 metadata commit (pending)
- **Files created:** 4 (1 service source + 3 test classes)
- **Files modified:** 8 (TickEngine + TacticalBattleService + CommandServices + CommandExecutor + OperationPlanCommand + OperationCancelCommand + TickEngineTest regression + deferred-items.md)

## Accomplishments

- **OperationLifecycleService (Task 1):** New @Service + @Transactional class wraps `activatePending` + `evaluateCompletion` in one `processTick(sessionId, tickCount)` entry point. D-15 activation keys on `fleet.planetId == operation.targetStarSystemId` for ANY participant fleet (one arriving is sufficient — D-17). D-16 completion evaluates 3 objective types exhaustively:
  - **CONQUEST:** `StarSystem.findById(targetStarSystemId).factionId == operation.factionId` (ownership flip)
  - **DEFENSE:** `stabilityTickCounter` increments on enemy-free ticks, resets to 0 on enemy presence, triggers COMPLETED at `DEFENSE_STABILITY_TICKS = 60` (D-18). Counter mutation is persisted on every tick via the else-branch save so state crosses tick boundaries.
  - **SWEEP:** `fleetRepository.findByPlanetId(target).count { it.factionId != operation.factionId } == 0` (no enemies remaining)
  - Successful PENDING→ACTIVE and ACTIVE→COMPLETED transitions call `tacticalBattleService.syncOperationToActiveBattles(operation)` wrapped in `runCatching { ... }.onFailure { logger.warn(...) }` so sync failures log but do not roll back the status change.
- **TickEngine Step 5.5 (Task 1):** `operationLifecycleService.processTick(world.id.toLong(), world.tickCount)` inserted between Step 5 (processPolitics) and Step 6 (tacticalBattleService.processSessionBattles) wrapped in try/catch. New 15th constructor parameter (`operationLifecycleService: OperationLifecycleService`) added at the END of the parameter list for backward-compat with the pre-existing TickEngineTest construction site.
- **Merit bonus + snapshot guard (Task 2a):** `TacticalBattleService.endBattle` now captures `val participantSnapshot: Set<Long> = state.operationParticipantFleetIds.toSet()` BEFORE the unit loop (Blocker 4 race guard from 12-VALIDATION). Inside the loop, after the existing ships/morale update, the code computes `baseMerit = computeBaseMerit(unit, outcome)` then applies `multiplier = if (participantSnapshot.contains(unit.fleetId)) 1.5 else 1.0` and mutates `officer.meritPoints += (baseMerit * multiplier).toInt()`. The new `computeBaseMerit` private helper uses a simple winning-side survival-ratio formula: `(100 * ships/maxShips).toInt().coerceAtLeast(10)`, returning 0 for losers/draws. Full-ship survival on the winning side yields exactly 100 (tests assert concretely on this).
- **Sync channel wiring from commands (Task 2b):** `CommandServices` gains `tacticalBattleService: TacticalBattleService? = null` as the 11th nullable field. `CommandExecutor` primary constructor gains a 13th `tacticalBattleService: TacticalBattleService? = null` parameter, threaded through BOTH CommandServices construction sites (executeOfficerCommand L112-122 + executeFactionCommand L222-232). `OperationPlanCommand.run()` now calls `services.tacticalBattleService?.syncOperationToActiveBattles(plan)` after the successful `assignOperation` delegate returns; `OperationCancelCommand.run()` mirrors the pattern with `cancelled`. Both calls are nullable-safe for unit tests that don't load the full Spring context.
- **Test coverage (Tasks 1+2a+2b):** 11 new tests, 0 pre-existing tests broken:
  - `OperationLifecycleServiceTest` — 6 tests: activates_on_first_arrival, remains_pending_if_none_arrived, conquest_completion_when_target_faction_matches, defense_stability_window_requires_60_consecutive_clean_ticks (59 → 60 boundary), defense_counter_resets_on_enemy_presence, sweep_completion_when_enemies_at_target_drop_to_zero
  - `TickEngineOrderingTest` — 1 test: operation_lifecycle_runs_before_tactical_battle_processing (Mockito InOrder verification)
  - `OperationMeritBonusTest` — 4 @SpringBootTest integration tests: participant_receives_1_5x_merit_bonus (==150 exactly), non_participant_gets_base_merit_not_bonus (==100 exactly), cancelled_mid_battle_removes_bonus (==100 after sync removes fleet), snapshot_prevents_concurrent_cancel_race (both officers must receive identical merit)
  - Pre-existing `TickEngineTest` regression — 13/13 tests still pass after adding the new constructor parameter (fixed the hand-constructed test site with `mock(OperationLifecycleService::class.java)`)
- **54/54 Phase 12 targeted tests green across 11 classes** — complete Phase 12 test matrix verified one final time after Task 2b:
  - `OperationPlanServiceTest` 6/6 (Plan 12-02)
  - `OperationLifecycleServiceTest` 6/6 (Plan 12-04 Task 1)
  - `OperationMeritBonusTest` 4/4 (Plan 12-04 Task 2a)
  - `TacticalBattleServiceSyncTest` 2/2 (Plan 12-03 Task 2)
  - `TickEngineOrderingTest` 1/1 (Plan 12-04 Task 1)
  - `TickEngineTest` 13/13 (pre-existing regression)
  - `BattleTriggerOperationInjectionTest` 2/2 (Plan 12-03 Task 2)
  - `MissionObjectiveDefaultTest` 5/5 (Plan 12-01)
  - `TacticalBattleEngineTest` 11/11 (extended by Plan 12-03 Task 1)
  - `OperationPlanRepositoryTest` 2/2 (Plan 12-01)
  - `WarpNavigationCommandTest` 2/2 (Plan 12-02 Task 1)
- **Full game-app suite:** `./gradlew :game-app:test` reports `1854 tests completed, 207 failed, 1 skipped`. The 207 failing tests are the exact pre-existing Three Kingdoms legacy + DetectionService.commandRange set documented in `deferred-items.md` by Plans 12-01 and 12-03. No new regressions from Plan 12-04.

## Task Commits

Each task was committed atomically with `--no-verify` per the parallel-execution contract:

1. **Task 1: OperationLifecycleService + TickEngine Step 5.5** — `2588ae8f` (feat)
2. **Task 2a: merit bonus inline with snapshot guard** — `f7c9560d` (feat)
3. **Task 2b: sync channel wiring from commands** — `56971106` (feat)

**Plan metadata commit:** pending (will include 12-04-SUMMARY.md, STATE.md, ROADMAP.md, REQUIREMENTS.md, deferred-items.md)

## Files Created/Modified

### Created

- `backend/game-app/src/main/kotlin/com/openlogh/service/OperationLifecycleService.kt` (140 lines) — `@Service` + `@Transactional` with `processTick(sessionId, tickCount)` entry point, internal `activatePending` + `evaluateCompletion`, private `evaluateConquest/evaluateDefense/evaluateSweep` helpers, `DEFENSE_STABILITY_TICKS = 60` companion constant, sync-channel `runCatching` pattern for failure isolation
- `backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt` (188 lines) — 6 plain-Mockito unit tests with a `svc()` builder and `pendingOp` / `fleet` helpers. Uses `mock(Class::class.java)` + `` `when`(...).thenReturn(...) `` per the 12-03 classpath constraint
- `backend/game-app/src/test/kotlin/com/openlogh/service/OperationMeritBonusTest.kt` (264 lines) — 4 @SpringBootTest integration tests. Exact-value assertions (`assertEquals(150, ...)` and `assertEquals(100, ...)` — never `> 0`) per the 12-VALIDATION requirement. Includes `AopTestUtils.getTargetObject` for proxy-unwrapping the TacticalBattleService CGLIB proxy when seeding the private `activeBattles` map via reflection
- `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineOrderingTest.kt` (62 lines) — 1 plain-Mockito test using `inOrder(operationLifecycleService, tacticalBattleService)` to assert Step 5.5 ordering. Constructor param list matches TickEngine.kt:30-45 exactly with the new 15th parameter added at the END

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt` — added `import com.openlogh.service.OperationLifecycleService`, new 15th constructor parameter `private val operationLifecycleService: OperationLifecycleService`, Step 5.5 try/catch block between lines 73 and 76 (`operationLifecycleService.processTick(world.id.toLong(), world.tickCount)`)
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` — added `participantSnapshot` capture before the `for (unit in state.units)` loop in `endBattle` (line 372), added merit-bonus `if (baseMerit > 0)` block inside the loop after the existing ships/morale update (lines 390-398 — `officer.meritPoints +=`), added `computeBaseMerit` private helper method before the closing brace (lines 695-702)
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt` — added `import com.openlogh.service.TacticalBattleService` and `val tacticalBattleService: TacticalBattleService? = null` as the 11th nullable field
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` — added `import com.openlogh.service.TacticalBattleService`, new 13th primary-constructor parameter `private val tacticalBattleService: TacticalBattleService? = null`, added `tacticalBattleService = tacticalBattleService` to BOTH CommandServices construction sites (executeOfficerCommand L112-122 + executeFactionCommand L222-232). Secondary constructor unchanged (defaults new param to null via primary delegation)
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` — replaced the 12-02 TODO comment with the actual sync-channel call: `services.tacticalBattleService?.syncOperationToActiveBattles(plan)` after the successful `opService.assignOperation(...)` delegate
- `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt` — replaced the 12-02 TODO comment with the actual sync-channel call: `services.tacticalBattleService?.syncOperationToActiveBattles(cancelled)` after the successful `opService.cancelOperation(...)` delegate
- `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineTest.kt` (pre-existing test file) — added `import com.openlogh.service.OperationLifecycleService` and `mock(OperationLifecycleService::class.java)` at the end of the TickEngine construction argument list. Pre-existing 13/13 tests still pass after this additive fix
- `.planning/phases/12-operation-integration/deferred-items.md` — added Plan 12-04 section documenting the 207-failure baseline match (no new regressions) and the pre-existing CommandExecutorTest NPE root cause (`CommandRegistry.kt:34`, last modified `f06570f0` in Phase 01)

## Decisions Made

- **Lifecycle ownership at Step 5.5, not at tick 6 inside battle processing:** Running BEFORE `tacticalBattleService.processSessionBattles` means `BattleTriggerService.buildInitialState` sees the fresh ACTIVE status on the same tick as activation. Without this ordering, a new battle triggered on tick N would load PENDING ops from DB while Step 5.5 was still running — the race is eliminated by enforcing the order via Mockito InOrder in `TickEngineOrderingTest`.
- **DEFENSE counter persisted on every tick:** `evaluateCompletion` has an else-branch `operationPlanRepository.save(operation)` for DEFENSE ops when `completed == false`. Without this, the counter increment inside `evaluateDefense` would be lost when the transaction commits. The counter must cross tick boundaries, so the save is required even when the threshold is not yet reached.
- **Merit accumulation via += is the FIRST such path:** Before this plan, `Officer.meritPoints` was only mutated in `RankLadderService.kt:124` (`= MERIT_AFTER_PROMOTION`) and `.kt:143` (`= MERIT_AFTER_DEMOTION`) — both RESETS, not accumulations. Plan 12-04's `officer.meritPoints += awarded` establishes the canonical += pattern that future merit sources should follow (combat, mission completion, diplomatic achievement, etc.).
- **computeBaseMerit heuristic is intentionally simple for Phase 12:** `(100 * ships/maxShips).toInt().coerceAtLeast(10)` with `0` for losers/draws. This gives test fixtures a predictable anchor (full survival = 100) so OperationMeritBonusTest can assert concrete values (`== 150` for participant ×1.5, `== 100` for non-participant). Future phases can refine the formula (e.g., officer leadership bonus, ship class weighting) without breaking the contract — all extensions should keep winning-side-full-survival at exactly 100 to preserve the test anchor.
- **Snapshot-before-iterate is the ONLY correct race guard:** Any alternative (iterating the live set, snapshotting inside each iteration, or locking the set) would either produce non-deterministic behavior or block the sync channel. The snapshot captures authoritative membership at the moment `endBattle` started — if a CANCELLED sync arrives mid-iteration, the snapshot is already frozen and all units in the same operation get consistent treatment (all bonus or all base, all-or-nothing).
- **Nullable-default tacticalBattleService on CommandServices + CommandExecutor:** Mirrors the 12-02 `operationPlanService` pattern exactly. Chosen over making the field required because 8+ pre-existing `CommandServices(...)` test construction sites (NationCommandTest, NationResourceCommandTest, CommandParityTest, etc.) use positional args and would ALL need touched if the new field were required. The nullable default keeps Task 2b scope minimal.
- **Step 5.5 wrapped in try/catch (not rethrow):** A lifecycle service exception must not block the tactical battle processing (Step 6) or downstream tick steps (7+). The `logger.warn` pattern matches the existing `shipyardProductionService.runProduction` and `fleetSortieCostService.processSortieCost` error handling at lines 83 and 92.
- **OperationMeritBonusTest uses @SpringBootTest integration style:** Unit-test mocking of `@Transactional` on `endBattle` would require a full AOP proxy — cleaner to use the real Spring context with `@Transactional` rollback for test isolation. Uses `classes = [OpenloghApplication::class]` to avoid the duplicate `@SpringBootConfiguration` discovery issue documented in Plan 12-01.
- **No REFACTOR phase needed:** GREEN code for all 3 tasks is already minimal and mirrors the plan's `<action>` specs exactly. Skipping refactor to avoid touching working code.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-existing TickEngineTest broke after adding new constructor parameter**
- **Found during:** Task 1 GREEN verification (first test run)
- **Issue:** `./gradlew :game-app:test --tests "com.openlogh.engine.TickEngineOrderingTest"` failed with `No value passed for parameter 'operationLifecycleService'` at `TickEngineTest.kt:45`. The pre-existing `TickEngineTest.setUp()` method constructs TickEngine via positional args — adding a required 15th param broke the hand-constructed site.
- **Fix:** Added `import com.openlogh.service.OperationLifecycleService` and `mock(OperationLifecycleService::class.java)` as the 15th positional argument in `TickEngineTest.setUp()`. No other changes needed — the existing 13 tests cover tick counting, CP regen, month boundary, broadcasts, and command duration, none of which depend on OperationLifecycleService behavior.
- **Files modified:** `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineTest.kt`
- **Verification:** `./gradlew :game-app:test --tests "com.openlogh.engine.TickEngineTest"` now returns 13/13 pass alongside the new TickEngineOrderingTest.
- **Committed in:** `2588ae8f` (Task 1 commit — bundled because the fix is inseparable from the TickEngine constructor signature change)

**2. [Rule 1 - Bug] Plan's sketch code uses mockito-kotlin which is NOT on the classpath**
- **Found during:** Task 1 RED test authoring
- **Issue:** Plan 12-04's code sketches use `org.mockito.kotlin.mock`, `whenever`, `verify`, `inOrder` — none of which are on the :game-app test classpath (12-03-SUMMARY.md explicitly documents this exclusion at build.gradle.kts:85).
- **Fix:** Rewrote all 3 new test files to use plain `org.mockito.Mockito.mock(Class::class.java)`, `` `when`(...).thenReturn(...) ``, `verify(...)`, and `inOrder(...)`. No change to test semantics — same coverage, same assertions, just the Java-style Mockito API instead of the Kotlin DSL.
- **Files modified:** `OperationLifecycleServiceTest.kt`, `TickEngineOrderingTest.kt` (created with correct imports from the start)
- **Verification:** Both files compile and all tests pass.
- **Committed in:** `2588ae8f` (Task 1 commit)

**3. [Rule 1 - Bug] OperationMeritBonusTest initial draft failed with StaleObjectStateException + NullPointerException**
- **Found during:** Task 2a GREEN test verification (first @SpringBootTest run)
- **Issue:** Three problems in the initial test draft:
  1. `endedBattle(id: Long)` constructed a detached TacticalBattle with `it.id = id` — when `endBattle` subsequently called `tacticalBattleRepository.save(battle)`, Hibernate treated it as an UPDATE on a non-existent row, throwing `StaleObjectStateException` even though Officer has no `@Version`.
  2. Reflection on `TacticalBattleService::class.java.getDeclaredField("activeBattles")` returned a null value when called on the @SpringBootTest-injected `tacticalBattleService` because Spring wraps `@Transactional` services in a CGLIB proxy — the proxy subclass inherits the field but its instance holds null for inherited fields.
  3. `fleetId = 42L` was hardcoded, but `endBattle`'s unit loop does `fleetRepository.findById(unit.fleetId).orElse(null) ?: continue` — with no Fleet row at ID 42, the loop body was skipped entirely and merit never accrued (tests asserted `== 150` but saw `0`).
- **Fix:**
  1. Replaced `endedBattle(id)` with `persistBattle()` that calls `tacticalBattleRepository.saveAndFlush(...)` to pre-persist the battle before `endBattle` tries to update it.
  2. Added `realService()` helper using `AopTestUtils.getTargetObject(tacticalBattleService)` to unwrap the CGLIB proxy, plus `seedActiveBattle(state)` helper that does the reflection on the unwrapped target.
  3. Added `@Autowired fleetRepository` and `seedFleet(factionId)` helper that calls `fleetRepository.saveAndFlush(Fleet().also { ... })` to pre-persist a fleet with an auto-generated ID. All test call sites now use `fleet.id` (the real auto-generated ID) instead of hardcoded `42L/43L/77L`.
- **Files modified:** `backend/game-app/src/test/kotlin/com/openlogh/service/OperationMeritBonusTest.kt`
- **Verification:** After all 3 fixes, `./gradlew :game-app:test --tests "com.openlogh.service.OperationMeritBonusTest"` reports 4/4 pass. Key assertion values (150 for participant, 100 for non-participant) are met exactly.
- **Committed in:** `f7c9560d` (Task 2a commit)

---

**Total deviations:** 3 auto-fixed (1 Rule 3 blocking + 2 Rule 1 bugs in plan sketch code or initial test drafts)

**Impact on plan:** No scope creep. All three fixes were necessary to get tests running correctly. Test content exactly matches the plan spec — same behaviors, same concrete-value assertions, same coverage.

## Issues Encountered

- **Full-suite 207-failure baseline unchanged:** Final `./gradlew :game-app:test` run reports `1854 tests completed, 207 failed, 1 skipped`. Every failing class is in the pre-existing Three Kingdoms legacy set (PlanetServiceTest/ScenarioServiceTest/CityServiceTest/EconomyFormulaParityTest suite/DisasterParityTest/etc.) or `DetectionServiceTest.commandRange` — all documented in `deferred-items.md` by Plans 12-01 and 12-03 as out-of-scope. The 5 `CommandExecutorTest` NPEs are at `CommandRegistry.kt:34`, which was last modified in commit `f06570f0` (Phase 01) — pre-existing, not caused by my CommandExecutor changes (which are purely additive: new nullable constructor param + new named arg in existing CommandServices constructions).

## Known Stubs

None. All new code is production-ready:
- `OperationLifecycleService` has full @Service + @Transactional + sync-channel wiring with all 3 objective types implemented exhaustively (no `else -> TODO`)
- `DEFENSE_STABILITY_TICKS = 60` const is consumed directly by `evaluateDefense` — not a placeholder
- `computeBaseMerit` is a fully-implemented heuristic — the comment explicitly notes this is "the first Phase 12 iteration" and documents the test anchor (full survival = 100) so future refinements know what contract to preserve. This is documentation, not a stub.
- Merit bonus `officer.meritPoints += awarded` is a real accumulation path, not a TODO
- Both sync channel calls in OperationPlanCommand + OperationCancelCommand use the actual method (not a placeholder) with nullable-safe `?.` operator for unit-test compatibility
- Pre-existing Plan 12-02 TODO comments in both commands have been REPLACED with the actual sync channel calls — the comments referenced "Plan 12-04 Task 2b" which is exactly this plan, and the resolution is now on disk

## Phase 12 End-to-End Validation

**OPS-01 (OperationPlan → tactical sync):**
- Sync channel exists (`TacticalBattleService.syncOperationToActiveBattles` — Plan 12-03)
- Command layer wires the call from `OperationPlanCommand` and `OperationCancelCommand` (Plan 12-04 Task 2b)
- `OperationLifecycleService` also calls it on PENDING→ACTIVE and ACTIVE→COMPLETED (Plan 12-04 Task 1)
- Battle init populates from repository (`BattleTriggerService.buildInitialState` — Plan 12-03)
- Tactical engine read-through at Step 0.6 (Plan 12-03)
- `MissionObjective.defaultForPersonality` fallback (Plan 12-01)

**OPS-02 (merit bonus for operation participants):**
- `endBattle` applies ×1.5 via `participantSnapshot.contains(unit.fleetId)` (Plan 12-04 Task 2a)
- Snapshot race guard prevents concurrent cancel corruption (Plan 12-04 Task 2a)
- First `officer.meritPoints +=` accumulation path in the codebase (Plan 12-04 Task 2a)
- `OperationMeritBonusTest.snapshot_prevents_concurrent_cancel_race` asserts all-or-nothing semantics (Plan 12-04 Task 2a)

**OPS-03 (operation lifecycle: activation + completion):**
- `WarpNavigationCommand.Fleet.planetId` fix (Plan 12-02 Task 1)
- `OperationPlanService.assignOperation` 1-fleet-1-operation atomicity (Plan 12-02 Task 2)
- `OperationLifecycleService.activatePending` arrival detection (Plan 12-04 Task 1)
- `OperationLifecycleService.evaluateCompletion` CONQUEST/DEFENSE/SWEEP (Plan 12-04 Task 1)
- `TickEngine` Step 5.5 ordering before battle trigger (Plan 12-04 Task 1)
- `TickEngineOrderingTest` asserts the order via Mockito InOrder (Plan 12-04 Task 1)

**All three OPS requirements are complete end-to-end. Phase 12 closes.**

## Next Phase Readiness

Plan 12-04 is the final Plan in Phase 12. Phase 12 is now complete and unblocks:

- **Phase 13 (Strategic AI):** Can drive operations programmatically via `OperationPlanCommand` and trust that the tactical layer will:
  1. See the objective on the next tick (Step 0.6 read-through from 12-03)
  2. Activate the operation when a fleet arrives at target (Step 5.5 from 12-04)
  3. Complete the operation when the objective condition is met (Step 5.5 from 12-04)
  4. Award ×1.5 merit to participating fleet officers on battle end (inline in endBattle from 12-04)
  5. Cancel cleanly via `OperationCancelCommand` with sync-channel propagation (12-04 Task 2b)
- **Any future phase that adds merit sources** (combat victories, mission rewards, diplomatic achievements): should follow the `officer.meritPoints += awarded` accumulation pattern established here. Do NOT use `=` (that would reset — see RankLadderService for the two RESET sites).
- **Any future system that reads a concurrent set inside a @Transactional loop:** should follow the `val snapshot: Set<T> = concurrentSet.toSet()` pattern established in `endBattle`. The snapshot must be taken BEFORE the loop header, not inside iterations.

**Cross-references for downstream agents:**
- `OperationLifecycleService.processTick` is `@Transactional` — callers should NOT wrap it in another transaction. TickEngine's call is outside any tx, which is correct.
- `DEFENSE_STABILITY_TICKS = 60` is a companion-object const, accessible via `OperationLifecycleService.DEFENSE_STABILITY_TICKS`. If Phase 13 wants a different stability window for AI-driven operations, either expose a configurable field or add an override parameter to `evaluateCompletion` — do NOT hardcode `60` elsewhere.
- `computeBaseMerit` is `private` — expose as `internal` or move to a separate `MeritScorer` object if Phase 13 wants to call it directly. For now, Phase 13 should call `endBattle` and let the inline logic run.
- `participantSnapshot` pattern: if `endBattle` ever grows to iterate MULTIPLE concurrent sets (e.g., a "protected" set AND a "participant" set), BOTH must be snapshotted before the loop — the pattern applies per-set, not per-loop.
- `services.tacticalBattleService?.syncOperationToActiveBattles(...)`: any future command that creates/modifies OperationPlan should mirror this call. The `?.` is load-bearing (unit-test compat).

## Self-Check: PASSED

**Files verified to exist:**
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/service/OperationLifecycleService.kt`
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt` (modified with Step 5.5)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` (modified with participantSnapshot + merit bonus + computeBaseMerit)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt` (modified with tacticalBattleService nullable field)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` (modified with tacticalBattleService constructor param + 2 construction-site wirings)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` (modified with sync channel call)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt` (modified with sync channel call)
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt`
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/service/OperationMeritBonusTest.kt`
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineOrderingTest.kt`
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineTest.kt` (modified with new mock arg)

**Commits verified:**
- FOUND: `2588ae8f` — feat(12-04): add OperationLifecycleService + TickEngine Step 5.5
- FOUND: `f7c9560d` — feat(12-04): inline x1.5 merit bonus in endBattle with snapshot guard
- FOUND: `56971106` — feat(12-04): wire sync channel from OperationPlan/Cancel commands

**Test results verified (from targeted run):**
- FOUND: `TEST-com.openlogh.service.OperationLifecycleServiceTest.xml` — `tests="6" skipped="0" failures="0" errors="0"`
- FOUND: `TEST-com.openlogh.engine.TickEngineOrderingTest.xml` — `tests="1" skipped="0" failures="0" errors="0"`
- FOUND: `TEST-com.openlogh.service.OperationMeritBonusTest.xml` — `tests="4" skipped="0" failures="0" errors="0"`
- FOUND: `TEST-com.openlogh.engine.TickEngineTest.xml` — `tests="13" skipped="0" failures="0" errors="0"` (regression check)
- FOUND: `TEST-com.openlogh.service.OperationPlanServiceTest.xml` — `tests="6" skipped="0" failures="0" errors="0"` (regression check)
- FOUND: `TEST-com.openlogh.service.TacticalBattleServiceSyncTest.xml` — `tests="2" skipped="0" failures="0" errors="0"` (regression check)
- FOUND: `TEST-com.openlogh.engine.tactical.BattleTriggerOperationInjectionTest.xml` — `tests="2" skipped="0" failures="0" errors="0"` (regression check)
- FOUND: `TEST-com.openlogh.engine.tactical.ai.MissionObjectiveDefaultTest.xml` — `tests="5" skipped="0" failures="0" errors="0"` (regression check)
- FOUND: `TEST-com.openlogh.engine.tactical.TacticalBattleEngineTest.xml` — `tests="11" skipped="0" failures="0" errors="0"` (regression check)
- FOUND: `TEST-com.openlogh.repository.OperationPlanRepositoryTest.xml` — `tests="2" skipped="0" failures="0" errors="0"` (regression check)
- FOUND: `TEST-com.openlogh.command.gin7.operations.WarpNavigationCommandTest.xml` — `tests="2" skipped="0" failures="0" errors="0"` (regression check)

**Acceptance criteria verified (grep-confirmed on disk):**
- `DEFENSE_STABILITY_TICKS` appears 3× in OperationLifecycleService.kt (doc reference + const + usage)
- `const val DEFENSE_STABILITY_TICKS: Int = 60` literal present
- `activatePending`, `evaluateCompletion`, `processTick` methods all present
- `@Service` + `@Transactional` annotations present
- All three MissionObjective cases (CONQUEST/DEFENSE/SWEEP) present in `evaluateCompletion` when-branch
- `operation.stabilityTickCounter = 0` reset path present in `evaluateDefense`
- `operationLifecycleService.processTick(world.id.toLong(), world.tickCount)` literal present in TickEngine.kt
- TickEngine.kt Step 5.5 appears BEFORE `tacticalBattleService.processSessionBattles` (line ordering verified)
- `private val operationLifecycleService: OperationLifecycleService` in TickEngine constructor
- `val participantSnapshot: Set<Long> = state.operationParticipantFleetIds.toSet()` literal present in TacticalBattleService.kt
- `participantSnapshot.contains(unit.fleetId)` literal present (uses snapshot, not live set)
- `officer.meritPoints +=` literal present
- `multiplier = if (isOperationParticipant) 1.5 else 1.0` literal present
- `private fun computeBaseMerit` helper present
- Merit mutation is inside the existing `@Transactional fun endBattle` (no new @Transactional)
- `services.tacticalBattleService?.syncOperationToActiveBattles(plan)` in OperationPlanCommand.kt
- `services.tacticalBattleService?.syncOperationToActiveBattles(cancelled)` in OperationCancelCommand.kt
- `val tacticalBattleService: TacticalBattleService? = null` in CommandServices.kt
- `import com.openlogh.service.TacticalBattleService` in CommandServices.kt
- `private val tacticalBattleService: TacticalBattleService? = null` in CommandExecutor.kt primary constructor
- `tacticalBattleService = tacticalBattleService` appears twice in CommandExecutor.kt (both CommandServices construction sites)

---
*Phase: 12-operation-integration*
*Plan: 04*
*Completed: 2026-04-09*
