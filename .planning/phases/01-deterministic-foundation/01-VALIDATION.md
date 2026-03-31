---
phase: 1
slug: deterministic-foundation
status: draft
nyquist_compliant: true
wave_0_complete: true
created: 2026-03-31
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter + Spring Boot Test |
| **Config file** | `backend/game-app/src/test/resources/application-test.yml` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.*" -x :gateway-app:test` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick engine tests
- **After every plan wave:** Run full game-app test suite
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 01-01 | 01 | 1 | FOUND-01 | unit | `./gradlew :game-app:compileKotlin -x :gateway-app:compileKotlin` + grep | N/A (compile + grep) | ⬜ pending |
| 01-02 | 01 | 1 | FOUND-01, FOUND-05 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.RandUtilTest" --tests "com.opensam.engine.trigger.GeneralTriggerTest"` | ✅ | ⬜ pending |
| 01-03 | 01 | 1 | FOUND-02 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.LiteHashDRBGTest"` | ✅ | ⬜ pending |
| 01-04 | 02 | 2 | FOUND-03 | integration | `./gradlew :game-app:test --tests "com.opensam.engine.*"` + grep | ✅ | ⬜ pending |
| 01-05 | 02 | 2 | FOUND-04 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.turn.*"` + grep | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [x] Extend `LiteHashDRBGTest.kt` with 100+ draw golden vectors (hardcoded, not TODO) and edge seeds including Long.MAX_VALUE
- [x] Add `RandUtilTest` single-element choice test case
- [x] Add `GeneralTriggerTest` CityHealTrigger constructor injection test (was marked ❌ W0, now covered by Plan 01-01 Task 2)

*All Wave 0 requirements addressed in plan tasks.*

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved (revised 2026-03-31)
