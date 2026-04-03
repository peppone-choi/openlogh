---
phase: 09-turn-engine-completion
plan: 01
subsystem: engine
tags: [turn-engine, stub-methods, legacy-parity, kotlin]

requires:
  - phase: 08-npc-ai-gap-closure
    provides: NPC AI methods and TurnService integration
provides:
  - checkWander wander nation auto-dissolution via 해산 command
  - updateOnline per-tick online count and nation grouping in world.meta
  - checkOverhead refreshLimit recalculation per legacy formula
  - updateGeneralNumber nation gennum refresh from active general counts
  - postUpdateMonthly call ordering corrected to match legacy PHP
affects: [09-02-turn-step-ordering-verification, turn-engine, game-parity]

tech-stack:
  added: []
  patterns:
    - "checkWander uses CommandRegistry.createGeneralCommand + checkFullCondition + run pattern for command-based dissolution"
    - "updateOnline joins GeneralAccessLog with General entity to map online generals to nations"

key-files:
  created: []
  modified:
    - backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/TurnServiceTest.kt

key-decisions:
  - "checkOverhead formula: round(turnterm^0.6 * 3) * refreshLimitCoef -- verified 300^0.6=30.64, result=920 for tickSeconds=300"
  - "updateOnline filters accessLogs by accessedAt >= world.updatedAt for recent-access detection (no lastRefresh field on entity)"
  - "checkWander year guard placed inside method (not caller) for encapsulation -- guard still effective"

patterns-established:
  - "Command-based auto-actions: checkWander creates GeneralCommand via registry, checks constraints, then runs -- reusable for future auto-dissolution patterns"

requirements-completed: [TURN-01, TURN-02, TURN-03, TURN-04, TURN-05]

duration: 7min
completed: 2026-04-02
---

# Phase 9 Plan 1: Turn Engine Stub Implementation Summary

**4 TurnService stub methods implemented with legacy PHP parity + postUpdateMonthly ordering fixed to match func_gamerule.php**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-02T09:41:22Z
- **Completed:** 2026-04-02T09:48:30Z
- **Tasks:** 1 (TDD: RED + GREEN)
- **Files modified:** 2

## Accomplishments
- Implemented checkWander: dissolves wander nations (level=0) after startYear+2 via CommandRegistry 해산 command
- Implemented updateOnline: per-tick online count and nation grouping stored in world.meta
- Implemented checkOverhead: refreshLimit = round(turnterm^0.6 * 3) * refreshLimitCoef
- Implemented updateGeneralNumber: counts non-npcState5 generals per nation, saves gennum via nationRepository.saveAll
- Fixed postUpdateMonthly ordering from checkWander->triggerTournament->registerAuction->updateGeneralNumber to checkWander->updateGeneralNumber->triggerTournament->registerAuction (legacy parity)
- All 28 TurnServiceTest tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED): Add failing tests for 4 stub methods** - `43e5720` (test)
2. **Task 1 (GREEN): Implement 4 stub methods and fix ordering** - `f513ba3` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` - 4 stub methods implemented, postUpdateMonthly ordering fixed
- `backend/game-app/src/test/kotlin/com/opensam/engine/TurnServiceTest.kt` - 8 new tests for checkWander (3), updateOnline (1), checkOverhead (2), updateGeneralNumber (2)

## Decisions Made
- checkOverhead golden value: 300^0.6 = 30.64, not 36.517 as plan stated -- corrected test to expect 920
- updateOnline uses GeneralAccessLog.accessedAt >= world.updatedAt since entity has no lastRefresh field
- checkWander year guard placed inside the method body for encapsulation (plan suggested caller-site)
- checkWander uses CommandRegistry.createGeneralCommand (not commandRegistry.create which doesn't exist)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected checkOverhead golden value calculation**
- **Found during:** Task 1 GREEN phase
- **Issue:** Plan specified round(300^0.6 * 3) * 10 = 1100, but actual 300^0.6 = 30.64 (not 36.517), giving 920
- **Fix:** Updated test expected value from 1100 to 920
- **Files modified:** TurnServiceTest.kt
- **Verification:** Test passes with correct value
- **Committed in:** f513ba3

**2. [Rule 3 - Blocking] Adapted updateOnline for actual entity schema**
- **Found during:** Task 1 GREEN phase
- **Issue:** Plan assumed GeneralAccessLog has lastRefresh and nationId fields; actual entity has accessedAt and no nationId
- **Fix:** Filter by accessedAt >= world.updatedAt; join with General entity for nationId mapping
- **Files modified:** TurnService.kt
- **Verification:** updateOnline test passes
- **Committed in:** f513ba3

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both fixes necessary for correctness against actual entity schema. No scope creep.

## Issues Encountered
- JDK 25 incompatible with Gradle 8.12; used JDK 23 for compilation/testing (pre-existing environment issue)

## Known Stubs
None -- all 4 stub methods are now fully implemented.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All turn pipeline stubs filled -- ready for Plan 2 (turn step ordering verification + disaster golden value tests)
- postUpdateMonthly ordering now matches legacy, enabling accurate ordering assertion tests

---
*Phase: 09-turn-engine-completion*
*Completed: 2026-04-02*
