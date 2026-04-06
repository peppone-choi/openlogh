---
phase: 03-command-point-system
plan: 01
subsystem: database
tags: [flyway, jpa, kotlin, command-points, pcp, mcp]

requires:
  - phase: 01-entity-model-foundation
    provides: Officer entity with command_points field and officer_level rank
provides:
  - "PCP/MCP columns on officer table (V31 migration)"
  - "Officer entity with pcp, mcp, pcpMax, mcpMax JPA fields"
  - "CpPoolConfig rank-based pool size lookup object"
affects: [03-02, 03-03, command-execution, turn-engine]

tech-stack:
  added: []
  patterns: [dual-cp-pool, rank-based-scaling]

key-files:
  created:
    - game-app/src/main/resources/db/migration/V31__add_pcp_mcp_columns.sql
    - game-app/src/main/kotlin/com/openlogh/model/CpPoolConfig.kt
  modified:
    - game-app/src/main/kotlin/com/openlogh/entity/Officer.kt

key-decisions:
  - "Legacy commandPoints field kept for backward compatibility during transition"
  - "PCP and MCP share identical max pool sizes per rank"

patterns-established:
  - "CpPoolConfig object pattern: static lookup with clamped rank input"

requirements-completed: [CMD-02]

duration: 1min
completed: 2026-04-06
---

# Phase 3 Plan 1: CP Data Model Summary

**Dual PCP/MCP columns on officer table via V31 migration with rank-based pool sizing (5-35 points by rank 0-10)**

## Performance

- **Duration:** 1 min
- **Started:** 2026-04-06T02:43:12Z
- **Completed:** 2026-04-06T02:44:33Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- V31 Flyway migration adds pcp, mcp, pcp_max, mcp_max to officer table with data migration from legacy command_points
- CpPoolConfig object provides rank-to-pool-size lookup (rank 0=5 through rank 10=35)
- Officer entity extended with 4 new JPA fields while preserving backward-compatible commandPoints

## Task Commits

Each task was committed atomically:

1. **Task 1+2: V31 migration + CpPoolConfig + Officer entity fields** - `4ba3e1bb` (feat)

**Plan metadata:** pending

## Files Created/Modified
- `game-app/src/main/resources/db/migration/V31__add_pcp_mcp_columns.sql` - Adds pcp/mcp/pcp_max/mcp_max columns, migrates existing data, sets rank-based max values
- `game-app/src/main/kotlin/com/openlogh/model/CpPoolConfig.kt` - Rank-based max pool size lookup object with Int/Short overloads
- `game-app/src/main/kotlin/com/openlogh/entity/Officer.kt` - Added pcp, mcp, pcpMax, mcpMax JPA fields; deprecated commandPoints

## Decisions Made
- Legacy commandPoints field kept (not dropped) for backward compatibility during transition period
- PCP and MCP share identical max pool sizes per rank -- no asymmetry needed at this stage

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Officer entity and database ready for CP consumption/recovery logic in Plan 03-02
- CpPoolConfig available for max pool lookups in recovery service

---
*Phase: 03-command-point-system*
*Completed: 2026-04-06*
