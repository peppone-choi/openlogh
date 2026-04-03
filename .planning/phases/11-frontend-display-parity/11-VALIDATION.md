---
phase: 11
slug: frontend-display-parity
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 3.2.4 |
| **Config file** | `frontend/vitest.config.ts` |
| **Quick run command** | `cd frontend && npx vitest run --reporter=verbose` |
| **Full suite command** | `cd frontend && npx vitest run` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontend && npx vitest run --reporter=verbose`
- **After every plan wave:** Run `cd frontend && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | FE-01 | audit | Manual field comparison | N/A | ⬜ pending |
| 11-01-02 | 01 | 1 | FE-02 | audit | Manual field comparison | N/A | ⬜ pending |
| 11-01-03 | 01 | 1 | FE-03 | audit | Manual field comparison | N/A | ⬜ pending |
| 11-01-04 | 01 | 1 | FE-04 | audit | Manual format analysis | N/A | ⬜ pending |
| 11-02-01 | 02 | 1 | FE-01 | unit | `cd frontend && npx vitest run src/components/game/__tests__/game-dashboard.test.tsx` | ❌ W0 | ⬜ pending |
| 11-02-02 | 02 | 1 | FE-02 | unit | `cd frontend && npx vitest run src/components/game/__tests__/general-basic-card.test.tsx` | ❌ W0 | ⬜ pending |
| 11-02-03 | 02 | 1 | FE-03 | unit | `cd frontend && npx vitest run src/components/game/__tests__/nation-basic-card.test.tsx` | ❌ W0 | ⬜ pending |
| 11-02-04 | 02 | 2 | FE-04 | unit | `cd frontend && npx vitest run src/lib/formatBattleLog.test.ts` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `frontend/src/components/game/__tests__/game-dashboard.test.tsx` — covers FE-01 dashboard field completeness
- [ ] `frontend/src/components/game/__tests__/general-basic-card.test.tsx` — covers FE-02 calculated stat accuracy
- [ ] `frontend/src/components/game/__tests__/nation-basic-card.test.tsx` — covers FE-03 aggregated data accuracy
- [ ] `frontend/src/lib/formatBattleLog.test.ts` — covers FE-04 battle log HTML parsing
- [ ] Vitest environment: component tests need `jsdom` — may need `@vitest-environment jsdom` pragma or config update
