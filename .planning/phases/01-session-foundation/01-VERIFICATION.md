---
phase: 01-session-foundation
verified: 2026-03-28T13:00:00Z
status: passed
score: 5/5 must-haves verified
---

# Phase 1: Session Foundation Verification Report

**Phase Goal:** Players can create and join game sessions, choose factions, and the backend is free of exploit-grade concurrency bugs
**Verified:** 2026-03-28T13:00:00Z
**Status:** passed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| #   | Truth                                                                                                                 | Status   | Evidence                                                                                                                                                                                                                                                                                                     |
| --- | --------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1   | A player can create a new game session by selecting a scenario and choosing Empire or Alliance faction                | VERIFIED | `join/page.tsx` has Empire/Alliance binary picker with faction colors, `FactionJoinService` enforces 3:2 ratio, `OfficerController` exposes `/faction-counts` endpoint                                                                                                                                       |
| 2   | A second player can join the same session, select a faction, and both players are bound with separate officer slots   | VERIFIED | `OfficerService.createOfficer` calls `factionJoinService.canJoinFaction` before persisting; each officer gets unique `userId` binding; 9 edge-case tests in `FactionJoinServiceTest`                                                                                                                         |
| 3   | A logged-out player's character remains present in the game world and CP continues recovering while offline           | VERIFIED | `CommandPointService.recoverAllCp` iterates ALL officers via `findBySessionId` with zero online/offline filter; 5 tests in `CommandPointServiceTest` prove this including NPC + offline mix                                                                                                                  |
| 4   | Two concurrent command submissions cannot both succeed when only one CP unit is available (race condition eliminated) | VERIFIED | `Officer.kt` has `@Version Long` field; `V38__add_officer_version_column.sql` adds BIGINT column; `CommandExecutor.withOptimisticRetry` catches `OptimisticLockingFailureException` with 3 retries; 4 tests in `OfficerOptimisticLockTest`                                                                   |
| 5   | Ending a tactical battle does not leak JVM threads (executor leak fixed)                                              | VERIFIED | `TacticalWebSocketController` line 218 uses `turnScheduler.scheduleOnce` (managed pool); zero instances of `Executors.newSingleThreadScheduledExecutor` in main source; `TacticalTurnScheduler.scheduleOnce` reuses existing 4-thread pool with `@PreDestroy` cleanup; 4 tests in `TacticalExecutorLeakTest` |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact                                                       | Expected                                                | Status   | Details                                                                                    |
| -------------------------------------------------------------- | ------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------ |
| `backend/.../entity/Officer.kt`                                | @Version optimistic locking on Officer entity           | VERIFIED | `@Version` annotation on `version: Long` field at line 19                                  |
| `backend/.../db/migration/V38__add_officer_version_column.sql` | Flyway migration adding version column                  | VERIFIED | `ALTER TABLE officer ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0`           |
| `backend/.../command/CommandExecutor.kt`                       | withOptimisticRetry method with retry handler           | VERIFIED | Method at line 40 with `OptimisticLockingFailureException` catch at line 50                |
| `backend/.../engine/tactical/TacticalTurnScheduler.kt`         | Public scheduleOnce method for delayed cleanup          | VERIFIED | `fun scheduleOnce(delay: Long, unit: TimeUnit, task: () -> Unit)` at line 117              |
| `backend/.../websocket/TacticalWebSocketController.kt`         | Fixed executor leak using managed scheduler             | VERIFIED | `turnScheduler.scheduleOnce` at line 218; no `newSingleThreadScheduledExecutor`            |
| `backend/.../service/FactionJoinService.kt`                    | 3:2 faction ratio enforcement service                   | VERIFIED | `MAX_FACTION_RATIO_NUMERATOR=3`, `MAX_FACTION_RATIO_DENOMINATOR=5`, SERIALIZABLE isolation |
| `backend/.../test/.../OfficerOptimisticLockTest.kt`            | Test proving @Version prevents concurrent CP corruption | VERIFIED | 4 tests: annotation check, retry success, retry exhaustion, officer not found              |
| `backend/.../test/.../TacticalExecutorLeakTest.kt`             | Test proving no executor thread leak                    | VERIFIED | 4 tests: execution after delay, cancellation, source-level check, shutdown                 |
| `backend/.../test/.../FactionJoinServiceTest.kt`               | Tests for faction ratio edge cases                      | VERIFIED | 9 tests: ratio pass/fail, edge cases, NPC exclusion, faction counts                        |
| `backend/.../test/.../CommandPointServiceTest.kt`              | Tests proving offline CP recovery works                 | VERIFIED | 5 tests: normal/tactical/all-officers/cap/stat-scaling                                     |
| `backend/.../test/.../ReregistrationServiceTest.kt`            | Tests for re-entry restriction rules                    | VERIFIED | 5 tests: non-ejected/same-faction/different-faction/original-char/death-only               |
| `frontend/src/app/(lobby)/lobby/join/page.tsx`                 | Faction picker with Empire/Alliance binary selection    | VERIFIED | Empire gold/Alliance blue cards, ratio bar, blocked state with Korean message              |
| `frontend/src/app/(lobby)/lobby/page.tsx`                      | Session list with Empire/Alliance counts and status     | VERIFIED | Faction counts fetched via API, empire-gold/alliance-blue-bright colors, D-04 badges       |
| `frontend/src/types/index.ts`                                  | FactionCounts interface                                 | VERIFIED | `export interface FactionCounts { [factionId: number]: number; }` at line 114              |

