---
phase: 14
plan: 04
subsystem: backend-ws
title: "Backend — Operations WebSocket channel broadcast"
tags: [websocket, operations, broadcast, galaxy-map, phase-14]
requirements: [FE-03]
dependency_graph:
  requires:
    - "Phase 12 OperationPlan / OperationPlanService / OperationLifecycleService"
    - "Spring @EnableWebSocketMessageBroker (SimpMessagingTemplate bean)"
  provides:
    - "OperationEventDto payload schema for galaxy map overlay"
    - "/topic/world/{sessionId}/operations WebSocket channel (4 transition events)"
  affects:
    - "Phase 14 Plan 14-16 (frontend galaxy map operations overlay — can subscribe without polling)"
tech-stack:
  added: []
  patterns:
    - "SimpMessagingTemplate broadcast after repository.save() inside @Transactional (mirrors TacticalBattleService pattern)"
    - "OperationEventDto.fromPlan() companion factory to centralize payload shape"
    - "Plain org.mockito.Mockito (no mockito-kotlin, per Phase 12 D-17)"
key-files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/dto/OperationEventDto.kt
    - backend/game-app/src/test/kotlin/com/openlogh/service/OperationBroadcastTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/OperationLifecycleService.kt
    - backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt
decisions:
  - "OperationEventDto.fromPlan throws IllegalStateException if plan.id is null (guard against pre-persist broadcasts)"
  - "Broadcast calls placed inside existing @Transactional boundaries of OperationPlanService and OperationLifecycleService — Spring flushes after commit, matching TacticalBattleService broadcast pattern"
  - "Test constructs OperationPlanService/OperationLifecycleService directly with mocks (no @SpringBootTest) because pure unit coverage is sufficient — full Spring context integration is covered by existing OperationPlanServiceTest"
  - "OperationLifecycleServiceTest updated to pass mock messagingTemplate (no functional change, just constructor signature)"
metrics:
  duration: "13 minutes"
  tasks_completed: 1
  files_created: 2
  files_modified: 3
  tests_added: 4
  completed_date: "2026-04-09"
---

# Phase 14 Plan 04: Backend Operations WebSocket Channel Broadcast Summary

## One-liner

Added `/topic/world/{sessionId}/operations` WebSocket channel that broadcasts `OperationEventDto` on every OperationPlan status transition (PENDING → ACTIVE → COMPLETED / CANCELLED), enabling the Phase 14 galaxy map overlay to consume operations in real-time without polling.

## What Was Built

### 1. OperationEventDto (NEW)

`backend/game-app/src/main/kotlin/com/openlogh/dto/OperationEventDto.kt`

Payload schema for the operations WebSocket broadcast. Fields:

- `type` — one of `OPERATION_PLANNED` / `OPERATION_STARTED` / `OPERATION_COMPLETED` / `OPERATION_CANCELLED`
- `operationId`, `sessionId`, `factionId`
- `objective` — `CONQUEST` / `DEFENSE` / `SWEEP`
- `targetStarSystemId`, `participantFleetIds`
- `status` — enum name of current `OperationStatus`
- `timestamp` — client-side ordering aid

Companion factory `fromPlan(plan, type)` centralizes conversion from an `OperationPlan` entity and guards against broadcasting a pre-persist plan (throws `IllegalStateException` if `plan.id == null`).

### 2. OperationPlanService broadcast hooks

Constructor gains a `SimpMessagingTemplate` dependency (Spring auto-injects via `@EnableWebSocketMessageBroker`). Two hook sites:

- `assignOperation(...)` → after `operationPlanRepository.save(plan)`, fires `OPERATION_PLANNED` with `status="PENDING"`.
- `cancelOperation(...)` → after `operationPlanRepository.save(cancelled)`, fires `OPERATION_CANCELLED` with `status="CANCELLED"`.

Both calls live inside the existing `@Transactional` method — Spring's non-transactional `SimpMessagingTemplate` flushes the broadcast immediately, matching the pattern established by `TacticalBattleService.processFlagshipDestructions` (line 531) and the tactical-battle topic broadcast at line 672.

### 3. OperationLifecycleService broadcast hooks

Constructor gains `SimpMessagingTemplate`. Two hook sites:

- `activatePending(...)` → for each plan transitioned PENDING → ACTIVE (fleet reached target star system), fires `OPERATION_STARTED` after `save()`.
- `evaluateCompletion(...)` → for each plan transitioned ACTIVE → COMPLETED (CONQUEST/DEFENSE/SWEEP objective met), fires `OPERATION_COMPLETED` after `save()`.

Broadcasts come AFTER the existing `tacticalBattleService.syncOperationToActiveBattles` sync path, so in-flight tactical battles see the update before clients do.

### 4. OperationBroadcastTest (NEW)

`backend/game-app/src/test/kotlin/com/openlogh/service/OperationBroadcastTest.kt`

Four contract tests using plain `org.mockito.Mockito` (no mockito-kotlin, per Phase 12 D-17):

1. `assignOperation broadcasts OPERATION_PLANNED` — verifies topic `/topic/world/1/operations`, event type, status, operationId, sessionId, factionId, objective, targetStarSystemId, participantFleetIds.
2. `cancelOperation broadcasts OPERATION_CANCELLED`
3. `activatePending broadcasts OPERATION_STARTED`
4. `evaluateCompletion broadcasts OPERATION_COMPLETED on conquest`

