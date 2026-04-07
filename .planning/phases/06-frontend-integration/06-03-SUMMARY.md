---
phase: "06"
plan: "03"
subsystem: frontend-tactical
tags: [konva, tactical, gin7, battle-map, minimap, info-panel]
dependency_graph:
  requires: ["06-01"]
  provides: [tactical-map-ui, unit-icons, command-range, minimap, info-panel]
  affects: [tactical-page]
tech_stack:
  added: []
  patterns: [react-konva-canvas, konva-animation, absolute-overlay-panels]
key_files:
  created:
    - frontend/src/components/tactical/TacticalUnitIcon.tsx
    - frontend/src/components/tactical/CommandRangeCircle.tsx
    - frontend/src/components/tactical/MiniMap.tsx
    - frontend/src/components/tactical/InfoPanel.tsx
  modified:
    - frontend/src/components/tactical/BattleMap.tsx
    - frontend/src/app/(game)/tactical/page.tsx
decisions:
  - "Used unitType field (existing on TacticalUnit) as ship class key instead of a separate shipClass field not present in the type"
  - "BattleMap converted from SVG to React Konva Stage for consistency with gin7 dot-style rendering"
  - "MiniMap and InfoPanel implemented as pure React/HTML overlays (not Konva) for simpler DOM positioning"
  - "Pre-existing 1519 TS errors in other codebase files not touched — out of scope per deviation rules"
metrics:
  duration_minutes: 25
  completed_date: "2026-04-06"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 2
---

# Phase 06 Plan 03: Gin7 Tactical Map UI Summary

**One-liner:** React Konva tactical battle map with gin7 dot-style unit icons (△□◇), animated command range circle, orange/black minimap overlay, and Korean info panel.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | TacticalUnitIcon and CommandRangeCircle | 998789a2 | TacticalUnitIcon.tsx, CommandRangeCircle.tsx |
| 2 | BattleMap with MiniMap and InfoPanel — gin7 tactical layout | 7ab4b6e0 | BattleMap.tsx, MiniMap.tsx, InfoPanel.tsx, tactical/page.tsx |

## What Was Built

### TacticalUnitIcon.tsx
- React Konva `Group` component rendering gin7 dot-style ship icons
- `SHIP_SHAPE` map: 14 ship classes → triangle (flagship/carriers) / square (battleship/cruiser/transport/etc) / diamond (destroyer)
- `SHIP_LETTER` map: letter label centered inside each shape (B/C/S/D/F/A/E/T/H/L/M)
- Faction colors: empire=`#4466ff`, alliance=`#ff4444` derived from unit's `side` field
- Damaged units (ships < 50% maxShips) render at 0.5 fill opacity
- Selected unit shows dashed white glow ring (Circle radius=14, dash=[2,2])

### CommandRangeCircle.tsx
- React Konva `Group` with two circles: animated inner + static dashed outer boundary
- Uses `Konva.Animation` in `useEffect` to expand radius 0→maxRadius over 3s loop
- Faction-colored stroke derived from `side` prop

### BattleMap.tsx
- Converted from SVG to React Konva `Stage` with three layers: background / command-range / units
- Background: `#000008` space fill + 200 deterministically placed stars + faint blue grid lines every 50px
- Renders `TacticalUnitIcon` for each unit at `posX * scaleX, posY * scaleY`
- `CommandRangeCircle` rendered for selected unit when `commandRange > 0`
- Click on background deselects; click on unit calls `onSelectUnit(fleetId)`

### MiniMap.tsx
- Pure React/HTML overlay (not Konva), fixed 120×120px
- Orange/black color scheme with `#ff8800` border
- Grid lines at 25%/50%/75% positions
- Unit dots: white=self, `#ff8800`=ally, `#884400`=enemy
- Title "성계 미니맵" in orange monospace 9px
- Positioned absolute top-right via inline style

### InfoPanel.tsx
- Pure React/HTML overlay, 200px wide
- Dark panel `#0d0d1a` with `#333` border
- 8 Korean info rows: 진영 / 입력 턴 / UC/RC 날짜 / 성계명 / 작전명 / 작전총사령관 / 작전공적 / 총물자량
- Positioned absolute bottom-right via inline style

### tactical/page.tsx
- Top toolbar: 5 Korean buttons — 작전조회, 함대정보, 성계정보, 자함대, 해결
- Layout: `flex flex-col h-screen` — toolbar row + flex main area
- Main area: relative BattleMap container (with MiniMap + InfoPanel overlays) + 256px right sidebar
- Right sidebar: EnergyPanel, FormationSelector, retreat button, BattleStatus
- Local `selectedUnitId` state wired to BattleMap `onSelectUnit` and InfoPanel

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] JSX return type namespace error**
- **Found during:** Task 1 TypeScript verification
- **Issue:** `JSX.Element` return type annotation caused `Cannot find namespace 'JSX'` in strict mode
- **Fix:** Removed explicit return type annotations from both components (TypeScript infers correctly)
- **Files modified:** TacticalUnitIcon.tsx, CommandRangeCircle.tsx
- **Commit:** 998789a2

**2. [Rule 1 - Bug] unitType used instead of shipClass**
- **Found during:** Task 1 implementation
- **Issue:** Plan references `shipClass` but `TacticalUnit` in existing types has `unitType: string` only
- **Fix:** Used `unit.unitType` as the ship class key in SHIP_SHAPE/SHIP_LETTER maps — no type changes needed
- **Files modified:** TacticalUnitIcon.tsx

**3. [Rule 1 - Bug] git add path with parentheses**
- **Found during:** Task 2 commit
- **Issue:** zsh glob expansion failed on `frontend/src/app/(game)/tactical/page.tsx`
- **Fix:** Quoted the path in git add command
- **Commit:** 7ab4b6e0

## Known Stubs

- `InfoPanel` props `ucYear`, `ucMonth`, `starSystemName`, `operationName`, `commanderName`, `commanderRank`, `meritPoints` default to placeholder values — the tactical page passes no values for these, so the info panel shows defaults (UC 800년 1월, "미지정", etc.). A future plan should wire real battle metadata from the store/API.
- `MiniMap` viewport indicator is optional and not wired from the page — shows without viewport rectangle.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| TacticalUnitIcon.tsx | FOUND |
| CommandRangeCircle.tsx | FOUND |
| MiniMap.tsx | FOUND |
| InfoPanel.tsx | FOUND |
| SUMMARY.md | FOUND |
| commit 998789a2 | FOUND |
| commit 7ab4b6e0 | FOUND |
