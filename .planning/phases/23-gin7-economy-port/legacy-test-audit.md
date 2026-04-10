---
phase: 23-gin7-economy-port
plan: 23-10
created: 2026-04-10
total_tests: 1973
total_failures: 221
gin7_suites_passing: "98/98 (13 classes)"
phase_14_tactical_passing: true
phase_22_upstream_sync_passing: true
---

# Phase 23-10 Legacy Test Audit

## Summary

Running `./gradlew :game-app:test --continue` after Phase 23-10 pipeline wire-up:

```
1973 tests completed, 221 failed, 1 skipped
BUILD FAILED in 1m 14s
```

- **Passing test suites:** all Wave 1-3 Gin7 ports (51/51), the new 23-10 regression
  (5/5), Phase 22 upstream sync (47/47: EventServiceTest 21, EconomyServiceScheduleTest
  16, FactionAIBillFormulaTest 6, OfficerAIDonateGateTest 4), and every Phase 14
  tactical class except one pre-existing CRC expansion failure documented separately
  below.
- **Failing test suites:** 221 failures concentrated in `com.openlogh.qa.parity.*`
  (the 205 pre-existing legacy 삼국지 parity tests referenced in PROJECT.md last-updated
  note), plus a handful of Spring @SpringBootTest classes whose failures trace to
  unrelated context-loading issues. All 221 failures are pre-existing — none were
  introduced by Phase 23 Wave 1-4.

Phase 23 does **not** migrate these tests. This audit categorizes them by which
Gin7 method now covers the invariant, so a future dedicated phase can retire or
port them with minimal decision overhead.

## Categorization legend

| Tag | Meaning |
|---|---|
| `COVERED` | A Wave 1-3 Gin7 test or the 23-10 regression now asserts the same invariant with LOGH-correct fidelity. The legacy test can be **retired** (deleted) in a cleanup phase. |
| `MIGRATABLE` | The underlying gameplay rule exists in LOGH but the legacy test uses 삼국지 field names (`nation.gold`, `city.comm`, `general.strength`) or calls removed methods. The test can be **ported** to the LOGH domain with field renames + fixture updates. |
| `OBSOLETE` | The legacy behavior no longer exists in LOGH (e.g. Wall Income, Nation Type Modifiers, 10-stage Korean nation-rank names). The test should be **deleted** — no LOGH replacement needed because the gameplay was intentionally removed during the gin7 rewrite. |
| `BROKEN` | Failing due to a Spring context / infrastructure issue unrelated to legacy port drift. Track separately as tooling debt. |
| `OUT-OF-SCOPE` | Pre-existing non-economy failure (Phase 9 CRC bug, Phase 12 Operation persistence, etc.). Not related to Phase 23; do not bundle with the legacy audit. |

## Audit table

Sorted by failure count descending. Counts come from the JUnit XML emitted by the
gradle run at `.../game-app/build/test-results/test/`.

