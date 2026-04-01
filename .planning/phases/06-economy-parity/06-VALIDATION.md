---
phase: 6
slug: economy-parity
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.x (Spring Boot Test) + H2 in-memory (PostgreSQL mode) |
| **Config file** | `backend/game-app/src/main/resources/application-test.yml` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.economy.*"` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test` |
| **Estimated runtime** | ~30 seconds (economy tests), ~120 seconds (full suite) |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.economy.*"`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | ECON-01 | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest.taxCollection*"` | ✅ | ⬜ pending |
| 06-01-02 | 01 | 1 | ECON-02 | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest.tradeIncome*"` | ✅ | ⬜ pending |
| 06-01-03 | 01 | 1 | ECON-03 | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest.supply*"` | ✅ | ⬜ pending |
| 06-01-04 | 01 | 1 | ECON-04 | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest.population*"` | ✅ | ⬜ pending |
| 06-01-05 | 01 | 1 | ECON-05 | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest.infrastructure*"` | ✅ | ⬜ pending |
| 06-01-06 | 01 | 1 | ECON-06 | unit | `./gradlew :game-app:test --tests "*.EconomyFormulaParityTest.salary*"` | ✅ | ⬜ pending |
| 06-02-01 | 02 | 2 | ECON-01..06 | integration | `./gradlew :game-app:test --tests "*.EconomyIntegrationTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Verify `EconomyFormulaParityTest.kt` compiles and runs green with current golden values
- [ ] Verify `EconomyEventParityTest.kt` compiles and runs green
- [ ] Create integration test stub for 24-turn simulation (`EconomyIntegrationTest.kt`)

*Existing infrastructure covers most phase requirements. Wave 0 adds integration test scaffolding only.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| PHP golden value extraction | ECON-01..06 | Requires reading legacy PHP source and tracing formula execution | Clone legacy-core, trace each formula through PHP code, record input→output pairs |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
