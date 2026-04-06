---
phase: 06-galaxy-map-planet-model
plan: 01
subsystem: database
tags: [jpa, flyway, postgresql, jsonb, galaxy-map, star-system]

requires:
  - phase: 01-entity-model-foundation
    provides: Planet entity, session_state table, faction entities
provides:
  - logh.json with 80 LOGH star systems in CityConst-compatible format
  - StarSystem JPA entity with fortress stats and spectral data
  - StarRoute JPA entity for route connections
  - FortressType enum with gun power/range/cooldown stats
  - StarSystemConst data class for map loading
  - V35 Flyway migration for star_system and star_route tables
  - Planet entity extended with fortress gun fields and starSystemId FK
affects: [06-galaxy-map-planet-model, 07-combat-engine]

tech-stack:
  added: []
  patterns: [star-system-session-scoping, fortress-type-enum-pattern, dual-format-map-json]

key-files:
  created:
    - backend/shared/src/main/resources/data/maps/logh.json
    - backend/game-app/src/main/kotlin/com/openlogh/entity/StarSystem.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/StarRoute.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/FortressType.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/StarSystemConst.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/StarSystemRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/StarRouteRepository.kt
    - backend/game-app/src/main/resources/db/migration/V35__add_star_system_and_routes.sql
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt

key-decisions:
  - "logh.json uses dual format: CityConst-compatible cities array + starSystems array for LOGH-specific data"
  - "Fortress types stored as VARCHAR string in DB for flexibility, enum used in application layer"
  - "Star system levels derived from connection count and special status (capital=7, fortress=8)"

patterns-established:
  - "Star system data: cities array for MapService compat, starSystems for extended LOGH data"
  - "Fortress stats on both StarSystem and Planet entities for different query contexts"

requirements-completed: [GAL-01, GAL-02, GAL-03, GAL-04]

duration: 2min
completed: 2026-04-06
---

# Phase 6 Plan 1: Galaxy Map Data Model Summary

**80-system LOGH galaxy map with StarSystem/StarRoute entities, 4 fortress types, and V35 Flyway migration**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-06T04:21:47Z
- **Completed:** 2026-04-06T04:24:13Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- Created logh.json with 80 star systems converted from docs/star_systems.json into CityConst-compatible format
- FortressType enum with gun stats for Iserlohn (9000/3/300), Geiersburg (7000/2/360), Rentenberg and Garmisch (5000/2/240)
- StarSystem and StarRoute JPA entities with session-scoped queries and JSONB starRgb field
- V35 Flyway migration creating star_system/star_route tables and adding fortress columns to planet table

## Task Commits

Each task was committed atomically:

1. **Task 1: Create logh.json map data + StarSystemConst model + FortressType enum** - `9571f8b5` (feat)
2. **Task 2: Flyway V35 migration + StarSystem/StarRoute entities + Planet fortress fields** - `9681f08d` (feat)

## Files Created/Modified
- `backend/shared/src/main/resources/data/maps/logh.json` - 80 star systems with cities + starSystems arrays
- `backend/game-app/src/main/kotlin/com/openlogh/model/FortressType.kt` - Enum with gun power/range/cooldown/garrison stats
- `backend/game-app/src/main/kotlin/com/openlogh/model/StarSystemConst.kt` - Data class for LOGH star system map data
- `backend/game-app/src/main/kotlin/com/openlogh/entity/StarSystem.kt` - JPA entity with JSONB starRgb, fortress stats
- `backend/game-app/src/main/kotlin/com/openlogh/entity/StarRoute.kt` - JPA entity for session-scoped route connections
- `backend/game-app/src/main/kotlin/com/openlogh/repository/StarSystemRepository.kt` - Session/faction/mapStarId queries
- `backend/game-app/src/main/kotlin/com/openlogh/repository/StarRouteRepository.kt` - Session/fromStarId queries
- `backend/game-app/src/main/resources/db/migration/V35__add_star_system_and_routes.sql` - DDL for star_system, star_route, planet fortress columns
- `backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt` - Added starSystemId, fortressType, gun fields

## Decisions Made
- logh.json uses dual format: CityConst-compatible `cities` array for existing MapService, plus `starSystems` array for LOGH-specific data (spectral type, RGB, planets, fortress type)
- Fortress type stored as VARCHAR string in DB columns for flexibility; FortressType enum used in application layer for type safety
- Star system levels derived algorithmically: capitals=7, fortresses=8, 5+ connections=6, 3-4 connections=5, 1-2 connections=4

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- logh.json ready for MapService loading via `getCities("logh")`
- StarSystem/StarRoute entities ready for service layer in subsequent plans
- Planet fortress fields ready for combat engine integration

## Self-Check: PASSED

All 9 files verified present. Both task commits (9571f8b5, 9681f08d) found in git log.

---
*Phase: 06-galaxy-map-planet-model*
*Completed: 2026-04-06*
