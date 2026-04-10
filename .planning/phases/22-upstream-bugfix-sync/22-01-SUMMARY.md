---
phase: 22-upstream-bugfix-sync
plan: 22-01-FactionAI-bill-formula
milestone: v2.2
subsystem: engine.ai
tags: [upstream-port, bugfix, npc-economy, tdd]
requirements: [US-01]
upstream_commit: a7a19cc3cd5b3fa5a7c8720484d289fc55845adc
dependency_graph:
  requires:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt (existing buggy implementation)
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Faction.kt (taxRate, conscriptionRateTmp, funds, supplies)
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt (dedication, npcState)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/port/WorldWritePort.kt
  provides:
    - FactionAI.getBillFromDedication private helper
    - legacy-correct totalBill formula in FactionAI.adjustTaxAndBill
    - taxRate floor of 20 (was 0)
  affects:
    - NPC faction monthly tax/salary auto-balancing — no longer drains treasury via 4x salary underestimate
tech_stack:
  added: []
  patterns:
    - reflection-based unit test for private Kotlin helpers (Java getDeclaredMethod + setAccessible)
    - capturing fake WorldWritePort for write-side observation without full Spring context
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/FactionAIBillFormulaTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt
decisions:
  - Plan listed getBillFromDedication(10000)==4400, but the legacy PHP formula gives 2400 (sqrt(10000)/10=10, 10*200+400=2400). Test asserts the correct legacy value; plan arithmetic was off and ported value takes precedence.
  - Added a 5th test (max-clamp at dedication=1_000_000 → 6400) to lock in the maxDedLevel=30 cap, beyond the plan's 4-test minimum.
  - Test isolation pattern uses reflection on private helpers + a capturing fake WorldWritePort instead of @SpringBootTest, mirroring NationAITest's no-Spring approach (D-37 from Phase 13).
  - GREEN edit replaced the stale TODO comment with a KDoc citing the upstream commit + legacy PHP reference, matching upstream's KDoc verbatim aside from the package/type names.
metrics:
  duration_sec: 312
  duration_human: ~5m
  tasks: 2
  files_created: 1
  files_modified: 1
  tests_added: 5
  tests_pass: 6
  completed: 2026-04-10T06:13:00Z
commits:
  - 7b1014a2: test(22-01) RED — FactionAIBillFormulaTest 5 cases, 4 fail
  - 0c424259: fix(22-01) GREEN — port getBillFromDedication + sum-based totalBill + min taxRate=20
---

# Phase 22 Plan 01: FactionAI bill formula port — Summary

Ported upstream commit `a7a19cc3` `fix: NPC 국가/장수 금 증발 버그 수정` into LOGH's `FactionAI.adjustTaxAndBill`, replacing the broken `(officers + planets) * taxRate` salary estimate with the legacy PHP `sum(getBill(dedication))` formula and clamping the minimum tax rate to 20 (not 0).

## What changed

`FactionAI.adjustTaxAndBill` previously computed `totalBill = (nationGenerals.size + nationCities.size) * nation.taxRate.toInt().coerceAtLeast(0)`. With ~10 officers and ~5 planets at taxRate=100, this yielded `totalBill=1500` — but the actual monthly salary outlay (per legacy `hwe/func_converter.php`) for those same officers averages ~6_000-14_000. The 4x underestimate caused the lower-rate branch (`funds < totalBill * 3`) to fire incorrectly, producing a tax-rate spiral that drained NPC faction funds toward zero.

The fix introduces a private helper `getBillFromDedication` matching legacy PHP exactly:

```kotlin
private fun getBillFromDedication(dedication: Int): Int {
    val dedLevel = ceil(sqrt(dedication.toDouble()) / 10.0).toInt().coerceIn(0, 30)
    return dedLevel * 200 + 400
}
```

