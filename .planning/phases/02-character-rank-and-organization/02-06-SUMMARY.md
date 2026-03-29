---
phase: 02-character-rank-and-organization
plan: 06
subsystem: ui, frontend
tags: [react, next.js, officer-profile, org-chart, position-card, faction-colors, collapsible-tree, tooltip, zustand]

requires:
    - phase: 02-character-rank-and-organization
      plan: 03
      provides: RankController REST endpoints for rank ladder data
    - phase: 02-character-rank-and-organization
      plan: 04
      provides: OrgChart API /api/org-chart/{sessionId} and PositionCard API /api/position-cards/{sessionId}/{officerId}
provides:
    - OfficerProfileCard component with D-10 4-section layout (basic info, 8 stats, position cards, location/status)
    - PositionCardGrid and PositionCardChip components for game-font position card display with tooltips
    - OrgTreeNodeLive component with Collapsible tree, faction color differentiation (empire-gold / alliance-blue)
    - Org chart page with live holder data binding from /api/org-chart/{sessionId}
    - OfficerPositionCard, OrgChartHolder, OrgChartResponse, PositionTypeInfo TypeScript interfaces
affects: [03-command-point-system, 04-galactic-map]

tech-stack:
    added: []
    patterns: [faction-color-css-variables, collapsible-org-tree, position-card-chip-tooltip, static-tree-with-api-merge]

key-files:
    created:
        - frontend/src/components/game/officer-profile-card.tsx
        - frontend/src/components/game/position-card-grid.tsx
        - frontend/src/components/game/position-card-chip.tsx
        - frontend/src/components/game/org-tree-node-live.tsx
    modified:
        - frontend/src/app/(game)/officer/page.tsx
        - frontend/src/app/(game)/org-chart/page.tsx
        - frontend/src/types/index.ts

key-decisions:
    - "Officer profile uses existing LoghBar for stat bars rather than StatBar/Progress to match existing page style"
    - "Position card location/status shows IDs (planet #ID, fleet #ID) rather than resolved names — name resolution requires additional API calls and will be wired when planet/fleet stores are available"
    - "Org chart static tree structures kept inline with positionType fields for API matching — avoids separate config file until backend defines canonical tree"
    - "OrgTreeNodeLive uses Radix Collapsible for accessible expand/collapse vs manual state toggle"

patterns-established:
    - "Faction color differentiation: pass factionColor='empire'|'alliance' prop, map to CSS variables var(--empire-gold)/var(--alliance-blue)"
    - "Static tree + API merge pattern: client defines org hierarchy, API provides live holder data, mergeHolders() binds them"
    - "Position card chip: Badge with font-game class + TooltipProvider for granted commands display"

requirements-completed: [CHAR-03, CHAR-06, CHAR-13, RANK-01, RANK-03, RANK-04, ORG-01, ORG-02, ORG-03, ORG-06, ORG-08]

duration: 17min
completed: 2026-03-29
---

# Phase 02 Plan 06: Officer Profile & Org Chart UI Summary

**D-10 officer profile with 4-section layout (stats, position cards, location) and live org chart with faction-differentiated collapsible tree**

## Performance

- **Duration:** 17 min
- **Started:** 2026-03-29T03:21:09Z
- **Completed:** 2026-03-29T03:38:55Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Officer profile card with 4 sections per D-10: basic info (portrait, name, rank, origin, career, age), 8 stat bars with exp indicators, position card grid, location/status
- Position card display as game-font chips with tooltip showing granted commands, empty state for no cards
- Org chart rewritten with live holder data from API, expandable/collapsible tree defaulting to depth 3
- Faction visual differentiation: empire nodes use --empire-gold, alliance nodes use --alliance-blue for branch lines and titles
- Vacant positions display [vacant marker] in muted-foreground color

## Task Commits

Each task was committed atomically:

1. **Task 1: OfficerProfileCard + PositionCardGrid/Chip components** - `71870b6` (feat)
2. **Task 2: Org chart page rewrite with live data and faction differentiation** - `0d737d1` (feat)

## Files Created/Modified
- `frontend/src/components/game/officer-profile-card.tsx` - D-10 4-section officer profile layout with stats, cards, location
- `frontend/src/components/game/position-card-grid.tsx` - Flex-wrap grid of position card chips (max 16)
- `frontend/src/components/game/position-card-chip.tsx` - Single position card badge with game font and tooltip
- `frontend/src/components/game/org-tree-node-live.tsx` - Enhanced org tree node with Collapsible, faction colors, live holder display
- `frontend/src/app/(game)/officer/page.tsx` - Added OfficerProfileCard rendering and position card API fetch
- `frontend/src/app/(game)/org-chart/page.tsx` - Rewritten with live API data, faction tabs, merged holder data
- `frontend/src/types/index.ts` - Added OfficerPositionCard, OrgChartHolder, OrgChartResponse, PositionTypeInfo interfaces + Officer origin/career fields

## Decisions Made
- Used existing LoghBar component for stat bars to maintain visual consistency with the existing officer page
- Location/status section shows planet/fleet IDs rather than names — full name resolution requires planet/fleet store lookups that will be wired in later phases
- Kept static org tree definitions inline in the page file with positionType fields for API matching
- Used Radix Collapsible (shadcn) for accessible expand/collapse rather than manual state toggle

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added missing TypeScript types for position cards and org chart**
- **Found during:** Task 1
- **Issue:** OfficerPositionCard, OrgChartHolder, OrgChartResponse, PositionTypeInfo interfaces not in frontend types
- **Fix:** Added all four interfaces plus originType/careerType/homePlanetId fields to Officer type in types/index.ts
- **Files modified:** frontend/src/types/index.ts
- **Verification:** TypeScript compilation passes
- **Committed in:** 71870b6 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Type additions were necessary for TypeScript compilation. No scope creep.

## Issues Encountered
- Pre-existing TypeScript error in `frontend/src/app/(lobby)/lobby/select-pool/page.tsx` (Zod `.errors` property access) — not caused by this plan's changes, logged as out-of-scope

## Known Stubs
- Officer profile Section 4 shows planet/fleet by ID (`행성 #123`) instead of resolved name — planet/fleet name lookup requires store wiring in future phase

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Officer profile and org chart UI surfaces complete for integration testing
- Backend API endpoints (/api/position-cards/, /api/org-chart/) must be deployed for live data
- Planet/fleet name resolution can be added when map/fleet phases provide store access

## Self-Check: PASSED

All 8 files verified present. Both task commits (71870b6, 0d737d1) verified in git log.

---
*Phase: 02-character-rank-and-organization*
*Completed: 2026-03-29*
