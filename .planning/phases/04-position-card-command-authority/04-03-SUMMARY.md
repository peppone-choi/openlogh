---
phase: 04-position-card-command-authority
plan: 03
subsystem: proposal-system
tags: [proposal, suggestion, command-authority, position-card, multiplayer]
dependency_graph:
  requires: [04-01]
  provides: [proposal-entity, proposal-service, proposal-controller]
  affects: [command-system, message-system]
tech_stack:
  added: []
  patterns: [gin7-proposal-system, cp-deduction-from-requester]
key_files:
  created:
    - backend/game-app/src/main/resources/db/migration/V33__create_proposal_table.sql
    - backend/game-app/src/main/kotlin/com/openlogh/entity/Proposal.kt
    - backend/game-app/src/main/kotlin/com/openlogh/repository/ProposalRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/ProposalDtos.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/ProposalService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/ProposalController.kt
  modified: []
decisions:
  - CP deduction on approval goes to requester via CommandService.executeCommand (delegates to existing command pipeline)
  - Proposal notifications use MessageService private mailbox (personal mailboxCode)
  - Eligible approvers filtered by same session+faction, excludes requester
metrics:
  duration: 2min
  completed: 2026-04-06T03:27:47Z
  tasks_completed: 2
  tasks_total: 2
  files_created: 6
  files_modified: 0
requirements: [CMD-05]
---

# Phase 04 Plan 03: Proposal/Suggestion System Summary

Proposal system enabling lower-rank officers to propose commands to superiors holding the required position cards, with card authority validation and CP deduction from requester on approval (gin7 rule).

## What Was Built

### Task 1: Proposal Entity + Repository + Migration
- **V33 Flyway migration**: Creates `proposal` table with JSONB args, status check constraint (`pending/approved/rejected/expired`), and indexes on session_id, approver+status, and requester
- **Proposal entity**: JPA entity with all fields including JSONB args column
- **ProposalRepository**: Query methods for approver pending list, requester history, and session-wide status queries
- **DTOs**: `SubmitProposalRequest`, `ResolveProposalRequest`, `ProposalResponse`, `EligibleApproverResponse`

### Task 2: ProposalService + ProposalController
- **ProposalService** with 6 methods:
  - `submitProposal`: Validates same session/faction, approver has card, requester lacks card; sends in-game message
  - `resolveProposal`: Approves (executes command via CommandService, notifies requester) or rejects (with reason, notifies requester)
  - `listPendingForApprover`: Returns pending proposals with officer names
  - `listMyProposals`: Returns requester's proposal history
  - `findEligibleApprovers`: Finds faction officers holding cards for the given command, sorted by rank
  - `expireOldProposals`: Marks proposals older than 24h as expired
- **ProposalController** with 5 REST endpoints:
  - `POST /api/proposals/submit/{generalId}` - Submit proposal
  - `POST /api/proposals/resolve/{generalId}/{proposalId}` - Approve/reject
  - `GET /api/proposals/pending/{generalId}` - List pending for approver
  - `GET /api/proposals/my/{generalId}` - List requester's proposals
  - `GET /api/proposals/eligible-approvers/{generalId}?actionCode=X` - Find eligible approvers

## Decisions Made

1. **CP deduction via CommandService.executeCommand**: Rather than manually calling CpService, the resolve flow delegates to CommandService.executeCommand with the requester's generalId. This ensures the full command pipeline (constraints, cooldowns, CP deduction, stat changes) runs correctly.
2. **Message notifications via personal mailbox**: Proposal notifications use `mailboxCode=personal` to appear in the officer's private message inbox, consistent with existing recruitment messages.
3. **Eligible approver filtering**: Uses PositionCardRegistry.getCardsForCommand to find all cards that grant the command group, then filters faction officers whose position cards intersect with that set.

## Deviations from Plan

None - plan executed exactly as written.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 7b9afa88 | Proposal entity, repository, DTOs, V33 migration |
| 2 | 40738e33 | ProposalService and ProposalController |

## Known Stubs

None - all methods are fully implemented with real data sources.

## Self-Check: PASSED

- All 6 created files verified present on disk
- Both commit hashes (7b9afa88, 40738e33) verified in git log
- `./gradlew :game-app:compileKotlin` BUILD SUCCESSFUL
