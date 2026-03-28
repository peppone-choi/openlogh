---
phase: 01-session-foundation
plan: 01
subsystem: database, engine
tags: [jpa, optimistic-locking, flyway, concurrency, thread-pool, websocket]

# Dependency graph
requires: []
provides:
    - '@Version optimistic locking on Officer entity preventing CP race condition'
    - 'Flyway V38 migration adding version BIGINT column to officer table'
    - 'CommandExecutor.withOptimisticRetry for 3-attempt retry on version conflicts'
    - 'TacticalTurnScheduler.scheduleOnce for managed one-shot delayed tasks'
    - 'HARD-02 executor leak fix: TacticalWebSocketController uses managed scheduler'
affects: [02-character-rank, 04-command-point, 06-tactical-combat]

# Tech tracking
tech-stack:
    added: []
    patterns:
        - 'Optimistic locking via JPA @Version for concurrent entity mutation safety'
        - 'Retry-on-conflict pattern: withOptimisticRetry re-reads entity up to 3 times'
        - 'Managed thread pool delegation: never create inline Executors in controller methods'

key-files:
    created:
        - 'backend/game-app/src/main/resources/db/migration/V38__add_officer_version_column.sql'
        - 'backend/game-app/src/test/kotlin/com/openlogh/entity/OfficerOptimisticLockTest.kt'
        - 'backend/game-app/src/test/kotlin/com/openlogh/websocket/TacticalExecutorLeakTest.kt'
    modified:
        - 'backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt'
        - 'backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt'
        - 'backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalTurnScheduler.kt'
        - 'backend/game-app/src/main/kotlin/com/openlogh/websocket/TacticalWebSocketController.kt'

key-decisions:
    - 'V38 migration number (not V10) because 37 migrations already exist'
    - 'Officer @Version as Long (not Int) to match PostgreSQL BIGINT column type'
    - 'withOptimisticRetry placed in CommandExecutor for proximity to command execution path'
    - 'scheduleOnce reuses existing 4-thread pool in TacticalTurnScheduler rather than creating a new pool'

patterns-established:
    - 'Optimistic locking: all concurrent-write entities should use @Version + withOptimisticRetry'
    - 'Managed scheduling: delayed tasks must use TacticalTurnScheduler.scheduleOnce, never inline Executors'

requirements-completed: [HARD-01, HARD-02]

# Metrics
duration: 14min
completed: 2026-03-28
---

# Phase 01 Plan 01: Concurrency Bug Fixes Summary

**Officer @Version optimistic locking preventing CP race condition (HARD-01) and managed scheduler eliminating tactical executor thread leak (HARD-02)**

## Performance

- **Duration:** 14 min
- **Started:** 2026-03-28T11:44:51Z
- **Completed:** 2026-03-28T11:59:47Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- Officer entity now has JPA @Version optimistic locking, preventing two concurrent CP consumption attempts from both succeeding when only 1 CP remains
- CommandExecutor.withOptimisticRetry provides a 3-attempt retry wrapper that re-reads the officer on version conflict
- Flyway V38 migration adds version BIGINT column with DEFAULT 0 for all existing rows
- TacticalWebSocketController no longer leaks a JVM thread every time a tactical battle ends
- TacticalTurnScheduler.scheduleOnce exposes a managed one-shot scheduling method with @PreDestroy lifecycle cleanup

## Task Commits

Each task was committed atomically:

1. **Task 1: Add @Version optimistic locking to Officer entity with Flyway migration and retry handler** - `c813117` (fix)
2. **Task 2: Fix tactical executor thread leak using managed scheduler** - `ef6d972` (fix)

## Files Created/Modified

- `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` - Added @Version Long field for optimistic locking
- `backend/game-app/src/main/resources/db/migration/V38__add_officer_version_column.sql` - Flyway migration adding version BIGINT column
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` - Added withOptimisticRetry method with OptimisticLockingFailureException handling
- `backend/game-app/src/test/kotlin/com/openlogh/entity/OfficerOptimisticLockTest.kt` - 4 tests: @Version annotation, retry success, retry exhaustion, officer not found
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalTurnScheduler.kt` - Added scheduleOnce method for managed one-shot tasks
- `backend/game-app/src/main/kotlin/com/openlogh/websocket/TacticalWebSocketController.kt` - Replaced inline Executors.newSingleThreadScheduledExecutor with turnScheduler.scheduleOnce
- `backend/game-app/src/test/kotlin/com/openlogh/websocket/TacticalExecutorLeakTest.kt` - 4 tests: execution after delay, cancellation, source-level leak check, shutdown termination

## Decisions Made

- Used V38 migration number instead of V10 as plan specified, because 37 migrations already exist in the codebase
- Officer @Version field typed as Long (not Int) to match PostgreSQL BIGINT for JPA compatibility
- withOptimisticRetry placed directly in CommandExecutor class for proximity to the command execution path
- scheduleOnce reuses the existing 4-thread ScheduledThreadPool in TacticalTurnScheduler rather than creating a dedicated pool

## Deviations from Plan

### Plan Adjustments

**1. Migration file numbering: V38 instead of V10**

- **Found during:** Task 1
- **Issue:** Plan specified V10\_\_add_officer_version_column.sql but V10 through V37 already exist
- **Fix:** Used V38\_\_add_officer_version_column.sql (next available number)
- **Impact:** None - functionally identical, just correct sequencing

### Test Execution Limitation

**2. Pre-existing test compilation errors prevent full test suite execution**

- **Found during:** Task 1 verification
- **Issue:** 9 pre-existing broken test files (NationResourceCommandTest, DiplomacyServiceTest, InMemoryTurnHarness, etc.) prevent `compileTestKotlin` from succeeding
- **Scope:** Out of scope - these errors are NOT caused by plan changes
- **Mitigation:** Main source compiles successfully; test logic verified through code inspection and file-level acceptance checks
- **Logged to:** deferred-items.md (already documented from Plan 03)

## Issues Encountered

- JAVA_HOME was set to invalid directory (/opt/homebrew/opt/openjdk@17); resolved by using Amazon Corretto 17 at /Users/apple/Library/Java/JavaVirtualMachines/corretto-17.0.17/Contents/Home

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Officer entity is safe for concurrent CP mutations via @Version + withOptimisticRetry
- Tactical battle cleanup is leak-free via managed scheduler
- Ready for Phase 02 (character-rank) which builds on Officer entity
- Pre-existing test compilation errors should be addressed before adding more test coverage

## Self-Check: PASSED

- All 3 created files exist on disk
- All 4 modified files verified via grep checks
- Commit c813117 (Task 1) verified in git log
- Commit ef6d972 (Task 2) verified in git log
- Main source compiles successfully (compileKotlin passes)

---

_Phase: 01-session-foundation_
_Completed: 2026-03-28_
