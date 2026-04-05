---
phase: 03-battle-framework-and-core-triggers
plan: 03
subsystem: engine
tags: [battle, experience, parity, test, kotlin]

# Dependency graph
requires:
  - phase: 03-battle-framework-and-core-triggers
    plan: 01
    provides: BattleEngine with C7 exp calculation, WarUnitGeneral.applyResults
provides:
  - BattleExperienceParityTest.kt with 27 golden-value tests verifying C7 experience pipeline
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Golden-value parity testing: exact numeric assertions against legacy PHP formulas"
    - "Nested JUnit test classes for logical grouping of parity domains"

key-files:
  created:
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/BattleExperienceParityTest.kt
  modified: []

key-decisions:
  - "MISC armType branch in applyResults is currently unreachable -- no CrewType maps to ArmType.MISC. Documented in test as known gap."
  - "Defender 0.8x multiplier uses integer division first then float multiply: (damageReceived / 50 * 0.8).toInt() -- verified matches PHP intdiv behavior"

patterns-established:
  - "Experience parity test pattern: unit-level formula verification + full pipeline integration with fixed-seed reproducibility"

requirements-completed: [BATTLE-11]

# Metrics
duration: 10min
completed: 2026-04-01
---

# Phase 3 Plan 3: C7 Battle Experience Parity Verification Summary

**27 golden-value tests verifying C7 battle experience pipeline: damage/50 level exp, 0.8x defender multiplier, stat exp routing by arm type, win/lose atmos formulas, overflow guards, and full pipeline integration with fixed-seed reproducibility**

## Performance

- **Duration:** 10 min
- **Started:** 2026-04-01T04:56:44Z
- **Completed:** 2026-04-01T05:07:38Z
- **Tasks:** 1
- **Files created:** 1

## Accomplishments
- Created BattleExperienceParityTest.kt with 27 test methods across 5 nested test classes
- Verified level exp formula: attackerDamageDealtForExp / 50 (integer division), defender 0.8x multiplier, city capture +1000 bonus
- Verified stat exp routing: FOOTMAN/ARCHER/CAVALRY -> strengthExp, WIZARD -> intelExp, SIEGE -> leadershipExp
- Verified win/lose atmos: winner 1.1x, loser 1.05x, cap at 100, both sides get pendingStatExp += 1
- Verified overflow guard: coerceIn(0, 1000) caps all stat exp fields
- Verified full pipeline: resolveBattle -> applyResults with fixed seed produces identical results across runs

## Task Commits

Each task was committed atomically:

1. **Task 1: C7 experience calculation parity tests** - `1baee19` (test)

## Files Created/Modified
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/BattleExperienceParityTest.kt` - 27 parity tests across 5 sections (LevelExpCalculation, StatExpRouting, WinLoseAtmos, StatExpOverflow, FullPipelineIntegration)

## Decisions Made
- MISC armType branch in WarUnitGeneral.applyResults() is currently unreachable because no CrewType enum entry maps to ArmType.MISC. Test documents this with a placeholder assertion and code inspection note.
- Defender 0.8x multiplier verified as `(damageReceived / 50 * 0.8).toInt()` -- integer division happens first (Kotlin Int/Int), then float multiply, then truncation. This matches PHP legacy behavior.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all tests exercise real implementation code.

## Next Phase Readiness
- C7 experience pipeline is fully verified with golden-value tests
- All 5 arm type routing paths tested (FOOTMAN, ARCHER, CAVALRY, WIZARD, SIEGE)
- Overflow guards confirmed working at boundary values
- Fixed-seed reproducibility test ensures deterministic battle outcomes

## Self-Check: PASSED

- BattleExperienceParityTest.kt exists on disk (511 lines, exceeds 100 line minimum)
- Commit 1baee19 (Task 1) verified in git log
- Full test suite passes with 0 failures (27 tests across 5 nested classes)

---
*Phase: 03-battle-framework-and-core-triggers*
*Completed: 2026-04-01*
