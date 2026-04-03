---
phase: 08-npc-ai-parity
verified: 2026-04-02T07:00:00Z
status: human_needed
score: 7/7 must-haves verified
re_verification:
  previous_status: gaps_found
  previous_score: 6/7
  gaps_closed:
    - "TurnService now calls generalAI.chooseNationTurn() instead of nationAI.decideNationAction() (line 497)"
    - "decideWandererAction() now uses injury > wandererPolicy.cureThreshold (line 256) instead of injury > 0"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Run a game world with NPC nations over several turns and observe tax rate and gold/rice bill rates applied to NPC nations"
    expected: "Tax rates follow PHP chooseTexRate formula (avg popRate/devScore thresholds 10/15/20/25); gold/rice bill rates use income/outcome*90 clamped formula from chooseGoldBillRate/chooseRiceBillRate"
    why_human: "Runtime path is now GeneralAI.chooseNationTurn() which contains PHP-matching rate choosers, but confirming they execute and produce correct values requires a live game session"
  - test: "Observe NPC nation strategic priority ordering (attack vs defend vs develop) over 5+ turns in mixed war/peace states"
    expected: "NPC nations should prioritize attack/defense/development in the same order as PHP GeneralAI.php for equivalent game states"
    why_human: "Priority ordering parity of chooseNationTurn cannot be confirmed without observing actual NPC behavior in a live session"
---

# Phase 8: NPC AI Parity Verification Report

