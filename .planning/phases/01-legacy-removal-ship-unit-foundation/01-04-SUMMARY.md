---
phase: 01-legacy-removal-ship-unit-foundation
plan: "04"
subsystem: ship-unit-entity
tags: [entity, jpa, flyway, ship-unit, repository]
dependency_graph:
  requires: [01-01, 01-02]
  provides: [ShipUnit entity, ShipUnitRepository, V45 migration]
  affects: [Phase 2 command system, Phase 3 tactical battle]
tech_stack:
  added: []
  patterns: [JPA entity with JSONB meta, Spring Data Repository derived queries]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/ShipUnit.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/ShipUnitRepository.kt
    - backend/game-app/src/main/resources/db/migration/V45__create_ship_unit_table.sql
  modified: []
decisions:
  - Used Java 23 (not default Java 25) to work around Gradle 8.12 / JDK 25 incompatibility — pre-existing environment issue
  - shipClass and shipSubtype stored as String (enum name) not @Enumerated to allow schema evolution without migration
  - JSONB meta field follows existing Fleet.kt pattern for flexible extension
metrics:
  duration: ~8 minutes
  completed: "2026-04-06T13:45:54Z"
  tasks: 2
  files: 3
---

# Phase 1 Plan 04: ShipUnit Entity + V45 Migration Summary

ShipUnit JPA entity (300-ship unit) with Fleet FK, ShipClass/ShipSubtype/CrewProficiency fields, and V45 Flyway migration for the `ship_unit` table.

## What Was Built

### Task 1: V45 Flyway Migration (`1df20b52`)
Created `V45__create_ship_unit_table.sql` with the `ship_unit` DDL:
- Primary columns: `session_id`, `fleet_id` (REFERENCES fleet(id) ON DELETE CASCADE), `slot_index`
- Ship type columns: `ship_class`, `ship_subtype`
- Combat stats: `armor`, `shield`, `weapon_power`, `speed`, `crew_capacity`, `supply_capacity`
- State columns: `morale`, `training`, `missile_stock`, `stance`, `crew_proficiency`
- Flagship info: `is_flagship`, `flagship_code`
- Ground unit transport: `ground_unit_type`, `ground_unit_count`
- Flexible extension: `meta JSONB`
- Three indexes: fleet_id, session_id, partial index on is_flagship

### Task 2: ShipUnit Entity + ShipUnitRepository (`023b087e`)
Created `ShipUnit.kt` JPA entity:
- Fleet FK via `fleetId` column
- Enum fields stored as String names (shipClass, shipSubtype, crewProficiency)
- `isFlagship` boolean with `flagshipCode` for named flagships (e.g., Brunhild)
- Convenience methods: `resolveSubtype()`, `resolveProficiency()`, `effectiveCombatPower()`
- Follows Fleet.kt annotation patterns (jakarta.persistence, @JdbcTypeCode(SqlTypes.JSON))

Created `ShipUnitRepository.kt`:
- `findByFleetId(fleetId)` — primary query for fleet composition
- `findByFleetIdAndSlotIndex(fleetId, slotIndex)` — single slot lookup
- `findBySessionId(sessionId)` — all units in a session
- `findByFleetIdAndIsFlagshipTrue(fleetId)` — flagship unit lookup
- `countByFleetId(fleetId)` — slot count
- `deleteByFleetId(fleetId)` — cascade delete helper

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Environment Note

The default JDK (25.0.2) is incompatible with Gradle 8.12 (`IllegalArgumentException: 25.0.2`). This is a pre-existing environment issue. Compilation was verified using Java 23 (`JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/temurin-23.0.2/Contents/Home`), which produced BUILD SUCCESSFUL.

## Known Stubs

None. All fields are wired to the database schema. ShipStatRegistry integration (combat stat population from SubType) is deferred to Plan 05 as planned.

## Commits

| Hash | Message |
|------|---------|
| `1df20b52` | feat(phase-01): add V45 Flyway migration — ship_unit table DDL |
| `023b087e` | feat(phase-01): add ShipUnit entity and ShipUnitRepository |

## Self-Check: PASSED

- `ShipUnit.kt` exists at expected path
- `ShipUnitRepository.kt` exists at expected path
- `V45__create_ship_unit_table.sql` exists with `CREATE TABLE ship_unit` + `REFERENCES fleet`
- `./gradlew :game-app:compileKotlin` BUILD SUCCESSFUL (Java 23)
