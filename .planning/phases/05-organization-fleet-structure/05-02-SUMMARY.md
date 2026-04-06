---
phase: 05-organization-fleet-structure
plan: 02
subsystem: unit-formation-crew
tags: [formation-cap, crew-management, population-military-linkage, rest-api]
dependency_graph:
  requires: [05-01]
  provides: [FormationCapService, UnitCrewService, updated-FleetService, unit-REST-API]
  affects: [fleet-creation, crew-assignment, formation-limits]
tech_stack:
  added: []
  patterns: [population-based-cap-calculation, crew-slot-validation, deprecated-endpoint-aliasing]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/service/FormationCapService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/UnitCrewService.kt
    - backend/game-app/src/test/kotlin/com/openlogh/service/FormationCapServiceTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/service/FleetService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/TroopController.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/TroopDtos.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/FleetRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/PlanetRepository.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/war/FieldBattleTriggerTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/RealtimeServiceTest.kt
decisions:
  - Population cap uses UnitType.populationPerUnit for precise calculation (not integer billions * multiplier)
  - FleetRepository injected into TroopController for direct fleet lookup in crew endpoints
  - Legacy /troops endpoints preserved as deprecated aliases delegating to new methods
metrics:
  duration: 7min
  completed: 2026-04-06
---

# Phase 5 Plan 2: Formation Caps & Crew Management Summary

Population-based formation caps enforcing gin7's core constraint (1B people = 1 fleet or 6 patrols) with crew slot validation per unit type

## What Was Built

### FormationCapService
- Calculates max formable units per type based on total faction population across planets
- Uses `UnitType.populationPerUnit` for precise calculation: 0.5B population correctly yields 3 patrols
- `canFormUnit()` checks current count vs cap before unit creation
- `getFormationCaps()` returns full map of all 6 unit types with current/max/available
- 9 unit tests covering 2B pop, 0.5B pop, cap reached, non-population-limited types, multi-planet sums

### UnitCrewService
- Crew slot assignment with validation: role must be in `UnitType.allowedSlotRoles`, count < `maxCrew`, slot not already filled
- Officer uniqueness check: cannot be assigned to multiple units simultaneously
- Korean error messages for all validation failures
- `removeAllCrew()` for unit disbanding

### Updated FleetService
- `create()` now accepts `unitType` parameter (defaults to FLEET for backward compat)
- Formation cap validation before unit creation via `FormationCapService.canFormUnit()`
- Auto-assigns creator as COMMANDER via `UnitCrewService`
- GARRISON type requires `planetId` parameter
- `join()` uses `UnitCrewService` for crew slot assignment with automatic next-available-slot detection
- `exit()` removes crew assignment alongside legacy `officer.fleetId` update
- `disband()` removes all crew assignments before deleting unit

### REST API
- `POST /api/units` -- create any unit type with formation cap enforcement
- `GET /api/factions/{factionId}/units?sessionId=` -- list units with crew roster
- `GET /api/factions/{factionId}/formation-caps?sessionId=` -- get formation caps
- `POST /api/units/{id}/crew` -- assign crew member with role validation
- `DELETE /api/units/{id}/crew/{officerId}` -- remove crew member
- `GET /api/units/{id}/crew` -- get crew roster
- Legacy `/troops` endpoints preserved as deprecated aliases

### New DTOs
- `CreateUnitRequest`, `UnitResponse`, `AssignCrewRequest`, `CrewMemberResponse`, `FormationCapEntry`, `FormationCapResponse`
- Legacy `CreateTroopRequest` marked `@Deprecated`

### Repository Updates
- `PlanetRepository.findBySessionIdAndFactionId()` for session-isolated population queries
- `FleetRepository.findBySessionIdAndFactionId()`, `findByFactionIdAndUnitType()`, `findByPlanetId()`, `countByFactionIdAndUnitType()`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Population cap calculation used integer billions instead of populationPerUnit**
- **Found during:** Task 1
- **Issue:** Plan formula `totalPop / 100_000 * 6` yields 0 patrols for 0.5B pop (integer division truncates), but gin7 allows 3 patrols
- **Fix:** Used `UnitType.populationPerUnit` for precise calculation: `totalActual / popPerUnit`
- **Commit:** 3bbd7941

**2. [Rule 3 - Blocking] Pre-existing test compilation errors**
- **Found during:** Task 1
- **Issue:** `FieldBattleTriggerTest.StubPlanetRepository` missing new `findBySessionIdAndFactionId` method; `RealtimeServiceTest` missing `cpService` constructor param
- **Fix:** Added stub implementation and mock parameter
- **Commit:** 3bbd7941

## Known Stubs

None -- all services are fully wired with real data sources.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 3bbd7941 | FormationCapService with population-based formation caps |
| 2 | 33042bb8 | UnitCrewService, updated FleetService, REST API |