**Phase Goal:** NPC generals make the same strategic, tactical, and diplomatic decisions as legacy GeneralAI.php given the same game state
**Verified:** 2026-04-02T07:00:00Z
**Status:** human_needed
**Re-verification:** Yes — after gap closure (Plan 08-05)

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | NPC military AI selects same action (attack target, defend city, develop) as legacy PHP for reference game states | VERIFIED | 23 golden value tests in GeneralAITest.kt MilitaryAIParityTests. Policy threshold fixes applied to doSortie/doCombatPrep. |
| 2 | NPC diplomacy AI proposes and responds to same diplomatic actions as legacy PHP | VERIFIED | calcDiplomacyState rewritten to PHP 5-state term model. doDeclaration/doNonAggressionProposal rewritten. 8+ golden value tests in NpcAiParityTest. |
| 3 | NPC recruitment and personnel management decisions match legacy PHP | VERIFIED | 33 tests in PersonnelAIParityTests + WandererAIParityTests + PromotionAIParityTests. 17/18 methods directly tested. |
| 4 | NPC strategic priority ordering (attack vs defend vs develop) matches legacy PHP for early/mid/late game | VERIFIED | TurnService line 497 now calls `generalAI.chooseNationTurn(general, world)`. PHP-matching priority iteration and rate choosers (chooseTexRate, chooseGoldBillRate, chooseRiceBillRate) are now the runtime path. NationAI.decideNationAction() is no longer called. |
| 5 | calcDiplomacyState returns PHP-matching 5-state values | VERIFIED | DiplomacyState.kt: PEACE(0), DECLARED(1), RECRUITING(2), IMMINENT(3), AT_WAR(4). 8+ golden value tests in NpcAiParityTest. |
| 6 | categorizeNationGeneral classifies generals into PHP-matching 7 categories | VERIFIED | GeneralAI.kt line 439. 6 golden value tests in NpcAiParityTest. |
| 7 | Default priority lists match PHP AutorunGeneralPolicy/AutorunNationPolicy | VERIFIED | NpcPolicy.kt DEFAULT_GENERAL_PRIORITY starts with "NPC사망대비" at index 0. DEFAULT_NATION_PRIORITY present. |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/game-app/src/main/kotlin/com/opensam/engine/ai/DiplomacyState.kt` | DiplomacyState enum with PHP-matching 5 coded values | VERIFIED | PEACE(0) through AT_WAR(4) with PHP comments. |
| `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` | Rewritten structural methods, categorizeNationGeneral, do*() methods, cureThreshold injury guard | VERIFIED | 4102+ lines. decideWandererAction line 256 uses `injury > wandererPolicy.cureThreshold`. No `injury > 0` in live code. |
| `backend/game-app/src/main/kotlin/com/opensam/engine/ai/NpcPolicy.kt` | Corrected default priority lists | VERIFIED | DEFAULT_GENERAL_PRIORITY starts with "NPC사망대비". |
| `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` | Calls generalAI.chooseNationTurn() for NPC nation turns | VERIFIED | Line 497: `val aiAction = generalAI.chooseNationTurn(general, world)`. Guard changed from `!= "Nation휴식"` to `!= "휴식"` matching chooseNationTurn return values. |
| `backend/game-app/src/test/kotlin/com/opensam/qa/parity/NpcAiParityTest.kt` | Golden value parity tests for diplomacy, personnel, priorities | VERIFIED | 885 lines. calcDiplomacyState (8 tests), categorizeNationGeneral (6 tests), doDeclaration (3 tests), doNonAggressionProposal (4 tests). |
| `backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt` | Golden value tests for military, domestic, economy, personnel, wanderer, promotion; WandererInjuryThresholdTests | VERIFIED | WandererInjuryThresholdTests inner class at line 2991. 3 injury threshold tests: injury=5 (no 요양), injury=15 (요양), injury=10 (no 요양, strict >). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `GeneralAI.kt::calcDiplomacyState()` | All do*() methods via AIContext.diplomacyState | AIContext construction | VERIFIED | calcDiplomacyState result used in decideAndExecute AIContext construction. |
| `TurnService` | `GeneralAI.chooseNationTurn()` | `generalAI.chooseNationTurn(general, world)` at line 497 | VERIFIED | Previously called NationAI.decideNationAction(). Now calls GeneralAI.chooseNationTurn(). PHP-matching rate choosers are now reachable at runtime. |
| `GeneralAI.kt::decideWandererAction()` | `wandererPolicy.cureThreshold` | `injury > wandererPolicy.cureThreshold` at line 256 | VERIFIED | Replaced old `injury > 0` guard. Uses same NpcNationPolicy lookup pattern as chooseInstantNationTurn. |
| `GeneralAI.kt::doSortie()` | calcWarRoute / mapService adjacency | war target selection | VERIFIED | doSortie references warTargetNations with attackable check. Policy-based thresholds applied. |
| `GeneralAI.kt::doRecruit()` | general.crew / city.pop | recruitment calculation | VERIFIED | Uses crew/train thresholds per PHP. |
| `GeneralAI.kt::doRise()` | SelectNpcTokenService / nation join logic | wanderer nation selection | VERIFIED | Uses npcToken system check. |
| `GeneralAI.kt::choosePromotion()` | officer_ranks.json / officerLevel mapping | rank selection | VERIFIED | Tested with nationLevel 7 and 0. PHP officer_level 12 -> Kotlin officerLevel 20 mapping applied. |
| `TurnService` | `GeneralAI.decideAndExecute()` | `generalAI.decideAndExecute()` call | VERIFIED | TurnService.kt calls generalAI.decideAndExecute(). General-level AI runtime path unchanged. |

### Data-Flow Trace (Level 4)

This phase produces AI decision logic with no data-rendering components. Data-flow trace is not applicable.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| TurnService calls generalAI.chooseNationTurn() | `grep "chooseNationTurn\|decideNationAction" TurnService.kt` | Line 497: `generalAI.chooseNationTurn(general, world)`. decideNationAction absent. | PASS |
| No `injury > 0` in GeneralAI.kt live code | `grep "injury > 0" GeneralAI.kt` | Only in comment at line 3563. Zero live code matches. | PASS |
| decideWandererAction uses cureThreshold | `grep "injury > wandererPolicy.cureThreshold" GeneralAI.kt` | Line 256 confirmed. | PASS |
| WandererInjuryThresholdTests class exists | `grep "WandererInjuryThresholdTests" GeneralAITest.kt` | Line 2991 confirmed. | PASS |
| Commits 061b7b5 and 7e16ac3 exist | git log | Both hashes present per SUMMARY-05 self-check. | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AI-01 | 08-02, 08-03 | Verify NPC military AI decision trees match legacy GeneralAI.php (40+ do*() methods) | SATISFIED | 23 military + 22 domestic golden value tests. PHP divergences fixed in doSortie, doCombatPrep. GeneralAI.decideAndExecute() is runtime path and is PHP-matching. |
| AI-02 | 08-01 | Fix NPC diplomacy AI to match legacy behavior (currently "completely different") | SATISFIED | calcDiplomacyState rewritten to PHP 5-state term model. doDeclaration/doNonAggressionProposal rewritten. 8+ golden value tests pass. |
| AI-03 | 08-01, 08-04 | Verify NPC recruitment/personnel decisions match legacy | SATISFIED | categorizeNationGeneral 7-category classification implemented. 33 personnel/wanderer/promotion tests pass. Wanderer injury threshold fixed to cureThreshold. |
| AI-04 | 08-01, 08-02, 08-03, 08-04 | Verify NPC strategic priorities (attack/defend/develop) match legacy | SATISFIED | TurnService now calls GeneralAI.chooseNationTurn() (PHP-matching priority iteration, rate choosers) at line 497. NationAI.decideNationAction() no longer in the NPC nation decision path. Runtime path now matches PHP. Human verification required to confirm live behavior. |

**Orphaned requirements check:** All AI-01 through AI-04 declared in plan frontmatter and accounted for. No orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `GeneralAI.kt` | 3563 | `// NOT injury > 0` — comment only, not live code | Info | Comment documents the fix. Not a stub or live divergence. |