### Key Link Verification

| From                             | To                                | Via                                         | Status | Details                                                                  |
| -------------------------------- | --------------------------------- | ------------------------------------------- | ------ | ------------------------------------------------------------------------ |
| Officer.kt                       | V38 migration                     | @Version Long maps to BIGINT column         | WIRED  | `version: Long` in entity matches `version BIGINT` in migration          |
| TacticalWebSocketController.kt   | TacticalTurnScheduler.kt          | `turnScheduler.scheduleOnce` for cleanup    | WIRED  | Constructor-injected at line 40; called at line 218                      |
| FactionJoinService.kt            | OfficerService.kt                 | Faction ratio check before officer creation | WIRED  | `factionJoinService` injected; `canJoinFaction` called at line 55        |
| frontend join/page.tsx           | backend OfficerController         | REST API for faction count and join         | WIRED  | Fetches `/worlds/${currentWorld.id}/faction-counts` via api.get          |
| CommandPointService.recoverAllCp | OfficerRepository.findBySessionId | Iterates ALL officers, no filter            | WIRED  | `findBySessionId` at line 154, no userId/online check                    |
| ReregistrationService            | Officer.meta                      | Checks ejectedFrom and wasOriginal flags    | WIRED  | `meta.containsKey("ejectedFrom")` at line 34; `markAsEjected` sets flags |
| frontend lobby/page.tsx          | backend faction-counts API        | Promise.allSettled per-world fetch          | WIRED  | `api.get<FactionCounts>` at line 201, stored in worldFactionCounts state |

### Data-Flow Trace (Level 4)

| Artifact       | Data Variable      | Source                            | Produces Real Data | Status  |
| -------------- | ------------------ | --------------------------------- | ------------------ | ------- |
| lobby/page.tsx | worldFactionCounts | `/worlds/{id}/faction-counts` API | Yes (DB query)     | FLOWING |
| join/page.tsx  | factionCounts      | `/worlds/{id}/faction-counts` API | Yes (DB query)     | FLOWING |
| lobby/page.tsx | worlds             | worldStore.fetchWorlds            | Yes (DB query)     | FLOWING |

### Behavioral Spot-Checks

| Behavior                          | Command                                                                 | Result                      | Status |
| --------------------------------- | ----------------------------------------------------------------------- | --------------------------- | ------ |
| Backend Kotlin compiles           | `./gradlew :game-app:compileKotlin`                                     | BUILD SUCCESSFUL            | PASS   |
| Frontend TypeScript compiles      | `npx tsc --noEmit`                                                      | Zero errors                 | PASS   |
| No leaked executor in main source | `grep newSingleThreadScheduledExecutor` on main source                  | Comment only (line 114 doc) | PASS   |
| All 6 task commits exist          | `git log --oneline` for c813117 ef6d972 697600a 585047a 24d7a46 cef0290 | All found                   | PASS   |

