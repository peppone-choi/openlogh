---
phase: 01-session-foundation
plan: 03
subsystem: testing, ui
tags: [junit, mockito, kotlin, next.js, lobby, cp-recovery, re-entry]

# Dependency graph
requires:
  - phase: 01-session-foundation
    provides: CommandPointService, ReregistrationService, faction-counts API endpoint
provides:
  - Test suites proving offline CP recovery covers all officers (SMGT-01)
  - Test suites proving re-entry restriction rules D-07/D-08/D-09 (SESS-07)
  - Enhanced lobby session list with Empire/Alliance counts and D-04 status badges
affects: [02-character-rank, 03-command-points]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Mockito-based unit tests for game services with Officer entity builders"
    - "Per-world faction count fetching via Promise.allSettled for lobby display"

key-files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/CommandPointServiceTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/service/ReregistrationServiceTest.kt
  modified:
    - frontend/src/app/(lobby)/lobby/page.tsx
    - frontend/src/app/(lobby)/layout.tsx

key-decisions:
  - "Faction counts fetched per-world using Promise.allSettled to avoid N+1 blocking"
  - "Status labels mapped: 가오픈 -> 모집중 (green), 오픈 -> 진행중 (blue) per D-04"
  - "Empire/Alliance counts derived from faction-counts API index order (first=empire, second=alliance)"

patterns-established:
  - "Officer test builder pattern: makeOfficer() with sensible defaults and named params"
  - "D-04 session list display: empire-gold/alliance-blue-bright faction colors"

requirements-completed: [SMGT-01, SESS-07]

# Metrics
duration: 71min
completed: 2026-03-28
---

# Phase 01 Plan 03: Offline CP Recovery Tests, Re-entry Restriction Tests, and Lobby D-04 Enhancement Summary

**10 unit tests proving offline CP recovery and re-entry restrictions, plus lobby session list with Empire/Alliance faction counts and D-04 status badges**

## Performance

- **Duration:** 71 min
- **Started:** 2026-03-28T10:17:04Z
- **Completed:** 2026-03-28T11:28:00Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- CommandPointServiceTest (5 tests): Proves recoverAllCp iterates ALL officers (online, offline, NPC) with no online/offline filter; officers in tactical battle skip recovery; CP caps at max; recovery scales with stats
- ReregistrationServiceTest (5 tests): Proves ejected players can only re-enter same faction with generated characters (D-09), no cooldown (D-08), logout != ejection (D-07)
- Lobby session list now shows Empire/Alliance player counts with faction colors (empire-gold, alliance-blue-bright) and D-04 status badges (모집중/진행중)
- Header updated from "오픈삼국" to "오픈 은하영웅전설"

## Task Commits

Each task was committed atomically:

1. **Task 1: Create tests proving offline CP recovery and re-entry restrictions** - `24d7a46` (test)
2. **Task 2: Enhance lobby session list with Empire/Alliance counts and status per D-04** - `cef0290` (feat)

## Files Created/Modified
- `backend/game-app/src/test/kotlin/com/openlogh/engine/CommandPointServiceTest.kt` - 5 tests for CP recovery (SMGT-01)
- `backend/game-app/src/test/kotlin/com/openlogh/service/ReregistrationServiceTest.kt` - 5 tests for re-entry rules (SESS-07)
- `frontend/src/app/(lobby)/lobby/page.tsx` - D-04 status badges, Empire/Alliance counts with faction colors, faction count fetching
- `frontend/src/app/(lobby)/layout.tsx` - Header text updated to "오픈 은하영웅전설"

## Decisions Made
- Used Promise.allSettled for per-world faction count fetching to avoid blocking on individual API failures
- Mapped status labels per D-04: 가오픈 -> 모집중 (text-green-400), 오픈 -> 진행중 (text-blue-400)
- Empire/Alliance counts extracted from faction-counts API response by index order (matches existing join page pattern)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-existing test compilation errors blocked test execution**
- **Found during:** Task 1 (TDD test execution)
- **Issue:** 9 pre-existing test files had compilation errors (unresolved references in ArgSchemaValidationTest, GeneralMilitaryCommandTest, etc.) blocking Kotlin test compilation
- **Fix:** Temporarily moved broken files aside during test execution, restored after verification. No changes to broken files (out of scope per SCOPE BOUNDARY rule).
- **Files modified:** None (temporary rename only)
- **Verification:** All 10 new tests compiled and passed. Broken files restored to original state.

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Workaround for pre-existing test compilation failures. No scope creep.

## Issues Encountered
- Pre-existing Kotlin test compilation errors in 9 test files (ArgSchemaValidationTest, GeneralMilitaryCommandTest, GeneralPoliticalCommandTest, NationCommandTest, NationDiplomacyStrategicCommandTest, NationResearchSpecialCommandTest, NationResourceCommandTest, DiplomacyServiceTest, InMemoryTurnHarness). These prevent running `./gradlew :game-app:test` across all tests. Logged to deferred-items.md.
- JAVA_HOME was set to invalid path; resolved by using Amazon Corretto 17.0.17 installation.

## Known Stubs
None - all data sources are wired (faction counts from API, status from world metadata).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CP recovery and re-entry restriction logic verified with dedicated test suites
- Lobby UI enhanced per D-04 specification
- Pre-existing test compilation issues should be addressed in a future maintenance task

## Self-Check: PASSED

- All 4 created/modified files exist on disk
- Both task commits verified: 24d7a46, cef0290
- 10/10 tests passed (5 CommandPointServiceTest + 5 ReregistrationServiceTest)
- TypeScript compilation: zero errors

---
*Phase: 01-session-foundation*
*Completed: 2026-03-28*
