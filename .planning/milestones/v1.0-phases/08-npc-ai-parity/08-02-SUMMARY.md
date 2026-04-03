---
phase: 08-npc-ai-parity
plan: 02
subsystem: ai
tags: [npc-ai, military-ai, golden-value, legacy-parity]

requires:
  - phase: 08-npc-ai-parity
    plan: 01
    provides: "PHP-matching calcDiplomacyState, categorizeNationGeneral, priority lists"
provides:
  - "PHP-matching doSortie with policy-based train/atmos/crew thresholds"
  - "PHP-matching doCombatPrep with weighted random choice and policy threshold"
  - "Golden value parity tests for all 7 military do*() methods"
affects: [08-03-PLAN, 08-04-PLAN]

tech-stack:
  added: []
  patterns:
    - "Weighted choice pattern in doCombatPrep matching PHP choiceUsingWeightPair"

key-files:
  created: []
  modified:
    - "backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt"
    - "backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt"

key-decisions:
  - "doSortie train/atmos threshold uses nationPolicy.properWarTrainAtmos (default 90) not hardcoded 80"
  - "doSortie crew threshold uses min((leadership-2)*100, nationPolicy.minWarCrew) not hardcoded 500"
  - "doCombatPrep uses weighted random choice between train/atmos actions per PHP choiceUsingWeightPair"
  - "doCombatPrep threshold uses nationPolicy.properWarTrainAtmos (default 90) not hardcoded 80"

requirements-completed: [AI-01, AI-04]

duration: 9min
completed: 2026-04-02
---

# Phase 08 Plan 02: Military AI do*() Golden Value Parity Summary

**Fix doSortie/doCombatPrep policy thresholds and weighted choice, add 23 golden value parity tests for all 7 military do*() methods**

## Performance

- **Duration:** 9 min
- **Started:** 2026-04-02T05:14:36Z
- **Completed:** 2026-04-02T05:24:01Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Fixed doSortie train/atmos guard from hardcoded 80 to nationPolicy.properWarTrainAtmos (PHP parity)
- Fixed doSortie crew guard from hardcoded 500 to min((leadership-2)*100, nationPolicy.minWarCrew) (PHP parity)
- Fixed doCombatPrep threshold from hardcoded 80 to nationPolicy.properWarTrainAtmos (PHP parity)
- Rewrote doCombatPrep from deterministic first-match to weighted random choice matching PHP choiceUsingWeightPair
- Added 12 golden value tests for Task 1: doSortie (4 tests), doRecruit (3 tests), doCombatPrep (3 tests), doWarpToFront (2 tests)
- Added 11 golden value tests for Task 2: doWarpToRear (3 tests), doRally (3 tests), doDismiss (4 tests) + reflection helpers
- All 67 tests pass (23 new military AI parity tests added)

## Task Commits

1. **Task 1: doSortie/doRecruit/doCombatPrep/doWarpToFront parity** - `140adb7` (feat)
2. **Task 2: doWarpToRear/doRally/doDismiss parity** - `7fbf238` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` - Fixed doSortie thresholds to use nationPolicy, rewrote doCombatPrep weighted choice
- `backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt` - Added 23 golden value parity tests + 7 reflection helpers for military do*() methods

## Decisions Made
- doSortie train/atmos guard uses `min(100, nationPolicy.properWarTrainAtmos)` matching PHP line 2729-2731 (default 90, not 80)
- doSortie crew guard uses `min((leadership-2)*100, nationPolicy.minWarCrew)` matching PHP line 2735 (default 1500, not 500)
- doCombatPrep builds weighted list with `maxTrainByCommand/train` and `maxAtmosByCommand/atmos` weights, then uses random weighted choice matching PHP line 2681
- doCombatPrep threshold uses nationPolicy.properWarTrainAtmos matching PHP line 2664

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed doSortie hardcoded thresholds**
- **Found during:** Task 1
- **Issue:** doSortie used hardcoded `min(100, 80)` for train/atmos and `min((leadership-2)*100, 500)` for crew, diverging from PHP which uses policy values
- **Fix:** Changed to use `nationPolicy.properWarTrainAtmos` and `nationPolicy.minWarCrew`
- **Files modified:** GeneralAI.kt
- **Committed in:** 140adb7

**2. [Rule 1 - Bug] Fixed doCombatPrep hardcoded threshold and deterministic choice**
- **Found during:** Task 1
- **Issue:** doCombatPrep used hardcoded threshold 80 and always returned first match (훈련 before 사기진작), while PHP uses policy threshold and weighted random choice
- **Fix:** Changed threshold to nationPolicy.properWarTrainAtmos, implemented weighted random choice matching PHP
- **Files modified:** GeneralAI.kt
- **Committed in:** 140adb7

---

**Total deviations:** 2 auto-fixed (2 bugs from parity-breaking hardcoded values)
**Impact on plan:** Both fixes necessary for PHP parity. No scope creep.

## Issues Encountered
- JDK 17 not installed on system (only JDK 23 and 25 available); resolved by using JDK 23 which can compile/run JVM 17 targets

## Known Stubs
None - all methods implemented with PHP-matching logic.

## Next Phase Readiness
- All 7 military do*() methods verified against PHP GeneralAI.php
- Ready for Phase 08 Plan 03 (domestic/economy do*() methods parity)
- doSortie, doRecruit, doCombatPrep now use correct policy thresholds

## Self-Check: PASSED

---
*Phase: 08-npc-ai-parity*
*Completed: 2026-04-02*
