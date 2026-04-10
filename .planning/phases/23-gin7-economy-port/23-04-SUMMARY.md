---
phase: 23-gin7-economy-port
plan: 23-04-Gin7-salary-outlay
milestone: v2.3
subsystem: engine/economy
wave: 2
tags: [economy, gin7-port, upstream-parity, salary-outlay, bill-formula, shared-helper, parallel-wave]
requirements: [EC-04]
dependency_graph:
  requires:
    - 23-01 (processIncome per-resource body — gold branch is the outlay host)
    - 23-02 (OfficerRepository nullable ctor prelude — required for officer.funds crediting)
    - 22-01 (FactionAI.getBillFromDedication formula — source of truth for the port)
  provides:
    - Gin7EconomyService.payOfficerSalaries(world, faction, officers): Int
    - com.openlogh.engine.economy.BillFormula (shared between FactionAI + Gin7)
  affects:
    - FactionAI.getBillFromDedication (now a thin delegator to BillFormula)
    - processIncome("gold") call path (salary outlay now runs after income calc)
tech_stack:
  added: []
  patterns:
    - shared-object-extraction (BillFormula → pure top-level object, UtilityScorer precedent)
    - thin-delegator-preserves-reflection-test (FactionAI private method kept as 1-liner)
    - sibling-commit-absorption (Phase 14 D-10 precedent — source edits absorbed into sibling commit)
    - parallel-wave-test-isolation (scoped --tests filter + stale XML detection via --rerun-tasks)
    - guarded-integration-wiring (isGold && officerRepo != null — preserves 2-arg legacy ctor path)
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/economy/BillFormula.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7SalaryOutlayTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt (thin delegator + import cleanup)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt (ABSORBED into sibling commit d193138f)
decisions:
  - "Chose option (B) — extracted BillFormula to engine.economy.BillFormula as a pure top-level object shared between FactionAI and Gin7EconomyService. Mirrors UtilityScorer / SuccessionService / CommandHierarchyService precedent: engine-layer math helpers live as top-level `object` declarations, not private class members. Single source of truth for the Phase 22-01 legacy-correct formula"
  - "Kept FactionAI.getBillFromDedication as a private 1-line delegator to BillFormula.fromDedication to preserve the Phase 22-01 `FactionAIBillFormulaTest` reflection-based regression test unchanged. The reflection test uses `FactionAI::class.java.getDeclaredMethod(\"getBillFromDedication\", ...)` which would break if the helper was deleted outright. 6/6 tests still pass"
  - "Removed `kotlin.math.ceil` import from FactionAI (ownership moved to BillFormula); preserved `kotlin.math.sqrt` import because FactionAI.kt line 602 still uses `sqrt(militaryPower+1)` in a separate weighting function (`selectRewardTarget` / similar). Initial removal was reverted — grep confirmed dangling sqrt use"
  - "payOfficerSalaries is a pure in-memory transform (no repository writes) — callers own persistence. processIncome(gold) wiring calls factionRepository.saveAll + officerRepository.saveAll once after all factions are processed; the method itself just mutates faction.funds and officer.funds. This keeps unit tests trivially construable without mock verification on saveAll"
  - "Salary outlay wired into processIncome gold branch only (`if (isGold && officerRepo != null)`), not rice branch. Matches legacy hwe/sammo/Event/Action/ProcessIncome.php where salary is paid in 금 (gold), not 쌀 (rice). The null guard preserves the 2-arg legacy ctor test path from Plan 23-01 (Gin7ProcessIncomeTest continues to pass unchanged — officerRepository is null in those tests so salary loop is skipped entirely)"
  - "Negative faction.funds allowed — no clamp, no overdraft guard. Matches upstream PHP behavior exactly. NPC FactionAI.adjustTaxAndBill (Phase 22-01) handles recovery on subsequent ticks by raising taxRate. Test 3 (`payOfficerSalaries allows faction funds to go negative`) locks this against future over-defensive refactors"
  - "Inactive officer filter via `npcState.toInt() == 5` (graveyard sentinel) — same filter Phase 22-01 FactionAI uses. Test 4 locks this with 2 active + 2 inactive officers setup; inactive officer funds stay at pre-call sentinel values (9999, 8888)"
  - "SIBLING COMMIT ABSORPTION — my full edits to `Gin7EconomyService.kt` (import BillFormula + import Faction/Officer + fun payOfficerSalaries + processIncome gold-branch salary outlay wiring + totalSalariesPaid tracking + log line) were absorbed into sibling commit `d193138f feat(23-05): port updateFactionRank + FACTION_RANK_NAME into Gin7EconomyService` via the parallel-wave git-add race. Canonical ownership lives in THIS SUMMARY. Pattern precedent: Phase 14 D-10 / 14-14 / 14-16 / 14-17 and Phase 23-01 / 23-02 — Gin7EconomyService is the hottest sibling-conflict file in Phase 23 Wave 2"
