---
phase: 12-operation-integration
plan: 03
subsystem: tactical-engine
tags: [kotlin, spring-boot, concurrent-hashmap, tactical, mockito, tdd]

# Dependency graph
requires:
  - phase: 12-operation-integration
    provides: "OperationPlan entity, OperationStatus enum, OperationPlanRepository, MissionObjective.defaultForPersonality (Plan 12-01 outputs)"
provides:
  - "TacticalBattleState.missionObjectiveByFleetId: ConcurrentHashMap<Long, MissionObjective>"
  - "TacticalBattleState.operationParticipantFleetIds: ConcurrentHashMap-backed MutableSet<Long>"
  - "TacticalBattleEngine.processTick Step 0.6 read-through cache refresh (runs between Step 0.5 processOutOfCrcUnits and Step 0.7 TacticalAIRunner.processAITick)"
  - "BattleTriggerService(fleetRepository, officerRepository, starSystemRepository, tacticalBattleRepository, operationPlanRepository) — 5-param constructor with OperationPlanRepository injection"
  - "BattleTriggerService.buildInitialState seeds missionObjectiveByFleetId + operationParticipantFleetIds from OperationPlanRepository.findBySessionIdAndStatus(session, ACTIVE) + PENDING, with MissionObjective.defaultForPersonality(unit.personality) fallback"
  - "TacticalBattleService.syncOperationToActiveBattles(OperationPlan) — direct-call CRUD sync channel (Plan 12-04 will invoke from commands)"
affects: [12-04-lifecycle-service, 12-04-merit-bonus, 13-strategic-ai]

# Tech tracking
tech-stack:
  added: []  # No new dependencies — uses existing java.util.concurrent + Mockito core (mockito-kotlin is NOT on the classpath; test fixtures use plain org.mockito.Mockito)
  patterns:
    - "ConcurrentHashMap + Collections.newSetFromMap(ConcurrentHashMap) for lock-free tick-loop read / sync-channel write race protection"
    - "TDD RED/GREEN combined per task (test-first edit → failing compile → GREEN impl → passing run, committed atomically)"
    - "Reflection-based private-field seeding (`TacticalBattleService::class.java.getDeclaredField('activeBattles')`) in unit tests to exercise TacticalBattleService without Spring context"
    - "Plain org.mockito.Mockito.mock + `when` for Kotlin unit tests — mockito-kotlin intentionally excluded from :game-app classpath (build.gradle.kts:85)"

key-files:
  created:
    - "backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/BattleTriggerOperationInjectionTest.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/service/TacticalBattleServiceSyncTest.kt"
  modified:
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt"
    - "backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt"
    - "backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleEngineTest.kt"
    - ".planning/phases/12-operation-integration/deferred-items.md"

key-decisions:
  - "Step 0.6 placed BETWEEN Step 0.5 (processOutOfCrcUnits) and Step 0.7 (TacticalAIRunner.processAITick) so the read-through copies the map value into unit.missionObjective BEFORE the AI runner consumes it at TacticalAIRunner.kt:77 — verified by both awk line-ordering check AND the new unit test which would fail if ordering were wrong"
  - "Absence semantics: if a fleetId is NOT in missionObjectiveByFleetId at tick start, Step 0.6 leaves unit.missionObjective unchanged. BattleTriggerService seeds ALL units at battle init (participants from ops, non-participants from defaultForPersonality), so absence only occurs after a CANCELLED sync — in which case the sync channel intentionally removes the entry to force the unit back to the pre-seeded default on the next tick"
  - "BattleTriggerService loads BOTH ACTIVE and PENDING operations at battle init, not just ACTIVE — a fleet assigned to a PENDING op that just landed on the battle's star system should still receive its future objective. The first-arrival activation (Plan 12-04 OperationLifecycleService) will move it to ACTIVE on the same tick, but this plan guarantees the objective flows even if that ordering slips"
  - "operationParticipantFleetIds is a separate Set (not just keys of missionObjectiveByFleetId) because non-participants ALSO end up in the map — with defaultForPersonality values. The set specifically flags 'fleets attached to a real OperationPlan' so Plan 12-04's endBattle merit bonus can filter cleanly (NOT 1.5× multiplier for default-personality fleets)"
  - "Test fixtures use plain org.mockito.Mockito — NOT mockito-kotlin — because build.gradle.kts:85 explicitly excludes ShipyardProductionServiceTest with the note 'Phase 04-03: ShipyardProductionServiceTest uses mockito-kotlin whenever not in classpath'. Every new test in this plan uses `mock(ClassName::class.java)` + `when`(...).thenReturn(...) to match the existing EconomyServiceTest pattern"
  - "TacticalBattleServiceSyncTest seeds the private `activeBattles` ConcurrentHashMap via reflection — no Spring context, no @SpringBootTest — because the sync method is pure state mutation and exercising it under a Spring bootstrap would be ~50× slower for no value"
  - "12-VALIDATION.md OPS-01 sync test row and WarpNavigationCommand package path were already reconciled by the gap-closure revision of the plan before execution started — verified via grep, no edit needed"

