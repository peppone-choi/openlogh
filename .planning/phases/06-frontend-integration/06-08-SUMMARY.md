---
phase: "06"
plan: "08"
subsystem: frontend-cleanup
tags: [cleanup, legacy-removal, samguk-deletion, navigation, tactical-ui]
dependency_graph:
  requires: [06-01, 06-02, 06-03, 06-04, 06-05, 06-06, 06-07]
  provides: [FE-08-clean-codebase]
  affects: [game-dashboard, map-viewer, command-arg-form, layout-nav, tactical-map]
tech_stack:
  added: []
  patterns: [delete-legacy-components, nav-cleanup, icon-shape-simplification]
key_files:
  deleted:
    - frontend/src/components/game/city-basic-card.tsx
    - frontend/src/components/game/general-basic-card.tsx
    - frontend/src/components/game/nation-basic-card.tsx
    - frontend/src/components/game/crew-type-browser.tsx
    - frontend/src/components/game/equipment-browser.tsx
    - frontend/src/components/game/deployment-selector.tsx
    - frontend/src/components/game/map-mode-toggle.tsx
    - frontend/src/components/game/map-3d/ (entire directory, 13 files)
    - frontend/src/app/(tutorial)/ (entire directory, 10 files)
    - frontend/src/hooks/useMap3d.ts
    - frontend/src/lib/map-3d-utils.ts
    - frontend/src/lib/map-3d-utils.test.ts
    - (all associated test files for deleted components)
  modified:
    - frontend/src/components/game/game-dashboard.tsx
    - frontend/src/components/game/command-arg-form.tsx
    - frontend/src/components/game/map-viewer.tsx
    - frontend/src/app/(game)/layout.tsx
    - frontend/src/types/index.ts
    - frontend/src/components/tactical/TacticalUnitIcon.tsx
    - frontend/src/types/tactical.ts
decisions:
  - "Removed samguk 3D map entirely rather than replacing — gin7 uses 2D galaxy map (/galaxy page)"
  - "command-arg-form: CrewTypeBrowser/EquipmentBrowser/DeploymentSelector replaced with comment; commands fall through to standard ArgField select UI"
  - "Tactical icon rule: isFlagship=true → △, all others → □, ◇ removed per user feedback"
  - "crewType field not renamed — it is a live API/DB field, not a display term; renaming requires backend changes (deferred)"
metrics:
  duration: "25 minutes"
  completed: "2026-04-07T03:09:18Z"
  tasks_completed: 2
  files_deleted: 46
  files_modified: 7
---

# Phase 06 Plan 08: Legacy Cleanup Summary

**One-liner:** Deleted all samguk-era UI components (46 files), cleaned map-3d/ and tutorial/ directories, updated navigation to gin7 Korean labels, and fixed tactical map icons to △기함 + □나머지 only.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Delete samguk component files and remove imports | 62fa3e9c | 46 files deleted, 4 modified |
| 2 | Navigation cleanup, final term audit, and typecheck | 5f09926c | layout.tsx |
| Extra | Tactical map icon fix (user feedback) | e08b7f7e | TacticalUnitIcon.tsx, tactical.ts |

## What Was Done

### Task 1: Samguk Component Deletion

Deleted all samguk-era UI components that were replaced by gin7 equivalents in plans 01-07:

**Component files deleted:**
- `city-basic-card.tsx` → replaced by `PlanetResourcePanel`
- `general-basic-card.tsx` → replaced by `OfficerInfoPanel`
- `nation-basic-card.tsx` → faction display in layout
- `crew-type-browser.tsx` → samguk crew type browser (병종)
- `equipment-browser.tsx` → samguk weapon/book/horse items
- `deployment-selector.tsx` → samguk troop deployment

**Directories deleted:**
- `map-3d/` (13 files): Map3dScene, CityModel, TerrainMesh, NationOverlay, SeasonEffects, HeightMapGenerator, RoadOverlay, HoverTooltip, CameraController, UnitMarkers3d, RenderLoop, SceneSetup, CastleLoader
- `(tutorial)/` (10 files): all samguk tutorial pages

