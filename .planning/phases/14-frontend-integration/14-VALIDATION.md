---
phase: 14
slug: frontend-integration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-09
---

# Phase 14 — Validation Strategy

> Per-phase validation contract for FE-01..FE-05 during execution. Draws from the `## Validation Architecture` section of `14-RESEARCH.md`.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 3.2.4 (unit/component) + Playwright 1.58.2 (E2E) + JUnit 5 (Kotlin backend) |
| **Config file** | `frontend/vitest.config.ts`, `frontend/playwright.config.ts`, `backend/build.gradle.kts` |
| **Quick run command** | `cd frontend && pnpm test --run` |
| **Full suite command** | `cd frontend && pnpm test --run && pnpm e2e && cd ../backend && ./gradlew test` |
| **Estimated runtime** | ~180 seconds (frontend ~60s, backend ~100s, E2E ~20s) |

---

## Sampling Rate

- **After every task commit:** Run `pnpm test --run <changed-file>.test.tsx` (targeted)
- **After every plan wave:** Run `pnpm test --run` (full frontend unit suite)
- **Before `/gsd:verify-work`:** Full suite (frontend unit + E2E + backend) must be green; `pnpm typecheck && pnpm build` must succeed; `pnpm verify:parity` must pass
- **Max feedback latency:** 60 seconds (targeted unit run)

---

## Per-Task Verification Map

> Populated by the planner once PLAN.md files exist. Rows enumerated here map to the research `## Validation Architecture` tables.

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 14-XX-01 | DTO ext | 1 | FE-01 | unit (Kotlin) | `./gradlew :game-app:test --tests "*TacticalBattleDtoTest*"` | ❌ W0 | ⬜ pending |
| 14-XX-02 | DTO ext | 1 | FE-01 | unit (Kotlin) | `./gradlew :game-app:test --tests "*CommandHierarchyDtoTest*"` | ❌ W0 | ⬜ pending |
| 14-XX-03 | CRC rewrite | 2 | FE-01 | unit (Vitest) | `pnpm test --run CommandRangeCircle` | ❌ W0 | ⬜ pending |
| 14-XX-04 | CRC rewrite | 2 | FE-01 | unit (Vitest) | `pnpm test --run battleMap-crc` | ❌ W0 | ⬜ pending |
| 14-XX-05 | CRC rewrite | 2 | FE-01 | e2e (Playwright) | `pnpm exec playwright test tactical-crc.spec.ts` | ❌ W0 | ⬜ pending |
| 14-XX-06 | R3F removal | 1 | FE-01 | smoke | `pnpm typecheck && pnpm build` | ✅ | ⬜ pending |
| 14-XX-07 | R3F removal | 1 | FE-01 | regression | `pnpm test --run no-r3f-imports` | ❌ W0 | ⬜ pending |
| 14-XX-08 | Drawer | 3 | FE-02 | unit (Vitest) | `pnpm test --run subFleetAssignmentDrawer` | ❌ W0 | ⬜ pending |
| 14-XX-09 | Drawer | 3 | FE-02 | unit (Vitest) | `pnpm test --run dragGating` | ❌ W0 | ⬜ pending |
| 14-XX-10 | Drawer | 3 | FE-02 | e2e (Playwright) | `pnpm exec playwright test sub-fleet-drawer.spec.ts` | ❌ W0 | ⬜ pending |
| 14-XX-11 | Gating | 3 | FE-03 | unit (Vitest) | `pnpm test --run canCommandUnit` | ❌ W0 | ⬜ pending |
| 14-XX-12 | Gating | 3 | FE-03 | unit (Vitest) | `pnpm test --run command-execution-panel` | ❌ W0 | ⬜ pending |
| 14-XX-13 | Gating | 3 | FE-03 | unit (Vitest) | `pnpm test --run proposal-shift-click` | ❌ W0 | ⬜ pending |
| 14-XX-14 | Gating | 3 | FE-03 | e2e (Playwright) | `pnpm exec playwright test gating.spec.ts` | ❌ W0 | ⬜ pending |
| 14-XX-15 | Succession FX | 4 | FE-04 | unit (Vitest) | `pnpm test --run successionReducer` | ❌ W0 | ⬜ pending |
| 14-XX-16 | Succession FX | 4 | FE-04 | unit (Vitest) | `pnpm test --run flagshipFlash` | ❌ W0 | ⬜ pending |
| 14-XX-17 | Succession FX | 4 | FE-04 | unit (Vitest) | `pnpm test --run toastSuccession` | ❌ W0 | ⬜ pending |
| 14-XX-18 | Succession FX | 4 | FE-04 | e2e (Playwright) | `pnpm exec playwright test succession.spec.ts` | ❌ W0 | ⬜ pending |
| 14-XX-19 | Fog | 4 | FE-05 | unit (Vitest) | `pnpm test --run fogReducer` | ❌ W0 | ⬜ pending |
| 14-XX-20 | Fog | 4 | FE-05 | unit (Vitest) | `pnpm test --run hierarchyVision` | ❌ W0 | ⬜ pending |
| 14-XX-21 | Fog | 4 | FE-05 | unit (Vitest) | `pnpm test --run fogLayer` | ❌ W0 | ⬜ pending |
| 14-XX-22 | Fog | 4 | FE-05 | e2e (Playwright) | `pnpm exec playwright test fog.spec.ts` | ❌ W0 | ⬜ pending |
| 14-XX-23 | Ops UI | 5 | FE-03 | unit (Vitest) | `pnpm test --run operations-overlay` | ❌ W0 | ⬜ pending |
| 14-XX-24 | Battle end | 5 | FE-01 | unit (Vitest) | `pnpm test --run battle-end-modal` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

