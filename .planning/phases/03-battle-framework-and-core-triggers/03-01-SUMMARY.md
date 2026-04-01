---
phase: 03-battle-framework-and-core-triggers
plan: 01
subsystem: engine
tags: [battle, trigger, war-unit, modifier, killnum, kotlin]

# Dependency graph
requires:
  - phase: 02-numeric-parity
    provides: coerceIn guards on Short fields, deterministic RNG
provides:
  - WarUnitTrigger interface with 4 hook methods (onEngagementStart, onPreAttack, onPostDamage, onPostRound)
  - WarUnitTriggerRegistry for trigger registration and lookup
  - BattleEngine integration with WarUnitTrigger hook calls in resolveBattle and resolveBattleWithPhases
  - StatContext.killnum field populated from general.meta rank data
  - Fixed che_무쌍 modifier reading killnum from StatContext instead of hardcoded 0.0
affects: [03-02, 03-03, 05-modifiers]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "WarUnitTrigger: separate interface from BattleTrigger for coarser phase-level hooks"
    - "WarUnitTriggerRegistry: mutable map singleton with register/get/allCodes (Plan 02 will register implementations)"
    - "StatContext field population: read from general.meta nested map in BattleService.applyWarModifiers"

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnitTrigger.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/war/WarUnitTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/opensam/engine/modifier/MusangKillnumTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleEngine.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ActionModifier.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt
    - backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleService.kt

key-decisions:
  - "WarUnitTrigger uses mutable registry (register method) rather than immutable list -- Plan 02 triggers will self-register"
  - "killnum populated at BattleService.applyWarModifiers call site since that is the only StatContext construction with general.meta access"

patterns-established:
  - "WarUnitTrigger hook pattern: onEngagementStart/onPreAttack/onPostDamage/onPostRound alongside existing BattleTrigger hooks"
  - "StatContext nested meta field access: (general.meta[key] as? Map<*, *>)?.get(field) as? Number"

requirements-completed: [BATTLE-01, BATTLE-12]

# Metrics
duration: 19min
completed: 2026-04-01
---

# Phase 3 Plan 1: Battle Framework and Core Triggers Summary

**WarUnitTrigger framework interface with 4 battle-phase hooks integrated into BattleEngine, plus killnum fix for che_무쌍 modifier**

## Performance

- **Duration:** 19 min
- **Started:** 2026-04-01T04:34:31Z
- **Completed:** 2026-04-01T04:53:45Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Created WarUnitTrigger interface separate from BattleTrigger with onEngagementStart, onPreAttack, onPostDamage, onPostRound hooks
- Integrated WarUnitTrigger hook calls into both resolveBattle() and resolveBattleWithPhases() in BattleEngine
- Added killnum field to StatContext and fixed che_무쌍 hardcoded killnum=0.0 to read from stat.killnum
- Populated killnum from general.meta["rank"]["killnum"] at BattleService.applyWarModifiers construction site

## Task Commits

Each task was committed atomically:

1. **Task 1: Create WarUnitTrigger interface, registry, and integrate into BattleEngine** - `10abf15` (feat)
2. **Task 2: Add killnum to StatContext and fix che_무쌍 modifier** - `fb5c11c` (fix)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnitTrigger.kt` - WarUnitTrigger interface and WarUnitTriggerRegistry
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleEngine.kt` - Added collectWarUnitTriggers and hook calls in battle loop
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ActionModifier.kt` - Added killnum field to StatContext
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt` - Fixed che_무쌍 to read stat.killnum
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleService.kt` - Populate killnum from general.meta in applyWarModifiers
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/WarUnitTriggerTest.kt` - 6 tests for framework verification
- `backend/game-app/src/test/kotlin/com/opensam/engine/modifier/MusangKillnumTest.kt` - 8 tests for killnum scaling

## Decisions Made
- WarUnitTriggerRegistry uses mutable map with register() method (rather than immutable list like BattleTriggerRegistry) because Plan 02 triggers need to register themselves at runtime
- killnum populated at BattleService.applyWarModifiers since that is the only StatContext construction site with access to general.meta; other StatContext usages (GeneralTrigger) use default 0.0 which is correct for non-battle contexts

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - WarUnitTriggerRegistry is intentionally empty (no registered implementations). Plan 02 will add the four trigger implementations (intimidation, sniping, healing, rage).

## Next Phase Readiness
- WarUnitTrigger framework is operational and ready for Plan 02 trigger implementations
- Hook call points in BattleEngine are in place at onEngagementStart, onPostDamage, and onPostRound
- StatContext.killnum is wired end-to-end from general.meta through modifier pipeline
- All 49 existing BattleTriggerRegistry triggers still resolve correctly (full test suite green)

## Self-Check: PASSED

- All 4 key files exist on disk
- Commit 10abf15 (Task 1) verified in git log
- Commit fb5c11c (Task 2) verified in git log
- Full test suite passes with 0 failures

---
*Phase: 03-battle-framework-and-core-triggers*
*Completed: 2026-04-01*
