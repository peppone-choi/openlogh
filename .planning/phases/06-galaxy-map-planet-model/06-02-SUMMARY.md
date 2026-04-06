---
phase: 06-galaxy-map-planet-model
plan: 02
subsystem: galaxy-map-services
tags: [star-system, service, rest-api, dto, map-service]
dependency_graph:
  requires: [06-01]
  provides: [star-system-service, star-system-api, galaxy-map-dto]
  affects: [planet-service, map-service]
tech_stack:
  added: []
  patterns: [service-layer, rest-controller, dto-mapping]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/service/StarSystemService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/StarSystemDtos.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/StarSystemController.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/service/MapService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/PlanetService.kt
decisions:
  - StarSystemExtra as inner data class of MapService for encapsulation
  - Bidirectional routes deduplicated in GalaxyMapDto (fromStarId < toStarId)
  - Planet count per system computed from starSystemId FK join
metrics:
  duration: 3min
  completed: "2026-04-06T04:29:34Z"
---

# Phase 06 Plan 02: Star System Services & REST API Summary

StarSystemService initializes 80 star systems with bidirectional route network from logh.json; MapService extended with StarSystemExtra parsing; REST API serves galaxy map with faction territories and fortress data.

## What Was Built

### StarSystemService
- `initializeStarSystems()`: Creates all 80 StarSystem entities + bidirectional StarRoute entries from map data, applying FortressType gun stats for 4 fortress systems (Iserlohn, Geiersburg, Rentenberg, Garmisch)
- Query methods: `getStarSystemsBySession`, `getStarSystem`, `getRoutes`, `getRoutesFrom`, `getFortressSystems`, `getSystemsByFaction`
- `transferOwnership()`: Updates faction on star system and cascades to all planets

### MapService LOGH Support
- Added `StarSystemExtra` data class for LOGH-specific star data (nameEn, starRgb, spectralType, planets, fortressType)
- `loadMap()` now parses `starSystems` JSON array when present
- `getStarSystemExtras()` and `getStarSystemExtra()` accessor methods
- All existing "che" map functionality preserved

### PlanetService Updates
- `initializeCityFromConst()` accepts optional `fortressType` and `starSystemId` parameters
- `initializeAllCities()` accepts optional `starSystemMap` to wire star system IDs and fortress data into planets

### REST API (StarSystemController)
- `GET /api/world/{sessionId}/galaxy` - Full galaxy map with systems, routes, faction territories
- `GET /api/world/{sessionId}/galaxy/system/{mapStarId}` - Single system detail
- `GET /api/world/{sessionId}/galaxy/fortresses` - Fortress systems only
- `GET /api/world/{sessionId}/galaxy/faction/{factionId}` - Systems by faction

### DTOs
- `StarSystemDto` - All star system fields + connections + planetCount
- `StarRouteDto` - Route edge (fromStarId, toStarId, distance)
- `GalaxyMapDto` - Aggregated systems, routes, factionTerritories map

## Verification

- `./gradlew :game-app:compileKotlin` passes with zero errors on both task commits

## Deviations from Plan

None - plan executed exactly as written.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | fc68e53f | StarSystemService, MapService LOGH support, PlanetService fortress fields |
| 2 | 3e02ef3f | StarSystemController REST API with DTOs for galaxy map |
