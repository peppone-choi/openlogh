---
phase: 08-scenario-character-system
plan: 01
subsystem: tactical-engine
tags: [tactical-command, command-hierarchy, data-model, test-scaffold]
dependency_graph:
  requires: []
  provides: [TacticalCommand-sealed-class, CommandHierarchy-data-model, command-buffer-tests, hierarchy-tests]
  affects: [08-02-engine-merge, 08-03-buffer-integration]
tech_stack:
  added: []
  patterns: [sealed-class-command-pattern, disabled-test-scaffold]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/CommandHierarchy.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CommandBufferTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/CommandHierarchyTest.kt
  modified: []
decisions:
  - TacticalCommand uses sealed class with abstract battleId/officerId fields for exhaustive when() matching
  - CommandHierarchy uses MutableMap/MutableList for in-place mutation during tick processing
  - SubFleet is a separate top-level data class (not nested) for cleaner imports
  - Test scaffolds use @Disabled with fail() calls to clearly mark RED state for Plan 03
metrics:
  duration: 9m
  completed: "2026-04-07T08:46:43Z"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 08 Plan 01: TacticalCommand and CommandHierarchy Data Models Summary

TacticalCommand sealed class with 7 subtypes mapping 1:1 to WebSocket command handlers, plus CommandHierarchy/SubFleet data models for gin7 organizational command structure.

## What Was Built

### TacticalCommand Sealed Class
- 7 data class subtypes: `SetEnergy`, `SetStance`, `SetFormation`, `Retreat`, `SetAttackTarget`, `UnitCommand`, `PlanetConquest`
- Each subtype carries `battleId` and `officerId` as abstract fields from the sealed parent
- Imports existing domain types: `EnergyAllocation`, `Formation`, `UnitStance`, `ConquestRequest`
- Enables the command buffer pattern (ENGINE-02): enqueue into `ConcurrentLinkedQueue<TacticalCommand>`, drain per tick

### CommandHierarchy Data Model
- `CommandHierarchy` data class: `fleetCommander`, `subCommanders`, `successionQueue`, `crcRadius`, `commJammed`
- `SubFleet` data class: `commanderId`, `commanderName`, `unitFleetIds`, `commanderRank`
- Pure data model with no logic methods or Spring dependencies
- Ready for Phase 9-10 hierarchy processing with CRC and succession

### Wave 0 Test Scaffolds
- `CommandBufferTest`: 5 `@Disabled` tests defining ENGINE-02 buffer drain contract
- `CommandHierarchyTest`: 4 `@Disabled` tests defining ENGINE-03 hierarchy init contract
- All tests compile; skipped until Plan 03 implements `drainCommandBuffer()` and `buildCommandHierarchy()`

## Commits

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | TacticalCommand sealed class + CommandHierarchy data model | 3b6e240f | TacticalCommand.kt, CommandHierarchy.kt |
| 2 | Wave 0 test scaffolds | 805d181c | CommandBufferTest.kt, CommandHierarchyTest.kt |

## Decisions Made

1. **Sealed class pattern**: TacticalCommand uses sealed class (not interface) so `when()` expressions are exhaustive at compile time
2. **Mutable collections**: CommandHierarchy uses MutableMap/MutableList because hierarchy state mutates during tick processing (succession, CRC updates)
3. **Separate SubFleet class**: Top-level data class rather than nested class for cleaner imports across packages
4. **@Disabled + fail()**: Test scaffolds use both `@Disabled` annotation (skip in CI) and `fail()` body (clear RED signal if accidentally enabled)

## Deviations from Plan

None -- plan executed exactly as written.

## Known Issues (Pre-existing, Out of Scope)

Pre-existing test compilation errors in `DetectionServiceTest.kt`, `TacticalBattleEngineTest.kt`, and `TacticalBattleIntegrationTest.kt` due to `CommandRange` type refactor and removed timing fields. Logged to `deferred-items.md`. Not caused by this plan's changes.

## Known Stubs

None -- all files are complete data models with no placeholder values.

## Self-Check: PASSED

- [x] TacticalCommand.kt exists and contains `sealed class TacticalCommand`
- [x] CommandHierarchy.kt exists and contains `data class CommandHierarchy`
- [x] CommandBufferTest.kt exists with 5 @Test methods
- [x] CommandHierarchyTest.kt exists with 4 @Test methods
- [x] Commit 3b6e240f found in git log
- [x] Commit 805d181c found in git log
- [x] compileKotlin passes (main sources)
