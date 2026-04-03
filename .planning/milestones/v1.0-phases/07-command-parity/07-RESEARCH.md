# Phase 7: Command Parity - Research

**Researched:** 2026-04-02
**Domain:** 93개 커맨드(55 장수 + 38 국가) 레거시 PHP 패러티 검증 및 수정
**Confidence:** HIGH

## Summary

Phase 7은 93개 등록 커맨드가 레거시 PHP와 동일한 entity mutation, log message, resource change를 생성하는지 검증하는 단계이다. 경제 관련 커맨드 12개는 Phase 6에서 이미 검증 완료되었으므로, 이 Phase에서는 비경제 커맨드만을 대상으로 한다.

기존 테스트 인프라는 매우 탄탄하다. 카테고리별 테스트 파일 7개(GeneralCivilCommandTest, GeneralMilitaryCommandTest, GeneralPoliticalCommandTest, NationCommandTest, NationDiplomacyStrategicCommandTest, NationResourceCommandTest, NationResearchSpecialCommandTest)가 이미 존재하며, CommandParityTest와 EconomyCommandParityTest에서 golden value 패턴이 확립되어 있다. 기존 테스트는 주로 "동작 확인"(constraint 통과, message 키 존재) 수준이고, Phase 7에서는 이를 "정확 값 매칭"(golden value assertion) 수준으로 보강해야 한다.

Kotlin-only 커맨드 8개(장수: 순찰, 요격, 좌표이동 / 국가: 독립선언, 선양요구, 신속, 천자맞이, 칭제)는 PHP에 대응물이 없으므로 parity 대상이 아니며, 기본 동작 테스트만 추가한다.

**Primary recommendation:** 카테고리별 순차 검증(내정 -> 군사 -> 정치 -> 국가) 순서로 기존 테스트 파일에 golden value assertion을 추가하되, PHP 원본의 `run()` 로직을 수동 추적하여 기대값을 산출하고 불일치 발견 시 즉시 Kotlin 코드를 수정한다.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 카테고리별 순차 검증 -- 내정(18) -> 군사(18) -> 정치(19) -> 국가(38) 순서로 Plan 2~3개로 분할. 기존 테스트 파일 구조와 일치.
- **D-02:** 기존 테스트 파일에 golden value assertion 추가하여 확장. PHP 원본에서 기대값 추출 후 기존 테스트 구조를 보강.
- **D-03:** 경제 관련 커맨드(무역, 징수 등)는 Phase 6에서 검증 완료. Phase 7에서는 회귀 테스트만 수행.
- **D-05:** 정확 문자열 매칭 -- PHP 원본과 byte-level 동일한 로그 문자열 생성. color tag 포함 완전 일치.
- **D-06:** 로그 + entity mutation 동시 검증. CommandResult.logs 정확 매칭 + run() 전후 entity 상태 diff를 PHP 예상값과 비교.
- **D-07:** Kotlin-only 커맨드 8개는 parity 대상 아님. 기본 동작 테스트만 추가.

### Claude's Discretion
- Constraint 검증 접근법: 커맨드별 통합 vs 별도 감사 결정
- Plan 분할 전략: 카테고리별 2~3개 Plan의 구체적 경계
- PHP 코드 읽기 순서 및 카탈로그 정리 형식
- golden value 추출 시 PHP 코드 수동 추적 세부 방식
- Kotlin-only 커맨드 기본 동작 테스트의 범위와 깊이

### Deferred Ideas (OUT OF SCOPE)
None
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| CMD-01 | 55개 장수 커맨드가 레거시 PHP와 동일한 결과 생성 검증 | 커맨드 카탈로그 완성, 기존 테스트 파일 구조 파악, golden value 패턴 확립 |
| CMD-02 | 38개 국가 커맨드가 레거시 PHP와 동일한 결과 생성 검증 | 국가 커맨드 카탈로그(수동 33 + 이벤트 10), 테스트 파일 4개 구조 파악 |
| CMD-03 | 커맨드 constraint check가 레거시와 동일하게 accept/reject 검증 | ConstraintHelper 30+ 제약, ConstraintChain 패턴, 기존 테스트에 constraint 검증 이미 포함 |
| CMD-04 | 커맨드 side effect(entity mutation, log message)가 레거시와 일치 검증 | CommandResult 구조, log tag 시스템, JosaUtil 조사 처리, color tag 포맷 파악 |
</phase_requirements>

