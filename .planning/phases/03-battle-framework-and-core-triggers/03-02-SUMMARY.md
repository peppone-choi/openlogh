---
phase: 03-battle-framework-and-core-triggers
plan: 02
subsystem: engine
tags: [battle, trigger, war-unit, intimidation, sniping, healing, rage, kotlin]

# Dependency graph
requires:
  - phase: 03-battle-framework-and-core-triggers
    plan: 01
    provides: WarUnitTrigger interface, WarUnitTriggerRegistry, BattleEngine integration
provides:
  - IntimidationTrigger (che_위압) with 0.4 probability, intimidation flags, atmos reduction
  - SnipingTrigger (che_저격) with 0.5 probability on new opponent, wound 20-40, morale +20
  - BattleHealTrigger (che_의술) with 0.4 probability, damage reduction 30%, injury clear
  - RageTrigger (che_격노) with critical/dodge reaction, warPower = 1 + 0.2 * count, extra phase chance
  - Che위압Trigger, Che저격Trigger, Che의술Trigger, Che격노Trigger made no-op
affects: [03-03, 05-modifiers]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "WarUnitTrigger self-registration via init block calling WarUnitTriggerRegistry.register(this)"
    - "Fixed-seed RNG test pattern with findSeed() helper for probabilistic trigger testing"
    - "No-op Che*Trigger pattern: keep object in BattleTriggerRegistry but empty all method bodies"

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/IntimidationTrigger.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/SnipingTrigger.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/BattleHealTrigger.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/RageTrigger.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/IntimidationTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/SnipingTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/BattleHealTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/RageTriggerTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTrigger.kt

key-decisions:
  - "RageTrigger onPreAttack uses legacy TODO formula (1 + 0.2 * count) instead of existing Che격노Trigger rollCriticalDamageMultiplier() -- TODO parameters are closer to legacy PHP intent"
  - "Che*Trigger objects kept in BattleTriggerRegistry as no-ops rather than removed -- prevents breaking code that checks registry presence"

patterns-established:
  - "trigger/ package under engine/war/ for WarUnitTrigger implementations"
  - "Self-registration pattern: object init {} block calls WarUnitTriggerRegistry.register(this)"

requirements-completed: [BATTLE-05, BATTLE-06, BATTLE-09, BATTLE-10]

# Metrics
duration: 7min
completed: 2026-04-01
---

# Phase 3 Plan 2: Core Battle Triggers Summary

**Four core battle triggers (intimidation, sniping, healing, rage) implemented as WarUnitTrigger with legacy-parity probabilistic behavior and existing Che*Triggers made no-op**

## Performance

- **Duration:** 7 min
- **Started:** 2026-04-01T04:57:25Z
- **Completed:** 2026-04-01T05:05:22Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- Created IntimidationTrigger with 0.4 probability per engagement, setting intimidated/dodgeDisabled/criticalDisabled/magicDisabled flags, reducing defender atmos by 5
- Created SnipingTrigger with 0.5 probability on new opponent, wound amount 20-40, +20 morale boost
- Created BattleHealTrigger with 0.4 probability per phase, reducing damage by 30% (floor(damage*0.7)), clearing attacker injury
- Created RageTrigger reacting to opponent critical (50%) and dodge (25%), accumulating warPower = 1 + 0.2 * activationCount, 50% chance of extra phase per activation
- Made Che위압Trigger, Che저격Trigger, Che의술Trigger, Che격노Trigger into no-ops to prevent double-firing
- All four triggers self-register in WarUnitTriggerRegistry via init blocks

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement IntimidationTrigger and SnipingTrigger with tests** - `b086aeb` (feat)
2. **Task 2: Implement BattleHealTrigger and RageTrigger with tests** - `1eb0133` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/IntimidationTrigger.kt` - che_위압 WarUnitTrigger with 0.4 prob
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/SnipingTrigger.kt` - che_저격 WarUnitTrigger with 0.5 prob, 20-40 wound
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/BattleHealTrigger.kt` - che_의술 WarUnitTrigger with 0.4 prob, damage*0.7
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/RageTrigger.kt` - che_격노 WarUnitTrigger with accumulating warPower
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTrigger.kt` - Che위압/저격/의술/격노Trigger made no-op
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/IntimidationTriggerTest.kt` - 9 tests for IntimidationTrigger
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/SnipingTriggerTest.kt` - 9 tests for SnipingTrigger
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/BattleHealTriggerTest.kt` - 8 tests for BattleHealTrigger
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/RageTriggerTest.kt` - 11 tests for RageTrigger

## Decisions Made
- RageTrigger onPreAttack uses the legacy TODO formula `1 + 0.2 * count` instead of the existing Che격노Trigger approach (rollCriticalDamageMultiplier rng range 1.3-2.0). The TODO parameters from SpecialModifiers.kt are based on legacy PHP analysis and represent the intended parity behavior.
- Che*Trigger objects kept in BattleTriggerRegistry as no-ops rather than removed, to avoid breaking code that checks `BattleTriggerRegistry.get("che_*") != null`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all four triggers are fully implemented with complete probabilistic behavior matching legacy parameters.

## Next Phase Readiness
- All four core battle triggers are operational and registered in WarUnitTriggerRegistry
- BattleEngine calls WarUnitTrigger hooks at onEngagementStart, onPreAttack, onPostDamage, onPostRound (set up in Plan 01)
- Plan 03 (battle experience parity) can proceed independently
- Existing BattleTriggerTest suite passes with 0 regressions (Che*Trigger no-ops do not affect simple trigger tests)

## Self-Check: PASSED

- All 9 key files exist on disk (4 trigger implementations + 4 test files + 1 modified BattleTrigger.kt)
- Commit b086aeb (Task 1) verified in git log
- Commit 1eb0133 (Task 2) verified in git log
- Full trigger test suite passes with 0 failures

---
*Phase: 03-battle-framework-and-core-triggers*
*Completed: 2026-04-01*
