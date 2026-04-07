---
phase: 07-scenario-data-balancing
plan: "01"
subsystem: scenario-data
tags: [scenario, officer-data, fleet-creation, logh, UC799]
dependency_graph:
  requires: []
  provides:
    - S10 scenario officer data (43 officers, 8-stat LOGH format)
    - LOGH scenario initial fleet creation in ScenarioService
  affects:
    - ScenarioService.initializeWorld()
    - scenario_logh_10.json
    - ScenarioServiceTest
tech_stack:
  added: []
  patterns:
    - "JSON scenario data: 18-element 8-stat row format [affinity, name, pic, nationIdx, planet, lead, cmd, intel, pol, admin, mob, atk, def, officerLevel, bornYear, deadYear, p1, p2]"
    - "officerLevel 0-10 LOGH rank scale (원수=10, 상급대장=9, 대장=8, 중장=7, 소장=6, 준장=5)"
    - "Fleet creation: groupBy factionId, maxByOrNull officerLevel for leader selection"
key_files:
  created:
    - backend/game-app/src/test/resources/data/general_pool.json
  modified:
    - backend/shared/src/main/resources/data/scenarios/scenario_logh_10.json
    - backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt
    - backend/game-app/src/test/kotlin/com/openlogh/service/ScenarioServiceTest.kt
    - backend/game-app/build.gradle.kts
decisions:
  - "S10 officerLevels corrected from legacy scale (0-30) to LOGH 0-10 rank system"
  - "로이엔탈 S10 제외: UC799.4 시점 기준 이미 사망"
  - "양 웬리 deadYear=799: 버밀리언 해전 이전 생존 배치"
  - "페잔 자치령 3번째 진영으로 S10에 추가"
metrics:
  duration: "~90 minutes"
  completed: "2026-04-07"
  tasks_completed: 2
  files_modified: 5
---

# Phase 07 Plan 01: S10 시나리오 데이터 완성 + LOGH 초기 함대 생성 Summary

S10(UC799.4 황제 만세!) 시나리오의 장교 데이터를 LOGH 세계관에 맞게 완성하고, LOGH 맵 시나리오 초기화 시 진영별 제1함대를 자동 생성하는 로직을 ScenarioService에 추가했다. 이전 실행에서 Task 2가 미완료 상태였으며 이번 실행에서 완료했다.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | S10 장교 데이터 완성 | 3d2add32 | scenario_logh_10.json |
| 2 | LOGH 시나리오 초기 함대 생성 | 1c50c1b1 | ScenarioService.kt, ScenarioServiceTest.kt, build.gradle.kts, general_pool.json |

## What Was Built

### Task 1: S10 장교 데이터 완성

`scenario_logh_10.json`의 `general[]` 배열을 UC799.4 시점에 맞게 재작성:

- **43명** 장교 데이터, 모두 18-element 8-stat LOGH 형식
- officerLevel을 삼국지 스케일(0-30)에서 LOGH 0-10 계급 체계로 정정
  - 라인하르트: officerLevel=10 (원수)
  - 미터마이어, 키르히아이스: officerLevel=9 (상급대장)
  - 뮐러, 비텐펠트, 바렌, 케슬러, 오베르슈타인: officerLevel=8 (대장)
  - 양 웬리: officerLevel=8, deadYear=799
- 로이엔탈 제외 (UC799.4 시점 기준 이미 사망)
- 페잔 자치령 3번째 진영으로 추가 (루빈스키, 코넬리우스, 도밀로)

### Task 2: LOGH 시나리오 초기 함대 생성

`ScenarioService.initializeWorld()`에서 `mapName == "logh"` 조건 시 진영별 초기 함대 자동 생성. FleetRepository를 생성자에 주입하고 officerRepository.saveAll() 직후 함대 생성 블록 추가. 함대 리더는 해당 진영 최고 officerLevel 장교로 자동 선정.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] ScenarioServiceTest 반사 메서드명 오류 수정**
- **Found during:** Task 2
- **Issue:** `ScenarioServiceTest`가 `build.gradle.kts`에서 컴파일 제외되어 있었음. 리플렉션 호출이 실제 메서드명과 불일치 (`parseGeneral`→`parseOfficer`, `parseNation`→`parseFaction`, `factionIdx`→`nationIdx`)
- **Fix:** 모든 반사 메서드명 수정, build.gradle.kts 제외 목록에서 제거
- **Files modified:** ScenarioServiceTest.kt, build.gradle.kts
- **Commit:** 1c50c1b1

**2. [Rule 2 - Missing Fixture] seedGeneralPool 테스트 픽스처 누락**
- **Found during:** Task 2
- **Issue:** `seedGeneralPool` 테스트가 `general_pool.json` 클래스패스 리소스를 필요로 하나 test/resources에 파일 없음
- **Fix:** `test/resources/data/general_pool.json` 최소 픽스처 파일 생성
- **Files modified:** backend/game-app/src/test/resources/data/general_pool.json
- **Commit:** 1c50c1b1

**3. [Rule 1 - Bug] S10 officerLevel 스케일 오류**
- **Found during:** Task 1
- **Issue:** S10 데이터가 S09 복사본으로 officerLevel이 삼국지 스케일(30, 28, 26...)로 설정
- **Fix:** 모든 officerLevel을 LOGH 0-10 체계로 재설정
- **Files modified:** scenario_logh_10.json
- **Commit:** 3d2add32

## Verification Results

```
S10 general count: 43 - OK
Nations: ['은하제국', '자유행성동맹', '페잔 자치령']
Reinhard: officerLevel=10, leadership=98, command=96
Yang Wenli: officerLevel=8, leadership=92, command=95
Roiental present: False (correct for S10)

All S1-S10 scenarios have general[] data:
S01: 46  S02: 46  S03: 46  S04: 46  S05: 46
S06: 46  S07: 46  S08: 46  S09: 43  S10: 43

ScenarioServiceTest: BUILD SUCCESSFUL (32 tests, 0 failures)
```

## Known Stubs

None.

## Self-Check: PASSED

- `/Users/apple/Desktop/개인프로젝트/openlogh/backend/shared/src/main/resources/data/scenarios/scenario_logh_10.json` — FOUND
- `/Users/apple/Desktop/개인프로젝트/openlogh/backend/game-app/src/main/kotlin/com/openlogh/service/ScenarioService.kt` — FOUND (fleetRepository injected)
- `/Users/apple/Desktop/개인프로젝트/openlogh/backend/game-app/src/test/kotlin/com/openlogh/service/ScenarioServiceTest.kt` — FOUND (fixed and passing)
- Commit `3d2add32` — scenario JSON data
- Commit `1c50c1b1` — fleet creation logic + test fixes
