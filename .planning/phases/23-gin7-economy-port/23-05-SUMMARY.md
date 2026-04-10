---
phase: 23-gin7-economy-port
plan: 23-05
subsystem: economy
tags: [gin7, economy-port, faction-rank, legacy-parity, tdd]
requirements: [EC-05]
dependency-graph:
  requires:
    - Phase 22-03 (per-resource event API contract)
    - Plan 23-06 (Gin7EconomyService 4-arg constructor)
  provides:
    - Gin7EconomyService.updateFactionRank(world)
    - Gin7EconomyService.getFactionRankName(level)
    - Gin7EconomyService.FACTION_RANK_THRESHOLDS
    - Gin7EconomyService.FACTION_RANK_NAME
  affects:
    - Plan 23-10 (UpdateNationLevelAction stub → gin7 wire-up + 【작위】 history log)
tech-stack:
  added: []
  patterns:
    - companion-object for constants (mirrors legacy EconomyService pattern)
    - unconditional write for bidirectional rank update (rank-up + rank-down)
    - narrow additive append-at-class-tail for parallel-wave safety
key-files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7UpdateFactionRankTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt
decisions:
  - Plan 23-05: updateFactionRank writes new level UNCONDITIONALLY (not legacy "only promote") — enables rank-down per plan acceptance (Rule 2 additive)
  - Plan 23-05: Rank calculation uses upstream highCount formula (count of planets with level >= 4), NOT the plan-text "military_power + population + planet count" description — upstream ground truth, Rule 1 plan-vs-reality correction
  - Plan 23-05: Companion constants `FACTION_RANK_THRESHOLDS` / `FACTION_RANK_NAME` kept verbatim from legacy EconomyService for OpenSamguk parity; LOGH Empire/Alliance rank titles (원수/대장 etc.) deferred to Plan 23-10 alongside history logging
  - Plan 23-05: Neutral faction (id=0) skipped, mirroring sibling processIncome / processSemiAnnual / processMonthly pattern
  - Plan 23-05: History logging (【작위】), level-up gold/rice reward, inheritance point accrual, UpdateNationLevelAction stub routing all deferred to Plan 23-10 cleanup pass
metrics:
  duration_minutes: 21
  tests_added: 5
  files_touched: 2
  completed_date: 2026-04-10
---

# Phase 23 Plan 05: Gin7.updateFactionRank Summary

Ported legacy `EconomyService.updateNationLevelEvent` into `Gin7EconomyService.updateFactionRank(world)` with `FACTION_RANK_THRESHOLDS` / `FACTION_RANK_NAME` companion constants, using the `count(planet.level >= 4)` formula from upstream a7a19cc3~1 and adding bidirectional rank-down support beyond the legacy promote-only body.

## What was built

### Companion constants (`Gin7EconomyService.Companion`)

```kotlin
val FACTION_RANK_THRESHOLDS: IntArray = intArrayOf(0, 1, 2, 4, 6, 9, 12, 16, 20, 25)
val FACTION_RANK_NAME: Array<String> = arrayOf(
    "방랑군", "도위", "주자사", "주목", "중랑장", "대장군", "대사마", "공", "왕", "황제",
)
fun getFactionRankName(level: Int): String = FACTION_RANK_NAME.getOrElse(level) { "???" }
```

Threshold index is the rank level (0–9); value is the minimum count of owned planets with `level >= 4` required to reach that rank.

### Method (`Gin7EconomyService.updateFactionRank`)

- Annual recalculation (triggered via Jan 1 scenario event in Plan 23-10).
- Skips neutral faction (`id = 0`).
- For each faction: count owned planets with `level >= 4` → walk thresholds ascending → pick the highest matching index.
- Writes `faction.factionRank = newLevel.coerceIn(0, 9).toShort()` **unconditionally** so factions can rank UP or DOWN.
- Logs `[World X] Faction Y 등급 승격|강등 oldName → newName (highCount=N)` per mutation.
- Defensive empty-world short-circuit returns before touching planet repository.

## Tests

File: `backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7UpdateFactionRankTest.kt` (5 tests, all passing)

| # | Test | Asserts |
|---|------|---------|
| 1 | `faction with no high-level planets stays at rank 0` | level-1 planet only → `factionRank == 0` (방랑군) |
| 2 | `faction with 4 high-level planets reaches rank 3` | 4× level-4 planets → `factionRank == 3` (주목) |
| 3 | `faction rank drops when high-level planet count shrinks` | pre-rank 5 + only 2 high planets → `factionRank == 2` (주자사, rank DOWN) |
| 4 | `getFactionRankName returns legacy ranks and clamps out-of-range` | 10 ranks verified + `-1`/`10` → `"???"` |
| 5 | `empty world runs cleanly and neutral faction is skipped` | neutral (id=0) with level-5 planet → `factionRank` untouched, no crash |

Scoped run (after sibling 23-04 RED stash isolation):

