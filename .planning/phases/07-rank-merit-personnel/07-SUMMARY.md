---
phase: "07"
subsystem: rank-merit-personnel
tags: [rank, merit, personnel, promotion, demotion, authority]
dependency-graph:
  requires: [phase-01-entity-model, phase-04-position-cards, phase-06-galaxy-map]
  provides: [rank-ladder, merit-system, personnel-management, auto-promotion]
  affects: [officer-entity, position-cards, cp-pools]
tech-stack:
  added: []
  patterns: [rank-ladder-ordering, headcount-enforcement, position-card-authority-gating]
key-files:
  created:
    - backend/game-app/src/main/resources/db/migration/V36__add_merit_evaluation_fame_points.sql
    - backend/game-app/src/main/kotlin/com/openlogh/model/RankHeadcount.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/RankLadderService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/PersonnelService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/PersonnelDtos.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/PersonnelController.kt
    - frontend/src/types/personnel.ts
    - frontend/src/lib/api/personnel.ts
    - frontend/src/stores/personnelStore.ts
    - frontend/src/components/game/rank-badge.tsx
    - frontend/src/components/game/personnel-panel.tsx
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt
decisions:
  - Merit/evaluation/fame as Int fields on Officer entity (simple, sufficient range)
  - Headcount enforcement at service level (not DB constraint) for flexibility
  - Authority validation via PositionCard enum matching (EMPEROR, MILITARY_AFFAIRS_SECRETARY, etc.)
  - Auto-promotion for tier 0-4 only (Captain and below), per gin7 manual
  - Position card reset on promotion/demotion retains PERSONAL, CAPTAIN, FIEF
metrics:
  tasks-completed: 15
  plans-completed: 3
  completed: 2026-04-05
---

# Phase 7: Rank, Merit & Personnel Summary

11-tier rank ladder with merit-based ordering, auto-promotion for junior ranks, manual promote/demote with position card authority gating, and headcount limits per gin7 manual.

## Plans Executed

### Plan 07-01: Flyway Migration + Officer Entity Update + Rank Config
- **Commit:** 117ee184
- V36 migration adds `merit_points`, `evaluation_points`, `fame_points` columns to officer table
- Composite index `idx_officer_rank_ladder` on (session_id, faction_id, officer_level, merit_points) for efficient ladder queries
- Officer entity updated with 3 new JPA-mapped fields
- RankHeadcount config model: tier 10=5, tier 9=5, tier 8=10, tier 7=20, tier 6=40, tier 5=80, tiers 0-4=unlimited

### Plan 07-02: RankLadderService + PersonnelService + Controller + DTOs
- **Commit:** f632df8e
- **RankLadderService**: builds rank ladder sorted by tier desc, merit desc, fame desc, total stats desc. Auto-promote method for tiers 0-4 every 30 game days. Headcount enforcement before any promotion.
- **PersonnelService**: manual promote/demote with authority validation. Authority cards mapped per gin7:
  - Tier 10: EMPEROR only
  - Tier 5-9: EMPEROR, SUPREME_COMMANDER, MILITARY_AFFAIRS_SECRETARY, COUNCIL_DEFENSE_CHAIR
  - Tier 0-4: above + MILITARY_HR_CHIEF, DEFENSE_DEPT_CHIEF
- **PersonnelController**: REST API at `/api/world/{sessionId}/personnel` with ladder, info, promote, demote endpoints
- **PersonnelDtos**: RankLadderEntryDto, PersonnelInfoDto, PromoteDemoteRequest, PersonnelActionResponse

### Plan 07-03: Frontend Rank Display + Personnel Panel
- **Commit:** 74821531
- TypeScript types matching backend DTOs, with rank tier constants and headcount limits
- Personnel API client for all 4 endpoints
- Zustand store with full CRUD state management (load, promote, demote, auto-reload ladder)
- RankBadge component with tier-based coloring (gold/silver/bronze/gray) and MeritProgressBar
- PersonnelPanel with rank ladder table, officer detail view, promote/demote action buttons (authority-gated)

## Deviations from Plan

None - plan executed exactly as written.

## Decisions Made

1. **Merit/Evaluation/Fame as simple Int fields**: No complex schema needed. Sufficient for all gin7 ranking criteria.
2. **Authority via existing PositionCard system**: Reuses Phase 4 infrastructure. No new auth mechanism needed.
3. **Headcount at service level**: Allows runtime flexibility and easy testing vs. DB constraints.
4. **JDK 23 for compilation**: JDK 25 (system default) has Gradle compatibility issues; JDK 23 used successfully.

## Known Stubs

None - all data flows are wired to backend APIs. Merit/evaluation/fame points start at 0 and will accumulate through future game commands (Phase 9: Strategic Commands) and combat (Phase 10: Tactical Combat).

## Self-Check: PASSED
