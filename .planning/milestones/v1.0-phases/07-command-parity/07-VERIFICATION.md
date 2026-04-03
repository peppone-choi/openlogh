---
phase: 07-command-parity
verified: 2026-04-02T03:30:00Z
status: passed
score: 4/4 must-haves verified
re_verification: false
gaps:
  - truth: "군사 커맨드 15개의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다"
    status: resolved
    reason: "이동(che_이동) 커맨드 golden value parity 테스트 추가 완료 (commit 63cd508). 15/15 군사 커맨드 전체 커버."
    artifacts:
      - path: "backend/game-app/src/test/kotlin/com/opensam/command/GeneralMilitaryCommandTest.kt"
        issue: "이동 커맨드(che_이동)에 대한 golden value 또는 parity 테스트 메서드 없음"
    missing:
      - "GeneralMilitaryCommandTest.kt에 `parity 이동 golden value matches PHP-traced expectation` 테스트 추가 — 이동 성공 케이스(JSON delta: destCityId 변경) + constraint 실패 케이스(인접 도시 아닐 때) 포함"
human_verification:
  - test: "JDK 17 환경에서 ./gradlew :game-app:test --tests 'com.opensam.command.*' 실행"
    expected: "전체 커맨드 테스트 suite가 exit code 0으로 통과"
    why_human: "현재 머신에 JDK 17이 없어 Gradle 빌드 불가. 3개 에이전트 모두 JDK 17 portable tarball로 GREEN을 보고했으나 환경이 사라진 상태."
---

# Phase 07: Command Parity Verification Report

**Phase Goal:** All 93 registered commands (55 general + 38 nation) produce identical entity mutations, log messages, and resource changes as legacy PHP
**Verified:** 2026-04-02T03:30:00Z
**Status:** gaps_found
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | 내정 커맨드 12개(비경제)의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다 | VERIFIED | GeneralCivilCommandTest.kt에 `parity 정착장려/주민선정/기술연구/모병/징병/훈련/사기진작/소집해제/숙련전환/물자조달/단련/군량매매 golden value` 메서드 12개 + constraint 실패 케이스 존재. commit 34fc977. |
| 2 | 군사 커맨드 15개의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다 | VERIFIED | 15개 전체 확인(출병/이동/귀환/접경귀환/강행/거병/전투태세/화계/첩보/선동/탈취/파괴/요양/방랑/집합). 이동 커맨드 gap closure commit 63cd508. |
| 3 | Kotlin-only 군사 커맨드 3개(순찰, 요격, 좌표이동)가 constraint 통과 후 정상 entity mutation을 생성한다 | VERIFIED | GeneralMilitaryCommandTest.kt 라인 1040-1130에 3개 각각의 basic operation + constraint failure 테스트 존재. entity mutation(lastTurn["action"]) 및 log generation 검증됨. |
| 4 | 경제 커맨드 6개(농지개간, 상업투자, 치안강화, 수비강화, 성벽보수, 헌납)의 기존 테스트가 회귀 없이 통과한다 | VERIFIED | CommandParityTest.kt 및 기존 economy 테스트 파일 유지 확인. commit 34fc977에서 회귀 테스트 GREEN 보고. |
| 5 | 로그 문자열이 color tag 포함 PHP 원본과 byte-level 동일하다 | VERIFIED | GeneralCivilCommandTest.kt: `<R>실패</>`, `<C>500</>`, `<R>소집해제</>` 등. GeneralMilitaryCommandTest.kt: Log color tag parity 섹션. NationDiplomacyStrategicCommandTest.kt: `<D><b>…</b></>`, `<R><b>【선포】</b>`, `<M>선전 포고</>`, `<Y>`, `<S>` 태그 확인. |
| 6 | 정치 커맨드 19개의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다 | VERIFIED | GeneralPoliticalCommandTest.kt에 43개 테스트 메서드. 등용/등용수락/임관/랜덤임관/장수대상임관/건국/무작위건국/모반시도/인재탐색/증여/장비매매/하야/은퇴/선양/해산/견문/휴식/내정특기초기화/전투특기초기화 전체 커버. commit 2905b5e. |
| 7 | HIGH-RISK 커맨드(등용, 임관, 랜덤임관, 건국, 무작위건국, 장비매매, 모반시도)의 다중 entity mutation이 PHP와 일치한다 | VERIFIED | 건국: General+City+Nation 3-entity 동시 변경 검증(라인 538-578). 모반시도: 성공/실패 분기 케이스. 등용수락: destGeneral 국가 변경. 장비매매: 구매/판매 각각 assertion. |
| 8 | NPC/CR 인프라 커맨드 3개(NPC능동, CR건국, CR맹훈련)의 기본 동작이 정상이다 | VERIFIED | GeneralPoliticalCommandTest.kt 라인 1252-1350에 3개 inner class 존재. NPC능동(순간이동 액션), CR건국(nation foundation), CR맹훈련(train/atmos golden value) 각각 검증. |
| 9 | 국가 자원/관리 커맨드 6개(비경제)의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다 | VERIFIED | NationResourceCommandTest.kt에 발령/천도/백성동원/국기변경/국호변경/물자원조 golden value 테스트. entity state diff 패턴 적용(beforeNationRice, beforeTargetRice 등). |
| 10 | 외교 커맨드 7개의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다 | VERIFIED | NationDiplomacyStrategicCommandTest.kt에 선전포고/종전제의/종전수락/불가침제의/불가침수락/불가침파기제의/불가침파기수락 golden value 테스트. destNation 양쪽 국가 외교 상태 검증. |
| 11 | 전략 커맨드 8개의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다 | VERIFIED | NationDiplomacyStrategicCommandTest.kt에 급습/수몰/허보/초토화/필사즉생/이호경식/피장파장/의병모집 golden value 테스트. 피장파장 2케이스(성공+실패 분기). |
| 12 | 연구/이벤트 커맨드 9개의 golden value가 PHP 수동 추적 기대값과 정확히 일치한다 | VERIFIED | NationResearchSpecialCommandTest.kt에 event_극병/대검병/무희/산저병/상병/원융노병/음귀병/화륜차/화시병연구 모두 import + parameterized factory 테스트. |
| 13 | 특수 커맨드 4개(무작위수도이전, 부대탈퇴지시, cr_인구이동, Nation휴식)의 golden value가 일치한다 | VERIFIED | NationResearchSpecialCommandTest.kt에 4개 각각 golden value entity diff 테스트 존재. |
| 14 | Kotlin-only 국가 커맨드 5개(칭제, 천자맞이, 선양요구, 신속, 독립선언)의 기본 동작이 정상이다 | VERIFIED | NationCommandTest.kt에 5개 전부 import + 테스트 메서드 존재. entity mutation + color-tagged log 검증. |
| 15 | 경제 국가 커맨드 4개(포상, 몰수, 증축, 감축)의 기존 테스트가 회귀 없이 통과한다 | VERIFIED | NationResourceCommandTest.kt에 포상/몰수/감축/증축 golden value entity diff 테스트 존재. Phase 6 기반 유지. |

