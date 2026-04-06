---
phase: 03-tactical-battle-engine
plan: "04"
subsystem: tactical-battle
tags:
  - ground-battle
  - planet-conquest
  - websocket
  - gin7
dependency_graph:
  requires:
    - 03-01  # TacticalUnit groundUnitsEmbark/missileCount fields
    - 03-02  # TacticalBattleEngine processTick structure
    - 03-03  # BattleWebSocketController base + DetectionService
  provides:
    - GroundBattleEngine (30유닛 박스, 대기큐 보충)
    - PlanetConquestService (6종 점령 커맨드)
    - /planet-conquest WebSocket 채널
  affects:
    - TacticalBattleState (groundBattleState 필드 추가)
    - TacticalBattleService (executeConquest 메서드)
    - BattleWebSocketController (planet-conquest 채널)
tech_stack:
  added: []
  patterns:
    - TDD (RED→GREEN) for both tasks
    - Delegation to PlanetCaptureProcessor for capture aftermath
    - Stateless GroundBattleEngine (no Spring injection — pure logic)
key_files:
  created:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/GroundBattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/PlanetConquestService.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/GroundBattleEngineTest.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/PlanetConquestServiceTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/BattleWebSocketController.kt
    - backend/game-app/build.gradle.kts
decisions:
  - "GroundBattleEngine is stateless pure class — no Spring injection, injected via TacticalBattleEngine constructor pattern"
  - "GROUND_ASSAULT returns success=false (ground battle starts, not yet captured) — actual conquest via GroundBattleState.isConquestComplete tick check"
  - "groundBattleState added to TacticalBattleState as nullable field — null means no ground battle in progress"
  - "build.gradle.kts: removed GroundBattleEngineTest.kt exclusion (implemented in this plan)"
  - "GroundUnit uses String groundUnitType (ARMORED_GRENADIER/ARMORED_INFANTRY/LIGHT_MARINE) for schema flexibility"
metrics:
  duration_minutes: 35
  completed_date: "2026-04-06"
  tasks_completed: 2
  files_changed: 8
---

# Phase 3 Plan 04: Ground Battle + Planet Conquest Summary

gin7 지상전 박스(최대 30유닛)와 행성 점령 6종 커맨드 구현 완료. 육전대 강하 → 지상전 틱 처리 → 점령 완료 → PlanetCaptureProcessor 후처리 흐름을 완성했다.

## Tasks Completed

### Task 1: GroundBattleEngine — 지상전 박스 + 30유닛 제한 틱 처리

**Commit:** `9a5415c3`

**Created:** `GroundBattleEngine.kt`, `GroundBattleEngineTest.kt`
**Modified:** `TacticalBattleEngine.kt` (groundBattleState 추가 + step 5.5), `build.gradle.kts` (exclusion 제거)

- `GroundUnit`: unitId/factionId/groundUnitType/count/maxCount/morale 필드, `isAlive` computed property
- `GroundBattleState`: attackers/defenders/waitingAttackers 리스트, `totalUnitsInBox`, `isConquestComplete`, `isAttackerDefeated` computed properties
- `GroundBattleEngine.addAttackers`: 30유닛 박스 제한 — 초과 시 waitingAttackers 큐에 보관
- `GroundBattleEngine.processTick`: 공격자↔수비자 자동 교전, 타입별 피해 보정 (ARMORED_GRENADIER 1.3x, LIGHT_MARINE 0.8x)
- `replenishFromQueue`: 전멸 공격자 수만큼 대기 큐에서 보충
- `TacticalBattleState.groundBattleState: GroundBattleState? = null` 추가
- `TacticalBattleEngine.processTick` step 5.5: groundBattleState != null 시 틱 처리, conquest_complete 이벤트 발행

**Tests (10 passed):** 30유닛 정확 수용, 31번째 유닛 waitingAttackers 이동, 총 유닛 박스 계산, 수비자 전멸 시 isConquestComplete=true, 대기 큐 보충, ARMORED_GRENADIER > LIGHT_MARINE 피해, isAttackerDefeated 조건 검증

