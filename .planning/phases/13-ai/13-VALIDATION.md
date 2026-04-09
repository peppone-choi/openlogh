---
phase: 13
slug: ai
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-09
---

# Phase 13 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) |
| **Config file** | backend/game-app/build.gradle.kts |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.ai.*" -x :gateway-app:test` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.ai.*" -x :gateway-app:test`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 13-01-01 | 01 | 1 | SAI-01, SAI-02 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.StrategicPowerScorerTest" -x :gateway-app:test` | ❌ W0 | ⬜ pending |
| 13-01-02 | 01 | 1 | SAI-02 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.FogOfWarEstimatorTest" -x :gateway-app:test` | ❌ W0 | ⬜ pending |
| 13-01-03 | 01 | 1 | SAI-02 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.OperationTargetSelectorTest" -x :gateway-app:test` | ❌ W0 | ⬜ pending |
| 13-01-04 | 01 | 1 | SAI-02 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.FleetAllocatorTest" -x :gateway-app:test` | ❌ W0 | ⬜ pending |
| 13-02-01 | 02 | 2 | SAI-01 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.NationAITest" -x :gateway-app:test` | ✅ (update) | ⬜ pending |
| 13-02-02 | 02 | 2 | SAI-01 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.ai.strategic.StrategicOperationPlannerTest" -x :gateway-app:test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/StrategicPowerScorerTest.kt` — stubs for composite power scoring
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/OperationTargetSelectorTest.kt` — stubs for SAI-02 operation type selection
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FleetAllocatorTest.kt` — stubs for D-09 fleet allocation
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/FogOfWarEstimatorTest.kt` — stubs for D-02 intelligence noise
- [ ] `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/strategic/StrategicOperationPlannerTest.kt` — stubs for end-to-end planner

*Existing infrastructure covers test framework — only test stubs needed.*

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
