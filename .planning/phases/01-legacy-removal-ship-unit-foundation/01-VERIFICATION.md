---
phase: 01-legacy-removal-ship-unit-foundation
verified: 2026-04-06T00:00:00Z
status: gaps_found
score: 4/5 must-haves verified
re_verification: false
gaps:
  - truth: "삼국지 병종 상성 및 수치비교 전투 로직이 코드베이스에 존재하지 않는다"
    status: partial
    reason: "ARM_PER_PHASE/BattleEngine/GroundBattleEngine은 완전 삭제됨. 그러나 NpcPolicy.kt가 CrewType.FOOTMAN을 활성 코드에서 참조하고, SpecialAssignmentService.kt에 보병/궁병/기병 삼국지 병종 분류 로직이 남아 있다. ItemModifiers.kt의 isRegionalOrCityCrewType() 호출은 항상 false를 반환하는 stub이라 전투 결과에 영향 없음."
    artifacts:
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/ai/NpcPolicy.kt"
        issue: "private val defaultCrewType = CrewType.FOOTMAN — 삼국지 enum을 활성 코드에서 참조"
      - path: "backend/game-app/src/main/kotlin/com/openlogh/engine/SpecialAssignmentService.kt"
        issue: "보병/궁병/기병 SpecialCandidate 분류 로직 잔존 (dex bitmask 기반 삼국지 특기 배정)"
    missing:
      - "NpcPolicy.kt defaultCrewType 제거 또는 gin7 함종 기반 대체"
      - "SpecialAssignmentService.kt 보병/궁병/기병 분류를 gin7 함종 기반으로 교체 또는 Phase 3 stub 처리"
---

# Phase 1: 레거시 제거 + 함종 유닛 기반 Verification Report

**Phase Goal:** 삼국지 게임 로직이 완전히 제거되고 gin7 함종/유닛 엔티티가 전투와 커맨드의 기반으로 작동한다
**Verified:** 2026-04-06
**Status:** gaps_found
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #  | Truth                                                                         | Status      | Evidence                                                                                                       |
|----|-------------------------------------------------------------------------------|-------------|----------------------------------------------------------------------------------------------------------------|
| 1  | CommandRegistry에 삼국지 커맨드가 0개 존재한다 (gin7 stub 81종으로 대체됨)   | ✓ VERIFIED  | Gin7CommandRegistry.kt 158줄, 82개 registerStub() 호출. command/general/ 및 command/nation/ 디렉토리 비어있음  |
| 2  | ShipUnit 엔티티가 DB에 존재하며 11함종 × 서브타입 스탯이 ship_stats JSON에서 로드된다 | ✓ VERIFIED | ShipUnit.kt 존재, V45__create_ship_unit_table.sql fleet_id FK 확인. ShipStatRegistry @PostConstruct로 ship_stats_empire.json/ship_stats_alliance.json/ground_unit_stats.json 로드 (shared 모듈 classloader 경유) |
| 3  | 기함/육전대 유닛이 Fleet에 연결 가능하며 승조원 수련도 4단계가 적용된다       | ✓ VERIFIED  | ShipUnit.fleetId FK, isFlagship, groundUnitType/groundUnitCount 필드 확인. CrewProficiency enum: GREEN/NORMAL/VETERAN/ELITE 4단계, combatMultiplier 적용 |
| 4  | 삼국지 병종 상성 및 수치비교 전투 로직이 코드베이스에 존재하지 않는다         | ✗ PARTIAL   | BattleEngine/GroundBattleEngine/WarUnit* 19개 파일 삭제 확인. ARM_PER_PHASE/resolveBattle 0건. 그러나 NpcPolicy.kt CrewType.FOOTMAN 활성 참조 및 SpecialAssignmentService.kt 보병/궁병/기병 분류 로직 잔존 |
| 5  | `grep -r "officerLevel >= 5"` 결과가 0이거나 stub 대체가 완료된 상태이다     | ✓ VERIFIED  | backend/ 전체 grep 결과 0건 확인                                                                               |

**Score:** 4/5 truths verified

### Required Artifacts