### Task 2: PlanetConquestService — 6종 점령 커맨드 + WebSocket 채널

**Commit:** `5acc61f6`

**Created:** `PlanetConquestService.kt`, `PlanetConquestServiceTest.kt`
**Modified:** `TacticalBattleService.kt` (executeConquest), `BattleWebSocketController.kt` (planet-conquest 채널)

- `ConquestCommand` enum: SURRENDER_DEMAND/PRECISION_BOMBING/CARPET_BOMBING/GROUND_ASSAULT/INFILTRATION/SUBVERSION, 각 커맨드별 비용/효과 정의
- `PlanetConquestService.executeConquest`: 6종 분기 처리
  - 항복권고: planetDefense <= 5000 → 30%, 초과 → 5% 성공 확률
  - 정밀폭격: missileCount >= 200 필요, defenseEffect=0.6, missilesConsumed=200
  - 무차별폭격: missileCount >= 500 필요, defenseEffect=0.3, approvalChange=-30, economyMultiplier=0.5
  - 육전대강하: 지상전 개시 신호 반환 (success=false)
  - 점거: militaryWorkPoint >= 4000 필요, 즉시 점령
  - 선동: intelWorkPoint >= 1000 필요, approvalChange=-50
- 점령 성공 시 `PlanetCaptureProcessor.processCaptureAftermath` 위임
- `TacticalBattleService.executeConquest`: GROUND_ASSAULT 시 groundUnitsEmbark 확인 후 GroundBattleState 초기화, 미사일 소모 반영
- `BattleWebSocketController`: `/app/battle/{sessionId}/{battleId}/planet-conquest` 채널 추가, `PlanetConquestRequest` DTO

**Tests (15 passed):** 미사일 부족 실패, 정확한 소모량, 공작포인트 부족/충족, 항복권고 확률 검증(30%/5%), captureResult 연동, empire 직할령, 6종 enum 존재 확인

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] build.gradle.kts exclusion for GroundBattleEngineTest**
- **Found during:** Task 1 execution
- **Issue:** Previous plan (03-03) had pre-emptively excluded `GroundBattleEngineTest.kt` from compilation since the implementation didn't exist yet. Our new test file was silently excluded.
- **Fix:** Removed the exclusion entry from `sourceSets.test.kotlin.exclude()` block
- **Files modified:** `backend/game-app/build.gradle.kts`
- **Commit:** `9a5415c3`

**2. [Rule 3 - Blocking] Java 25 incompatibility with Gradle 8.12**
- **Found during:** Task 1 test execution
- **Issue:** Default `java` (25.0.2) causes `IllegalArgumentException: 25.0.2` in Kotlin compiler via Gradle
- **Fix:** Used `JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/temurin-23.0.2/Contents/Home` for all Gradle invocations
- **Files modified:** None (environment only)

**3. [Rule 2 - Missing] BattleWebSocketController had additional existing handlers (unit-command)**
- **Found during:** Task 2 — controller had been extended in Plan 03-03 with `/unit-command` channel
- **Fix:** Added planet-conquest handler preserving existing channels; imported `ConquestCommand` and `ConquestRequest` from `engine.tactical`

## Known Stubs

None — all conquest commands produce real results. GROUND_ASSAULT returns `success=false` intentionally (ground battle continues via tick engine, not immediately completing).

## Self-Check: PASSED

| Item | Result |
|------|--------|
| GroundBattleEngine.kt exists | FOUND |
| PlanetConquestService.kt exists | FOUND |
| 03-04-SUMMARY.md exists | FOUND |
| Commit 9a5415c3 (Task 1) | FOUND |
| Commit 5acc61f6 (Task 2) | FOUND |
| MAX_UNITS_IN_BOX=30 in GroundBattleEngine | FOUND |
| planet-conquest in BattleWebSocketController | FOUND |
| processCaptureAftermath in PlanetConquestService | FOUND |
