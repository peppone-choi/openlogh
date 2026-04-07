---
phase: 03-tactical-battle-engine
verified: 2026-04-06T00:00:00Z
status: gaps_found
score: 3/5 success criteria verified
gaps:
  - truth: "같은 그리드에 적아 유닛이 공존하면 전투가 자동 개시되고, 종료 조건 달성 시 정상 종료된다"
    status: partial
    reason: "BattleTriggerService.checkForBattles() 및 checkBattleEnd() 구현은 완성됐으나 TickEngine.processTick()이 TacticalBattleService.processSessionBattles()를 호출하지 않아 자동 개시 루프가 게임 틱에 연결되지 않음"
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/TickEngine.kt"
        issue: "processTick()에 TacticalBattleService 의존성 없음 — processSessionBattles() 호출 누락"
    missing:
      - "TickEngine에 TacticalBattleService 주입 및 processTick() 내 processSessionBattles(sessionId) 호출 추가"
  - truth: "빔/건/미사일(물자소비) 무기가 사거리/위력/보정에 따라 피해를 계산하고 88 서브타입 스탯이 적용된다"
    status: partial
    reason: "BEAM/GUN/미사일 무기 계산은 구현됐으나 ShipStatRegistry의 88 서브타입 스탯(장갑/실드/무기/속도 세부값)이 TacticalBattleEngine 전투 계산에 반영되지 않음. shipSubtype 필드는 TacticalUnit에 존재하나 전투 로직에서 ShipStatRegistry 조회 없음"
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/tactical/TacticalBattleEngine.kt"
        issue: "processCombat()이 unit.shipSubtype을 사용하지 않음. ShipStatRegistry import 없음. 서브타입 스탯 보정 없이 고정값(BEAM_BASE_DAMAGE=30, GUN_BASE_DAMAGE=40)만 사용"
    missing:
      - "TacticalBattleEngine에 ShipStatRegistry 주입 및 shipSubtype 기반 무기/장갑 스탯 보정 적용"
      - "BattleTriggerService.buildInitialState()에서 함선 서브타입 조회 및 TacticalUnit.shipSubtype 설정"
human_verification:
  - test: "전투 자동 개시 E2E 확인"
    expected: "두 진영 함대가 같은 성계에 진입 시 전투가 자동으로 시작되고 WebSocket /topic/world/{sessionId}/tactical-battle/{battleId}에 상태가 브로드캐스트된다"
    why_human: "TickEngine-TacticalBattleService 연결이 런타임 동작이며 통합 실행 없이 grep으로 검증 불가"
  - test: "에너지 슬라이더 실시간 반영 확인"
    expected: "클라이언트가 /app/battle/{sessionId}/{battleId}/energy에 에너지 배분을 전송하면 다음 틱 브로드캐스트에 변경이 반영된다"
    why_human: "WebSocket 실시간 흐름은 실행 없이 완전 검증 불가"
---

# Phase 3: 실시간 전술전 엔진 Verification Report

**Phase Goal:** 에너지 배분, 무기 시스템, 진형, 커맨드레인지서클, 색적, 요새포, 지상전을 포함한 gin7 전술전이 실시간으로 동작한다
**Verified:** 2026-04-06
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | 같은 그리드에 적아 유닛이 공존하면 전투가 자동 개시되고, 종료 조건 달성 시 정상 종료된다 | PARTIAL | BattleTriggerService/checkBattleEnd 구현 완성. 그러나 TickEngine이 processSessionBattles() 미호출 — 자동 개시 루프 끊김 |
| 2 | BEAM/GUN/SHIELD/ENGINE/WARP/SENSOR 6채널 에너지 슬라이더 합계가 100을 유지하며 WebSocket으로 실시간 반영된다 | VERIFIED | EnergyAllocation.init에서 total()==100 강제. /energy WebSocket 채널 → setEnergyAllocation() → broadcastBattleState() 연결 완성 |
| 3 | 빔/건/미사일(물자소비) 무기가 사거리/위력/보정에 따라 피해를 계산하고 88 서브타입 스탯이 적용된다 | PARTIAL | BEAM/GUN/미사일 사거리·위력·보정 계산 구현됨. 그러나 shipSubtype 필드 존재에도 불구하고 ShipStatRegistry 기반 88 서브타입 스탯이 전투 계산에 미반영 |
| 4 | 커맨드레인지서클이 tick 경과에 따라 확대되고 명령 발령 시 0으로 리셋된다 | VERIFIED | updateCommandRange()가 매 틱 commandRange를 growthRate만큼 증가. 모든 플레이어 커맨드(에너지/태세/퇴각/공격목표/유닛커맨드)에서 commandRange=0.0, ticksSinceLastOrder=0 리셋 확인 |
| 5 | 육전대 강하 후 지상전이 시작되며 6종 행성 점령 방식이 각기 다른 결과를 낸다 | VERIFIED | PlanetConquestService에 6종 ConquestCommand(항복권고/정밀폭격/무차별폭격/육전대강하/점거/선동) 구현. GroundBattleEngine 30유닛 박스 + 대기큐. /planet-conquest WebSocket 채널 연결 |

