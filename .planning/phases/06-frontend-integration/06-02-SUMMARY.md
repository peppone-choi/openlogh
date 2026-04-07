---
phase: 06-frontend-integration
plan: "02"
subsystem: frontend/galaxy-map
tags: [react-konva, galaxy-map, fleet-positions, faction-colors, ui]
dependency_graph:
  requires: []
  provides: [fleet-position-markers, movement-range-highlight, faction-shade-colors]
  affects: [frontend/src/components/galaxy, frontend/src/stores/galaxyStore, frontend/src/types/galaxy]
tech_stack:
  added: []
  patterns: [react-konva-layers, zustand-store-actions, bfs-graph-traversal]
key_files:
  created:
    - frontend/src/components/galaxy/FleetPositionMarker.tsx
  modified:
    - frontend/src/types/galaxy.ts
    - frontend/src/components/galaxy/StarSystemNode.tsx
    - frontend/src/stores/galaxyStore.ts
    - frontend/src/components/galaxy/GalaxyMap.tsx
    - frontend/src/components/galaxy/StarSystemDetailPanel.tsx
decisions:
  - "FACTION_SHADES added to galaxy.ts types — single source of truth for 5-shade palette used by both Konva and HTML layers"
  - "getFactionShadeColor uses factionId numeric fallback (1=empire, 2=alliance) for backward compat with existing StarSystem.factionId field"
  - "fetchFleetPositions is non-blocking (catches errors silently) — map works without fleet data"
  - "Movement range BFS excludes origin star — only highlights reachable destinations, not current position"
  - "StarSystemDetailPanel 생산력 shown as level*100 stub — real Planet entity data wired in future plan"
metrics:
  duration_minutes: 15
  completed_date: "2026-04-07"
  tasks_completed: 2
  files_changed: 6
---

# Phase 06 Plan 02: Galaxy Map Fleet Markers + Faction Color Enhancement Summary

Enhanced React Konva galaxy map with FACTION_SHADES 5-shade faction coloring, FleetPositionMarker triangle overlays, 2-hop movement range highlighting, and a Korean-language detail panel showing 주둔 함대 and 행성 정보.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Dot-style StarSystemNode with 5-shade faction color + FleetPositionMarker | 4490e01f | galaxy.ts, StarSystemNode.tsx, FleetPositionMarker.tsx |
| 2 | GalaxyMap fleet markers + movement range + detail panel | cdf07759 | galaxyStore.ts, GalaxyMap.tsx, StarSystemDetailPanel.tsx |

## What Was Built

### Task 1: 5-shade faction colors + FleetPositionMarker

- Added `FleetPosition` interface and `controlStrength?: number` field to `StarSystem` type
- Added `FACTION_SHADES` constant map with 5-shade palettes for empire/alliance/fezzan/rebel/neutral
- Added `getFactionShadeColor(factionId, factionType, controlStrength)` — resolves shade index via `Math.floor(strength / 20)` clamped 0-4
- Updated `StarSystemNode` to use `getFactionShadeColor` instead of raw `factionColor`, preserving existing pixel-art dot rendering
- Created `FleetPositionMarker.tsx`: Konva `RegularPolygon` (triangle, sides=3, radius=5), stacked horizontally at `x - 5 + index*10, y - 16` offset; shows ship count label; selection ring on selected fleet

### Task 2: Fleet markers, movement range, detail panel

- `galaxyStore.ts`: Added `fleetPositions: Record<number, FleetPosition[]>`, `selectedFleetId`, `fetchFleetPositions(sessionId)`, `selectFleet(id)`, `getReachableStars(mapStarId, hops)` (BFS up to N hops)
- `fetchFleetPositions` calls `GET /api/{sessionId}/fleets/positions` — non-blocking, map renders without it
- `GalaxyMap.tsx`: New movement range layer renders semi-transparent `Circle` (radius 20, fill opacity 0.1, stroke opacity 0.4) for all BFS-reachable stars within 2 hops of selected fleet; new fleet markers layer renders `FleetPositionMarker` per stationed fleet; toggle selection on click
- `StarSystemDetailPanel.tsx`: Added `주둔 함대` section (faction color badge, officer name, ship count in 척); added `행성 정보` grid (생산력, 행성 수); switched to `FACTION_SHADES` for fleet badge colors; removed `getFactionColor` import

## Deviations from Plan

### Auto-decisions

**1. [Rule 2 - Enhancement] Added fezzan/rebel shades to FACTION_SHADES**
- Plan specified empire/alliance/neutral only; added fezzan and rebel for completeness since faction_type can be 'fezzan' or 'rebel' per CLAUDE.md domain mapping
- No extra work — same pattern, avoids future rework

**2. [Rule 2 - Enhancement] Added factionType string path to getFactionShadeColor**
- Plan only showed factionId numeric; added string factionType parameter for future API responses that return type strings directly

**3. [Plan adaptation] 생산력 in detail panel uses level*100 stub**
- Real Planet entity data is not yet wired to the galaxy map API response; stub documented in Known Stubs below

## Known Stubs

| File | Line | Stub | Reason |
|------|------|------|--------|
| StarSystemDetailPanel.tsx | ~155 | `system.level * 100` for 생산력 | Planet.production not yet in GalaxyMap API response; future plan wires planet entity data to star system detail |

## Self-Check: PASSED

- FOUND: frontend/src/components/galaxy/FleetPositionMarker.tsx
- FOUND: frontend/src/types/galaxy.ts
- FOUND: frontend/src/stores/galaxyStore.ts
- FOUND: commit 4490e01f (Task 1)
- FOUND: commit cdf07759 (Task 2)
- TypeScript: 0 errors
