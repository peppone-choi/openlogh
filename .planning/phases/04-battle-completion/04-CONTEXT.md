# Phase 4: Battle Completion - Context

**Gathered:** 2026-04-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement 5 remaining combat triggers (반계, 돌격지속, 부상무효, 필살강화_회피불가, 도시치료), verify battle resolution formulas against legacy process_war.php for all unit type pairings, and verify siege/defense mechanics with golden value comparison. All trigger outputs must match legacy PHP for identical inputs.

Requirements: BATTLE-02, BATTLE-03, BATTLE-04, BATTLE-07, BATTLE-08, BATTLE-13, BATTLE-14

</domain>

<decisions>
## Implementation Decisions

### 도시치료 (BATTLE-08) Scope
- **D-01:** 도시치료는 Phase 4에서 구현한다. WarUnitTrigger가 아니라 GeneralTriggerCaller (preTurn trigger)로 구현. SpecialModifiers.kt의 TODO 주석대로 턴 시작 전 도시 치료 기능.

### Formula Verification Strategy
- **D-02:** 전투 데미지 공식은 **전수 매트릭스**로 검증. 모든 병종 조합 (7x7 = 49쌍)에 대해 PHP golden value를 추출하여 Kotlin 결과와 비교. 사상자 수, 사기 영향, 데미지 계산 모두 포함.

### Siege Mechanics Verification
- **D-03:** 공성전 검증은 **golden value 비교**로 개별 공식 검증. 성벽 데미지, 수비 보너스, 성문 돌파 각각의 기대값을 PHP process_war.php에서 추출하여 검증. 통합 시뮬레이션은 하지 않음.

### Trigger Implementation Order
- **D-04:** 5개 트리거는 **process_war.php의 legacy 코드 순서**대로 구현. 의존성 문제를 자연스럽게 해결.

### Claude's Discretion
- 각 트리거의 구체적 hook method 선택 (onEngagementStart/onPreAttack/onPostDamage/onPostRound 중 어느 것을 사용할지) — researcher가 legacy PHP 분석 후 결정
- Plan 분할 전략 (트리거 묶음 vs 트리거+검증 묶음) — planner가 작업량 분석 후 결정
- 도시치료의 GeneralTriggerCaller 구체적 인터페이스 — 기존 GeneralTrigger 패턴 참고하여 결정

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Legacy PHP Source (parity target)
- `legacy-core/hwe/process_war.php` — 전투 해결 로직 (33KB), 트리거 포인트, 데미지 공식, 공성 메커니즘
- `legacy-core/hwe/func.php` — 게임 유틸리티 함수 (RNG, 스탯 계산)
- `legacy-core/hwe/sammo/Command/General/` — 장수 커맨드 중 특수 능력 참조

### Existing Kotlin Implementation
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleEngine.kt` — 전투 루프, WarUnitTrigger 4개 hook이 이미 배선됨
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnitTrigger.kt` — WarUnitTrigger 인터페이스 + WarUnitTriggerRegistry
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleTrigger.kt` — BattleTriggerContext (모든 상태 필드 보유)
- `backend/game-app/src/main/kotlin/com/opensam/engine/modifier/SpecialModifiers.kt` — 5개 트리거 TODO 주석 (legacy 파라미터 포함)
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/trigger/` — Phase 3 구현 트리거 4개 (패턴 참고)
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/WarUnitCity.kt` — 공성전 도시 유닛
- `backend/game-app/src/main/kotlin/com/opensam/engine/war/BattleService.kt` — 전투 서비스
- `backend/game-app/src/main/kotlin/com/opensam/engine/trigger/GeneralTrigger.kt` — 도시치료 구현 시 참고할 GeneralTrigger 패턴

### Phase 3 Context (선행 결정)
- `.planning/phases/03-battle-framework-and-core-triggers/03-CONTEXT.md` — WarUnitTrigger 설계 결정, 테스트 전략

### Test Patterns
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/IntimidationTriggerTest.kt` — Phase 3 트리거 테스트 패턴
- `backend/game-app/src/test/kotlin/com/opensam/engine/war/BattleEngineParityTest.kt` — 전투 엔진 parity 테스트 패턴
- `backend/game-app/src/test/kotlin/com/opensam/engine/FormulaParityTest.kt` — 공식 parity 테스트 패턴

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `WarUnitTrigger` 인터페이스: 4개 hook (onEngagementStart, onPreAttack, onPostDamage, onPostRound) — Phase 3에서 검증 완료
- `WarUnitTriggerRegistry`: mutable map + register() — 새 트리거 등록에 바로 사용
- `BattleTriggerContext`: injuryImmune, counterDamageRatio, dodgeDisabled, criticalDisabled, bonusPhases 등 Phase 4 트리거용 필드 이미 존재
- Phase 3 트리거 4개 (IntimidationTrigger, SnipingTrigger, BattleHealTrigger, RageTrigger) — 구현 패턴 그대로 따름
- `GeneralTrigger` / `TriggerCaller` — 도시치료 구현 시 사용할 턴 트리거 인프라

### Established Patterns
- 시도/발동 (attempt/activation) 2단계 패턴 — 모든 트리거에 적용
- Fixed-seed RNG 테스트 — 확률적 결과를 결정적으로 테스트
- Loop-scoped var 패턴 — cross-phase 상태 유지 (rage에서 검증됨)
- coerceIn 가드 — 모든 Short 필드 산술에 적용 (Phase 2)

### Integration Points
- `BattleEngine.kt` — WarUnitTrigger hook 호출 지점 (이미 배선됨, 새 트리거는 Registry에 등록만 하면 됨)
- `SpecialModifiers.kt` — TODO 제거 + ActionModifier 통합
- `GeneralTrigger.kt` — 도시치료 등록 지점

</code_context>

<specifics>
## Specific Ideas

- SpecialModifiers.kt TODO에 각 트리거의 legacy 파라미터가 이미 기록되어 있음 — 구현 시 참조
- BattleTriggerContext에 Phase 4용 필드 (counterDamageRatio, dodgeDisabled, criticalDisabled, bonusPhases 등)가 이미 존재하므로 새 필드 추가 최소화
- 전수 매트릭스 검증 시 CrewType enum의 모든 병종을 순회하여 조합 생성

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 04-battle-completion*
*Context gathered: 2026-04-01*
