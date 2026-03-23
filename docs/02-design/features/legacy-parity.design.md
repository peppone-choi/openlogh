# Design: Legacy Parity (전체 패러티 점검 및 버그 수정)

## Executive Summary

| 항목 | 내용 |
|------|------|
| Feature | legacy-parity |
| Plan 참조 | `docs/01-plan/features/legacy-parity.plan.md` |
| 작성일 | 2026-03-23 |
| 설계 방향 | Option A — 최소 변경 (테스트 수정 + 로직 비교 기반 패치) |

### 현재 상태

| 항목 | 결과 |
|------|------|
| 패러티 테스트 (Phase 1) | 181/181 PASS |
| 백엔드 전체 테스트 | 1112/1136 (24 FAIL) |
| 프론트엔드 테스트 | 237/237 PASS |
| 프론트엔드 타입체크 | PASS |

---

## 1. 테스트 실패 수정 (24건 → 근본 원인 1개)

### 1.1 근본 원인

`HistoryService.logWorldHistory()` 시그니처에 `scenarioInit: Boolean = false` 파라미터가 추가된 후, 6개 테스트 파일의 `verify()` 호출이 미업데이트됨.

```kotlin
// Production 시그니처
fun logWorldHistory(worldId: Long, message: String, year: Int, month: Int, scenarioInit: Boolean = false)

// 문제 패턴: 5번째 any()가 Boolean에 null을 전달 → NPE
verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), any())

// 수정: 5번째 파라미터 제거 (default 사용) 또는 eq(false) 명시
verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt())
```

### 1.2 수정 대상 파일 (6개)

| 파일 | 수정 라인 | 수정 내용 |
|------|----------|----------|
| `EventActionServiceTest.kt` | :348 | `any()` 제거 |
| `EventServiceTest.kt` | :91, :163, :287 | `any()` 제거 (3곳) |
| `GeneralMaintenanceServiceTest.kt` | :475 | `any()` 제거 |
| `BattleServiceTest.kt` | :209, :410 | `any()` 제거 (2곳) |
| `ScenarioServiceTest.kt` | :603 | `any()` 제거 또는 `eq(true)` (scenarioInit=true 케이스) |
| `NpcAiParityTest.kt` | :44 | mock 설정 순서 수정 (캐스케이드 해소) |

### 1.3 검증

```bash
cd backend && ./gradlew test --no-daemon
# 목표: 1136/1136 PASS
```

---

## 2. 코드 비교 전략 (Phase 2~10)

### 2.1 비교 방법론

```
Legacy PHP 파일 읽기 → Kotlin 대응 코드 읽기 → 수식/조건/결과 1:1 대조
                                                      ↓
                                              불일치 발견 시
                                                      ↓
                                    의도적 변경? → Yes → 스킵 (기록)
                                         ↓ No
                                    패러티 테스트 추가 → 코드 수정 → 테스트 통과
```

### 2.2 Phase 2: 커맨드 로직 비교

**비교 단위**: 개별 커맨드 파일 1:1

```
Legacy: hwe/sammo/Command/General/che_XXX.php
   ↔
Current: backend/game-app/src/.../command/general/XXX.kt
```

**비교 체크리스트 (각 커맨드):**
- [ ] 실행 조건 (제약조건 체인) 일치
- [ ] 성공/실패 확률 공식 일치
- [ ] 결과 수치 변화량 일치
- [ ] 로그 메시지 형식 일치 (차이는 의도적 변경으로 분류 가능)
- [ ] 에지 케이스 처리 일치

**55 장수 커맨드 비교 순서 (우선도순):**

| 순서 | 카테고리 | 커맨드 (수) | 이유 |
|------|---------|------------|------|
| 1 | 전투 | 출병, 강행, 거병, 탈취, 파괴, 화계, 전투태세 (7) | 전투 로직과 밀접 |
| 2 | 내정 | 모병~군량매매 (14) | 경제 밸런스 핵심 |
| 3 | 이동 | 이동, 집합, 귀환, 접경귀환, 방랑 (5) | 맵 이동 로직 |
| 4 | 인사 | 등용, 임관, 건국, 모반, 선동 등 (12) | 정치 시스템 |
| 5 | 경제 | 증여, 장비매매, 헌납 (3) | 자원 관리 |
| 6 | 기타 | 휴식, 은퇴, 요양, NPC능동 등 (14) | 상태 변경 |

**38 국가 커맨드 비교 순서:**

| 순서 | 카테고리 | 커맨드 (수) | 이유 |
|------|---------|------------|------|
| 1 | 외교 | 선전포고~불가침파기수락 (7) | 외교 상태 전이 |
| 2 | 전략 | 급습, 의병, 필사즉생, 초토화, 허보 등 (9) | 전투 영향 |
| 3 | 행정 | 천도, 국호변경, 포상, 몰수 등 (12) | 국가 관리 |
| 4 | 연구 | event_상병~원융노병 (9) | 병종 해금 |

### 2.3 Phase 3: 전투 시스템 비교

**파일 매핑:**

