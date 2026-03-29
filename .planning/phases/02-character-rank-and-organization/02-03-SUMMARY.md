---
phase: 02-character-rank-and-organization
plan: 03
subsystem: rank-system, stat-growth, api
tags: [rank-ladder, position-card, personnel-authority, stat-growth, age-modifier, exp-system, rest-api, kotlin, spring-service]

requires:
    - phase: 02-character-rank-and-organization
      plan: 01
      provides: PositionCardService facade with revokeOnRankChange, getHeldCardCodes, appointPosition, dismissPosition
provides:
    - RankLadderService with PositionCardService integration and canPromote authority check
    - RankController REST endpoints for rank ladder data and limits
    - StatGrowthService for age-based stat modifiers and exp-to-stat conversion
    - Personnel commands using PositionCard-based authority (no more officerLevel < 20)
affects: [03-command-point-system, 04-galactic-map, 06-tactical-combat]

tech-stack:
    added: []
    patterns: [position-card-based-authority, age-modifier-pattern, exp-threshold-growth]

key-files:
    created:
        - backend/game-app/src/main/kotlin/com/openlogh/controller/RankController.kt
        - backend/game-app/src/main/kotlin/com/openlogh/service/StatGrowthService.kt
        - backend/game-app/src/test/kotlin/com/openlogh/service/RankLadderServiceTest.kt
        - backend/game-app/src/test/kotlin/com/openlogh/service/StatGrowthServiceTest.kt
    modified:
        - backend/game-app/src/main/kotlin/com/openlogh/service/RankLadderService.kt
        - backend/game-app/src/main/kotlin/com/openlogh/command/nation/PersonnelCommands.kt
        - backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt

key-decisions:
    - "Personnel authority via PositionCard codes (emperor/chairman/military_minister/personnel_chief) instead of legacy officerLevel < 20"
    - "RankLadderService.canPromote encapsulates authority + rank-limit validation for reuse by commands"
    - "StatGrowthService uses Short arithmetic matching Officer entity field types"
    - "PERSONNEL_AUTHORITY_CARDS set defined in PersonnelCommands for locality; must sync with PositionCardType entries"

patterns-established:
    - "PositionCard-based authority: all personnel commands check held card codes, not rank level"
    - "Age modifier pattern: getAgeMultiplier(age) returns 1.2/1.0/0.8 for youth/prime/elder"
    - "Exp threshold growth: 100 exp = +1 stat with carry-over, cap at 100"

requirements-completed: [CHAR-04, CHAR-05, RANK-01, RANK-02, RANK-03, RANK-04, RANK-05, RANK-06, RANK-07, RANK-08, RANK-09, RANK-10, RANK-11, RANK-12, RANK-13, RANK-14]

duration: 63min
completed: 2026-03-29
---

# Phase 02 Plan 03: Rank Ladder + Stat Growth Summary

**Rank ladder wired to PositionCardService with REST API, personnel commands migrated to card-based authority, and StatGrowthService implementing gin7 age modifiers + exp-to-stat conversion**

## Performance

- **Duration:** 63 min
- **Started:** 2026-03-29T02:05:57Z
- **Completed:** 2026-03-29T03:09:33Z
- **Tasks:** 3
- **Files modified:** 7

## Accomplishments

- Injected PositionCardService into RankLadderService; auto-promotion and auto-demotion now call revokeOnRankChange to clear non-basic/non-fief cards on rank changes
- Added canPromote() method enforcing PositionCard-based personnel authority (emperor/chairman -> any rank, military_minister -> rank 8, personnel_chief -> rank 6) plus per-rank personnel limits
- Created RankController with GET /api/rank/ladder/{sessionId} and GET /api/rank/limits endpoints for frontend org chart display
- Replaced all 9 `officerLevel < 20` legacy permission checks in PersonnelCommands with PositionCard-based authority (hasPersonnelAuthority / hasEmperorAuthority helpers)
- Fixed demotion command to set experience=100 per RANK-07 (was incorrectly setting dedication=0)
- Added revokeOnRankChange calls in promotion/demotion/special-promotion command run() methods
- Created StatGrowthService with age-based growth multiplier (youth +20%, elder -20%), exp-to-stat conversion (100 threshold with carry-over), stat cap at 100, and elder decay (1% per stat per tick for age > 55)
- 16 total unit tests: 6 for RankLadderService, 10 for StatGrowthService

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire RankLadderService to PositionCardService + RankController** - `94afff0` (feat)
2. **Task 2: Personnel command authority migration to PositionCard-based checks** - `66e8a64` (feat)
3. **Task 3: StatGrowthService — age-based stat modifiers and exp-to-stat growth** - `52c31d8` (feat)

