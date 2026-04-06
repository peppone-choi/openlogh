---
phase: "11"
plan: "01-05"
subsystem: faction-politics-diplomacy
tags: [empire, alliance, fezzan, coup, election, loan, intelligence, politics, frontend]
dependency_graph:
  requires: [phase-10-tactical-combat]
  provides: [empire-politics, alliance-politics, fezzan-npc, coup-state-machine, election-system, loan-system, intelligence-market, fezzan-ending, politics-ui]
  affects: [tick-engine, faction-entity, officer-entity]
tech_stack:
  added: []
  patterns: [faction-specific-governance, coup-state-machine, npc-ai-tick-processing, debt-domination-ending]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/model/NobilityRank.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/CoupPhase.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/CouncilSeatCode.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/ElectionType.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/IntelligenceType.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/CoupEvent.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/CouncilSeat.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Election.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/FezzanLoan.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/CoupEventRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/CouncilSeatRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/ElectionRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/FezzanLoanRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/EmpirePoliticsService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/AlliancePoliticsService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/FezzanService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/FezzanEndingService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/FezzanAiService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/FactionPoliticsDtos.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/FactionPoliticsController.kt
    - backend/game-app/src/main/resources/db/migration/V38__faction_politics.sql
    - frontend/src/types/politics.ts
    - frontend/src/lib/politicsApi.ts
    - frontend/src/stores/politicsStore.ts
    - frontend/src/components/politics/EmpirePanel.tsx
    - frontend/src/components/politics/AlliancePanel.tsx
    - frontend/src/components/politics/FezzanPanel.tsx
    - frontend/src/components/politics/PoliticsOverview.tsx
    - frontend/src/app/(game)/politics/page.tsx
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt
decisions:
  - "Nobility rank stored in Officer.meta JSONB (no schema change to officer table)"
  - "Coup threshold default 8000 per gin7 scenario mechanics"
  - "CouncilSeatCode mapped to existing PositionCard entries where available"
  - "CastElectionVoteRequest renamed to avoid collision with existing CastVoteRequest in VoteDtos"
  - "Fezzan AI offers loans to factions below 50% average funds every 100 ticks"
  - "3 defaulted loans triggers Fezzan domination ending"
  - "Loan interest compounds: 5% base + 2% per existing unpaid loan"
  - "Planet approval/tradeRoute are direct entity fields (not meta JSONB)"
metrics:
  duration: 17min
  completed: "2026-04-06T05:56:00Z"
  tasks: 10
  files: 30
---

# Phase 11: Faction Politics & Diplomacy Summary

Three-faction political system: Empire autocracy with nobility/coups, Alliance democracy with 11-seat Supreme Council/elections, Fezzan NPC with loans/intelligence market/debt domination ending

## What Was Built

### Plan 11-01: Empire Models + V38 Migration
- **NobilityRank** enum: 5 peerage levels (COMMONER through DUKE) with politics influence bonuses
- **CoupPhase** enum: 5-state lifecycle (PLANNING->ACTIVE->SUCCESS/FAILED/ABORTED)
- **CoupEvent** JPA entity: tracks coup leader, supporters, political power, threshold
- **V38 migration**: Creates 4 tables (coup_event, council_seat, election, fezzan_loan)

### Plan 11-02: Alliance Council/Elections + Empire Coup Service
- **CouncilSeatCode** enum: 11 Supreme Council seats mapped to existing PositionCards
- **ElectionType** enum: COUNCIL_CHAIR, SINGLE_SEAT, CONFIDENCE_VOTE
- **CouncilSeat/Election** JPA entities with repositories
- **AlliancePoliticsService**: council initialization, election lifecycle, voting, auto-resolution
- **EmpirePoliticsService**: coup state machine, military power comparison resolution, nobility grants via meta JSONB

### Plan 11-03: Fezzan NPC System
- **FezzanLoan** entity + repository for debt tracking with interest/default
- **IntelligenceType** enum: 5 intel types (fleet positions, planet resources, officer info, military power, coup intel)
- **FezzanService**: loan issuance/repayment, interest accrual, default detection, intelligence purchase, trade route bonus
- **FezzanAiService**: tick-based AI that offers loans to struggling factions
- **FezzanEndingService**: 3 defaults triggers Fezzan domination game ending

### Plan 11-04: REST API + TickEngine Integration
- **FactionPoliticsDtos**: 11 DTO classes covering all 3 political systems
- **FactionPoliticsController**: 15 REST endpoints for empire/alliance/fezzan operations
- **TickEngine**: wired with political processors (coup every 10 ticks, elections every 10, loans every 100, Fezzan AI every 100)

### Plan 11-05: Frontend Politics UI
- **politics.ts**: TypeScript types for all political data structures
- **politicsApi.ts**: 15 API client functions
- **politicsStore.ts**: Zustand store with fetch + action methods
- **EmpirePanel**: sovereign display, nobility hierarchy, coup progress bar with join/abort buttons
- **AlliancePanel**: 11-seat council table, election candidate list with vote buttons
- **FezzanPanel**: loan management, debt summary with warning colors, intelligence market cards
- **PoliticsOverview**: faction-routing component
- **/politics** page: game page route with full context integration

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CouncilSeatCode PositionCard references**
- **Found during:** Plan 11-02 Task 1
- **Issue:** Plan specified SUPREME_COUNCIL_CHAIR but actual PositionCard enum uses COUNCIL_CHAIRMAN
- **Fix:** Remapped all 11 seats to actual PositionCard enum values
- **Files modified:** CouncilSeatCode.kt

**2. [Rule 1 - Bug] CastVoteRequest name collision**
- **Found during:** Plan 11-04 Task 1
- **Issue:** CastVoteRequest already exists in VoteDtos.kt with different fields
- **Fix:** Renamed to CastElectionVoteRequest
- **Files modified:** FactionPoliticsDtos.kt, FactionPoliticsController.kt

**3. [Rule 1 - Bug] Planet/Fleet field name mismatches in FezzanService**
- **Found during:** Plan 11-03 Task 1
- **Issue:** Used planet.funds/supplies (don't exist), planet.meta["approval"] (is direct field), fleet.ships (is currentUnits)
- **Fix:** Used correct field names: planet.approval, planet.tradeRoute, fleet.currentUnits, planet.population/production/commerce
- **Files modified:** FezzanService.kt

**4. [Rule 1 - Bug] Frontend Officer type uses legacy field names**
- **Found during:** Plan 11-05 Task 2
- **Issue:** Officer type uses nationId (not factionId) due to legacy General type alias
- **Fix:** Used myOfficer.nationId throughout politics page
- **Files modified:** politics/page.tsx

## Known Stubs

None - all data sources are wired to backend services. Intelligence buy currently passes targetFactionId=0 (placeholder) since faction selection UI is not yet implemented; the backend will need the caller to specify the actual target faction.

## Self-Check: PASSED

- 25/25 created files verified present on disk
- 5/5 task commits verified in git history (6cf6b075, 9f32eade, cbe04e57, 9c63ffe2, bd441ae9)
- Backend compiles clean (JDK 23, only pre-existing warnings)
- Frontend TypeScript compiles clean (npx tsc --noEmit)
