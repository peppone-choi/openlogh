---
phase: 12-operation-integration
verified: 2026-04-09T04:00:00Z
status: passed
score: 3/3 must-haves verified
---

# Phase 12: Operation Integration — Verification Report

**Phase Goal:** 전략 게임의 작전계획이 전술전 AI 행동을 결정하고, 작전 참가 부대가 공적 보상을 받으며, 발령-도달로 작전이 시작된다  
(Strategic-game operation plans drive tactical-battle AI behavior, participating fleets receive merit rewards, and operations auto-start via issue-and-arrive.)

**Verified:** 2026-04-09  
**Status:** PASSED  
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Operation mission objectives auto-propagate to TacticalAI on battle entry | VERIFIED | `TacticalBattleEngine.kt` lines 198-279: `missionObjectiveByFleetId` ConcurrentHashMap on `TacticalBattleState`; Step 0.6 read-through loop refreshes `unit.missionObjective` from map BEFORE `TacticalAIRunner.processAITick`; `BattleTriggerService.kt` populates both fields from `OperationPlanRepository.findBySessionIdAndStatus` (ACTIVE + PENDING) at battle init; `MissionObjective.defaultForPersonality` used for non-participants |
| 2 | Participating fleets receive merit bonus vs non-participants after battle | VERIFIED | `TacticalBattleService.endBattle()` line 372: `val participantSnapshot: Set<Long> = state.operationParticipantFleetIds.toSet()` snapshot guard; lines 394-397: `val multiplier = if (isOperationParticipant) 1.5 else 1.0`; `officer.meritPoints += awarded`; confirmed first `+=` accumulation path (RankLadderService uses `=` resets only) |
| 3 | Issued fleets auto-start operation on arrival at target star system | VERIFIED | `WarpNavigationCommand.kt` line 47: `troop?.planetId = destPlanetId`; `OperationLifecycleService.kt`: `activatePending()` checks `fleet.planetId == operation.targetStarSystemId`; `TickEngine.kt` line 82: `operationLifecycleService.processTick(world.id.toLong(), world.tickCount)` at Step 5.5 (before `processSessionBattles`) |

