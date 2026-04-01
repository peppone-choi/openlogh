---
phase: 04-battle-completion
plan: 01
subsystem: engine
tags: [kotlin, battle-triggers, war-engine, legacy-parity]

# Dependency graph
requires:
  - phase: 03-battle-framework-and-core-triggers
    provides: WarUnitTrigger interface, WarUnitTriggerRegistry, BattleTriggerContext fields, BattleEngine phase loop
provides:
  - CounterStrategyTrigger (che_반계) WarUnitTrigger with phase-level counter-strategy logging
  - SustainedChargeTrigger (che_돌격지속) WarUnitTrigger extending phases via bonusPhases
  - InjuryNullificationTrigger (che_부상무효) WarUnitTrigger setting injuryImmune
  - UnavoidableCriticalTrigger (che_필살강화_회피불가) WarUnitTrigger disabling dodge
  - BattleEngine phase loop extension via bonusPhases + rageExtraPhases consumption
  - CityHealTrigger verification (9 tests)
affects: [05-modifier-pipeline, war-resolution-parity]

# Tech tracking
tech-stack:
  added: []
  patterns: [WarUnitTrigger singleton object self-registration pattern]

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/CounterStrategyTrigger.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/SustainedChargeTrigger.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/InjuryNullificationTrigger.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/UnavoidableCriticalTrigger.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/CounterStrategyTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/SustainedChargeTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/InjuryNullificationTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/UnavoidableCriticalTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/CityHealTriggerTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleEngine.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt

key-decisions:
  - "CounterStrategyTrigger logs phase-level attempt/activation only; Che반계Trigger BattleTrigger continues to handle magic reflection via onPreMagic (dual registry, no conflict)"
  - "InjuryNullificationTrigger and Che견고Trigger both set injuryImmune=true -- idempotent, both fire safely"
  - "BattleEngine phase loop uses var maxPhase with += for bonusPhases/rageExtraPhases instead of fixed val"

patterns-established:
  - "WarUnitTrigger + BattleTrigger dual registry: same special can have both a WarUnitTrigger (phase-level hooks) and a BattleTrigger (roll-level hooks) without conflict"

requirements-completed: [BATTLE-02, BATTLE-03, BATTLE-04, BATTLE-07, BATTLE-08]

# Metrics
duration: 9min
completed: 2026-04-01
---

# Phase 04 Plan 01: Battle Completion Triggers Summary

**4 WarUnitTriggers (반계/돌격지속/부상무효/필살강화_회피불가) implemented with BattleEngine phase loop fix consuming bonusPhases/rageExtraPhases, plus CityHealTrigger verification**

## Performance

- **Duration:** 9 min
- **Started:** 2026-04-01T07:10:02Z
- **Completed:** 2026-04-01T07:19:02Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- Implemented 4 remaining WarUnitTriggers completing the battle trigger system (8 total triggers now registered)
- Fixed BattleEngine to consume bonusPhases and rageExtraPhases, enabling sustained charge and rage to actually extend battle phases
- Verified CityHealTrigger with 9 dedicated tests covering self-heal, probability, nation filtering, and buildPreTurnTriggers wiring
- Removed all 5 Phase 4 TODO comments from SpecialModifiers.kt

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement 4 WarUnitTriggers and fix BattleEngine phase loop**
   - `85b9546` (test: add failing tests for 4 WarUnitTriggers - TDD RED)
   - `eea3043` (feat: implement triggers, fix phase loop, clean TODOs - TDD GREEN)
2. **Task 2: Verify CityHealTrigger with dedicated test** - `77cf86e` (test)

## Files Created/Modified
- `trigger/CounterStrategyTrigger.kt` - che_반계 WarUnitTrigger: phase-level logging on onPreAttack (40% probability)
- `trigger/SustainedChargeTrigger.kt` - che_돌격지속 WarUnitTrigger: +1 bonusPhases on onPostDamage when winning (40% probability)
- `trigger/InjuryNullificationTrigger.kt` - che_부상무효 WarUnitTrigger: injuryImmune on onEngagementStart
- `trigger/UnavoidableCriticalTrigger.kt` - che_필살강화_회피불가 WarUnitTrigger: dodgeDisabled on onPreAttack (always active)
- `BattleEngine.kt` - Phase loop uses var maxPhase, consumes bonusPhases + rageExtraPhases after onPostDamage
- `SpecialModifiers.kt` - Removed 5 Phase 4 TODO comments (반계시도, 돌격지속, 부상무효, 필살강화_회피불가, 도시치료/전투치료)
- `CounterStrategyTriggerTest.kt` - 5 tests for counter-strategy trigger
- `SustainedChargeTriggerTest.kt` - 5 tests for sustained charge trigger
- `InjuryNullificationTriggerTest.kt` - 5 tests for injury nullification trigger
- `UnavoidableCriticalTriggerTest.kt` - 5 tests for unavoidable critical trigger
- `CityHealTriggerTest.kt` - 9 tests for city heal trigger verification

## Decisions Made
- CounterStrategyTrigger logs phase-level attempt/activation only; Che반계Trigger BattleTrigger continues to handle magic reflection via onPreMagic (dual registry, no conflict)
- InjuryNullificationTrigger and Che견고Trigger both set injuryImmune=true -- idempotent, both fire safely
- BattleEngine phase loop uses `var maxPhase` with `+=` for bonusPhases/rageExtraPhases instead of fixed `val`
- Also removed `criticalDamageRange` TODO from che_필살 as it is a separate concern covered by existing onPostCritical

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed CounterStrategyTriggerTest Che반계Trigger assertion**
- **Found during:** Task 1 (test verification)
- **Issue:** Test called `Che반계Trigger.onPostMagic` expecting it to set `magicReflected=true`, but reflection happens in `onPreMagic` (onPostMagic only applies damage multiplier when already reflected)
- **Fix:** Changed test to call `onPreMagic` with `magicChanceBonus=0.5` to simulate opponent magic
- **Files modified:** CounterStrategyTriggerTest.kt
- **Verification:** Test passes

---

**Total deviations:** 1 auto-fixed (1 bug in test)
**Impact on plan:** Test correction only, no scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 8 WarUnitTriggers now registered and operational (Phase 3: 4 triggers + Phase 4: 4 triggers)
- BattleEngine phase loop properly extends for sustained charge and rage triggers
- Ready for Phase 05 (Modifier Pipeline) which depends on Phase 3 triggers

## Self-Check: PASSED

All 9 created files verified present. All 3 commit hashes (85b9546, eea3043, 77cf86e) verified in git log.

---
*Phase: 04-battle-completion*
*Completed: 2026-04-01*
