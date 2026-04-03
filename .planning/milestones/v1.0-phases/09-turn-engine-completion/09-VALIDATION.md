---
phase: 9
slug: turn-engine-completion
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.x + AssertJ 3.x |
| **Config file** | `backend/game-app/src/test/resources/application-test.yml` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.TurnServiceTest" -x :gateway-app:test` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.TurnServiceTest" -x :gateway-app:test`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 09-01-01 | 01 | 1 | TURN-01 | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*checkWander*"` | ✅ Extend TurnServiceTest.kt | ⬜ pending |
| 09-01-02 | 01 | 1 | TURN-02 | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*updateOnline*"` | ✅ Extend TurnServiceTest.kt | ⬜ pending |
| 09-01-03 | 01 | 1 | TURN-03 | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*checkOverhead*"` | ✅ Extend TurnServiceTest.kt | ⬜ pending |
| 09-01-04 | 01 | 1 | TURN-04 | unit | `./gradlew :game-app:test --tests "*TurnServiceTest*updateGeneralNumber*"` | ✅ Extend TurnServiceTest.kt | ⬜ pending |
| 09-02-01 | 02 | 2 | TURN-05 | unit | `./gradlew :game-app:test --tests "*TurnPipelineParityTest*"` | ✅ TurnPipelineParityTest.kt | ⬜ pending |
| 09-02-02 | 02 | 2 | TURN-06 | unit | `./gradlew :game-app:test --tests "*DisasterParityTest*"` | ❌ New or extend EconomyIntegrationParityTest.kt | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- None critical — existing test infrastructure (TurnServiceTest.kt, TurnPipelineParityTest.kt, EconomyIntegrationParityTest.kt) covers all needs. Tests will be added to existing files per D-07.

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| RNG seed `"disater"` vs `"disaster"` | TURN-06 | Requires checking if exact RNG parity is intended | Compare PHP seed string with Kotlin; decide if typo should be replicated |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
