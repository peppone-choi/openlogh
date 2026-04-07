---
phase: 8
slug: scenario-character-system
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-07
---

# Phase 8 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) |
| **Config file** | `backend/game-app/build.gradle.kts` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.tactical.*" -x bootJar` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test -x bootJar` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.openlogh.engine.tactical.*" -x bootJar`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test -x bootJar`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 08-01-01 | 01 | 0 | ENGINE-02 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandBufferTest" -x bootJar` | ❌ W0 | ⬜ pending |
| 08-01-02 | 01 | 0 | ENGINE-03 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandHierarchyTest" -x bootJar` | ❌ W0 | ⬜ pending |
| 08-02-01 | 02 | 1 | ENGINE-01 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.TacticalBattleEngineTest" -x bootJar` | ✅ (needs update) | ⬜ pending |
| 08-02-02 | 02 | 1 | ENGINE-01 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.DetectionServiceTest" -x bootJar` | ✅ (needs import update) | ⬜ pending |
| 08-03-01 | 03 | 2 | ENGINE-02 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandBufferTest" -x bootJar` | ❌ W0 | ⬜ pending |
| 08-04-01 | 04 | 2 | ENGINE-03 | unit | `./gradlew :game-app:test --tests "com.openlogh.engine.tactical.CommandHierarchyTest" -x bootJar` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `CommandBufferTest.kt` — stubs for ENGINE-02 (buffer + drain + batch apply)
- [ ] `CommandHierarchyTest.kt` — stubs for ENGINE-03 (auto-generation, rank ordering)
- [ ] Update `TacticalBattleEngineTest.kt` — verify merged fields work in tick processing
- [ ] Update `DetectionServiceTest.kt` — verify imports after DetectionEngine move
- [ ] Cleanup/delete war/ test files that reference deleted classes

*Existing JUnit 5 infrastructure covers framework needs.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| WebSocket command buffering under concurrent load | ENGINE-02 | Requires real WebSocket connections | Start game, open 2+ browser tabs, send commands simultaneously, verify tick processes them in batch |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
