---
phase: 3
slug: battle-framework-and-core-triggers
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter (via Spring Boot Test) |
| **Config file** | `backend/game-app/build.gradle.kts` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.war.*Trigger*" --no-daemon` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test --no-daemon` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.war.*" --no-daemon`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test --no-daemon`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 03-01-01 | 01 | 1 | BATTLE-01 | unit | `./gradlew :game-app:test --tests "*.WarUnitTriggerTest"` | ❌ W0 | ⬜ pending |
| 03-01-02 | 01 | 1 | BATTLE-12 | unit | `./gradlew :game-app:test --tests "*.MusangKillnumTest"` | ❌ W0 | ⬜ pending |
| 03-02-01 | 02 | 2 | BATTLE-05 | unit | `./gradlew :game-app:test --tests "*.IntimidationTriggerTest"` | ❌ W0 | ⬜ pending |
| 03-02-02 | 02 | 2 | BATTLE-06 | unit | `./gradlew :game-app:test --tests "*.SnipingTriggerTest"` | ❌ W0 | ⬜ pending |
| 03-02-03 | 02 | 2 | BATTLE-09 | unit | `./gradlew :game-app:test --tests "*.BattleHealTriggerTest"` | ❌ W0 | ⬜ pending |
| 03-02-04 | 02 | 2 | BATTLE-10 | unit | `./gradlew :game-app:test --tests "*.RageTriggerTest"` | ❌ W0 | ⬜ pending |
| 03-03-01 | 03 | 2 | BATTLE-11 | unit | `./gradlew :game-app:test --tests "*.BattleExperienceParityTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `WarUnitTriggerTest.kt` — covers BATTLE-01 (framework hook verification)
- [ ] `IntimidationTriggerTest.kt` — covers BATTLE-05
- [ ] `SnipingTriggerTest.kt` — covers BATTLE-06
- [ ] `BattleHealTriggerTest.kt` — covers BATTLE-09
- [ ] `RageTriggerTest.kt` — covers BATTLE-10
- [ ] `BattleExperienceParityTest.kt` — covers BATTLE-11
- [ ] `MusangKillnumTest.kt` — covers BATTLE-12

Existing test infrastructure is mature (50+ test files in engine/). `BattleTriggerTest.kt` and `BattleEngineParityTest.kt` provide established patterns.

---

## Manual-Only Verifications

All phase behaviors have automated verification.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
