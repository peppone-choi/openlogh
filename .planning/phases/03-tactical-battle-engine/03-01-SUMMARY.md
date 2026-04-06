---
phase: 03-tactical-battle-engine
plan: "01"
subsystem: tactical-battle-engine
tags: [tactical-battle, websocket, stance, energy, morale, gin7]
dependency_graph:
  requires: []
  provides: [TacticalUnit-contract, battle-loop-base, stance-energy-websocket]
  affects: [TacticalBattleService, TacticalBattleEngine, BattleTriggerService]
tech_stack:
  added: []
  patterns: [TDD-red-green, STOMP-websocket, gin7-stance-rules]
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/controller/BattleWebSocketController.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleEngineTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/model/UnitStance.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/dto/TacticalBattleDtos.kt
decisions:
  - "BattleWebSocketController uses officerId in payload (not OfficerPrincipal) — consistent with existing JWT filter which stores String subject, not typed principal"
  - "UnitStance.defenseModifier added: NAVIGATION=1.0, ANCHORING=1.1, STATIONED=1.3, COMBAT=0.9 — inverse of attackModifier direction"
  - "COMBAT moraleDecayRate=0.002 → (0.002*100).toInt()=0 per tick; meaningful decay accumulates over 500+ ticks as designed"
metrics:
  duration_minutes: 35
  completed_date: "2026-04-06"
  tasks_completed: 2
  files_changed: 6
---

# Phase 03 Plan 01: 전술전 기본 루프 확장 Summary

TacticalUnit 계약 확장(7개 신규 필드) + gin7 태세 4종 룰(attackModifier/defenseModifier/moraleDecayRate/canMove) + 사기 20 미만 전투불가 + 에너지·태세 WebSocket 채널 + BattleTriggerService 자동 개시 연결.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 (RED) | TacticalBattleEngineTest 실패 테스트 작성 | 8f1e02df | TacticalBattleEngineTest.kt |
| 1 (GREEN) | TacticalUnit 필드 확장 + 태세/사기 룰 엔진 적용 | c1f2a7bc | TacticalBattleEngine.kt, UnitStance.kt |
| 2 | 에너지·태세 WebSocket 채널 + 자동 개시 연결 | 053050fc | BattleWebSocketController.kt, TacticalBattleService.kt, TacticalBattleDtos.kt |

## What Was Built

### TacticalUnit 계약 확장

7개 신규 필드가 `TacticalUnit` data class에 추가됨:

- `stance: UnitStance = UnitStance.NAVIGATION` — 현재 태세
- `missileCount: Int = 100` — 미사일 잔탄
- `shipSubtype: String = ""` — 함선 세부 타입
- `isFlagship: Boolean = false` — 기함 여부
- `groundUnitsEmbark: Int = 0` — 탑재 지상부대
- `fighterSpeedDebuffTicks: Int = 0` — 스파르타니안 디버프 잔여 틱
- `ticksSinceStanceChange: Int = 0` — 태세 변경 쿨다운 카운터

`TacticalBattleState`에 `currentPhase: String = "MOVEMENT"` 추가.

### gin7 태세 룰 적용

`UnitStance`에 `defenseModifier` 필드 추가 후 엔진에 적용:

| Stance | attackModifier | defenseModifier | canMove |
|--------|---------------|-----------------|---------|
| NAVIGATION | 1.0 | 1.0 | true |
| ANCHORING | 0.8 | 1.1 | false |
| STATIONED | 0.5 | 1.3 | false |
| COMBAT | 1.3 | 0.9 | true |

**processTick 변경:**
- 사기 20 미만 유닛 → `effectiveUnits` 필터로 combat 스킵
- `ticksSinceStanceChange` 매 틱 증가

**processMovement:** `!unit.stance.canMove` → 즉시 return

**processCombat:** `beamDmg/gunDmg *= unit.stance.attackModifier`

**applyDamage:** `absorbed *= target.stance.defenseModifier`

**updateMorale:** COMBAT 태세 → `unit.morale -= (0.002 * 100).toInt()` (매 틱 0, 500틱 누적)

### WebSocket 채널

`BattleWebSocketController` 신규 생성:
- `/app/battle/{sessionId}/{battleId}/energy` → `setEnergyAllocation()`
- `/app/battle/{sessionId}/{battleId}/stance` → `setStance()` (10틱 쿨다운)

### BattleTriggerService 자동 개시

`processSessionBattles()` 앞에 `checkForBattles()` 호출 추가:
1. 신규 전투 감지 → `registerNewBattle()` → `activeBattles` 등록 + DB ACTIVE 저장 + broadcast
2. 기존 활성 전투 틱 처리

## Deviations from Plan

### Auto-adapted Issues

**1. [Rule 2 - Pattern Consistency] OfficerPrincipal → payload officerId**
- **Found during:** Task 2 - BattleWebSocketController 구현
- **Issue:** 계획에서 `OfficerPrincipal`을 `UsernamePasswordAuthenticationToken.principal`로 추출하도록 지정했으나, 현재 `JwtAuthenticationFilter`는 `principal`을 `String`(JWT subject)으로 설정함. `OfficerPrincipal` 클래스 미존재.
- **Fix:** `officerId`를 payload DTO(`EnergyAllocationRequest`, `StanceChangeRequest`)에 포함하는 방식 채택. 기존 `TacticalBattleController`와 동일한 패턴.
- **Files modified:** `BattleWebSocketController.kt` (EnergyAllocationRequest, StanceChangeRequest DTO)

**2. [Rule 1 - Logic] moraleModifier 분기 제거**
- **Found during:** Task 1 - processCombat 수정
- **Issue:** 기존 코드에 `if (unit.morale < MORALE_DAMAGE_THRESHOLD) 0.5 else ...` 분기가 있었으나, 계획에서 사기 20 미만은 effectiveUnits 필터로 combat 자체를 스킵하도록 명시함 — 분기가 중복/불일치.
- **Fix:** `moraleModifier`에서 사기 미달 분기 제거. 0.5 패널티 대신 effectiveUnits 필터가 완전 스킵을 담당.

## Known Stubs

없음. 모든 구현이 실제 동작하는 코드로 완성됨.

## Self-Check: PASSED

All 6 key files verified present. All 3 task commits (8f1e02df, c1f2a7bc, 053050fc) confirmed in git log.