| Artifact                                                                  | Expected                          | Status      | Details                                                           |
|---------------------------------------------------------------------------|-----------------------------------|-------------|-------------------------------------------------------------------|
| `backend/game-app/src/main/kotlin/com/openlogh/command/Gin7CommandRegistry.kt` | 81종 gin7 stub 레지스트리    | ✓ VERIFIED  | 158줄, 82개 stub (81커맨드 + 대기), @Primary @Component           |
| `backend/game-app/src/main/kotlin/com/openlogh/command/general/`          | 삼국지 커맨드 0개 (전량 삭제)     | ✓ VERIFIED  | 디렉토리 존재하나 비어있음                                        |
| `backend/game-app/src/main/kotlin/com/openlogh/command/nation/`           | 삼국지 커맨드 0개 (전량 삭제)     | ✓ VERIFIED  | 디렉토리 존재하나 비어있음                                        |
| `backend/game-app/src/main/kotlin/com/openlogh/entity/ShipUnit.kt`        | Fleet FK + 11함종 + 4단계 수련도  | ✓ VERIFIED  | fleetId, shipClass, shipSubtype, crewProficiency, isFlagship, groundUnitType 확인 |
| `backend/game-app/src/main/resources/db/migration/V45__create_ship_unit_table.sql` | ship_unit 테이블 DDL       | ✓ VERIFIED  | fleet_id REFERENCES fleet(id) ON DELETE CASCADE 확인              |
| `backend/game-app/src/main/kotlin/com/openlogh/repository/ShipUnitRepository.kt` | ShipUnit CRUD 레포지토리    | ✓ VERIFIED  | findByFleetId, findFlagship 등 파생 쿼리 메서드 확인              |
| `backend/game-app/src/main/kotlin/com/openlogh/service/ShipStatRegistry.kt` | JSON 기반 88 서브타입 스탯 레지스트리 | ✓ VERIFIED | @PostConstruct load(), getShipStat(), ShipSubtypeStat/GroundUnitStat data class 확인 |
| `backend/game-app/src/main/kotlin/com/openlogh/service/ShipUnitService.kt` | ShipUnit 생성/조회/전투 서비스  | ✓ VERIFIED  | createShipUnit() ShipStatRegistry 스탯 주입 확인                 |
| `backend/shared/src/main/resources/data/ship_stats_empire.json`           | 제국 함종 스탯 JSON               | ✓ VERIFIED  | 파일 존재 확인                                                    |
| `backend/shared/src/main/resources/data/ship_stats_alliance.json`         | 동맹 함종 스탯 JSON               | ✓ VERIFIED  | 파일 존재 확인                                                    |
| `backend/shared/src/main/resources/data/ground_unit_stats.json`           | 지상 유닛 스탯 JSON               | ✓ VERIFIED  | 파일 존재 확인                                                    |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/war/BattleEngine.kt` | 삭제됨                            | ✓ VERIFIED  | 파일 없음 확인                                                    |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/war/GroundBattleEngine.kt` | 삭제됨                      | ✓ VERIFIED  | 파일 없음 확인                                                    |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/ai/NpcPolicy.kt`    | 삼국지 CrewType 참조 없어야 함    | ✗ PARTIAL   | `private val defaultCrewType = CrewType.FOOTMAN` 활성 참조 잔존  |
| `backend/game-app/src/main/kotlin/com/openlogh/engine/SpecialAssignmentService.kt` | 삼국지 병종 분류 없어야 함 | ✗ PARTIAL | 보병/궁병/기병 SpecialCandidate 분류 로직 및 dex bitmask 잔존     |

### Key Link Verification

| From                   | To                              | Via                            | Status      | Details                                              |
|------------------------|---------------------------------|--------------------------------|-------------|------------------------------------------------------|
| Gin7CommandRegistry    | CommandExecutor                 | @Primary Spring 주입           | ✓ WIRED     | @Primary @Component 확인                             |
| ShipUnit               | Fleet                           | fleet_id FK (V45 migration)    | ✓ WIRED     | REFERENCES fleet(id) ON DELETE CASCADE               |
| ShipUnitService        | ShipStatRegistry                | createShipUnit() 내 호출       | ✓ WIRED     | getShipStat() 호출로 스탯 주입                       |
| ShipStatRegistry       | ship_stats JSON files           | classloader getResourceAsStream | ✓ WIRED    | @PostConstruct load() 확인                           |
| CommandRegistry        | Gin7CommandRegistry             | open class 상속                | ✓ WIRED     | extends CommandRegistry() 확인                       |

### Data-Flow Trace (Level 4)

ShipUnit/ShipStatRegistry는 동적 데이터를 렌더링하는 UI 컴포넌트가 아니므로 Level 4 data-flow trace 대상에서 제외.
ShipUnitService.createShipUnit()이 ShipStatRegistry.getShipStat()을 호출하여 실제 스탯을 주입하는 흐름은 Level 3에서 확인됨.

### Behavioral Spot-Checks

| Behavior                          | Command                                                           | Result              | Status  |
|-----------------------------------|-------------------------------------------------------------------|---------------------|---------|
| Gin7CommandRegistry 82종 등록     | wc -l Gin7CommandRegistry.kt                                      | 158줄, 82 registerStub | ✓ PASS |
| officerLevel >= 5 bypass 0건      | grep -rn "officerLevel >= 5" backend/ --include="*.kt"            | 0건                 | ✓ PASS  |
| BattleEngine 삭제 확인            | ls engine/war/ 내 BattleEngine.kt                                | 파일 없음           | ✓ PASS  |
| ARM_PER_PHASE 0건                 | grep -rn "ARM_PER_PHASE" backend/ --include="*.kt"               | 0건                 | ✓ PASS  |
| ShipUnit V45 마이그레이션 존재     | ls V45__create_ship_unit_table.sql                                | EXISTS              | ✓ PASS  |
| NpcPolicy CrewType.FOOTMAN 잔존   | grep "FOOTMAN" NpcPolicy.kt                                       | line 202에서 발견   | ✗ FAIL  |
| SpecialAssignmentService 보병/기병/궁병 | grep "보병\|기병\|궁병" SpecialAssignmentService.kt           | lines 35,64,68,72   | ✗ FAIL  |

### Requirements Coverage

| Requirement | Description                              | Status         | Evidence                                                         |
|-------------|------------------------------------------|----------------|------------------------------------------------------------------|
| LEGACY-01   | 삼국지 93개 커맨드 완전 제거             | ✓ SATISFIED    | command/general/ 75개 + command/nation/ 47개 삭제, Gin7CommandRegistry 대체 |
| LEGACY-02   | 병종 상성 시스템(보병/궁병/기병) 완전 제거 | ✗ PARTIAL    | BattleEngine/GroundBattleEngine 삭제, ItemModifiers 수치비교 로직 stub처리. 그러나 NpcPolicy.kt/SpecialAssignmentService.kt에 보병/궁병/기병 로직 잔존 |
| LEGACY-03   | 삼국지 수치비교 자동전투 로직 제거       | ✓ SATISFIED    | ARM_PER_PHASE/resolveBattle/WarUnit* 0건. BattleSimService stub 처리 |
| LEGACY-04   | 삼국지 경제 로직(농업/상업 수치) 제거    | ✓ SATISFIED    | EconomyService processIncome/calcCityGoldIncome 등 삭제, processMonthly() stub |
| LEGACY-05   | 삼국지 아이템(무기/서적/말) 제거         | ✓ SATISFIED    | SUMMARY 확인 (Plan 01-05 범위 내 완료 기록)                      |
| LEGACY-06   | 삼국지 NPC 데이터(general_pool.json) 제거 | ✓ SATISFIED   | SUMMARY 확인 (Plan 01-05 범위 내 완료 기록)                      |
| SHIP-01     | 11함종 × I~VIII 서브타입 유닛 구현      | ✓ SATISFIED    | ShipUnit.kt shipClass/shipSubtype, ShipStatRegistry JSON 로드     |
| SHIP-02     | 진영별 함종 차이 구현                    | ✓ SATISFIED    | ship_stats_empire.json / ship_stats_alliance.json 별도 파일, getShipStat() factionType 기반 조회 |
| SHIP-03     | 기함 유닛 구현                           | ✓ SATISFIED    | ShipUnit.isFlagship, flagshipCode 필드 확인                      |
| SHIP-04     | 육전대 유닛 구현                         | ✓ SATISFIED    | ShipUnit.groundUnitType, groundUnitCount, ground_unit_stats.json 확인 |
| SHIP-05     | 함정 유닛 상세 스탯 구현                 | ✓ SATISFIED    | armor, shield, weaponPower, speed, crewCapacity, supplyCapacity 컬럼 V45 확인 |
| SHIP-06     | 승조원 수련도 시스템 4단계               | ✓ SATISFIED    | CrewProficiency enum GREEN/NORMAL/VETERAN/ELITE, combatMultiplier 0.7/1.0/1.2/1.5 |

**Note on LEGACY-01~03 REQUIREMENTS.md 상태:** REQUIREMENTS.md에서 LEGACY-01/02/03이 `[ ]` (미완료)로 표시되어 있으나, 실제 코드베이스 검증 결과 LEGACY-01/03은 완료됨. LEGACY-02는 부분 완료.

### Anti-Patterns Found

| File                                                       | Line | Pattern                              | Severity    | Impact                                                          |
|------------------------------------------------------------|------|--------------------------------------|-------------|-----------------------------------------------------------------|
| `engine/ai/NpcPolicy.kt`                                  | 202  | `CrewType.FOOTMAN` 활성 참조         | ⚠️ Warning  | 삼국지 CrewType enum을 NPC 병력 비용 계산에 사용. 전투 결과에 직접 영향 없으나 완전 제거 미완 |
| `engine/SpecialAssignmentService.kt`                       | 35,64,68,72 | 보병/궁병/기병 SpecialCandidate 분류 | ⚠️ Warning | 삼국지 병종 기반 특기 배정 로직 잔존. Phase 3 전까지 호출 여부 불명확 |
| `engine/modifier/ItemModifiers.kt`                         | 258,334 | TODO Phase 3 주석 + isRegionalOrCityCrewType() 호출 | ℹ️ Info | 항상 false 반환 stub — 전투 결과에 영향 없음. Phase 3 대체 예정 |
| `Gin7CommandRegistry.kt` (전체)                            | -    | 82개 커맨드 전부 stub (Phase 2 미구현) | ℹ️ Info   | 의도된 stub — Phase 2에서 구현 예정. 이 페이즈 목표 범위 내    |

### Human Verification Required

없음 — 이 페이즈는 백엔드 코드 변경만 포함하며 UI/시각적 검증이 필요한 항목 없음.

### Gaps Summary

**Truth 4 (삼국지 병종 상성 및 수치비교 전투 로직 코드베이스에 존재하지 않는다)** 가 PARTIAL 상태임.

핵심 삼국지 전투 엔진(BattleEngine, GroundBattleEngine, WarUnit*, ARM_PER_PHASE)은 완전히 제거됨. 수치비교 자동전투 로직(LEGACY-03)도 정리됨.

그러나 다음 두 파일에 삼국지 병종 분류 잔재가 남아있음:

1. **NpcPolicy.kt line 202**: `private val defaultCrewType = CrewType.FOOTMAN` — NPC 병력 비용 계산에 삼국지 CrewType enum 사용. 전투 공식에 직접 참여하지는 않으나 레거시 타입을 활성 비즈니스 로직에서 참조.

2. **SpecialAssignmentService.kt lines 35,64,68,72**: 보병/궁병/기병 기반 SpecialCandidate 분류. 삼국지 병종 체계를 gin7 특기 배정에 계속 사용하고 있음.

이 두 파일은 Plan 01-02의 Task 2 scope에서 명시적으로 다루지 않았고 SUMMARY에도 언급되지 않음. LEGACY-02 (병종 상성 시스템 완전 제거) 요구사항이 이 잔재로 인해 완전 충족되지 않은 상태.

**근본 원인:** `CrewType` enum 자체가 Plan 01-02에서 "다수 command/AI 파일에서 참조됨"을 이유로 삭제 보류됨. 이로 인해 해당 enum을 활성 코드에서 참조하는 파일들이 정리 대상에서 누락됨.

**영향도:** 현재 전투 결과에는 영향 없음 (ItemModifiers stub, BattleEngine 삭제됨). Phase 3 gin7 전술전 엔진 구현 전에 정리하면 충분.

---

_Verified: 2026-04-06_
_Verifier: Claude (gsd-verifier)_