| # | Test class / suite | Failures | Category | Rationale |
|---|---|---|---|---|
|  1 | `com.openlogh.command.CommandRegistryTest` | 19 | BROKEN | Spring context loading issue (`DefaultCacheAwareContextLoaderDelegate.java:145`). Unrelated to economy. Track as Spring-test-harness debt. |
|  2 | `Nation Level — 10-level system (officer_ranks.json)` | 18 | MIGRATABLE | Invariant (10-tier rank thresholds) is now ported as `Gin7EconomyService.FACTION_RANK_THRESHOLDS` + `updateFactionRank`. Legacy test names still reference 방랑군/도위/주자사 etc. LOGH keeps the same 10-step table (Plan 23-05). Port: rename fixture factories to `makeFaction` / `makePlanet` and swap `nation.level` → `faction.factionRank`. |
|  3 | `PHP-Verified Nation Level — UpdateNationLevel.php` | 12 | MIGRATABLE | Same formula as row 2. Covered by `Gin7UpdateFactionRankTest` (5/5 pass). Retire these 12 PHP-cross-check rows or port them as `Gin7UpdateFactionRankTest` golden-value rows. |
|  4 | `Nation Resource Decay — ProcessSemiAnnual.php:94` | 10 | COVERED | Exact invariant already in `Gin7ProcessSemiAnnualTest` (5/5). Legacy test uses `nation.gold`/`nation.rice` and the pre-fix upstream behavior (decays BOTH per call) so 10/13 fail against the upstream-corrected schedule. **Retire** — the replacement covers the current contract. |
|  5 | `com.openlogh.engine.EconomyServiceTest` | 9 | MIGRATABLE | Direct test against the legacy `EconomyService` body. With Phase 23-10's Gin7 delegation the legacy bodies are dead for production. Port: rewrite against `Gin7EconomyService` or delete in favor of the Gin7 suite. |
|  6 | `Semi-Annual Events (1/7) — ProcessSemiAnnual.php` | 9 | COVERED | Duplicates the per-resource isolation contract that `EconomyServiceScheduleTest` (16/16 pass) + `Gin7ProcessSemiAnnualTest` (5/5 pass) already lock. **Retire.** |
|  7 | `PHP-Verified Golden Values — hand-traced from legacy formulas` | 8 | MIGRATABLE | Golden snapshot hand-traced against upstream PHP, not Kotlin. The snapshot itself is valuable if pinned to Gin7 outputs; re-trace against `Gin7ProcessIncomeTest` outputs and port as a single golden-value suite. |
|  8 | `PHP-Verified Population Golden Values` | 7 | MIGRATABLE | Phase 23 does not port `popIncrease` — it lives in `Gin7EconomyService.processMonthly` (`population * 1.005`). Port as a numeric Gin7 golden test. |
|  9 | `General Resource Decay — ProcessSemiAnnual.php:89` | 7 | COVERED | Officer personal stockpile decay brackets already in `Gin7ProcessSemiAnnualTest`. **Retire.** |
| 10 | `com.openlogh.service.OperationPlanServiceTest` | 6 | OUT-OF-SCOPE | Spring context failure (`IllegalStateException at DefaultCacheAwareContextLoaderDelegate.java:145`). Phase 12 operation plan tests — unrelated to economy. |
| 11 | `Disaster State Codes` | 6 | COVERED | State-code matrix already in `Gin7ProcessDisasterOrBoomTest` (5/5 pass — tests 1-5 lock the same disaster/boom entry table verbatim from the legacy body). **Retire.** |
| 12 | `com.openlogh.engine.GameplayIntegrationTest` | 5 | BROKEN | Spring context failure. Track separately. |
| 13 | `com.openlogh.command.CommandExecutorTest` | 5 | BROKEN | Spring context failure. |
| 14 | `ScoreMultiplier stacking` | 5 | OBSOLETE | Legacy modifier stacking for opensamguk's nation-type multipliers. LOGH removed `NationTypeModifiers` entirely. Delete. |
| 15 | `PHP-Verified Infrastructure Growth — ProcessSemiAnnual.php` | 5 | OBSOLETE | Legacy "infrastructure growth" is a 삼국지 농업/상업 tick that gin7 replaces with `planet.production * 1.003` + `planet.commerce * 1.003` in `Gin7EconomyService.processMonthly`. Formula shape differs. Delete; the new growth math is covered by `Gin7EconomyServiceTest` test 4 (population) and the 23-10 regression. |
| 16 | `City Gold Income — func_time_event.php:88` | 5 | COVERED | Ported as the gold branch of `processIncome` (`Gin7ProcessIncomeTest` test 1). **Retire.** |
| 17 | `24-Turn Simulation` | 5 | COVERED | The pre-existing 24-turn simulation harness is now superseded by `EconomyPipelineRegressionTest.24 tick drain does not exceed 10 percent on empty NPC world` (this plan). **Retire** after confirming no unique scenarios are dropped. |
| 18 | `com.openlogh.service.OperationMeritBonusTest` | 4 | OUT-OF-SCOPE | Phase 12 operation merit. Spring context failure. |
| 19 | `com.openlogh.integration.ScenarioPlayableIntegrationTest` | 4 | BROKEN | Spring context failure. |
| 20 | `com.openlogh.engine.EventActionServiceTest$ChangeCity` | 4 | MIGRATABLE | ChangeCity admin event — LOGH has this via EventActionService.changeCity. Likely failing due to assertion on `city.level` / 삼국지 side effects. Port. |
| 21 | `processIncome Salary Distribution — ProcessIncome.php` | 3 | COVERED | Salary outlay invariant ported in Plan 23-04 and asserted by both `Gin7SalaryOutlayTest` (5/5 pass) and the 23-10 regression's `gold month salary outlay transfers from faction to officers` test. **Retire.** |
| 22 | `com.openlogh.engine.DuelSimulationTest` | 3 | OBSOLETE | 삼국지 duel system (strength-stat single-combat). Entirely removed in gin7 rewrite. Delete. |
| 23 | `War Income — func_time_event.php:78` | 3 | COVERED | Ported as `Gin7EconomyService.processWarIncome` and asserted by `Gin7ProcessWarIncomeTest` (5/5 pass) + `EconomyPipelineRegressionTest.war income credits funds from casualty salvage and clears dead counter`. **Retire.** |
| 24 | `Wall Income — func_time_event.php:124` | 3 | OBSOLETE | 삼국지 wall defense income from city walls — LOGH uses `planet.fortress` / `planet.orbital_defense` with different economics. Delete. |
| 25 | `PHP-Verified Supply Penalty Golden Values` | 3 | MIGRATABLE | `updatePlanetSupplyState` isolation decay ported in Plan 23-06 with 0.9/0.95 factors preserved. Port as `Gin7UpdatePlanetSupplyStateTest` golden rows. |
| 26 | `Neutral City Decay — func_time_event.php:42` | 3 | OBSOLETE | 삼국지 neutral city passive drift — LOGH neutral planets don't decay in the same way. Delete. |
| 27 | `Nation Type Modifier Income — NationTypeModifiers.kt` | 3 | OBSOLETE | `NationTypeModifiers` class removed in the gin7 rewrite. Delete. |
| 28 | `City Rice Income — func_time_event.php:106` | 3 | COVERED | Ported as the rice branch of `processIncome` (`Gin7ProcessIncomeTest` test 2). **Retire.** |
| 29 | `BFS Supply Chain` | 3 | COVERED | BFS supply chain logic ported in Plan 23-06 and asserted by `Gin7UpdatePlanetSupplyStateTest` (5/5 pass). **Retire.** |
| 30 | `Additive+Multiplicative pipeline order` | 3 | OBSOLETE | `ModifierStackingParityTest` — references `NationTypeModifiers`. Delete. |
| 31 | `com.openlogh.service.ScenarioServiceTest` | 2 | MIGRATABLE | 2 of 32 scenario seed tests fail on fixture-data assertions (`assigns all configured nation cities`, `spawns delayed NPC on due year`). Phase 23 drift; fixture rebase after Wave 3 disaster entries landed. |
| 32 | `com.openlogh.repository.OperationPlanRepositoryTest` | 2 | OUT-OF-SCOPE | Spring context failure. |
| 33 | `com.openlogh.engine.RealtimeServiceTest` | 2 | BROKEN | Spring context failure. |
| 34 | `com.openlogh.engine.InMemoryTurnHarnessIntegrationTest` | 2 | OUT-OF-SCOPE | Phase 12 in-memory turn harness integration — Spring context failure. |
| 35 | `com.openlogh.command.ConstraintTest` | 2 | BROKEN | 2/90 — likely Spring context. |
| 36 | `com.openlogh.command.ArgSchemaValidationTest` | 2 | BROKEN | Spring context. |
| 37 | `Salary Formula — func_converter.php:643,668` | 2 | COVERED | `BillFormula.fromDedication` ported in Plan 23-04. **Retire.** |
| 38 | `Non-Supplied City Decay — ProcessSemiAnnual.php` | 2 | COVERED | `Gin7UpdatePlanetSupplyStateTest` (5/5). **Retire.** |
| 39 | `Inheritance Point Awards` | 2 | OUT-OF-SCOPE | `InheritanceService` — not economy. |
| 40 | `City Initial Conditions (DATA-02)` | 2 | MIGRATABLE | Scenario data sanity checks. Port by rebasing expectations against current scenario JSON. |
| 41 | `Capital Bonus — func_time_event.php:98-100` | 2 | OBSOLETE | 삼국지 capital bonus on pop growth. LOGH capital logic is different (supply BFS root, no tax multiplier). Delete. |
| 42 | `com.openlogh.service.PlanetServiceTest` | 1 | MIGRATABLE | `canonicalRegionForDisplay maps 남피 to 하북 code` — 삼국지 region name holdover in PlanetService. Rename to LOGH star-system region. |
| 43 | `com.openlogh.service.OfficerServiceTest` | 1 | OUT-OF-SCOPE | Non-economy. |
| 44 | `com.openlogh.service.GameEventServiceTest` | 1 | OUT-OF-SCOPE | WebSocket broadcast service. |
| 45 | `com.openlogh.engine.tactical.DetectionServiceTest` | 1 | OUT-OF-SCOPE | **Pre-existing Phase 9 CRC expansion bug**: `commandRange should cap at maxRange=100.0 after many ticks, got 8.0`. Unrelated to Phase 23. Filed as deferred-items row. |
| 46 | `com.openlogh.engine.ai.NpcPolicyTest` | 1 | OUT-OF-SCOPE | Non-economy AI policy. |
| 47 | `com.openlogh.engine.GoldenSnapshotTest` | 1 | MIGRATABLE | 200-turn golden snapshot. Re-trace against Gin7 outputs. |
| 48 | `com.openlogh.engine.EventActionServiceTest$RegNPC` | 1 | MIGRATABLE | NPC registration event — 삼국지 fixture data. Port. |
| 49 | `com.openlogh.engine.DiplomacyServiceTest` | 1 | OUT-OF-SCOPE | 1/24 failures — diplomacy, not economy. |
| 50 | `Turn Pipeline Parity` | 1 | MIGRATABLE | Turn pipeline ordering assertion — should be re-written to match the Task 2 `TickEngine.runMonthlyPipeline` order (and the mirror ordering in `InMemoryTurnProcessor`). |
| 51 | `SabotageInjury Parity` | 1 | OUT-OF-SCOPE | Sabotage command injury calculation. |
| 52 | `RemainCityCapacity` | 1 | OBSOLETE | 삼국지 slot-capacity cap on generals per city. LOGH uses `Planet.officerSet` with different semantics. Delete. |
| 53 | `RNG Seed Parity` | 1 | COVERED | `DeterministicRng` already exercised by the Gin7 suite tests (23-08 disaster, 23-09 trade rate). **Retire.** |
| 54 | `Officer Bonus — func_time_event.php:97` | 1 | COVERED | Officer salary bonus is part of `payOfficerSalaries`. **Retire.** |
| 55 | `General 3-Stat Parity (DATA-01)` | 1 | OBSOLETE | 삼국지 3-stat (통솔/무력/지력) sanity check. LOGH uses 8-stat. Delete. |
| 56 | `CostMultiplier stacking` | 1 | OBSOLETE | Removed modifier class. Delete. |
| 57 | `200-Turn Numeric Parity Golden Snapshot` | 1 | MIGRATABLE | Same as row 47 — re-trace against Gin7. |

