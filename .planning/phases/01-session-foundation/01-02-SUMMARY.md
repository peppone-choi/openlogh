---
phase: 01-session-foundation
plan: 02
subsystem: api, ui
tags: [spring-boot, kotlin, next.js, react, faction-ratio, session-join, serializable-isolation]

# Dependency graph
requires:
  - phase: 01-session-foundation
    provides: "UI design contract (faction colors, spacing, typography)"
provides:
  - "FactionJoinService with 3:2 ratio enforcement (SERIALIZABLE isolation)"
  - "Faction count REST endpoint for lobby UI"
  - "Empire/Alliance binary faction picker with ratio bar"
  - "OfficerService faction ratio check before officer creation"
affects: [02-character-rank, 03-command-point]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "SERIALIZABLE transaction isolation for TOCTOU-safe concurrent join enforcement"
    - "Integer ratio comparison (avoid floating point): (count+1)*5 > (total+1)*3"
    - "CSS variable faction colors: --empire-gold, --alliance-blue"

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/service/FactionJoinService.kt
    - backend/game-app/src/test/kotlin/com/openlogh/service/FactionJoinServiceTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/service/OfficerService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/OfficerController.kt
    - backend/game-app/src/test/kotlin/com/openlogh/service/OfficerServiceTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/controller/OfficerControllerTest.kt
    - frontend/src/app/(lobby)/lobby/join/page.tsx
    - frontend/src/types/index.ts

key-decisions:
  - "Used integer arithmetic (numerator*denominator comparison) instead of floating point for ratio checks to avoid precision issues"
  - "Threw IllegalStateException (not IllegalArgumentException) for ratio violations to distinguish from input validation errors in controller"
  - "Added faction picker above existing character form rather than replacing the nation dropdown, preserving backward compatibility"

patterns-established:
  - "FactionJoinResult data class pattern for allow/deny with Korean reason message"
  - "SERIALIZABLE isolation for concurrent game state checks"
  - "Frontend faction ratio display with CSS variable colors and blocked state styling"

requirements-completed: [SESS-01, SESS-02, SESS-03, SESS-06]

# Metrics
duration: 82min
completed: 2026-03-28
---

# Phase 01 Plan 02: Session Join & Faction Ratio Summary

**FactionJoinService with SERIALIZABLE 3:2 ratio enforcement, faction count endpoint, and Empire/Alliance binary picker with ratio bar and blocked state UI**

## Performance

- **Duration:** 82 min
- **Started:** 2026-03-28T10:17:00Z
- **Completed:** 2026-03-28T11:39:02Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- FactionJoinService enforces 3:2 ratio (60% cap) with SERIALIZABLE transaction isolation, preventing TOCTOU race conditions on concurrent joins
- OfficerService.createOfficer calls ratio check before officer persistence, returning Korean error message when blocked
- Faction count REST endpoint (/worlds/{worldId}/faction-counts) exposes per-faction player counts for lobby UI
- Empire/Alliance binary faction picker with gold/blue faction colors, ratio bar with 60% cap indicator, and blocked state with Korean message
- 9 comprehensive unit tests covering ratio edge cases, NPC exclusion, empty session, and faction counts

## Task Commits

Each task was committed atomically:

1. **Task 1: Create FactionJoinService with 3:2 ratio enforcement and wire into officer creation** - `697600a` (feat, TDD)
2. **Task 2: Enhance join page with Empire/Alliance faction picker and ratio display** - `585047a` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/openlogh/service/FactionJoinService.kt` - 3:2 faction ratio enforcement service with SERIALIZABLE isolation
- `backend/game-app/src/main/kotlin/com/openlogh/service/OfficerService.kt` - Added factionJoinService dependency and ratio check before officer creation
- `backend/game-app/src/main/kotlin/com/openlogh/controller/OfficerController.kt` - Added faction-counts endpoint and IllegalStateException handling
- `backend/game-app/src/test/kotlin/com/openlogh/service/FactionJoinServiceTest.kt` - 9 tests for ratio enforcement edge cases
- `backend/game-app/src/test/kotlin/com/openlogh/service/OfficerServiceTest.kt` - Updated for FactionJoinService dependency
- `backend/game-app/src/test/kotlin/com/openlogh/controller/OfficerControllerTest.kt` - Updated for FactionJoinService dependency
- `frontend/src/app/(lobby)/lobby/join/page.tsx` - Empire/Alliance faction picker with ratio bar and blocked state
- `frontend/src/types/index.ts` - Added FactionCounts interface

## Decisions Made
- Used integer arithmetic for ratio comparison ((count+1)*5 > (total+1)*3) to avoid floating point precision issues
- Threw IllegalStateException for ratio violations to distinguish from IllegalArgumentException input validation errors, mapped to HTTP 409 Conflict in controller
- Added faction picker as a new section above existing character creation form rather than replacing the nation dropdown, preserving backward compatibility with OpenSamguk stat system (Phase 2 will update stats)
- Used CSS variable references (--empire-gold, --alliance-blue) for faction colors, matching existing globals.css definitions

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed pre-existing test compilation errors blocking test execution**
- **Found during:** Task 1 (test verification)
- **Issue:** 10 pre-existing test files had compilation errors (unresolved references to symbols like `withOptimisticRetry`, `findBySessionIdAndSrcNationIdOrDestFactionId`, various command-related symbols) blocking `compileTestKotlin`
- **Fix:** Temporarily renamed broken test files to .skip during test execution, restored after verification. No permanent changes to those files.
- **Files modified:** None permanently
- **Verification:** FactionJoinServiceTest, OfficerServiceTest, OfficerControllerTest all pass when pre-existing broken tests are excluded

**2. [Rule 3 - Blocking] Updated OfficerControllerTest for new constructor parameter**
- **Found during:** Task 1 (test compilation)
- **Issue:** OfficerControllerTest constructed OfficerController without the new factionJoinService parameter
- **Fix:** Added mock(FactionJoinService::class.java) to OfficerController constructor call in test
- **Files modified:** backend/game-app/src/test/kotlin/com/openlogh/controller/OfficerControllerTest.kt
- **Committed in:** 697600a (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes necessary for test execution. No scope creep.

## Issues Encountered
- Pre-existing test compilation failures in 10 test files (NationResourceCommandTest, DiplomacyServiceTest, OfficerOptimisticLockTest, InMemoryTurnHarness, and 6 dependent files) prevented running `compileTestKotlin` normally. These are NOT caused by this plan's changes. Worked around by temporarily excluding them during test verification.
- Kotlin compilation takes 7+ minutes on this machine due to Gradle daemon cold starts and the project's large codebase.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None - all data flows are wired (faction counts fetched from backend, ratio computed from live data).

## Next Phase Readiness
- Faction join enforcement is complete and ready for Phase 2 character system updates
- The join page still uses OpenSamguk's 5-stat system (leadership/strength/intel/politics/charm) -- Phase 2 will update to the 8-stat LOGH system
- The faction picker works alongside the existing nation dropdown; Phase 2 can remove the legacy dropdown

## Self-Check: PASSED

All files verified present, all commits verified in git log.

---
*Phase: 01-session-foundation*
*Completed: 2026-03-28*