requirements-completed: [OPS-01, OPS-02]

# Metrics
duration: 13min
completed: 2026-04-09
---

# Phase 12 Plan 03: Tactical Engine Operation Wire-Through Summary

**OperationPlan → TacticalBattleState sync: read-through Step 0.6 cache + BattleTriggerService init population + TacticalBattleService CRUD channel. Every tactical unit now sees its current operation objective within one tick of any strategic change.**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-04-09T03:07:50Z
- **Completed:** 2026-04-09T03:21:18Z
- **Tasks:** 2 (both tdd="true")
- **Commits:** 2 task commits + 1 metadata commit
- **Files created:** 2 (both test files)
- **Files modified:** 5 (3 source + 1 test + 1 deferred-items.md)

## Accomplishments

- **TacticalBattleState SoT fields (Task 1):** Added `missionObjectiveByFleetId: MutableMap<Long, MissionObjective>` (ConcurrentHashMap) and `operationParticipantFleetIds: MutableSet<Long>` (ConcurrentHashMap-backed) immediately after `connectedPlayerOfficerIds`. Both fields are thread-safe by construction — the tick loop reads while command threads write via the sync channel.
- **Engine Step 0.6 (Task 1):** Inserted read-through cache refresh between Step 0.5 `processOutOfCrcUnits(state)` and Step 0.7 `TacticalAIRunner.processAITick(state)`. Iterates `state.units`, skips dead units, and copies `state.missionObjectiveByFleetId[unit.fleetId]` into `unit.missionObjective` if present. Absence = no mutation. Line ordering proven via both `awk` check and unit test.
- **BattleTriggerService init population (Task 2):** Constructor gains `operationPlanRepository: OperationPlanRepository` as the 5th param. `buildInitialState` queries ACTIVE + PENDING operations for the battle's session, then iterates `units` and seeds either the operation's objective (participants) or `MissionObjective.defaultForPersonality(unit.personality)` (non-participants). Non-participants are intentionally absent from `operationParticipantFleetIds` so Plan 12-04's merit bonus filter matches real operation membership.
- **TacticalBattleService.syncOperationToActiveBattles (Task 2):** New public method wired to be called by Plan 12-04's OperationPlanCommand / OperationCancelCommand / OperationLifecycleService. Iterates `activeBattles.values` and applies OperationStatus-specific transforms:
  - `PENDING | ACTIVE` → add fleetIds to map + set
  - `CANCELLED` → remove fleetIds from map + set
  - `COMPLETED` → intentional no-op (current battles continue until natural end)
- **Test coverage (Tasks 1+2):** 4 new tests, 0 TODO placeholders:
  - `TacticalBattleEngineTest.mission read through refreshes unit objective at tick start` — seed map, tick once, assert unit.missionObjective flipped from DEFENSE → CONQUEST
  - `TacticalBattleEngineTest.non_participant_uses_default` — omit from map, tick once, assert pre-seeded SWEEP preserved
  - `BattleTriggerOperationInjectionTest.buildInitialState populates map for participants from operation plan` — stub ops repo to return ACTIVE plan with fleet 42, assert map[42]=CONQUEST AND operationParticipantFleetIds contains 42
  - `BattleTriggerOperationInjectionTest.non_participant_gets_default_from_personality` — empty ops, assert map.containsKey(99) AND !operationParticipantFleetIds.contains(99)
  - `TacticalBattleServiceSyncTest.sync_adds_entries_for_PENDING_or_ACTIVE_operation` — seed empty state via reflection, call sync with ACTIVE plan, assert both fleets land in map + set
  - `TacticalBattleServiceSyncTest.sync_removes_entries_for_CANCELLED_operation` — pre-seed state with fleet 42, call sync with CANCELLED plan, assert fleet 42 removed from both
