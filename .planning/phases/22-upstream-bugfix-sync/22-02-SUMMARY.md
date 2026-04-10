---
phase: 22-upstream-bugfix-sync
plan: 22-02-OfficerAI-doDonate-gate
milestone: v2.2
subsystem: engine.ai
tags: [upstream-port, bugfix, npc-economy, tdd, rng-gate]
requirements: [US-02]
upstream_commit: a7a19cc3cd5b3fa5a7c8720484d289fc55845adc
dependency_graph:
  requires:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/OfficerAI.kt (existing doDonate + doNpcDedicate implementations)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/NpcPolicy.kt (NpcNationPolicy.reqNationGold/reqNationRice thresholds)
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt (funds, supplies fields)
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Faction.kt (funds, supplies fields)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/AIContext.kt
  provides:
    - probability-gated excess-resource donation in OfficerAI.doDonate (primary site, line ~2390)
    - probability-gated excess-resource donation in OfficerAI.doNpcDedicate (secondary site, line ~3940)
    - divide-by-zero guards (reqGold/reqRice > 0) on every probability gate in both sites
    - OfficerAIDonateGateTest: 4 regression tests (excess-funds, zero-req, formula anchor, excess-supplies)
  affects:
    - NPC officer monthly donate decision path — no longer drains personal stockpiles unconditionally at > 5x threshold
    - Phase 22-03 EconomyService per-resource split — shares the same 'legacy schedule' root-cause category
tech_stack:
  added: []
  patterns:
    - reflection-based private-method test for OfficerAI (mirrors existing GeneralAITest.invokeDoDonate pattern)
    - FixedRandom test double returning saturating last-value for deterministic gate assertions
    - dual-site upstream port — primary site matches upstream verbatim, secondary LOGH-only site gets parity fix for pattern
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/ai/OfficerAIDonateGateTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/OfficerAI.kt
decisions:
  - Applied fix at BOTH doDonate (line 2388-2410) AND doNpcDedicate (line 3940) even though upstream GeneralAI.kt only had the primary site. LOGH's doNpcDedicate is a post-fork refactor containing the same unconditional-excess-branch pattern at the devel-general path; parity demands the same gate.
  - Used reflection on the private doDonate method (mirroring GeneralAITest.invokeDoDonate) instead of refactoring to internal visibility — keeps blast radius minimal and matches the established test pattern in the file-sibling.
  - FixedRandom(10.0) is the canonical "gate-blocking" RNG: 10.0 exceeds any reasonable 'funds/reqGold - 0.5' probability (max ≈ 10 for funds=100_000/req=10_000), so it cleanly distinguishes "gate consulted" from "unconditional donate".
  - Test 3 anchors the legacy PHP formula via two complementary cases: rng=1.0 < 5.5 donates, rng=6.0 >= 5.5 blocks. This locks in the exact comparison shape (strict less-than, probability = funds/reqGold - 0.5) so any future refactor preserves the legacy semantics.
  - Wave-1 sibling 22-01 owned FactionAI.kt; no file collision with this plan's OfficerAI.kt edit — both landed on main cleanly without rebase.
  - Existing GeneralAITest golden tests (doDonate with excess gold donates to nation + doNpcDedicate with excess resources donates) continue to pass post-fix because their funds=30_000/reqGold=10_000 setup yields probability 2.5, which Random(0).nextDouble() always clears — no regression.
metrics:
  duration_sec: 422
  duration_human: ~7m
  tasks: 2
  files_created: 1
  files_modified: 1
  tests_added: 4
  tests_pass: 4
  completed: 2026-04-10T06:15:14Z
commits:
  - c3387079: test(22-02) RED — OfficerAIDonateGateTest with 4 tests, all fail on pre-fix code
  - e5ce35ab: fix(22-02) GREEN — probability gate + divide-by-zero guard on doDonate + doNpcDedicate excess branches
---

# Phase 22 Plan 02: OfficerAI.doDonate probability gate — Summary

Ported upstream commit `a7a19cc3` `fix: NPC 국가/장수 금 증발 버그 수정` into LOGH's `OfficerAI.doDonate` (primary site, line ~2390) and `OfficerAI.doNpcDedicate` (secondary site, line ~3940), replacing the unconditional "excess resource" donation branches with the legacy PHP probability gate `rng.nextDouble() < (funds / reqGold - 0.5)`, and adding `reqGold > 0 &&` / `reqRice > 0 &&` divide-by-zero guards to every probability check in both methods.

## What changed

### Primary site — `OfficerAI.doDonate` (line 2388-2410)

**Before (bug):**

```kotlin
// Check gold
if (nation.funds < nationPolicy.reqNationGold && general.funds > reqGold * 1.5) {
    if (rng.nextDouble() < (general.funds.toDouble() / reqGold - 0.5)) {
        donateGold = true
    }
}
// Excess gold even if nation doesn't need it
if (!donateGold && general.funds > reqGold * 5 && general.funds > 5000) {
    donateGold = true  // ← unconditional, 100% donate every tick in 1만 초과 구간
}
```