**Score:** 3/3 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/game-app/src/main/resources/db/migration/V47__create_operation_plan.sql` | operation_plan table DDL | VERIFIED | EXISTS; contains `CREATE TABLE operation_plan`, `REFERENCES session_state(id) ON DELETE CASCADE`, `BEGIN;`/`COMMIT;` wrap, 3 indexes (session_status, GIN participants, faction) |
| `backend/game-app/src/main/kotlin/com/openlogh/entity/OperationPlan.kt` | JPA entity with JSONB List<Long> | VERIFIED | EXISTS; contains `@JdbcTypeCode(SqlTypes.JSON)`, `participantFleetIds: MutableList<Long>`, `@Enumerated(EnumType.STRING)` on `objective` and `status` |
| `backend/game-app/src/main/kotlin/com/openlogh/model/OperationStatus.kt` | PENDING/ACTIVE/COMPLETED/CANCELLED enum | VERIFIED | EXISTS; 4-state enum under `com.openlogh.model` |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt` | defaultForPersonality companion helper | VERIFIED | EXISTS; line 24: `fun defaultForPersonality(personality: PersonalityTrait): MissionObjective` — AGGRESSIVE→SWEEP, all others→DEFENSE |
| `backend/game-app/src/main/kotlin/com/openlogh/repository/OperationPlanRepository.kt` | JpaRepository with findBySessionIdAndStatus | VERIFIED | EXISTS; `JpaRepository<OperationPlan, Long>` + `findBySessionIdAndStatus` + `findBySessionIdAndFactionIdAndStatusIn` + native JSONB `@>` query (H2-incompatible, prod-only) |
| `backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt` | @Transactional boundary enforcing D-04 | VERIFIED | EXISTS; `assignOperation` atomically enforces 1-fleet-1-operation; `cancelOperation` idempotent; no direct repo access from commands |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` | missionObjectiveByFleetId + Step 0.6 | VERIFIED | EXISTS; `missionObjectiveByFleetId: MutableMap<Long, MissionObjective>` (ConcurrentHashMap init); Step 0.6 read-through at lines 272-280; `operationParticipantFleetIds: MutableSet<Long>` (ConcurrentHashMap-backed) |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt` | OperationPlanRepository injection + map population | VERIFIED | EXISTS; 5th constructor param `operationPlanRepository: OperationPlanRepository`; `buildInitialState` seeds both SoT fields from ACTIVE+PENDING operations |
| `backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt` | syncOperationToActiveBattles + endBattle merit bonus | VERIFIED | EXISTS; `fun syncOperationToActiveBattles(operation: OperationPlan)` with 4-branch `when` on OperationStatus; `endBattle` has `participantSnapshot` guard and `officer.meritPoints +=` |
| `backend/game-app/src/main/kotlin/com/openlogh/service/OperationLifecycleService.kt` | activatePending + evaluateCompletion + DEFENSE_STABILITY_TICKS=60 | VERIFIED | EXISTS; `const val DEFENSE_STABILITY_TICKS: Int = 60`; `processTick` calls both internal methods; CONQUEST/DEFENSE/SWEEP all covered |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt` | Step 5.5 operationLifecycleService.processTick before battles | VERIFIED | EXISTS; line 82: `operationLifecycleService.processTick(world.id.toLong(), world.tickCount)` in try/catch before `processSessionBattles` |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommand.kt` | troop?.planetId = destPlanetId fix | VERIFIED | EXISTS; line 47: `troop?.planetId = destPlanetId` with Phase 12 comment |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationPlanCommand.kt` | syncOperationToActiveBattles call after assign | VERIFIED | EXISTS; `services.tacticalBattleService?.syncOperationToActiveBattles(plan)` after assignOperation success |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/OperationCancelCommand.kt` | syncOperationToActiveBattles call after cancel | VERIFIED | EXISTS; `services.tacticalBattleService?.syncOperationToActiveBattles(cancelled)` after cancelOperation success |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `TacticalBattleEngine.kt` | `TacticalAIRunner.processAITick` | Step 0.6 runs BEFORE AI tick | WIRED | Lines 272-283: Step 0.6 loop precedes `TacticalAIRunner.processAITick(state)` at line 283 |
| `BattleTriggerService.kt` | `OperationPlanRepository` | constructor injection | WIRED | Line 29: `private val operationPlanRepository: OperationPlanRepository` |
| `TacticalBattleService.endBattle` | `Officer.meritPoints` | `officer.meritPoints +=` | WIRED | Line 397: `officer.meritPoints += awarded` confirmed first accumulation path |
| `TickEngine.kt` | `OperationLifecycleService` | Step 5.5 constructor injection + call | WIRED | Line 46: constructor param; line 82: `operationLifecycleService.processTick(...)` |
| `OperationPlanCommand.kt` | `TacticalBattleService.syncOperationToActiveBattles` | `services.tacticalBattleService?.sync...` | WIRED | Line 83: nullable-safe call after assignOperation |
| `OperationCancelCommand.kt` | `TacticalBattleService.syncOperationToActiveBattles` | `services.tacticalBattleService?.sync...` | WIRED | Line 53: nullable-safe call after cancelOperation |
| `WarpNavigationCommand.kt` | `Fleet.planetId` | `troop?.planetId = destPlanetId` | WIRED | Line 47: one-line fix mirroring IntraSystemNavigationCommand |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `TacticalBattleEngine.processTick` | `unit.missionObjective` | `state.missionObjectiveByFleetId` (ConcurrentHashMap seeded from DB at battle init) | Yes — `BattleTriggerService.buildInitialState` queries `OperationPlanRepository` from DB | FLOWING |
| `TacticalBattleService.endBattle` | `participantSnapshot` | `state.operationParticipantFleetIds` (seeded from DB via BattleTriggerService) | Yes — set populated from real OperationPlan entities | FLOWING |
| `OperationLifecycleService.activatePending` | fleet.planetId | `FleetRepository.findAllById` from DB | Yes — real fleet entities with updated planetId (WarpNavigationCommand fix ensures fresh values) | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Method | Result | Status |
|----------|--------|--------|--------|
| Step 0.6 runs before TacticalAIRunner | `TickEngineOrderingTest.operation lifecycle runs before tactical battle processing` | Verified via `inOrder(operationLifecycleService, tacticalBattleService)` Mockito assertion | PASS |
| Merit ×1.5 for operation participant | `OperationMeritBonusTest.participant receives 1_5x merit bonus` | `(BASE_MERIT * 1.5).toInt() == 150` asserted exactly | PASS |
| Non-participant gets base merit | `OperationMeritBonusTest.non participant gets base merit not bonus` | Base 100, no multiplier | PASS |
| Concurrent cancel race guard | `OperationMeritBonusTest.snapshot prevents concurrent cancel race` | `participantSnapshot.toSet()` before loop | PASS |
| WarpNavigation updates Fleet.planetId | `WarpNavigationCommandTest.updates_fleet_planet_id` | `fleet.planetId == destPlanetId` after run() | PASS |
| BattleTrigger seeds map for operation participants | `BattleTriggerOperationInjectionTest.buildInitialState populates map for participants from operation plan` | `map[42] == CONQUEST`, set contains 42 | PASS |
| Non-participant gets defaultForPersonality | `BattleTriggerOperationInjectionTest.non_participant_gets_default_from_personality` | Map contains fleet 99, NOT in participantSet | PASS |
| syncOperationToActiveBattles adds PENDING/ACTIVE entries | `TacticalBattleServiceSyncTest.sync_adds_entries_for_PENDING_or_ACTIVE_operation` | Both fleets in map + set | PASS |
| syncOperationToActiveBattles removes CANCELLED entries | `TacticalBattleServiceSyncTest.sync_removes_entries_for_CANCELLED_operation` | Fleet 42 removed from both | PASS |
| OperationLifecycleService PENDING→ACTIVE on arrival | `OperationLifecycleServiceTest` activation cases (188 lines) | `fleet.planetId == targetStarSystemId` triggers status flip | PASS |

