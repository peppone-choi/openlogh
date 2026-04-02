# Phase 7: Command Parity - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

93개 등록 커맨드(55 장수 + 38 국가)가 레거시 PHP와 동일한 entity mutation, log message, resource change를 생성하는지 검증하고, 불일치 발견 시 즉시 수정. 경제 관련 커맨드는 Phase 6에서 완료되었으므로 비경제 커맨드만 대상.

Requirements: CMD-01, CMD-02, CMD-03, CMD-04

</domain>

<decisions>
## Implementation Decisions

### 검증 범위 및 배치 (Verification Scope & Batching)
- **D-01:** 카테고리별 순차 검증 — 내정(18) → 군사(18) → 정��(19) → ���가(38) 순서로 Plan 2~3개로 분할. 기존 테스트 파일 구조(GeneralCivilCommandTest, GeneralMilitaryCommandTest, GeneralPoliticalCommandTest, NationCommandTest 등)와 일치.
- **D-02:** 기존 테스트 파일에 golden value assertion 추가하여 확장. PHP 원본에서 기대값 추출 후 기존 테스트 구조를 보강.
- **D-03:** 경제 관련 커맨드(무역, 징수 등)는 Phase 6에서 검증 완료. Phase 7에서는 회귀 테스트(기존 테스트 통과 확인)만 수행하고 재검증하지 않음.

### Constraint Parity Strategy
- **D-04:** Claude 재량. 커맨드별 상황에 따라 constraint를 커맨드 run()과 함께 통합 검증할지, 별도 감사할지 판단.

### 로그 메시지 및 Side Effect 매칭 (Log & Side Effect Matching)
- **D-05:** 정확 문자열 매칭 — PHP 원본과 byte-level 동일한 로그 문자열 생성. color tag(`<R>`, `<C>` 등) 포함 완전 일치.
- **D-06:** 로그 + entity mutation 동시 검증 (가장 엄격한 수준). CommandResult.logs 정확 매칭 + run() 전후 entity 상태 차이(diff)를 PHP 예상값과 비교.

### Kotlin-only 커맨드 처리
- **D-07:** 레거시에 없는 Kotlin-only 커맨드 8개(장수: 순찰, 요격, 좌표이동 / 국가: 독립선언, 선양요구, 신속, 천자맞이, 칭제)는 parity 대상이 아님. 기본 동작 테스트(constraint 통과, entity mutation 정상)만 추가.

### Claude's Discretion
- Constraint 검증 접근법: 커맨드별 통합 vs 별도 감사 결정
- Plan 분할 전략: 카테고리별 2~3개 Plan의 구체적 경계
- PHP 코드 읽기 순서 및 카탈로그 정리 형식
- golden value 추출 시 PHP 코드 수동 추적 세부 방식
- Kotlin-only 커맨드 기본 동작 테스트의 범위와 깊이

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Legacy PHP Source (parity target)
- `legacy-core/hwe/sammo/Command/General/` — 55개 장수 커맨드 PHP 원본 (내정 18 + 군사 15 + 정치 19 + 기본 3)
- `legacy-core/hwe/sammo/Command/Nation/` — 38개 국가 커맨드 PHP 원본
- `legacy-core/hwe/func.php` — 공통 게임 함수, 커맨드에서 호출하는 헬퍼 (80KB)

### Existing Kotlin Command Infrastructure
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandRegistry.kt` — 93개 커맨드 등록, 카테고리별 구분 주석
- `backend/game-app/src/main/kotlin/com/opensam/command/BaseCommand.kt` — 커맨드 인터페이스: run(), getCost(), constraints, modifiers
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandExecutor.kt` — 커맨드 실행 오케스트레이터: constraint check, cooldown, modifier injection
- `backend/game-app/src/main/kotlin/com/opensam/command/constraint/` — Constraint.kt, ConstraintChain.kt, ConstraintHelper.kt
- `backend/game-app/src/main/kotlin/com/opensam/command/general/` — 60개 장수 커맨드 Kotlin 구현
- `backend/game-app/src/main/kotlin/com/opensam/command/nation/` — 43개 국가 커맨드 Kotlin 구현

