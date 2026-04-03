---
phase: 08-npc-ai-parity
plan: 01
subsystem: ai
tags: [npc-ai, diplomacy-state, priority-list, general-classification, legacy-parity]

requires:
  - phase: 07-nation-command-parity
    provides: "Nation command system and command constraint framework"
provides:
  - "PHP-matching 5-state calcDiplomacyState with term-based transitions"
  - "categorizeNationGeneral 7-category classification matching PHP"
  - "PHP-matching default priority lists for general and nation actions"
  - "Corrected chooseGeneralTurn/chooseNationTurn entry flow matching PHP branch order"
  - "PHP-matching doDeclaration with frontCities guard fix and categorizeNationGeneral"
  - "PHP-matching doNonAggressionProposal with assistance-based logic"
affects: [08-02-PLAN, 08-03-PLAN, 08-04-PLAN]

tech-stack:
  added: []
  patterns:
    - "CalcDiplomacyResult data class for carrying diplomacy context"
    - "NationGeneralCategories data class for 7-way general classification"
    - "DiplomacyState enum with PHP-matching integer codes"

key-files:
  created: []
  modified:
    - "backend/game-app/src/main/kotlin/com/opensam/engine/ai/DiplomacyState.kt"
    - "backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt"
    - "backend/game-app/src/main/kotlin/com/opensam/engine/ai/NpcPolicy.kt"
    - "backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt"
    - "backend/game-app/src/test/kotlin/com/opensam/qa/parity/NpcAiParityTest.kt"

key-decisions:
  - "DiplomacyState enum gains code: Int field matching PHP d constants (0-4)"
  - "calcDiplomacyState uses PHP term-based transitions (term > 8 -> DECLARED, > 5 -> RECRUITING, else -> IMMINENT)"
  - "Existing tests updated from stateCode 선전포고 to 전쟁 for active war scenarios (PHP state=0 vs state=1)"
  - "RNG seed context unified to GeneralAI for both chooseGeneralTurn and chooseNationTurn (PHP Pitfall 5)"
  - "Injury check uses cureThreshold (default 10) across all code paths, not > 0"
  - "doDeclaration frontCities guard reversed to match PHP (return null when non-empty)"
  - "doNonAggressionProposal rewritten to use recv_assist/resp_assist assistance tracking"

patterns-established:
  - "CalcDiplomacyResult pattern: diplomacy methods return rich result with attackable/warTargetNation context"
  - "categorizeNationGeneral pattern: centralized general classification for all nation-level AI decisions"

requirements-completed: [AI-02, AI-03, AI-04]

duration: 25min
completed: 2026-04-02
---

# Phase 08 Plan 01: NPC AI Structural Divergences Summary

**Rewrite calcDiplomacyState to PHP 5-state term model, add categorizeNationGeneral, fix priority lists, correct chooseGeneralTurn/chooseNationTurn entry flow, rewrite do선전포고/do불가침제의**

## Performance

- **Duration:** 25 min
- **Started:** 2026-04-02T04:46:26Z
- **Completed:** 2026-04-02T05:11:12Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Rewrote DiplomacyState enum with PHP-matching code values (0-4) and calcDiplomacyState with term-based state transitions
- Added categorizeNationGeneral method matching PHP 7-category classification (npcWar/npcCivil/userWar/userCivil/troopLeaders/lost/chief)
- Fixed DEFAULT_GENERAL_PRIORITY and DEFAULT_NATION_PRIORITY to match PHP AutorunGeneralPolicy/AutorunNationPolicy ordering
- Fixed chooseGeneralTurn: do선양 before npcType==5, injury threshold uses cureThreshold, structured lord-without-capital flow
- Fixed RNG seed contexts to "GeneralAI" in both chooseGeneralTurn and chooseNationTurn
- Rewrote doDeclaration with reversed frontCities guard and categorizeNationGeneral resource calculation
- Rewrote doNonAggressionProposal to use PHP recv_assist/resp_assist assistance tracking
- All 108 tests pass (26 new golden value and branch-point tests added)

