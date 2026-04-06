---
phase: 01-legacy-removal-ship-unit-foundation
plan: 02
subsystem: engine/war
tags: [legacy-removal, battle-engine, samguk, crew-type]
dependency_graph:
  requires: []
  provides: [engine/war 삼국지 전투 엔진 제거]
  affects: [BattleSimService, OfficerAI, ItemModifiers, SpecialModifiers]
tech_stack:
  added: []
  patterns: [stub-with-todo, delete-and-replace]
key_files:
  deleted:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/FieldBattleService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/GroundBattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarUnit.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarUnitOfficer.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarUnitPlanet.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarUnitTrigger.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarFormula.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/WarAftermath.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/FieldBattleTrigger.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/trigger/ (8 files)
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleTrigger.kt (stub)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/TacticalCombatEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/BattleSimService.kt (stub)
    - backend/game-app/src/main/kotlin/com/openlogh/engine/ai/OfficerAI.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/ItemModifiers.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/SpecialModifiers.kt
  preserved:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/DetectionEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/PlanetCaptureProcessor.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/war/TacticalCombatEngine.kt
decisions:
  - BattleTrigger.kt kept as stub (not deleted) because ItemModifiers.kt imports from engine.war.BattleTrigger — deletion would break compilation
  - TacticalCombatEngine.kt kept in engine/war/ (not moved to tactical/) — move is Phase 3 work
  - DetectionEngine.kt and PlanetCaptureProcessor.kt preserved — gin7 logic, needed by TacticalCombatEngine
  - OfficerAI.pickCrewType() stubbed to return general.shipClass — removes all CrewType.fromCode calls while preserving method signature
  - ItemModifiers typeAdvantage/antiRegional/demonSlayer blocks stubbed — CrewType 병종 상성 logic removed
metrics:
  duration: ~25 minutes
  completed: 2026-04-06
  tasks_completed: 2
  files_deleted: 19
  files_modified: 6
---

# Phase 1 Plan 02: 삼국지 전투 엔진 제거 Summary

**One-liner:** 삼국지 ARM_PER_PHASE 수치비교 전투 엔진(BattleEngine + WarUnit* + trigger/ 19개 파일) 전량 삭제, gin7 TacticalBattleEngine 보존, CrewType 병종 상성 참조 제거, compileKotlin BUILD SUCCESS.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | engine/war/ 삼국지 전투 파일 삭제 및 호출 지점 제거 | 01a92de3 | 19 deleted, BattleTrigger.kt stub, TacticalCombatEngine.kt fixed, BattleSimService.kt stub |
| 2 | CrewType 병종 참조 제거 + model/CrewType.kt 확인 | 01a92de3 | OfficerAI.kt, ItemModifiers.kt, SpecialModifiers.kt |

## What Was Done

### Task 1: engine/war/ 삼국지 전투 파일 삭제

Deleted 19 files from `engine/war/`:
- `BattleEngine.kt` — ARM_PER_PHASE 기반 수치비교 전투 로직
- `BattleService.kt` — resolveBattle() Spring 서비스
- `FieldBattleService.kt` — 삼국지 야전 서비스
- `GroundBattleEngine.kt` — 삼국지 지상전 엔진
- `WarUnit.kt`, `WarUnitOfficer.kt`, `WarUnitPlanet.kt`, `WarUnitTrigger.kt` — 삼국지 전투 유닛 모델
- `WarFormula.kt`, `WarAftermath.kt` — 삼국지 전투 공식/사후처리
- `FieldBattleTrigger.kt` — 삼국지 야전 트리거
- `trigger/` 서브패키지 8개 파일 (BattleHealTrigger, RageTrigger, SnipingTrigger 등)

**Kept in engine/war/ (gin7 logic):**
- `DetectionEngine.kt` — gin7 색적 시스템
- `PlanetCaptureProcessor.kt` — gin7 행성 점령 처리
- `TacticalCombatEngine.kt` — gin7 전술전 틱 엔진 (GroundBattleEngine 참조 제거)
- `BattleTrigger.kt` — stub으로 대체 (ItemModifiers 컴파일 의존성 유지)

**External callers fixed:**
- `BattleSimService.kt` — stub 반환 (TODO Phase 3)
- `TacticalCombatEngine.kt` — `groundBattleEngine` 프로퍼티 제거

