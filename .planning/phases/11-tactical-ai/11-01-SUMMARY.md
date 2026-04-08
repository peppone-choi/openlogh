---
phase: 11-tactical-ai
plan: 01
subsystem: ai
tags: [kotlin, tactical-ai, threat-assessment, personality, pure-object]

requires:
  - phase: 08-command-buffer
    provides: TacticalUnit, CommandHierarchy, Formation, BattleSide types
  - phase: 09-command-hierarchy
    provides: CommandHierarchyService, OutOfCrcBehavior patterns
provides:
  - MissionObjective enum (CONQUEST, DEFENSE, SWEEP)
  - TacticalPersonalityConfig with 5 personality tactical profiles
  - TacticalAIContext data class for AI decision snapshots
  - ThreatAssessor with scoring, ranking, retreat check, high-threat detection
affects: [11-02-mission-behaviors, 11-03-engine-integration]

tech-stack:
  added: []
  patterns: [pure-object-no-spring-di, personality-dispatch-via-when, threat-scoring-formula]

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/MissionObjective.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/TacticalPersonalityConfig.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/TacticalAIContext.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/ai/ThreatAssessor.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/TacticalAIDataModelTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/ai/ThreatAssessorTest.kt
  modified: []

key-decisions:
  - "ThreatAssessor scoring formula: HP*40 + ships*20 + proximity*25 + attack*15 (0-100 scale)"
  - "High threat threshold at 60.0 for CONQUEST engage/bypass decisions"
  - "Pure object pattern (no Spring DI) consistent with UtilityScorer and OutOfCrcBehavior"

patterns-established:
  - "TacticalTacticsProfile: personality-specific tactical parameters resolved via when() dispatch"
  - "TacticalAIContext: immutable snapshot pattern for AI decision input"

requirements-completed: [TAI-02, TAI-03]

duration: 5min
completed: 2026-04-08
---

# Phase 11 Plan 01: Tactical AI Data Model Summary

**Tactical AI foundation with mission objectives, personality-specific retreat/formation profiles, threat scoring (HP/distance/ships/attack), and retreat condition logic per D-05 thresholds**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-08T03:33:22Z
- **Completed:** 2026-04-08T03:38:13Z
- **Tasks:** 2
- **Files created:** 6

## Accomplishments
- MissionObjective enum with CONQUEST/DEFENSE/SWEEP and Korean display names
- TacticalPersonalityConfig with 5 profiles: retreat HP thresholds (AGGRESSIVE=0.10, CAUTIOUS=0.30, others=0.20), morale thresholds, engagement ranges, formation preferences per D-05/D-09
- TacticalAIContext data class capturing unit, allies, enemies, mission, personality, profile, hierarchy, anchor point, and map bounds
- ThreatAssessor with scoreThreat (0-100 formula), rankThreats (sorted, filters retreating), shouldRetreat (personality-specific), isHighThreat (>60 threshold)

## Task Commits

Each task was committed atomically:

1. **Task 1: Data model -- MissionObjective + TacticalPersonalityConfig + TacticalAIContext** - `4df85667` (feat)
2. **Task 2: ThreatAssessor -- threat scoring + retreat condition check** - `cc1facf0` (feat)

## Files Created/Modified
- `engine/tactical/ai/MissionObjective.kt` - Mission objective enum (CONQUEST, DEFENSE, SWEEP)
- `engine/tactical/ai/TacticalPersonalityConfig.kt` - Per-personality tactical profiles with retreat thresholds
- `engine/tactical/ai/TacticalAIContext.kt` - Immutable AI decision context snapshot
- `engine/tactical/ai/ThreatAssessor.kt` - Threat scoring, ranking, retreat check, high-threat detection
- `test/.../TacticalAIDataModelTest.kt` - 9 tests for data model contracts
- `test/.../ThreatAssessorTest.kt` - 11 tests for threat assessor behavior

## Decisions Made
- ThreatAssessor scoring formula: HP ratio * 40 + ship ratio * 20 + proximity * 25 + attack/100 * 15 (0-100 scale)
- High threat threshold set at 60.0 for CONQUEST AI engage/bypass decisions per D-01
- Pure object pattern (no Spring DI) consistent with UtilityScorer and OutOfCrcBehavior

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All contracts ready for Plan 02 (mission behaviors): MissionObjective, TacticalAIContext, ThreatAssessor
- Plan 03 (engine integration) can consume TacticalPersonalityConfig.forTrait() and ThreatAssessor.shouldRetreat()
- No blockers

---
*Phase: 11-tactical-ai*
*Completed: 2026-04-08*
