---
phase: "06"
plan: "05"
subsystem: frontend
tags: [commands, position-cards, cp-display, officer-info, websocket]
dependency_graph:
  requires: ["06-01"]
  provides: ["FE-04", "FE-05"]
  affects: ["frontend/src/app/(game)/commands/page.tsx"]
tech_stack:
  added: []
  patterns:
    - "publishWebSocket() helper for STOMP publish without re-connecting"
    - "CommandTableEntry shape from legacy @/types index (actionCode, commandPointCost, poolType)"
    - "Officer 8-stat type imported from @/types/officer to bypass legacy General alias"
    - "Cooldown tracking via local Map<code, endEpochMs> + 1s interval"
key_files:
  created:
    - frontend/src/components/game/officer-info-panel.tsx
    - frontend/src/components/game/cp-display.tsx
    - frontend/src/components/game/position-card-panel.tsx
    - frontend/src/components/game/command-execution-panel.tsx
  modified:
    - frontend/src/app/(game)/commands/page.tsx
    - frontend/src/lib/websocket.ts
decisions:
  - "Used REST (commandApi.execute) as authoritative command execution; WebSocket publish is fire-and-forget supplement"
  - "CommandGroup defined locally in components — not re-exported from @/types (legacy index uses string field)"
  - "Officer cast via unknown to bypass legacy General alias in officerStore"
  - "Co-located officers fetched via generalApi.listByCity(planetId) — planet=city in gin7 mapping"
metrics:
  duration_minutes: 35
  tasks_completed: 2
  files_created: 4
  files_modified: 2
  completed_at: "2026-04-07T02:50:22Z"
---

# Phase 06 Plan 05: Strategic Command Screen Summary

**One-liner:** Full strategic command screen with 8-stat officer panel, position card tabs, CP display, and real-time command execution via REST+WebSocket.

## What Was Built

### Task 1: OfficerInfoPanel, CpDisplay, PositionCardPanel

**OfficerInfoPanel** (`officer-info-panel.tsx`):
- Renders all 8 gin7 stats (통솔/지휘/정보/정치/운영/기동/공격/방어) as color-coded progress bars
- Color scheme: yellow=통솔, orange=지휘, cyan=정보, green=정치, purple=운영, sky=기동, red=공격, blue=방어
- Shows officer name, rank title badge, and position card count summary

**CpDisplay** (`cp-display.tsx`):
- Dual progress bars: PCP (blue) and MCP (orange)
- Shows current/max values with regen hint ("5분마다 N씩 회복")

**PositionCardPanel** (`position-card-panel.tsx`):
- Derives available CommandGroups from officer's position card codes using prefix mapping
- Renders tab buttons (전체 + available groups) with card count badges
- Active tab: `bg-blue-700`, inactive: `bg-slate-800`

### Task 2: CommandExecutionPanel + commands/page.tsx rewrite

**CommandExecutionPanel** (`command-execution-panel.tsx`):
- Flat list of `CommandTableEntry` items filtered by selected CommandGroup
- Each row: name, CP cost badge (PCP=blue, MCP=orange), cooldown countdown, execute button
- Execute button disabled when: insufficient CP, on cooldown, or another command executing
- On execute: publishes to WebSocket `/app/command/{sessionId}/execute` + calls REST `commandApi.execute()`
- Shows sonner toast on success/failure; refreshes officer CP via `onResult` callback
- Cooldown tracking: local `Map<actionCode, endEpochMs>` polled every second

**commands/page.tsx** (rewritten):
- 3-column layout: left sidebar (officer info + CP), center (card tabs + command list), right (co-located officers)
- Left sidebar: OfficerInfoPanel + CpDisplay stacked
- Center: PositionCardPanel tabs + CommandExecutionPanel scrollable list
- Right sidebar: "동스폿 장교" list — officers on same planet, clickable to `/generals/{id}`
- WebSocket subscriptions: turn/command/events topics trigger debounced refresh
- ServerClock retained in header

**websocket.ts** addition:
- `publishWebSocket(destination, body)` — publishes to STOMP destination, returns boolean success

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CommandTableEntry field name mismatch**
- **Found during:** Task 2 TypeScript check
- **Issue:** Plan assumed `code`, `group`, `cpCost`, `cooldown`, `available` field names but actual `@/types` `CommandTableEntry` uses `actionCode`, `commandGroup`, `commandPointCost`, `durationSeconds`, `enabled`
- **Fix:** Updated CommandExecutionPanel to use correct legacy field names
- **Files modified:** `command-execution-panel.tsx`
- **Commit:** a54254c6

**2. [Rule 1 - Bug] CommandResult.log vs CommandResult.logs**
- **Found during:** Task 2 TypeScript check
- **Issue:** Plan referenced `result.log[0]` but legacy `@/types` `CommandResult` uses `logs: string[]`
- **Fix:** Changed to `result.logs[0]`
- **Files modified:** `command-execution-panel.tsx`
- **Commit:** a54254c6

**3. [Rule 1 - Bug] Officer/General type aliasing**
- **Found during:** Task 2 TypeScript check
- **Issue:** `officerStore` returns `General` (legacy 5-stat type) via `@/types`, but the actual API returns the 8-stat `Officer` with `pcpPool`, `positionCards`, `planetId` — TypeScript errors on those fields
- **Fix:** Import `Officer` from `@/types/officer` directly; cast `myOfficer` via `unknown as Officer | null` in page; import `Officer` type in `officer-info-panel.tsx` from `@/types/officer`
- **Files modified:** `commands/page.tsx`, `officer-info-panel.tsx`
- **Commit:** a54254c6

**4. [Rule 2 - Missing] CommandGroup not exported from @/types**
- **Found during:** Task 1/2 TypeScript check
- **Issue:** `CommandGroup` and `OfficerSummary` not re-exported from legacy `@/types/index.ts`
- **Fix:** Defined `CommandGroup` as a local type union in each component; imported `OfficerSummary` directly from `@/types/officer`
- **Files modified:** `position-card-panel.tsx`, `command-execution-panel.tsx`, `commands/page.tsx`
- **Commit:** a54254c6

## Known Stubs

None — all data is wired to live API calls (generalApi, commandApi) and WebSocket subscriptions.

## Self-Check: PASSED

| Item | Status |
|------|--------|
| officer-info-panel.tsx | FOUND |
| cp-display.tsx | FOUND |
| position-card-panel.tsx | FOUND |
| command-execution-panel.tsx | FOUND |
| commands/page.tsx | FOUND |
| commit 5d66dba3 (Task 1) | FOUND |
| commit a54254c6 (Task 2) | FOUND |
