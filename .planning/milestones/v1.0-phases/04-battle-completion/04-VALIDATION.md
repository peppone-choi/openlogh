---
phase: 4
slug: battle-completion
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 4 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5.x (Spring Boot BOM) |
| **Config file** | `backend/build.gradle.kts` (useJUnitPlatform in all subprojects) |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests 'com.opensam.engine.war.*' --no-daemon` |
| **Full suite command** | `cd backend && ./gradlew test --no-daemon` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests 'com.opensam.engine.war.*' --no-daemon`
- **After every plan wave:** Run `cd backend && ./gradlew test --no-daemon`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 04-01-01 | 01 | 0 | BATTLE-02 | unit | `./gradlew :game-app:test --tests '*CounterStrategyTriggerTest*' --no-daemon` | ❌ W0 | ⬜ pending |
| 04-01-02 | 01 | 0 | BATTLE-03 | unit | `./gradlew :game-app:test --tests '*SustainedChargeTriggerTest*' --no-daemon` | ❌ W0 | ⬜ pending |
| 04-01-03 | 01 | 0 | BATTLE-04 | unit | `./gradlew :game-app:test --tests '*InjuryNullificationTriggerTest*' --no-daemon` | ❌ W0 | ⬜ pending |
| 04-01-04 | 01 | 0 | BATTLE-07 | unit | `./gradlew :game-app:test --tests '*UnavoidableCriticalTriggerTest*' --no-daemon` | ❌ W0 | ⬜ pending |
| 04-01-05 | 01 | 0 | BATTLE-08 | unit | `./gradlew :game-app:test --tests '*CityHealTriggerTest*' --no-daemon` | ❌ W0 | ⬜ pending |
| 04-02-01 | 02 | 1 | BATTLE-13 | unit | `./gradlew :game-app:test --tests '*BattleFormulaMatrixTest*' --no-daemon` | ❌ W0 | ⬜ pending |
| 04-02-02 | 02 | 1 | BATTLE-14 | unit | `./gradlew :game-app:test --tests '*SiegeParityTest*' --no-daemon` | Partial | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `CounterStrategyTriggerTest.kt` — stubs for BATTLE-02
- [ ] `SustainedChargeTriggerTest.kt` — stubs for BATTLE-03
- [ ] `InjuryNullificationTriggerTest.kt` — stubs for BATTLE-04
- [ ] `UnavoidableCriticalTriggerTest.kt` — stubs for BATTLE-07
- [ ] `CityHealTriggerTest.kt` — stubs for BATTLE-08 (CityHealTrigger exists, test needs creation)
- [ ] `BattleFormulaMatrixTest.kt` — stubs for BATTLE-13 (7x7 ArmType matrix)
- [ ] `SiegeParityTest.kt` — stubs for BATTLE-14 (extends existing WarUnitCityParityTest)

---

## Manual-Only Verifications

*All phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