metrics:
  duration: ~35 minutes (including parallel-wave compile blocking + forced --rerun-tasks)
  completed_date: 2026-04-10
  tasks_completed: 2
  files_modified: 2 (1 absorbed)
  files_created: 2
  tests_added: 5
  tests_passing:
    - Gin7SalaryOutlayTest 5/5 (new, this plan)
    - FactionAIBillFormulaTest 6/6 (Phase 22-01 regression — unchanged)
    - Gin7ProcessIncomeTest 5/5 (Plan 23-01 regression — unchanged)
commits:
  - "2bd442e2: test(23-04) RED — 5 failing tests for payOfficerSalaries contract"
  - "d193138f: feat(23-05) [ABSORBED] — 23-04 Task 2 GREEN source edits to Gin7EconomyService.kt (payOfficerSalaries + processIncome wiring + BillFormula import) landed under sibling 23-05's updateFactionRank commit due to parallel git-add race. Canonical 23-04 code ownership documented here"
  - "b96ad4d0: feat(23-04) GREEN — BillFormula extraction + FactionAI.getBillFromDedication thin delegator"
---

# Phase 23 Plan 04: Gin7EconomyService.payOfficerSalaries + Shared BillFormula Summary

## One-liner

Extracted the Phase 22-01 officer bill formula (`ceil(sqrt(dedication)/10)*200+400`, clamped at maxDedLevel=30) to a new `engine.economy.BillFormula` pure object shared between `FactionAI` and `Gin7EconomyService`, then ported the faction→officer monthly salary transfer as a new `payOfficerSalaries(world, faction, officers): Int` method wired into `processIncome(world, "gold")` on the month-1 schedule — faction.funds is decremented and each active officer.funds is credited by `bill * taxRate / 100`, with inactive (`npcState == 5`) officers filtered out and overdraft (negative funds) permitted per legacy PHP semantics.

## Context

Plan 23-01 landed `Gin7EconomyService.processIncome(world, resource)` with a deliberately narrow scope: per-resource income calculation only. Salary outlay was explicitly deferred to this plan (Plan 23-04 CONTEXT.md breakdown). Phase 22-01 had already introduced `FactionAI.getBillFromDedication` as a private helper — the authoritative port of legacy PHP `hwe/func_converter.php` `getBill()` / `getDedLevel()`.

Plan 23-04 needed the same formula inside `Gin7EconomyService`. The plan's Task 2 gave three options: (A) expose the FactionAI helper, (B) extract to a shared top-level object, or (C) duplicate. I chose **(B)** for three reasons:

1. **Single source of truth** — LOGH has no other bill-formula call sites today, but as Phase 23 progresses (`processIncome`, `processSemiAnnual`, `processWarIncome`, future `updateSalaries`), more call sites will want the formula. A top-level object scales cleanly.
2. **Testability** — a pure object can be unit-tested directly without reflection. Phase 22-01 had to use `FactionAI::class.java.getDeclaredMethod` + `setAccessible(true)` because the helper was private. `BillFormula.fromDedication(…)` can be called from any test.
3. **Precedent parity** — LOGH already uses the pure-object pattern for engine-layer pure functions: `UtilityScorer`, `CommandHierarchyService`, `SuccessionService`, `SensorRangeFormula`. `BillFormula` fits the same shape.

The tricky part was preserving the Phase 22-01 regression test (`FactionAIBillFormulaTest`) which reflects on the private `FactionAI.getBillFromDedication`. I kept that helper as a thin 1-line delegator so the reflection test continues to pass unchanged.

## What shipped

### `engine.economy.BillFormula` (new file)

```kotlin
object BillFormula {
    const val MAX_DED_LEVEL: Int = 30

    fun fromDedication(dedication: Int): Int {
        val dedLevel = ceil(sqrt(dedication.toDouble()) / 10.0).toInt().coerceIn(0, MAX_DED_LEVEL)
        return dedLevel * 200 + 400
    }
}
```