`adjustTaxAndBill` now sums `getBillFromDedication(officer.dedication)` over active officers (`npcState != 5`) to derive `baseOutcome`, then computes `totalBill = baseOutcome * taxRate / 100`. The lower-bound guard becomes `newBill > 20`, the lowering coercion uses `coerceAtLeast(20)`, and the final write uses `coerceIn(20, 200)` so the rate can never drop into the broken sub-20 zone the legacy treasury cannot recover from.

## TDD cycle

**RED (commit 7b1014a2):** Created `FactionAIBillFormulaTest.kt` with 5 cases — 3 direct helper assertions, 1 anchor test that proves the formula shape (10 officers at dedication=400 + funds=20_000 must lower taxRate from 100 to 95), and 1 min-clamp invariant. 4 failed (NoSuchMethodException on the missing helper, AssertionFailedError on the anchor); the min-clamp test coincidentally passed under the broken code at taxRate=25 because both paths lower to 20 there.

**GREEN (commit 0c424259):** Added `kotlin.math.ceil` import, wrote the helper, replaced the totalBill computation, updated the lower-bound guard + coercion, replaced the stale TODO with a KDoc citing the upstream commit and legacy PHP reference. All 6 tests pass; existing `NationAITest` and `FactionAISchedulerTest` confirmed unaffected.

## Domain mapping applied

| Upstream (com.opensam) | LOGH (com.openlogh) |
|---|---|
| `Nation` | `Faction` |
| `nation.gold` | `nation.funds` |
| `nation.bill` | `nation.taxRate` |
| `nation.rateTmp` | `nation.conscriptionRateTmp` |
| `putNation` | `putFaction` |
| `General.dedication` / `npcState` | `Officer.dedication` / `npcState` (preserved) |

The variable name `nation` is left unchanged inside `FactionAI.adjustTaxAndBill` (the parameter is already typed `Faction`); upstream's variable was `nation: Nation`. Renaming the parameter is out of scope and would expand the diff with no behavior impact.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Plan arithmetic correction] Test 3 expected value**
- **Found during:** Task 1 (RED)
- **Issue:** Plan task 1 listed `getBillFromDedication(10000) == 4400`. Recomputing per the upstream KDoc: `sqrt(10000)=100, /10=10, ceil(10)=10, 10*200+400=2400`. The plan's value was off by 2000.
- **Fix:** Test asserts `2400` (the legacy-correct value). Documented in test comment + this summary so the plan author can patch the plan text if reused.
- **Files modified:** FactionAIBillFormulaTest.kt
- **Commit:** 7b1014a2

### Authentication gates

None.

## Verification

- `./gradlew :game-app:test --tests "com.openlogh.engine.ai.FactionAIBillFormulaTest"` — 6/6 pass (5 tests, 1 of which has a parameterised display with the same name)
- `./gradlew :game-app:test --tests "com.openlogh.engine.ai.NationAITest" --tests "com.openlogh.engine.ai.FactionAISchedulerTest"` — pass, no regression

## Acceptance criteria

- [x] `FactionAI.getBillFromDedication` helper exists with legacy PHP formula
- [x] `FactionAI.adjustTaxAndBill` uses `baseOutcome * taxRate / 100` for `totalBill`
- [x] Minimum `taxRate` clamped to 20 throughout (`> 20` guard, `coerceAtLeast(20)`, `coerceIn(20, 200)`)
- [x] `kotlin.math.ceil` and `kotlin.math.sqrt` imports present
- [x] `FactionAIBillFormulaTest.kt` created with ≥4 tests, all passing
- [x] Each task committed individually with `--no-verify`
- [ ] SUMMARY.md + STATE.md + ROADMAP.md updated (this commit)

## Self-Check: PASSED

- FOUND: backend/game-app/src/test/kotlin/com/openlogh/engine/ai/FactionAIBillFormulaTest.kt
- FOUND: backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt (modified — getBillFromDedication present, ceil import present)
- FOUND: 7b1014a2 (RED commit)
- FOUND: 0c424259 (GREEN commit)
- 6/6 tests passing under `:game-app:test --tests FactionAIBillFormulaTest`
- No regression in `NationAITest` or `FactionAISchedulerTest`
