---
phase: 23-gin7-economy-port
milestone: v2.3
created: 2026-04-10
requirement_ids: [EC-01, EC-02, EC-03, EC-04, EC-05, EC-06, EC-07, EC-08, EC-09, EC-10]
---

# Phase 23: Gin7 Economy Pipeline Full Port

## Background

Phase 22 established the `processIncomeEvent(world, resource)` / `processSemiAnnualEvent(world, resource)` / `processWarIncomeEvent` / `updateNationLevelEvent` API contract in legacy `EconomyService.kt` as structural guards (see Phase 22-03 SUMMARY). However, all four remain **no-op stubs** with `TODO Phase 4` markers.

`Gin7EconomyService.kt` is currently 96 lines and only handles tax collection (month 1/4/7/10) + approval adjustment + planet resource growth. It lacks salary outlay, semi-annual decay, war income, faction rank updates, yearly statistics, disasters/booms, and trade rate randomization.

Phase 23 ports the full economy pipeline from legacy `EconomyService.kt` (640 lines, 18 public methods) into `Gin7EconomyService.kt`, routes the stubs to Gin7, wires the TickEngine pipeline, and activates the 24-tick drain invariant test that Phase 22-03 deferred.

## Scope boundary

**In scope:**
- Port 9 legacy economy methods to Gin7EconomyService (income, semi-annual, war income, salary outlay, faction rank, yearly statistics, disasters/booms, trade rate, planet supply state — the last is already in legacy, move to Gin7)
- Wire `EconomyService` stubs → `Gin7EconomyService` routing
- Schedule per-resource events (month 1 = funds, month 7 = supplies)
- Activate 24-tick empty-NPC-world drain invariant test
- **Audit** (not migrate) the 205 pre-existing legacy 삼국지 parity test failures — categorize by which Gin7 method now covers them and flag which tests can be retired vs. migrated

**Out of scope:**
- Full migration of all 205 legacy tests (deferred — Phase 23 will log a categorized backlog)
- New economy features beyond parity with upstream opensamguk's legacy bodies
- Balance tuning (parity with legacy constants is the goal)

## Plan breakdown

| # | Plan | Wave | Objective |
|---|------|------|-----------|
| 23-01 | Gin7.processIncome per-resource | 1 | Port legacy income body into Gin7; split by resource literal |
| 23-02 | Gin7.processSemiAnnual per-resource | 1 | Port legacy semi-annual decay body; per-resource |
| 23-03 | Gin7.processWarIncome | 1 | Port war income body (factions in war_state > 0) |
| 23-04 | Gin7 salary outlay + FactionAI integration | 2 | Port faction→officer salary payment using FactionAI bill formula |
| 23-05 | Gin7.updateFactionRank | 2 | Port faction rank recalculation (was updateNationLevelEvent) |
| 23-06 | Gin7.updatePlanetSupplyState | 2 | Move legacy updateCitySupply to Gin7 with LOGH domain mapping |
| 23-07 | Gin7.processYearlyStatistics | 3 | Port annual stats refresh (power/gennum, Jan 1 trigger) |
| 23-08 | Gin7.processDisasterOrBoom | 3 | Port disaster/boom event generation (low probability per month) |
| 23-09 | Gin7.randomizePlanetTradeRate | 3 | Port trade rate randomization (periodic shuffle) |
| 23-10 | Pipeline wire-up + 24-tick regression + legacy test audit | 4 | Route stubs, schedule events, activate invariant test, categorize 205 legacy failures |

## Domain mapping (unchanged from Phase 22)

| Legacy | LOGH |
|---|---|
| Nation | Faction |
| General | Officer |
| City | Planet |
| nation.gold | faction.funds |
| nation.rice | faction.supplies |
| nation.bill | faction.taxRate |
| nation.rateTmp | faction.conscriptionRateTmp |
| general.gold | officer.funds |
| general.rice | officer.supplies |
| general.dedication | officer.dedication |
| putNation/putCity/putGeneral | putFaction/putPlanet/putOfficer |

## Wire format for scheduled events

Per Phase 22-03 decision: resource literals on the wire are `"gold"` / `"rice"` (OpenSamguk convention) and get mapped to `faction.funds` / `faction.supplies` internally. This lets imported legacy event JSON work without translation.

## References

- Upstream commit: `a7a19cc3cd5b3fa5a7c8720484d289fc55845adc` (Phase 22 port)
- LOGH files:
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/EconomyService.kt` (640 lines, source)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt` (96 lines, target)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt:177` `runMonthlyPipeline`
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/steps/EconomyPreUpdateStep.kt` (currently skipped)
  - `backend/game-app/src/main/kotlin/com/openlogh/engine/turn/steps/EconomyPostUpdateStep.kt`
- Legacy upstream PHP (referenced via LOGH KDoc comments):
  - `hwe/func_gamerule.php:189` preUpdateMonthly
  - `hwe/func_gamerule.php:260` postUpdateMonthly
  - `hwe/sammo/Event/Action/ProcessIncome.php`
  - `hwe/sammo/Event/Action/ProcessSemiAnnual.php`
  - `hwe/sammo/Event/Action/ProcessWarIncome.php`
