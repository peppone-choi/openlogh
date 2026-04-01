# Phase 6: Economy Parity - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

모든 경제 공식(세금, 무역, 보급, 인구, 도시 개발, 봉록)이 레거시 PHP와 동일한 값을 산출하는지 검증하고, 불일치 발견 시 즉시 수정. 경제 관련 커맨드(무역, 징수 등)도 이 Phase에서 완전히 검증하여 Phase 7에서는 비경제 커맨드만 다룸.

Requirements: ECON-01, ECON-02, ECON-03, ECON-04, ECON-05, ECON-06

</domain>

<decisions>
## Implementation Decisions

### 검증 방법론 (Verification Methodology)
- **D-01:** 병행 접근 — 공식별 golden value 단위 테스트 + 24턴(2년) 통합 시뮬레이션 테스트 모두 수행.
  - 단위: 각 경제 공식(calcCityGoldIncome, calcCityRiceIncome, processSemiAnnual 등)에 고정 입력 → PHP 수동 계산 기대값 하드코딩
  - 통합: 24턴 시뮬레이션으로 반기 처리 4회(1월/7월 × 2년) + 장기 인구/자원 누적 변동 검증
  - Phase 4/5 golden value 패턴과 동일한 접근법

### 레거시 소스 접근 (Legacy Source Access)
- **D-02:** legacy-core/ 디렉토리를 devsam/core에서 다시 clone하여 복원. PHP 원본 코드를 직접 읽어 golden value 추출. 기존 Kotlin 코드의 주석/공식만으로는 불충분.

### 불일치 발견 시 대응 (Mismatch Handling)
- **D-03:** Phase 5 D-04와 동일 — 감사 중 불일치 발견 시 즉시 수정하고 테스트 추가. 카탈로그 후 일괄 수정 아님.

### 검증 범위 (Verification Scope)
- **D-04:** EconomyService 6개 공식 + 재난/호황/국력 계산 + 경제 관련 커맨드(che_무역, che_징수 등) 전체 동작 검증.
  - 경제 관련 커맨드는 이 Phase에서 constraint, side-effect 포함 완전 검증
  - Phase 7(Command Parity)에서는 비경제 커맨드만 다룸
  - 턴 파이프라인 경제 스텝(EconomyPreUpdateStep, EconomyPostUpdateStep, DisasterAndTradeStep, YearlyStatisticsStep) 실행 순서/조건도 검증

### Claude's Discretion
- golden value 추출 시 PHP 코드 수동 추적 세부 방식
- 단위 테스트 클래스 분할 전략 (공식별 vs 기능 그룹별)
- 통합 테스트 시나리오 설계 (도시 수, 국가 수, 초기 조건)
- 경제 관련 커맨드 식별 및 우선순위 결정

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Legacy PHP Source (parity target)
- `legacy-core/hwe/func.php` — 경제 관련 함수 (processIncome, processSemiAnnual, popIncrease 등, 80KB)
- `legacy-core/hwe/sammo/Command/General/` — 경제 관련 장수 커맨드 (무역, 징수, 세율조정 등)
- `legacy-core/hwe/sammo/Command/Nation/` — 경제 관련 국가 커맨드 (세율, 국고 등)
- `legacy-core/src/daemon.ts` — 턴 데몬 경제 스텝 실행 순서 (441줄)

### Existing Kotlin Implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/EconomyService.kt` — 핵심 경제 로직 (878줄): processIncome, processSemiAnnual, updateCitySupply, updateNationLevel, processDisasterOrBoom, randomizeCityTradeRate, processYearlyStatistics
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/steps/EconomyPreUpdateStep.kt` — Step 300: preUpdateMonthly (현재 shouldSkip=true)
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/steps/EconomyPostUpdateStep.kt` — Step 1000: postUpdateMonthly
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/steps/DisasterAndTradeStep.kt` — Step 1100: 재난/호황 + 무역률
- `backend/game-app/src/main/kotlin/com/opensam/engine/turn/steps/YearlyStatisticsStep.kt` — 연초 통계
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/NationTypeModifiers.kt` — 국가타입별 경제 보너스 (IncomeContext)

### Existing Tests
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/EconomyFormulaParityTest.kt` — 기존 경제 공식 parity 테스트
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/EconomyEventParityTest.kt` — 경제 이벤트 parity 테스트
- `backend/game-app/src/test/kotlin/com/opensam/engine/EconomyServiceTest.kt` — EconomyService 단위 테스트

### Phase 2 Context (선행 결정)
- `.planning/phases/02-numeric-type-safety/02-CONTEXT.md` — 200턴 golden snapshot 패턴, rounding 규칙
- `.planning/phases/05-modifier-pipeline/05-CONTEXT.md` — PHP 전수 읽기 방식(D-01), 발견 즉시 수정(D-04)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `EconomyService`: 7개 주요 메서드 이미 구현됨 (processIncome, processSemiAnnual, updateCitySupply, updateNationLevel, processDisasterOrBoom, randomizeCityTradeRate, processYearlyStatistics)
- `EconomyFormulaParityTest`: 기존 경제 공식 golden value 테스트 프레임워크
- `InMemoryTurnHarness`: 턴 파이프라인 통합 테스트 하네스
- `DeterministicRng`: 재난/호황/국력 계산의 결정론적 RNG
- `NationTypeModifiers + IncomeContext`: 국가타입별 경제 보너스 시스템

### Established Patterns
- Golden value 테스트: 고정 입력 → PHP 수동 계산 기대값 하드코딩 비교 (Phase 4/5 확립)
- `WorldPortFactory` → `allCities/allNations/allGenerals` → entity 변환 → 계산 → save 패턴
- `IncomeContext`: goldMultiplier, riceMultiplier, popGrowthMultiplier 필드
- 반기 처리: month == 1 || 7 조건으로 processSemiAnnual 호출

### Integration Points
- `TurnService.processWorld()`: preUpdateMonthly 직접 호출 (EconomyPreUpdateStep은 shouldSkip=true)
- `EconomyPostUpdateStep.execute()`: postUpdateMonthly 호출 (Step 1000)
- `DisasterAndTradeStep.execute()`: processDisasterOrBoom + randomizeCityTradeRate (Step 1100)
- 경제 관련 커맨드: CommandRegistry에 등록된 che_무역, che_징수 등

</code_context>

<specifics>
## Specific Ideas

- EconomyService.calcCityGoldIncome: pop * comm/commMax * trustRatio / 30 * secuBonus * officerBonus * capitalBonus * nationTypeMod
- EconomyService.calcCityRiceIncome: pop * agri/agriMax * trustRatio / 30 * secuBonus * officerBonus * capitalBonus * nationTypeMod
- EconomyService.calcCityWallIncome: def * wall/wallMax / 3 * secuBonus * officerBonus * capitalBonus * nationTypeMod
- processSemiAnnual: 0.99 decay → popIncrease(popRatio) → infrastructure growth(genericRatio) → trust 조정 → 장수/국가 자원 decay
- getBill: ceil(sqrt(dedication)/10) * 200 + 400 — 봉록 계산 공식
- 통합 시뮬레이션: 24턴으로 반기 처리 4회 포함, 다턴 누적 드리프트 감지

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 06-economy-parity*
*Context gathered: 2026-04-01*
