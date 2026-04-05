---
phase: 7
slug: command-parity
status: draft
nyquist_compliant: false
wave_0_complete: true
created: 2026-04-02
---

# Phase 7 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter 5 (spring-boot-starter-test) |
| **Config file** | `backend/game-app/build.gradle.kts` |
| **Quick run command** | `cd backend && ./gradlew :game-app:test --tests "com.opensam.command.*" -x :gateway-app:test` |
| **Full suite command** | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |
| **Estimated runtime** | ~45 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd backend && ./gradlew :game-app:test --tests "com.opensam.command.*" -x :gateway-app:test`
- **After every plan wave:** Run `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 45 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 07-01-01 | 01 | 1 | CMD-01 | unit | `./gradlew :game-app:test --tests "com.opensam.command.GeneralCivilCommandTest"` | ✅ | ⬜ pending |
| 07-01-02 | 01 | 1 | CMD-01, CMD-03, CMD-04 | unit | `./gradlew :game-app:test --tests "com.opensam.command.GeneralMilitaryCommandTest"` | ✅ | ⬜ pending |
| 07-01-03 | 01 | 1 | CMD-01, CMD-03, CMD-04 | unit | `./gradlew :game-app:test --tests "com.opensam.command.GeneralPoliticalCommandTest"` | ✅ | ⬜ pending |
| 07-02-01 | 02 | 1 | CMD-02 | unit | `./gradlew :game-app:test --tests "com.opensam.command.Nation*"` | ✅ | ⬜ pending |
| 07-02-02 | 02 | 1 | CMD-02, CMD-03, CMD-04 | unit | `./gradlew :game-app:test --tests "com.opensam.command.NationDiplomacyStrategicCommandTest"` | ✅ | ⬜ pending |
| 07-03-01 | 03 | 1 | CMD-01, CMD-02 | unit | `./gradlew :game-app:test --tests "com.opensam.command.CommandParityTest"` | ✅ | ⬜ pending |
| 07-03-02 | 03 | 1 | CMD-03 | unit | `./gradlew :game-app:test --tests "com.opensam.command.Constraint*"` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

*Existing infrastructure covers all phase requirements.* 기존 테스트 파일 7개 + parity 테스트 2개 + constraint 테스트 2개가 이미 존재. 새 테스트 파일 생성 불필요, 기존 파일에 golden value assertion 추가만 필요.

---

## Manual-Only Verifications

*All phase behaviors have automated verification.* 로그 메시지 정확 매칭과 entity mutation 검증 모두 자동화된 단위 테스트로 검증 가능.

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [ ] Feedback latency < 45s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