**Orphaned helpers also deleted:**
- `map-mode-toggle.tsx`, `useMap3d.ts`, `map-3d-utils.ts`, `map-3d-utils.test.ts`

**Import cleanups:**
- `game-dashboard.tsx`: removed CityBasicCard/NationBasicCard/GeneralBasicCard imports and their JSX grid block
- `command-arg-form.tsx`: removed CrewTypeBrowser/EquipmentBrowser/DeploymentSelector imports and rich browser JSX blocks; commands now fall through to standard ArgField UI
- `map-viewer.tsx`: removed Map3dScene dynamic import, useMap3d hook, mapMode state, 3D render branch, and MapModeToggle

**Types cleaned:**
- `types/index.ts`: removed `MapRenderMode`, `Map3dConfig`, `SpotModelType`, `CityModelType`, `UnitModelType`, `TerrainType` samguk 3D type aliases

### Task 2: Navigation Cleanup

Updated `layout.tsx` navSections:
- `/battle` label changed from `'전투 검토'` to `'전술전 목록'`
- Added `/general` nav item with label `'내 장교'`
- Added `/galaxy` nav item with label `'은하 지도'`

All other nav labels were already using correct gin7 Korean terms:
- `현재 행성`, `진영 행성`, `진영 현황`, `진영 장교`, `전체 장교`, `함대 편성`, `작전실`, `원수/의장`, etc.

### Extra: Tactical Map Icon Fix (User Feedback)

Simplified `TacticalUnitIcon.tsx` icon logic per user specification:
- **Before:** flagship → △, battleship/cruiser/etc → □, destroyer → ◇, carrier → △
- **After:** `isFlagship=true` → △ (triangle), everything else → □ (square), ◇ (diamond) completely removed
- Added `isFlagship?: boolean` optional field to `TacticalUnit` type in `tactical.ts`
- Removed entire `SHIP_SHAPE` map and `ShapeType` type alias
- `isFlagship` check uses `unit.isFlagship === true || shipClass === 'flagship'` for backward compat

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Orphaned map-3d helper files**
- **Found during:** Task 1 post-deletion grep audit
- **Issue:** `map-mode-toggle.tsx`, `useMap3d.ts`, `map-3d-utils.ts` were not in the plan's delete list but became orphaned (zero importers) after map-3d/ deletion
- **Fix:** Deleted all three files plus `map-3d-utils.test.ts`
- **Files modified:** 4 additional files deleted
- **Commit:** 62fa3e9c

### User-Requested Addition (Extra Task)

**1. Tactical map icon rule change**
- **Requested:** After plan tasks, fix TacticalUnitIcon.tsx to use only 2 shapes: △ flagship, □ everything else
- **Action:** Rewrote shape selection logic, removed diamond render branch, added `isFlagship?` to TacticalUnit type
- **Commit:** e08b7f7e

## TypeScript Status

- **Files changed by this plan:** 0 new errors
- **Pre-existing errors:** fleet-composition-panel.tsx, fleet-unit-card.tsx, planet-resource-panel.tsx, troop/page.tsx — all introduced by gin7 plans 01-07, not regressions from this plan
- **Stale .next/types cache:** cleared (had tutorial page references)

## Known Stubs

- `crewType` field in `types/index.ts` lines 163 and 1090 and throughout active game pages: this is a live backend API field (`crew_type` DB column), not a display term. Renaming requires backend Kotlin entity + DTO changes. Deferred to a future plan.

## Self-Check: PASSED

Files verified:
- `FOUND: frontend/src/components/tactical/TacticalUnitIcon.tsx` (modified)
- `FOUND: frontend/src/app/(game)/layout.tsx` (modified)
- map-3d directory: DELETED
- tutorial directory: DELETED

Commits verified:
- 62fa3e9c: present
- 5f09926c: present
- e08b7f7e: present
