---
phase: 04-position-card-command-authority
plan: 01
subsystem: position-card-model
tags: [position-card, command-group, officer, flyway, cqrs]
dependency_graph:
  requires: []
  provides: [PositionCard, CommandGroup, PositionCardRegistry, officer-position-cards-field]
  affects: [Officer, OfficerSnapshot, SnapshotEntityMapper, JpaWorldPorts]
tech_stack:
  added: []
  patterns: [JSONB-enum-storage, command-group-mapping, position-card-registry]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/model/PositionCard.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/CommandGroup.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/PositionCardRegistry.kt
    - backend/game-app/src/main/resources/db/migration/V32__add_position_cards_column.sql
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Officer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/memory/InMemoryWorldState.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/persist/SnapshotEntityMapper.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/turn/cqrs/persist/JpaWorldPorts.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/DeterministicReplayParityTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/turn/cqrs/TurnCoordinatorIntegrationTest.kt
decisions:
  - "82 position cards defined instead of plan's stated 77 -- plan explicitly lists 82 unique cards; count discrepancy is in plan text"
  - "Unknown commands fall back to PERSONAL group (safe default) rather than throwing"
  - "positionCards stored as List<String> (enum names) in JSONB for forward-compatible schema evolution"
metrics:
  duration: 8min
  completed: "2026-04-06T03:23:00Z"
---

# Phase 4 Plan 1: Position Card Data Model Summary

gin7 position card system with 82 organizational positions, 7 command groups, and command-to-group mapping for all 93 existing action codes, stored as JSONB on Officer entity

## What Was Built

### CommandGroup Enum (7 groups)
OPERATIONS, PERSONAL, COMMAND, LOGISTICS, PERSONNEL, POLITICS, INTELLIGENCE -- each maps to a distinct set of game commands.

### PositionCard Enum (82 cards)
Complete organizational hierarchy for both Empire and Alliance factions:
- **Universal:** PERSONAL, CAPTAIN (all officers)
- **Empire (40 cards):** Imperial Court (5), Cabinet (8), Fezzan Embassy (3), Military Affairs (5), High Command (6), Space Fleet (4), Military Police (2), Ground Forces (2), Science & Tech (1), Officer Academy (2)
- **Faction-neutral (17 cards):** Fleet unit (12), Fortress (3), Planet (2), Capital Defense (2), Intelligence (1)
- **Alliance (19 cards):** Supreme Council (7), Defense Dept (1), Joint Ops HQ (6), Logistics HQ (5), Officer Academy (2), Strategic Ops (1)

Each card defines: Korean/English names, department, maxHolders, rank range, faction constraint, and granted command groups.

### PositionCardRegistry
Static mapping of all 93 commands to command groups:
- PERSONAL: 26 commands (rest, movement, personal actions, NPC/CR specials)
- OPERATIONS: 5 commands (fleet tactical operations)
- COMMAND: 12 commands (military strategy, rebellion)
- LOGISTICS: 19 commands (infrastructure, supply, military management)
- PERSONNEL: 7 commands (hiring, promotion, appointment)
- POLITICS: 27 commands (diplomacy, governance, research)
- INTELLIGENCE: 5 commands (espionage, sabotage)

### Database Migration (V32)
Adds `position_cards` JSONB column to `officer` table with default `["PERSONAL","CAPTAIN"]`.

### Officer Entity Integration
- `positionCards: MutableList<String>` JSONB field with default cards
- `getPositionCardEnums()` helper for typed access
- `hasPositionCard(card)` convenience check

### CQRS Snapshot Layer
- OfficerSnapshot includes `positionCards` field
- SnapshotEntityMapper maps in both directions
- JpaWorldPorts.applySnapshot copies positionCards
- Test fixtures updated with default cards

## Deviations from Plan

### Minor Adjustments

**1. [Deviation] 82 position cards instead of plan's stated 77**
- **Issue:** Plan objective states "77 position cards" but the explicit card listing in the plan specifies 82 unique positions
- **Resolution:** Implemented all 82 explicitly-listed cards since they represent the complete gin7 organizational structure
- **Impact:** None -- more complete coverage

**2. [Rule 3 - Blocking] Updated test fixtures and JpaWorldPorts**
- **Found during:** Task 2
- **Issue:** Adding positionCards to OfficerSnapshot required updates to test files (DeterministicReplayParityTest, TurnCoordinatorIntegrationTest) and JpaWorldPorts.applySnapshot
- **Fix:** Added positionCards with default values to all OfficerSnapshot construction sites
- **Files modified:** 3 additional files beyond plan scope

## Known Issues

- Pre-existing test compilation error in RealtimeServiceTest.kt:60 (missing cpService parameter) -- unrelated to this plan

## Known Stubs

None -- all data is fully wired.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 183c12d4 | PositionCard enum, CommandGroup enum, Flyway V32 migration |
| 2 | 381a44b0 | PositionCardRegistry, Officer entity integration, CQRS snapshot update |