## Command Catalog

### 장수 커맨드 (General Commands) - 55개 실제 커맨드 + 5개 인프라

#### 내정 (Civil/Domestic) - 18개
| # | 커맨드 | PHP 파일 | Kotlin 파일 | 라인수(PHP/KT) | 복잡도 | Phase 6 검증 |
|---|--------|----------|-------------|----------------|--------|-------------|
| 1 | 농지개간 | che_농지개간.php (9행, DomesticCommand 상속) | che_농지개간.kt | 9/~ | LOW | 검증완료 |
| 2 | 상업투자 | che_상업투자.php (227행) | che_상업투자.kt | 227/~ | LOW | 검증완료 |
| 3 | 치안강화 | - | che_치안강화.kt | ~/~ | LOW | 검증완료 |
| 4 | 수비강화 | - | che_수비강화.kt | ~/~ | LOW | 검증완료 |
| 5 | 성벽보수 | - | che_성벽보수.kt | ~/~ | LOW | 검증완료 |
| 6 | 정착장려 | che_정착장려.php (199) | che_정착장려.kt | 199/113 | MEDIUM | 미검증 |
| 7 | 주민선정 | che_주민선정.php (199) | che_주민선정.kt | 199/114 | MEDIUM | 미검증 |
| 8 | 기술연구 | che_기술연구.php | che_기술연구.kt | ~/116 | MEDIUM | 미검증 |
| 9 | 모병 | che_모병.php | che_모병.kt | ~/~ | MEDIUM | 미검증 |
| 10 | 징병 | che_징병.php (337) | che_징병.kt (193) | 337/193 | HIGH | 부분검증 |
| 11 | 훈련 | - | che_훈련.kt | ~/~ | LOW | 부분검증 |
| 12 | 사기진작 | - | che_사기진작.kt | ~/~ | LOW | 부분검증 |
| 13 | 소집해제 | che_소집해제.php | che_소집해제.kt | ~/~ | LOW | 미검증 |
| 14 | 숙련전환 | che_숙련전환.php (217) | che_숙련전환.kt | 217/~ | MEDIUM | 미검증 |
| 15 | 물자조달 | - | che_물자조달.kt (113) | ~/113 | MEDIUM | 미검증 |
| 16 | 군량매매 | che_군량매매.php | che_군량매매.kt (122) | ~/122 | MEDIUM | 검증완료 |
| 17 | 헌납 | che_헌납.php | che_헌납.kt | ~/~ | LOW | 검증완료 |
| 18 | 단련 | che_단련.php | che_단련.kt (96) | ~/96 | MEDIUM | 부분검증 |

#### 군사 (Military) - 15개 + Kotlin-only 3개
| # | 커맨드 | PHP 파일 | Kotlin 파일 | 라인수(KT) | 복잡도 | Kotlin-only |
|---|--------|----------|-------------|------------|--------|-------------|
| 1 | 출병 | che_출병.php (274) | 출병.kt (276) | 276 | HIGH | |
| 2 | 이동 | che_이동.php | 이동.kt | ~ | MEDIUM | |
| 3 | 집합 | che_집합.php | 집합.kt | ~ | MEDIUM | |
| 4 | 귀환 | che_귀환.php | 귀환.kt | ~ | MEDIUM | |
| 5 | 접경귀환 | che_접경귀환.php | 접경귀환.kt (162) | 162 | HIGH | |
| 6 | 강행 | che_강행.php | 강행.kt | ~ | MEDIUM | |
| 7 | 거병 | che_거병.php | 거병.kt | ~ | MEDIUM | |
| 8 | 전투태세 | che_전투태세.php | 전투태세.kt | ~ | LOW | |
| 9 | 화계 | che_화계.php (352) | 화계.kt (352) | 352 | HIGH | |
| 10 | 첩보 | che_첩보.php (232) | 첩보.kt (188) | 188 | HIGH | |
| 11 | 선동 | che_선동.php (61) | 선동.kt | ~ | MEDIUM | |
| 12 | 탈취 | che_탈취.php | 탈취.kt (106) | 106 | MEDIUM | |
| 13 | 파괴 | che_파괴.php | 파괴.kt | ~ | MEDIUM | |
| 14 | 요양 | che_요양.php | 요양.kt | ~ | LOW | |
| 15 | 방랑 | che_방랑.php | 방랑.kt | ~ | MEDIUM | |
| K1 | 요격 | 없음 | 요격.kt (72) | 72 | LOW | YES |
| K2 | 순찰 | 없음 | 순찰.kt (55) | 55 | LOW | YES |
| K3 | 좌표이동 | 없음 | 좌표이동.kt (35) | 35 | LOW | YES |

