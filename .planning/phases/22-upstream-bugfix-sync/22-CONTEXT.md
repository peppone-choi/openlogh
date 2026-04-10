---
phase: 22-upstream-bugfix-sync
milestone: v2.2
created: 2026-04-09
requirement_ids: [US-01, US-02, US-03]
---

# Phase 22: Upstream opensamguk bugfix sync

## Background

OpenLOGH is forked from OpenSamguk. After the v2.1 milestone (Phases 8-14) completed, the user requested reflecting recent upstream bug fixes selectively.

Upstream remote: `https://github.com/peppone-choi/opensamguk.git`

## Scope

**ONE upstream commit actually applies to LOGH:**

- **`a7a19cc3`** `fix: NPC 국가/장수 금 증발 버그 수정 (legacy 이벤트 스케줄 준수)`
  - Author: peppone-choi, 2026-04-09
  - Files: EconomyService.kt (258+/-), GeneralAI.kt (16), NationAI.kt (42), ProcessIncomeAction.kt (16), ProcessSemiAnnualAction.kt (16) + 6 test files
  - Root cause: income/salary/upkeep decrement was running every tick instead of on legacy schedule (month 1 = gold, month 7 = rice). Combined with `NationAI.adjustTaxAndBill` using wrong `(generals+cities)*bill` formula and `GeneralAI.doDonate` having unconditional donate branches.

## Excluded upstream commits (analysis)

- **9 voxel/3D terrain commits** — 삼국지 map only, LOGH uses Konva 2D galaxy
- **5 grid battle commits** — 삼국지 legacy tactical, LOGH has own TacticalBattleEngine (Phase 10)
- **3 cosmetic/feat commits** — out of scope per user's "fixes only" rule
- **1 three-stdlib fix** — LOGH removed R3F/three.js in Phase 14-08
- **1 AccountDetailedInfo picture fix** — already present in LOGH

## Confirmed LOGH bug presence

1. **`FactionAI.adjustTaxAndBill:327`** — `val totalBill = (nationGenerals.size + nationCities.size) * nation.taxRate.toInt().coerceAtLeast(0)` — **SAME BUG**
2. **`OfficerAI.doDonate:2375`** — exists, needs the same probability gate on "excess resource" branches
3. **`EconomyService.processIncomeEvent/processSemiAnnualEvent`** — currently stubs pointing to Gin7EconomyService (TODO Phase 4). Upstream's per-resource split pattern should be mirrored when these are wired.

## Domain mapping (upstream → LOGH)

| Upstream field/type | LOGH field/type | Notes |
|---|---|---|
| `com.opensam.*` | `com.openlogh.*` | package rename |
| `Nation` | `Faction` | |
| `General` | `Officer` | |
| `City` | `Planet` | |
| `nation.gold` | `faction.funds` | |
| `nation.rice` | `faction.supplies` | |
| `nation.bill` | `faction.taxRate` | confirmed via LOGH adjustTaxAndBill body |
| `nation.rateTmp` | `faction.conscriptionRateTmp` | confirmed |
| `putNation` | `putFaction` | |
| `general.dedication` | `officer.dedication` | preserved |
| `general.npcState` | `officer.npcState` | preserved |
| `getBill(dedication)` from `hwe/func_converter.php` | — | helper `getBillFromDedication` ported as-is |

## Quantitative impact (upstream measurements)

- 국가 금: 봉급 12x/년 → 1x/년 (금), 1x/년 (쌀)
- 장수 금 유지비 감소: 11.5%/년 → 3%/년 (1만 초과 구간)
- 장수 헌납: 1만 초과 시 100% → 확률 게이트 적용

LOGH verification will include a regression "running an empty NPC world for 24 ticks should not drain NPC faction funds to zero" invariant.

## Plans

- `22-01-PLAN.md` — FactionAI.adjustTaxAndBill legacy-correct bill formula
- `22-02-PLAN.md` — OfficerAI.doDonate probability gates on excess branches
- `22-03-PLAN.md` — EconomyService processIncome/processSemiAnnual per-resource split + ProcessIncomeAction/ProcessSemiAnnualAction resource param
