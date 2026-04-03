---
phase: 8
slug: npc-ai-parity
status: ready
nyquist_compliant: true
wave_0_complete: true
created: 2026-04-02
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5 (via Spring Boot Test) |
| **Config file** | `backend/game-app/src/test/resources/application-test.yml` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.ai.*" --tests "com.opensam.qa.parity.NpcAiParityTest" -x :gateway-app:test` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.ai.*" --tests "com.opensam.qa.parity.NpcAiParityTest" -x :gateway-app:test`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 1 | AI-02, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.qa.parity.NpcAiParityTest" --tests "com.opensam.engine.ai.GeneralAITest"` | Exists, extend | pending |
| 08-01-02 | 01 | 1 | AI-02, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.qa.parity.NpcAiParityTest" --tests "com.opensam.engine.ai.GeneralAITest"` | Exists, extend | pending |
| 08-02-01 | 02 | 2 | AI-01, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.GeneralAITest"` | Exists, extend | pending |
| 08-02-02 | 02 | 2 | AI-01, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.GeneralAITest"` | Exists, extend | pending |
| 08-03-01 | 03 | 2 | AI-01, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.GeneralAITest"` | Exists, extend | pending |
| 08-03-02 | 03 | 2 | AI-01, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.GeneralAITest" --tests "com.opensam.engine.ai.NationAITest"` | Exists, extend | pending |
| 08-04-01 | 04 | 2 | AI-03, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.GeneralAITest" --tests "com.opensam.engine.ai.NationAITest"` | Exists, extend | pending |
| 08-04-02 | 04 | 2 | AI-03, AI-04 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.ai.GeneralAITest" --tests "com.opensam.engine.ai.NationAITest"` | Exists, extend | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements. Golden value assertions are added to existing test files (GeneralAITest.kt, NationAITest.kt, NpcAiParityTest.kt) per D-06.*

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

**Approval:** approved