- **20/20 target tests green:** full `BattleTriggerOperationInjectionTest` + `TacticalBattleServiceSyncTest` + `TacticalBattleEngineTest` (11 tests, 9 pre-existing + 2 new) + `MissionObjectiveDefaultTest` (Plan 12-01's 5 tests) pass on Java 17.

## Task Commits

1. **Task 1: TacticalBattleState.missionObjectiveByFleetId + Step 0.6** — `645459a4` (feat)
2. **Task 2: BattleTriggerService OperationPlanRepository + TacticalBattleService.syncOperationToActiveBattles + 12-VALIDATION.md verification** — `b99d6490` (feat)

**Plan metadata commit:** pending (will include 12-03-SUMMARY.md, STATE.md, ROADMAP.md, REQUIREMENTS.md)

## Files Created/Modified

### Created

- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/BattleTriggerOperationInjectionTest.kt` — 2 tests using plain Mockito to verify BattleTriggerService correctly injects OperationPlanRepository and populates both SoT fields at battle init
- `backend/game-app/src/test/kotlin/com/openlogh/service/TacticalBattleServiceSyncTest.kt` — 2 tests exercising the CRUD sync channel via reflection-seeded `activeBattles` map (no Spring context)

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` — added `import java.util.concurrent.ConcurrentHashMap`, 2 new fields on `TacticalBattleState`, and Step 0.6 read-through loop in `processTick`
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` — added 3 imports (`MissionObjective`, `OperationStatus`, `OperationPlanRepository`) + `java.util.concurrent.ConcurrentHashMap`, new constructor param, 20-line population block in `buildInitialState` before the `return TacticalBattleState(...)` call
- `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` — added 3 imports (`OperationPlan`, `OperationStatus`, `MissionObjective`) and new `syncOperationToActiveBattles(OperationPlan)` method with when-branch on the 4 OperationStatus values
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleEngineTest.kt` — added `MissionObjective` import and 2 new test methods at the end of the class
- `.planning/phases/12-operation-integration/deferred-items.md` — documented the pre-existing `DetectionServiceTest.commandRange` failure (confirmed at `645459a4^`)

## Decisions Made

- **Step 0.6 insertion point verification:** Used both an `awk` line-ordering check (`p<m && m<a` where p=processOutOfCrcUnits, m=Step 0.6, a=TacticalAIRunner.processAITick) AND a unit test that would fail if the order were wrong. The unit test sets `missionObjectiveByFleetId[42]=CONQUEST` and asserts `unit.missionObjective==CONQUEST` after one tick. If Step 0.6 ran AFTER the AI runner, the AI would consume the stale DEFENSE objective instead.
- **Why BOTH ACTIVE and PENDING operations at init:** Plan 12-04's `OperationLifecycleService.activatePending` flips PENDING→ACTIVE on fleet arrival. If a battle triggers on the same tick as activation, we'd get a race where the operation is technically still PENDING when `buildInitialState` runs. Loading BOTH statuses eliminates the race without requiring tick-ordering guarantees.
- **CANCELLED sync removes from BOTH map and set:** Per D-13, cancelling an operation mid-battle must not only stop new merit bonus accrual (set) but also force the unit back to `defaultForPersonality` behavior on the next tick (map). Because Step 0.6 only acts on PRESENCE, removing the entry is enough — the existing personality-default value set at init remains on `unit.missionObjective` until the sync channel writes a new one.
- **COMPLETED sync is a no-op:** Per D-13, COMPLETED operations leave the battle's missionObjective map untouched. A battle already in flight continues to execute on the operation's objective; completion only affects NEW battles (filtered at `buildInitialState`) and merit credit (filtered at Plan 12-04's `endBattle`).
- **Mockito flavor:** Confirmed `org.mockito.kotlin.*` is NOT on the :game-app test classpath by checking `build.gradle.kts:85` (which excludes ShipyardProductionServiceTest with a comment saying so). Every new test uses `mock(ClassName::class.java)` + `org.mockito.Mockito.`when`` matching the existing `EconomyServiceTest.kt` pattern. This was a critical deviation from the plan's fixture sketches (which used `mockito-kotlin` shorthand).
- **TacticalBattleService test via reflection:** Chose reflection-seeded `activeBattles` rather than `@SpringBootTest` because (a) the sync method is pure state mutation with no repository, WebSocket, or DB dependency, (b) @SpringBootTest adds ~8 seconds of Spring bootstrap per suite, (c) the reflection seed is a single 3-line helper and exercises exactly the code path under test.
- **Fleet and Officer constructor shape:** `Fleet(id = 0, ...)` has ALL fields with defaults (id is a non-nullable `Long = 0`, planetId is `Long? = null`). Had to use `Fleet().also { it.id = ...; it.planetId = 1L }` pattern because passing `planetId = 1L` to the primary constructor would conflict with the entity's `@Id` `var id: Long = 0` default. Officer stats are `Short` not `Int` — used Int literals which Kotlin auto-converts.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Plan test fixtures used `mockito-kotlin` which is NOT on the classpath**
- **Found during:** Task 2 RED test authoring
- **Issue:** Plan 12-03 sketch code imports `org.mockito.kotlin.mock`, `org.mockito.kotlin.whenever`. Neither is available — `build.gradle.kts:85` excludes ShipyardProductionServiceTest specifically because mockito-kotlin is missing. Attempting to use those imports would produce `Unresolved reference 'mockito.kotlin'` at compile time.
- **Fix:** Rewrote both new test files to use plain `org.mockito.Mockito.mock(Class::class.java)` + `` `when`(repo.findById(42L)).thenReturn(Optional.of(fleet)) `` matching the existing `EconomyServiceTest.kt` pattern. No change to test semantics or coverage.
- **Files modified:** `BattleTriggerOperationInjectionTest.kt`, `TacticalBattleServiceSyncTest.kt` (created with correct imports from the start)
- **Verification:** Both files compile and all 4 tests pass.

**2. [Rule 3 - Blocking] `Fleet(planetId = 1L)` constructor call incompatible with entity shape**
- **Found during:** Task 2 RED test authoring
- **Issue:** Plan sketch uses `Fleet(planetId = 1L).also { ... }` but `Fleet` primary constructor has `var id: Long = 0` as the first parameter with a default, not `id: Long? = null`. Kotlin named-arg resolution doesn't let you skip the first parameter cleanly here because Fleet's primary constructor parameter list starts with `var id`.
- **Fix:** Used `Fleet().also { it.id = fleetId; it.sessionId = 1L; it.factionId = factionId; it.leaderOfficerId = officerId; it.name = "F$fleetId"; it.planetId = 100L }` — empty primary constructor call plus post-construction mutation. Matches the existing fixture pattern I found in 12-02's `OperationPlanServiceTest.kt`.
- **Files modified:** `BattleTriggerOperationInjectionTest.kt`
- **Verification:** Compile + test pass.

### Transient issues (not deviations, resolved by parallel agent)

**Transient compile break from parallel Plan 12-02 work:** At the start of Task 1 GREEN verification, `:game-app:compileTestKotlin` failed with `Unresolved reference 'OperationPlanService'` and `Unresolved reference 'assignOperation'` in `OperationPlanServiceTest.kt`. This was Plan 12-02 (running in parallel) mid-edit on its service file. By the time I retried the compile ~30 seconds later, 12-02 had committed `deec74f1 feat(12-02): add OperationPlanService transactional boundary (D-04)` and the compile succeeded. No change to Plan 12-03 scope — documented here for future plan-parallelism diagnostics.

## Issues Encountered

- **Pre-existing `DetectionServiceTest.commandRange` failure (out of scope):** Full tactical-suite regression caught `commandRange increases by expansionRate each tick up to maxRange` failing at line 176 with `expected: 100.0 but was: 7.0`. Verified pre-existing by restoring `TacticalBattleEngine.kt` + `TacticalBattleEngineTest.kt` from `git show 645459a4^` and running the same test — identical failure, same `got 7.0`. Not caused by Step 0.6. Belongs to Phase 9 CRC work (likely interaction with `updateCommandRange` or a Phase 11 TacticalAI retreat path). Documented in `deferred-items.md`.
- **None blocking this plan.**

## Known Stubs

None. All new code is production-ready:
- `missionObjectiveByFleetId` and `operationParticipantFleetIds` fields have real concurrent-safe initializers and are consumed both at init and runtime
- Step 0.6 runs in the main tick loop path — not behind a feature flag
- `syncOperationToActiveBattles` is a fully-implemented 4-branch `when` on `OperationStatus` — COMPLETED's intentional no-op is documented with rationale (not a TODO)
- `BattleTriggerService.buildInitialState` seeds both fields for EVERY unit — no partial coverage

## 12-VALIDATION.md Row Reconciliation

Both edits the plan requested were already applied by the gap-closure revision of 12-03-PLAN.md before execution started. Verified via grep:

- Line 47: `TacticalBattleService.syncOperationToActiveBattles(plan)` row references `TacticalBattleServiceSyncTest.sync_adds_entries_for_PENDING_or_ACTIVE_operation` (NOT the old `OperationLifecycleServiceTest.sync_updates_active_battles`)
- Line 62: WarpNavigationCommand row references `command.gin7.operations.WarpNavigationCommandTest.updates_fleet_planet_id` (NOT the old `command.gin7.commander.WarpNavigationCommandTest`)
- Line 78: Wave 0 requirements list includes `backend/game-app/src/test/kotlin/com/openlogh/service/TacticalBattleServiceSyncTest.kt`

No edit needed to 12-VALIDATION.md for this plan. The file is already consistent with the Task 2 test name landed here.

## Next Phase Readiness

Plan 12-03 unblocks the following downstream work:

- **Plan 12-04 (OperationLifecycleService + merit bonus + command wiring):**
  1. Can invoke `tacticalBattleService.syncOperationToActiveBattles(operation)` from `OperationPlanCommand.execute`, `OperationCancelCommand.execute`, and `OperationLifecycleService.activatePending/processTick` — the method signature and behavior are frozen
  2. Can read `state.operationParticipantFleetIds.toSet()` in `TacticalBattleService.endBattle()` to filter merit bonus recipients — the set is already populated correctly by both `buildInitialState` (init path) and `syncOperationToActiveBattles` (CRUD path). Per 12-VALIDATION.md failure mode #3, the snapshot-before-iterate pattern is REQUIRED — `endBattle` must do `val participants = state.operationParticipantFleetIds.toSet()` at the top of the unit loop to prevent a concurrent CANCELLED sync from mutating the set mid-iteration
  3. Can trust that `state.missionObjectiveByFleetId` always contains an entry for every live fleet (via personality default) — Step 0.6's absence-tolerant design means 12-04 doesn't need to guard against null

- **Phase 13 (Strategic AI):** Can drive operations via `OperationPlanCommand` and trust that the tactical layer will see the objective on the next tick without manual state pokes. The sync channel makes strategic AI → tactical behavior coupling trivial (one direct-call method).

**Cross-references for downstream agents:**
- `missionObjectiveByFleetId` is a read-through cache, NOT a write-through — the tick loop never writes to it. Only `buildInitialState` (init) and `syncOperationToActiveBattles` (CRUD) mutate the map. This keeps the data flow unidirectional: strategic layer → map → tick Step 0.6 → unit field → TacticalAIRunner.
- `operationParticipantFleetIds` is a DIFFERENT set from "fleets with an entry in missionObjectiveByFleetId" — the map contains ALL fleets (participants + personality-defaulted non-participants) while the set contains ONLY fleets attached to a real OperationPlan. Use the set, NOT `map.keys`, for merit bonus filtering.
- The `COMPLETED` branch of `syncOperationToActiveBattles` is a deliberate no-op, not a TODO. Per D-13, completed operations should not disrupt in-flight battles. If Plan 12-04 wants different semantics, it should extend the `when` branch — but the current plan set this as the baseline.

## Self-Check: PASSED

**Files verified to exist:**
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` (modified)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` (modified)
- FOUND: `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` (modified)
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleEngineTest.kt` (modified)
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/BattleTriggerOperationInjectionTest.kt` (created)
- FOUND: `backend/game-app/src/test/kotlin/com/openlogh/service/TacticalBattleServiceSyncTest.kt` (created)
- FOUND: `.planning/phases/12-operation-integration/deferred-items.md` (modified)

**Commits verified:**
- FOUND: `645459a4` — feat(12-03): add missionObjectiveByFleetId + Step 0.6 read-through to TacticalBattleEngine
- FOUND: `b99d6490` — feat(12-03): BattleTriggerService injects OperationPlanRepository + TacticalBattleService.syncOperationToActiveBattles

**Test results verified:**
- FOUND: `TEST-com.openlogh.engine.tactical.TacticalBattleEngineTest.xml` — `tests="11" skipped="0" failures="0" errors="0"`
- FOUND: `TEST-com.openlogh.engine.tactical.BattleTriggerOperationInjectionTest.xml` — `tests="2" skipped="0" failures="0" errors="0"`
- FOUND: `TEST-com.openlogh.service.TacticalBattleServiceSyncTest.xml` — `tests="2" skipped="0" failures="0" errors="0"`
- FOUND: `TEST-com.openlogh.engine.tactical.ai.MissionObjectiveDefaultTest.xml` — `tests="5" skipped="0" failures="0" errors="0"`

**Acceptance criteria verified:**
- `missionObjectiveByFleetId: MutableMap<Long,` present in `TacticalBattleEngine.kt` — grep confirmed
- `ConcurrentHashMap()` initializer present — grep confirmed
- `operationParticipantFleetIds: MutableSet<Long>` present — grep confirmed
- `import java.util.concurrent.ConcurrentHashMap` present — grep confirmed
- `state.missionObjectiveByFleetId[unit.fleetId]?.let` present in processTick — grep confirmed
- Step 0.6 line ordering verified via awk: `p=270 m=272 a=283` → `processOutOfCrcUnits < Step 0.6 < TacticalAIRunner` ✓
- `missionObjectiveByFleetId` literal appears 2× in `TacticalBattleEngine.kt` (field decl + read-through)
- `operationPlanRepository` literal appears 3× in `BattleTriggerService.kt` (import + ctor + 2 queries — technically 3 unique lines, 4 total occurrences)
- `syncOperationToActiveBattles` literal appears 1× in `TacticalBattleService.kt` (fun definition; callers will be added by Plan 12-04)
- `fun syncOperationToActiveBattles(operation: OperationPlan)` literal present — grep confirmed
- CANCELLED branch removes from map + set — code inspection confirmed
- PENDING/ACTIVE branch adds to map + set — code inspection confirmed
- Zero `TODO(` in either new test file — grep confirmed (count=0)
- Both new test files use the REAL 7-param `TacticalBattleService` constructor (tacticalBattleRepository, fleetRepository, officerRepository, battleTriggerService, gameEventService, messagingTemplate, shipStatRegistry) — code inspection confirmed
- 12-VALIDATION.md contains `TacticalBattleServiceSyncTest.sync_adds_entries_for_PENDING_or_ACTIVE_operation` — grep confirmed (line 47)
- 12-VALIDATION.md contains `command.gin7.operations.WarpNavigationCommandTest` — grep confirmed (line 62)
- 12-VALIDATION.md does NOT contain `OperationLifecycleServiceTest.sync_updates_active_battles` — grep confirmed (0 hits)
- DetectionServiceTest.commandRange failure confirmed pre-existing (reproduced at `645459a4^`)

---
*Phase: 12-operation-integration*
*Plan: 03*
*Completed: 2026-04-09*
