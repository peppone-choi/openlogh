---
phase: 01-entity-model-foundation
plan: 08
subsystem: frontend
tags: [frontend, types, stores, rename, logh-domain]
dependency_graph:
  requires: [01-07]
  provides: [frontend-logh-types, officer-store, logh-api-aliases]
  affects: [frontend/src/types, frontend/src/stores, frontend/src/lib, frontend/src/app, frontend/src/components]
tech_stack:
  added: []
  patterns: [type-alias-migration, deprecated-alias-pattern]
key_files:
  created:
    - frontend/src/stores/officerStore.ts
    - frontend/src/stores/officerStore.test.ts
  modified:
    - frontend/src/types/index.ts
    - frontend/src/lib/gameApi.ts
    - frontend/src/components/tutorial/tutorial-provider.tsx
    - 52 consumer files (store import migration)
decisions:
  - Type aliases (not field renames) for backward-compatible migration
  - Keep old field names in interfaces until backend DTO serialization confirmed
  - officerStore.ts as new file alongside generalStore.ts (not destructive rename)
metrics:
  duration: 24min
  completed: "2026-04-05T08:29:00Z"
  tasks_completed: 2
  files_changed: 59
---

# Phase 1 Plan 8: Frontend LOGH Domain Type Aliases and Store Rename Summary

LOGH domain type aliases added to types/index.ts, API object aliases in gameApi.ts, and generalStore renamed to officerStore across 59 files.

## What Was Done

### Task 1: TypeScript types, API client, and Zustand stores

**types/index.ts** - Added 25 LOGH type aliases at the bottom of the file:
- `Officer = General`, `Planet = City`, `Faction = Nation`, `Fleet = Troop`, `SessionState = WorldState`
- `BestOfficer = BestGeneral`, `OfficerTurn = GeneralTurn`, `FactionTurn = NationTurn`
- `FactionStatistic = NationStatistic`, `FactionPolicyInfo = NationPolicyInfo`
- `OfficerFrontInfo = GeneralFrontInfo`, `FactionFrontInfo = NationFrontInfo`, `PlanetFrontInfo = CityFrontInfo`
- `PlanetConst = CityConst`, `AdminOfficer = AdminGeneral`, `OfficerLogEntry = GeneralLogEntry`
- `FleetWithMembers = TroopWithMembers`, `BattleSimPlanet = BattleSimCity`, and more

**gameApi.ts** - Added API object aliases:
- `officerApi = generalApi`, `planetApi = cityApi`, `factionApi = nationApi`, `fleetApi = troopApi`
- `factionManagementApi = nationManagementApi`, `factionPolicyApi = nationPolicyApi`
- `officerLogApi = generalLogApi`

**stores/officerStore.ts** - New store replacing generalStore:
- `useOfficerStore` with `myOfficer`, `fetchMyOfficer`, `clearMyOfficer`, `officers`, `fetchOfficers`
- Persists to sessionStorage under `officer-store` key

### Task 2: Game pages and components

All 52 consumer files updated:
- Import path: `@/stores/generalStore` to `@/stores/officerStore`
- Store hook: `useGeneralStore` to `useOfficerStore`
- State fields: `myGeneral` to `myOfficer`, `fetchMyGeneral` to `fetchMyOfficer`
- Tutorial provider: `generals` to `officers` in OfficerStore setState

## Deviations from Plan

### [Rule 3 - Blocking] Pragmatic type alias approach instead of field renames

- **Found during:** Task 1
- **Issue:** Renaming interface field names (e.g., `strength` to `command`, `gold` to `funds`) in types/index.ts caused 1766 TypeScript errors across 100+ component files. The sed-based bulk rename was too aggressive, changing generic property names (`.level`, `.item`, `.gold`) in non-entity contexts.
- **Fix:** Adopted a type-alias migration pattern instead. Original interfaces keep their field names (matching current backend DTO serialization). New LOGH type names are exported as aliases. This allows all existing code to compile while new code can use LOGH names.
- **Impact:** Field-level renames (e.g., `strength` to `command`, `gold` to `funds`) are deferred to a future plan when backend DTO serialization is confirmed to use new names. Type name renames (General to Officer, City to Planet, etc.) work immediately through aliases.
- **Files modified:** types/index.ts, gameApi.ts

## Verification

- `pnpm typecheck` passes with zero errors
- `pnpm build` succeeds with all pages compiling
- `grep -r "useGeneralStore" frontend/src/` returns 0 matches (excluding generalStore.ts itself)
- All LOGH type aliases are importable and usable

## Known Stubs

- **Field-level renames deferred**: Interface fields still use OpenSamguk names (strength, intel, charm, gold, rice, crew, etc.). When backend DTOs are confirmed to serialize new field names, a follow-up plan should rename interface fields and update all component field accesses.
- **generalStore.ts retained**: The original file is kept for any potential backward compatibility needs. No code imports from it.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 40db9222 | feat(01-08): add LOGH domain type aliases and rename generalStore to officerStore |

## Self-Check: PASSED

- FOUND: frontend/src/stores/officerStore.ts
- FOUND: frontend/src/stores/officerStore.test.ts
- FOUND: .planning/phases/01-entity-model-foundation/01-08-SUMMARY.md
- FOUND: commit 40db9222
