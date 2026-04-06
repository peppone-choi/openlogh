---
phase: 04-position-card-command-authority
plan: 02
subsystem: command-system
tags: [position-card, authority-gating, cooldown, realtime, command-table]

requires:
  - phase: 04-01
    provides: "PositionCard enum, CommandGroup enum, PositionCardRegistry with canExecute"
provides:
  - "Authority gating in CommandExecutor using position cards"
  - "Real-time OffsetDateTime cooldowns replacing turn-index cooldowns"
  - "Command table filtering by officer's position cards"
  - "commandGroup field in CommandTableEntry DTO"
affects: [04-03, 04-04, 07-appointment-system, frontend-command-table]

tech-stack:
  added: []
  patterns: [card-based-authority-gating, real-time-cooldown-iso-timestamp, legacy-fallback-pattern]

key-files:
  created: []
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/CommandDtos.kt

key-decisions:
  - "Legacy officerLevel >= 5 fallback kept for faction commands during migration period"
  - "Cooldown base period set to 300 seconds (5 min) per postReqTurn unit"
  - "Old integer cooldown values treated as expired (allow execution) for backward compat"

patterns-established:
  - "Card authority pattern: check ALWAYS_ALLOWED_COMMANDS set first, then PositionCardRegistry.canExecute"
  - "Legacy fallback pattern: card check fails -> check officerLevel >= 5 for faction commands"
  - "ISO timestamp cooldown: store OffsetDateTime.toString() in meta map, parse back with OffsetDateTime.parse"

requirements-completed: [CMD-01, CMD-04]

duration: 4min
completed: 2026-04-06
---

# Phase 4 Plan 2: Authority Gating & Real-Time Cooldowns Summary

**Position card authority enforcement in CommandExecutor/RealtimeService with OffsetDateTime cooldowns and card-filtered command tables**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-06T03:25:51Z
- **Completed:** 2026-04-06T03:30:14Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- CommandExecutor enforces position card authority before officer and faction command execution
- Cooldown system converted from turn-index integers to wall-clock OffsetDateTime ISO timestamps
- Command table APIs filter commands to only show those matching officer's position cards
- RealtimeService enforces card authority in both submitCommand and submitNationCommand

## Task Commits

Each task was committed atomically:

1. **Task 1: Authority gating in CommandExecutor + real-time cooldowns** - `2fa12969` (feat)
2. **Task 2: Command table filtering + RealtimeService card check** - `a6feb65b` (feat)

## Files Created/Modified
- `backend/game-app/src/main/kotlin/com/openlogh/command/CommandExecutor.kt` - Position card authority check + OffsetDateTime cooldowns
- `backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt` - Card-filtered command tables for officer and faction
- `backend/game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt` - Card authority check in realtime command submission
- `backend/game-app/src/main/kotlin/com/openlogh/dto/CommandDtos.kt` - Added commandGroup field to CommandTableEntry

## Decisions Made
- Legacy officerLevel >= 5 fallback kept for faction commands: officers promoted before card system will retain access until Phase 7 appointment system assigns proper cards
- Cooldown base period: 300 seconds (5 min) per postReqTurn unit, matching the default command duration
- Old integer cooldown values in meta maps treated as expired rather than throwing, ensuring smooth migration
- NPC commands (NPC, CR) and rest commands bypass card checks entirely via ALWAYS_ALLOWED_COMMANDS constant

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Authority gating is active; officers with only default cards (PERSONAL + CAPTAIN) are restricted to basic commands
- Command table API now exposes commandGroup for frontend categorization
- Ready for Plan 03 (proposal/suggestion system) and Plan 04 (card appointment)
- Phase 7 appointment system will tighten the legacy officerLevel fallback

---
*Phase: 04-position-card-command-authority*
*Completed: 2026-04-06*