---

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|---------------|-------------|--------|----------|
| OPS-01 | 12-01, 12-03, 12-04 | 작전계획 목적이 전술전 AI 기본 행동을 결정한다 | SATISFIED | `MissionObjective.defaultForPersonality` (12-01); `missionObjectiveByFleetId` + Step 0.6 (12-03); `syncOperationToActiveBattles` CRUD channel (12-03, 12-04). REQUIREMENTS.md: `[x] OPS-01` |
| OPS-02 | 12-03, 12-04 | 작전에 참가한 부대만 공적 보너스를 받는다 | SATISFIED | `operationParticipantFleetIds` set (12-03); `endBattle` ×1.5 merit with `participantSnapshot` guard (12-04). REQUIREMENTS.md: `[x] OPS-02` |
| OPS-03 | 12-02, 12-04 | 발령된 부대가 목표 성계 도달 시 작전이 시작된다 | SATISFIED | `WarpNavigationCommand.troop?.planetId = destPlanetId` fix (12-02); `OperationLifecycleService.activatePending` (12-04); `TickEngine` Step 5.5 (12-04). REQUIREMENTS.md: `[x] OPS-03` |

---

### Anti-Patterns Found

None. Scan of all Phase 12 source files (`OperationLifecycleService.kt`, `OperationPlanService.kt`, `OperationPlan.kt`, `V47__create_operation_plan.sql`, `TacticalBattleService.kt`, `TacticalBattleEngine.kt`, `BattleTriggerService.kt`, `WarpNavigationCommand.kt`, `OperationPlanCommand.kt`, `OperationCancelCommand.kt`) returned zero matches for `TODO`, `FIXME`, `placeholder`, `not implemented`, `return null`, `return []`, or `return {}`.

The `syncOperationToActiveBattles` COMPLETED branch is a documented intentional no-op (per D-13: completed operations don't disrupt in-flight battles), not a stub.

---

### Out-of-Scope Pre-Existing Failures

Per `.planning/phases/12-operation-integration/deferred-items.md`, the following failures predate Phase 12 and are explicitly out of scope:

- **207 test failures** in `PlanetServiceTest`, `ScenarioServiceTest`, `CityServiceTest` — Three Kingdoms legacy city-name cleanup (`남피`, `하북` etc.), introduced in commits `8ab11cfc` and `2e113181` before Phase 12 began. Verified reproducible at `2d6adfce` (pre-Phase-12 baseline).
- **`DetectionServiceTest.commandRange`** — CRC expansion rate regression (`expected: 100.0 but was: 7.0`), confirmed pre-existing at commit `645459a4^` before any Phase 12 TacticalBattleEngine changes.
- **5 `CommandExecutorTest` NPEs** — Pre-existing NPEs in the command executor test harness. Reproduced identically at `2d6adfce`.

None of these are caused by Phase 12 changes.

---

### Human Verification Required

None. All three success criteria are fully code-verifiable:
- Criterion 1 (AI objective propagation): confirmed via static code analysis of `missionObjectiveByFleetId` map population chain and the `TacticalBattleEngineTest` read-through test.
- Criterion 2 (merit bonus): confirmed via `OperationMeritBonusTest` which asserts exact `baseMerit * 1.5` values and the concurrent-cancel snapshot race test.
- Criterion 3 (auto-start on arrival): confirmed via `WarpNavigationCommandTest` (Fleet.planetId fix), `OperationLifecycleServiceTest` (activation on arrival), and `TickEngineOrderingTest` (Step 5.5 ordering).

---

### Gaps Summary

No gaps. All three success criteria are fully implemented and verified:

1. **OPS-01 (AI objective injection):** Complete data-flow chain from `OperationPlanCommand` → `OperationPlanRepository` → `BattleTriggerService.buildInitialState` → `TacticalBattleState.missionObjectiveByFleetId` → `TacticalBattleEngine` Step 0.6 → `TacticalUnit.missionObjective` → `TacticalAIRunner`. CRUD sync channel (`syncOperationToActiveBattles`) keeps in-flight battles updated.

2. **OPS-02 (merit bonus):** `operationParticipantFleetIds` correctly distinguishes real operation participants from personality-default non-participants. `endBattle` applies exactly ×1.5 multiplier with `participantSnapshot.toSet()` race guard against concurrent CANCELLED sync. First canonical `officer.meritPoints +=` accumulation path in the codebase.

3. **OPS-03 (issue-and-arrive):** `WarpNavigationCommand` now updates `Fleet.planetId` (was stale pre-fix, breaking ~90% of cross-system operations). `OperationLifecycleService.activatePending` correctly detects fleet arrival. `TickEngine` Step 5.5 ensures lifecycle evaluation runs before tactical battle triggering on the same tick, eliminating the activation-vs-battle-trigger race.

---

_Verified: 2026-04-09_  
_Verifier: Claude (gsd-verifier)_
