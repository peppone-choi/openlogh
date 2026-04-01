---
phase: 2
slug: numeric-type-safety
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.x (backend), H2 PostgreSQL mode (integration) |
| **Config file** | `backend/game-app/src/main/resources/application-test.yml` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.*"` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.*"`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 02-01-01 | 01 | 1 | TYPE-01 | unit | `./gradlew :game-app:test --tests "*ShortOverflow*"` | ❌ W0 | ⬜ pending |
| 02-01-02 | 01 | 1 | TYPE-01 | unit | `./gradlew :game-app:test --tests "*CoerceGuard*"` | ❌ W0 | ⬜ pending |
| 02-02-01 | 02 | 1 | TYPE-02 | unit | `./gradlew :game-app:test --tests "*RoundingParity*"` | ❌ W0 | ⬜ pending |
| 02-02-02 | 02 | 1 | TYPE-02 | integration | `./gradlew :game-app:test --tests "*GoldenSnapshot*"` | ✅ | ⬜ pending |
| 02-02-03 | 02 | 2 | TYPE-03 | unit | `./gradlew :game-app:test --tests "*IntegerDivision*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/game-app/src/test/kotlin/com/opensam/engine/ShortOverflowTest.kt` — boundary tests for Short field coerceIn guards (TYPE-01)
- [ ] `backend/game-app/src/test/kotlin/com/opensam/engine/RoundingParityTest.kt` — PHP vs Kotlin rounding golden values (TYPE-02)
- [ ] `backend/game-app/src/test/kotlin/com/opensam/engine/IntegerDivisionParityTest.kt` — negative dividend/divisor edge cases (TYPE-03)

*Existing infrastructure covers simulation harness (InMemoryTurnHarness, GoldenSnapshotTest pattern).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 200-turn cumulative drift | TYPE-02 | Requires PHP baseline values from legacy execution | Run legacy PHP 200-turn sim, record accumulator fields, compare with Kotlin output |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
