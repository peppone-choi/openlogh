---
phase: 23-gin7-economy-port
plan: 23-03-Gin7-processWarIncome
milestone: v2.3
subsystem: engine/economy
tags: [economy, gin7-port, upstream-a7a19cc3, war-income, casualty-salvage]
requirements: [EC-03]
dependency_graph:
  requires: [22-03]  # EconomyService per-resource schedule must exist
  provides: [Gin7EconomyService.processWarIncome]
  affects: [EconomyService.processWarIncomeEvent-stub (23-10 will wire)]
tech_stack:
  added: []
  patterns:
    - upstream-body-port (a7a19cc3)
    - parallel-wave-test-isolation (init-script exclude)
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7ProcessWarIncomeTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt
decisions:
  - "Plan text described 'factions with warState > 0 receive bonus' but upstream a7a19cc3 body has NO warState filter. Faithful port iterates planets with dead > 0 (casualty salvage model). Documented as Rule 1 deviation."
  - "War income = casualty salvage: faction.funds += dead/10 (ship/material recovery), planet.population += (dead*0.2).coerceAtMost(headroom), dead reset to 0"
  - "No resource parameter: war income is always funds per upstream signature (distinct from processIncome which is per-resource)"
  - "Parallel wave isolation: sibling 23-01/23-02 RED test files block shared :game-app:compileTestKotlin. Used Groovy init script (/tmp/gsd-23-03-exclude-siblings.init.gradle) to exclude them during scoped test run — Phase 14-03 precedent pattern"
  - "Runs every month via pre_month event (unlike processIncome month 1/7). Wiring deferred to 23-10 per CONTEXT.md"
metrics:
  duration: ~12 minutes
  completed_date: 2026-04-10
  tasks_completed: 2
  files_modified: 2
  tests_added: 5
  tests_passing: 27 (5 new + 6 Gin7EconomyServiceTest + 16 EconomyServiceScheduleTest)
commits:
  - "4015e542: test(23-03) RED — 5 failing tests for processWarIncome contract"
  - "4f23edb5: feat(23-03) GREEN — port upstream a7a19cc3 processWarIncome body"
---

# Phase 23 Plan 03: Gin7EconomyService.processWarIncome Summary

**One-liner:** Port upstream commit `a7a19cc3`'s `processWarIncome` body to LOGH's `Gin7EconomyService` — casualty salvage model that credits `faction.funds += planet.dead/10` and recovers `planet.population += (dead*0.2)` for every planet with non-zero casualties, then clears the dead counter.

## Context

Phase 22-03 ported the upstream `EconomyService.processWarIncomeEvent(world)` public entry point as a no-op stub with a `TODO Phase 4` marker. Plan 23-03 delivers the actual body inside `Gin7EconomyService` so Phase 23-10's pipeline wire-up can simply route the stub call to Gin7.

Unlike monthly tax collection (month 1/4/7/10) or per-resource income (month 1 gold, month 7 rice), **war income runs every month** via the scenario `pre_month` event `["ProcessWarIncome"]`, reflecting continuous casualty salvage from active fronts.

## What shipped

### `Gin7EconomyService.processWarIncome(world: SessionState)` — 79 LoC

```kotlin
@Transactional
fun processWarIncome(world: SessionState) {
    val sessionId = world.id.toLong()
    val factions = factionRepository.findBySessionId(sessionId)
    val planets = planetRepository.findBySessionId(sessionId)
    if (planets.isEmpty()) { /* short-circuit */ return }

    val factionMap = factions.associateBy { it.id }
    var payoutCount = 0

    for (planet in planets) {
        if (planet.dead <= 0) continue
        val faction = factionMap[planet.factionId] ?: continue

        // Upstream formula: nation.gold += city.dead / 10
        faction.funds += planet.dead / 10

        // Upstream formula: popGain = (dead * 0.2).toInt().coerceAtMost(headroom)
        val uncappedPopGain = (planet.dead * 0.2).toInt()
        val headroom = (planet.populationMax - planet.population).coerceAtLeast(0)
        planet.population += uncappedPopGain.coerceAtMost(headroom)

        planet.dead = 0
        payoutCount++
    }

    if (payoutCount > 0) {
        factionRepository.saveAll(factions)
        planetRepository.saveAll(planets)
    }
}
```