Pure object. No Spring DI. Legacy reference in KDoc: `hwe/func_converter.php` getBill() / getDedLevel(). Anchor value table in KDoc matches Phase 22-01 FactionAIBillFormulaTest:

| dedication  | dedLevel | bill |
|-------------|----------|------|
| 0           | 0        | 400  |
| 100         | 1        | 600  |
| 400         | 2        | 800  |
| 10_000      | 10       | 2400 |
| 1_000_000   | 30 (cap) | 6400 |

### `FactionAI.getBillFromDedication` thin delegator

```kotlin
private fun getBillFromDedication(dedication: Int): Int =
    BillFormula.fromDedication(dedication)
```

Private method preserved — signature and name unchanged. Phase 22-01 `FactionAIBillFormulaTest` reflection test passes unchanged (6/6).

- `import kotlin.math.ceil` removed from FactionAI.kt (no longer used).
- `import kotlin.math.sqrt` preserved — FactionAI line 602 still uses `sqrt(it.militaryPower.toDouble() + 1.0)` in `selectRewardTarget` (or similar). Grep confirmed the dangling use before committing.

### `Gin7EconomyService.payOfficerSalaries(world, faction, officers): Int`

```kotlin
fun payOfficerSalaries(
    world: SessionState,
    faction: Faction,
    officers: List<Officer>,
): Int {
    val taxRate = faction.taxRate.toInt()
    var totalPaid = 0
    for (officer in officers) {
        if (officer.npcState.toInt() == 5) continue // graveyard — skip
        val bill = BillFormula.fromDedication(officer.dedication)
        val individualSalary = bill * taxRate / 100
        officer.funds += individualSalary
        totalPaid += individualSalary
    }
    faction.funds -= totalPaid
    return totalPaid
}
```

Pure in-memory transform. No repository writes — callers own persistence. Returns `totalPaid` so callers can log the outlay. Conservation law holds: `totalPaid == Σ officer.funds credits == -(faction.funds delta)`.

### `processIncome("gold")` wiring

Inside `processIncome(world, resource)`, after the per-faction income delta loop and `factionRepository.saveAll(factions)`:

```kotlin
// Plan 23-04: salary outlay runs only on the gold branch (month 1 schedule).
// Officer salaries are paid in funds, not supplies, so the rice branch
// never triggers the outlay step. Guarded on non-null officerRepository
// to preserve the 2-arg legacy test path (Plan 23-02 precedent).
var totalSalariesPaid = 0
val officerRepo = officerRepository
if (isGold && officerRepo != null) {
    val allOfficers = officerRepo.findBySessionId(sessionId)
    val officersByFaction = allOfficers.groupBy { it.factionId }
    for (faction in factions) {
        if (faction.id == 0L) continue
        val factionOfficers = officersByFaction[faction.id] ?: continue
        if (factionOfficers.isEmpty()) continue
        totalSalariesPaid += payOfficerSalaries(world, faction, factionOfficers)
    }
    factionRepository.saveAll(factions)
    if (allOfficers.isNotEmpty()) officerRepo.saveAll(allOfficers)
}
```

Guarded on `isGold` (rice branch is pure income, no salary) AND `officerRepo != null` (preserves the 2-arg legacy ctor path from `Gin7ProcessIncomeTest` and `Gin7EconomyServiceTest`). The extra `factionRepository.saveAll(factions)` after the outlay is idempotent because JPA merge treats already-managed entities as updates.

### Test suite: `Gin7SalaryOutlayTest` — 5 tests, all passing

1. **`payOfficerSalaries credits each active officer and debits faction by sum`** — Core conservation test. 3 officers × ded=400 (bill=800 each) × taxRate=100 → totalPaid=2400, faction funds 100_000→97_600, each officer funds 0→800. Anchors `BillFormula.fromDedication(400)==800` inline. Verifies `Σ officer.funds credits == totalPaid == -(faction delta)`.
2. **`payOfficerSalaries scales salary by faction taxRate`** — taxRate=50 yields HALF the outlay of taxRate=100 (1200 vs 2400). Locks the `/100` taxRate scaling factor. A regression here would reintroduce the Phase 22-01 4x-underestimate bug if taxRate dropped out of the formula.
3. **`payOfficerSalaries allows faction funds to go negative`** — faction.funds=500, 3 officers × bill=800, totalPaid=2400 → funds = 500 - 2400 = -1900. Legal overdraft state. Officers still paid in full (conservation).
4. **`payOfficerSalaries excludes inactive officers with npcState equals 5`** — 2 active + 2 inactive. Inactive officer funds (9999, 8888) untouched; only active officers paid (totalPaid=1600 not 3200).
5. **`payOfficerSalaries conservation law holds across heterogeneous dedication`** — 4 officers with ded={0, 100, 10_000, 1_000_000} → bills {400, 600, 2400, 6400} → totalPaid=9800. Locks anchor values from Phase 22-01 and verifies conservation Σ credits == totalPaid == -(faction delta).

