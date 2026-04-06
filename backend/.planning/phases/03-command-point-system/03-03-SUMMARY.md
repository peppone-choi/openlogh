---
phase: 03-command-point-system
plan: 03
subsystem: game-engine
tags: [command-points, pcp, mcp, dual-pool, cp-regeneration, realtime]

requires:
  - phase: 03-command-point-system/plan-02
    provides: CpService with deductCp/regeneratePcpMcp, Officer pcp/mcp/pcpMax/mcpMax fields, StatCategory enum, CommandTableEntry poolType field
provides:
  - CpService wired into RealtimeService for dual-pool deduction and stat-based regeneration
  - CommandService command tables include poolType per command
  - Frontend types support dual PCP/MCP fields
  - Command panel displays separate PCP (blue) and MCP (red) pools
affects: [04-position-card-system, 05-combat-engine]

tech-stack:
  added: []
  patterns: [dual-pool-cp-deduction, backward-compat-legacy-commandPoints, cross-use-2x-penalty]

key-files:
  created: []
  modified:
    - game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt
    - game-app/src/main/kotlin/com/openlogh/service/CommandService.kt
    - frontend/src/types/index.ts
    - frontend/src/components/game/command-panel.tsx
    - frontend/src/components/game/command-select-form.tsx
    - frontend/src/data/tutorial/index.ts
    - frontend/src/data/tutorial/mock-generals.ts

key-decisions:
  - "Legacy commandPoints field kept as sum of pcp+mcp for backward compatibility"
  - "poolType badge shown only in realtime mode alongside existing CP/duration display"

patterns-established:
  - "Dual-pool CP pattern: deductCp with cross-use fallback at 2x cost"
  - "Backward compat: general.commandPoints = general.pcp + general.mcp after mutations"

requirements-completed: [CMD-02, CMD-03]

duration: 4min
completed: 2026-04-06
---

# Phase 3 Plan 3: CP Service Integration Summary

**Dual PCP/MCP command point system wired end-to-end: CpService deduction with 2x cross-use in RealtimeService, stat-based regeneration, poolType in command tables, and frontend dual-pool display with color coding**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-06T02:52:09Z
- **Completed:** 2026-04-06T02:56:00Z
- **Tasks:** 2 auto + 1 checkpoint (auto-approved)
- **Files modified:** 7

## Accomplishments
- RealtimeService.scheduleCommand uses CpService.deductCp for dual PCP/MCP deduction with automatic 2x cross-use fallback
- RealtimeService.regenerateCommandPoints delegates to CpService.regeneratePcpMcp for stat-based recovery
- getRealtimeStatus returns pcp, mcp, pcpMax, mcpMax alongside legacy commandPoints
- CommandService includes poolType (PCP/MCP) in both officer and nation command table entries
- Frontend General, GeneralFrontInfo, RealtimeStatus, CommandTableEntry types updated with dual CP fields
- Command panel shows PCP (blue) and MCP (red) separately with max values
- Command select form shows [PCP]/[MCP] badge next to CP cost in realtime mode

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire CpService into RealtimeService and CommandService** - `d6ac5f91` (feat)
2. **Task 2: Update frontend types and command panel for dual CP display** - `45aeff27` (feat)
3. **Task 3: Human verification checkpoint** - auto-approved (auto mode)

## Files Created/Modified
- `game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt` - CpService injection, dual-pool deduction in scheduleCommand, stat-based regen in regenerateCommandPoints, dual CP in getRealtimeStatus and event broadcasts
- `game-app/src/main/kotlin/com/openlogh/service/CommandService.kt` - poolType added to both officer and nation command table entry builders
- `frontend/src/types/index.ts` - pcp/mcp/pcpMax/mcpMax on General, GeneralFrontInfo, RealtimeStatus; poolType on CommandTableEntry
- `frontend/src/components/game/command-panel.tsx` - Dual CP display with blue PCP / red MCP color coding
- `frontend/src/components/game/command-select-form.tsx` - [PCP]/[MCP] badge next to CP cost
- `frontend/src/data/tutorial/index.ts` - Mock data updated with dual CP fields
- `frontend/src/data/tutorial/mock-generals.ts` - Mock data updated with dual CP fields

## Decisions Made
- Legacy commandPoints field maintained as pcp+mcp sum for backward compatibility with existing frontend code
- poolType badge only shown in realtime mode to avoid clutter in turn-based mode

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed tutorial mock data missing dual CP fields**
- **Found during:** Task 2 (Frontend types update)
- **Issue:** TypeScript compilation failed because tutorial mock data for GeneralFrontInfo and General types lacked pcp/mcp/pcpMax/mcpMax fields
- **Fix:** Added pcp: 5, mcp: 5, pcpMax: 5, mcpMax: 5 to both mock data files
- **Files modified:** frontend/src/data/tutorial/index.ts, frontend/src/data/tutorial/mock-generals.ts
- **Verification:** TypeScript compiles cleanly after fix
- **Committed in:** 45aeff27 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary fix for type safety. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Dual PCP/MCP system fully wired end-to-end
- Ready for Phase 4 (Position Card System) which will set specific commands to MCP pool type
- TickEngine already calls regenerateCommandPoints at CP_REGEN_INTERVAL_TICKS (300 ticks / 5 min), now using stat-based formula

---
*Phase: 03-command-point-system*
*Completed: 2026-04-06*
