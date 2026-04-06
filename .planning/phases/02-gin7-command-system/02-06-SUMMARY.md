---
phase: 02-gin7-command-system
plan: "06"
subsystem: command-proposal
tags: [proposal, command-system, rest-api, jpa, flyway]
dependency_graph:
  requires: [02-02, 02-03, 02-04, 02-05]
  provides: [CommandProposal entity, proposal REST API, rank-gated approval]
  affects: [CommandExecutor, GameEventService, SessionStateRepository]
tech_stack:
  added: []
  patterns: [JPA entity with JSONB, Spring Data Repository, runBlocking for suspend in controller]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/entity/CommandProposal.kt
    - backend/game-app/src/main/resources/db/migration/V46__add_command_proposal.sql
    - backend/game-app/src/main/kotlin/com/openlogh/repository/CommandProposalRepository.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/CommandProposalService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/CommandProposalController.kt
  modified: []
decisions:
  - "SessionState has no startYear field — used currentYear for both year and startYear in CommandEnv"
  - "GameEventService has no broadcastProposalResult — used broadcastCommand() with typed map payload"
  - "SessionStateRepository uses Short ID — cast sessionId.toLong() to .toShort() for findById call"
  - "approveProposal is suspend fun — Controller wraps call in runBlocking{} (Spring MVC, not WebFlux)"
  - "CommandEnv.gameStor is MutableMap<String,Any> — used mutableMapOf() not emptyMap()"
metrics:
  duration: ~10 minutes
  completed: 2026-04-06
  tasks_completed: 2
  files_created: 5
---

# Phase 02 Plan 06: Command Proposal System Summary

**One-liner:** Rank-gated command proposal pipeline — junior officers propose commands, seniors approve/reject via REST API backed by CommandExecutor execution and WebSocket broadcast.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | CommandProposal entity + V46 migration + Repository | 9addf390 | CommandProposal.kt, V46__add_command_proposal.sql, CommandProposalRepository.kt |
| 2 | CommandProposalService + CommandProposalController | 7f39fcb4 | CommandProposalService.kt, CommandProposalController.kt |

## What Was Built

- **CommandProposal JPA entity** — `session_id`, `proposer_id`, `approver_id`, `command_code`, `args` (JSONB), `status` (PENDING/APPROVED/REJECTED), timestamps, `result_log`
- **V46 Flyway migration** — `command_proposal` table with FK to `session_state(id) ON DELETE CASCADE`, two indexes (session+status, approver+status WHERE PENDING)
- **CommandProposalRepository** — `findBySessionIdAndApproverIdAndStatus`, `findBySessionIdAndProposerId`, `findBySessionIdAndStatus`
- **CommandProposalService** — `createProposal()` with `officerLevel` rank check, `approveProposal()` calling `CommandExecutor.executeOfficerCommand()`, `rejectProposal()`, pending/mine query helpers
- **CommandProposalController** — `POST /api/{sessionId}/proposals`, `PUT /{id}/approve`, `PUT /{id}/reject`, `GET /pending`, `GET /mine`

## REST API

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/{sessionId}/proposals` | Create proposal (rank check enforced) |
| PUT | `/api/{sessionId}/proposals/{id}/approve` | Approve and execute command |
| PUT | `/api/{sessionId}/proposals/{id}/reject` | Reject without executing |
| GET | `/api/{sessionId}/proposals/pending?approverId=` | List pending for approver |
| GET | `/api/{sessionId}/proposals/mine?proposerId=` | List proposer's own proposals |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SessionState.startYear does not exist**
- **Found during:** Task 2 implementation
- **Issue:** Plan's `CommandEnv` constructor call used `session.startYear` but `SessionState` entity only has `currentYear` and `currentMonth`
- **Fix:** Used `session.currentYear.toInt()` for both `year` and `startYear` in `CommandEnv`
- **Files modified:** CommandProposalService.kt

**2. [Rule 1 - Bug] GameEventService has no broadcastProposalResult method**
- **Found during:** Task 2 implementation
- **Issue:** Plan referenced `gameEventService.broadcastProposalResult()` which doesn't exist
- **Fix:** Used existing `gameEventService.broadcastCommand(sessionId, proposerId, mapOf(...))` with typed payload as documented in plan's fallback instruction
- **Files modified:** CommandProposalService.kt

**3. [Rule 1 - Bug] SessionStateRepository uses Short not Long as ID**
- **Found during:** Task 2 implementation
- **Issue:** `sessionStateRepository.findById(sessionId)` where `sessionId: Long` but repo uses `JpaRepository<SessionState, Short>`
- **Fix:** Cast to `sessionId.toShort()` in findById call
- **Files modified:** CommandProposalService.kt

**4. [Rule 1 - Bug] emptyMap() type inference failure for MutableMap**
- **Found during:** Compilation of Task 2
- **Issue:** `CommandEnv.gameStor` is `MutableMap<String, Any>` but `emptyMap()` couldn't infer type — compile error
- **Fix:** Changed `emptyMap()` to `mutableMapOf()`
- **Files modified:** CommandProposalService.kt

**5. [Rule 2 - Pattern] runBlocking for suspend function in Spring MVC controller**
- **Found during:** Task 2 implementation
- **Issue:** Plan used `suspend fun approveProposal` in controller, but project uses Spring MVC (not WebFlux) — suspend controllers not directly supported
- **Fix:** Used `runBlocking { proposalService.approveProposal(...) }` in controller handler

## Known Stubs

None — all endpoints are fully wired to service/repository/executor logic.

## Self-Check: PASSED

- [x] CommandProposal.kt exists
- [x] V46__add_command_proposal.sql exists
- [x] CommandProposalRepository.kt exists
- [x] CommandProposalService.kt exists
- [x] CommandProposalController.kt exists
- [x] Commits 9addf390 and 7f39fcb4 exist
- [x] `commandExecutor.executeOfficerCommand` present in CommandProposalService.kt
- [x] `./gradlew :game-app:compileKotlin` BUILD SUCCESSFUL
