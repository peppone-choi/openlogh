---
phase: 09-turn-engine-completion
plan: 02
subsystem: engine
tags: [turn-engine, disaster, parity, golden-values, kotlin]

requires:
  - phase: 09-turn-engine-completion
    provides: TurnService stub implementations and postUpdateMonthly ordering fix
provides:
  - postUpdateMonthly call ordering locked by source-code assertion test
  - Disaster/boom probability, effect, and state code golden value parity tests
  - RNG seed string divergence documented (Kotlin "disaster" vs PHP "disater" typo)
affects: [turn-engine, economy-parity, game-parity]

tech-stack:
  added: []
  patterns:
    - "Source-code order assertion: read .kt file and verify method call positions for ordering parity"
    - "Balanced-paren block extraction for parsing Kotlin map literals in tests"

key-files:
  created:
    - backend/game-app/src/test/kotlin/com/opensam/qa/parity/DisasterParityTest.kt
  modified:
    - backend/game-app/src/test/kotlin/com/opensam/qa/parity/TurnPipelineParityTest.kt

key-decisions:
  - "Source-code reading approach for postUpdateMonthly ordering assertion (simple, explicit, matches file content)"
  - "RNG seed divergence documented but not fixed -- Kotlin uses 'disaster', PHP uses 'disater' typo; fixing requires coordinating with existing world data"
  - "Floating point golden values use isCloseTo(within(1e-10)) to avoid IEEE 754 precision artifacts"

patterns-established:
  - "Source-file assertion: read source .kt file during test, find method call positions by string index, assert ordering"
  - "Balanced-paren parser for extracting nested Kotlin map/listOf blocks from source"

requirements-completed: [TURN-05, TURN-06]

duration: 12min
completed: 2026-04-02
---

# Phase 9 Plan 2: Turn Step Ordering Verification + Disaster Golden Value Tests

**postUpdateMonthly call ordering locked by assertion test + disaster probabilities, effects, and state codes verified against legacy RaiseDisaster.php via golden values**

## Performance

- **Duration:** 12 min
- **Started:** 2026-04-02T09:51:15Z
- **Completed:** 2026-04-02T10:03:00Z
- **Tasks:** 2 (both TDD)
- **Files modified:** 2

## Accomplishments
- Locked postUpdateMonthly call ordering (checkWander -> updateGeneralNumber -> triggerTournament -> registerAuction) with source-code position assertion
- Verified UnificationCheck step at order 1600 covers legacy checkEmperior() and WarFrontRecalc at order 1300 covers legacy SetNationFront()
- Created 23 disaster parity tests covering grace period, boomRate, state codes, affectRatio formulas, raiseProp formulas, and SabotageInjury parameters
- Documented RNG seed string parity divergence: Kotlin "disaster" vs PHP "disater" (typo in legacy)

## Task Commits

Each task was committed atomically:

1. **Task 1: postUpdateMonthly ordering assertion + pipeline coverage** - `46ac305` (test)
2. **Task 2: Disaster probability and effect golden value parity tests** - `dcb2cc5` (test)

## Files Created/Modified
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/TurnPipelineParityTest.kt` - 3 new tests: postUpdateMonthly ordering, UnificationCheck covers checkEmperior, WarFrontRecalc covers SetNationFront
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/DisasterParityTest.kt` - 23 new tests: grace period, boomRate, RNG seed, state codes, disaster/boom affectRatio, raiseProp, SabotageInjury

## Decisions Made
- Used source-code reading approach for ordering assertion rather than Mockito InOrder spy (simpler, no dependency injection needed)
- Documented RNG seed divergence without fixing it -- changing "disaster" to "disater" would require verifying all existing world data; tracked as known parity gap
- Floating point golden values use `isCloseTo(within(1e-10))` for IEEE 754 precision tolerance

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- JDK 25 incompatible with Gradle 8.12; used JDK 23 (pre-existing environment issue)
- Worktree was based on old commit; fast-forward merged to 09-01 completion point before starting
- 3 pre-existing test failures in unrelated files (NationCommandTest, NationResearchSpecialCommandTest, NpcPolicyTest) -- not caused by this plan's changes

## Known Stubs
None -- all tests are assertions against existing implementations.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 9 (turn-engine-completion) fully verified: stub methods implemented (Plan 1) + ordering and disaster parity locked (Plan 2)
- RNG seed divergence ("disaster" vs "disater") noted for future parity fix pass
- Ready for Phase 10

## Self-Check: PASSED

- TurnPipelineParityTest.kt: FOUND
- DisasterParityTest.kt: FOUND
- 09-02-SUMMARY.md: FOUND
- Commit 46ac305: FOUND
- Commit dcb2cc5: FOUND

---
*Phase: 09-turn-engine-completion*
*Completed: 2026-04-02*