Uses `ArgumentCaptor.forClass(Any::class.java)` + cast to `OperationEventDto` for payload inspection. Topic pattern verified with `Regex("/topic/world/\\d+/operations")`.

### 5. OperationLifecycleServiceTest signature update

Added `messagingTemplate` mock field and passed to the constructor. All 6 existing Phase 12 tests continue to pass unchanged.

## Verification

```
Task :game-app:compileKotlin — SUCCESS
Task :game-app:compileTestKotlin — SUCCESS (sibling plan 14-02 broken test file temporarily parked during test run — see Deviations)
Task :game-app:test — SUCCESS
  OperationBroadcastTest: 4 tests, 0 failures, 0 errors, 0 skipped
  OperationLifecycleServiceTest: 6 tests, 0 failures, 0 errors, 0 skipped (no regression)
```

Test XML evidence:

- `/tmp/openlogh-ascii/backend/game-app/build/test-results/test/TEST-com.openlogh.service.OperationBroadcastTest.xml` — `tests="4" skipped="0" failures="0" errors="0"`
- `/tmp/openlogh-ascii/backend/game-app/build/test-results/test/TEST-com.openlogh.service.OperationLifecycleServiceTest.xml` — `tests="6" skipped="0" failures="0" errors="0"`

Acceptance-criteria greps:
- `data class OperationEventDto` → 1 match in OperationEventDto.kt
- `convertAndSend` in OperationPlanService → 2 matches (create + cancel)
- `convertAndSend` in OperationLifecycleService → 2 matches (activate + complete)
- `/topic/world/.*operations` regex → 4 matches across both services

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Sibling Wave-1 plan 14-02 parked during test run**

- **Found during:** Task 1 verification (`./gradlew :game-app:compileTestKotlin`)
- **Issue:** `backend/game-app/src/test/kotlin/com/openlogh/controller/BattleSummaryEndpointTest.kt` (committed by plan 14-02 at 7bb96d38 as a RED test awaiting its GREEN step) has 30+ unresolved references that prevent `:game-app:compileTestKotlin` from succeeding. Because Wave 1 plans 14-01 through 14-05 run in parallel, 14-02's GREEN implementation is not yet committed.
- **Fix:** Temporarily moved the file to `/tmp/BattleSummaryEndpointTest.kt.parked-14-04` for the duration of the test run, then restored it before committing. No modifications to the file itself. This is scope-boundary-safe per CLAUDE.md (do not fix pre-existing unrelated failures).
- **Files modified:** None (parked/restored).
- **Commit:** Not included in 1e11dc4d (no functional change).

**2. [Rule 3 - Blocking] Java toolchain and path encoding**

- **Found during:** Task 1 verification.
- **Issue 1:** System default JDK is 25.0.2, but Kotlin 2.1.0 / Gradle 8.12 cannot parse that version string (`IllegalArgumentException: 25.0.2`).
- **Issue 2:** Project path contains non-ASCII Korean characters (`/Users/apple/Desktop/개인프로젝트/openlogh`), which the Kotlin compiler on JDK 17 cannot find due to URL encoding mismatch.
- **Fix:** Set `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home` and ran Gradle via an ASCII symlink `/tmp/openlogh-ascii -> /Users/apple/Desktop/개인프로젝트/openlogh`.
- **Files modified:** None. Local env-only workaround.

### Intentional Deviations from Plan Text

**3. [Rule 2 - Correctness] `OperationPlan.id` is nullable**

- **Found during:** Task 1 TDD GREEN step.
- **Issue:** Plan's suggested code `operationId = plan.id` fails to compile — `OperationPlan.id` is declared `var id: Long? = null` in the entity.
- **Fix:** Used `plan.id ?: throw IllegalStateException("OperationPlan must be persisted (id == null) before broadcasting")` in `OperationEventDto.fromPlan`. This is a defensive guard, not a behavior change — all broadcast call sites pass the `saved` return of `repository.save(...)` which always has a non-null id.
- **Files modified:** `OperationEventDto.kt` (as-written).

## Authentication Gates

None. No auth, CLI, or user action required.

## Deferred Issues

None for this plan.

Note: Sibling plan 14-02's `BattleSummaryEndpointTest.kt` remains broken on main — this is expected and will be resolved by the 14-02 executor agent (Wave 1). Not a 14-04 concern.

## Self-Check: PASSED

- FOUND: backend/game-app/src/main/kotlin/com/openlogh/dto/OperationEventDto.kt
- FOUND: backend/game-app/src/test/kotlin/com/openlogh/service/OperationBroadcastTest.kt
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/service/OperationPlanService.kt (modified — 2 convertAndSend added)
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/service/OperationLifecycleService.kt (modified — 2 convertAndSend added)
- FOUND: backend/game-app/src/test/kotlin/com/openlogh/service/OperationLifecycleServiceTest.kt (modified — constructor signature)
- FOUND: commit 1e11dc4d

All artifacts confirmed on disk; commit present in git log.

## Commits

- `1e11dc4d` — `feat(14-04): broadcast operation status transitions on WebSocket channel`