#### 정치 (Political) - 19개
| # | 커맨드 | PHP 파일 | 라인수(PHP) | 복잡도 |
|---|--------|----------|-------------|--------|
| 1 | 등용 | che_등용.php (204) | 204 | HIGH |
| 2 | 등용수락 | che_등용수락.php (217) | 217 | HIGH |
| 3 | 임관 | che_임관.php (224) | 224 | HIGH |
| 4 | 랜덤임관 | che_랜덤임관.php (289) | 289 | HIGH |
| 5 | 장수대상임관 | che_장수대상임관.php (205) | 205 | HIGH |
| 6 | 하야 | che_하야.php | ~ | MEDIUM |
| 7 | 은퇴 | che_은퇴.php | ~ | MEDIUM |
| 8 | 건국 | che_건국.php (237) | 237 | HIGH |
| 9 | 무작위건국 | che_무작위건국.php (248) | 248 | HIGH |
| 10 | 모반시도 | che_모반시도.php (108) | 108 | HIGH |
| 11 | 선양 | che_선양.php | ~ | MEDIUM |
| 12 | 해산 | che_해산.php | ~ | MEDIUM |
| 13 | 견문 | che_견문.php | 171(KT) | MEDIUM |
| 14 | 인재탐색 | che_인재탐색.php (227) | 227 | HIGH |
| 15 | 증여 | che_증여.php (201) | 201 | MEDIUM |
| 16 | 장비매매 | che_장비매매.php (260) | 260 | HIGH |
| 17 | 내정특기초기화 | che_내정특기초기화.php | ~ | LOW |
| 18 | 전투특기초기화 | che_전투특기초기화.php | ~ | LOW |
| 19 | 휴식 | 휴식.php | ~ | LOW |

#### NPC/CR Special - 3개 (인프라 커맨드, 별도 검증)
| 커맨드 | 비고 |
|--------|------|
| NPC능동 | NPC AI 전용, Phase 8 범위 |
| CR건국 | 시나리오 건국 전용 |
| CR맹훈련 | 시나리오 특수 훈련 |

### 국가 커맨드 (Nation Commands) - 38개

#### 자원/관리 (Resource/Management) - 10개
| # | 커맨드 | PHP 라인수 | 복잡도 | Phase 6 검증 |
|---|--------|-----------|--------|-------------|
| 1 | 포상 | 203 | MEDIUM | 검증완료 |
| 2 | 몰수 | 241 | MEDIUM | 검증완료 |
| 3 | 감축 | 210 | MEDIUM | 검증완료 |
| 4 | 증축 | 201 | MEDIUM | 검증완료 |
| 5 | 발령 | 192 | MEDIUM | 미검증 |
| 6 | 천도 | 246 | HIGH | 미검증 |
| 7 | 백성동원 | 183 | MEDIUM | 미검증 |
| 8 | 물자원조 | 309 | HIGH | 검증완료 |
| 9 | 국기변경 | 154 | LOW | 미검증 |
| 10 | 국호변경 | 163 | LOW | 미검증 |

#### 외교 (Diplomacy) - 7개
| # | 커맨드 | PHP 라인수 | 복잡도 |
|---|--------|-----------|--------|
| 1 | 선전포고 | 232 | HIGH |
| 2 | 종전제의 | 205 | MEDIUM |
| 3 | 종전수락 | 197 | MEDIUM |
| 4 | 불가침제의 | 272 | HIGH |
| 5 | 불가침수락 | 234 | MEDIUM |
| 6 | 불가침파기제의 | 219 | MEDIUM |
| 7 | 불가침파기수락 | 183 | MEDIUM |

