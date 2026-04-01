---
phase: 02-numeric-type-safety
plan: 01
subsystem: engine
tags: [kotlin, short-overflow, coerceIn, integer-division, parity, type-safety]

# Dependency graph
requires:
  - phase: 01-deterministic-foundations
    provides: Deterministic RNG and entity processing order for engine services
provides:
  - coerceIn overflow guards on all .toShort() assignments in 16 engine service files
  - ShortOverflowGuardTest verifying domain-bound clamping for all Short field categories
  - IntegerDivisionParityTest confirming Kotlin / matches PHP intdiv for all sign combinations
  - RoundingParityTest scaffold with @Disabled tests for Plan 02 TYPE-02 normalization
affects: [02-numeric-type-safety, battle-parity, economy-parity, npc-ai-parity]

# Tech tracking
tech-stack:
  added: []
  patterns: ["coerceIn guard pattern: (expression).coerceIn(domainMin, domainMax).toShort()"]

key-files:
  created:
    - backend/game-app/src/test/kotlin/com/opensam/engine/ShortOverflowGuardTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/IntegerDivisionParityTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/RoundingParityTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/GeneralMaintenanceService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/EconomyService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/NpcSpawnService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/StatChangeService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/ai/NationAI.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnitGeneral.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/WarAftermath.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/DiplomacyService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/EventActionService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/EventService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/SpecialAssignmentService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt

key-decisions:
  - "city.state coerceIn bound widened to 0..32767 (plan specified 0..10 but actual game uses multi-digit state codes 31-43)"
  - "GeneralAI nation.bill sites with pre-existing coerceIn(20,200) on preceding line left as-is (guard already effective)"
  - "Non-assignment .toShort() calls (constants, query params, type conversions for comparison) left unguarded as they cannot overflow entity fields"

patterns-established:
  - "coerceIn guard pattern: every .toShort() entity field assignment preceded by .coerceIn(domainMin, domainMax)"
  - "Domain bounds reference: interfaces block in 02-01-PLAN.md defines authoritative field bounds from legacy PHP"

requirements-completed: [TYPE-01, TYPE-03]

# Metrics
duration: 21min
completed: 2026-04-01
---

# Phase 02 Plan 01: Short Overflow Guards & Integer Division Parity Summary

**coerceIn overflow guards on all 108 .toShort() assignments across 16 engine files, plus integer division parity confirmed for all PHP intdiv sign combinations**

## Performance

- **Duration:** 21 min
- **Started:** 2026-04-01T02:16:04Z
- **Completed:** 2026-04-01T02:37:34Z
- **Tasks:** 2
- **Files modified:** 19 (3 created, 16 modified)

## Accomplishments
- All .toShort() entity field assignments in engine service files now have domain-appropriate coerceIn guards preventing silent Short wrap-around (TYPE-01 complete)
- Kotlin integer division confirmed to match PHP intdiv() for all sign combinations -- no phpIntdiv() utility needed (TYPE-03 complete)
- Wave 0 test scaffolds created for all three phase requirements (ShortOverflowGuardTest, IntegerDivisionParityTest, RoundingParityTest with @Disabled tests for Plan 02)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Wave 0 test scaffolds** - `5ba9d97` (test)
2. **Task 2: Add coerceIn overflow guards** - `07a3b4d` (feat)

## Files Created/Modified

### Created
- `backend/game-app/src/test/kotlin/com/opensam/engine/ShortOverflowGuardTest.kt` - Tests for coerceIn guard pattern across 7 field categories (stats, stat-exp, military, nation-economy, extreme overflow, non-zero lower bounds)
- `backend/game-app/src/test/kotlin/com/opensam/engine/IntegerDivisionParityTest.kt` - Parameterized tests verifying Kotlin / matches PHP intdiv for 15 cases including all sign combinations
- `backend/game-app/src/test/kotlin/com/opensam/engine/RoundingParityTest.kt` - Scaffold with @Disabled tests for PHP round() golden values (enabled in Plan 02)

