---
phase: 01-entity-model-foundation
plan: 07
subsystem: backend-command-engine-dto-controller
tags: [domain-rename, bulk-refactor, LOGH]
dependency_graph:
  requires: [01-03, 01-04, 01-05, 01-06]
  provides: [logh-domain-names-in-command-engine-dto-controller]
  affects: [command-system, war-engine, turn-engine, ai-system, dto-layer, controller-layer, service-layer]
tech_stack:
  added: []
  patterns: [legacy-parity-json-keys, context-aware-field-rename]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/command/OfficerCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/FactionCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarUnitOfficer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarUnitPlanet.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/OfficerAI.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/FactionAI.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/OfficerMaintenanceService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/SovereignConstants.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ShipClassAvailability.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/trigger/OfficerTrigger.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/steps/OfficerMaintenanceStep.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/steps/ExecuteOfficerCommandsStep.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/event/actions/game/AssignOfficerSpecialityAction.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/command/ (all 110+ files)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ (all 90+ files)
    - backend/game-app/src/main/kotlin/com/openlogh/dto/ (23 files)
    - backend/game-app/src/main/kotlin/com/openlogh/controller/ (35 files)
    - backend/game-app/src/main/kotlin/com/openlogh/service/ (30+ files)
decisions:
  - "Legacy parity: JSON keys in command message strings (gold, rice, crew, etc.) kept as-is for backward compatibility with CommandResultApplicator protocol"
  - "CommandResultApplicator maps legacy JSON keys to new entity field names (gold->funds, rice->supplies, etc.)"
  - "CommandCost renamed: gold->funds, rice->supplies to match entity domain"
  - "CommandEnv.worldId renamed to sessionId"
  - "Local variable names (general, city, nation) kept as-is in command bodies per Plan 06 convention"
  - "Service body variable names renamed (general->officer, city->planet, nation->faction) where parameter names were already changed in Plan 06"
  - "Faction-specific field renames (level->factionRank, tech->techLevel, etc.) applied only on faction/nation variable access to avoid breaking Planet.level"
metrics:
  duration: 53min
  completed: "2026-04-05"
  tasks_completed: 1
  tasks_total: 3
  files_modified: 228
---

# Phase 1 Plan 7: Command System, Engine, DTO, Controller Domain Rename Summary

Bulk propagation of LOGH domain names through the command system (93 commands), war/battle engine, turn engine, AI system, DTOs, controllers, and service layer. Reduced compilation errors from 9609 to ~903.

## What Was Done

### Task 1: Bulk Domain Rename (Partial - compilation not yet passing)

**Completed renames across 228 files:**

1. **13 file renames:**
   - GeneralCommand.kt -> OfficerCommand.kt
   - NationCommand.kt -> FactionCommand.kt
   - WarUnitGeneral.kt -> WarUnitOfficer.kt
   - WarUnitCity.kt -> WarUnitPlanet.kt
   - GeneralAI.kt -> OfficerAI.kt
   - NationAI.kt -> FactionAI.kt
   - GeneralMaintenanceService.kt -> OfficerMaintenanceService.kt
   - GeneralTrigger.kt -> OfficerTrigger.kt
   - EmperorConstants.kt -> SovereignConstants.kt
   - CrewTypeAvailability.kt -> ShipClassAvailability.kt
   - AssignGeneralSpecialityAction.kt -> AssignOfficerSpecialityAction.kt
   - GeneralMaintenanceStep.kt -> OfficerMaintenanceStep.kt
   - ExecuteGeneralCommandsStep.kt -> ExecuteOfficerCommandsStep.kt

2. **Import renames:** All `import com.openlogh.entity.General/City/Nation/Troop/Emperor/WorldState` updated to Officer/Planet/Faction/Fleet/Sovereign/SessionState across all files

3. **Type references:** General->Officer, City->Planet, Nation->Faction, Troop->Fleet in type annotations, generics, casts, constructor calls

4. **Class name renames:** 30+ compound class names (GeneralRepository->OfficerRepository, GeneralService->OfficerService, GeneralSnapshot->OfficerSnapshot, etc.)

