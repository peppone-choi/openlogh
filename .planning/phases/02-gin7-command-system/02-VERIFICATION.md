---
phase: 02-gin7-command-system
verified: 2026-04-06T15:30:00Z
status: gaps_found
score: 3/4 must-haves verified
gaps:
  - truth: "플레이어가 직무권한카드를 통해서만 해당 커맨드 그룹에 접근할 수 있다 (officerLevel >= 5 우회 0건)"
    status: failed
    reason: "executeFactionCommand (CommandService.kt:72) 및 submitNationCommand (RealtimeService.kt:85) 두 경로에 'officerLevel < 5' 조건부 우회가 잔존함. 조건식 `!canExecute(cards, actionCode) && officerLevel < 5`는 officerLevel >= 5인 장교가 직무권한카드 없이도 해당 커맨드를 통과하게 허용함."
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt"
        issue: "Line 72: `&& officer.officerLevel < 5` — officerLevel >= 5 우회 허용"
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt"
        issue: "Line 85: `&& general.officerLevel < 5` — 동일 우회 패턴, submitNationCommand 경로"
    missing:
      - "CommandService.executeFactionCommand: officerLevel < 5 조건 제거, PositionCardRegistry.canExecute() 만으로 게이팅"
      - "RealtimeService.submitNationCommand: officerLevel < 5 조건 제거, 동일 수정"
---

# Phase 2: gin7 81종 커맨드 시스템 Verification Report

**Phase Goal:** 직무권한카드 기반 81종 gin7 커맨드가 실시간 실행 파이프라인을 통해 동작하며 삼국지 권한 우회가 완전히 제거된다
**Verified:** 2026-04-06T15:30:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 플레이어가 직무권한카드를 통해서만 커맨드 그룹에 접근할 수 있다 (officerLevel >= 5 우회 0건) | ✗ FAILED | CommandService.kt:72, RealtimeService.kt:85 — `&& officerLevel < 5` 조건부 우회 2건 잔존 |
| 2 | 커맨드 실행 시 CP 차감 → 대기시간 → 실행 → WebSocket 결과 브로드캐스트 흐름이 동작한다 | ✓ VERIFIED | RealtimeService.scheduleCommand():320 `cpService.deductCp()`, line 328-331 duration/commandEndTime, CommandProposalService:91 `broadcastCommand()` |
| 3 | 7개 커맨드 그룹 81종이 모두 CommandRegistry에 등록된다 | ✓ VERIFIED | Gin7CommandRegistry.kt: 81 `registerOfficerCommand` + 1 `registerPcpStub("대기")` = 82. 7개 패키지(operations/personal/commander/logistics/personnel/politics/intelligence) 모두 존재. Gin7CommandRegistryTest 82건 단언 |
| 4 | 계급이 낮은 장교가 상급자에게 제안을 발행하고 승인/거부가 처리된다 | ✓ VERIFIED | CommandProposalService.kt:37 `proposer.officerLevel < approver.officerLevel` rank check, PENDING→APPROVED/REJECTED 상태 전환, commandExecutor.executeOfficerCommand() 호출 확인 |

