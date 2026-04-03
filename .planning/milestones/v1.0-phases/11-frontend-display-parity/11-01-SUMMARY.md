---
phase: 11-frontend-display-parity
plan: 01
subsystem: ui
tags: [audit, legacy-parity, vue, react, battle-log, frontend]

requires:
  - phase: 10-diplomacy-and-scenario-data
    provides: Complete backend game data layer
provides:
  - Complete field-by-field audit of all 28 legacy pages vs current frontend
  - 18 classified gaps with action items for Plan 02 implementation
  - Battle log HTML template structure fully documented
  - CSS class-to-color mapping for battle log renderer
affects: [11-02, frontend-display-parity]

tech-stack:
  added: []
  patterns:
    - "Gap classification taxonomy: type-missing, calc-missing, format-diff, display-only, component-new"
    - "Battle log detection heuristic: isBattleLogHtml() checks for class=small_war_log"

key-files:
  created:
    - .planning/phases/11-frontend-display-parity/11-AUDIT.md
  modified: []

key-decisions:
  - "calcInjury uses Math.floor (legacy) not Math.round (current) -- parity fix needed"
  - "Kill ratio formula is killcrew/max(deathcrew,1) not killcrew/(killcrew+deathcrew)"
  - "Battle log needs two rendering paths: color tags (existing) + HTML template (new component)"
  - "Tech level constants must come from game config, not hardcoded values"

patterns-established:
  - "Audit document structure: per-page field comparison tables + gap summary with classification"
  - "Gap priority: P0 correctness, P1 display parity, P2 polish, P3 skip"

requirements-completed: [FE-01, FE-02, FE-03, FE-04]

duration: 6min
completed: 2026-04-03
---

# Phase 11 Plan 01: Frontend Display Parity Audit Summary

**Field-by-field audit of all 28 legacy pages identifying 18 display gaps (4 calc-missing, 8 format-diff, 3 component-new, 1 type-missing, 2 display-only) with battle log HTML template fully documented**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-03T01:52:50Z
- **Completed:** 2026-04-03T01:58:52Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Complete field-by-field audit of 4 core page categories (dashboard, general card, nation card, city card) with 95+ field comparisons
- All 28 legacy pages (16 Vue + 12 TS) verified to have matching current routes -- no missing pages
- Battle log HTML template structure (`small_war_log.php`) fully documented with 6 CSS class-to-color mappings
- 18 concrete gaps identified, classified, and prioritized for Plan 02 implementation

## Task Commits

1. **Task 1: Audit core pages** - `90f5e6f` (docs)
2. **Task 2: Audit remaining pages + battle log** - `4338886` (docs)

## Files Created/Modified
- `.planning/phases/11-frontend-display-parity/11-AUDIT.md` - Complete 8-section audit document with field comparison tables and gap summary

## Decisions Made
- `calcInjury` in `game-utils.ts` uses `Math.round` but legacy uses `Math.floor` -- needs correction for parity
- Kill ratio formula differs: current uses `killcrew/(killcrew+deathcrew)`, legacy uses `killcrew/max(deathcrew,1)` -- legacy formula is correct
- Battle log rendering requires two paths: existing `formatLog()` for color tags + new `BattleLogEntry` component for HTML templates
- Tech level and stat upgrade threshold constants are hardcoded in components but should come from game config API
- Officer level 20/19 vs legacy 12/11 is intentional opensamguk extension, not a parity gap

## Deviations from Plan

None -- plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None -- this plan produces documentation only (11-AUDIT.md), no code stubs.

## Next Phase Readiness
- 11-AUDIT.md provides complete gap inventory for Plan 02 to implement without re-reading legacy source
- All 18 gaps have classification and specific action items
- Battle log component specification is detailed enough for direct implementation
- Priority classification (P0-P3) guides implementation order

## Self-Check: PASSED

- [x] 11-AUDIT.md exists (826 lines, above 200 minimum)
- [x] 11-01-SUMMARY.md exists
- [x] Commit 90f5e6f found (Task 1)
- [x] Commit 4338886 found (Task 2)
- [x] Gap summary has 18 rows with non-empty Action Needed column
- [x] All 8 sections present in audit document

---
*Phase: 11-frontend-display-parity*
*Completed: 2026-04-03*