**Score:** 3/5 truths verified (2 partial = gaps)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|---------|--------|---------|
| `engine/tactical/BattleTriggerService.kt` | 자동 전투 개시 감지 | WIRED | checkForBattles() 구현. 같은 planetId에 2+ 진영 함대 감지 → createBattle() 호출 |
| `engine/tactical/TacticalBattleEngine.kt` | 전투 틱 루프 엔진 | WIRED | processTick(), checkBattleEnd(), processCombat(), updateCommandRange() 구현 완성 |
| `model/EnergyAllocation.kt` | 6채널 에너지, 합=100 강제 | VERIFIED | init 블록에서 total()==100 require() 강제. 6개 채널 multiplier 함수 구현 |
| `controller/BattleWebSocketController.kt` | 에너지/태세/퇴각/공격목표/유닛커맨드 채널 | WIRED | 7개 @MessageMapping 채널 모두 존재 및 TacticalBattleService 위임 |
| `service/TacticalBattleService.kt` | 전투 생애주기 관리 + broadcast | WIRED | startBattle, processBattleTick, setEnergyAllocation, broadcastBattleState 구현 |
| `engine/tactical/MissileWeaponSystem.kt` | 미사일/전투정 무기 시스템 | WIRED | processMissileAttack(missileCount 소모), processFighterAttack(CARRIER전용, 속도디버프) |
| `engine/tactical/DetectionService.kt` | SENSOR 기반 색적 매트릭스 | WIRED | updateDetectionMatrix() → detectionMatrix 갱신. 정지유닛 탐지범위 +20% |
| `engine/tactical/FortressGunSystem.kt` | 요새포 4종 | WIRED | THOR_HAMMER/GAIESBURGHER/ARTEMIS/LIGHT_XRAY enum. processFortressGunFire() 사선 아군 피격 포함 |
| `engine/tactical/GroundBattleEngine.kt` | 지상전 박스 30유닛 | WIRED | MAX_UNITS_IN_BOX=30, waitingAttackers 큐, processTick() 자동 교전 |
| `engine/tactical/PlanetConquestService.kt` | 행성 점령 6종 | WIRED | ConquestCommand 6종 enum + executeConquest() 분기 처리 |
| `engine/TickEngine.kt` | 전술전 루프 호출 | ORPHANED | processTick()에 TacticalBattleService 없음. processSessionBattles() 미호출 |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| TickEngine.processTick() | TacticalBattleService.processSessionBattles() | 직접 호출 | NOT_WIRED | TickEngine에 TacticalBattleService 의존성 없음. 전술전 루프가 게임 틱과 분리됨 |
| BattleWebSocketController /energy | TacticalBattleService.setEnergyAllocation() | 직접 호출 | WIRED | 검증됨 |
| setEnergyAllocation() | broadcastBattleState() | 간접 (다음 틱) | PARTIAL | setEnergyAllocation()은 직접 broadcast 없음. 상태 변경 후 다음 processBattleTick()에서만 broadcast |
| TacticalBattleEngine.processCombat() | ShipStatRegistry (88 서브타입) | 미연결 | NOT_WIRED | shipSubtype 필드 존재하나 combat 계산에서 ShipStatRegistry 미조회 |
| PlanetConquestService.executeConquest() | PlanetCaptureProcessor.processCaptureAftermath() | 위임 | WIRED | 점령 성공 시 후처리 연결 |
| TacticalBattleEngine.checkBattleEnd() | TacticalBattleService.endBattle() | processBattleTick() 경유 | WIRED | 검증됨 |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| TacticalBattleEngine.processCombat() | beamDmg | BEAM_BASE_DAMAGE × 고정값 | 부분 — 서브타입 스탯 미반영 | STATIC (서브타입 보정 없음) |
| BattleTriggerService.checkForBattles() | allFleets | fleetRepository.findBySessionId() | DB 조회 | FLOWING |
| TacticalBattleService.broadcastBattleState() | BattleTickBroadcast | activeBattles in-memory state | 실시간 상태 | FLOWING |
| EnergyAllocation init | total() | beam+gun+shield+engine+warp+sensor | 계산값, sum=100 강제 | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — 전술전 엔진은 서버 실행 없이 독립 테스트 불가. 단 SUMMARY에서 보고된 테스트 결과 확인.