*Note: Specific Task IDs (14-01-01 etc.) to be reassigned by the planner once PLAN.md files exist. The row count and coverage targets are authoritative.*

---

## Wave 0 Requirements

Wave 0 scaffolds all test files before implementation tasks so that every requirement has a failing-then-passing test to drive development.

### Frontend (Vitest — colocated)
- [ ] `frontend/src/components/tactical/CommandRangeCircle.test.tsx` — stub for FE-01 (CRC props + no-animation assertion)
- [ ] `frontend/src/components/tactical/BattleMap.test.tsx` — stub for FE-01 (multi-CRC render counts from hierarchy)
- [ ] `frontend/src/stores/tacticalStore.fog.test.ts` — stub for FE-05 (fog reducer)
- [ ] `frontend/src/stores/tacticalStore.succession.test.ts` — stub for FE-04 (succession event reducer)
- [ ] `frontend/src/stores/tacticalStore.hierarchy.test.ts` — stub for FE-03 (hierarchy + gating source of truth)
- [ ] `frontend/src/lib/canCommandUnit.test.ts` — stub for FE-03 (pure gating function)
- [ ] `frontend/src/components/game/command-execution-panel.gating.test.tsx` — stub for FE-03 (disabled + tooltip)
- [ ] `frontend/src/components/game/command-execution-panel.proposal.test.tsx` — stub for FE-03 (Shift+click → createProposal)
- [ ] `frontend/src/components/tactical/SubFleetAssignmentDrawer.test.tsx` — stub for FE-02 (dnd-kit headless)
- [ ] `frontend/src/components/tactical/SubFleetAssignmentDrawer.gating.test.tsx` — stub for FE-02 (PREPARING vs ACTIVE)
- [ ] `frontend/src/components/tactical/FlagshipFlash.test.tsx` — stub for FE-04 (FX mount/unmount)
- [ ] `frontend/src/components/tactical/FogLayer.test.tsx` — stub for FE-05 (ghost dash + opacity)
- [ ] `frontend/src/components/tactical/BattleEndModal.test.tsx` — stub for FE-01/02 summary
- [ ] `frontend/src/components/game/OperationsOverlay.test.tsx` — stub for F1 toggle (D-28)
- [ ] `frontend/src/__tests__/no-r3f-imports.test.ts` — regression: grep `frontend/src` for `@react-three` after removal, expect zero

