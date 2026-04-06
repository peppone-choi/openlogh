---
phase: 02-gin7-command-system
plan: "02"
subsystem: command-engine
tags: [commands, mcp, operations, logistics, fleet, gin7]
dependency_graph:
  requires: [02-01]
  provides: [operations-16, logistics-6, registry-22-wired]
  affects: [Gin7CommandRegistry, CommandExecutor]
tech_stack:
  added: []
  patterns: [OfficerCommand-subclass, MCP-pool, fleet-meta-mutation]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/WarpNavigationCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/FuelResupplyCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/IntraSystemNavigationCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/TrainingCommands.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/AlertSortieCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/ArmedSuppressionCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/SplitMarchCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/RequisitionCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/SpecialSecurityCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/operations/GroundForceCommands.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/logistics/FullRepairCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/logistics/FullResupplyCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/logistics/ReorganizeCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/logistics/ReinforceCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/logistics/TransferGoodsCommand.kt
    - backend/game-app/src/main/kotlin/com/openlogh/command/gin7/logistics/AllocateCommand.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/command/Gin7CommandRegistry.kt
decisions:
  - "Planet has no supplies field — RequisitionCommand and TransferGoodsCommand use planet.production as the resource proxy"
  - "FullRepairCommand sets fleet.meta[pendingFullRepair]=true instead of directly calling ShipUnitRepository (not available in CommandServices) — turn engine processes the flag"
  - "ArmedSuppressionCommand approval uses Float arithmetic (approval is Float on Planet entity)"
metrics:
  duration: 45
  completed_date: "2026-04-06"
  tasks: 2
  files: 17
---

# Phase 02 Plan 02: 작전커맨드 16종 + 병참커맨드 6종 구현 Summary

작전커맨드(MCP) 16종과 병참커맨드(MCP) 6종 구현체를 신규 생성하고, Gin7CommandRegistry init 블록에서 22종 stub을 실제 클래스 팩토리로 교체했다.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | 작전커맨드 16종 구현체 (operations/) | b937634b | 10 files |
| 2 | 병참커맨드 6종 + Gin7CommandRegistry 갱신 | b937634b | 7 files |

## What Was Built

### 작전커맨드 (Operations, MCP) — 16종

| Class | 커맨드명 | 핵심 효과 | waitTime | duration |
|-------|----------|-----------|----------|----------|
| WarpNavigationCommand | 워프항행 | officer.planetId = destPlanetId | 0 | 0 |
| FuelResupplyCommand | 연료보급 | officer.supplies += 1000 (faction차감) | 8 | 48 |
| IntraSystemNavigationCommand | 성계내항행 | officer.planetId 변경, fleet.planetId 동기화 | 8 | 0 |
| MaintainDisciplineCommand | 군기유지 | officer.morale = min(100, morale+5) | 0 | 0 |
| FlightTrainingCommand | 항공훈련 | officer.training = min(100, training+3) | 0 | 0 |
| GroundCombatTrainingCommand | 육전훈련 | officer.training = min(100, training+3) | 0 | 0 |
| SpaceCombatTrainingCommand | 공전훈련 | officer.training = min(100, training+3) | 0 | 0 |
| GroundTacticsTrainingCommand | 육전전술훈련 | officer.training = min(100, training+4) | 0 | 0 |
| SpaceTacticsTrainingCommand | 공전전술훈련 | officer.training = min(100, training+4) | 0 | 0 |
| AlertSortieCommand | 경계출동 | fleet.meta["stance"] = "COMBAT" | 24 | 0 |
| ArmedSuppressionCommand | 무력진압 | planet.security+20, planet.approval-10 | 24 | 0 |
| SplitMarchCommand | 분열행진 | log only (Phase 3 전술전 연동 예정) | 24 | 0 |
| RequisitionCommand | 징발 | planet.production-500, faction.supplies+500 | 24 | 0 |
| SpecialSecurityCommand | 특별경비 | city.security = min(100, security+10) | 0 | 24 |
| GroundForceDeployCommand | 육전대출격 | officer.meta["groundForceStance"]="DEPLOYED" | 0 | 0 |
| GroundForceWithdrawCommand | 육전대철수 | officer.meta["groundForceStance"]="WITHDRAWN" | 0 | 0 |

### 병참커맨드 (Logistics, MCP) — 6종

| Class | 커맨드명 | 핵심 효과 |
|-------|----------|-----------|
| FullRepairCommand | 완전수리 | fleet.meta["pendingFullRepair"]=true (turn engine 처리) |
| FullResupplyCommand | 완전보급 | officer.supplies → maxSupplies (faction차감) |
| ReorganizeCommand | 재편성 | fleet.meta["formation"] = WEDGE/BY_CLASS/MIXED/THREE_COLUMN |
| ReinforceCommand | 보충 | officer.ships += shipCount |
| TransferGoodsCommand | 반출입 | IN: planet.production→officer.supplies / OUT: 반대 |
| AllocateCommand | 할당 | faction.funds -= amount, targetOfficer.funds += amount |

### Gin7CommandRegistry

22종 stub(`registerMcpStub`) 호출을 실제 클래스 팩토리(`registerOfficerCommand { g, e, a -> XxxCommand(g, e, a) }`)로 교체. 나머지 stub(지휘/대기)은 Plan 02-03에서 처리됨.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Planet.supplies 필드 없음 — production으로 대체**
- **Found during:** Task 1 (RequisitionCommand), Task 2 (TransferGoodsCommand)
- **Issue:** 계획에서 `destPlanet.supplies`를 참조했으나 Planet 엔티티에 `supplies` 필드가 없고 `production`이 가장 근접한 자원 필드
- **Fix:** RequisitionCommand와 TransferGoodsCommand에서 `planet.production`을 자원 proxy로 사용
- **Files modified:** RequisitionCommand.kt, TransferGoodsCommand.kt

**2. [Rule 1 - Bug] Planet.approval이 Float — 산술 연산 타입 수정**
- **Found during:** Task 1 (ArmedSuppressionCommand)
- **Issue:** `planet.approval -= 10`이 Float 타입 불일치로 컴파일 오류
- **Fix:** `maxOf(0f, target.approval - 10f)` 로 Float 연산 (linter 자동 수정)
- **Files modified:** ArmedSuppressionCommand.kt

**3. [Rule 2 - Missing functionality] FullRepairCommand ShipUnitRepository 미노출**
- **Found during:** Task 2
- **Issue:** CommandServices에 `shipUnitRepository`가 없어 ShipUnit 직접 복구 불가
- **Fix:** `fleet.meta["pendingFullRepair"] = true` 플래그로 대체 — turn engine이 처리하도록 위임
- **Files modified:** FullRepairCommand.kt

## Self-Check

- [x] 16종 operations 파일 존재: `ls operations/*.kt | wc -l` = 10 (16 classes in 10 files)
- [x] 6종 logistics 파일 존재: `ls logistics/*.kt | wc -l` = 6
- [x] Gin7CommandRegistry에 워프항행/완전수리 실제 팩토리 등록 확인
- [x] `./gradlew :game-app:compileKotlin` BUILD SUCCESSFUL

## Self-Check: PASSED