### Modified (coerceIn guards added)
- `EconomyService.kt` - atmos, train, nation.level, injury guards
- `GeneralMaintenanceService.kt` - expLevel, dedLevel guards
- `NpcSpawnService.kt` - stat, year, crewType guards (already mostly guarded from previous work)
- `StatChangeService.kt` - stat, exp guards (already mostly guarded)
- `TurnService.kt` - killTurn, year/month, makeLimit, strategicCmdLimit, surrenderLimit, atmos, city.state, city.term guards
- `GeneralAI.kt` - killTurn, officerLevel, rate guards
- `NationAI.kt` - officerLevel, rateTmp, bill guards
- `WarUnitGeneral.kt` - train, atmos, injury, stat exp guards
- `WarAftermath.kt` - atmos guard
- `BattleService.kt` - atmos guard
- `DiplomacyService.kt` - diplomacy.term guards
- `EventActionService.kt` - betray, bornYear, deadYear, stat, age, belong, officerLevel guards
- `EventService.kt` - targetYear guard
- `SpecialAssignmentService.kt` - specAge, spec2Age guards
- `trigger/GeneralTrigger.kt` - injury, atmos guards
- `turn/cqrs/memory/InMemoryTurnProcessor.kt` - killTurn, strategicCmdLimit, year, month guards

## Decisions Made

1. **city.state bound widened**: Plan specified 0..10 but actual game uses multi-digit state codes (31, 32, 33, 34, 41, 42, 43). Used 0..32767 to prevent behavioral regression while still guarding against Short overflow.
2. **Pre-guarded sites left as-is**: GeneralAI `nation.bill` sites already had `bill.coerceIn(20, 200)` on the preceding line -- adding redundant coerceIn on the `.toShort()` call would be noise.
3. **Non-assignment .toShort() excluded**: Constants (`6.toShort()`), query parameters (`worldId.toShort()`), comparison values (`(condition["year"] as? Number)?.toShort()`), and literal comparisons (`1.toShort()`) do not assign to entity fields and cannot cause overflow corruption.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] city.state coerceIn bound corrected from 0..10 to 0..32767**
- **Found during:** Task 2 (TurnService.kt transitionCityStates)
- **Issue:** Plan's interface block specified city.state bounds as 0..10, but the actual transition code uses states 31-43 as valid intermediate values. coerceIn(0, 10) would clamp these to 10, breaking city state transitions.
- **Fix:** Used coerceIn(0, 32767) to prevent Short overflow while preserving all valid state codes.
- **Files modified:** backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt
- **Verification:** Full test suite passes
- **Committed in:** 07a3b4d

**2. [Rule 2 - Missing Critical] Extended coerceIn guards to engine files beyond plan scope**
- **Found during:** Task 2 audit
- **Issue:** Plan listed 11 files but acceptance criteria grep covers entire engine/ directory. Additional unguarded .toShort() found in DiplomacyService, EventActionService, EventService, SpecialAssignmentService, GeneralTrigger, InMemoryTurnProcessor, NationAI.
- **Fix:** Added coerceIn guards to all unguarded .toShort() entity field assignments in these additional files.
- **Files modified:** 7 additional engine files
- **Verification:** Full test suite passes, grep check shows only non-assignment .toShort() remaining
- **Committed in:** 07a3b4d

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both fixes necessary for correctness. city.state fix prevents behavioral regression. Extended scope ensures the TYPE-01 guarantee ("no Short field can silently wrap") applies to ALL engine files, not just the 11 listed.

## Issues Encountered
None -- all edits were mechanical coerceIn additions following the established ItemService.kt pattern.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None -- all guards are fully wired with domain-appropriate bounds.

## Next Phase Readiness
- TYPE-01 (Short overflow) and TYPE-03 (integer division) are complete
- RoundingParityTest scaffold ready for Plan 02 (TYPE-02 rounding normalization)
- @Disabled tests in RoundingParityTest will be enabled when phpRound() utility is introduced

## Self-Check: PASSED

- ShortOverflowGuardTest.kt: FOUND
- IntegerDivisionParityTest.kt: FOUND
- RoundingParityTest.kt: FOUND
- 02-01-SUMMARY.md: FOUND
- Commit 5ba9d97 (Task 1): FOUND
- Commit 07a3b4d (Task 2): FOUND

---
*Phase: 02-numeric-type-safety*
*Completed: 2026-04-01*