**Score:** 3/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/game-app/src/main/kotlin/com/openlogh/command/Gin7CommandRegistry.kt` | 81종 gin7 커맨드 등록 | ✓ VERIFIED | 254 lines, 81 registerOfficerCommand + 1 stub(대기) |
| `backend/game-app/src/main/kotlin/com/openlogh/model/PositionCardRegistry.kt` | 7그룹 commandGroupMap gin7 매핑 | ✓ VERIFIED | OPERATIONS/PERSONAL/COMMAND/LOGISTICS/PERSONNEL/POLITICS/INTELLIGENCE 7그룹 확인 |
| `backend/game-app/src/main/kotlin/com/openlogh/entity/CommandProposal.kt` | 제안 엔티티 | ✓ VERIFIED | 파일 존재 |
| `backend/game-app/src/main/resources/db/migration/V46__add_command_proposal.sql` | DB 마이그레이션 | ✓ VERIFIED | 파일 존재 |
| `backend/game-app/src/main/kotlin/com/openlogh/service/CommandProposalService.kt` | 제안 서비스 rank check + 실행 | ✓ VERIFIED | 128 lines, officerLevel 비교, executeOfficerCommand, broadcastCommand |
| `backend/game-app/src/main/kotlin/com/openlogh/controller/CommandProposalController.kt` | 제안 REST API | ✓ VERIFIED | 파일 존재 |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/` | 작전커맨드 16종 | ✓ VERIFIED | 10 파일 (16 클래스) |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/logistics/` | 병참커맨드 6종 | ✓ VERIFIED | 6 파일 |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/personal/` | 개인커맨드 15종 | ✓ VERIFIED | 9 파일 |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/personnel/` | 인사커맨드 10종 | ✓ VERIFIED | 6 파일 |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/politics/` | 정치커맨드 12종 | ✓ VERIFIED | PoliticsCommands.kt |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/intelligence/` | 첩보커맨드 14종 | ✓ VERIFIED | IntelligenceCommands.kt |
| `backend/game-app/src/main/kotlin/com/openlogh/command/gin7/commander/` | 지휘커맨드 8종 | ✓ VERIFIED | 7 파일 (8 클래스) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| CommandService.executeCommand | CommandExecutor.executeOfficerCommand | runBlocking | ✓ WIRED | CommandService.kt:52 |
| RealtimeService.submitCommand | CpService.deductCp | scheduleCommand():320 | ✓ WIRED | RealtimeService.kt:320 |
| RealtimeService.scheduleCommand | OfficerTurn queue | commandEndTime + duration | ✓ WIRED | lines 328-336 |
| CommandProposalService.approveProposal | CommandExecutor.executeOfficerCommand | suspend call | ✓ WIRED | CommandProposalService.kt:77 |
| CommandProposalService.approveProposal | GameEventService.broadcastCommand | WebSocket | ✓ WIRED | CommandProposalService.kt:91 |
| PositionCardRegistry.canExecute | CommandExecutor.executeOfficerCommand | gating check | ✓ WIRED | CommandExecutor.kt:87 |
| PositionCardRegistry.canExecute | CommandService.executeFactionCommand | gating check | ✗ PARTIAL | CommandService.kt:72 — `&& officerLevel < 5` 우회 조건으로 인해 officerLevel >= 5 장교는 카드 없이 통과 |
| PositionCardRegistry.canExecute | RealtimeService.submitNationCommand | gating check | ✗ PARTIAL | RealtimeService.kt:85 — 동일 우회 패턴 |

### Data-Flow Trace (Level 4)

해당 Phase는 데이터 렌더링 컴포넌트 없음 (백엔드 커맨드 파이프라인). Level 4 skip.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 82 commands registered | Gin7CommandRegistryTest (unit test) | 82건 단언 (3 tests GREEN per 02-07-SUMMARY) | ✓ PASS |
| PositionCard gating | Gin7CommandPipelineTest (unit test) | canExecute false/true 단언 (4 tests GREEN) | ✓ PASS |
| MCP/PCP pool separation | Gin7CommandPipelineTest | 완전수리=MCP, 승진=PCP (4 tests GREEN) | ✓ PASS |
| Proposal rank check + execute | CommandProposalServiceTest (unit test) | 6 tests GREEN per 02-07-SUMMARY | ✓ PASS |
| officerLevel bypass = 0 | grep production code | 2건 발견 (CommandService:72, RealtimeService:85) | ✗ FAIL |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| CMD-01 | 02-02 | 작전커맨드 16종 구현 | ✓ SATISFIED | operations/ 16 클래스 등록 확인 |
| CMD-02 | 02-03 | 개인커맨드 15종 구현 | ✓ SATISFIED | personal/ 15 클래스 등록 확인 |
| CMD-03 | 02-05 | 지휘커맨드 8종 구현 | ✓ SATISFIED | commander/ 8 클래스 등록 확인 |
| CMD-04 | 02-02 | 병참커맨드 6종 구현 | ✓ SATISFIED | logistics/ 6 클래스 등록 확인 |
| CMD-05 | 02-03 | 인사커맨드 10종 구현 | ✓ SATISFIED | personnel/ 10 클래스 등록 확인 |
| CMD-06 | 02-04 | 정치커맨드 12종 구현 | ✓ SATISFIED | politics/ 12 클래스 등록 확인 |
| CMD-07 | 02-04 | 첩보커맨드 14종 구현 | ✓ SATISFIED | intelligence/ 14 클래스 등록 확인 |
| CMD-08 | 02-05 | 실시간 커맨드 실행 파이프라인 | ✓ SATISFIED | RealtimeService: deductCp→duration→commandEndTime→broadcast |
| CMD-09 | 02-01 | 직무권한카드 기반 커맨드 게이팅 | ✗ BLOCKED | PositionCardRegistry 매핑은 완성되었으나 executeFactionCommand, submitNationCommand 경로에서 officerLevel < 5 조건부 우회가 게이팅을 무력화함 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt` | 72 | `&& officer.officerLevel < 5` — 직무권한카드 게이팅 우회 | 🛑 Blocker | officerLevel >= 5 장교는 직무권한카드 없이 모든 faction 커맨드 실행 가능 (Success Criterion 1 위반) |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/RealtimeService.kt` | 85 | `&& general.officerLevel < 5` — 동일 우회, 실시간 경로 | 🛑 Blocker | 실시간 모드에서도 동일한 우회 허용 |
| `backend/game-app/src/main/kotlin/com/openlogh/service/CommandService.kt` | 213-219 | `generalCategory()` 함수에 삼국지 커맨드 코드 다수 존재 ("농지개간", "상업투자", "출병", "화계" 등) | ⚠️ Warning | 커맨드 테이블 분류 로직에 삼국지 잔재. 기능 영향은 없으나 gin7 커맨드 코드가 "기타"로 분류됨 |

**Stub classification note:** `getCost()` returns `CommandCost()` (default zero cost) across all 81 gin7 commands. This is NOT a stub — the actual CP cost is read via `getCommandPointCost()` in `scheduleCommand():318`. The `getCost()` method is a legacy BaseCommand abstract that is not called in the realtime pipeline. This is a design artifact, not a blocking issue.

### Human Verification Required

None required — all automated checks are conclusive.

### Gaps Summary

Phase 2의 핵심 목표인 "삼국지 권한 우회가 완전히 제거된다"가 달성되지 않았다. 81종 커맨드 등록, CP 파이프라인, 제안 시스템은 모두 정상 구현되었으나, Faction 커맨드(지휘/정치/인사 등) 실행 경로 두 곳에서 `officerLevel < 5` 조건부 게이팅 우회가 잔존한다.

**Root cause:** `executeFactionCommand` (CommandService.kt:72)와 `submitNationCommand` (RealtimeService.kt:85) 두 함수가 "Card-based authority check with legacy officerLevel fallback" 주석과 함께 삼국지 레거시 조건을 유지하고 있다. `&&` 조건으로 연결되어 있어, `canExecute(cards, actionCode)`가 false여도 `officerLevel >= 5`이면 통과한다 — 즉 조건이 역전되어 레거시가 우회를 **허용**한다.

**Fix scope:** 두 파일에서 `&& officer.officerLevel < 5` / `&& general.officerLevel < 5` 조건 제거만으로 해결됨. 단순 2-line 수정.

---

_Verified: 2026-04-06T15:30:00Z_
_Verifier: Claude (gsd-verifier)_
