# Phase 6 Research: Galaxy Map & Planet Model

## Discovery Level: 0 (Skip)

All work follows established codebase patterns. No new external dependencies needed.

## Current State Analysis

### Map System (existing)
- `MapService` in game-app loads JSON from `shared/src/main/resources/data/maps/{mapName}.json`
- `CityConst` data class: id, name, level, region, population, agriculture, commerce, security, defence, wall, x, y, connections
- `PlanetService` initializes Planet entities from CityConst via `initializeCityFromConst()`
- `MapController` serves raw map JSON via `GET /api/maps/{mapName}`
- Existing maps are Three Kingdoms themed (che.json = 80 Chinese cities)
- `Planet.kt` already has LOGH-renamed fields: population, production, commerce, security, approval, orbitalDefense, fortress, tradeRoute

### What Needs to Change
1. **New map data**: Create `logh.json` with 80 LOGH star systems (from `docs/star_systems.json`)
2. **CityConst adaptation**: The existing model maps well -- just needs LOGH-appropriate values for each star system
3. **Fortress mechanics**: New concept not in OpenSamguk. Need fortress type enum, fortress gun data, garrison slots
4. **Territory zones**: Regions map to Empire/Alliance/Fezzan zones instead of Chinese provinces
5. **Planet entity update**: Add fortress-specific fields (fortress_type, fortress_gun_power, fortress_gun_range, fortress_gun_cooldown)
6. **Frontend galaxy map**: New UI component -- no existing map UI to modify

### Star Systems Data (docs/star_systems.json)
- 80 systems total: 39 Empire, 40 Alliance, 1 Fezzan
- 4 fortress systems with "요새" planets:
  - ID 18: Iserlohn (이제르론) -- "이제르론 요새" -- Empire, connections [17, 19]
  - ID 60: Freya (프레이야) -- "렌텐베르크 요새" -- Empire, connections [45, 48, 56, 58, 73]
  - ID 67: Eisenherz (아이젠헤르츠) -- "가이에스부르크 요새" -- Empire, connections [64, 70, 80]
  - ID 76: Kiphoiser (키포이저) -- "가르미슈 요새" -- Empire, connections [73, 74, 79]
- Each system has: id, name_ko, name_en, faction, x, y, star_rgb, spectral_type, planets[], connections[]
- Coordinates range roughly x: 195-1752, y: 286-1037

### Fortress System Design (from gin7 reference)
- Iserlohn: Thor Hammer (토르 해머) -- massive beam weapon
- Geiersburg: Geiersburg Haken (가이에스하켄) -- fortress-class beam weapon
- Rentenberg/Garmisch: Secondary fortresses with standard fortress guns
- Fortress guns fire at fleets within range, dealing massive damage with cooldown
- Garrison slots: fortress can house garrison units

### DB Migration Plan (V35)
- Add `star_system` table: id, session_id, map_star_id, name_ko, name_en, faction_id, x, y, spectral_type, star_rgb, is_fortress, fortress_type
- Add `star_route` table: id, session_id, from_star_id, to_star_id, distance
- Add fortress columns to `planet`: fortress_type VARCHAR, fortress_gun_power INT, fortress_gun_range INT, fortress_gun_cooldown INT, garrison_capacity INT
- Keep existing planet table for planet-level data (resources, defense)
- StarSystem is the grid-level entity; Planet entities belong to star systems

### Architecture Decision: StarSystem vs Planet
The existing codebase conflates city (territory unit) with planet. In LOGH:
- **StarSystem** = grid-level entity (navigable point on galaxy map, has routes)
- **Planet** = sub-entity within a star system (has resources, population)

Current Planet entity serves as both. For this phase:
- Add `StarSystem` entity for map-level navigation and ownership
- Keep `Planet` entity for resource management but link to StarSystem
- Planet.mapPlanetId maps to StarSystem.mapStarId for backward compat

### Frontend Galaxy Map
- Canvas-based (Konva already in deps) 2D star map
- Star systems as nodes, routes as lines
- Color-coded by faction: Empire blue, Alliance green, Fezzan yellow
- Click to select system, show detail panel
- Fortress systems with special icon/glow