### Task 2: CrewType 병종 참조 제거

`CrewType.fromCode()` 호출 지점 3개 제거:
- `OfficerAI.kt` — `pickCrewType()` 함수 전체를 stub으로 대체 (`general.shipClass.toInt()` 반환)
- `ItemModifiers.kt` — `parseCrewType()` → `null` 반환 stub, `typeAdvantage`/`antiRegional` 블록 제거
- `SpecialModifiers.kt` — `isRegionalOrCityCrewType()` → `false` 반환 stub

`model/CrewType.kt` 자체는 삭제하지 않음 — 삼국지 enum 데이터이지만 현재도 다수 command/AI 파일에서 참조됨. 전량 제거는 Rule 4 (architectural scope) 해당. 핵심 수치비교 전투 로직 참조는 모두 제거됨.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] BattleTrigger.kt stub 생성**
- **Found during:** Task 1 컴파일 검증
- **Issue:** `ItemModifiers.kt`가 삭제된 `BattleTrigger` 및 `BattleTriggerRegistry`를 `engine.war` 패키지에서 import — 삭제하면 컴파일 실패
- **Fix:** `BattleTrigger.kt`를 stub interface + empty registry object로 재생성 (원본 내용 삭제, stub 유지)
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleTrigger.kt`
- **Commit:** 01a92de3

**2. [Rule 1 - Bug] TacticalCombatEngine.kt GroundBattleEngine 참조 제거**
- **Found during:** Task 1 컴파일 검증
- **Issue:** `TacticalCombatEngine.kt`가 삭제된 `GroundBattleEngine()`을 프로퍼티로 선언
- **Fix:** 해당 프로퍼티 선언을 TODO 주석으로 대체
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/war/TacticalCombatEngine.kt`
- **Commit:** 01a92de3

**3. [Rule 1 - Bug] ItemModifiers.kt typeAdvantage onCalcOpposeStat 블록 추가 제거**
- **Found during:** Task 2 컴파일 검증 (2차)
- **Issue:** `onCalcOpposeStat`에 `parseCrewType().getAttackCoef()` 호출이 추가로 존재 — `Any?` 반환 타입과 불일치
- **Fix:** 해당 블록을 TODO 주석으로 대체
- **Files modified:** `backend/game-app/src/main/kotlin/com/openlogh/engine/modifier/ItemModifiers.kt`
- **Commit:** 01a92de3

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `BattleSimService.simulate()` returns "미구현" | `service/BattleSimService.kt` | 삼국지 BattleEngine 삭제. Phase 3에서 gin7 TacticalBattleEngine으로 대체 |
| `BattleTriggerRegistry.get()` returns null | `engine/war/BattleTrigger.kt` | 삼국지 trigger/ 삭제. Phase 3에서 gin7 전투 트리거로 대체 |
| `OfficerAI.pickCrewType()` returns `general.shipClass` | `engine/ai/OfficerAI.kt` | 삼국지 CrewType 병종 선택 제거. Phase 3 AI 재설계 예정 |
| `ItemModifiers.parseCrewType()` returns null | `engine/modifier/ItemModifiers.kt` | 삼국지 병종 상성 제거. Phase 3 gin7 함종 상성으로 대체 |
| `SpecialModifiers.isRegionalOrCityCrewType()` returns false | `engine/modifier/SpecialModifiers.kt` | 삼국지 지역병 상성 제거. Phase 3 대체 예정 |

All stubs are intentional — the affected APIs (battle simulation, trigger lookup, crew selection) will be replaced with gin7-compatible implementations in Phase 3.

## Self-Check: PASSED

Verified:
- `BattleEngine.kt` deleted: confirmed missing
- `BattleService.kt` deleted: confirmed missing
- `GroundBattleEngine.kt` deleted: confirmed missing
- `TacticalBattleEngine.kt` preserved: confirmed exists at `engine/tactical/TacticalBattleEngine.kt`
- `ARM_PER_PHASE|resolveBattle|WarUnitOfficer|CrewType.fromCode` in main/kotlin: 0 matches
- `ARM_PER_PHASE|병종상성` in main/kotlin: 0 matches
- Commit 01a92de3 exists: confirmed
- `./gradlew :game-app:compileKotlin` BUILD SUCCESSFUL with Java 23
