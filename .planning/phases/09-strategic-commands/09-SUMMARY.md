---
phase: "09"
plan: "01-04"
subsystem: strategic-commands
tags: [commands, operations, logistics, intelligence, warp, occupation]
dependency_graph:
  requires: [phase-04-position-cards, phase-05-fleet-structure, phase-06-galaxy-map]
  provides: [strategic-command-layer, warp-navigation, supply-chain, reconnaissance, occupation]
  affects: [command-registry, position-card-registry, command-service]
tech_stack:
  added: []
  patterns: [command-pattern, stat-based-success, cp-deduction]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/작전수립.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/워프항행.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/장거리워프.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/물자배분.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/함선보급.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/함대재편.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/생산감독.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/정찰.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/통신방해.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/general/점거.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/nation/작전지시.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/nation/예산편성.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/command/CommandRegistry.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/PositionCardRegistry.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/ArgSchemas.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt
decisions:
  - "12 new gin7 strategic commands added across 4 command groups"
  - "Warp navigation uses mobility stat to reduce travel duration"
  - "Intelligence commands use stat-based success probability"
  - "Frontend dynamically renders new categories - no UI code changes needed"
metrics:
  duration: 14min
  completed: 2026-04-06
---

# Phase 9: Strategic Commands Summary

12 gin7-specific strategic commands enabling basic gameplay: warp navigation, operation planning, logistics, budget management, reconnaissance, communication jamming, and planet occupation.

## Plans Executed

| Plan | Name | Commits | Files |
|------|------|---------|-------|
| 09-01 | Operation Planning & Warp Navigation | 596068d7 | 9 |
| 09-02 | Logistics & Supply Chain | 3ac3aee4 | 10 |
| 09-03 | Intelligence & Occupation | 6847b93f | 8 |
| 09-04 | Frontend Compatibility | 1ef78985 | 1 |

## What Was Built

### Plan 01: Operation Planning & Warp Navigation (OPERATIONS group)
- **작전수립** (Operation Planning): Set faction strategic goals for target systems (occupy/defend/sweep). 2 MCP, 600s duration.
- **워프항행** (Warp Navigation): Move officer+fleet to adjacent star system via route. Duration reduced by mobility stat. 1 MCP.
- **장거리워프** (Long-range Warp): Multi-hop warp (2-3 systems). Engine item enables 3-hop range. 2 MCP.
- **작전지시** (Operation Directive): Faction-level strategic orders stored in faction.meta["operations"]. 3 MCP, 900s.

### Plan 02: Logistics & Supply Chain (LOGISTICS/POLITICS groups)
- **물자배분** (Supply Allocation): Transfer funds/supplies from faction to officer. 1 PCP.
- **함선보급** (Fleet Resupply): Receive ships from planet production capacity. 1 MCP.
- **함대재편** (Fleet Reorganization): Change ship class with 20% training penalty. 1 MCP, 600s.
- **생산감독** (Production Oversight): Boost planet production via administration stat. 1 PCP.
- **예산편성** (Budget Allocation): Set faction tax rate and conscription rate. 2 PCP, 600s.

### Plan 03: Intelligence & Occupation (INTELLIGENCE/OPERATIONS groups)
- **정찰** (Reconnaissance): Reveal enemy fleet positions and planet info. Intelligence-based success (50%+intel/2, cap 95%). 1 MCP.
- **통신방해** (Communication Jamming): Disrupt enemy CP recovery for 30 minutes. Intelligence-based success. 2 MCP.
- **점거** (Occupation): Seize weakly defended planets, changes ownership. Attack power vs defense comparison. 2 MCP, 900s.

### Plan 04: Frontend Compatibility
- Verified frontend command panel dynamically renders categories from backend.
- No frontend type/store changes needed -- new categories (병참, 재정) auto-appear.
- TypeScript compilation verified clean.

## Registry Updates

| Registry | Before | After | Added |
|----------|--------|-------|-------|
| CommandRegistry (officer) | 58 | 68 | +10 commands |
| CommandRegistry (faction) | 39 | 41 | +2 commands |
| PositionCardRegistry | 100 | 112 | +12 mappings |
| COMMAND_SCHEMAS | ~95 | ~107 | +12 schemas |

## New Command Categories

| Category | Type | Commands |
|----------|------|----------|
| 병참 (Logistics) | Officer | 물자배분, 함선보급, 함대재편 |
| 재정 (Finance) | Faction | 예산편성 |

## CP Pool Usage

| Command | Pool | Cost | Rationale |
|---------|------|------|-----------|
| 작전수립, 워프항행, 장거리워프, 점거, 함선보급, 함대재편, 정찰, 통신방해 | MCP | 1-3 | Military operations |
| 물자배분, 생산감독, 예산편성 | PCP | 1-2 | Administrative operations |
| 작전지시 | MCP | 3 | Strategic military command |

## Deviations from Plan

None -- plan executed exactly as written.

## Known Stubs

None -- all commands are fully implemented with stat-based calculations, success/failure logic, proper CP deduction, and command point costs.

## Self-Check: PASSED