No remaining blocker or warning anti-patterns. All previous anti-patterns (NationAI.adjustTaxAndBill runtime path, injury > 0 wanderer guard) resolved in Plan 08-05.

### Human Verification Required

#### 1. NPC Nation Economy Rate Verification

**Test:** Start a game world with NPC nations, advance several turns, and observe the tax rate and gold/rice bill rates applied to NPC nations.
**Expected:** Tax rates should follow PHP's `chooseTexRate` formula (based on avg popRate/devScore thresholds: 10/15/20/25). Gold/rice bill rates should use the income/outcome*90 clamped formula from `chooseGoldBillRate`/`chooseRiceBillRate`.
**Why human:** Although the runtime path is now correctly wired to `GeneralAI.chooseNationTurn()`, verifying that the rate choosers produce correct values for realistic game states requires a live game session. The PHP-matching formulas are exercised by unit tests with synthetic fixtures, but end-to-end economy behavior in a running world has not been observed.

#### 2. NPC Nation Strategic Priority (Attack vs Defend vs Develop) at Runtime

**Test:** Observe NPC nation decisions over 5+ turns in a game with both war and peace states.
**Expected:** NPC nations should prioritize attack/defense/development in the same order as PHP GeneralAI.php for equivalent game states.
**Why human:** `GeneralAI.chooseNationTurn()` priority loop is now the runtime path, but confirming its output matches legacy PHP decision ordering in a live world (with real entities, real distances, real resource levels) requires direct observation.

### Gaps Summary

Both gaps from the initial verification are closed.

**Gap 1 (BLOCKING) — closed:** TurnService.kt line 497 now calls `generalAI.chooseNationTurn(general, world)`. The non-PHP-matching `NationAI.decideNationAction()` is no longer in the NPC nation decision path. The PHP-matching rate choosers (`chooseTexRate`, `chooseGoldBillRate`, `chooseRiceBillRate`) inside `chooseNationTurn` are now reachable at runtime. The guard string was also corrected from `"Nation휴식"` to `"휴식"` to match `chooseNationTurn` return values.

**Gap 2 (MINOR) — closed:** `decideWandererAction()` in GeneralAI.kt line 256 now uses `injury > wandererPolicy.cureThreshold` (default 10). The `injury > 0` divergence is gone from all live code paths. Three regression tests in `WandererInjuryThresholdTests` (injury=5, injury=15, injury=10) cover the boundary conditions including the strict `>` comparison.

All 7 must-haves are now verified at the code level. Two human verification items remain to confirm live runtime behavior of the newly wired nation turn path.

---

_Verified: 2026-04-02T07:00:00Z_
_Verifier: Claude (gsd-verifier)_