### Frontend (Playwright — `frontend/e2e/`)
- [ ] `frontend/e2e/tactical-crc.spec.ts` — FE-01
- [ ] `frontend/e2e/sub-fleet-drawer.spec.ts` — FE-02
- [ ] `frontend/e2e/gating.spec.ts` — FE-03
- [ ] `frontend/e2e/succession.spec.ts` — FE-04
- [ ] `frontend/e2e/fog.spec.ts` — FE-05

### Backend (JUnit 5 — `backend/game-app/src/test/kotlin/`)
- [ ] `TacticalBattleDtoExtensionTest.kt` — asserts `attackerHierarchy`, `sensorRange`, `isOnline`, `isNpc`, `missionObjective` on DTOs
- [ ] `CommandHierarchyDtoMappingTest.kt` — asserts `toDto` includes sub-fleet commanders and succession queue
- [ ] `OperationBroadcastTest.kt` — asserts `/topic/world/{sessionId}/operations` fires on status transition
- [ ] `BattleSummaryEndpointTest.kt` — asserts `/api/.../battles/{id}/summary` returns per-unit merit breakdown
- [ ] `SensorRangeComputationTest.kt` — asserts sensorRange formula matches D-19 derivation

### Shared infrastructure
- [ ] Confirm `@dnd-kit/core`, `@dnd-kit/utilities` added to `frontend/package.json`
- [ ] Confirm `@react-three/fiber`, `@react-three/drei`, `three`, `@types/three` REMOVED from `frontend/package.json`
- [ ] `frontend/src/test/fixtures/tacticalBattleFixture.ts` — seed TacticalBattle with configurable hierarchy for unit tests

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Visual palette fidelity (진영색 + 호버/선택 hue 변종) | FE-01 (D-02) | Subjective color perception | 로그인 → 전술전 진입 → 각 지휘관 CRC가 진영색으로 표시; 유닛 hover 시 hue shift 확인 |
| CRC 실시간 축소 체감 | FE-01 (D-03) | Animation smoothness at 1 Hz tick | 명령 발령 직후 CRC 반경 축소 → 재확장; jank 없는지 육안 확인 |
| Drawer 드래그 체감 | FE-02 (D-05, D-07) | Pointer latency + touch support | 60유닛 → 부사령관 드래그; 모바일 viewport(<768px)에서 드로어 bottom sheet 확인 |
| Fog 고스트 UX 긴장감 | FE-05 (D-17) | Subjective UX ("저기 있었는데 어디 갔지?") | 적 유닛이 sensor 범위 밖으로 이동 → 고스트가 마지막 위치에 머무르는지 확인 |
| 기함 플래시 임팩트 | FE-04 (D-14) | 시각 효과 강도 판단 | SUCCESSION_STARTED 이벤트 직후 해당 유닛 위치에서 0.5초 ring flash 확인 |
| Sonner 토스트 가독성 (Korean particle) | FE-04 (D-13) | Korean UI review | 다양한 officerName (받침 유무) 으로 토스트 텍스트 자연스러운지 확인 |
| 작전 오버레이 F1 토글 | D-28 | Keyboard hotkey UX | 은하맵 진입 → F1 → 오버레이 ON 확인 → F1 → OFF; 브라우저 F1 도움말 뜨지 않는지 확인 |
| 한국어 텍스트 일관성 | 전반 | CLAUDE.md 규칙 | 모든 신규 UI 텍스트 한국어, 삼국지 용어 미사용 확인 |
| 성능 (FPS ≥ 50, 60유닛 + CRC + 고스트) | 전반 | 실기 디바이스 성능 | Chrome DevTools Performance 탭으로 60유닛 + 8 CRC 시나리오에서 FPS ≥ 50 확인 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (15 Vitest stubs + 5 Playwright stubs + 5 JUnit stubs)
- [ ] No watch-mode flags (`--watch` forbidden in CI sampling)
- [ ] Feedback latency < 60s (targeted unit run)
- [ ] `nyquist_compliant: true` set in frontmatter after planner confirms task-to-test mapping
- [ ] `pnpm verify:parity` green — confirms FE parity script still passes after UI rewrites
- [ ] `verify-type-parity` skill green — confirms Kotlin ↔ TypeScript DTO alignment
- [ ] `verify-api-parity` skill green — confirms every new controller/channel has a FE consumer

**Approval:** pending