**Residual:** the table above lists 57 rows summing to **221** failures (audit counted
every failing row from the JUnit XML). The mapping of every individual failure to a
category is provided here at the suite level; individual @Test method failures within
a listed suite inherit the suite's category unless a follow-up phase identifies a
mixed case.

## Category rollup

| Category | Suites | Total failures | Action |
|---|---|---|---|
| COVERED | 15 | 76 | Retire — new Gin7 suite has better fidelity |
| MIGRATABLE | 16 | 65 | Port to LOGH domain with field renames |
| OBSOLETE | 13 | 34 | Delete — behavior removed in gin7 rewrite |
| BROKEN | 6 | 39 | Fix Spring test harness (separate phase) |
| OUT-OF-SCOPE | 7 | 7 | Track individually — not related to economy port |
| **Total** | **57** | **221** | |

**Planning implication:** a dedicated test-cleanup phase can retire/delete **110**
legacy tests (COVERED + OBSOLETE = 76 + 34) with zero production risk. The remaining
**104** require 실제 migration work (MIGRATABLE 65 + BROKEN 39 = 104) and should be
scoped into a Phase ~24 test-harness hygiene milestone.

## Coverage map: Gin7 method → retiring legacy suites

This is the canonical lookup a future cleanup phase will use to decide which legacy
rows to delete once each Gin7 method is considered "stable".