| Behavior | Test Source | Result | Status |
|----------|-------------|--------|--------|
| TacticalBattleEngineTest (6종) | 03-03 SUMMARY 보고 | 6 tests, 0 failures | PASS (self-reported) |
| MissileWeaponSystemTest (9종) | 03-03 SUMMARY 보고 | 9 tests, 0 failures | PASS (self-reported) |
| GroundBattleEngineTest (10종) | 03-04 SUMMARY 보고 | 10 tests passed | PASS (self-reported) |
| PlanetConquestServiceTest (15종) | 03-04 SUMMARY 보고 | 15 tests passed | PASS (self-reported) |
| TacticalBattleIntegrationTest (5종) | 03-05 SUMMARY 보고 | 5 tests passed | PASS (self-reported) |
| TickEngine → processSessionBattles() 연결 | 코드 검증 | TickEngine에 호출 없음 | FAIL |

---

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|---------|
| BATTLE-01 | 전투 개시/종료 조건 | PARTIAL | BattleTriggerService + checkBattleEnd() 구현 완성. TickEngine 연결 누락 |
| BATTLE-02 | 에너지 배분 시스템 (6채널, 합=100) | SATISFIED | EnergyAllocation 완전 구현, WebSocket 채널 연결 |
| BATTLE-03 | 무기 시스템 (빔/건/미사일/전투정) | PARTIAL | BEAM/GUN/미사일/전투정 구현. 88 서브타입 스탯 미적용 |
| BATTLE-04 | 진형 시스템 (4종, 공격/방어/속도 보정) | SATISFIED | Formation enum 4종, attackModifier/defenseModifier 전투 적용 확인 |
| BATTLE-05 | 커맨드레인지서클 (시간경과 확대, 발령시 0 리셋) | SATISFIED | updateCommandRange() 매 틱 증가, 모든 커맨드에서 0 리셋 |
| BATTLE-06 | 색적 시스템 (SENSOR 배분 기반) | SATISFIED | DetectionService.updateDetectionMatrix(), SENSOR sensorMultiplier() 적용 |
| BATTLE-07 | 요새포 시스템 (토르해머/가이에스하켄, 사선 아군 피격) | SATISFIED | FortressGunSystem 4종 enum, processFortressGunFire() 아군 피격 포함 |
| BATTLE-08 | 지상전 (육전대 강하, 30유닛 제한) | SATISFIED | GroundBattleEngine MAX_UNITS_IN_BOX=30, waitingAttackers 큐 |
| BATTLE-09 | 행성/요새 점령 처리 (6종) | SATISFIED | PlanetConquestService 6종 ConquestCommand 구현 |
| BATTLE-10 | 태세 시스템 (4종, 각 보정) | SATISFIED | UnitStance 4종 enum, attackModifier/defenseModifier/canMove 전투 적용. REQUIREMENTS.md는 Pending 표시이나 실제 구현됨 |
| BATTLE-11 | 전사/부상 시스템 (기함 격침→부상→귀환성 워프) | SATISFIED | TacticalBattleEngine step 5 기함 격침 처리, processFlagshipDestructions() officer.planetId 갱신 |
| BATTLE-12 | 전술 유닛 커맨드 11종 | SATISFIED | BattleWebSocketController /unit-command + TacticalBattleService.executeUnitCommand() 11종 |

