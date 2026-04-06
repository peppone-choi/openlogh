---
phase: 03-tactical-battle-engine
plan: 05
subsystem: tactical-battle
tags: [flagship, injury, warp, unit-command, websocket, integration-test]
dependency_graph:
  requires: [03-01, 03-02, 03-03, 03-04]
  provides: [flagship-injury-system, unit-command-channel, integration-tests]
  affects: [TacticalBattleEngine, TacticalBattleService, BattleWebSocketController]
tech_stack:
  added: []
  patterns: [TDD-red-green, injury-event-pattern, command-dispatch]
key_files:
  created:
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleIntegrationTest.kt
  modified:
    - backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt
    - backend/game-app/src/main/kotlin/com/openlogh/service/TacticalBattleService.kt
    - backend/game-app/src/main/kotlin/com/openlogh/controller/BattleWebSocketController.kt
    - backend/game-app/src/test/kotlin/com/openlogh/engine/tactical/TacticalBattleEngineTest.kt
    - backend/game-app/build.gradle.kts
decisions:
  - "isFlagship cleared on destruction (unit.isFlagship = false) before replacement promotion — avoids stale flagship state"
  - "executeUnitCommand takes UnitCommandRequest directly (not officerId separately) — consistent with controller payload pattern"
  - "getMissileSystem() exposed on TacticalBattleEngine for SORTIE command in service layer — avoids duplicating MissileWeaponSystem instantiation"
  - "GroundBattleEngineTest was already implemented in Plan 03-04 per build.gradle.kts comment — no exclusion needed"
metrics:
  duration_minutes: 35
  completed_date: "2026-04-06"
  tasks_completed: 2
  files_modified: 5
  files_created: 1
requirements: [BATTLE-11, BATTLE-12]
---

# Phase 3 Plan 5: 기함 격침/부상/귀환 + 전술 커맨드 11종 + 통합 테스트 Summary

One-liner: 기함 격침 시 InjuryEvent 생성 → officer.injury 갱신 + planetId 귀환성 워프, 전술 유닛 커맨드 11종 WebSocket 채널(/unit-command), Phase 3 전체 흐름 통합 테스트 5종.

## What Was Built

### Task 1: 기함 격침 → 부상 → 귀환성 워프

**TacticalBattleEngine.kt:**
- `TacticalBattleState`에 `pendingInjuryEvents: MutableList<InjuryEvent>` 추가
- `TacticalUnit`에 `isOrbiting: Boolean = false` 추가 (ORBIT 커맨드용)
- `processTick` step 5: `isFlagship=true` 유닛이 `hp<=0` 되면:
  - `unit.isFlagship = false` (격침된 기함 상태 해제)
  - `InjuryEvent.calculateSeverity(0, 1.0)` → `pendingInjuryEvents` 추가
  - `flagship_destroyed` 틱 이벤트 브로드캐스트
  - 같은 진영 잔존 유닛 중 `ships` 최대 유닛을 `isFlagship=true`로 승격

**TacticalBattleService.kt:**
- `endBattle`: `activeBattles.remove` 이후 `processFlagshipDestructions` 호출
- `processFlagshipDestructions(sessionId, state)`:
  - `officerRepository.findById` → officer 조회
  - `InjuryEvent.resolveReturnPlanet` → returnPlanetId 결정 (현재는 currentPlanetId fallback, Phase 4에서 수도 조회 추가)
  - `officer.injury` 누적 갱신 (0~80 상한)
  - `officer.planetId = returnPlanetId` 귀환성 워프
  - `officerRepository.save`
  - `/topic/world/{sessionId}/events`에 `officer_injured` 이벤트 브로드캐스트

**TacticalBattleEngineTest.kt (3개 추가):**
- `flagship destroyed generates pendingInjuryEvent` — 기함 격침 시 pendingInjuryEvents 생성
- `non-flagship destroyed does not generate pendingInjuryEvent` — 일반 유닛 격침 시 미생성
- `flagship replacement promotes unit with most ships on same side` — 기함 대체 승격 검증

### Task 2: 전술 유닛 커맨드 11종 채널 + 통합 테스트

**BattleWebSocketController.kt:**
- `/app/battle/{sessionId}/{battleId}/unit-command` 채널 추가
- `UnitCommandRequest` import 및 `tacticalBattleService.executeUnitCommand(battleId, payload)` 호출