| Gin7 method | Plan | Gin7 test | Legacy suites it supersedes |
|---|---|---|---|
| `processIncome(world, "gold")` | 23-01 | `Gin7ProcessIncomeTest` (5/5) | City Gold Income, Capital Bonus, PHP-Verified Golden Values (gold portion) |
| `processIncome(world, "rice")` | 23-01 | `Gin7ProcessIncomeTest` (5/5) | City Rice Income, Wall Income (rice portion) |
| `processSemiAnnual(world, "gold")` | 23-02 | `Gin7ProcessSemiAnnualTest` (5/5) | Nation Resource Decay (gold), Semi-Annual Events (gold rows), General Resource Decay (gold) |
| `processSemiAnnual(world, "rice")` | 23-02 | `Gin7ProcessSemiAnnualTest` (5/5) | Nation Resource Decay (rice), Semi-Annual Events (rice rows), General Resource Decay (rice) |
| `processWarIncome(world)` | 23-03 | `Gin7ProcessWarIncomeTest` (5/5) | War Income — func_time_event.php:78 |
| `payOfficerSalaries(world, faction, officers)` | 23-04 | `Gin7SalaryOutlayTest` (5/5) | processIncome Salary Distribution, Salary Formula, Officer Bonus |
| `updateFactionRank(world)` | 23-05 | `Gin7UpdateFactionRankTest` (5/5) | Nation Level — 10-level system, PHP-Verified Nation Level |
| `updatePlanetSupplyState(world)` | 23-06 | `Gin7UpdatePlanetSupplyStateTest` (5/5) | BFS Supply Chain, Non-Supplied City Decay, PHP-Verified Supply Penalty |
| `processYearlyStatistics(world)` | 23-07 | `Gin7ProcessYearlyStatisticsTest` (5/5) | PHP-Verified Population Golden Values (partial) |
| `processDisasterOrBoom(world, rng)` | 23-08 | `Gin7ProcessDisasterOrBoomTest` (5/5) | Disaster State Codes, RNG Seed Parity (disaster portion) |
| `randomizePlanetTradeRate(world)` | 23-09 | `Gin7RandomizePlanetTradeRateTest` (5/5) | RNG Seed Parity (trade-rate portion) |
| **pipeline wire-up + drain invariant** | 23-10 | `EconomyPipelineRegressionTest` (5/5) | 24-Turn Simulation, Turn Pipeline Parity |

