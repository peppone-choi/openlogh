---
phase: 02-real-time-tick-engine
plan: 02
subsystem: engine
tags: [tick-engine, real-time, game-clock, tdd]
dependency_graph:
  requires: [02-01]
  provides: [TickEngine, processTick, advanceMonth]
  affects: [TurnDaemon, RealtimeService]
tech_stack:
  added: []
  patterns: [tdd-red-green, service-delegation, modular-gating]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/TickEngineTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/TurnDaemon.kt
    - backend/game-app/src/test/kotlin/com/openlogh/test/InMemoryTurnHarness.kt
decisions:
  - Monthly pipeline uses placeholder (log + broadcast) until TurnPipeline steps are decoupled from batch command execution
  - CP regen gated by tickCount modulo rather than wall-clock tracking
  - fixedRate used instead of fixedDelay to maintain tick cadence under load
metrics:
  duration: 600s
  completed: 2026-04-05T12:14:43Z
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 2
---

# Phase 2 Plan 02: TickEngine Core Summary

TickEngine service processing 1 tick/second at 24x game speed with month boundary detection, CP regen gating every 300 ticks, and TurnDaemon integration

## What Was Built

### TickEngine.kt (84 lines)
Core real-time tick processor as a Spring `@Service` with three dependencies: `RealtimeService`, `SessionStateRepository`, `GameEventService`.

**`processTick(world)`** method performs per-tick operations:
1. Increments `tickCount` and advances `gameTimeSec` by 24
2. Calls `processCompletedCommands` every tick
3. Gates CP regeneration to every 300 ticks (5 real minutes)
4. Detects month boundary at 2,592,000 game-seconds, advances month with year rollover
5. Persists world state

### TickEngineTest.kt (117 lines)
8 unit tests using JUnit 5 + Mockito:
- `test_single_tick_advances_game_time` -- tickCount=1, gameTimeSec=24
- `test_100_ticks` -- tickCount=100, gameTimeSec=2400
- `test_cp_regen_fires_at_300_ticks` -- verified exactly once at tick 300
- `test_cp_regen_does_not_fire_at_tick_zero` -- no regen on first tick
- `test_cp_regen_does_not_fire_at_299_or_301` -- boundary precision
- `test_month_boundary_crossing` -- gameTimeSec resets, month advances
- `test_year_rollover` -- month 12 -> month 1, year increments
- `test_monthly_pipeline_fires_only_on_boundary` -- no pipeline on normal ticks
- `test_completed_commands_processed_every_tick` -- called 5/5 times

### TurnDaemon.kt Changes
- Added `TickEngine` as constructor dependency
- Changed `@Scheduled(fixedDelayString = "${app.turn.interval-ms:5000}")` to `@Scheduled(fixedRateString = "${app.turn.tick-ms:1000}")`
- Realtime branch now delegates to `tickEngine.processTick(world)` instead of calling `realtimeService` directly
- Turn-based path unchanged for backward compatibility

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed pre-existing InMemoryTurnHarness compilation errors**
- **Found during:** Task 1 (RED phase)
- **Issue:** InMemoryTurnHarness.kt had 6+ compilation errors from the domain rename (General->Officer, City->Planet, Nation->Faction, GeneralTurn->OfficerTurn, NationTurn->FactionTurn, old method names)
- **Fix:** Updated class references, constructor parameter names, and repository method names to match current entity/repository definitions
- **Files modified:** `backend/game-app/src/test/kotlin/com/openlogh/test/InMemoryTurnHarness.kt`
- **Commit:** 2e90c8a9

**2. [Rule 3 - Blocking] 60+ other test files have pre-existing compilation errors from domain rename**
- **Found during:** Task 1 (RED phase)
- **Issue:** Kotlin compiles all test sources together; 60+ test files across command/, engine/, service/, qa/ packages have unresolved references from the domain rename
- **Action:** Out of scope -- logged as deferred item. Used temporary source exclusion during test verification, then reverted. Only InMemoryTurnHarness was fixed as it was directly blocking test compilation in the harness layer.

## Known Stubs

**1. Monthly pipeline placeholder** -- `TickEngine.kt:75-82`
- `runMonthlyPipeline()` currently only logs and broadcasts `broadcastTurnAdvance`
- Does NOT execute economy, diplomacy, maintenance, NPC AI steps
- Reason: TurnService.processWorld combines batch command execution with monthly processing; cannot be called directly without refactoring
- Resolution: Will be wired when TurnPipeline steps are decoupled (future plan)

## Decisions Made

1. **Monthly pipeline as placeholder**: The existing `TurnService.processWorld` entangles batch command execution with monthly processing. Rather than refactoring TurnService in this plan, the monthly pipeline logs and broadcasts. Full pipeline wiring deferred.
2. **Tick counter modulo for CP regen**: Using `tickCount % 300 == 0` is simpler and more deterministic than wall-clock tracking. No per-officer `lastCpRegenTime` needed.
3. **fixedRate over fixedDelay**: `fixedRate` maintains cadence even if tick processing takes time, preventing accumulated drift.

## Self-Check: PASSED

- [x] TickEngine.kt exists
- [x] TickEngineTest.kt exists
- [x] Commit 2e90c8a9 exists (Task 1: TickEngine + tests)
- [x] Commit 6e9b520c exists (Task 2: TurnDaemon wiring)