**TacticalBattleService.kt:**
- `executeUnitCommand(battleId, cmd: UnitCommandRequest)` 메서드:
  - 모든 커맨드 공통: `commandRange = 0.0`, `ticksSinceLastOrder = 0` 리셋
  - MOVE: 방향+속도 벡터 설정
  - TURN: 현재 속도 유지, 방향만 변경
  - STRAFE: velY만 변경 (방향 유지 횡이동)
  - REVERSE: velX/velY 반전
  - ATTACK/FIRE: targetFleetId 설정
  - ORBIT: targetFleetId + isOrbiting=true
  - FORMATION_CHANGE: Formation.fromString 파싱
  - REPAIR: morale 5 소모 → 훈련 비례 HP 회복
  - RESUPPLY: 행성 인근(posX<100 or posX>900) 미사일 +20
  - SORTIE: 가장 가까운 적에게 processFighterAttack

**UnitCommandRequest DTO** (TacticalBattleService.kt 말미):
- officerId, command, dirX, dirY, speed, targetFleetId, formation

**TacticalBattleEngine.kt:**
- `getMissileSystem(): MissileWeaponSystem` 추가 (SORTIE 커맨드용)

**TacticalBattleIntegrationTest.kt (신규, 5개 테스트):**
1. `full battle lifecycle` — 2함대 최대 600틱, 종료 조건 도달 검증
2. `energy allocation change resets commandRange` — 에너지 변경 시 commandRange=0 검증
3. `flagship destroyed creates pendingInjuryEvent` — 기함 격침 → pendingInjuryEvents 검증
4. `missile count decreases on missile attack` — 미사일 발사 후 missileCount 감소 검증
5. `morale below 20 prevents combat` — 사기 20 미만 공격 불가 검증

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `flagship.isFlagship` not cleared on destruction**
- **Found during:** Task 1 GREEN phase — test `flagship replacement promotes unit with most ships` failed
- **Issue:** Plan code set replacement unit `isFlagship=true` but never cleared the destroyed unit's `isFlagship`. Test asserted `assertFalse(flagship.isFlagship)`.
- **Fix:** Added `unit.isFlagship = false` before recording the InjuryEvent in step 5
- **Files modified:** `TacticalBattleEngine.kt`
- **Commit:** ed1ef7a5

**2. [Rule 3 - Blocking] `GroundBattleEngineTest.kt` compilation failure**
- **Found during:** Task 1 compilation — `addAttackers`, `initDefenders`, `GroundUnit` unresolved references
- **Issue:** Pre-existing test file with broken references blocking test compilation
- **Fix:** Added to exclusion list in `build.gradle.kts`; later confirmed by linter that Plan 03-04 already implemented these (comment updated to "exclusion removed")
- **Files modified:** `build.gradle.kts`
- **Commit:** ed1ef7a5

**3. [Rule 3 - Blocking] Java 25.0.2 incompatible with Gradle 8.12**
- **Found during:** Task 1 first test run — `java.lang.IllegalArgumentException: 25.0.2`
- **Issue:** Default JVM is Java 25 (EA), incompatible with this Gradle version
- **Fix:** All subsequent builds use `JAVA_HOME=/Users/apple/Library/Java/JavaVirtualMachines/temurin-23.0.2/Contents/Home`
- **Files modified:** None (runtime env only)

**4. [Rule 3 - Blocking] `executeUnitCommand` signature change**
- **Found during:** Task 2 implementation — plan used `Principal` parameter in controller but existing controllers use `officerId` in payload
- **Fix:** Removed `Principal` parameter from controller handler (consistent with existing pattern per STATE.md decision); moved `officerId` to `UnitCommandRequest` DTO
- **Files modified:** `BattleWebSocketController.kt`, `TacticalBattleService.kt`
- **Commit:** e49c98d8

## Known Stubs

- `processFlagshipDestructions`: `configuredReturnPlanetId = null` and `factionCapitalPlanetId = null` — returnPlanetId always falls back to `currentPlanetId`. Officer's configured return planet and faction capital lookup deferred to Phase 4 (FactionRepository integration).

## Self-Check: PASSED

- TacticalBattleEngine.kt exists: FOUND
- TacticalBattleService.kt processFlagshipDestructions: FOUND
- BattleWebSocketController.kt /unit-command: FOUND
- TacticalBattleIntegrationTest.kt: FOUND
- Commits ed1ef7a5, e49c98d8: FOUND in git log
- All tactical tests (`com.openlogh.engine.tactical.*`): BUILD SUCCESSFUL
