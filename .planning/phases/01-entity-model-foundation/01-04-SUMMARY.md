---
phase: 01-entity-model-foundation
plan: 04
subsystem: gateway + game-app entities
tags: [entity-rename, domain-mapping, gateway, auxiliary-entities]
dependency_graph:
  requires: [01-01]
  provides: [gateway-session-state, officer-turn, faction-turn, faction-flag, old-officer, old-faction, officer-record, officer-access-log]
  affects: [gateway-service-layer, game-app-repositories, game-app-services]
tech_stack:
  added: []
  patterns: [entity-rename-with-fk-propagation]
key_files:
  created:
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/entity/SessionState.kt
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/repository/SessionStateRepository.kt
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/dto/SessionStateResponse.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/OfficerTurn.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/FactionTurn.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/FactionFlag.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/OldOfficer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/OldFaction.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/OfficerRecord.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/OfficerAccessLog.kt
  modified:
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/service/WorldService.kt
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/controller/WorldController.kt
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/orchestrator/WorldActivationBootstrap.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Diplomacy.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Event.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Message.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/RankData.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Board.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/BoardComment.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Auction.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/AuctionBid.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Betting.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/BetEntry.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Vote.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/VoteCast.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Tournament.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/WorldHistory.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/YearbookHistory.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/TrafficSnapshot.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/SelectPool.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Record.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/HallOfFame.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/GameHistory.kt
  deleted:
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/entity/WorldState.kt
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/repository/WorldStateRepository.kt
    - backend/gateway-app/src/main/kotlin/com/openlogh/gateway/dto/WorldStateResponse.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/GeneralTurn.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/NationTurn.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/NationFlag.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/OldGeneral.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/OldNation.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/GeneralRecord.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/GeneralAccessLog.kt
decisions:
  - Gateway WorldState renamed to SessionState with @Table("session_state") matching V28 migration
  - NationAuxKey enum renamed to FactionAuxKey with columnDefinition "faction_aux_key"
  - All worldId FKs renamed to sessionId across 20+ entity files
  - HallOfFame.generalNo and GameHistory.winnerNation renamed for domain consistency
metrics:
  duration: 4min
  completed: 2026-04-05
---

# Phase 1 Plan 4: Gateway + Auxiliary Entity Renames Summary

Gateway WorldState -> SessionState with full service/controller/orchestrator propagation; 7 auxiliary entities renamed (turns, flags, old records, access logs); worldId -> sessionId FK rename across all 20+ game-app entity files.

## Task Results

### Task 1: Rename gateway WorldState to SessionState + update gateway layer
- **Commit:** 3fd14511
- **Entity:** WorldState.kt -> SessionState.kt with `@Table(name = "session_state")`
- **Repository:** WorldStateRepository -> SessionStateRepository
- **DTO:** WorldStateResponse -> SessionStateResponse with `from(SessionState)` factory
- **Service:** WorldService updated to use SessionStateRepository, SessionState types
- **Controller:** WorldController updated to use SessionStateResponse
- **Orchestrator:** WorldActivationBootstrap updated to use SessionState
- **Old files deleted:** WorldState.kt, WorldStateRepository.kt, WorldStateResponse.kt

### Task 2: Rename auxiliary game-app entities + FK propagation
- **Commit:** b445c814
- **Primary renames (7 entities):**
  - GeneralTurn -> OfficerTurn (`@Table("officer_turn")`)
  - NationTurn -> FactionTurn (`@Table("faction_turn")`)
  - NationFlag -> FactionFlag (`@Table("faction_flag")`, enum NationAuxKey -> FactionAuxKey)
  - OldGeneral -> OldOfficer (`@Table("old_officer")`, generalNo -> officerNo)
  - OldNation -> OldFaction (`@Table("old_faction")`, nation -> faction)
  - GeneralRecord -> OfficerRecord (`@Table("officer_record")`)
  - GeneralAccessLog -> OfficerAccessLog (`@Table("officer_access_log")`)
- **FK renames across 20 entity files:**
  - worldId/world_id -> sessionId/session_id (all entity files)
  - generalId/general_id -> officerId/officer_id (BetEntry, VoteCast, Tournament, SelectPool)
  - nationId/nation_id -> factionId/faction_id (RankData, Board, Vote, Diplomacy)
  - authorGeneralId -> authorOfficerId (Board, BoardComment)
  - sellerGeneralId/buyerGeneralId/hostGeneralId -> sellerOfficerId/buyerOfficerId/hostOfficerId (Auction)
  - bidderGeneralId -> bidderOfficerId (AuctionBid)
  - srcNationId/destNationId -> srcFactionId/destFactionId (Diplomacy)
  - winnerNation -> winnerFaction (GameHistory)
  - generalNo -> officerNo (HallOfFame)
- **Index rename:** idx_records_world_type -> idx_records_session_type (Record.kt)
- **Comment updates:** Record.kt KDoc updated to use officer/faction/session terminology

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Completeness] Extended FK renames to HallOfFame and GameHistory**
- **Found during:** Task 2
- **Issue:** HallOfFame.generalNo and GameHistory.winnerNation used old domain names but were not explicitly listed in the plan
- **Fix:** Renamed generalNo -> officerNo and winnerNation -> winnerFaction for consistency
- **Files modified:** HallOfFame.kt, GameHistory.kt

**2. [Rule 2 - Completeness] Updated Record.kt KDoc comments**
- **Found during:** Task 2
- **Issue:** Record.kt contained comments referencing general_action, nation_history, world_record using old domain terminology
- **Fix:** Updated all comments to use officer/faction/session domain names
- **Files modified:** Record.kt

## Known Stubs

None - all entity renames are complete with proper @Table annotations matching V28 migration.

## Self-Check: PASSED

- All 10 created files verified present
- Both commits (3fd14511, b445c814) verified in git log
- No worldId/generalId/nationId field references remain in entity files