**Coverage:** 10/12 BATTLE requirements SATISFIED, 2 PARTIAL (BATTLE-01, BATTLE-03)

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `engine/tactical/TacticalBattleEngine.kt:71` | `shipSubtype: String = ""` — 필드 존재하나 전투 로직에서 미사용 | BLOCKER | 88 서브타입 스탯 보정이 실제로 적용되지 않아 SC-3 실패 |
| `engine/TickEngine.kt` | TacticalBattleService 의존성 없음 | BLOCKER | 전술전 자동 개시/틱 처리가 게임 루프에서 호출되지 않음. SC-1 실패 |
| `service/TacticalBattleService.kt:57` | `processFlagshipDestructions`: returnPlanetId 항상 currentPlanetId fallback | WARNING | Phase 4에서 수도 조회 추가 예정 — 선언된 stub, Phase 3 범위 외 |
| `build.gradle.kts` | FortressGunSystemTest, MissileWeaponSystemTest 일부 exclude 잔존 가능성 | INFO | linter hook 제약으로 exclude 제거 불가 보고됨 (03-03 SUMMARY) |

---

### Human Verification Required

#### 1. 전술전 자동 개시 E2E

**Test:** 두 진영 함대를 같은 성계에 배치 후 게임 틱을 진행하여 전투가 자동으로 시작되는지 확인
**Expected:** `/topic/world/{sessionId}/tactical-battle/{battleId}` WebSocket 채널에 전투 시작 브로드캐스트 수신
**Why human:** TickEngine → TacticalBattleService 연결이 코드에 없어 반드시 연결 후 런타임 동작으로만 검증 가능

#### 2. 에너지 슬라이더 실시간 반영

**Test:** WebSocket으로 `/app/battle/{sessionId}/{battleId}/energy`에 에너지 배분 전송 후 다음 틱 브로드캐스트 확인
**Expected:** 변경된 에너지 배분이 unit 상태에 반영되어 broadcast됨
**Why human:** WebSocket 실시간 흐름은 실행 없이 완전 검증 불가

---

### Gaps Summary

Phase 3은 전체 12개 BATTLE 요구사항 중 10개가 실제 구현됐다. 그러나 두 가지 핵심 연결이 누락되어 Success Criteria를 완전히 충족하지 못한다.

**Gap 1 (BATTLE-01 / SC-1): TickEngine-전술전 루프 단절**

`TacticalBattleService.processSessionBattles()`와 `BattleTriggerService.checkForBattles()`는 구현 완성됐으나, `TickEngine.processTick()`에서 이를 호출하지 않는다. TickEngine에는 TacticalBattleService 의존성 자체가 없다. 결과적으로 전술전은 REST API(`POST /api/{sessionId}/battles/start`)로만 수동 시작 가능하며, 게임 틱에 의한 자동 개시는 동작하지 않는다.

수정: TickEngine 생성자에 `TacticalBattleService` 주입 추가, `processTick()` 내에 `tacticalBattleService.processSessionBattles(world.id.toLong())` 호출 삽입.

**Gap 2 (BATTLE-03 / SC-3): 88 서브타입 스탯 미적용**

`ShipStatRegistry`가 ship_stats JSON에서 11함종 × 8 서브타입 스탯을 로드하나, `TacticalBattleEngine.processCombat()`은 `BEAM_BASE_DAMAGE=30`, `GUN_BASE_DAMAGE=40` 고정값만 사용한다. `TacticalUnit.shipSubtype` 필드는 존재하나 전투 계산 내에서 ShipStatRegistry 조회가 전혀 없다. 88 서브타입의 장갑/실드/무기 세부 스탯이 실제 전투에 반영되지 않는다.

수정: TacticalBattleEngine에 ShipStatRegistry 주입, `processCombat()`에서 `shipSubtype`으로 stat 조회 후 기본 피해값에 보정 적용. buildInitialState()에서 fleet→officer→shipUnit 조회로 shipSubtype 설정.

이 두 gap은 독립적이며 각각 별도 플랜으로 수정 가능하다. BATTLE-10(태세 시스템)은 REQUIREMENTS.md에 Pending으로 표시되나 실제 코드에서 완전히 구현됐음을 확인했다 — REQUIREMENTS.md 상태가 코드를 반영하지 않는 문서 불일치.

---

_Verified: 2026-04-06_
_Verifier: Claude (gsd-verifier)_