## Files Created/Modified

- `backend/game-app/src/main/kotlin/com/openlogh/service/RankLadderService.kt` - Added PositionCardService injection, revokeOnRankChange calls in auto-promotion/demotion, canPromote() authority method
- `backend/game-app/src/main/kotlin/com/openlogh/controller/RankController.kt` - REST controller with ladder and limits endpoints
- `backend/game-app/src/main/kotlin/com/openlogh/command/nation/PersonnelCommands.kt` - All 9 personnel commands migrated to PositionCard-based authority
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandServices.kt` - Added rankLadderService field
- `backend/game-app/src/main/kotlin/com/openlogh/service/StatGrowthService.kt` - Age modifier, exp-to-stat, elder decay
- `backend/game-app/src/test/kotlin/com/openlogh/service/RankLadderServiceTest.kt` - 6 tests for ladder sort, promotion, demotion, limits, authority
- `backend/game-app/src/test/kotlin/com/openlogh/service/StatGrowthServiceTest.kt` - 10 tests for age brackets, exp growth, stat cap, decay, addExp

## Decisions Made

- **PositionCard-based authority replaces officerLevel < 20:** All personnel commands now check held position card codes against PERSONNEL_AUTHORITY_CARDS set, with rank-scoped authority (emperor=any, military_minister=rank 8, personnel_chief=rank 6)
- **canPromote encapsulates authority + limits:** Single method in RankLadderService checks card authority scope AND per-rank personnel limits, reusable by both commands and auto-promotion
- **Short type arithmetic:** StatGrowthService operates with Short to match Officer entity fields, avoiding Int/Short conversion issues at JPA persistence layer
- **PERSONNEL_AUTHORITY_CARDS locality:** Authority card set defined in PersonnelCommands file for locality rather than PositionCardType enum; must be kept in sync with card type additions

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed demotion experience value**
- **Found during:** Task 2 (reviewing 강등 command)
- **Issue:** 강등 command was setting `dedication = 0` instead of `experience = 100` per RANK-07 spec
- **Fix:** Changed to `dg.experience = 100` and added revokeOnRankChange call
- **Files modified:** PersonnelCommands.kt
- **Commit:** 66e8a64

**2. [Rule 2 - Missing functionality] Added revokeOnRankChange in command run() methods**
- **Found during:** Task 2 (reviewing promotion/demotion commands)
- **Issue:** 승진, 강등, and 발탁 commands were not calling revokeOnRankChange after rank changes, meaning position cards would persist through rank changes
- **Fix:** Added `services?.positionCardService?.revokeOnRankChange(env.worldId, dg.id)` in all three command run() methods
- **Files modified:** PersonnelCommands.kt
- **Commit:** 66e8a64

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing functionality)
**Impact on plan:** Both fixes are within scope of the personnel command migration. No scope creep.

## Known Stubs

None - all data paths are fully wired. StatGrowthService.addExp() will be called by the CP consumption system in Phase 3 but the service itself is complete.

## Next Phase Readiness

- RankLadderService.canPromote() ready for use in any promotion path (manual, auto, special)
- RankController endpoints ready for frontend org chart / rank ladder display
- StatGrowthService.addExp() ready to be called from CP consumption logic in Phase 3
- StatGrowthService.processExpGrowth() and processElderDecay() ready for turn tick integration in Phase 3
- CommandServices.rankLadderService must be wired in TurnService where CommandServices is constructed

---

## Self-Check: PASSED

All 8 files found. All 3 commit hashes verified.

---

_Phase: 02-character-rank-and-organization_
_Completed: 2026-03-29_