#### 전략 (Strategic) - 8개
| # | 커맨드 | PHP 라인수 | 복잡도 |
|---|--------|-----------|--------|
| 1 | 급습 | 236 | HIGH |
| 2 | 수몰 | 210 | MEDIUM |
| 3 | 허보 | 213 | MEDIUM |
| 4 | 초토화 | 221 | MEDIUM |
| 5 | 필사즉생 | 129 | MEDIUM |
| 6 | 이호경식 | 234 | HIGH |
| 7 | 피장파장 | 321 | HIGH |
| 8 | 의병모집 | 178 | MEDIUM |

#### 연구 (Research/Event) - 9개
| # | 커맨드 | PHP 라인수 | 복잡도 |
|---|--------|-----------|--------|
| 1-9 | event_극병/대검병/무희/산저병/상병/원융노병/음귀병/화륜차/화시병 연구 | 각 ~106 | LOW (템플릿 동일) |

#### 황제/봉신 (Emperor/Vassal) - 5개 (Kotlin-only)
| # | 커맨드 | Kotlin 라인수 | Kotlin-only |
|---|--------|--------------|-------------|
| 1 | 칭제 | 53 | YES |
| 2 | 천자맞이 | 65 | YES |
| 3 | 선양요구 | 63 | YES |
| 4 | 신속 | 65 | YES |
| 5 | 독립선언 | 68 | YES |

#### 특수 (Special) - 3개
| # | 커맨드 | PHP 라인수 | 복잡도 |
|---|--------|-----------|--------|
| 1 | 무작위수도이전 | 167 | MEDIUM |
| 2 | 부대탈퇴지시 | 158 | MEDIUM |
| 3 | cr_인구이동 | 197 | MEDIUM |
| 4 | Nation휴식 | 39 | LOW |

## Architecture Patterns

### 커맨드 실행 파이프라인
```
CommandExecutor.executeGeneralCommand()
  1. ArgSchema 검증 (parse + toLegacyMap)
  2. CommandRegistry.createGeneralCommand() 팩토리 호출
  3. hydrateCommandForConstraintCheck() — dest entity, modifiers 주입
  4. checkGeneralCooldown() — postReqTurn 기반 쿨다운 확인
  5. checkFullCondition() — constraint chain 순차 실행
     - 실패 시 getAlternativeCommand() 확인 후 대체 실행 또는 실패 반환
  6. preReqTurn 처리 — 멀티턴 스태킹 (addTermStack)
  7. command.run(rng) 실행
  8. CommandResultApplicator.apply() — JSON delta를 entity에 적용
  9. statChangeService.checkStatChange() — 스탯 레벨업 체크
  10. saveModifiedEntities() — JPA 저장
  11. applyGeneralCooldown() — postReqTurn 쿨다운 기록
```

### 테스트 패턴 (확립됨, Phase 4/5/6에서 검증)

#### Golden Value 패턴 (CommandParityTest 참조)
```kotlin
@Test
fun `커맨드 parity and determinism`() {
    val general = createGeneral(leadership = 80, crew = 200, ...)
    val city = createCity(nationId = 1, ...)
    val env = createEnv()

    // 1. 고정 seed RNG로 결정론적 실행
    val result = runBlocking { cmd.run(LiteHashDRBG.build("seed")) }

    // 2. success 확인
    assertTrue(result.success)

    // 3. entity mutation golden value 비교
    val json = mapper.readTree(result.message)
    assertEquals(EXPECTED_GOLD_DELTA, json["statChanges"]["gold"].asInt())
    assertEquals(EXPECTED_AGRI_DELTA, json["cityChanges"]["agri"].asInt())

    // 4. 로그 정확 문자열 매칭
    assertEquals("기대하는 로그 문자열", result.logs[0])

    // 5. 결정론 검증 (같은 seed -> 같은 결과)
    val second = runBlocking { cmd.run(LiteHashDRBG.build("seed")) }
    assertEquals(result.message, second.message)
}
```

#### Constraint 검증 패턴
```kotlin
@Test
fun `커맨드 constraint 실패 조건`() {
    val cmd = create_command(...)
    val cond = cmd.checkFullCondition()
    assertTrue(cond is ConstraintResult.Fail)
    assertTrue((cond as ConstraintResult.Fail).reason.contains("기대 메시지"))
}
```

### CommandResult 구조
```kotlin
data class CommandResult(
    val success: Boolean,
    val logs: List<String> = emptyList(),  // 로그 문자열 리스트
    val message: String? = null            // JSON delta (entity mutation)
)
```

