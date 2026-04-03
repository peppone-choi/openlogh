---
phase: 01-deterministic-foundation
verified: 2026-03-31T14:10:00Z
status: passed
score: 8/8 must-haves verified
re_verification: false
---

# Phase 01: Deterministic Foundation Verification Report

**Phase Goal:** All game execution is deterministic, observable, and correctly ordered -- enabling reliable parity verification for all subsequent phases
**Verified:** 2026-03-31T14:10:00Z
**Status:** passed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                             | Status     | Evidence                                                                                 |
|----|---------------------------------------------------------------------------------------------------|------------|------------------------------------------------------------------------------------------|
| 1  | No java.util.Random usage remains in game engine code                                             | VERIFIED   | grep returns 0 matches in engine source                                                  |
| 2  | LiteHashDRBG sequential draw output matches PHP SHA-512 RNG for same seed over 100+ draws        | VERIFIED   | LiteHashDRBGTest.kt: 100-draw golden array hardcoded, assertArrayEquals passes           |
| 3  | RandUtil.choice() on single-element list advances RNG state to match PHP array_rand behavior      | VERIFIED   | RandUtil.kt line 85: `if (items.size == 1)` guard calls `rng.nextLegacyInt(1L)`          |
| 4  | Running same turn with same seed produces identical RNG sequences every time                      | VERIFIED   | `mixed nextLegacyInt and nextFloat1 draws are deterministic` test; CityHealTrigger tests |
| 5  | CityHealTrigger accepts injected kotlin.random.Random and constructor injection is verified       | VERIFIED   | GeneralTrigger.kt: `rng: Random` (no default); 2 injection tests in GeneralTriggerTest   |
| 6  | Every exception catch block in engine code logs with context instead of silently swallowing it    | VERIFIED   | grep `catch (_:` returns 0; all 16 blocks confirmed `catch (e:` + log.warn               |
| 7  | Entity processing order within a turn tick is deterministic regardless of DB query order          | VERIFIED   | TurnService.kt line 425: `.sortedWith(compareBy<...> { it.turnTime }.thenBy { it.id })`  |
| 8  | Two generals with identical turnTime are always processed in same order (by ID)                   | VERIFIED   | InMemoryTurnProcessor.kt line 211: `compareBy<GeneralSnapshot> { it.turnTime }.thenBy { it.id }` |

**Score:** 8/8 truths verified

### Required Artifacts

| Artifact                                                                                          | Expected                                          | Status     | Details                                                          |
|---------------------------------------------------------------------------------------------------|---------------------------------------------------|------------|------------------------------------------------------------------|
| `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt`                             | DeterministicRng.create in registerAuction()      | VERIFIED   | Line 1054-1056: RandUtil(DeterministicRng.create(...) as LiteHashDRBG) |
| `backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt`                  | LiteHashDRBG-based CityHealTrigger                | VERIFIED   | Line 265: `rng: Random` (no default); import kotlin.random.Random |
| `backend/game-app/src/main/kotlin/com/opensam/engine/RandUtil.kt`                                | Single-element choice() with RNG state consumption | VERIFIED   | Lines 85-89: `if (items.size == 1)` + `rng.nextLegacyInt(1L)`   |
| `backend/game-app/src/test/kotlin/com/opensam/engine/LiteHashDRBGTest.kt`                        | 100+ sequential draw parity test with golden values | VERIFIED  | 8 test methods; GOLDEN_100_DRAWS hardcoded longArrayOf(100 values) |
| `backend/game-app/src/test/kotlin/com/opensam/engine/RandUtilTest.kt`                            | Single-element choice behavior test               | VERIFIED   | Lines 160-194: two `single-element` named tests                  |
| `backend/game-app/src/test/kotlin/com/opensam/engine/trigger/GeneralTriggerTest.kt`              | CityHealTrigger constructor injection test        | VERIFIED   | Lines 124-165: two CityHealTrigger tests                         |
| `backend/game-app/src/main/kotlin/com/opensam/engine/turn/cqrs/memory/InMemoryTurnProcessor.kt` | Deterministic sort tiebreaker                     | VERIFIED   | Line 211: `compareBy<GeneralSnapshot> { it.turnTime }.thenBy { it.id }` |
| `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/InheritBuffModifier.kt`            | Logger declaration + logged catch block           | VERIFIED   | Line 61: `LoggerFactory.getLogger(InheritBuffModifier::class.java)`; line 84: log.warn |
| `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarAftermath.kt`                        | Logger declaration + logged catch block           | VERIFIED   | Line 107: `LoggerFactory.getLogger(WarAftermath::class.java)`; line 459: log.warn |

### Key Link Verification

