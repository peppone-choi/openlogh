---
phase: 01-entity-model-foundation
plan: 06
subsystem: game-app repository and service layer
tags: [rename, repository, service, domain-mapping]
dependency_graph:
  requires: [01-03, 01-04]
  provides: [LOGH-named repositories, LOGH-named services]
  affects: [commands, controllers, engine, turn-processing]
tech_stack:
  added: []
  patterns: [Spring Data JPA derived queries, constructor injection]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/repository/OfficerRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/PlanetRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/FactionRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/FleetRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/SessionStateRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/SovereignRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/OfficerTurnRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/FactionTurnRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/FactionFlagRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/OldOfficerRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/OldFactionRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/OfficerAccessLogRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/OfficerRecordRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/OfficerService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/PlanetService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/FactionService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/FleetService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/OfficerLogService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/OfficerPoolService.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/repository/DiplomacyRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/EventRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/MessageRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/AuctionRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/AuctionBidRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/BoardRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/BoardCommentRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/SelectPoolRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/VoteRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/VoteCastRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/BettingRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/BetEntryRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/RankDataRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/TournamentRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/TrafficSnapshotRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/WorldHistoryRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/RecordRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/YearbookHistoryRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/GameEventService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/WorldService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/MapService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/MessageService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/AuctionService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/TrafficService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/TournamentService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/AdminService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/FrontInfoService.kt
decisions:
  - "Local variable names (e.g. 'nation', 'city', 'general') kept as-is in service method bodies; only types and repository calls renamed"
  - "WorldService kept its name since it manages world lifecycle conceptually, not just SessionState CRUD"
  - "Service method parameter names (worldId, nationId) kept for now; will be renamed when controllers are updated"
metrics:
  duration: 11min
  completed: "2026-04-05T07:52:41Z"
---

# Phase 1 Plan 6: Repository and Service LOGH Domain Rename Summary

Renamed all 34 repository interfaces and 41 service classes from OpenSamguk naming to LOGH domain names, updating Spring Data query methods, JPQL queries, entity type references, and constructor injection throughout.

## Completed Tasks

| Task | Name | Commit | Key Changes |
|------|------|--------|-------------|
| 1 | Rename all game-app repository interfaces | 499bbd5a | 13 repos renamed, 18 repos method-updated, all JPQL updated |
| 2 | Rename service classes and update field references | 803095cc | 6 services renamed, 29 services updated with new repo/entity refs |

## What Changed

### Repository Layer (Task 1)
- **Core renames (6):** GeneralRepository->OfficerRepository, CityRepository->PlanetRepository, NationRepository->FactionRepository, TroopRepository->FleetRepository, WorldStateRepository->SessionStateRepository, EmperorRepository->SovereignRepository
- **Auxiliary renames (7):** GeneralTurnRepository->OfficerTurnRepository, NationTurnRepository->FactionTurnRepository, NationFlagRepository->FactionFlagRepository, OldGeneralRepository->OldOfficerRepository, OldNationRepository->OldFactionRepository, GeneralAccessLogRepository->OfficerAccessLogRepository, GeneralRecordRepository->OfficerRecordRepository
- **Method renames across all 31 repositories:** findByWorldId->findBySessionId, findByNationId->findByFactionId, findByGeneralId->findByOfficerId, findByCityId->findByPlanetId, findByTroopId->findByFleetId, and all compound query methods
- **JPQL @Query updates:** All entity names and field references in JPQL queries updated (e.g., `from Officer o where o.sessionId = :sessionId`)
- **Native SQL queries updated:** Message repository native queries use `session_id` column name

### Service Layer (Task 2)
- **Class renames (6):** GeneralService->OfficerService, CityService->PlanetService, NationService->FactionService, TroopService->FleetService, GeneralLogService->OfficerLogService, GeneralPoolService->OfficerPoolService
- **Constructor injection updated in all 35 service files:** officerRepository, planetRepository, factionRepository, fleetRepository, sessionStateRepository, etc.
- **Entity type references updated:** Officer, Planet, Faction, Fleet, SessionState, Sovereign throughout
- **Repository method call sites updated:** All service code now calls renamed repository methods (findBySessionId, findByFactionId, etc.)

## Deviations from Plan

None - plan executed exactly as written.

## Verification Results

- `grep -r "GeneralRepository|CityRepository|NationRepository" repository/` returns 0 results
- `grep -r "GeneralService|CityService|NationService" service/` returns 0 results
- `grep -r "findByWorldId" repository/` returns 0 results
- All old files deleted, all new files created
- No old entity imports remain in service files

## Self-Check: PASSED

All 10 key files verified present. Both commit hashes (499bbd5a, 803095cc) found in git log.
