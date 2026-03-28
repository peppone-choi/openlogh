---
phase: 2
slug: character-rank-and-organization
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-03-28
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property                   | Value                                                                        |
| -------------------------- | ---------------------------------------------------------------------------- |
| **Framework (Backend)**    | JUnit 5 (Jupiter) + Spring Boot Test                                         |
| **Framework (Frontend)**   | Vitest 3.2.4                                                                 |
| **Config file (Backend)**  | backend/game-app/build.gradle.kts (standard Spring Boot test)                |
| **Config file (Frontend)** | frontend/vitest.config.ts                                                    |
| **Quick run (Backend)**    | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.*" -x bootJar` |
| **Quick run (Frontend)**   | `cd frontend && pnpm vitest run --reporter=verbose`                          |
| **Full suite command**     | Both above combined                                                          |
| **Estimated runtime**      | ~45 seconds                                                                  |

---

## Sampling Rate

- **After every task commit:** `cd backend && ./gradlew :game-app:test --tests "com.openlogh.*" -x bootJar` (backend) or `cd frontend && pnpm vitest run` (frontend)
- **After every plan wave:** Full backend + frontend test suite
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID  | Plan | Wave | Requirement   | Test Type | Automated Command                                                                       | File Exists  | Status  |
| -------- | ---- | ---- | ------------- | --------- | --------------------------------------------------------------------------------------- | ------------ | ------- |
| 02-01-01 | 01   | 1    | HARD-03       | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.PositionCardServiceTest"`       | W0           | pending |
| 02-01-02 | 01   | 1    | HARD-03       | unit      | `./gradlew :game-app:test -x bootJar`                                                   | needs update | pending |
| 02-02-01 | 02   | 1    | CHAR-01/02/03 | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.CharacterCreationServiceTest"`  | W0           | pending |
| 02-02-02 | 02   | 1    | CHAR-07/08    | unit      | `./gradlew :game-app:test -x bootJar`                                                   | needs update | pending |
| 02-03-01 | 03   | 2    | RANK-01~14    | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.RankLadderServiceTest"`         | W0           | pending |
| 02-03-02 | 03   | 2    | RANK-05/07/09 | unit      | `./gradlew :game-app:test -x bootJar`                                                   | needs update | pending |
| 02-03-03 | 03   | 2    | CHAR-04/05    | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.StatGrowthServiceTest"`         | W0           | pending |
| 02-04-01 | 04   | 2    | ORG-02/03/08  | unit      | `./gradlew :game-app:test -x bootJar`                                                   | needs update | pending |
| 02-05-01 | 05   | 3    | CHAR-01/02    | tsc       | `cd frontend && npx tsc --noEmit`                                                       | W0           | pending |
| 02-05-02 | 05   | 3    | CHAR-03/07/08 | tsc       | `cd frontend && npx tsc --noEmit`                                                       | W0           | pending |
| 02-06-01 | 06   | 3    | ORG-01/02/03  | tsc       | `cd frontend && npx tsc --noEmit`                                                       | W0           | pending |
| 02-06-02 | 06   | 3    | CHAR-03/06    | tsc       | `cd frontend && npx tsc --noEmit`                                                       | W0           | pending |
| 02-07-01 | 07   | 4    | visual        | manual    | Human visual inspection                                                                 | N/A          | pending |
| 02-08-01 | 08   | 2    | CHAR-09~12    | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.CharacterLifecycleServiceTest"` | W0           | pending |
| 02-08-02 | 08   | 2    | CHAR-14       | unit      | `./gradlew :game-app:test --tests "com.openlogh.service.CharacterCreationServiceTest"`  | needs update | pending |

_Status: pending / green / red / flaky_

---

## Wave 0 Requirements

- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/PositionCardServiceTest.kt` — stubs for HARD-03 CRUD
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/RankLadderServiceTest.kt` — stubs for RANK-02, RANK-06, RANK-08
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/CharacterCreationServiceTest.kt` — stubs for CHAR-02, CHAR-03
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/StatGrowthServiceTest.kt` — stubs for CHAR-04, CHAR-05
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/service/CharacterLifecycleServiceTest.kt` — stubs for CHAR-09~12
- [ ] Update existing `CommandExecutorTest.kt` — add relational card gating tests

---

## Manual-Only Verifications

| Behavior                  | Requirement    | Why Manual                                         | Test Instructions                                                                  |
| ------------------------- | -------------- | -------------------------------------------------- | ---------------------------------------------------------------------------------- |
| Org chart tree navigation | ORG-02, ORG-03 | Visual tree component requires browser interaction | Open /org-chart, expand Empire tree, verify all 100+ positions render with holders |
| 8-stat bar chart display  | CHAR-03        | Visual rendering verification                      | Open officer profile, verify 8 horizontal bars with numeric values                 |
| Character selection flow  | CHAR-01        | Multi-step UI interaction                          | Join session, select original character, verify stats are fixed and pre-populated  |

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 45s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** signed-off (revision pass)
