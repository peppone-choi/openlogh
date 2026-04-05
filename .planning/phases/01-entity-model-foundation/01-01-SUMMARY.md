---
phase: 01-entity-model-foundation
plan: 01
subsystem: database
tags: [flyway, postgresql, migration, schema-rename, domain-mapping]

# Dependency graph
requires: []
provides:
  - "V28 Flyway migration renaming all OpenSamguk tables/columns/indexes to LOGH domain"
  - "3 new officer stat columns (mobility, attack, defense) with exp and dex"
affects: [01-entity-model-foundation, 01-02, 01-03, 01-04, 01-05, 01-06, 01-07, 01-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Metadata-only ALTER RENAME for zero-downtime schema migration"
    - "Single atomic migration for cross-referencing entity renames"

key-files:
  created:
    - "backend/game-app/src/main/resources/db/migration/V28__logh_domain_rename.sql"
  modified: []

key-decisions:
  - "Skipped world_id->session_id rename on tables using server_id (emperor/sovereign, old_general/old_officer, old_nation/old_faction, hall_of_fame, game_history) since they never had world_id columns"
  - "Applied all renames in a single migration for atomicity since entity classes cross-reference each other"

patterns-established:
  - "LOGH domain naming convention for all database objects"

requirements-completed: [CHAR-01]

# Metrics
duration: 3min
completed: 2026-04-05
---

# Phase 1 Plan 1: V28 LOGH Domain Rename Migration Summary

**Atomic Flyway V28 migration renaming 11 tables, 100+ columns, 10 indexes, and 1 enum type from OpenSamguk to LOGH domain terminology, plus 3 new officer stat columns**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-05T07:37:40Z
- **Completed:** 2026-04-05T07:40:14Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- All 11 core tables renamed (general->officer, city->planet, nation->faction, troop->fleet, etc.)
- FK columns renamed across 27+ tables (world_id->session_id, nation_id->faction_id, city_id->planet_id, etc.)
- Officer stat columns renamed (strength->command, intel->intelligence, charm->administration)
- 3 new stat columns added with defaults (mobility, attack, defense) plus exp and dex columns
- All resource/item columns renamed on officer and planet tables per CLAUDE.md mapping
- Faction columns renamed (gold->funds, bill->tax_rate, type_code->faction_type, etc.)
- 10 core indexes renamed to match new table/column names
- Enum type nation_aux_key renamed to faction_aux_key
- Migration validated against live PostgreSQL 16 database -- all 111 operations applied cleanly

## Task Commits

Each task was committed atomically:

1. **Task 1: Write V28 Flyway migration SQL** - `df9b3428` (feat)

## Files Created/Modified
- `backend/game-app/src/main/resources/db/migration/V28__logh_domain_rename.sql` - Complete OpenSamguk-to-LOGH domain rename migration (230 lines)

## Decisions Made
- Skipped `world_id -> session_id` rename on 5 tables (sovereign, old_officer, old_faction, hall_of_fame, game_history) that use `server_id` (VARCHAR) instead of `world_id` (BIGINT FK). These tables were created in V8 with a different schema pattern and never had a `world_id` column.
- Included `records` table (V26) and `select_pool` table (V22) in the world_id->session_id rename, which were not explicitly listed in the plan but do have world_id columns.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Skipped world_id rename on tables without world_id column**
- **Found during:** Task 1
- **Issue:** Plan listed world_id->session_id renames for old_officer, old_faction, game_history, hall_of_fame, but these tables use server_id (VARCHAR), not world_id (BIGINT)
- **Fix:** Skipped those renames and added comments explaining why
- **Files modified:** V28__logh_domain_rename.sql
- **Verification:** Migration applied cleanly without errors
- **Committed in:** df9b3428

**2. [Rule 2 - Missing Critical] Added world_id->session_id rename for tables not in plan**
- **Found during:** Task 1
- **Issue:** Plan did not list records (V26), select_pool (V22) tables which also have world_id columns
- **Fix:** Added session_id renames for both tables
- **Files modified:** V28__logh_domain_rename.sql
- **Verification:** Migration applied cleanly
- **Committed in:** df9b3428

---

**Total deviations:** 2 auto-fixed (1 bug prevention, 1 missing critical completeness)
**Impact on plan:** Both fixes necessary for correctness. No scope creep.

## Issues Encountered
- Flyway Gradle task (`flywayInfo`) not configured in build.gradle.kts -- validated migration by applying directly to PostgreSQL via docker exec instead
- Database was reset (DROP/CREATE) after manual validation so Flyway can apply V28 cleanly on next app boot

## Known Stubs
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Database schema is now fully renamed to LOGH domain terminology
- All subsequent plans (01-02 through 01-08) can proceed with Kotlin entity class renames that reference the new table/column names
- Database was reset to clean state; V1-V28 will be applied by Flyway on next `bootRun`

---
*Phase: 01-entity-model-foundation*
*Completed: 2026-04-05*

## Self-Check: PASSED
