# Phase 10: Diplomacy and Scenario Data - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Verify diplomacy state transitions, timers, and game-end conditions match legacy PHP. Validate all 80+ scenario JSON files' initial data (NPC stats, city conditions, nation relations) against legacy source.

</domain>

<decisions>
## Implementation Decisions

### 외교 상태 검증
- **D-01:** 레거시 PHP에서 외교 상태 머신(선전포고, 동맹, 휴전) 추출 후 golden value 테스트로 검증 — Phase 9 DisasterParityTest 패턴 재사용
- **D-02:** 모든 타이머 값 검증 — 휴전 쿨다운, 동맹 최소 기간, 선전포고 제한 등 모든 디플로매시 타이머를 PHP에서 추출하여 검증
- **D-03:** DiplomacyParityTest.kt 새 파일 생성 — qa/parity/ 폴더에 외교 전용 패러티 테스트

### 통일/게임종료
- **D-04:** 전체 게임종료 조건 매핑 + 테스트 — 통일 외에도 방랑자 타임아웃, 기한 만료 등 모든 게임종료 조건을 PHP에서 추출하여 검증
- **D-05:** GameEndParityTest.kt 별도 파일 생성 — 통일/게임종료는 독립적 관심사이므로 별도 파일

### 시나리오 데이터 검증
- **D-06:** 레거시 PHP 시나리오 파일과 JSON diff 자동화 — legacy-core/hwe/scenario/ 파일들과 현재 scenarios/ JSON을 파싱하여 3-stat(leadership, strength, intel) 값을 자동 비교하는 테스트
- **D-07:** ScenarioDataParityTest.kt 새 파일 생성 — qa/parity/ 폴더에 시나리오 데이터 전용 패러티 테스트

### 플랜 분할
- **D-08:** 3플랜 분할 — Plan 1: 외교 상태 전환/타이머(DIPL-01,02), Plan 2: 게임종료 조건(DIPL-03), Plan 3: 시나리오 데이터 검증(DATA-01~03)
- **D-09:** Wave 1: 외교+게임종료 병렬, Wave 2: 시나리오 — 외교와 게임종료는 독립적이라 Wave 1에서 병렬 실행, 시나리오 데이터는 Wave 2로 순차 실행

### Claude's Discretion
- 도시/국가 초기 조건 검증의 자동화 범위 (장수 스탯은 자동화 필수, 나머지는 Claude 판단)
- 시나리오 데이터 diff 도구 구현 세부 사항
- 외교 상태 머신 추출 시 어떤 PHP 파일을 읽을지

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 외교 로직
- `legacy-core/hwe/sammo/Command/Nation/che_선전포고.php` — 선전포고 커맨드 (외교 상태 전환 트리거)
- `backend/game-app/src/main/kotlin/com/opensam/entity/Diplomacy.kt` — 외교 엔티티 (stateCode, term, meta)
- `backend/game-app/src/main/kotlin/com/opensam/repository/DiplomacyRepository.kt` — 외교 리포지토리
- `backend/game-app/src/main/kotlin/com/opensam/engine/ai/DiplomacyState.kt` — 외교 상태 코드
- `backend/game-app/src/main/kotlin/com/opensam/service/DiplomacyLetterService.kt` — 외교 서신 서비스

### 게임종료/통일
- `backend/game-app/src/main/kotlin/com/opensam/engine/TurnService.kt` — UnificationCheck 턴 스텝 (1600)
- `legacy-core/hwe/func_gamerule.php` — checkEmperior 등 게임종료 로직

### 시나리오 데이터
- `backend/shared/src/main/resources/data/scenarios/` — 84개 시나리오 JSON 파일
- `legacy-core/hwe/scenario/` — 레거시 시나리오 PHP 파일
- `backend/game-app/src/main/kotlin/com/opensam/service/ScenarioService.kt` — 시나리오 로딩 서비스
- `backend/game-app/src/main/kotlin/com/opensam/model/ScenarioData.kt` — 시나리오 데이터 모델

### 기존 패러티 테스트 패턴
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/DisasterParityTest.kt` — golden value 테스트 패턴 참조
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/TurnPipelineParityTest.kt` — 턴 파이프라인 패러티 패턴

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Diplomacy.kt` — stateCode, term, meta 필드로 외교 상태 추적 (이미 구현)
- `DiplomacyRepository.kt` — 외교 CRUD (이미 구현)
- `DiplomacyState.kt` — 외교 상태 코드 enum/constants (이미 구현)
- `ScenarioService.kt` — 시나리오 JSON 파싱 로직 (이미 구현)
- `ScenarioData.kt` — 시나리오 데이터 모델 (이미 구현)

### Established Patterns
- qa/parity/ 폴더의 golden value 테스트 패턴 (DisasterParityTest, EconomyFormulaParityTest 등)
- 레거시 PHP 코드를 수동 트레이싱하여 golden value 추출 (D-06 from Phase 9)

### Integration Points
- UnificationCheck 턴 스텝 (1600) — 게임종료 로직이 여기서 실행
- 시나리오 JSON 파일 → ScenarioService → 게임 초기화

</code_context>

<specifics>
## Specific Ideas

- Phase 9의 DisasterParityTest 패턴을 참조하여 golden value 테스트 구조 유지
- 84개 시나리오 전체에 대한 자동화 diff 테스트 (샘플링이 아닌 전수 검사)
- 3-stat(leadership, strength, intel) 값만 검증 — politics, charm은 opensamguk 확장이므로 제외

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 10-diplomacy-and-scenario-data*
*Context gathered: 2026-04-02*