| From                                        | To                           | Via                                              | Status  | Details                                                                    |
|---------------------------------------------|------------------------------|--------------------------------------------------|---------|----------------------------------------------------------------------------|
| `TurnService.registerAuction()`             | `DeterministicRng.create()`  | RandUtil wrapping LiteHashDRBG                   | WIRED   | Line 1053-1056: hiddenSeed + DeterministicRng.create cast to LiteHashDRBG  |
| `buildPreTurnTriggers()`                    | `CityHealTrigger constructor`| rng parameter passed through                     | WIRED   | Line 261-272: rng: Random parameter, line 271: CityHealTrigger(general, cityMates, rng) |
| `RealtimeService.firePreTurnTriggers()`     | `buildPreTurnTriggers()`     | world-scoped preTurnRng injected                 | WIRED   | Line 351: `buildPreTurnTriggers(general, modifiers, rng = preTurnRng)`     |
| `TurnService.executeGeneralCommandsUntil()` | entity iteration             | compareBy<General> { turnTime }.thenBy { id }    | WIRED   | Line 425: `.sortedWith(compareBy<com.opensam.entity.General> { it.turnTime }.thenBy { it.id })` |
| `InMemoryTurnProcessor.executeGeneralCommandsUntil()` | entity iteration | compareBy<GeneralSnapshot> { turnTime }.thenBy { id } | WIRED | Line 211: confirmed                                                  |

### Data-Flow Trace (Level 4)

Not applicable -- this phase produces no data-rendering components. All artifacts are engine internals (RNG, sorting, logging) with no user-visible data output path to trace.

### Behavioral Spot-Checks

| Behavior                                              | Check                                                       | Result                                              | Status |
|-------------------------------------------------------|-------------------------------------------------------------|-----------------------------------------------------|--------|
| No java.util.Random in engine source                  | grep count returns 0                                        | 0 matches                                           | PASS   |
| No silent catch blocks remain                         | grep `catch (_:` count returns 0                            | 0 matches                                           | PASS   |
| No single-field turnTime sorts remain                 | grep `sortedBy { it.turnTime }` count returns 0             | 0 matches                                           | PASS   |
| All 5 plan-01 commits present in git log              | git log grep for hashes 0062588, bc56b2c, 0546f3f           | All 3 found                                         | PASS   |
| All 2 plan-02 commits present in git log              | git log grep for hashes 9e30cc2, 8752adc                    | Both found                                          | PASS   |
| LiteHashDRBGTest has 8 test methods                   | grep `fun \`` count                                         | 8 methods (3 pre-existing + 5 new)                  | PASS   |
| RandUtil single-element guard for List and Map        | grep `items.size == 1` and `keys.size == 1` in RandUtil.kt  | Both guards present at lines 85 and 100             | PASS   |

### Requirements Coverage

| Requirement | Source Plan | Description                                                                           | Status    | Evidence                                                        |
|-------------|------------|--------------------------------------------------------------------------------------|-----------|------------------------------------------------------------------|
| FOUND-01    | 01-01      | Replace java.util.Random with LiteHashDRBG in TurnService.registerAuction() and GeneralTrigger | SATISFIED | TurnService line 1054; GeneralTrigger CityHealTrigger uses kotlin.random.Random |
| FOUND-02    | 01-01      | Verify LiteHashDRBG cross-language parity (Kotlin SHA-512 output matches PHP)        | SATISFIED | LiteHashDRBGTest.kt: 100-draw hardcoded golden values + PHP vector tests |
| FOUND-03    | 01-02      | Add logging to all exception-swallowing catch blocks in engine code                  | SATISFIED | grep `catch (_:` returns 0; all 16 blocks now use `catch (e:` + log.warn |
| FOUND-04    | 01-02      | Add turn ordering tiebreakers to prevent non-deterministic entity processing order   | SATISFIED | TurnService line 425 and InMemoryTurnProcessor line 211 both use .thenBy { it.id } |
| FOUND-05    | 01-01      | Fix RandUtil.choice() single-element bias (PHP array_rand vs Kotlin behavior)        | SATISFIED | RandUtil.kt lines 85-89 and 100-103: single-element guards consuming 1 RNG draw |

No orphaned requirements -- all 5 FOUND-0x IDs declared in plan frontmatter match REQUIREMENTS.md Phase 1 mapping.

### Anti-Patterns Found

| File                   | Line(s)    | Pattern                              | Severity | Impact                                                            |
|------------------------|------------|--------------------------------------|----------|-------------------------------------------------------------------|
| `TurnService.kt`       | 995-1016   | TODO comments (online/overhead/wander) | Info   | Pre-existing legacy stubs for features not in scope of this phase; no impact on determinism goal |

No blockers or warnings. All TODOs in TurnService.kt are for unrelated legacy features (updateOnline, CheckOverhead, checkWander) that were present before this phase and are outside its scope.

### Human Verification Required

None -- all behaviors in this phase are programmatically verifiable (RNG seeding, sorting, logging). No visual, real-time, or external service behaviors introduced.

### Gaps Summary

No gaps. All 8 observable truths are verified, all artifacts exist and are substantive and wired, all key links are confirmed, all 5 requirement IDs are satisfied, no blocker anti-patterns found.

---

_Verified: 2026-03-31T14:10:00Z_
_Verifier: Claude (gsd-verifier)_
