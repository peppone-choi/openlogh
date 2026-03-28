---
phase: 1
slug: session-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-03-28
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property               | Value                                                                             |
| ---------------------- | --------------------------------------------------------------------------------- |
| **Framework**          | JUnit 5 (Jupiter) + Mockito (backend), Vitest 3.2.4 (frontend)                    |
| **Config file**        | `backend/game-app/build.gradle.kts` (JUnit), `frontend/vitest.config.ts` (Vitest) |
| **Quick run command**  | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.*Test" -x bootJar`  |
| **Full suite command** | `cd backend && ./gradlew test`                                                    |
| **Estimated runtime**  | ~30 seconds                                                                       |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.openlogh.*Test" -x bootJar`
- **After every plan wave:** Run `cd backend && ./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID  | Plan | Wave | Requirement | Test Type   | Automated Command                                                                    | File Exists        | Status     |
| -------- | ---- | ---- | ----------- | ----------- | ------------------------------------------------------------------------------------ | ------------------ | ---------- |
| 01-01-01 | 01   | 1    | HARD-01     | unit        | `./gradlew :game-app:test --tests "com.openlogh.entity.OfficerOptimisticLockTest"`   | ❌ W0              | ⬜ pending |
| 01-01-02 | 01   | 1    | HARD-02     | unit        | `./gradlew :game-app:test --tests "com.openlogh.websocket.TacticalExecutorLeakTest"` | ❌ W0              | ⬜ pending |
| 01-02-01 | 02   | 2    | SESS-01     | integration | `./gradlew :game-app:test --tests "com.openlogh.service.SessionCreationTest"`        | ❌ W0              | ⬜ pending |
| 01-02-02 | 02   | 2    | SESS-02     | unit        | Existing `OfficerServiceTest` extended                                               | ✅ Partial         | ⬜ pending |
| 01-02-03 | 02   | 2    | SESS-03     | unit        | `./gradlew :game-app:test --tests "com.openlogh.service.FactionJoinServiceTest"`     | ❌ W0              | ⬜ pending |
| 01-02-04 | 02   | 2    | SESS-06     | unit        | Verify scenario JSON contains correct tickSeconds                                    | ✅ Existing        | ⬜ pending |
| 01-02-05 | 02   | 2    | SESS-07     | unit        | Existing `ReregistrationService` tests                                               | ✅ Needs extension | ⬜ pending |
| 01-03-01 | 03   | 3    | SMGT-01     | unit        | `./gradlew :game-app:test --tests "com.openlogh.engine.CommandPointServiceTest"`     | ❌ W0              | ⬜ pending |

_Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky_

---

## Wave 0 Requirements

- [ ] `backend/game-app/src/test/kotlin/com/openlogh/entity/OfficerOptimisticLockTest.kt` — stubs for HARD-01
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/websocket/TacticalExecutorLeakTest.kt` — stubs for HARD-02
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/FactionJoinServiceTest.kt` — stubs for SESS-03
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/CommandPointServiceTest.kt` — stubs for SMGT-01
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/SessionCreationTest.kt` — stubs for SESS-01

_5 test files needed before execution begins._

---

## Manual-Only Verifications

| Behavior                                     | Requirement  | Why Manual             | Test Instructions                                                                      |
| -------------------------------------------- | ------------ | ---------------------- | -------------------------------------------------------------------------------------- |
| Lobby session list displays correct info     | SESS-01/D-04 | Visual UI verification | Open lobby page, create session, verify scenario name/player count/date/status columns |
| Faction picker shows ratio and block message | SESS-03/D-02 | Visual UI verification | Fill one faction to 60%, try to join it, verify block message appears                  |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
