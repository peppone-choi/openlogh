---
phase: 05-ai-system
plan: "03"
subsystem: AI System
tags: [ai, scenario-events, civil-war, tick-engine, integration-test]
dependency_graph:
  requires: [05-01, 05-02]
  provides: [ScenarioEventAIService, AiIntegrationTest]
  affects: [TickEngine, FactionMeta]
tech_stack:
  added: []
  patterns: [hand-rolled-spy, tdd-green, kotlin-mockito-safe-patterns]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/ScenarioEventAIService.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/ScenarioEventAIServiceTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/AiIntegrationTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineTest.kt
decisions:
  - "ScenarioEventAIService uses hand-rolled WorldPorts spy to avoid Kotlin non-null + Mockito any() NPE — pattern reused in AiIntegrationTest"
  - "Civil war threshold: supportRatio >= 0.4 OR ticksActive > 300 — dual condition matches gin7 design intent"
  - "FactionAISchedulerTest pre-existing failures confirmed before this plan; not caused by plan-03 changes"
metrics:
  duration_minutes: 35
  completed_date: "2026-04-07"
  tasks: 2
  files: 5
---

# Phase 05 Plan 03: ScenarioEventAI + Integration Test Summary

**One-liner:** Coup condition detection + civil war trigger AI wired into TickEngine every 100 ticks, sealed with AI-01~04 integration test coverage.

## What Was Done

### Task 1: ScenarioEventAIService (TDD)

Created `ScenarioEventAIService.kt` implementing:
- Empire faction coup condition check every processTick call
- Triggers civil war when: `coupPhase == ACTIVE` AND (`supportRatio >= 0.4` OR `ticksActive > 300`)
- Idempotent: skips factions already with `meta[civilWarTriggered] = true`
- Only evaluates `factionType == "empire"` — alliance/fezzan ignored
- On trigger: sets `faction.meta[civilWarTriggered] = true`, persists via `ports.putFaction()`, broadcasts via `gameEventService.broadcastWorldUpdate()`

**TDD approach:** 5 tests written first (RED), then implementation (GREEN):
1. Civil war triggers on ACTIVE coup + threshold met
2. PLANNING phase does not trigger
3. Idempotency (already triggered → skip)
4. Non-empire factions not evaluated
5. Empty faction list is a no-op

**Kotlin/Mockito fix:** Used hand-rolled `SpyWorldPorts` and `SpyGameEventService` inner classes to avoid `any()` returning null for Kotlin non-null parameters — established pattern for this codebase.

### Task 2: TickEngine Wiring + AiIntegrationTest

**TickEngine wiring:**
- Added `ScenarioEventAIService` constructor param
- Hook in `processPolitics()`: `if (world.tickCount % 100 == 0L) scenarioEventAIService.processTick(world)`

**AiIntegrationTest (4 tests, AI-01~04):**
- AI-01: AGGRESSIVE trait scores OPERATIONS/COMMAND groups highest via `UtilityScorer.scoreGroups()`
- AI-02: Offline player with 2h inactivity triggers `AiCommandBridge.executeAiCommand()` via `SpyAiCommandBridge`
- AI-03: `FactionAIScheduler.processTick()` calls `decideNationAction` exactly once per tick via `SpyFactionAIPort`
- AI-04: Civil war triggers on ACTIVE coup + military threshold via `ScenarioEventAIService`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Hand-rolled spy pattern for Kotlin/Mockito compatibility**
- **Found during:** Task 1 test implementation
- **Issue:** `ArgumentCaptor.capture()` and `any()` return null in Kotlin non-null context, causing NPE when verifying methods with non-null parameters (`broadcastWorldUpdate(worldId: Long, data: Any)`, `putFaction(snapshot: FactionSnapshot)`)
- **Fix:** Replaced Mockito capture/verify with hand-rolled `SpyWorldPorts`, `SpyGameEventService`, `SpyFactionAIPort`, `SpyAiCommandBridge` inner classes that record calls in mutable lists
- **Files modified:** ScenarioEventAIServiceTest.kt, AiIntegrationTest.kt
- **Pattern:** Consistent with `FactionAISchedulerTest` which uses `doReturn` workaround for same reason

**2. [Rule 1 - Bug] Pre-existing test failures documented**
- `FactionAISchedulerTest`: 4 tests fail with same `capture() must not be null` NPE — pre-existing before plan-03, confirmed via `git stash` verification
- `NpcPolicyTest`: 1 test fails with AssertionFailedError — pre-existing
- `GeneralAITest`: test-ordering Mockito pollution when run with other tests — pre-existing
- **Action:** Documented as out-of-scope; logged to deferred items

## Known Stubs

None — all trigger/detection logic is fully implemented.

## Deferred Items

- Pre-existing `FactionAISchedulerTest` failures (4 tests): `capture() must not be null` NPE — same Kotlin/Mockito pattern issue in the test itself, not the service
- Pre-existing `NpcPolicyTest` failure: `default priority lists match expected order()` assertion mismatch
- Pre-existing `GeneralAITest` Mockito pollution when run alongside other test classes

## Self-Check: PASSED

- FOUND: ScenarioEventAIService.kt
- FOUND: ScenarioEventAIServiceTest.kt
- FOUND: AiIntegrationTest.kt
- FOUND commit acc735e6: ScenarioEventAIService TDD
- FOUND commit 666374ab: TickEngine wiring + AiIntegrationTest
