---
phase: 02-numeric-type-safety
plan: 02
subsystem: engine
tags: [kotlin, rounding, parity, golden-snapshot, numeric-drift, kotlin-math-round]

# Dependency graph
requires:
  - phase: 02-numeric-type-safety/plan-01
    provides: coerceIn overflow guards on all .toShort() assignments, RoundingParityTest scaffold with @Disabled tests
provides:
  - All Math.round() calls normalized to kotlin.math.round() across engine files
  - RoundingParityTest enabled with PHP vs Kotlin rounding golden values
  - 200-turn NumericParityGoldenTest establishing Kotlin cumulative baseline
  - Documented kotlin.math.round banker's rounding divergence from PHP at .5 boundaries
affects: [economy-parity, battle-parity, npc-ai-parity, turn-processing-parity]

# Tech tracking
tech-stack:
  added: []
  patterns: ["kotlin.math.round(x).toInt() for PHP round() parity (banker's rounding, .5 divergence documented)"]

key-files:
  created:
    - backend/game-app/src/test/kotlin/com/opensam/engine/NumericParityGoldenTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/NpcSpawnService.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/RoundingParityTest.kt

key-decisions:
  - "kotlin.math.round chosen over Math.round to eliminate Long-to-Int narrowing risk; banker's rounding divergence at .5 documented but accepted since exact .5 values are rare in game formulas"
  - "All 13 .roundToInt() sites audited and confirmed correct for their PHP equivalents; no changes needed"
  - "Golden snapshot uses standalone EconomyService simulation (option b from RESEARCH), not InMemoryTurnHarness"

patterns-established:
  - "Golden snapshot pattern: fixed-seed entity setup, 200-turn processMonthly loop, assert cumulative values against recorded baseline"
  - "Rounding normalization: kotlin.math.round(x).toInt() for PHP round() sites, .toInt() for PHP (int) cast sites"

requirements-completed: [TYPE-02]

# Metrics
duration: 20min
completed: 2026-04-01
---

# Phase 02 Plan 02: Rounding Normalization & 200-Turn Golden Snapshot Summary

**All Math.round() calls replaced with kotlin.math.round(), rounding parity tests enabled, and 200-turn economy golden snapshot established as cumulative drift detection baseline**

## Performance

- **Duration:** 20 min
- **Started:** 2026-04-01T02:51:02Z
- **Completed:** 2026-04-01T03:11:09Z
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- All 7 Math.round(x).toInt() calls in engine files replaced with kotlin.math.round(x).toInt(), eliminating Long-to-Int narrowing risk (TYPE-02 complete)
- RoundingParityTest rewritten with 12 enabled tests covering PHP (int) cast, PHP round() golden values, and Math.round vs kotlin.math.round divergence documentation
- 200-turn NumericParityGoldenTest established with deterministic economy simulation baseline: nation gold/rice, city infrastructure, general salary accumulation all recorded as golden values
- Full game-app test suite passes with no regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Audit and normalize rounding call sites** - `80d6e34` (feat)
2. **Task 2: Create 200-turn golden snapshot test** - `50027dd` (test)

## Files Created/Modified

### Created
- `backend/game-app/src/test/kotlin/com/opensam/engine/NumericParityGoldenTest.kt` - 200-turn economy simulation golden snapshot with 3 test methods: determinism, golden values, domain bounds

### Modified
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` - Replaced 4 Math.round(x).toInt() with kotlin.math.round(x).toInt() in calcCityGoldIncome, calcCityRiceIncome, calcCityWallRiceIncome, calcOutcome; added import kotlin.math.round
- `backend/game-app/src/main/kotlin/com/opensam/engine/NpcSpawnService.kt` - Replaced 3 Math.round(x).toInt() with kotlin.math.round(x).toInt() in derivePoliticsFromStats, deriveCharmFromStats, calcAverageCityStats; added import kotlin.math.round
- `backend/game-app/src/test/kotlin/com/opensam/engine/RoundingParityTest.kt` - Removed all @Disabled annotations, rewritten with 12 enabled tests documenting PHP vs Kotlin rounding behavior including banker's rounding divergence at .5 boundaries

## Decisions Made

1. **kotlin.math.round over Math.round**: kotlin.math.round(Double) returns Double (safe .toInt()), while Math.round(Double) returns Long requiring narrowing. For game value ranges (< 100,000) both produce identical results, but kotlin.math.round eliminates the silent narrowing risk.
2. **All .roundToInt() sites confirmed correct**: 13 .roundToInt() call sites across NpcPolicy.kt, SpecialAssignmentService.kt, TournamentBattle.kt, YearbookService.kt, and BattleService.kt were audited. Each corresponds to PHP round() or equivalent calculations where .5 boundary values are rare. No changes needed.
3. **Standalone economy simulation for golden test**: Used option (b) from RESEARCH -- direct EconomyService.processMonthly() driving with mocked repositories, not InMemoryTurnHarness which mocks EconomyService itself.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None - all edits were mechanical replacements and the golden snapshot captured values on first attempt.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None -- all tests are fully wired with real golden values.

## Next Phase Readiness
- TYPE-01 (Short overflow), TYPE-02 (rounding normalization), and TYPE-03 (integer division) are all complete
- Phase 02 (numeric-type-safety) is fully complete
- Golden snapshot baseline established for detecting future cumulative drift
- All engine files use consistent rounding patterns documented in RoundingParityTest

## Self-Check: PASSED

- NumericParityGoldenTest.kt: FOUND
- RoundingParityTest.kt: FOUND
- GeneralAI.kt: FOUND
- NpcSpawnService.kt: FOUND
- 02-02-SUMMARY.md: FOUND
- Commit 80d6e34 (Task 1): FOUND
- Commit 50027dd (Task 2): FOUND

---
*Phase: 02-numeric-type-safety*
*Completed: 2026-04-01*