### `Gin7ProcessWarIncomeTest.kt` — 5 tests, all green

| # | Test | Formula verified |
|---|------|------------------|
| 1 | `planet with no casualties yields no war income` | Gate `dead > 0` skips untouched planets; funds/pop unchanged |
| 2 | `planet with casualties credits funds and restores population` | dead=1000 → funds+=100, pop+=200, dead=0 |
| 3 | `multiple warring factions each receive independent war income` | factionA=1000→1250 (50+200), factionB=2000→2030 (30) |
| 4 | `empty world runs cleanly with no side effects` | No NPE on empty faction/planet lists |
| 5 | `population gain is capped at populationMax headroom` | dead=1000, pop=9900/10000 → pop+=100 (not 200), funds+=100 |

## Key decisions

1. **Plan description corrected to upstream reality** — Plan 23-03 text claimed the filter is `faction.warState > 0`. The actual upstream a7a19cc3 `processWarIncome` body has no such filter; it iterates every city with `dead > 0`. I followed the upstream body faithfully. Logged as Rule 1 deviation below.
2. **Domain mapping** — `Nation.gold` → `Faction.funds`, `City.dead` → `Planet.dead`, `City.pop` → `Planet.population`, `City.popMax` → `Planet.populationMax`. No schema changes needed; all fields already exist on the LOGH entities (verified: `Faction.funds: Int`, `Planet.dead: Int`, `Planet.population: Int`, `Planet.populationMax: Int`).
3. **No resource parameter** — upstream signature takes only `(nations, cities)` and always mutates `nation.gold`. LOGH signature takes `(world)` for consistency with `processIncome(world, resource)` but omits the resource arg since war income is always funds.
4. **Short-circuit on empty planet list** — early return with debug log, mirroring `processIncome`'s empty-faction short-circuit.
5. **Save only when payoutCount > 0** — avoids unnecessary JPA dirty-check and flush for no-op months (e.g. peacetime when no planet has casualties).

## Parallel wave isolation

Sibling plans 23-01 (processIncome) and 23-02 (processSemiAnnual) are running concurrently in Wave 1 and have committed their RED test files (`Gin7ProcessIncomeTest.kt`, `Gin7ProcessSemiAnnualTest.kt`) without corresponding GREEN implementations. This blocks shared `:game-app:compileTestKotlin`.

**Mitigation:** Created `/tmp/gsd-23-03-exclude-siblings.init.gradle` Gradle init script that excludes sibling RED files from `compileTestKotlin` via `exclude '**/Gin7ProcessSemiAnnualTest.kt'` + `exclude '**/Gin7ProcessIncomeTest.kt'`. Used only for local scoped test runs; not committed. Precedent: Phase 14-03 decision D-19 ("TDD RED+GREEN compressed into single commit to avoid wedging shared :game-app:compileTestKotlin on other executors").

Main source (`:game-app:compileKotlin`) compiles cleanly without the init script — my main-source edit is fully additive and does not touch sibling methods.

## Test results

**New tests (Plan 23-03):**
- `Gin7ProcessWarIncomeTest`: **5/5 passing**

**Regression (Phase 4 + Phase 22-03 scoped):**
- `Gin7EconomyServiceTest`: **6/6 passing** (zero delta)
- `EconomyServiceScheduleTest`: **16/16 passing** (zero delta)

**Total:** 27 scoped tests green, 0 failures, 0 errors.

Full-regression suite run deferred to 23-10 post-merge gate per parallel-wave precedent — three sibling RED files simultaneously blocking shared compile is the exact condition 23-10 is scoped to reconcile.

## Deviations from Plan

### Rule 1 — Plan text vs. upstream body mismatch

**1. Plan described wrong filter semantics for war income**

