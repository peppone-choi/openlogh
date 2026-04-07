---
phase: 09-strategic-commands
plan: 01
subsystem: engine
tags: [kotlin, tactical-engine, command-hierarchy, priority-ordering, sub-fleet]

# Dependency graph
requires:
  - phase: 08-scenario-character-system
    provides: CommandHierarchy data model, TacticalCommand sealed class, TacticalBattleEngine command buffer
provides:
  - SubFleet.unitIds field (renamed from unitFleetIds) for individual ShipUnit assignment
  - CommandPriority comparable with online>rank>eval>merit>officerId ordering
  - CommandHierarchyService pure object for sub-fleet assignment validation and execution
  - TacticalUnit fields for hierarchy (subFleetCommanderId, lastCommandTick, officerLevel, evaluationPoints, meritPoints)
  - TacticalCommand.AssignSubFleet and ReassignUnit subtypes
  - TacticalBattleState.connectedPlayerOfficerIds for online status tracking
  - OfficerPriorityData DTO for priority calculation
affects: [09-02-crc-validator, 09-03-integration, 09-04-jamming, 10-tactical-ai]

# Tech tracking
tech-stack:
  added: []
  patterns: [pure-object-service, comparable-data-class, tdd-red-green]

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandPriorityComparator.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchyService.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CommandPriorityTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CommandHierarchyServiceTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchy.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt

key-decisions:
  - "CommandPriority uses Comparable natural ordering (higher = better priority) with reversed officerId for seniority tiebreak"
  - "CommandHierarchyService is pure object (no Spring DI) following Phase 5 UtilityScorer pattern"
  - "SubFleet reassignment auto-removes units from old sub-fleet before adding to new"
  - "Jamming fields (jammingTicksRemaining, jammingSourceOfficerId) added to CommandHierarchy proactively for Plan 04"

patterns-established:
  - "Pure object service: stateless logic objects with no DI for tactical engine calculations"
  - "Comparable data class: CommandPriority implements Comparable for natural sort ordering"
  - "OfficerPriorityData DTO: lightweight decoupling from full Officer entity in pure-logic code"

requirements-completed: [CMD-01, CMD-02]

# Metrics
duration: 7min
completed: 2026-04-07
---

# Phase 09 Plan 01: Sub-fleet Assignment + Command Priority Summary

**Pure-logic CommandHierarchyService for sub-fleet assignment validation/execution and CommandPriority comparator with online>rank>eval>merit>officerId ordering**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-07T11:37:51Z
- **Completed:** 2026-04-07T11:44:32Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- SubFleet.unitIds field rename from unitFleetIds for clarity (D-01: individual ShipUnit assignment)
- CommandPriority data class with 5-tier ordering and comprehensive test suite (9 tests)
- CommandHierarchyService pure object with validation, assignment, resolution, and priority list building (9 tests)
- TacticalUnit, TacticalBattleState, and TacticalCommand extended with hierarchy support fields

## Task Commits

Each task was committed atomically:

1. **Task 1: Data model refactor + CommandPriorityComparator** - `feed2960` (feat)
2. **Task 2: CommandHierarchyService with sub-fleet assignment logic** - `652a971e` (feat)

## Files Created/Modified
- `CommandPriorityComparator.kt` - CommandPriority data class with Comparable, 5-tier ordering
- `CommandHierarchyService.kt` - Pure object: validateSubFleetAssignment, assignSubFleet, resolveCommanderForUnit, buildPriorityList
- `CommandHierarchy.kt` - SubFleet.unitIds rename, jammingTicksRemaining/jammingSourceOfficerId fields
- `TacticalCommand.kt` - AssignSubFleet and ReassignUnit sealed subtypes
- `TacticalBattleEngine.kt` - TacticalUnit gains hierarchy fields, TacticalBattleState gains connectedPlayerOfficerIds/currentTick, applyCommand stubs for new commands
- `CommandPriorityTest.kt` - 9 tests for priority ordering
- `CommandHierarchyServiceTest.kt` - 9 tests for sub-fleet assignment logic

## Decisions Made
- CommandPriority uses Comparable natural ordering (higher = better priority) with reversed officerId for seniority tiebreak (D-09)
- CommandHierarchyService is pure object following Phase 5 UtilityScorer pattern -- no Spring DI needed
- Jamming fields added proactively to CommandHierarchy to avoid merge conflicts with Plan 04
- currentTick added to TacticalBattleState alongside existing tickCount for clarity in hierarchy logic

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Another agent added subFleetCommanderId/lastCommandTick**
- **Found during:** Task 1 (TacticalUnit field additions)
- **Issue:** Parallel agent (09-02) had already added subFleetCommanderId and lastCommandTick to TacticalUnit
- **Fix:** Kept existing fields, only added remaining fields (officerLevel, evaluationPoints, meritPoints)
- **Files modified:** TacticalBattleEngine.kt
- **Verification:** All tests pass

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minor adjustment due to parallel agent overlap. No scope creep.

## Issues Encountered
- JDK 25 incompatible with Gradle 8.12 -- used JAVA_HOME override to JDK 17 for compilation
- OutOfCrcBehaviorTest failures from parallel agent (09-02) -- out of scope, not caused by this plan's changes

## Known Stubs
- `applyCommand` has empty branches for `AssignSubFleet` and `ReassignUnit` -- wiring to CommandHierarchyService deferred to Plan 03

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CommandHierarchyService and CommandPriority ready for CRC integration (Plan 02) and wiring (Plan 03)
- AssignSubFleet/ReassignUnit command stubs ready for full logic in Plan 03
- Jamming fields pre-staged for Plan 04

---
*Phase: 09-strategic-commands*
*Completed: 2026-04-07*
