---
phase: 11
slug: frontend-display-parity
status: draft
nyquist_compliant: true
wave_0_complete: false
created: 2026-04-03
updated: 2026-04-03
---

# Phase 11 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest 3.2.4 |
| **Config file** | `frontend/vitest.config.ts` |
| **Quick run command** | `cd frontend && npx vitest run -x --reporter=verbose` |
| **Full suite command** | `cd frontend && npx vitest run` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `cd frontend && npx vitest run -x --reporter=verbose`
- **After every plan wave:** Run `cd frontend && npx vitest run`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 11-01-01 | 01 | 1 | FE-01 | audit | `grep -c "^|" 11-AUDIT.md` | N/A | pending |
| 11-01-02 | 01 | 1 | FE-04 | audit | `grep -c "Section 7" 11-AUDIT.md` | N/A | pending |
| 11-02-01 | 02 | 2 | FE-01 | unit | `cd frontend && npx vitest run src/components/game/game-dashboard.test.tsx -x` | W0 | pending |
| 11-02-02 | 02 | 2 | FE-02 | unit+render | `cd frontend && npx vitest run src/components/game/general-basic-card.test.tsx -x` | W0 | pending |
| 11-02-03 | 02 | 2 | FE-03 | unit | `cd frontend && npx vitest run src/components/game/nation-basic-card.test.tsx -x` | W0 | pending |
| 11-02-04 | 02 | 2 | FE-04 | unit | `cd frontend && npx vitest run src/lib/formatBattleLog.test.ts src/components/game/battle-log-entry.test.tsx -x` | W0 | pending |

*Status: pending / green / red / flaky*

*Test paths follow CLAUDE.md convention: co-located `.test.ts`/`.test.tsx` suffix, NOT `__tests__/` subdirectory.*

---

## Wave 0 Requirements

- [ ] `frontend/src/components/game/game-dashboard.test.tsx` — covers FE-01 dashboard field completeness (source scan, `@vitest-environment node`)
- [ ] `frontend/src/components/game/general-basic-card.test.tsx` — covers FE-02 calculated stat accuracy (jsdom rendering + source scan, `@vitest-environment jsdom`)
- [ ] `frontend/src/components/game/nation-basic-card.test.tsx` — covers FE-03 tech level grade (source scan, `@vitest-environment node`)
- [ ] `frontend/src/lib/formatBattleLog.test.ts` — covers FE-04 battle log HTML parsing (unit, `@vitest-environment node`)
- [ ] `frontend/src/components/game/battle-log-entry.test.tsx` — covers FE-04 component + record-zone wiring (source scan, `@vitest-environment node`)

## D-03 Compliance Note

Per D-03 ("mock data로 렌더링 검증"), `general-basic-card.test.tsx` uses `@vitest-environment jsdom` to mount the component with mock `GeneralFrontInfo` data and asserts that:
- `calcInjury(80, 25)` result (60) appears in rendered output
- `lbonus` value appears as "+N" in rendered output
- Next execute time appears as "N분 남음" in rendered output
- Age has colored styling based on retirement proximity

Source-scan tests remain as supplemental structural checks in the same file.