- `logs`: 한글 + color tag(`<R>`, `<G>`, `<C>`, `<Y>`, `<D>`, `<b>`, `</>`, `<1>`) 포함 문자열
- `message`: JSON 형식의 entity 변경 delta (`statChanges`, `cityChanges`, `nationChanges`, `destCityChanges` 등)
- 로그 태그 접두어: `_history:`, `_global:`, `_globalAction:`, `_globalHistory:`, `_nationalHistory:`, `_destNationalHistory:ID:`, `_destGeneralLog:ID:`, `_destGeneralHistory:ID:`, `_broadcast:nationId:excludeId:`

### 한글 조사 처리 (JosaUtil)
```kotlin
JosaUtil.pick("장수", "이")  // "장수가" (받침 없음 -> "가")
JosaUtil.pick("관우", "을")  // "관우를" (받침 없음 -> "를")
JosaUtil.pick("조운", "은")  // "조운은" (받침 있음 -> "은")
```
- PHP의 `josaFormat`과 동일한 로직: 종성(받침) 유무로 조사 결정
- "로/으로" 특수 케이스: ㄹ받침은 "로" 사용 (종성 코드 8)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PHP 기대값 계산 | 자동화 도구 | PHP 코드 수동 추적 | PHP 런타임 없음, 코드 리딩이 유일한 방법 |
| entity fixture 생성 | 새 팩토리 클래스 | 기존 createTestGeneral/createTestCity/createTestNation 헬퍼 | 모든 테스트 파일에 이미 존재 |
| RNG 결정론 | 새 RNG 구현 | LiteHashDRBG.build(seed) | Phase 1에서 확립된 크로스언어 결정론적 RNG |
| constraint 검증 | 별도 감사 도구 | cmd.checkFullCondition() 직접 호출 | 이미 모든 테스트에서 사용 중인 패턴 |

## Common Pitfalls

### Pitfall 1: DomesticCommand 상속 트리의 PHP 코드 위치
**What goes wrong:** 농지개간.php가 9줄인 이유는 DomesticCommand.php를 상속하기 때문. 실제 로직은 상위 클래스에 있다.
**Why it happens:** PHP의 DomesticCommand 기반 커맨드들(농지개간, 상업투자, 치안강화, 수비강화, 성벽보수)은 공통 로직을 상위 클래스에서 처리.
**How to avoid:** PHP에서 DomesticCommand.php, DomesticActionPHP 등의 상위 클래스를 반드시 함께 읽는다. Kotlin의 DomesticCommand.kt (203줄)과 DomesticUtils.kt (125줄)가 대응.
**Warning signs:** PHP 커맨드 파일이 20줄 미만이면 상속 구조 확인 필요.

### Pitfall 2: exchangeFee 의도적 차이
**What goes wrong:** PHP exchangeFee=0.01 vs Kotlin exchangeFee=0.03은 의도적 opensamguk 설정 차이 (Phase 6 D-06 결정).
**Why it happens:** 게임 밸런스 조정으로 의도적으로 다른 값 사용.
**How to avoid:** golden value 테스트에서 Kotlin의 기본값(0.03)을 기준으로 기대값을 산출한다.
**Warning signs:** 경제 관련 수치가 PHP와 다르다면 Phase 6 결정사항 재확인.

### Pitfall 3: 로그 문자열의 조사 분기
**What goes wrong:** "관우를 등용했습니다" vs "조운을 등용했습니다" -- 같은 커맨드라도 장수 이름에 따라 조사가 달라진다.
**Why it happens:** JosaUtil.pick()이 한글 종성에 따라 조사를 결정하므로 테스트 fixture의 이름 선택이 로그 문자열에 영향.
**How to avoid:** golden value 테스트에서 fixture 이름을 고정하고, 해당 이름에 맞는 조사를 정확히 기대값에 포함한다.
**Warning signs:** 로그 assertion이 "이/가", "을/를", "은/는" 부분에서 실패하면 fixture 이름의 받침 확인.

### Pitfall 4: CommandResultApplicator와 run() 결과의 이중성
**What goes wrong:** 일부 커맨드는 run()에서 entity를 직접 수정하고, 다른 커맨드는 message JSON으로 delta를 반환하여 CommandResultApplicator가 적용.
**Why it happens:** 장수 커맨드는 message JSON delta 방식, 국가 커맨드는 run()에서 직접 entity 수정 방식 혼재.
**How to avoid:** 장수 커맨드는 result.message JSON의 golden value 검증, 국가 커맨드는 run() 전후의 entity 상태 diff 검증으로 구분.
**Warning signs:** `executeNationCommand()`의 `// 국가 커맨드는 run()에서 직접 엔티티를 수정한다` 주석 참조.