### Requirements Coverage

| Requirement | Source Plan | Description                                         | Status    | Evidence                                                                               |
| ----------- | ----------- | --------------------------------------------------- | --------- | -------------------------------------------------------------------------------------- |
| SESS-01     | 01-02       | Session creation with scenario selection            | SATISFIED | Existing session creation flow + scenario defaults verified                            |
| SESS-02     | 01-02       | Join session with character selection               | SATISFIED | OfficerService.createOfficer + faction ratio check before persist                      |
| SESS-03     | 01-02       | Empire/Alliance faction selection                   | SATISFIED | FactionJoinService + binary picker in join/page.tsx                                    |
| SESS-06     | 01-02       | Game time 24x real-time speed                       | SATISFIED | SessionState.tickSeconds configured in scenario JSON (verified, no code change needed) |
| SESS-07     | 01-03       | Ejected player re-entry restrictions                | SATISFIED | ReregistrationService enforces same-faction + no original character; 5 tests           |
| SMGT-01     | 01-03       | Offline persistence (character exists, CP recovers) | SATISFIED | recoverAllCp has no online/offline filter; 5 tests prove it                            |
| HARD-01     | 01-01       | Officer @Version optimistic locking (CP race fix)   | SATISFIED | @Version on Officer, withOptimisticRetry in CommandExecutor; 4 tests                   |
| HARD-02     | 01-01       | Tactical executor thread leak fix                   | SATISFIED | scheduleOnce on managed pool, no inline executor; 4 tests                              |

No orphaned requirements found. All 8 requirement IDs declared in ROADMAP Phase 1 are covered by plans and verified.

### Anti-Patterns Found

| File         | Line | Pattern | Severity | Impact                                                          |
| ------------ | ---- | ------- | -------- | --------------------------------------------------------------- |
| (none found) | -    | -       | -        | No TODO/FIXME/placeholder/stub patterns in phase-modified files |

### Known Limitations (Not Anti-Patterns)

- **Pre-existing test compilation errors:** 9 pre-existing test files (NationResourceCommandTest, DiplomacyServiceTest, etc.) have unresolved references unrelated to Phase 1 changes. These prevent running the full `./gradlew :game-app:test` suite. Phase 1's own tests compile and pass when run in isolation. Documented in `deferred-items.md`.
- **Join page still uses OpenSamguk 5-stat system:** The character creation form in join/page.tsx still uses legacy stat names (leadership/strength/intel/politics/charm). This is intentional -- Phase 2 will migrate to the 8-stat LOGH system.

### Human Verification Required

### 1. Faction Picker Visual Appearance

**Test:** Navigate to the join page, select a session, and verify the Empire/Alliance binary picker displays correctly with gold and blue faction colors.
**Expected:** Two cards side by side -- Empire with gold border/accent, Alliance with blue border/accent. Ratio bar below showing faction balance with 60% cap indicator.
**Why human:** Visual layout, color rendering, and responsive behavior cannot be verified programmatically.

### 2. Blocked Faction State

**Test:** With a session where one faction has 60%+ of players, attempt to select the capped faction on the join page.
**Expected:** Capped faction card shows `opacity-50 cursor-not-allowed` styling with Korean message: "{faction} 인원이 가득 찼습니다 -- 다른 진영에 참가하거나 자리가 날 때까지 기다려주세요"
**Why human:** Requires a live session with enough players to trigger the ratio cap.

### 3. Lobby Session List D-04 Display

**Test:** Open the lobby page with active sessions and verify Empire/Alliance counts display with correct colors.
**Expected:** Each session card shows "인원: {empire}/{alliance} ({total}/{max})" with empire count in gold and alliance count in blue. Status badges show "모집중" (green) for pre-open sessions and "진행중" (blue) for active sessions.
**Why human:** Requires running server with active sessions to verify live data rendering.

### Gaps Summary

No gaps found. All 5 success criteria truths are verified. All 8 requirement IDs are satisfied. All artifacts exist, are substantive, are wired, and have flowing data. No blocker anti-patterns detected. Backend and frontend both compile successfully.

---

_Verified: 2026-03-28T13:00:00Z_
_Verifier: Claude (gsd-verifier)_