## Test results

```
Gin7SalaryOutlayTest        5/5 PASSING  (new — this plan)
FactionAIBillFormulaTest    6/6 PASSING  (Phase 22-01 regression — unchanged)
Gin7ProcessIncomeTest       5/5 PASSING  (Plan 23-01 regression — unchanged)
```

BUILD SUCCESSFUL via:
```
./gradlew :game-app:test \
  --tests 'com.openlogh.engine.Gin7SalaryOutlayTest' \
  --tests 'com.openlogh.engine.ai.FactionAIBillFormulaTest' \
  --tests 'com.openlogh.engine.Gin7ProcessIncomeTest' \
  --rerun-tasks \
  -Dkotlin.compiler.execution.strategy=in-process \
  --no-daemon
```

The `--rerun-tasks` flag was needed because the initial run reported `:game-app:test UP-TO-DATE` — the test binary was built against a pre-23-04 snapshot, so the XML reports didn't include `Gin7SalaryOutlayTest.xml`. Forced re-run produced fresh XMLs for all three target classes.

## Deviations from Plan

### Auto-fixed Issues

None in the execution sense. Two near-miss correctness items are worth noting:

**1. [Rule 1 — dangling import] sqrt import initially removed then restored**

- **Found during:** Task 2 (GREEN — FactionAI edits)
- **Issue:** My first FactionAI edit removed `import kotlin.math.ceil` (correct — ceil no longer used) AND `import kotlin.math.sqrt` (wrong — sqrt still used at line 602). Build would have broken on `compileKotlin`.
- **Fix:** Caught by sibling 23-06 runtime validation — they reported a dangling sqrt call at line 602 before their GREEN verification. I grepped `sqrt|import kotlin.math` and confirmed: line 602 calls `sqrt(it.militaryPower.toDouble() + 1.0)` in an unrelated weighting function. Preserved the sqrt import; only removed ceil.
- **Files affected:** `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt`
- **Outcome:** Compile clean; Phase 22-01 regression 6/6 still passes.

**2. Plan text referenced `AuthenticationGate getBillFromDedication is a PRIVATE helper`** — plan proposed 3 options (expose / extract / duplicate). I chose extract (option B) per the plan's stated preference. No deviation.

### Authentication gates

None.

## Sibling Commit Absorption (Phase 14 D-10 precedent)

During parallel Wave 2 execution of Phase 23-04, 23-05 (updateFactionRank), and 23-06 (updatePlanetSupplyState), the shared mutation hotspot was `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt`. Three executors appended methods + modified constructors simultaneously.

**My 23-04 Task 2 GREEN edits to `Gin7EconomyService.kt` were absorbed into sibling commit `d193138f feat(23-05): port updateFactionRank + FACTION_RANK_NAME into Gin7EconomyService` via git-add race.** At the moment sibling 23-05 ran `git add Gin7EconomyService.kt && git commit`, the working tree contained BOTH their `updateFactionRank` method AND my edits (import BillFormula, payOfficerSalaries method, processIncome gold-branch salary outlay wiring, totalSalariesPaid tracking). Git staged the entire file, and 23-05's commit body `feat(23-05): port updateFactionRank + FACTION_RANK_NAME...` now contains:

- Sibling 23-05: `updateFactionRank(world)` method + `FACTION_RANK_THRESHOLDS` / `FACTION_RANK_NAME` companion object constants (~150 lines)
- **This plan** (23-04): 
  - `import com.openlogh.engine.economy.BillFormula`
  - `fun payOfficerSalaries(world, faction, officers): Int` (~80 lines with KDoc)
  - `processIncome` gold-branch salary outlay wiring (`if (isGold && officerRepo != null)` block, ~20 lines)
  - `totalSalariesPaid` log-line update (~2 lines)