```
./gradlew :game-app:test --tests "com.openlogh.engine.Gin7UpdateFactionRankTest"
BUILD SUCCESSFUL in 47s
```

## Commits

| Hash      | Type | Message                                                                      |
|-----------|------|------------------------------------------------------------------------------|
| 02110e53  | test | `test(23-05): RED failing test for Gin7EconomyService.updateFactionRank`     |
| d193138f  | feat | `feat(23-05): port updateFactionRank + FACTION_RANK_NAME into Gin7EconomyService` |

## Deviations from Plan

### Rule 1 — Plan vs upstream reality correction

**1. Rank formula is planet-level-count, not military_power**
- **Found during:** Task 1 (test scaffold)
- **Plan text said:** "Recalculates each faction's rank based on military_power, planet count, and population thresholds."
- **Upstream reality (a7a19cc3~1 `EconomyService.updateNationLevel`):** Uses ONLY `highCities = count(cities.level >= 4)` against `NATION_LEVEL_THRESHOLDS`. No `military_power` or `population` involvement.
- **Fix:** Ported upstream formula verbatim. The plan text was drifted; the upstream body is ground truth (Phase 23 CONTEXT.md scope: "parity with upstream opensamguk's legacy bodies").
- **Follows same pattern as:** Plan 23-03 plan-vs-upstream `warState > 0` correction.

### Rule 2 — Missing critical functionality (auto-added)

**2. Rank-down support (bidirectional update)**
- **Found during:** Task 1 (test 3 required it; plan acceptance explicitly lists "Rank-up and rank-down both work")
- **Issue:** Upstream body has `if (newLevel > nation.level.toInt())` — factions can only promote, never demote. A faction that loses high-level planets keeps its old rank forever.
- **Fix:** Write `faction.factionRank` unconditionally whenever `newLevel != oldLevel`. Tests 2 (rank-up) and 3 (rank-down) verify both directions.
- **Files modified:** `Gin7EconomyService.kt` (the `updateFactionRank` body)
- **Commit:** d193138f

## Auth gates

None — pure Kotlin port with mocked repositories. No external auth required.

## Deferred to Plan 23-10 (cleanup)

1. **Level-up gold/rice reward**: Legacy body added `newLevel * 1000` to both treasury resources on promotion. Deferred because it's coupled to history-log copy ("【작위】 …로 승격") and inheritance point accrual — cleaner to land as one coherent cleanup pass.
2. **【작위】 history logging**: Promotions generated both `historyService.logWorldHistory` and `logNationHistory` calls with rank-dependent Korean copy (옹립 / 책봉 / 임명 / 승격). Deferred.
3. **Inheritance point accrual**: `inheritanceService.accruePoints(general, "unifier", 1)` for all officers of a promoted faction. Deferred.
4. **UpdateNationLevelAction → gin7 wire-up**: `EconomyService.updateNationLevelEvent` is still a stub at line 257; Plan 23-10 will replace the `TODO Phase 4` comment with `gin7EconomyService.updateFactionRank(world)` call.
5. **LOGH-specific rank titles**: CLAUDE.md defines Empire (원수/대장/…) and Alliance rank tables; keeping OpenSamguk legacy names (방랑군/도위/…) for now matches the existing `NATION_LEVEL_NAME` in `EconomyService.kt` for parity-audit traceability. Plan 23-10 can layer a faction-type-aware name lookup if desired.

## Parallel-wave collision handling

Siblings 23-04 (`payOfficerSalaries`, in progress) and 23-06 (`updatePlanetSupplyState`, complete at 7a15f663/b5387124) both touch `Gin7EconomyService.kt` in the same wave.

**Applied patterns:**

1. **Append-at-class-tail for the method** — avoids the active `processIncome` / `processMonthly` area where 23-04 is still editing.
2. **Companion-block insertion after `private val logger`** — unique anchor, distant from both 23-04's salary additions (processIncome region) and 23-06's supply state block (already at the tail, now one method above mine).
3. **Temporary stash of sibling 23-04 RED test file** — `Gin7SalaryOutlayTest.kt` still references unimplemented `payOfficerSalaries`, blocking `compileTestKotlin`. Stashed to `/tmp/Gin7SalaryOutlayTest.kt.23-05-stash` during the scoped test run, then restored immediately after — never committed while stashed. Mirrors the Phase 14 compile-isolation pattern.
4. **Two Read retries on Gin7EconomyService.kt** — file modified between initial read and first Edit due to 23-06 constructor/supply-state landing, then again by 23-04's `BillFormula` import. Re-read + re-anchor on each retry; final edits use highly unique multi-line anchors (`processMonthly` KDoc header; `updatePlanetSupply` tail `planet.frontState = 0`).

## Self-Check

```
FOUND: backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt
FOUND: backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7UpdateFactionRankTest.kt
FOUND: 02110e53 (test RED)
FOUND: d193138f (feat GREEN)
PASS: 5 Gin7UpdateFactionRankTest tests BUILD SUCCESSFUL
```

## Self-Check: PASSED
