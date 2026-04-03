---
phase: 10
slug: diplomacy-and-scenario-data
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 10 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5 (via Spring Boot Test) |
| **Config file** | `backend/game-app/build.gradle.kts` (test dependencies) |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.*ParityTest" -x bootJar` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test -x bootJar` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.qa.parity.*ParityTest" -x bootJar`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test -x bootJar`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 10-01-01 | 01 | 1 | DIPL-01 | unit | `./gradlew :game-app:test --tests "*DiplomacyParityTest*"` | ❌ W0 | ⬜ pending |
| 10-01-02 | 01 | 1 | DIPL-02 | unit | `./gradlew :game-app:test --tests "*DiplomacyParityTest*"` | ❌ W0 | ⬜ pending |
| 10-02-01 | 02 | 1 | DIPL-03 | unit | `./gradlew :game-app:test --tests "*GameEndParityTest*"` | ❌ W0 | ⬜ pending |
| 10-03-01 | 03 | 2 | DATA-01 | unit | `./gradlew :game-app:test --tests "*ScenarioDataParityTest*"` | ❌ W0 | ⬜ pending |
| 10-03-02 | 03 | 2 | DATA-02 | unit | `./gradlew :game-app:test --tests "*ScenarioDataParityTest*"` | ❌ W0 | ⬜ pending |
| 10-03-03 | 03 | 2 | DATA-03 | unit | `./gradlew :game-app:test --tests "*ScenarioDataParityTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/game-app/src/test/kotlin/com/opensam/qa/parity/DiplomacyParityTest.kt` — covers DIPL-01, DIPL-02
- [ ] `backend/game-app/src/test/kotlin/com/opensam/qa/parity/GameEndParityTest.kt` — covers DIPL-03
- [ ] `backend/game-app/src/test/kotlin/com/opensam/qa/parity/ScenarioDataParityTest.kt` — covers DATA-01, DATA-02, DATA-03

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
