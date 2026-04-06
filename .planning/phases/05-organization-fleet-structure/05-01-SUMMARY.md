---
phase: 05-organization-fleet-structure
plan: 01
subsystem: game-app
tags: [entity, migration, cqrs, organization]
dependency_graph:
  requires: []
  provides: [UnitType, CrewSlotRole, UnitCrew, Fleet-unit-fields]
  affects: [fleet, unit_crew, InMemoryWorldState, SnapshotEntityMapper]
tech_stack:
  added: []
  patterns: [enum-with-constants, jpa-entity, cqrs-snapshot]
key_files:
  created:
    - backend/game-app/src/main/resources/db/migration/V34__add_unit_type_and_crew_table.sql
    - backend/game-app/src/main/kotlin/com/openlogh/model/UnitType.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/CrewSlotRole.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/UnitCrew.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/UnitCrewRepository.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryWorldState.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/persist/SnapshotEntityMapper.kt
decisions:
  - UnitType stored as VARCHAR string in DB, mapped to enum via helper method for flexibility
  - CrewSlotRole stored as VARCHAR string in unit_crew table, same pattern
  - SOLO type has maxUnits=0 and maxShips=1 representing flagship-only state
  - GARRISON has shipsPerUnit=0 since it uses ground units (육전대) not ships
metrics:
  duration: 2min
  completed: "2026-04-06T03:49:11Z"
---

# Phase 5 Plan 1: Unit Type Domain Model Summary

6 gin7 unit types defined with DB schema, enums, entities, and CQRS snapshot layer -- data foundation for organization structure.

## What Was Done

### Task 1: Flyway V34 migration + UnitType/CrewSlotRole enums
- **Commit:** efe2a332
- V34 migration adds `unit_type`, `max_units`, `current_units`, `max_crew`, `planet_id` columns to fleet table
- Creates `unit_crew` table with fleet/officer FK, slot_role, unique constraint on (fleet_id, officer_id)
- Indexes on unit_type, planet_id, fleet_id, officer_id
- UnitType enum: 6 types (FLEET/PATROL/TRANSPORT/GROUND/GARRISON/SOLO) with composition constants matching gin7 manual
- CrewSlotRole enum: 10 roles (COMMANDER through ADJUTANT)

### Task 2: Fleet entity, UnitCrew entity/repo, CQRS snapshots
- **Commit:** 603160d5
- Fleet entity updated with unitType, maxUnits, currentUnits, maxCrew, planetId fields + getUnitTypeEnum() helper
- UnitCrew JPA entity with getSlotRoleEnum() helper
- UnitCrewRepository with queries by fleet, officer, slot role
- FleetSnapshot updated with all new fields
- UnitCrewSnapshot added to InMemoryWorldState
- SnapshotEntityMapper updated for bidirectional Fleet and UnitCrew mapping

## Deviations from Plan

None - plan executed exactly as written.

## Verification

- `./gradlew :game-app:compileKotlin` passes after both tasks
- V34 migration SQL syntactically valid
- UnitType enum has exactly 6 entries with correct constants per gin7 manual
- CrewSlotRole enum has exactly 10 entries
- FleetSnapshot mirrors all new Fleet entity fields

## Known Stubs

None - this plan establishes data structures only, no runtime behavior.

## Self-Check: PASSED

- All 8 files verified present on disk
- Commits efe2a332 and 603160d5 verified in git log