## Task Commits

1. **Task 1: Rewrite calcDiplomacyState + categorizeNationGeneral + priority lists** - `ba45ade` (feat)
2. **Task 2: Rewrite do선전포고 + do불가침제의 + fix chooseGeneralTurn/chooseNationTurn entry flow** - `f59663f` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/DiplomacyState.kt` - DiplomacyState enum with PHP code values
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/GeneralAI.kt` - Rewritten calcDiplomacyState, categorizeNationGeneral, doDeclaration, doNonAggressionProposal, chooseGeneralTurn/chooseNationTurn
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/NpcPolicy.kt` - Fixed priority lists and enabled actions
- `backend/game-app/src/test/kotlin/com/opensam/engine/ai/GeneralAITest.kt` - Updated existing tests for new diplomacy/injury logic, added branch-point tests
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/NpcAiParityTest.kt` - Added golden value parity tests for all fixed methods

## Decisions Made
- DiplomacyState enum gains `code: Int` matching PHP d constants (d평화=0 through d전쟁=4)
- PHP diplomacy `state=0` maps to stateCode "전쟁" (active war), `state=1` maps to "선전포고" (declaration with term countdown)
- Existing tests that used "선전포고" for active war behavior updated to "전쟁"
- Injury check unified to `cureThreshold` (default 10) across all code paths -- old `> 0` check was too aggressive
- doNonAggressionProposal uses nation.meta for recv_assist/resp_assist storage (replacing probability-based approach)
- PHP officer_level 12 (chief) maps to Kotlin officerLevel 20

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed existing test diplomacy stateCodes**
- **Found during:** Task 1
- **Issue:** Existing GeneralAITest used stateCode "선전포고" (declaration) when expecting AT_WAR behavior. With PHP-correct term-based logic, declarations produce IMMINENT/DECLARED, not AT_WAR.
- **Fix:** Updated tests to use "전쟁" (active war, PHP state=0) for AT_WAR scenarios
- **Files modified:** GeneralAITest.kt
- **Committed in:** ba45ade

**2. [Rule 1 - Bug] Fixed injury threshold in decideWarAction/decidePeaceAction**
- **Found during:** Task 2
- **Issue:** decideWarAction and decidePeaceAction used `injury > 0` instead of `injury > cureThreshold`, causing premature healing even with minor injuries
- **Fix:** Changed to `injury > nationPolicy.cureThreshold` matching PHP line 3772
- **Files modified:** GeneralAI.kt
- **Committed in:** f59663f

**3. [Rule 1 - Bug] Updated existing injury tests to exceed threshold**
- **Found during:** Task 2
- **Issue:** Existing tests used injury=10 and injury=5, which are <= cureThreshold=10 and no longer trigger healing
- **Fix:** Updated test injury values to 15 (exceeds default cureThreshold=10)
- **Files modified:** GeneralAITest.kt
- **Committed in:** f59663f

---

**Total deviations:** 3 auto-fixed (3 bugs from parity-breaking changes)
**Impact on plan:** All fixes necessary for correctness after PHP-parity changes. No scope creep.

## Issues Encountered
- JDK version mismatch: system default JDK 25 incompatible with project JDK 17; resolved by explicitly setting JAVA_HOME
- Priority list reorder caused cascading test failures where tests depended on old wrong priority order; resolved by adjusting test expectations

## Known Stubs
None - all methods implemented with PHP-matching logic.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Structural divergences (Research D1-D5) are now fixed
- Ready for Phase 08 Plan 02 (military do*() methods parity) and Plan 03 (domestic do*() methods parity)
- categorizeNationGeneral and CalcDiplomacyResult are available for downstream do*() methods

---
*Phase: 08-npc-ai-parity*
*Completed: 2026-04-02*