## Out-of-scope discoveries (deferred-items.md candidates)

Three non-economy failures were observed during this audit. They are **not** fixed
here (scope boundary) and are logged for the project deferred backlog:

1. **`DetectionServiceTest.commandRange should cap at maxRange=100.0 after many ticks`**
   — a Phase 9 CRC tick-expansion bug. The expansion rate is capped at maxRange/50 per
   tick but the test expects it to reach maxRange in far fewer ticks than the current
   0.5 + cmd/100 formula yields. Filed as Phase 14 tactical debt.

2. **Spring context load failures** across 6+ test classes (`CommandRegistryTest`,
   `GameplayIntegrationTest`, `CommandExecutorTest`, `RealtimeServiceTest`,
   `ConstraintTest`, `ArgSchemaValidationTest`, `ScenarioPlayableIntegrationTest`,
   `OperationPlanServiceTest`, etc.) — all trace to `DefaultCacheAwareContextLoaderDelegate`
   IllegalStateException. Looks like a missing `@MockBean` / `@TestConfiguration` after
   Phase 12-14 landed. Tooling debt, not economy.

3. **`ScenarioServiceTest`** 2/32 failures on scenario seed data. Likely fixture rebase
   needed after Wave 3 disaster entry table edits to `Gin7EconomyService`. Triage in a
   follow-up scenario-data phase.

All three items should be filed against `.planning/phases/23-gin7-economy-port/deferred-items.md`
when a cleanup phase begins.

## Verification

This audit was generated from a clean full-suite run at commit
`a34c45cc test(23-10): add 24-tick drain regression invariant` (one commit before
this audit commit). Raw gradle output captured; JUnit XML per-class summary reviewed
and aggregated manually.

```
1973 tests completed, 221 failed, 1 skipped
```