- **Found during:** Task 1 RED — before writing the first test I extracted the upstream body via `git show a7a19cc3 -- backend/game-app/src/main/kotlin/com/opensam/engine/EconomyService.kt | awk '/fun processWarIncome/,...'`.
- **Plan claimed:** "Only factions with `warState > 0` receive war income bonus. Bonus is a flat funds payment proportional to military_power or number of officers."
- **Upstream reality:**
  ```kotlin
  private fun processWarIncome(nations: List<Nation>, cities: List<City>) {
      val nationMap = nations.associateBy { it.id }
      for (city in cities) {
          if (city.dead > 0) {
              val nation = nationMap[city.nationId] ?: continue
              nation.gold += (city.dead / 10)
              val popGain = (city.dead * 0.2).toInt()
                  .coerceAtMost((city.popMax - city.pop).coerceAtLeast(0))
              city.pop += popGain
              city.dead = 0
          }
      }
  }
  ```
  The gate is `city.dead > 0` (casualty count), NOT `nation.warState > 0`. The formula is proportional to `city.dead`, NOT to military_power or officer count. This is a **casualty salvage model**: every front-line engagement that produces casualties also salvages material/manpower on the next monthly tick.
- **Fix:** Implemented the upstream body verbatim in LOGH terms. Tests match upstream formulas exactly.
- **Files:** `Gin7EconomyService.kt` (processWarIncome body), `Gin7ProcessWarIncomeTest.kt` (5 tests built around `dead > 0` gate)
- **Commit:** `4f23edb5`
- **Note:** Plan 23-03 text is not updated — summary supersedes it. Future re-readers should treat this SUMMARY as the source of truth for EC-03 requirement semantics, and update REQUIREMENTS.md EC-03 text accordingly (currently reads "warState > 0" — deferred to 23-10 cleanup).

## Requirements status

- **EC-03** — "Gin7EconomyService.processWarIncome(world) 가 ..." — **SATISFIED (with semantic correction)**. Implementation follows upstream body: gate is `planet.dead > 0`, not `faction.warState > 0`. The plan-level requirement text needs a follow-up edit in 23-10's audit task; the **engine behavior** is correct per upstream source of truth.

## Deferred items

- **Pipeline wire-up:** `EconomyService.processWarIncomeEvent(world)` stub still logs "Phase 4 pending" — 23-10 will route the call to `gin7EconomyService.processWarIncome(world)`.
- **REQUIREMENTS.md EC-03 text correction:** replace "전쟁 상태(warState > 0) 팩션에만 월별 보너스를 지급" with "사상자가 발생한 행성(planet.dead > 0)에서 월별 회수 처리" — deferred to 23-10 audit task.
- **Full `:game-app:test` regression:** blocked by sibling 23-01/23-02 RED test compile failures; 23-10 pipeline wire-up plan will run the full suite after all three sibling GREENs merge.

## Files touched

### Created

- `backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7ProcessWarIncomeTest.kt` (239 LoC, 5 tests)

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt` (+79 LoC; additive — new `processWarIncome` method after `isTaxMonth` helper)

## Commits

| Hash | Type | Message |
|------|------|---------|
| `4015e542` | test | RED — 5 failing tests for processWarIncome contract |
| `4f23edb5` | feat | GREEN — port upstream a7a19cc3 processWarIncome body |

## Self-Check: PASSED

- `Gin7EconomyService.kt` contains `fun processWarIncome(world: SessionState)` — **FOUND**
- `Gin7ProcessWarIncomeTest.kt` exists at `backend/game-app/src/test/kotlin/com/openlogh/engine/` — **FOUND**
- Commit `4015e542` (RED) — **FOUND in git log**
- Commit `4f23edb5` (GREEN) — **FOUND in git log**
- `Gin7ProcessWarIncomeTest` JUnit XML: 5 tests, 0 failures — **VERIFIED**
- `Gin7EconomyServiceTest` regression: 6 tests, 0 failures — **VERIFIED**
- `EconomyServiceScheduleTest` regression: 16 tests, 0 failures — **VERIFIED**
