---
phase: 01-deterministic-foundation
plan: 01
subsystem: engine
tags: [rng, deterministic, parity, sha-512, litehash-drbg, kotlin-random]

# Dependency graph
requires: []
provides:
  - "Zero java.util.Random in game engine -- all RNG is deterministic LiteHashDRBG"
  - "RandUtil.choice() single-element RNG stream synchronization with PHP array_rand"
  - "100+ draw golden-value parity tests for LiteHashDRBG"
  - "CityHealTrigger constructor injection pattern for kotlin.random.Random"
affects: [02-type-safety, 03-battle-framework, 05-modifiers, 08-npc-ai]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DeterministicRng.create(hiddenSeed, ...tags) for world-scoped RNG injection"
    - "RandUtil wrapping LiteHashDRBG for all game random operations"
    - "Golden-value hardcoded test arrays for cross-language parity verification"

key-files:
  created: []
  modified:
    - "backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt"
    - "backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt"
    - "backend/game-app/src/main/kotlin/com/opensam/engine/RealtimeService.kt"
    - "backend/game-app/src/main/kotlin/com/opensam/engine/RandUtil.kt"
    - "backend/game-app/src/test/kotlin/com/opensam/engine/LiteHashDRBGTest.kt"
    - "backend/game-app/src/test/kotlin/com/opensam/engine/RandUtilTest.kt"
    - "backend/game-app/src/test/kotlin/com/opensam/engine/trigger/GeneralTriggerTest.kt"
    - "backend/game-app/src/test/kotlin/com/opensam/engine/trigger/TriggerCallerTest.kt"

key-decisions:
  - "RandUtil.choice() single-element guard uses rng.nextLegacyInt(1L) to consume exactly one draw for PHP array_rand parity"
  - "buildPreTurnTriggers() rng parameter has no default value -- callers must explicitly inject RNG"
  - "registerAuction() RNG seeded with (hiddenSeed, registerAuction, year, month) for deterministic auction generation"

patterns-established:
  - "Explicit RNG injection: all game-logic RNG flows through DeterministicRng.create() with contextual seed tags"
  - "Golden-value parity testing: hardcoded expected arrays from SHA-512 DRBG for cross-language verification"

requirements-completed: [FOUND-01, FOUND-02, FOUND-05]

# Metrics
duration: 14min
completed: 2026-03-31
---

# Phase 01 Plan 01: Deterministic Foundation Summary

**Replaced all java.util.Random with LiteHashDRBG in game engine, fixed single-element choice() RNG sync, and added 100-draw golden-value parity tests**

## Performance

- **Duration:** 14 min
- **Started:** 2026-03-31T13:31:57Z
- **Completed:** 2026-03-31T13:46:08Z
- **Tasks:** 3
- **Files modified:** 8

## Accomplishments
- Zero java.util.Random remaining in game engine source code -- TurnService.registerAuction() and CityHealTrigger both use world-seeded deterministic RNG
- RandUtil.choice() on single-element lists/maps now consumes one RNG draw to maintain PHP array_rand stream synchronization
- LiteHashDRBG parity test suite extended to 8 tests including 100-draw golden values, Long.MAX_VALUE edge seed, empty/zero seeds, float range validation, and mixed-method determinism

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace java.util.Random in TurnService.registerAuction() and CityHealTrigger** - `0062588` (feat)
2. **Task 2: Fix RandUtil.choice() single-element RNG consumption and add CityHealTrigger injection test** - `bc56b2c` (feat)
3. **Task 3: Extend LiteHashDRBG parity tests with hardcoded golden values and edge case seeds** - `0546f3f` (test)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` - registerAuction() now uses RandUtil(DeterministicRng.create(...))
- `backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt` - CityHealTrigger uses kotlin.random.Random; buildPreTurnTriggers() accepts rng parameter
- `backend/game-app/src/main/kotlin/com/opensam/engine/RealtimeService.kt` - firePreTurnTriggers() passes world-scoped RNG to buildPreTurnTriggers
- `backend/game-app/src/main/kotlin/com/opensam/engine/RandUtil.kt` - choice(List) and choice(Map) single-element guards with RNG state consumption
- `backend/game-app/src/test/kotlin/com/opensam/engine/LiteHashDRBGTest.kt` - 5 new golden-value and edge-case tests
- `backend/game-app/src/test/kotlin/com/opensam/engine/RandUtilTest.kt` - 2 new single-element choice RNG state tests
- `backend/game-app/src/test/kotlin/com/opensam/engine/trigger/GeneralTriggerTest.kt` - 2 new CityHealTrigger injection tests
- `backend/game-app/src/test/kotlin/com/opensam/engine/trigger/TriggerCallerTest.kt` - Updated buildPreTurnTriggers call to pass rng

## Decisions Made
- RandUtil.choice() single-element guard uses `rng.nextLegacyInt(1L)` to consume exactly one draw, matching PHP array_rand behavior on single-element arrays
- buildPreTurnTriggers() has no default for rng parameter -- forces explicit injection at all call sites, preventing accidental non-deterministic RNG usage
- registerAuction() RNG seeded with `(hiddenSeed, "registerAuction", year, month)` following the existing generalCommand seed pattern

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed flaky single-element choice test assertion**
- **Found during:** Task 2 (RandUtilTest single-element test)
- **Issue:** Plan's test compared single draw values which could collide (both produced 96). One-bit RNG consumption shifted state by 1 byte, but subsequent 7-bit reads from different offsets could produce identical mod-100 results.
- **Fix:** Changed assertion to compare 10 subsequent draws instead of 1, eliminating collision chance while still proving RNG state divergence.
- **Files modified:** backend/game-app/src/test/kotlin/com/opensam/engine/RandUtilTest.kt
- **Verification:** Test passes deterministically
- **Committed in:** bc56b2c (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor test robustness improvement. No scope creep.

## Issues Encountered
None beyond the test assertion fix documented above.

## Known Stubs
None -- all code paths are fully wired with no placeholder values.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Deterministic RNG foundation complete for all game engine paths
- All callers of buildPreTurnTriggers and registerAuction inject world-seeded RNG
- Golden-value parity tests provide cross-language verification anchor for PHP compatibility
- Ready for Phase 01 Plan 02 and subsequent phases that depend on deterministic RNG

## Self-Check: PASSED

All 9 files verified present. All 3 task commits verified in git log.

---
*Phase: 01-deterministic-foundation*
*Completed: 2026-03-31*
