---
phase: 02-character-rank-and-organization
plan: 02
subsystem: api
tags: [kotlin, spring-boot, jpa, flyway, character-creation, 8-stat, rest-api]

requires:
  - phase: 01-session-foundation
    provides: Session join/faction selection, Officer entity with @Version locking

provides:
  - CharacterCreationService with 8-stat allocation validation (total=400, min=10, max=100)
  - CharacterController REST endpoints (generate, select-original, available-originals)
  - ScenarioService 8-stat tuple parsing with 5-stat backward compatibility
  - V40 migration adding home_planet_id column to officer table
  - Origin type validation (empire: noble/knight/commoner, alliance: citizen)

affects: [character-selection-ui, rank-system, organization-structure]

tech-stack:
  added: []
  patterns:
    - "StatAllocation data class for validated 8-stat bundles"
    - "SecurityContextHolder + AppUserRepository auth pattern for game-app controllers"
    - "8-stat detection heuristic: row.size >= 18 for scenario data parsing"
    - "5-stat fallback: mobility=(leadership+command)/2, attack=command, defense=intelligence"

key-files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/service/CharacterCreationService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/CharacterController.kt
    - backend/game-app/src/main/resources/db/migration/V40__add_home_planet_and_origin_columns.sql
    - backend/game-app/src/test/kotlin/com/openlogh/service/CharacterCreationServiceTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt

key-decisions:
  - "Stats use Short type matching Officer entity fields, not Int as plan template suggested"
  - "Auth uses SecurityContextHolder + AppUserRepository pattern consistent with OfficerController"
  - "8-stat detection uses row.size >= 18 heuristic (8-stat rows have 19 elements vs 5-stat max 16)"
  - "Faction type resolved from DB (FactionRepository) rather than passed in request body"

patterns-established:
  - "StatAllocation: validated data class bundle for 8 stat values with toList() helper"
  - "CharacterController: game-app REST controller with resolveUserId() helper using SecurityContextHolder"

requirements-completed: [CHAR-01, CHAR-02, CHAR-03, CHAR-06, CHAR-07, CHAR-08, CHAR-13, CHAR-15, PERS-06]

duration: 29min
completed: 2026-03-29
---

# Phase 02 Plan 02: Character Creation Backend Summary

**CharacterCreationService with 8-stat allocation validation (total=400, min=10, max=100), 3 REST endpoints, ScenarioService 8-stat parsing with 5-stat backward compat, and V40 home_planet_id migration**

## Performance

- **Duration:** 29 min
- **Started:** 2026-03-29T01:32:34Z
- **Completed:** 2026-03-29T02:01:34Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- CharacterCreationService validates 8-stat allocation (total=400, per-stat [10,100]), enforces faction-specific origin types, supports generated and original character creation
- CharacterController exposes POST /generate, POST /select-original, GET /available-originals endpoints with proper auth
- ScenarioService.parseGeneral handles 8-stat tuples (row.size >= 18) alongside existing 5-stat and 3-stat legacy formats, computing mobility/attack/defense defaults for legacy data
- V40 Flyway migration adds home_planet_id column with JSONB backfill from meta.returnPlanetId
- 10 unit tests covering stat validation, origin enforcement, first-come-first-served selection, and edge cases

## Task Commits

Each task was committed atomically:

1. **Task 1: Officer entity extension + CharacterCreationService + V40 migration** - `96d2652` (feat)
2. **Task 2: Character REST endpoints + ScenarioService 8-stat parsing** - `1097575` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/openlogh/service/CharacterCreationService.kt` - 8-stat validation, generated/original officer creation, available originals query
- `backend/game-app/src/main/kotlin/com/openlogh/controller/CharacterController.kt` - REST endpoints for character creation and selection
- `backend/game-app/src/main/resources/db/migration/V40__add_home_planet_and_origin_columns.sql` - Adds home_planet_id column with JSONB backfill
- `backend/game-app/src/test/kotlin/com/openlogh/service/CharacterCreationServiceTest.kt` - 10 unit tests with Mockito
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` - Added homePlanetId field
- `backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt` - 8-stat tuple detection, mobility/attack/defense defaults, homePlanetId population

## Decisions Made
- **Stats as Short:** Officer entity uses Short for stat fields. StatAllocation uses Int for convenience but converts via toShort() on officer creation.
- **Auth pattern:** Used SecurityContextHolder + AppUserRepository.findByLoginId() consistent with existing OfficerController, rather than @AuthenticationPrincipal which is not used in game-app.
- **8-stat detection:** row.size >= 18 distinguishes 8-stat (19 elements) from 5-stat (max 16). Additional check on row[12]/row[13] being Number for safety.
- **Faction type from DB:** CharacterController resolves factionType from FactionRepository.findById() rather than trusting client-provided value, preventing origin validation bypass.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Stats are Short not Int in Officer entity**
- **Found during:** Task 1
- **Issue:** Plan template used Int types for stats but Officer entity fields are Short
- **Fix:** StatAllocation uses Int internally but converts via toShort() when creating Officer
- **Files modified:** CharacterCreationService.kt
- **Verification:** Tests pass with Short comparisons
- **Committed in:** 96d2652

**2. [Rule 2 - Missing Critical] Faction type resolved server-side**
- **Found during:** Task 2
- **Issue:** Plan had factionType as client request parameter, allowing origin validation bypass
- **Fix:** CharacterController looks up faction from DB to get authoritative factionType
- **Files modified:** CharacterController.kt
- **Verification:** Compile succeeds, endpoint correctly validates origin against DB faction type
- **Committed in:** 1097575

---

**Total deviations:** 2 auto-fixed (1 bug, 1 missing critical)
**Impact on plan:** Both fixes necessary for correctness and security. No scope creep.

## Issues Encountered
- 2 pre-existing ScenarioServiceTest failures (initializeWorld seeds cities, spawnScenarioNpcGeneralsForYear spawns delayed NPC) confirmed to fail before changes. Out of scope.
- JAVA_HOME pointed to missing openjdk@17; resolved by using Amazon Corretto 17 at /Users/apple/Library/Java/JavaVirtualMachines/corretto-17.0.17/

## Known Stubs
None - all service methods are fully wired with repository calls and return real data.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Character creation backend is complete, ready for frontend character selection UI (Plan 05)
- ScenarioService now populates all 8 stats and homePlanetId for scenario officers
- CharacterController endpoints ready for frontend integration

## Self-Check: PASSED

- All 6 key files: FOUND
- Commit 96d2652: FOUND
- Commit 1097575: FOUND

---
*Phase: 02-character-rank-and-organization*
*Completed: 2026-03-29*