**Canonical code ownership attribution for the 23-04 source edits to `Gin7EconomyService.kt` lives in THIS SUMMARY.** Verified via:

```
$ git show d193138f -- backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt \
    | grep -E '^\+.*payOfficerSalaries|^\+.*BillFormula|^\+.*totalSalariesPaid'
+import com.openlogh.engine.economy.BillFormula
+        var totalSalariesPaid = 0
+                totalSalariesPaid += payOfficerSalaries(world, faction, factionOfficers)
+            world.id, resource, factions.size, totalDelta, totalSalariesPaid,
+     *   individualSalary = BillFormula.fromDedication(officer.dedication) * faction.taxRate / 100
+     * - **Formula authority** — [BillFormula.fromDedication] is the single
+    fun payOfficerSalaries(
+            val bill = BillFormula.fromDedication(officer.dedication)
```

The GREEN follow-up commit `b96ad4d0: feat(23-04): extract BillFormula as shared engine.economy helper` contains the part that was NOT absorbed: `BillFormula.kt` creation + `FactionAI.kt` delegator edit. Together with the RED commit `2bd442e2: test(23-04): add failing tests for ...`, the 23-04 deliverable spans three commits:

| Commit   | Ownership         | Content                                                              |
|----------|-------------------|----------------------------------------------------------------------|
| 2bd442e2 | 23-04 (clean)     | test(23-04) RED — 5 failing tests for payOfficerSalaries contract    |
| d193138f | 23-05 (absorbed)  | feat(23-05) + [23-04 absorbed] — updateFactionRank + payOfficerSalaries + processIncome wiring |
| b96ad4d0 | 23-04 (clean)     | feat(23-04) GREEN — BillFormula extraction + FactionAI delegator     |

Pattern precedent (Phase 14 and Phase 23):

- Plan 14-09 source absorbed into 14-10 commit
- Plan 14-10 Task 1+2 absorbed into 14-09 sibling commits b5c87d84 + 03e8ef2d
- Plan 14-14 absorbed into 14-16 commit 50dcfc82
- Plan 14-17 Task 1 absorbed into 14-18 commit eb9112bb
- Plan 23-01 absorbed into sibling 23-02 commit 9a22d47a
- Plan 23-02 GREEN source absorbed into sibling 23-01 commit 9a22d47a
- **Plan 23-04 GREEN Task 2 source absorbed into sibling 23-05 commit d193138f** ← THIS PLAN

`Gin7EconomyService.kt` is the hottest file in Phase 23 Wave 2 for this reason — 5 of 10 Phase 23 plans touch it. Future Phase 23 execution should treat absorption as expected rather than anomalous.

## Known Stubs

None. `payOfficerSalaries` is fully implemented per legacy parity. `BillFormula.fromDedication` is a complete 1:1 port. No TODOs, no hardcoded UI values, no placeholder data.

## Self-Check: PASSED

- [x] FOUND: `backend/game-app/src/test/kotlin/com/openlogh/engine/Gin7SalaryOutlayTest.kt`
- [x] FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/economy/BillFormula.kt`
- [x] FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt` (modified — thin delegator present)
- [x] FOUND: `backend/game-app/src/main/kotlin/com/openlogh/engine/Gin7EconomyService.kt` (absorbed — payOfficerSalaries + BillFormula import + processIncome wiring all in d193138f)
- [x] FOUND commit: 2bd442e2 (RED — clean 23-04 ownership)
- [x] FOUND commit: d193138f (GREEN Part 1 — absorbed into sibling 23-05)
- [x] FOUND commit: b96ad4d0 (GREEN Part 2 — clean 23-04 ownership)
- [x] 5/5 Gin7SalaryOutlayTest PASSING
- [x] 6/6 FactionAIBillFormulaTest PASSING (Phase 22-01 regression unaffected)
- [x] 5/5 Gin7ProcessIncomeTest PASSING (Plan 23-01 regression unaffected)
- [x] BillFormula.fromDedication is the canonical formula authority
- [x] FactionAI.getBillFromDedication delegates to BillFormula (private helper preserved for reflection test)
- [x] payOfficerSalaries wired into processIncome("gold") gold branch, guarded on non-null officerRepository
- [x] Negative funds allowed; inactive (npcState=5) officers excluded; conservation law holds