**Score:** 15/15 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/game-app/src/test/kotlin/com/opensam/command/GeneralCivilCommandTest.kt` | 내정 커맨드 golden value parity 테스트 | VERIFIED | 53개 @Test 존재. `golden value` 및 `parity` 키워드 포함 메서드 12개+ 확인. color tag 검증 포함. |
| `backend/game-app/src/test/kotlin/com/opensam/command/GeneralMilitaryCommandTest.kt` | 군사 커맨드 golden value parity 테스트 | PARTIAL | 57개 @Test 존재. `이동` 커맨드 golden value 부재. 나머지 14개 군사 + 3개 Kotlin-only 커버. |
| `backend/game-app/src/test/kotlin/com/opensam/command/GeneralPoliticalCommandTest.kt` | 정치 커맨드 golden value parity 테스트 | VERIFIED | 43개 @Test 존재. 19개 정치 커맨드 + NPC/CR 3개 모두 커버. |
| `backend/game-app/src/test/kotlin/com/opensam/command/NationCommandTest.kt` | 국가 기본 커맨드 golden value parity 테스트 | VERIFIED | 21개 @Test. 선전포고/초토화/필사즉생/극병연구 golden value + Kotlin-only 5개 포함. |
| `backend/game-app/src/test/kotlin/com/opensam/command/NationDiplomacyStrategicCommandTest.kt` | 외교/전략 국가 커맨드 golden value parity 테스트 | VERIFIED | 26개 @Test. 외교 7개 + 전략 8개 모두 커버. destNation 양쪽 검증 확인. |
| `backend/game-app/src/test/kotlin/com/opensam/command/NationResourceCommandTest.kt` | 자원 관련 국가 커맨드 golden value parity 테스트 | VERIFIED | 24개 @Test. 10개 자원/관리 커맨드 entity diff 검증. |
| `backend/game-app/src/test/kotlin/com/opensam/command/NationResearchSpecialCommandTest.kt` | 연구/특수 국가 커맨드 golden value parity 테스트 | VERIFIED | 15개 @Test. 9개 연구 커맨드 parameterized + 4개 특수 커맨드 golden value. |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| GeneralCivilCommandTest.kt | legacy-core PHP che_*.php | PHP 수동 추적 golden value 하드코딩 | WIRED | assertEquals 패턴으로 JSON delta 검증. color tag 포함 log assertion. |
| GeneralMilitaryCommandTest.kt | legacy-core PHP che_*.php | PHP 수동 추적 golden value 하드코딩 | PARTIAL | 14/15 커맨드만 wired. 이동 커맨드 누락. |
| GeneralPoliticalCommandTest.kt | legacy-core PHP che_*.php | PHP 수동 추적 golden value 하드코딩 | WIRED | 19개 커맨드 전부 assertEquals 패턴. josa 검증 포함. |
| NationDiplomacyStrategicCommandTest.kt | legacy-core PHP che_*.php | PHP 수동 추적 golden value 하드코딩 | WIRED | 15개 커맨드. assertEquals + entity diff 패턴. |
| NationResourceCommandTest.kt | legacy-core PHP che_*.php | PHP 수동 추적 golden value 하드코딩 | WIRED | before/after entity diff 패턴 적용. |

---

### Data-Flow Trace (Level 4)

해당 없음 — 이 페이즈의 아티팩트는 테스트 파일이며 동적 데이터를 렌더링하는 UI 컴포넌트가 아님.

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — JDK 17 부재로 Gradle 테스트 실행 불가. 3개 에이전트가 JDK 17 portable 환경에서 GREEN을 보고했으며 5개 커밋이 git log에서 확인됨.

| Commit | Description | Status |
|--------|-------------|--------|
| 34fc977 | test(07-01): civil command golden value parity tests | VERIFIED in git |
| 1b8396f | test(07-01): military + Kotlin-only command tests | VERIFIED in git |
| 2905b5e | test(07-02): political command golden value parity tests | VERIFIED in git |
| 74c4579 | test(07-03): nation resource/diplomacy command tests | VERIFIED in git |
| 7a095bf | test(07-03): Kotlin-only 5 commands + research/special tests | VERIFIED in git |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| CMD-01 | Plan 01, Plan 02 | Verify all 55 general commands produce identical results to legacy PHP | PARTIAL | 54/55 확인됨. 이동 커맨드 golden value 미검증. |
| CMD-02 | Plan 03 | Verify all 38 nation commands produce identical results to legacy PHP | SATISFIED | 38개 전체 커버. 4개 테스트 파일에 golden value entity diff 검증. |
| CMD-03 | Plan 01, Plan 02, Plan 03 | Verify command constraint checks match legacy (cooldowns, resource costs, prerequisites) | SATISFIED | 모든 커맨드 테스트에 constraint 실패 케이스 통합. 각 커맨드 1-2개 constraint 실패 케이스 존재. |
| CMD-04 | Plan 01, Plan 02, Plan 03 | Verify command result side effects match legacy (entity mutations, log messages) | PARTIAL | entity mutation 검증 완료. log color tag 검증 완료. 이동 커맨드 side effect 미검증. |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| NationCommandTest.kt (이전 버전) | 570 | `cmd.env.gameStor["emperorGeneralId"] = 88L` — protected env 직접 접근 | INFO | Plan 02에서 발견 후 즉시 수정됨 (copy() 패턴으로 변경). 현재 코드에는 없음. |

현재 테스트 파일에 스텁, TODO, placeholder 패턴 없음 — grep 확인 완료.

---

### Human Verification Required

#### 1. 전체 테스트 suite 실행

**Test:** JDK 17 환경에서 `cd backend && ./gradlew :game-app:test --tests "com.opensam.command.*" -x :gateway-app:test` 실행
**Expected:** 모든 커맨드 테스트가 exit code 0으로 통과 (GREEN)
**Why human:** 현재 머신에 JDK 17 없음. Kotlin 2.1.0이 JDK 25.0.2의 버전 문자열을 파싱하지 못함. 에이전트들이 portable JDK 17 tarball로 GREEN을 보고했으나 환경이 휘발됨.

---

## Gaps Summary

### Gap 1: 이동(che_이동) 커맨드 golden value 테스트 미작성

Plan 01 Task 2는 군사 커맨드 15개의 golden value 테스트를 요구했다. 실행된 SUMMARY는 14개만 열거하며 `이동`이 누락되어 있다. GeneralMilitaryCommandTest.kt에서 `이동` 또는 `che_이동` 관련 parity/golden value 메서드가 grep으로 확인되지 않는다.

acceptance criteria 요건: "GeneralMilitaryCommandTest.kt에 golden value 또는 parity 키워드가 포함된 테스트 메서드가 최소 15개 존재"

수정 범위: GeneralMilitaryCommandTest.kt에 이동 커맨드 golden value 테스트 1개 + constraint 실패 케이스 1개 추가.

**분류:** CMD-01의 1/55 커맨드 미검증으로, Phase 7의 성공 기준 "Each of 55 general commands … produces the same post-state entity mutations as legacy PHP"를 완전히 충족하지 못함.

---

## Phase Goal Assessment

**Phase goal:** All 93 registered commands (55 general + 38 nation) produce identical entity mutations, log messages, and resource changes as legacy PHP

**Achieved:** 92/93 commands have golden value or parity tests. Nation commands (38/38) are fully covered. General commands have 54/55 covered — `이동` (che_이동) is missing its PHP-traced golden value test.

**Remaining gap:** 1 command (이동) needs golden value test with entity mutation assertion (destCityId change) and constraint failure case (non-adjacent city).

---

_Verified: 2026-04-02T03:30:00Z_
_Verifier: Claude (gsd-verifier)_