5. **Variable name renames:** All repository/service injection variables (generalRepository->officerRepository, cityRepository->planetRepository, etc.)

6. **Entity field access via dot notation:** .worldId->.sessionId, .nationId->.factionId, .cityId->.planetId, .troopId->.fleetId, .strength->.command, .intel->.intelligence, .charm->.administration, .gold->.funds, .rice->.supplies, .crew->.ships, .crewType->.shipClass, .atmos->.morale, .train->.training, .weaponCode->.flagshipCode, .bookCode->.equipCode, .horseCode->.engineCode, .itemCode->.accessoryCode, .pop->.population, .agri->.production, .comm->.commerce, .secu->.security, .trust->.approval, .def->.orbitalDefense, .wall->.fortress, .trade->.tradeRoute, .typeCode->.factionType, .gennum->.officerCount, .chiefGeneralId->.chiefOfficerId, .capitalCityId->.capitalPlanetId

7. **CQRS port method renames:** putGeneral->putOfficer, allCities->allPlanets, generalsByNation->officersByFaction, etc.

8. **Repository method renames:** findByWorldId->findBySessionId, findByNationId->findByFactionId, etc.

9. **CommandEnv.worldId -> sessionId**, **CommandCost.gold/rice -> funds/supplies**

10. **Faction-specific field renames** (applied only on nation/faction variables): level->factionRank, tech->techLevel, power->militaryPower, bill->taxRate, rate->conscriptionRate

### Tasks 2 and 3: Not Started

DTO renames, controller renames, and test updates are deferred as they depend on Task 1 compilation succeeding.

## Deviations from Plan

### Known Issues - Compilation Not Passing

**~903 compilation errors remain.** These fall into categories that cannot be solved with context-free regex:

1. **Named parameter mismatches in entity constructors (~400 errors):** Kotlin named parameters like `Officer(worldId = x, gold = y)` need `Officer(sessionId = x, funds = y)` but the same patterns (`gold =`, `level =`) appear in non-entity contexts (map literals, config objects, local variables) making regex replacement unreliable.

2. **Faction-specific field renames (~100 errors):** Fields like `level`, `tech`, `power`, `rate`, `bill` exist on Faction entity with new names but `level` also exists on Planet with the old name. Context-free regex cannot distinguish which entity a variable references.

3. **Cascading type inference errors (~400 errors):** When one type reference fails, Kotlin cannot infer types for downstream expressions, causing cascading `Unresolved reference 'it'`, `Cannot infer type`, etc.

### Deferred Items

These require manual context-aware editing:
- Fix named parameters in ~62 files with entity constructor calls
- Fix Faction-specific field renames in `it.level` / `it.tech` patterns within Faction iteration contexts
- Complete DTO class renames (GeneralResponse->OfficerResponse, etc.)
- Complete controller file renames (GeneralController->OfficerController, etc.)
- Update REST endpoint paths
- Update test files

## Decisions Made

1. **Legacy parity for JSON keys:** Command message strings use old field names (`gold`, `rice`, `crew`, etc.) as an internal protocol. CommandResultApplicator maps these to new entity fields. This avoids touching all 93 command `run()` method message strings.

2. **Variable names kept as-is in command bodies:** Following Plan 06 convention, local variables named `general`, `city`, `nation` are kept where they serve as parameter names in BaseCommand. Only type annotations and field access are updated.

3. **Faction field renames are context-dependent:** Fields like `level`, `tech`, `power` cannot be globally renamed because they exist on multiple entity types with different target names. Applied only on explicitly typed `nation.` / `faction.` prefixes.

## Known Stubs

None - this plan is purely a rename operation.

## Self-Check: PARTIAL

- [x] 13 files renamed
- [x] 228 files modified with domain renames
- [x] Commit b9542baa exists
- [ ] game-app compilation does NOT pass (~903 errors remain)
- [ ] DTOs not fully renamed
- [ ] Controllers not fully renamed
- [ ] Tests not updated
