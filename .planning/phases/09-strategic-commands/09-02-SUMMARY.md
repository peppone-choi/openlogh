---
phase: 09-strategic-commands
plan: 02
subsystem: engine
tags: [kotlin, crc, tactical-battle, command-hierarchy, ai-retreat]

requires:
  - phase: 08-scenario-character-system
    provides: TacticalUnit, CommandRange, CommandHierarchy, TacticalCommand data models
provides:
  - CrcValidator pure-logic object for binary CRC distance check and command reachability
  - OutOfCrcBehavior pure-logic object for maintain-last-order and AI retreat
affects: [09-strategic-commands, 10-tactical-ai]

tech-stack:
  added: []
  patterns: [pure-object stateless utility for tactical engine logic, TDD red-green for engine components]

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CrcValidator.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/OutOfCrcBehavior.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CrcValidatorTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/OutOfCrcBehaviorTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt

key-decisions:
  - "CRC formula: maxRange=50+cmd*3, expansionRate=0.5+cmd/100 -- command=50 gives 200 range, command=100 gives 350"
  - "Self-commands always bypass CRC (Pitfall 1) -- officers always control their own unit"
  - "HP retreat threshold at 30% with 80% BASE_SPEED retreat speed"
  - "Pitfall 5 avoidance: 120 ticks autonomous limit before move-toward-commander fallback at 50% speed"

patterns-established:
  - "Pure object CRC validation: CrcValidator.isWithinCrc/isCommandReachable as stateless functions"
  - "Out-of-CRC priority chain: HP retreat > stuck-unit fallback > maintain last order"

requirements-completed: [CMD-03, CMD-04]

duration: 8min
completed: 2026-04-07
---

# Phase 9 Plan 02: CRC Validation and Out-of-CRC Behavior Summary

**Binary CRC distance validation with self-command bypass and HP<30% AI autonomous retreat for out-of-CRC units**

## Performance

- **Duration:** 8 min
- **Started:** 2026-04-07T11:37:59Z
- **Completed:** 2026-04-07T11:45:29Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- CrcValidator with binary distance check (D-08), self-command bypass (Pitfall 1), fleet/sub-fleet CRC gating
- OutOfCrcBehavior with maintain-last-order default, HP<30% AI retreat, and stuck-unit move-toward-commander fallback (Pitfall 5)
- computeCrcRange formula: maxRange=50+cmd*3, expansionRate=0.5+cmd/100 with exposed constants for tuning
- 31 total unit tests covering all CRC and out-of-CRC behavior scenarios (18 + 13)

## Task Commits

Each task was committed atomically:

1. **Task 1: CrcValidator pure logic** - `8083a12b` (feat)
2. **Task 2: OutOfCrcBehavior pure logic** - `bc922e9c` (feat)

_Both tasks followed TDD: RED (failing tests) -> GREEN (implementation) flow_

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CrcValidator.kt` - Binary CRC check, command reachability, CRC range computation
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/OutOfCrcBehavior.kt` - Out-of-CRC maintain-last-order, AI retreat, move-toward-commander
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CrcValidatorTest.kt` - 18 test cases for CRC validation
- `backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/OutOfCrcBehaviorTest.kt` - 13 test cases for out-of-CRC behavior
- `backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt` - Added subFleetCommanderId and lastCommandTick fields to TacticalUnit

## Decisions Made
- CRC formula uses exposed constants (CRC_BASE_RANGE, CRC_RANGE_PER_COMMAND, etc.) for easy tuning
- Self-commands (officerId == unit.officerId) always bypass CRC to prevent Pitfall 1
- Fleet commander can issue orders to any unit in CRC, even those assigned to sub-fleets
- Sub-fleet commanders can only command their assigned units within their own CRC
- HP retreat is horizontal only (velY=0) toward own side's edge
- Move-toward-commander uses normalized direction vector at 50% BASE_SPEED

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added subFleetCommanderId and lastCommandTick to TacticalUnit**
- **Found during:** Task 1 (CrcValidator)
- **Issue:** Plan 01 (running in parallel) was supposed to add these fields but hadn't committed yet
- **Fix:** Added both fields to TacticalUnit data class in TacticalBattleEngine.kt
- **Files modified:** TacticalBattleEngine.kt
- **Verification:** Compilation successful, all tests pass
- **Committed in:** 8083a12b (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for compilation in parallel execution. No scope creep.

## Issues Encountered
- JDK 25 default on system incompatible with Gradle 8.12; resolved by using JAVA_HOME pointing to JDK 17
- SubFleet.unitFleetIds was already renamed to unitIds by Plan 01; updated test code to match

## User Setup Required

None - no external service configuration required.

## Known Stubs

None - all functions are fully implemented with complete logic.

## Next Phase Readiness
- CrcValidator and OutOfCrcBehavior ready for integration into TacticalBattleEngine.processTick() in Plan 03
- Both are pure objects with no dependencies, trivially injectable into tick loop
- Plan 03 will add CRC gate in drainCommandBuffer() and out-of-CRC processing step

## Self-Check: PASSED

All files exist, all commits found (8083a12b, bc922e9c).

---
*Phase: 09-strategic-commands*
*Completed: 2026-04-07*
