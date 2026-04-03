---
phase: 08-npc-ai-parity
plan: 05
subsystem: npc-ai
tags: [gap-closure, wiring, injury-threshold, parity]
dependency_graph:
  requires: [08-01, 08-03]
  provides: [runtime-nation-ai-parity, wanderer-injury-fix]
  affects: [TurnService, GeneralAI]
tech_stack:
  added: []
  patterns: [cureThreshold-guard, chooseNationTurn-wiring]
key_files:
  created: []
  modified:
    - backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt
decisions:
  - Wire TurnService to GeneralAI.chooseNationTurn() rather than porting PHP logic into NationAI
  - Wanderer injury uses same NpcNationPolicy lookup pattern as chooseInstantNationTurn
metrics:
  duration: 5min
  completed: 2026-04-02T05:51:00Z
  tasks: 2
  files: 3
---

# Phase 8 Plan 5: NPC AI Gap Closure Summary

TurnService wired to GeneralAI.chooseNationTurn() for NPC nation turns, replacing non-PHP-matching NationAI.decideNationAction(). Wanderer injury threshold fixed to use cureThreshold=10 with strict > comparison.

## What Was Done

### Task 1: Wire TurnService and fix wanderer injury threshold
- **Commit:** `061b7b5`
- Replaced `nationAI.decideNationAction(nation, world, rng)` call in TurnService.kt (line 497) with `generalAI.chooseNationTurn(general, world)`
- Changed guard from `!= "Nation휴식"` to `!= "휴식"` matching chooseNationTurn return values
- Removed DeterministicRng.create block (chooseNationTurn creates its own RNG internally with "GeneralAI" seed context)
- Fixed `decideWandererAction()` in GeneralAI.kt: replaced `injury > 0` with `injury > wandererPolicy.cureThreshold` using same NpcNationPolicy lookup pattern as chooseInstantNationTurn (line 3556-3563)

### Task 2: Regression tests for wanderer injury threshold
- **Commit:** `7e16ac3`
- Added `WandererInjuryThresholdTests` inner class with 3 tests:
  - injury=5 (below threshold=10): does NOT return "요양"
  - injury=15 (above threshold=10): returns "요양"
  - injury=10 (exactly at threshold): does NOT return "요양" (strict > comparison)
- Fixed pre-existing test `wanderer returns 요양 when injured` to use injury=15 (above threshold) instead of injury=10

## Verification Results

- `generalAI.chooseNationTurn` present in TurnService.kt: PASS
- `nationAI.decideNationAction` absent from TurnService.kt: PASS
- No `injury > 0` in GeneralAI.kt (only in comments): PASS
- All injury checks use `cureThreshold`: PASS (lines 173, 256, 1654, 1682, 3570)
- GeneralAITest: 91 tests, 0 failures: PASS
- WandererInjuryThresholdTests: 3 tests, 0 failures: PASS

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed pre-existing test expecting old injury > 0 behavior**
- **Found during:** Task 2
- **Issue:** Existing test `wanderer returns 요양 when injured` used injury=10 which equals cureThreshold, but strict > means it should NOT return "요양"
- **Fix:** Changed test fixture from injury=10 to injury=15 (above threshold)
- **Files modified:** GeneralAITest.kt
- **Commit:** 7e16ac3

## Out-of-Scope Issues

- **NpcPolicyTest `default priority lists match expected order`** fails with pre-existing mismatch (expects "긴급내정" but gets "NPC사망대비"). Not caused by this plan's changes. Filed as pre-existing.

## Known Stubs

None.

## Decisions Made

1. **Wire to chooseNationTurn rather than port logic into NationAI:** The PHP-matching implementations (chooseTexRate, chooseGoldBillRate, chooseRiceBillRate) already exist in GeneralAI.chooseNationTurn(). Wiring TurnService to call it directly is simpler and ensures all PHP-matching rate choosers are reachable at runtime.
2. **Wanderer policy lookup mirrors chooseInstantNationTurn pattern:** For nationId=0 wanderers, use default NpcNationPolicy() (cureThreshold=10). For wanderers with a nation, look up the nation's policy. Same pattern as line 3556-3563.

## Self-Check: PASSED

- All 3 modified files exist on disk
- SUMMARY.md created at expected path
- Commit 061b7b5 (Task 1) found in git log
- Commit 7e16ac3 (Task 2) found in git log
