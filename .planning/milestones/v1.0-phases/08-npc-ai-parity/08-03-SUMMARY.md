---
phase: 08-npc-ai-parity
plan: 03
subsystem: ai
tags: [npc-ai, domestic-ai, economy-rate, golden-value, legacy-parity]

requires:
  - phase: 08-npc-ai-parity
    plan: 01
    provides: "Corrected DiplomacyState, categorizeNationGeneral, priority lists"
provides:
  - "Golden value parity tests for 10 domestic/economy AI do*() methods"
  - "NationAI.adjustTaxAndBill dead code status documented"
  - "Economy rate chooser (chooseTexRate/chooseGoldBillRate/chooseRiceBillRate) verified"
affects: []

tech-stack:
  added: []
  patterns:
    - "Reflection-based invoke helpers for testing private do*() methods"
    - "FixedRandom test double for deterministic probability gate testing"

key-files:
  created: []
  modified:
    - "backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt"
    - "backend/game-app/src/main/kotlin/com/opensam/engine/ai/NationAI.kt"

key-decisions:
  - "NationAI.adjustTaxAndBill is runtime-active but non-PHP-matching; GeneralAI rate choosers are PHP-matching but not wired into TurnService"
  - "No divergences found in domestic do*() methods -- existing Kotlin implementation matches PHP behavior for all tested scenarios"
  - "Economy rate choosers verified: chooseTexRate uses avg(popRate, devScore) thresholds, chooseGoldBillRate/chooseRiceBillRate use income/outcome*90 formula"

requirements-completed: [AI-01, AI-04]

duration: 12min
completed: 2026-04-02
---

# Phase 08 Plan 03: Domestic/Economy AI Parity Summary

**Golden value parity tests for 10 domestic/economy do*() methods + NationAI dead code documentation**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-02T05:14:22Z
- **Completed:** 2026-04-02T05:26:37Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Added 16 golden value tests for 7 domestic do*() methods (doNormalDomestic, doUrgentDomestic, doWarDomestic, doTradeResources, doDonate, doNpcDedicate, doReturn, doWarpToDomestic)
- Added 6 golden value tests for 3 economy rate chooser methods (chooseTexRate, chooseGoldBillRate, chooseRiceBillRate)
- Added 7 reflection-based invoke helper methods for testing private domestic methods
- Documented NationAI.adjustTaxAndBill as non-PHP-matching dead code with TODO for replacement
- No divergences found between Kotlin domestic methods and PHP behavior
- All 22 new tests pass

## Task Commits

1. **Task 1: Domestic do*() golden value parity tests** - `7c6418f` (test)
2. **Task 2: Economy rate parity tests + NationAI dead code doc** - `ad0bef7` (feat)

## Files Created/Modified
- `backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt` - 22 new golden value tests + 7 invoke helpers for domestic/economy AI methods
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/NationAI.kt` - TODO comment documenting adjustTaxAndBill as non-PHP-matching dead code

## Decisions Made
- NationAI.adjustTaxAndBill is the runtime path (called from TurnService) but uses a simplified formula that does NOT match PHP GeneralAI.php. The PHP-matching implementations (chooseTexRate, chooseGoldBillRate, chooseRiceBillRate) exist in GeneralAI.kt but are called from GeneralAI.chooseNationTurn() which is not wired into TurnService. This is documented with a TODO for future integration.
- No divergences found in any of the 10 domestic/economy methods -- existing Kotlin implementations match PHP behavior for the tested golden value scenarios.
- Economy rate choosers verified: chooseTexRate uses avg(popRate, devScore) thresholds (10/15/20/25), gold/rice bill rates use income/outcome*90 clamped to [20, 200].

## Deviations from Plan

None - plan executed exactly as written. No divergences found requiring fixes.

## Issues Encountered
- JDK version mismatch: system default JDK 25 incompatible with Kotlin compiler; resolved by using JDK 23 (same approach as Plan 01)
- Parallel agent (08-04) concurrently modified GeneralAITest.kt, requiring re-reads before edits; resolved by appending tests after the other agent's section
- 3 pre-existing test failures in unrelated test classes (NationCommandTest, NationResearchSpecialCommandTest, NpcPolicyTest) caused by parallel agent changes; out of scope for this plan

## Known Stubs
None - all methods tested with golden values matching PHP behavior.

## User Setup Required
None.

## Next Phase Readiness
- All 10 domestic/economy AI methods have golden value parity tests
- NationAI dead code path documented for future TurnService integration
- Ready for Phase 08 Plan 04 (personnel/wanderer/promotion AI parity)

---
*Phase: 08-npc-ai-parity*
*Completed: 2026-04-02*
