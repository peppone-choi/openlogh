---
phase: 5
slug: modifier-pipeline
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-01
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5 + AssertJ |
| **Config file** | `backend/game-app/build.gradle.kts` (testImplementation) |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.*"` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.engine.modifier.*"`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | MOD-01 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.modifier.ItemDomesticModifierTest"` | Wave 0 | pending |
| 05-01-02 | 01 | 1 | MOD-02 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.modifier.SpecialDomesticModifierTest"` | Wave 0 | pending |
| 05-01-03 | 01 | 1 | MOD-03 | unit | `./gradlew :game-app:test --tests "com.opensam.engine.modifier.OfficerLevelModifierTest"` | EXISTS (partial) | pending |
| 05-02-01 | 02 | 2 | MOD-04 | integration | `./gradlew :game-app:test --tests "com.opensam.engine.modifier.ModifierStackingParityTest"` | Wave 0 | pending |

*Status: pending / green / red / flaky*

---

## Wave 0 Requirements

- [ ] `SpecialDomesticModifierTest.kt` -- golden values for all 13 original domestic specials + che_ variants (MOD-02)
- [ ] `ItemDomesticModifierTest.kt` -- golden values for MiscItem domestic effects (MOD-01)
- [ ] `ModifierStackingParityTest.kt` -- multi-source stacking golden values (MOD-04)
- [ ] Extend existing `OfficerLevelModifierTest.kt` with domestic score bonus per-action tests (MOD-03)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Legacy PHP triggerCall order matches Kotlin getModifiers() order | MOD-04 | Requires reading PHP source code | Clone legacy-core/, read func.php triggerCall, compare execution order with ModifierService.getModifiers() |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
