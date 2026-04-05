# Phase 5: Modifier Pipeline - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Item, special ability, officer rank 모디파이어가 내정 커맨드 결과에 올바르게 영향을 미치는지 검증하고, 레거시 PHP 대비 스태킹/우선순위 동작이 일치하는지 확인. PHP 전수 읽기 중 발견되는 stat/war 모디파이어 오류도 함께 수정.

Requirements: MOD-01, MOD-02, MOD-03, MOD-04

</domain>

<decisions>
## Implementation Decisions

### 완전성 감사 방법 (Completeness Audit)
- **D-01:** legacy-core/ triggerCall 사용처 (func.php, Command/General/) 전체를 읽고, 모디파이어 카탈로그를 만든 뒤 Kotlin 코드(SpecialModifiers.kt, ItemModifiers.kt, OfficerLevelModifier.kt)와 비교하여 갭을 찾는다. PHP 전수 읽기 방식.

### 검증 전략 (Verification Strategy)
- **D-02:** 모디파이어 단위 golden value 테스트 + 대표 커맨드 3~5개 통합 파이프라인 테스트 모두 수행.
  - 단위: 각 모디파이어별 DomesticContext 입력/출력 golden value (예: '농업' 특기 + 농지개간 → scoreMultiplier=1.2)
  - 통합: DomesticCommand.run() 통진 실행하여 최종 score 값이 레거시와 일치하는지 확인
  - Phase 4 golden value 패턴과 동일한 접근법

### 스태킹/우선순위 순서 (Stacking Order)
- **D-03:** 레거시 PHP triggerCall 순서를 확인하고, 현재 getModifiers() 순서(국가타입→성격→전투특기→내정특기→아이템4종→관직)가 일치하는지 검증. 불일치 시 순서 수정. 복합 스태킹 결과를 golden value 테스트로 검증.

### 범위 (Scope)
- **D-04:** DomesticContext(onCalcDomestic) 관련이 주요 범위이나, PHP 전수 읽기 중 발견되는 stat/war 모디파이어(onCalcStat, onCalcOpposeStat) 오류도 함께 수정한다. 발견 즉시 수정 방식.

### Claude's Discretion
- PHP triggerCall 카탈로그 작성 시 세부 정리 형식
- 단위 테스트 클래스 분할 전략 (모디파이어 소스별 vs 커맨드별)
- 통합 테스트 대상 커맨드 선정 (대표 3~5개)
- golden value 추출 시 PHP 코드 수동 추적 vs 패턴 기반 추출

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Legacy PHP Source (parity target)
- `legacy-core/hwe/func.php` — triggerCall 함수, 모디파이어 적용 순서, DomesticContext 해당 로직 (80KB)
- `legacy-core/hwe/sammo/Command/General/` — 55개 장수 커맨드, 각 커맨드별 onCalcDomestic 호출 패턴
- `legacy-core/hwe/sammo/ActionSpecialDomestic.php` — 내정 특기 모디파이어 정의 (존재 시)
- `legacy-core/hwe/sammo/ActionItem.php` — 아이템 모디파이어 정의 (존재 시)
- `legacy-core/hwe/sammo/Trigger/OfficerLevel.php` — 관직 모디파이어 정의 (존재 시)

### Existing Kotlin Implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ModifierService.kt` — getModifiers() 수집 순서, applyDomesticModifiers() 파이프라인
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ActionModifier.kt` — ActionModifier 인터페이스, DomesticContext/StatContext/StrategicContext/IncomeContext 정의
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt` — 전투 특기 22개 + 내정 특기 ~15개 + che_ 특기 ~20개 구현
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/ItemModifiers.kt` — items.json 기반 아이템 모디파이어 (StatItem, MiscItem, ConsumableItem)
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/OfficerLevelModifier.kt` — 관직 레벨별 leadership/domestic/warPower 보너스
- `backend/game-app/src/main/kotlin/com/opensam/command/general/DomesticCommand.kt` — 내정 커맨드 기본 클래스, getStat() + run() 모디파이어 사용
- `backend/game-app/src/main/kotlin/com/opensam/command/general/DomesticUtils.kt` — applyModifier() 유틸리티, DomesticContext 생성/적용
- `backend/game-app/src/main/kotlin/com/opensam/command/CommandExecutor.kt` — hydrateCommandForConstraintCheck()에서 modifiers 주입

### Phase 3/4 Context (선행 결정)
- `.planning/phases/03-battle-framework-and-core-triggers/03-CONTEXT.md` — ActionModifier는 stat 전용, WarUnitTrigger는 별도 (D-01)
- `.planning/phases/04-battle-completion/04-CONTEXT.md` — golden value 검증 접근법 (D-02)

### Test Patterns
- `backend/game-app/src/test/kotlin/com/opensam/engine/FormulaParityTest.kt` — 공식 parity 테스트 패턴
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/IntimidationTriggerTest.kt` — 모디파이어/트리거 단위 테스트 패턴

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ModifierService`: getModifiers() + applyDomesticModifiers() 파이프라인 이미 구현됨
- `DomesticContext`: costMultiplier, successMultiplier, failMultiplier, scoreMultiplier, riceMultiplier, trainMultiplier, atmosMultiplier, actionCode 필드 보유
- `DomesticUtils.applyModifier()`: actionKey + varType 기반 modifier 적용 유틸리티 이미 구현
- `DomesticCommand`: getStat() + run()에서 이미 modifier pipeline 사용 중
- Phase 4 golden value 테스트 패턴: fixed-seed RNG, 기대값 하드코딩 비교

### Established Patterns
- getModifiers() 6단계 수집: 국가타입 → 성격 → 전투특기 → 내정특기 → 아이템(무기/서적/말/기타) → 관직
- applyDomesticModifiers()는 순서대로 onCalcDomestic() 호출하여 DomesticContext를 fold
- SpecialModifiers.kt의 domestic special들은 actionCode 매칭 후 multiplier 조정 패턴
- OfficerLevelModifier는 officerLevel + nationLevel 기반 조건부 scoreMultiplier × 1.05

### Integration Points
- `DomesticCommand.getCost()` — costMultiplier 적용
- `DomesticCommand.run()` — scoreMultiplier, successMultiplier, failMultiplier 적용
- `DomesticCommand.getStat()` — StatContext를 통한 리더십/무력/지력 보너스 적용
- `CommandExecutor.hydrateCommandForConstraintCheck()` — command.modifiers 주입

</code_context>

<specifics>
## Specific Ideas

- SpecialModifiers.kt에 이미 구현된 내정 특기: 농업, 상업, 징수, 보수, 발명, 의술, 치료, 인덕, 등용, 정치, 건축, 훈련_특기, 모병_특기 + che_ 계열 (~10종)
- ItemModifiers.kt MiscItem.onCalcDomestic()에 domesticSuccess, domesticSabotageSuccess, domesticSupplySuccess/Score, recruit triggerType 구현됨
- OfficerLevelModifier.onCalcDomestic()에 농업/상업/기술/민심·인구/수비·성벽·치안별 레벨 셋 구현됨
- DomesticUtils.applyModifier()는 varType별로 DomesticContext 필드를 선택적으로 사용하는 패턴 — baseValue를 해당 필드에 넣고, 결과에서 같은 필드를 꺼냄

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-modifier-pipeline*
*Context gathered: 2026-04-01*
