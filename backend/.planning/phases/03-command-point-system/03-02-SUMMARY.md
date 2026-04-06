---
phase: 03-command-point-system
plan: 02
subsystem: command-points
tags: [cp-service, dual-pool, cross-use, recovery, tdd]
dependency_graph:
  requires: [03-01]
  provides: [CpService, CpDeductionResult, BaseCommand.getCommandPoolType, CommandTableEntry.poolType]
  affects: [RealtimeService, CommandService, CommandExecutor]
tech_stack:
  added: []
  patterns: [dual-pool-deduction, cross-use-penalty, stat-based-recovery]
key_files:
  created:
    - game-app/src/main/kotlin/com/openlogh/service/CpService.kt
    - game-app/src/test/kotlin/com/openlogh/service/CpServiceTest.kt
  modified:
    - game-app/src/main/kotlin/com/openlogh/command/BaseCommand.kt
    - game-app/src/main/kotlin/com/openlogh/dto/CommandDtos.kt
decisions:
  - Default pool type is PCP; military commands will override to MCP in future phases
  - Cross-use deducts from the OTHER pool (not primary), leaving primary unchanged
  - poolType in DTO is String ("PCP"/"MCP") for JSON serialization simplicity
metrics:
  duration: 2min
  completed: "2026-04-06"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 2
---

# Phase 3 Plan 2: CP Deduction Service Summary

CpService with dual PCP/MCP pool deduction, 2x cross-use penalty, and stat-based regeneration; BaseCommand declares pool type for all commands.

## What Was Built

### CpService (game-app/src/main/kotlin/com/openlogh/service/CpService.kt)

- `deductCp(officer, cost, poolType)` -- tries primary pool first, falls back to cross pool at 2x cost, returns structured `CpDeductionResult`
- `regeneratePcpMcp(officer)` -- PCP regen = floor((politics+administration)/20)+1, MCP regen = floor((command+mobility)/20)+1, capped at max
- `getExpStatsForPool(poolType)` -- returns the 4 stats in each category for experience gain routing

### BaseCommand Pool Type

- `getCommandPoolType(): StatCategory` added with default PCP
- Individual command overrides (MCP for military/scheme commands) deferred to future phases when commands are migrated

### CommandTableEntry DTO

- `poolType: String = "PCP"` field added for frontend consumption

## Test Coverage

13 unit tests in CpServiceTest covering:
- Primary pool deduction (PCP and MCP)
- Exact-amount deduction emptying pool
- Cross-use at 2x cost (both directions)
- Insufficient pool failure (both pools)
- Stat-based regeneration with correct formulas
- Cap at max values
- Already-at-max stays unchanged
- Low-stat minimum regen of 1

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None - all functionality is fully wired with real logic.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 2196e6b3 | CpService + CpDeductionResult + 13 tests |
| 2 | cb10bcf1 | BaseCommand.getCommandPoolType + CommandTableEntry.poolType |

## Self-Check: PASSED