### Pitfall 5: 복합 커맨드의 다중 entity mutation
**What goes wrong:** 출병, 등용, 건국 등은 General, City, Nation, Troop 등 여러 entity를 동시에 변경.
**Why it happens:** 복잡한 게임 로직으로 단일 커맨드가 다수 entity에 side effect 발생.
**How to avoid:** destGeneral, destCity, destNation, destCityGenerals 모두 검증 대상에 포함. saveModifiedEntities()가 저장하는 모든 entity 추적.
**Warning signs:** PHP 코드에서 `$destGeneral->`, `$destCity->`, `$destNation->` 참조가 있으면 다중 entity 검증 필요.

## High-Risk Commands (우선 검증 대상)

복잡도, 라인수, 조건 분기 수를 기준으로 가장 위험도 높은 커맨드:

### 장수 커맨드 HIGH-RISK
1. **화계** (352줄) -- 계략 성공률 계산, 방어장수 부상, destCity 피해, 모디파이어 적용
2. **출병** (276줄) -- 전투 트리거, 경로 탐색, 다중 entity 변경
3. **첩보** (188줄) -- 정찰 결과 생성, 비용 계산
4. **접경귀환** (162줄) -- BFS 경로 탐색, 보급 상태 확인
5. **랜덤임관** (213줄) -- 국가 선택 로직, affinity 기반 가중치
6. **장비매매** (139줄) -- 아이템 시스템 연동
7. **건국/무작위건국** (237/248줄 PHP) -- 국가 생성, 도시 점령, 다중 entity

### 국가 커맨드 HIGH-RISK
1. **피장파장** (321줄) -- 가장 복잡한 국가 전략 커맨드
2. **물자원조** (309줄 PHP) -- Phase 6 검증완료
3. **천도** (246줄) -- 수도 이전, 다중 도시 변경
4. **불가침제의** (272줄) -- 외교 상태 변경
5. **급습** (236줄) -- 기습 공격, 피해 계산
6. **이호경식** (234줄) -- 이간책

## Existing Test Coverage Analysis

### 기존 테스트의 검증 수준

| 테스트 파일 | 커맨드 수 | 검증 수준 | Phase 7 보강 필요 |
|------------|----------|----------|------------------|
| GeneralCivilCommandTest | 18 | constraint + message 키 존재 확인 | golden value 추가 |
| GeneralMilitaryCommandTest | 15 | constraint + message 키 존재 확인 | golden value 추가 |
| GeneralPoliticalCommandTest | 19 | constraint + message 키 존재 확인 | golden value 추가 |
| NationCommandTest | 5 | constraint + 기본 동작 | golden value + 누락 커맨드 추가 |
| NationDiplomacyStrategicCommandTest | 11 | constraint + 기본 동작 | golden value 추가 |
| NationResourceCommandTest | 10 | constraint + 기본 동작 | golden value 추가 |
| NationResearchSpecialCommandTest | 12 | constraint + 기본 동작 | golden value 추가 |
| CommandParityTest | 8 | golden value (훈련, 징병, 농지개간, 상업투자, 사기진작, 단련 + modifier) | Phase 6 수준 확장 |
| EconomyCommandParityTest | 12 | golden value + 정확 수치 매칭 | Phase 6 완료, 회귀 테스트만 |
| ConstraintTest | 30+ | 개별 constraint 단위 테스트 | 커맨드별 통합 constraint 확인 보강 |

### Phase 6 검증 완료 커맨드 (회귀 테스트만)
경제 관련 12개: 군량매매, 헌납, 농지개간, 상업투자, 치안강화, 수비강화, 성벽보수, 포상, 몰수, 물자원조, 증축, 감축

### Phase 7 비검증 대상 (Kotlin-only 8개)
장수: 순찰, 요격, 좌표이동
국가: 독립선언, 선양요구, 신속, 천자맞이, 칭제

## Plan 분할 권장 (Claude's Discretion)

카테고리별 순차 검증(D-01)을 준수하되, 작업량 균형을 위해 3개 Plan 권장:

### Plan 1: 내정 + 군사 장수 커맨드 (33개)
- 내정 18개 중 비경제 12개 golden value 추가 + 경제 6개 회귀 확인
- 군사 15개 golden value 추가 + Kotlin-only 3개 기본 동작 테스트
- 예상 작업량: 기존 GeneralCivilCommandTest + GeneralMilitaryCommandTest 보강

### Plan 2: 정치 장수 커맨드 (19개) + NPC/CR (3개)
- 정치 19개 golden value 추가 (HIGH-RISK 다수 포함)
- NPC능동, CR건국, CR맹훈련 기본 검증
- 예상 작업량: GeneralPoliticalCommandTest 보강 + NPC/CR 간단 검증

### Plan 3: 국가 커맨드 (38개)
- 자원/관리 10개 중 비경제 6개 golden value + 경제 4개 회귀
- 외교 7개 golden value 추가
- 전략 8개 golden value 추가
- 연구/이벤트 9개 golden value 추가
- Kotlin-only 5개 기본 동작 테스트
- 특수 3개 + Nation휴식 검증
- 예상 작업량: NationCommandTest + 나머지 3개 국가 테스트 파일 보강

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit Jupiter 5 (spring-boot-starter-test) |
| Config file | `backend/game-app/build.gradle.kts` |
| Quick run command | `cd backend && ./gradlew :game-app:test --tests "com.opensam.command.*" -x :gateway-app:test` |
| Full suite command | `cd backend && ./gradlew :game-app:test -x :gateway-app:test` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CMD-01 | 55 장수 커맨드 golden value parity | unit | `./gradlew :game-app:test --tests "com.opensam.command.General*" --tests "com.opensam.command.CommandParityTest"` | 기존 파일 보강 |
| CMD-02 | 38 국가 커맨드 golden value parity | unit | `./gradlew :game-app:test --tests "com.opensam.command.Nation*"` | 기존 파일 보강 |
| CMD-03 | Constraint accept/reject 매칭 | unit | `./gradlew :game-app:test --tests "com.opensam.command.Constraint*"` | 기존 + 통합 검증 |
| CMD-04 | Log message + entity mutation 매칭 | unit | `./gradlew :game-app:test --tests "com.opensam.command.*"` | 기존 파일 보강 |

### Sampling Rate
- **Per task commit:** `cd backend && ./gradlew :game-app:test --tests "com.opensam.command.*" -x :gateway-app:test`
- **Per wave merge:** `cd backend && ./gradlew :game-app:test -x :gateway-app:test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
None -- 기존 테스트 인프라가 Phase 7 요구사항을 모두 커버. 새 테스트 파일 생성 불필요, 기존 파일에 golden value assertion 추가만 필요.

## Sources

### Primary (HIGH confidence)
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandRegistry.kt` -- 93개 커맨드 등록 전체 목록
- `backend/game-app/src/main/kotlin/com/opensam/command/BaseCommand.kt` -- 커맨드 인터페이스, 로그 헬퍼, 조사 처리
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandExecutor.kt` -- 실행 파이프라인 전체 흐름
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandResult.kt` -- 결과 구조체
- `backend/game-app/src/main/kotlin/com/opensam/util/JosaUtil.kt` -- 한글 조사 유틸
- `backend/game-app/src/test/kotlin/com/opensam/command/CommandParityTest.kt` -- golden value 패턴 레퍼런스
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/EconomyCommandParityTest.kt` -- Phase 6 golden value 패턴
- `legacy-core/hwe/sammo/Command/General/` -- 55개 PHP 원본 (라인수 분석 완료)
- `legacy-core/hwe/sammo/Command/Nation/` -- 38개 PHP 원본 (라인수 분석 완료)

### Secondary (MEDIUM confidence)
- `backend/game-app/src/main/kotlin/com/opensam/command/constraint/ConstraintHelper.kt` -- 30+ constraint 구현
- 기존 7개 카테고리별 테스트 파일 -- 현재 검증 수준 분석

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- 기존 인프라 활용, 새 라이브러리 불필요
- Architecture: HIGH -- CommandExecutor 파이프라인, golden value 패턴 모두 확립
- Pitfalls: HIGH -- Phase 4/5/6 경험에서 도출, 코드 직접 확인

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (안정적 -- 내부 코드베이스 기반)
