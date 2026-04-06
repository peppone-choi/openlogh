---
phase: 04-position-card-command-authority
plan: 04
subsystem: ui
tags: [react, zustand, typescript, position-cards, proposals, command-panel]

requires:
  - phase: 04-position-card-command-authority/04-02
    provides: "Backend command gating and CP deduction by position cards"
  - phase: 04-position-card-command-authority/04-03
    provides: "Backend proposal API endpoints"
provides:
  - "Command panel UI grouped by commandGroup with Korean labels"
  - "Card-based command filtering with propose button for unauthorized commands"
  - "Proposal submission dialog with approver selection"
  - "Proposal management panel with received/sent tabs and approve/reject workflow"
  - "Command store (Zustand) for command table and proposal state"
  - "Proposal API client integration"
affects: [05-combat-system, 06-diplomacy]

tech-stack:
  added: []
  patterns: [commandGroup-based command grouping, proposal workflow UI pattern]

key-files:
  created:
    - frontend/src/stores/commandStore.ts
    - frontend/src/components/game/proposal-panel.tsx
  modified:
    - frontend/src/types/index.ts
    - frontend/src/lib/gameApi.ts
    - frontend/src/components/game/command-select-form.tsx

key-decisions:
  - "commandGroup field is optional on CommandTableEntry for backward compat with existing category-based grouping"
  - "Propose button shown only for commands disabled due to missing card authority (reason contains '권한')"
  - "Proposal dialog uses inline approver list selection rather than dropdown for better UX"

patterns-established:
  - "Command grouping: commands grouped by commandGroup with COMMAND_GROUP_LABELS Korean map"
  - "Proposal workflow: submit -> pending -> approve/reject with toast notifications"

requirements-completed: [CMD-01, CMD-04, CMD-05]

duration: 4min
completed: 2026-04-06
---

# Phase 4 Plan 4: Command Panel UI + Proposal Workflow Summary

**Card-filtered command panel grouped by commandGroup with Korean labels, proposal submission dialog, and full proposal management panel**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-06T03:32:56Z
- **Completed:** 2026-04-06T03:37:14Z
- **Tasks:** 3 (2 auto + 1 checkpoint auto-approved)
- **Files modified:** 5

## Accomplishments
- Command panel now groups commands by commandGroup (작전/개인/지휘/병참/인사/정치/첩보) instead of flat categories
- Disabled commands lacking position card authority show a "제안" button to propose to a superior
- Proposal submission dialog with eligible approver selection and optional reason text
- Proposal management panel with "받은 제안" (received) and "보낸 제안" (sent) tabs
- Zustand command store managing command table and proposal state with API integration

## Task Commits

Each task was committed atomically:

1. **Task 1: Types, API client, command store** - `3fff59bd` (feat)
2. **Task 2: Command panel + proposal panel components** - `991b754f` (feat)
3. **Task 3: Verify position card command panel and proposal UI** - auto-approved (checkpoint)

## Files Created/Modified
- `frontend/src/types/index.ts` - Added PositionCard, Proposal, EligibleApprover, SubmitProposalRequest types; added commandGroup to CommandTableEntry
- `frontend/src/lib/gameApi.ts` - Added proposalApi with submit/resolve/pending/my/eligibleApprovers endpoints
- `frontend/src/stores/commandStore.ts` - New Zustand store for command table and proposal state management
- `frontend/src/components/game/command-select-form.tsx` - Refactored to group by commandGroup with Korean labels, added propose button and proposal dialog
- `frontend/src/components/game/proposal-panel.tsx` - New component with received/sent proposal tabs, approve/reject workflow

## Decisions Made
- CommandTableEntry.commandGroup is optional (backward compat): falls back to category if commandGroup not present
- Propose button visibility triggered by reason containing '권한' (authority) keyword
- Inline approver list selection in dialog rather than dropdown for clearer display of approver cards

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all components are wired to real API endpoints via the command store and proposalApi.

## Next Phase Readiness
- Position card command authority system is complete end-to-end (backend + frontend)
- Ready for Phase 5 (combat system) or any phase that builds on command execution

---
*Phase: 04-position-card-command-authority*
*Completed: 2026-04-06*