| Legacy | Current | 비교 항목 |
|--------|---------|----------|
| `process_war.php` 전체 | `BattleEngine.kt` | 전투 루프, 라운드 처리 |
| `process_war.php` 데미지 | `WarFormula.kt` | 물리/지략/화공 데미지 공식 |
| `process_war.php` 유닛 | `WarUnitGeneral.kt`, `WarUnitCity.kt` | 공격/방어력 계산 |
| `process_war.php` 결과 | `BattleTrigger.kt` | 사망/포로/부상/경험치 |

**핵심 공식 비교:**
```
1. baseAttack = (leadership * techAbil + strength * dexLevel) * crewCoeff
2. baseDamage = attacker.attack / (attacker.attack + defender.defence) * multiplier
3. criticalChance = f(intel, dexLevel, crewType)
4. dodgeChance = f(leadership, dexLevel, crewType)
5. injuryChance = f(damage, maxHp, crewType)
```

### 2.4 Phase 4: NPC AI 비교

**파일 매핑:**

| Legacy (GeneralAI.php) | Current | 비교 항목 |
|------------------------|---------|----------|
| 줄 1~500 | `GeneralAI.kt` | 분류(classifyGeneral), 우선순위 |
| 줄 500~2000 | `GeneralAI.kt` | 방랑/내정/군사/정치 결정 |
| 줄 2000~3500 | `NationAI.kt` | 국가 전략, 외교 판단 |
| 줄 3500~4293 | `NationAI.kt` | 고급 전략, 특수 상황 |

**비교 방법**: 500줄 단위로 분할 비교 (에이전트 병렬 가능)

### 2.5 Phase 5~10: 나머지 영역

| Phase | Legacy 소스 | Current 대응 | 비교 방법 |
|-------|-----------|-------------|----------|
| 5 경제 | `func.php` 내 경제함수 | `EconomyService.kt` | 함수 단위 추출 비교 |
| 6 턴처리 | `daemon.ts` + `func_process.php` | `TurnService.kt` 파이프라인 | 단계 순서 대조 |
| 7 이벤트 | `func_time_event.php` | `EventService.kt` | 이벤트 종류/조건 대조 |
| 8 API | `hwe/sammo/API/` 78개 | 50+ Controllers | 엔드포인트 매핑표 작성 |
| 9 데이터 | `hwe/scenario/`, `hwe/data/` | `resources/data/` | JSON 구조 비교 |
| 10 프론트 | PHP 출력 페이지들 | Next.js 페이지 | 표시 항목 누락 점검 |

---

## 3. 구현 순서

```
Step 1: 테스트 수정 (24건 → 0건)
  ├─ 6개 파일의 verify() 호출 수정
  └─ 검증: ./gradlew test → 1136/1136 PASS
        ↓
Step 2: 커맨드 비교 (Phase 2) — 에이전트 병렬
  ├─ 장수 전투 커맨드 7개 비교
  ├─ 장수 내정 커맨드 14개 비교
  ├─ 국가 외교 커맨드 7개 비교
  └─ 나머지 커맨드 비교
        ↓
Step 3: 전투 시스템 비교 (Phase 3) — 에이전트 병렬
  ├─ 데미지 공식 비교
  ├─ 유닛 스탯 비교
  └─ 결과 처리 비교
        ↓
Step 4: NPC AI 비교 (Phase 4)
  ├─ GeneralAI 500줄씩 분할 비교
  └─ NationAI 비교
        ↓
Step 5: 나머지 Phase 5~10 비교
        ↓
Step 6: Gap Analysis (/pdca analyze)
```

---

## 4. 불일치 발견 시 처리 기준

| 상황 | 처리 |
|------|------|
| 수식/조건 불일치 (의도적 변경 아님) | 패러티 테스트 추가 → 코드 수정 |
| 의도적 변경 (5-stat, DB, 아키텍처 등) | 스킵, 비교 보고서에 기록 |
| 레거시 버그 (PHP에도 버그가 있는 경우) | 수정하되, 레거시 동작 기록 |
| 표시 형식 차이 (UI/UX 개선) | 스킵 (데이터 정확성만 확인) |
| 누락 기능 | 구현 추가 |

---

## 5. 파일 변경 예상

### Step 1 (테스트 수정)

| 파일 | 변경 내용 | 라인 수 |
|------|----------|--------|
| `EventActionServiceTest.kt` | verify 5th param 제거 | ~1줄 |
| `EventServiceTest.kt` | verify 5th param 제거 (3곳) | ~3줄 |
| `GeneralMaintenanceServiceTest.kt` | verify 5th param 제거 | ~1줄 |
| `BattleServiceTest.kt` | verify 5th param 제거 (2곳) | ~2줄 |
| `ScenarioServiceTest.kt` | verify param 수정 | ~1줄 |
| `NpcAiParityTest.kt` | mock 초기화 순서 수정 | ~3줄 |

**총 변경**: 6개 파일, ~11줄

### Step 2~5 (패러티 비교 후 수정)

현재 미확정 — 비교 결과에 따라 결정