**After (fix — mirrors upstream GeneralAI.kt verbatim aside from funds/supplies rename):**

```kotlin
// Check gold — legacy probability gate (PHP doNPC헌납 line 2841; upstream a7a19cc3)
if (nation.funds < nationPolicy.reqNationGold && general.funds > reqGold * 1.5) {
    if (reqGold > 0 && rng.nextDouble() < (general.funds.toDouble() / reqGold - 0.5)) {
        donateGold = true
    }
}
// Excess gold — still gated by probability (parity fix: previously unconditional)
if (!donateGold && general.funds > reqGold * 5 && general.funds > 5000) {
    if (reqGold > 0 && rng.nextDouble() < (general.funds.toDouble() / reqGold - 0.5)) {
        donateGold = true
    }
}
```

Same treatment applied to the supplies (rice) branches immediately below.

### Secondary site — `OfficerAI.doNpcDedicate` (line 3940)

The devel-general "Excess resources (5x threshold and >= 5000)" branch was donating unconditionally, matching the primary-site bug pattern even though this function is a LOGH-specific post-fork refactor not present in upstream GeneralAI.kt. Applied the same probability gate + divide-by-zero guard for parity:

```kotlin
// Excess resources (5x threshold and >= 5000) — parity fix (upstream a7a19cc3):
// previously unconditional, now gated by the same legacy probability formula.
if (gRes >= reqD * 5 && gRes >= 5000) {
    if (reqD > 0 && rng.nextDouble() < (gRes.toDouble() / reqD - 0.5)) {
        val amount = gRes - reqD
        candidates.add(DonateCandidate(isGold, amount, amount))
    }
    continue
}
```

The war-general branch and the "nation-needs" probability branches in `doNpcDedicate` already had the `reqW > 0 &&` / `reqRes > 0 &&` guards (added in an earlier LOGH refactor); no change was needed there.

## Performance

- **Duration:** ~7m (2026-04-10T06:08:12Z → 2026-04-10T06:15:14Z)
- **Tasks:** 2 (RED + GREEN, TDD flow)
- **Files created:** 1 (OfficerAIDonateGateTest.kt)
- **Files modified:** 1 (OfficerAI.kt — 17 insertions, 9 deletions)
- **Tests added:** 4 (all in OfficerAIDonateGateTest)
- **Tests passing:** 4/4 new + ~300 existing GeneralAITest cases (no regression)

## Accomplishments

1. **Drain fixed.** NPC officers with `funds > 5 * reqGold` no longer burn personal stockpiles every tick. Pre-fix drain rate was ~11.5%/year in the 1만 초과 구간 per upstream measurement; post-fix the rate drops to the legacy schedule's ~3%/year.
2. **Divide-by-zero protected.** Scenarios where `NpcNationPolicy.reqNationGold == 0` (e.g. custom scenario configs) previously would have triggered `funds / 0` in the probability computation — now guarded by short-circuit `reqGold > 0 &&`.
3. **Legacy formula anchored by tests.** The exact PHP formula `rng < (funds / reqGold - 0.5)` is now locked in by Test 3's two-case assertion (rng=1.0 donates, rng=6.0 blocks at funds=60_000/req=10_000 → prob=5.5). Any future refactor that changes the comparison shape will fail this test.
4. **Dual-site parity.** Both OfficerAI donation entry points are consistently gated, closing a side-channel where the `doNpcDedicate` devel path could still unconditionally drain.

## Task Commits

1. **Task 1 (RED): failing test for unconditional donate** — `c3387079` (`test(22-02)`)
2. **Task 2 (GREEN): add probability gates** — `e5ce35ab` (`fix(22-02)`)

## Files Created/Modified

### Created

- `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/OfficerAIDonateGateTest.kt` — 294 lines, 4 tests
  - `doDonate excess funds branch consults RNG and does not donate when gate blocks` (FixedRandom(10.0) blocks)
  - `doDonate excess funds branch does not donate when reqNationGold is zero` (NpcNationPolicy(reqNationGold = 0) guard test)
  - `doDonate excess funds branch matches legacy probability formula` (rng=1.0 donates, rng=6.0 blocks at funds=60k/req=10k)
  - `doDonate excess supplies branch also consults RNG gate` (symmetric fix verification, supplies=70k/req=12k)

### Modified

- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/OfficerAI.kt`
  - Lines 2388-2410 (`doDonate`): 4 probability gates hardened (nation-needs gold, excess gold, nation-needs rice, excess rice), all now carry `reqX > 0 &&` divide-by-zero guards
  - Lines 3940-3946 (`doNpcDedicate`): devel-general excess branch wrapped in `reqD > 0 && rng.nextDouble() < prob` gate

## Decisions Made

See `decisions:` in frontmatter above. Key rationale:

- **Dual-site port.** Upstream only touched `GeneralAI.doDonate`, but LOGH has a second donation path (`doNpcDedicate`) with the same unconditional-excess pattern. Porting to both sites is required by plan acceptance criteria and preserves LOGH's "no unconditional drain" invariant end-to-end.
- **Reflection-based private-method test.** Mirrors the existing `GeneralAITest.invokeDoDonate` pattern at line 2573 — no visibility change to production code, no Spring context needed, consistent with other test classes in this directory.
- **FixedRandom saturation semantics.** The existing FixedRandom in GeneralAITest returns `doubles.lastOrNull() ?: 0.0` when exhausted; my copy in OfficerAIDonateGateTest preserves that semantics so a single-value seed blocks every subsequent gate consultation deterministically.

## Deviations from Plan

**None that changed the shape of the work.** Two small plan-refinement adjustments:

1. **Task 1 (test) — corrected Test 1 supplies threshold.** Initial draft of Test 4 used `supplies = 60_000` with `reqNationRice = 12_000`, but `60_000 > 12_000 * 5 = 60_000` is strictly false so the excess-supplies branch was not reached and the test passed spuriously on pre-fix code during the first RED run (revealed in the first background run showing only 3/4 failures). Bumped to `supplies = 70_000` (`> 60_000`) before the commit, which made Test 4 fail correctly on pre-fix and pass correctly on post-fix. Caught and fixed during RED verification, no commit pollution.
2. **`doNpcDedicate` secondary-site scope decision.** Plan text said "port the same fix there if the bug pattern exists". The pattern IS present at line 3940 (devel-general excess branch donates unconditionally on 5x threshold + 5000 floor), so I applied the fix. The war-general branch at 3920-3927 already carried `reqW > 0 &&` and a probability `continue`-gate from an earlier LOGH refactor, so it needed no change.

## Issues Encountered

- **FixedRandom saturation behavior confusion (self-resolved).** Initially I was uncertain whether FixedRandom would consume extra RNG values during the doDonate flow's multiple `rng.nextDouble()` calls. Traced the exact call order: at `faction.funds = 999_999` (rich faction), the nation-needs branch short-circuits on `nation.funds < reqNationGold` BEFORE consuming RNG, so only the excess branch consumes one call. FixedRandom(10.0) with saturation returns 10.0 on first and any subsequent call, so any additional consumption would also block.
- **`classifyGeneral` call ordering.** My `buildCtx` helper calls `ai.classifyGeneral(officer, Random(0), policy.minNPCWarLeadership)` which uses its own Random(0) — NOT the test's FixedRandom. At leadership=50, the COMMANDER flag is set deterministically by `l >= minNPCWarLeadership` regardless of RNG, so this doesn't affect isWarGen selection. Confirmed no cross-contamination between context-building RNG and the injected doDonate RNG.

## Quantitative Impact

Matches upstream measurement (per `a7a19cc3` commit body and Phase 22 CONTEXT.md):

| Metric | Pre-fix | Post-fix |
|---|---|---|
| 장수 금 유지비 감소 (1만 초과 구간) | 11.5%/year | 3%/year (legacy schedule) |
| 장수 헌납 (1만 초과 구간, per tick) | 100% unconditional | Probability-gated (funds/reqGold - 0.5) |

## Next Phase Readiness

- **Wave 1 complete.** Both 22-01 (FactionAI bill) and 22-02 (OfficerAI donate) have landed; NPC officer- and faction-level economic drains are both closed on the AI-decision side.
- **Wave 2 (22-03) ready.** The EconomyService per-resource split plan for `processIncome` / `processSemiAnnual` (the legacy event-schedule root cause) has no dependency on 22-02 since that plan touches a different file (`EconomyService.kt`) and focuses on the tick-processing event dispatch rather than the AI decision tree.
- **Phase 22 deferred-items review.** No entries added to `deferred-items.md` — plan 22-02 scope was fully contained to OfficerAI.kt + its dedicated test file. Pre-existing 205 legacy-test failures remain out of scope per plan constraints.

## Self-Check: PASSED

- `backend/game-app/src/test/kotlin/com/openlogh/engine/ai/OfficerAIDonateGateTest.kt` — FOUND
- `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/OfficerAI.kt` — FOUND (modified)
- Commit `c3387079` (RED) — FOUND in git log
- Commit `e5ce35ab` (GREEN) — FOUND in git log
- Test run (`:game-app:test --tests OfficerAIDonateGateTest`) — BUILD SUCCESSFUL, 4/4 tests pass
- Regression run (`:game-app:test --tests GeneralAITest`) — BUILD SUCCESSFUL, no regressions in ~300 existing tests

---
*Phase: 22-upstream-bugfix-sync*
*Plan: 22-02 (OfficerAI.doDonate probability gate)*
*Completed: 2026-04-10T06:15:14Z*
