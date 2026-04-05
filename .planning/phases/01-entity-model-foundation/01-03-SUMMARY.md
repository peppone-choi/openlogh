---
phase: 01-entity-model-foundation
plan: 03
subsystem: game-app entities
tags: [entity-rename, domain-mapping, 8-stat-system]
dependency_graph:
  requires: [01-01, 01-02]
  provides: [Officer, Planet, Faction, Fleet, Sovereign, SessionState]
  affects: [repositories, services, commands, snapshots, DTOs]
tech_stack:
  added: []
  patterns: [JPA entity with JSONB, 8-stat officer model]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Planet.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Faction.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Fleet.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Sovereign.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/SessionState.kt
  modified: []
  deleted:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/General.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/City.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Nation.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Troop.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Emperor.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/WorldState.kt
decisions:
  - "Emperor.serverId renamed to sessionId (was server_id in DB, maps to session concept)"
  - "Sovereign entity kept all legacy hall-of-fame fields unchanged (l5-l12 name/pic, tiger, eagle, gen)"
  - "Planet preserved City's Number-parameter constructor trick for approval/dead fields"
metrics:
  duration: 2min
  completed: "2026-04-05T07:46:09Z"
---

# Phase 1 Plan 3: Core Entity Rename Summary

Renamed 6 core game-app JPA entities from OpenSamguk to LOGH domain names with 8-stat Officer system, matching V28 migration schema.

## One-liner

6 core entities renamed (General->Officer with 8-stat, City->Planet, Nation->Faction, Troop->Fleet, Emperor->Sovereign, WorldState->SessionState) with all FK/resource/item fields mapped to LOGH domain.

## Tasks Completed

| Task | Name | Commit | Key Changes |
|------|------|--------|-------------|
| 1 | Rename General.kt to Officer.kt with 8-stat system | 0f6e86e3 | 8 stats, 3 new dex fields, LOGH resource/item/FK renames |
| 2 | Rename City/Nation/Troop/Emperor/WorldState | b7f563e7 | 5 entities renamed with all field mappings per CLAUDE.md |

## Changes Made

### Officer.kt (was General.kt)
- **Class/Table:** General -> Officer / `officer`
- **FK renames:** worldId->sessionId, nationId->factionId, cityId->planetId, troopId->fleetId
- **Stat system:** 5-stat (leadership, strength, intel, politics, charm) -> 8-stat (leadership, command, intelligence, politics, administration, mobility, attack, defense) with corresponding _exp fields
- **New dex fields:** dex6, dex7, dex8 (for 8-stat dexterity mapping)
- **Resources:** gold->funds, rice->supplies, crew->ships, crewType->shipClass, train->training, atmos->morale
- **Items:** weaponCode->flagshipCode, bookCode->equipCode, horseCode->engineCode, itemCode->accessoryCode

### Planet.kt (was City.kt)
- **FK renames:** worldId->sessionId, nationId->factionId
- **Fields:** pop->population, agri->production, comm->commerce, secu->security, trust->approval, def->orbitalDefense, wall->fortress, trade->tradeRoute (with corresponding max fields)

### Faction.kt (was Nation.kt)
- **FK renames:** worldId->sessionId, capitalCityId->capitalPlanetId, chiefGeneralId->chiefOfficerId
- **Fields:** gold->funds, rice->supplies, bill->taxRate, rate->conscriptionRate, rateTmp->conscriptionRateTmp, tech->techLevel, power->militaryPower, gennum->officerCount, level->factionRank, typeCode->factionType

### Fleet.kt (was Troop.kt)
- **FK renames:** worldId->sessionId, leaderGeneralId->leaderOfficerId, nationId->factionId

### Sovereign.kt (was Emperor.kt)
- **FK renames:** serverId->sessionId
- **Table:** emperor -> sovereign

### SessionState.kt (was WorldState.kt)
- **Table:** world_state -> session_state
- No FK renames needed (it IS the session root entity)

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None - all entity fields are fully mapped with proper @Column annotations matching V28 schema.

## Self-Check: PASSED

All 6 entity files verified present. Both commit hashes confirmed in git log.
