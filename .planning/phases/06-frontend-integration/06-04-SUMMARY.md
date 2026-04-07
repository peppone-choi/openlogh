---
phase: "06"
plan: "04"
subsystem: frontend-tactical-battle-view
tags: [react-three-fiber, battle-ui, tactical-map, websocket]
dependency_graph:
  requires: ["06-01", "tacticalStore (Phase 3)"]
  provides: ["BattleCloseView", "BattleCloseViewScene", "BattleCloseViewPanel", "TacticalMapR3F"]
  affects: ["battle/page.tsx"]
tech_stack:
  added: []
  patterns:
    - "React Three Fiber Canvas with Suspense wrapper pattern"
    - "useFrame hook for per-frame animation (effects, explosions)"
    - "useTacticalStore selector for reactive battle state"
    - "Fixed fullscreen overlay (position:fixed, z-index:50) for battle view"
key_files:
  created:
    - frontend/src/components/tactical/BattleCloseViewScene.tsx
    - frontend/src/components/tactical/TacticalMapR3F.tsx
    - frontend/src/components/tactical/BattleCloseViewPanel.tsx
    - frontend/src/components/tactical/BattleCloseView.tsx
  modified:
    - frontend/src/app/(game)/battle/page.tsx
decisions:
  - "Used inline style objects (not Tailwind) for R3F-adjacent panel components to avoid className conflicts with Canvas context"
  - "Effect deduplication uses a Set<string> keyed by event type+ids+value to prevent duplicate renders"
  - "effectKeyCounter is module-level to ensure unique React keys across re-renders"
  - "BattleCloseViewPanel exports buildPanelProps helper to derive panel data from TacticalUnit without requiring full officer API data"
  - "Unused FACTION_META record in BattleCloseView kept minimal — faction colors derived from attackerFactionId comparison"
metrics:
  duration: "~20 minutes"
  completed_date: "2026-04-07T02:44:10Z"
  tasks: 2
  files: 5
---

# Phase 06 Plan 04: Battle Close-Range View Summary

**One-liner:** gin7-style split-screen battle view with R3F combat drama (top) and 3D tactical grid map (bottom), integrated into battle/page.tsx via tacticalStore.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | BattleCloseViewScene + TacticalMapR3F | 5b5fc879 | BattleCloseViewScene.tsx, TacticalMapR3F.tsx |
| 2 | BattleCloseViewPanel + BattleCloseView + battle page | c67c7d5c | BattleCloseViewPanel.tsx, BattleCloseView.tsx, battle/page.tsx |

## What Was Built

### BattleCloseViewScene.tsx
R3F Canvas for the top-center combat drama column:
- Camera: PerspectiveCamera fov=60, position [0, 8, 12]
- Attacker units placed on the left (x=-3 to -8), defender units on the right (x=3 to 8), stacked vertically for multiples
- Unit blocks: BoxGeometry scaled by `ships/300`, clamped 0.3-3.0 width. Empire=blue (`#4466ff`), Alliance=red (`#ff4444`)
- Flagship: taller geometry (height 0.8), gold emissive color, officer name rendered via `<Text>` from drei
- BEAM effect: `<Line>` between unit positions, yellow, auto-removed after 400ms
- MISSILE effect: lerp-animated SphereGeometry over 500ms
- EXPLOSION effect: `<pointLight>` intensity=5 decaying to 0 over 300ms + scale pulse via useFrame
- Event deduplication with `processedEvents` Set to avoid re-triggering effects on re-renders

### TacticalMapR3F.tsx
R3F Canvas for the bottom tactical overview:
- Camera: fov=50, position [0, 20, 15] looking down at grid
- `<gridHelper>` 40x40 with blue lines (`#1a3060`, `#0d1830`)
- Unit blocks sized by `ships/300 * 1.5`, positioned by `posX * 0.4, posY * 0.4`
- Flagship marked with inverted gold ConeGeometry above the block
- `<OrbitControls>` enabled for zoom/rotate/pan

### BattleCloseViewPanel.tsx
Officer info panel (ally=dark red-brown `#3a1010`, enemy=dark blue-navy `#0d1a3a`):
- Officer portrait placeholder (60x60px, faction-colored border)
- Faction name, officer name, rank title
- Fleet/unit number label
- Ship class (Korean mapped: battleship→전함, cruiser→순양함, etc.) + ships/shipsMax count
- Morale progress bar filled with faction color
- Weapon name field
- Exports `buildPanelProps` helper to derive panel data from `TacticalUnit`

### BattleCloseView.tsx
Root layout component (reads from tacticalStore, no props):
- Fixed fullscreen overlay (position:fixed, inset:0, z-index:50, background:#000)
- Top section (45% height): flex-row with ally panel (180px) | BattleCloseViewScene (flex:1) | enemy panel (180px)
- Bottom section (flex:1, min-height:0): TacticalMapR3F filling full width/height
- "전술전 종료" button (top-right, absolute positioned) calls `clearBattle()`

### battle/page.tsx integration
- Imports `useTacticalStore` and `BattleCloseView`
- When `currentBattle` is non-null, renders `<BattleCloseView />` instead of the regular page content
- Existing war status / military power / frontline tabs are fully preserved

## Deviations from Plan

None - plan executed exactly as written.

Pre-existing TypeScript errors in `battle/page.tsx` (OpenSamguk field names like `nationId`, `crew`, `train`, `atmos`, `wallMax` that have not been migrated to LOGH naming) were not introduced by this plan and are out of scope.

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| Officer portrait "초상화" placeholder text | BattleCloseViewPanel.tsx | No officer portrait image system exists yet; future plan will wire actual portrait assets |
| `officer.rankTitle` always empty string | BattleCloseView.tsx (buildPanelProps) | TacticalUnit does not carry rank data; requires officer API fetch or rank field addition |
| `unit.weaponName` always empty string | BattleCloseView.tsx (buildPanelProps) | TacticalUnit does not carry weapon name; requires fleet weapon data from backend |
| Faction name hardcoded as 은하제국/자유행성동맹 | BattleCloseView.tsx | Faction name lookup by ID requires faction store integration; acceptable for current phase |

These stubs do not prevent the plan goal (battle view layout + R3F rendering) from being achieved. The visual structure, effect system, and tactical map are fully functional with live tacticalStore data.

## Self-Check: PASSED

Files created:
- frontend/src/components/tactical/BattleCloseViewScene.tsx — FOUND
- frontend/src/components/tactical/TacticalMapR3F.tsx — FOUND
- frontend/src/components/tactical/BattleCloseViewPanel.tsx — FOUND
- frontend/src/components/tactical/BattleCloseView.tsx — FOUND

Commits:
- 5b5fc879 — FOUND (feat(06-04): add BattleCloseViewScene R3F combat and TacticalMapR3F grid map)
- c67c7d5c — FOUND (feat(06-04): add BattleCloseViewPanel, BattleCloseView layout, and battle page integration)

TypeScript errors in new tactical components: 0
