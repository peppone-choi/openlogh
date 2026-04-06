---
phase: 02-gin7-command-system
plan: "04"
subsystem: command-system
tags: [politics, intelligence, commands, gin7, PCP, MCP]
dependency_graph:
  requires: [02-01]
  provides: [PoliticsCommands-12, IntelligenceCommands-14]
  affects: [Gin7CommandRegistry, Faction, Planet, Officer]
tech_stack:
  added: []
  patterns: [OfficerCommand-subclass, MCP-override, meta-map-state]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/politics/PoliticsCommands.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/intelligence/IntelligenceCommands.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/command/Gin7CommandRegistry.kt
decisions:
  - All 12 politics commands use PCP pool (default, no override needed)
  - All 14 intelligence commands override getCommandPoolType() = StatCategory.MCP
  - Planet.approval is Float - arithmetic uses Float arithmetic and toFloat() casts
  - Officer.morale is Short - morale arithmetic uses toShort() cast
  - DistributionCommand validates funds sufficiency before transfer
  - ExecutionCommand calls pushGlobalHistoryLog for global visibility
metrics:
  duration_minutes: 8
  completed_date: "2026-04-06T14:31:20Z"
  tasks_completed: 2
  files_created: 2
  files_modified: 1
---

# Phase 02 Plan 04: 정치커맨드 12종 + 첩보커맨드 14종 Summary

**One-liner:** PCP 정치커맨드 12종(세율변경/외교/지지도) + MCP 첩보커맨드 14종(잠입/파괴/선동/체포) 구현, Gin7CommandRegistry 26종 실제 클래스 교체 완료

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | 정치커맨드 12종 구현체 | ae1f6cc0 | PoliticsCommands.kt (created) |
| 2 | 첩보커맨드 14종 + Registry 갱신 | 64cea7b6 | IntelligenceCommands.kt (created), Gin7CommandRegistry.kt (modified) |

## What Was Built

### PoliticsCommands.kt (12종, PCP)

| Class | 커맨드 | Effect |
|-------|--------|--------|
| BanquetCommand | 야회 | nation.meta["approvalBonus"] += 5 |
| HuntCommand | 수렵 | general.morale = min(100, morale+10) |
| ConferenceCommand | 회담 | destFaction.meta["diplomacyStatus"] = "TALK" |
| AddressCommand | 담화 | city.approval = min(100, approval+3) |
| SpeechCommand | 연설 | city.approval += 5, nation.meta["speechBonus"] = true |
| NationalGoalCommand | 국가목표 | nation.meta["goal"] = arg["goal"] |
| TaxRateChangeCommand | 납입률변경 | nation.taxRate = rate (0~100 validated) |
| TariffRateChangeCommand | 관세율변경 | nation.meta["tariffRate"] = rate |
| DistributionCommand | 분배 | nation.funds -= amount, destFaction.funds += amount |
| ExecutionCommand | 처단 | destOfficer.meta["executed"]=true, factionId=0, pushGlobalHistoryLog |
| DiplomacyCommand | 외교 | nation.meta["diplomacyAction_{id}"] = action |
| GovernanceGoalCommand | 통치목표 | city.meta["governanceGoal"] = arg["goal"] |

### IntelligenceCommands.kt (14종, MCP)

| Class | 커맨드 | cpCost | Effect |
|-------|--------|--------|--------|
| GeneralSearchCommand | 일제수색 | 160 | destPlanetOfficers.forEach { meta["searched"]=true } |
| ArrestAuthorizationCommand | 체포허가 | 800 | destOfficer.meta["arrestWarrant"]=true |
| ExecutionOrderCommand | 집행명령 | 800 | destOfficer.meta["executionOrder"]=true |
| ArrestOrderCommand | 체포명령 | 160 | destOfficer.meta["arrested"]=true |
| InspectionCommand | 사열 | 160 | city.security = min(100, security+5) |
| RaidCommand | 습격 | 160 | destPlanet.orbitalDefense -= rng(100..500) |
| SurveillanceCommand | 감시 | 160 | destOfficer.meta["underSurveillance"]=true |
| InfiltrationOpCommand | 잠입공작 | 160 | general.meta["infiltratedPlanet"]=destPlanet.id |
| EscapeOpCommand | 탈출공작 | 160 | general.meta.remove("infiltratedPlanet") |
| IntelligenceOpCommand | 정보공작 | 160 | general.meta["intelOn_{id}"]=mapOf(funds,techLevel) |
| SabotageOpCommand | 파괴공작 | 160 | destPlanet.production -= rng(1..5) |
| AgitationOpCommand | 선동공작 | 160 | destPlanet.approval -= rng(5..20) |
| IncursionOpCommand | 침입공작 | 320 | general.meta["incursionTarget"]=destFaction.id |
| ReturnOpCommand | 귀환공작 | 320 | general.meta.remove("incursionTarget") |

### Gin7CommandRegistry.kt Changes

- Replaced 12 `registerPcpStub` calls with `registerOfficerCommand { -> XxxCommand(...) }` for politics
- Replaced 14 `registerMcpStub` calls with `registerOfficerCommand { -> XxxCommand(...) }` for intelligence
- Added 26 explicit imports for all new command classes

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Type precision fixes**
- **Found during:** Task 1 compilation
- **Issue:** `Officer.morale` is `Short`, `Planet.approval` is `Float` — raw arithmetic produced type mismatch
- **Fix:** Added `.toShort()` cast for morale, used `Float` arithmetic for approval
- **Files modified:** PoliticsCommands.kt, IntelligenceCommands.kt
- **Commit:** ae1f6cc0, 64cea7b6

**2. [Out-of-scope] Pre-existing build failures**
- **Found during:** Task 1 verification
- **Issue:** Build was already failing before this plan due to errors in `AssignmentCommand.kt`, `FullRepairCommand.kt`, `ArmedSuppressionCommand.kt` (from previous plans), plus unused `FiefCommands` import
- **Action:** Confirmed pre-existing via `git stash` test. Logged to deferred-items. Not fixed (out of scope per deviation rules)
- **Note:** Java 25 incompatible with Gradle 8.12 — used `JAVA_HOME=temurin-23` for build verification

## Self-Check: PASSED

- `/Users/apple/Desktop/개인프로젝트/openlogh/backend/game-app/src/main/kotlin/com/openlogh/command/gin7/politics/PoliticsCommands.kt` — FOUND
- `/Users/apple/Desktop/개인프로젝트/openlogh/backend/game-app/src/main/kotlin/com/openlogh/command/gin7/intelligence/IntelligenceCommands.kt` — FOUND
- Commit ae1f6cc0 — FOUND (Task 1)
- Commit 64cea7b6 — FOUND (Task 2)
- Registry grep count for key classes (BanquetCommand|IntelligenceOpCommand|TaxRateChangeCommand) = 6 — FOUND
- 12 politics `registerOfficerCommand` entries in registry — CONFIRMED
- 14 intelligence `registerOfficerCommand` entries in registry — CONFIRMED
- All 14 intelligence commands have `override fun getCommandPoolType(): StatCategory = StatCategory.MCP` — CONFIRMED