### Existing Tests
- `backend/game-app/src/test/kotlin/com/opensam/command/GeneralCivilCommandTest.kt` — 내정 커맨드 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/GeneralMilitaryCommandTest.kt` — 군사 커맨드 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/GeneralPoliticalCommandTest.kt` — 정치 커맨드 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/NationCommandTest.kt` — 국가 커맨드 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/NationDiplomacyStrategicCommandTest.kt` — 외교/전략 국가 커맨드 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/NationResourceCommandTest.kt` — 자원 관련 국가 커맨드 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/NationResearchSpecialCommandTest.kt` — 연구/특수 국가 커맨드 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/CommandParityTest.kt` — 커맨드 parity 테스트 프레임워크
- `backend/game-app/src/test/kotlin/com/opensam/command/ConstraintTest.kt` — constraint 단위 테스트
- `backend/game-app/src/test/kotlin/com/opensam/command/ConstraintChainTest.kt` — constraint chain 테스트
- `backend/game-app/src/test/kotlin/com/opensam/qa/parity/EconomyCommandParityTest.kt` — Phase 6 경제 커맨드 parity 테스트

### Prior Phase Context
- `.planning/phases/05-modifier-pipeline/05-CONTEXT.md` — PHP 전수 읽기 방식(D-01), golden value 테스트 패턴(D-02), 발견 즉시 수정(D-04)
- `.planning/phases/06-economy-parity/06-CONTEXT.md` — 경제 커맨드 검증 완료(D-04), 비경제 커맨드만 Phase 7 범위

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CommandRegistry`: 93개 커맨드를 카테고리별(Civil 18, Military 18, Political 19, Nation 38)로 등록. 검증 배치 기준으로 직접 활용 가능
- `BaseCommand`: run(), getCost(), getPreReqTurn(), getPostReqTurn(), fullConditionConstraints, minConditionConstraints 인터페이스 표준화
- `CommandExecutor`: hydrateCommandForConstraintCheck()에서 city/nation/troop/modifiers 자동 주입
- `ConstraintChain`: Constraint 목록을 순차 실행하여 Pass/Fail(reason) 반환
- 기존 테스트 파일: 카테고리별 테스트 클래스 7개 + parity 테스트 2개 + constraint 테스트 2개

### Established Patterns
- Golden value 테스트: 고정 입력 → PHP 수동 계산 기대값 하드코딩 비교 (Phase 4/5/6 확립)
- CommandResult: success/failure + logs(한글 + color tag) + entity mutations
- Constraint: fullConditionConstraints(전체 조건) + minConditionConstraints(최소 조건) 이중 체크
- ArgSchema: 커맨드 인자 검증 및 type coercion

### Integration Points
- `CommandExecutor.execute()` — constraint check → cost deduction → run() → result application
- `CommandResultApplicator` — CommandResult의 entity mutation을 DB에 적용
- `ModifierService.getModifiers()` → `command.modifiers` 주입 → run() 내에서 사용
- `CommandServices` — 커맨드가 사용하는 서비스 묶음 (GeneralService, CityService, NationService 등)

</code_context>

<specifics>
## Specific Ideas

- 장수 커맨드 60개 중 DomesticCommand, DomesticUtils, NPC능동, CR건국, CR맹훈련은 인프라/유틸리티이므로 검증 대상에서 제외 → 실제 커맨드는 55개
- 국가 커맨드 43개 중 Nation휴식은 레거시 휴식.php에 매핑, cr_인구이동 및 event_* 9개는 자동 이벤트이므로 별도 처리 → 실제 수동 커맨드는 33개 + 이벤트 10개
- 정확 문자열 매칭은 PHP `sprintf` / `josaFormat` 등의 한글 조사 처리까지 포함해야 함
- entity mutation 검증 시 General, City, Nation, Troop 등 영향받는 모든 entity의 변경된 필드를 추적

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-command-parity*
*Context gathered: 2026-04-02*
