---
phase: 10-tactical-combat
plan: 07
subsystem: tactical-engine
tags: [succession, command-breakdown, ai-behavior, succ-06]

requires:
  - phase: 10-05
    provides: "SuccessionService with designateSuccessor, delegateCommand, getActiveCommander"
provides:
  - "SuccessionService.findNextSuccessor() for designated/queue-based successor lookup"
  - "SuccessionService.isCommandBroken() detecting total command failure"
  - "TacticalBattleEngine.processCommandBreakdown() step 5.4 transitioning units to independent AI"
affects: [10-tactical-combat, tactical-ai]

tech-stack:
  added: []
  patterns: ["command breakdown -> OutOfCrcBehavior fallback for all alive units"]

key-files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CommandBreakdownTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/SuccessionService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt

key-decisions:
  - "findNextSuccessor checks designatedSuccessor first, then walks successionQueue in rank order"
  - "processCommandBreakdown passes null commanderUnit to OutOfCrcBehavior so HP<30% units retreat, healthy units maintain velocity"

patterns-established:
  - "Command breakdown is terminal state: once detected, all units on that side run autonomous AI each tick"

requirements-completed: [SUCC-06]

duration: 5min
completed: 2026-04-07
---

# Phase 10 Plan 07: Command Breakdown Summary

**SUCC-06 command breakdown detection: isCommandBroken() triggers OutOfCrcBehavior for all units when entire command hierarchy is dead**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-07T13:22:16Z
- **Completed:** 2026-04-07T13:26:50Z
- **Tasks:** 1
- **Files modified:** 3

## Accomplishments
- Added findNextSuccessor() to SuccessionService checking designated successor then succession queue
- Added isCommandBroken() to SuccessionService detecting total command failure (active commander dead + no successor)
- Added processCommandBreakdown() as step 5.4 in TacticalBattleEngine.processTick() applying OutOfCrcBehavior to all alive non-retreating units
- Broadcasts command_broken_ai BattleTickEvent with affected unit count
- 8 unit tests covering detection logic, behavior transitions, and successor lookup

## Task Commits

Each task was committed atomically:

1. **Task 1: Add isCommandBroken() to SuccessionService + processCommandBreakdown() to engine** - `4540068f` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/SuccessionService.kt` - Added findNextSuccessor() and isCommandBroken() methods
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` - Added processCommandBreakdown() private method and step 5.4 call in processTick()
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CommandBreakdownTest.kt` - 8 unit tests for command breakdown detection and behavior

## Decisions Made
- findNextSuccessor checks designatedSuccessor first, then walks successionQueue in rank order -- consistent with gin7 manual priority
- processCommandBreakdown passes null as commanderUnit to OutOfCrcBehavior so HP<30% units auto-retreat while healthy units maintain last velocity

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added findNextSuccessor() method**
- **Found during:** Task 1
- **Issue:** Plan references findNextSuccessor() from 10-06 (running in parallel) but it does not exist yet in SuccessionService
- **Fix:** Implemented findNextSuccessor() directly -- checks designated successor first, then queue in order
- **Files modified:** SuccessionService.kt
- **Verification:** 3 dedicated tests pass (designated alive, designated dead fallback, nobody alive)
- **Committed in:** 4540068f (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Required for isCommandBroken() to work. May need merge reconciliation with 10-06 which adds the same method.

## Issues Encountered
- JDK 25 incompatible with Gradle 8.12 -- used JAVA_HOME override to JDK 17 for compilation and tests

## Known Stubs
None - all methods are fully wired with real logic.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Command breakdown detection completes the SUCC-06 succession chain safety net
- All surviving units properly fall back to independent AI when command hierarchy collapses
- May need merge conflict resolution with 10-06 for findNextSuccessor() in SuccessionService.kt

---
*Phase: 10-tactical-combat*
*Completed: 2026-04-07*
