---
phase: "10"
plan: "01-04"
subsystem: tactical-combat
tags: [websocket, rts, combat, energy-allocation, formations, fortress-guns, frontend]
dependency_graph:
  requires: [phase-05-fleet-structure, phase-06-galaxy-map]
  provides: [tactical-battle-engine, energy-allocation, formation-system, fortress-gun-integration, tactical-battle-ui]
  affects: [fleet-entity, officer-entity, game-event-service]
tech_stack:
  added: []
  patterns: [in-memory-battle-state, tick-based-rts, svg-battle-map]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/model/EnergyAllocation.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/Formation.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/BattlePhase.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/TacticalUnitState.kt
    - backend/game-app/src/main/kotlin/com/openlogh/entity/TacticalBattle.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/TacticalBattleRepository.kt
    - backend/game-app/src/main/resources/db/migration/V37__tactical_battle.sql
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/BattleTriggerService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleController.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/TacticalBattleRestController.kt
    - frontend/src/types/tactical.ts
    - frontend/src/lib/tacticalApi.ts
    - frontend/src/stores/tacticalStore.ts
    - frontend/src/components/tactical/BattleMap.tsx
    - frontend/src/components/tactical/EnergyPanel.tsx
    - frontend/src/components/tactical/FormationSelector.tsx
    - frontend/src/components/tactical/BattleStatus.tsx
    - frontend/src/app/(game)/tactical/page.tsx
  modified: []
decisions:
  - "Active battles stored in ConcurrentHashMap for fast tick processing, persisted to DB periodically"
  - "SVG-based battle map instead of React Konva for tactical view (simpler, no extra dependency)"
  - "Energy allocation enforces sum=100 constraint with auto-redistribution in frontend sliders"
  - "Fortress gun line-of-fire hits ALL units in path including friendlies per gin7 manual"
  - "Battle timeout at 600 ticks (10 minutes) with HP ratio comparison for winner"
  - "Retreat requires 50% WARP energy allocation"
metrics:
  duration: 12min
  completed: "2026-04-06T05:28:00Z"
  tasks: 8
  files: 21
---

# Phase 10: Tactical Combat (RTS) Summary

Real-time WebSocket-based fleet battles with 6-channel energy allocation, 4 formation types, and fortress gun line-of-fire calculation

## What Was Built

### Plan 10-01: Models & Migration
- **EnergyAllocation** data class: 6 channels (BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR) with sum=100 constraint and multiplier methods
- **Formation** enum: WEDGE (+30% atk/-30% def), BY_CLASS (+10%/+10%), MIXED (neutral), THREE_COLUMN (-20% atk/+40% def)
- **BattlePhase** enum: PREPARING/ACTIVE/PAUSED/ENDED lifecycle
- **TacticalUnitState**: serializable per-unit state for JSONB storage
- **TacticalBattle** JPA entity with JSONB battle_state and participants
- **V37 migration**: tactical_battle table with session/phase/star_system indexes

### Plan 10-02: Battle Engine
- **TacticalBattleEngine**: tick-based combat processor
  - Movement AI: units approach to optimal weapon range, retreat from close range
  - BEAM/GUN weapons: damage scaled by energy allocation, attack stat, formation, morale, training
  - Shield absorption: up to 80% at full SHIELD allocation, modified by defense stat and formation
  - Command range: expands based on command stat, resets to 0 on new order
  - Morale: drops when HP < 30%, boosted by leadership > 70
  - Battle ends on elimination, full retreat, or 600-tick timeout
- **BattleTriggerService**: detects opposing fleets at same star system, creates battle instances
- **Fortress gun**: line-of-fire calculation hits ALL units in path (including friendlies per gin7), cooldown-based firing

### Plan 10-03: WebSocket & REST API
- **TacticalBattleService**: manages full lifecycle with in-memory ConcurrentHashMap for active battles
  - Player commands: setEnergyAllocation, setFormation, retreat
  - Battle completion: persists ship losses back to Fleet/Officer entities
  - Fires BattleEvent through existing GameEventService
- **TacticalBattleController**: WebSocket `@MessageMapping("/battle/{sessionId}/command")` for real-time commands
- **TacticalBattleRestController**: REST endpoints for battle queries
  - `GET /api/v1/battle/{sessionId}/active` — list active battles
  - `GET /api/v1/battle/{sessionId}/{battleId}` — get battle state
- Broadcasts via `/topic/world/{sessionId}/tactical-battle/{battleId}`

### Plan 10-04: Frontend UI
- **TacticalPage** (`/tactical`): battle list + active battle view
- **BattleMap**: SVG-based 2D tactical view with unit circles, HP bars, command range circles, faction colors
- **EnergyPanel**: 6 sliders with auto-redistribution maintaining sum=100
- **FormationSelector**: 4 formation buttons with Korean names and modifier descriptions
- **BattleStatus**: tick counter, elapsed time, side HP bars, damage event log
- **tacticalStore**: Zustand store with WebSocket subscription for real-time updates

## Decisions Made

1. **In-memory battle state**: Active battles use `ConcurrentHashMap<Long, TacticalBattleState>` for sub-millisecond tick processing, with periodic DB persistence every 10 ticks
2. **SVG battle map**: Used SVG instead of React Konva to avoid adding another canvas dependency; SVG provides sufficient rendering for unit positions and HP bars
3. **Fortress friendly fire**: Faithful to gin7 manual - fortress guns fire in a line and hit ALL units in path including the fortress faction's own ships
4. **600-tick timeout**: Maximum battle duration of 10 minutes real-time; winner determined by remaining HP ratio
5. **WARP retreat threshold**: 50% WARP energy required to initiate retreat, preventing instant escapes

## Deviations from Plan

None - plan executed exactly as written.

## Commits

| Plan | Hash | Message |
|------|------|---------|
| 10-01 | 3dc76158 | feat(10-01): tactical battle models, entity, and V37 migration |
| 10-02 | 9678138b | feat(10-02): tactical battle engine with energy, formations, fortress guns |
| 10-03 | 0c5a162f | feat(10-03): WebSocket battle controller, REST API, TacticalBattleService |
| 10-04 | 896ef184 | feat(10-04): frontend tactical battle UI with energy panel, formations, battle map |

## Known Stubs

None - all components are fully wired to their data sources. The tactical battle system is complete as a standalone subsystem. Integration with the game tick engine loop (calling `processSessionBattles` each tick) will be wired when Phase 2 tick engine completion is done.

## Self-Check: PASSED
